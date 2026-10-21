package com.app.ralaunch.feature.steam.data

import android.content.Context
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.platform.runtime.dotnet.DotNetLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/**
 * SteamContentCli 的宿主封装。
 *
 * CLI 是 .NET 10 程序集，用 RAL 自带的 hostfxr 在进程内跑（Android 上 Process.Start 会被 SELinux 拒）。
 * hostfxr 在一个进程里只能初始化一次，所以这里常驻一个 `--serve` 服务进程：
 *   - 每条命令写一个 <id>.req（JSON: {id, args}）到控制目录
 *   - 服务把事件写 events-<id>.jsonl，结束时写 <id>.done（退出码）
 *   - Steam Guard 验证码通过 --guard-file 回传
 */
object SteamCli {
    private const val TAG = "SteamCli"
    private const val ASSET_ROOT = "steam"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var serviceReady = false
    @Volatile private var serviceStarted = false
    @Volatile private var serviceDead = false
    private var serviceJob: Job? = null

    fun cliDir(context: Context): File = File(context.filesDir, "steam-cli")

    private fun workDir(context: Context): File =
        File(context.filesDir, "steam").also { it.mkdirs() }

    private fun controlDir(context: Context): File = File(workDir(context), "service")

    fun credentialsFile(context: Context): File = File(workDir(context), "credentials.json")

    fun guardFile(context: Context): File = File(workDir(context), "guard.txt")

    fun hasCredentials(context: Context): Boolean =
        credentialsFile(context).let { it.exists() && it.length() > 0 }

    fun forgetCredentials(context: Context) {
        credentialsFile(context).delete()
    }

    /** 把 assets/steam 解到 filesDir/steam-cli（首次或版本变化时） */
    suspend fun ensureAssets(context: Context): File = withContext(Dispatchers.IO) {
        val dir = cliDir(context)
        val marker = File(dir, ".version")
        val expected = assetVersion(context)
        if (!File(dir, "SteamContentCli.dll").exists() || !marker.exists() || marker.readText() != expected) {
            AppLog.i(TAG, "展开 Steam CLI 资产 -> ${dir.absolutePath}")
            dir.deleteRecursively()
            dir.mkdirs()
            copyAssetDir(context, ASSET_ROOT, dir)
            marker.writeText(expected)
        }
        dir
    }

    /** 跑一条 CLI 命令；事件逐行回调，返回退出码 */
    suspend fun run(
        context: Context,
        args: List<String>,
        onEvent: (JSONObject) -> Unit,
    ): Int = mutex.withLock {
        if (!ensureService(context)) {
            onEvent(JSONObject(mapOf("type" to "error", "message" to "Steam 服务启动失败（.NET 运行时不可用）")))
            return@withLock -1
        }

        val dir = controlDir(context)
        val id = UUID.randomUUID().toString().replace("-", "")
        val requestFile = File(dir, "$id.req")
        val eventsFile = File(dir, "events-$id.jsonl")
        val doneFile = File(dir, "$id.done")
        val guard = guardFile(context)

        val requestArgs = args + listOf("--guard-file", guard.absolutePath)
        val payload = JSONObject().put("id", id).put("args", org.json.JSONArray(requestArgs))
        requestFile.writeText(payload.toString())
        AppLog.i(TAG, "Steam 请求: ${redact(requestArgs).joinToString(" ")}")

        var offset = 0L
        var code = -1
        val deadline = System.currentTimeMillis() + 3 * 60 * 60 * 1000L // 下载可能很久，最多 3 小时
        while (System.currentTimeMillis() < deadline) {
            if (eventsFile.exists()) {
                offset = readFrom(eventsFile, offset, onEvent)
            }
            if (doneFile.exists()) {
                code = runCatching { doneFile.readText().trim().toInt() }.getOrDefault(-1)
                break
            }
            if (serviceDead) break
            delay(200)
        }

        if (eventsFile.exists()) readFrom(eventsFile, offset, onEvent)
        requestFile.delete()
        doneFile.delete()
        eventsFile.delete()
        AppLog.i(TAG, "Steam 请求结束，退出码 $code")
        code
    }

    /** 宿主写入 Steam Guard 验证码（服务轮询该文件，读到即删除） */
    fun submitGuard(context: Context, code: String) {
        guardFile(context).apply { parentFile?.mkdirs() }.writeText(code.trim())
    }

    private suspend fun ensureService(context: Context): Boolean {
        if (serviceReady) return true
        if (serviceDead) return false

        val cli = ensureAssets(context)
        val dir = controlDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        val ready = File(dir, "service.ready")

        // hostfxr 的 JNI 实现（libmain.so）默认由游戏进程加载，这里在宿主进程里手动加载一次
        ensureNativeLoaded()

        serviceStarted = true
        serviceJob = scope.launch {
            try {
                DotNetLauncher.hostfxrLaunch(
                    File(cli, "SteamContentCli.dll").absolutePath,
                    arrayOf("--serve", dir.absolutePath),
                )
                AppLog.w(TAG, "Steam 服务进程退出")
            } catch (e: Throwable) {
                AppLog.e(TAG, "Steam 服务异常: ${e.message}", e)
            }
        }

        repeat(300) {
            if (ready.exists()) {
                serviceReady = true
                AppLog.i(TAG, "Steam 服务已就绪")
                return true
            }
            if (serviceJob?.isActive == false) {
                serviceDead = true
                AppLog.e(TAG, "Steam 服务未能启动")
                return false
            }
            delay(200)
        }

        serviceDead = true
        AppLog.e(TAG, "Steam 服务启动超时")
        return false
    }

    /** 日志里不要把密码打出来 */
    private fun redact(args: List<String>): List<String> {
        val result = mutableListOf<String>()
        var maskNext = false
        for (arg in args) {
            result += if (maskNext) "***" else arg
            maskNext = arg == "--password"
        }
        return result
    }

    private fun ensureNativeLoaded() {
        for (library in listOf("dotnethost", "main")) {
            try {
                System.loadLibrary(library)
                AppLog.i(TAG, "已加载 lib$library.so")
            } catch (e: UnsatisfiedLinkError) {
                AppLog.w(TAG, "加载 lib$library.so 失败: ${e.message}")
            }
        }
    }

    private fun readFrom(file: File, from: Long, onEvent: (JSONObject) -> Unit): Long {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() <= from) return from
                raf.seek(from)
                val builder = StringBuilder()
                var line = raf.readLine()
                while (line != null) {
                    builder.append(line).append('\n')
                    line = raf.readLine()
                }
                val newOffset = raf.filePointer
                builder.toString().lineSequence().forEach { raw ->
                    val text = raw.trim()
                    if (text.isEmpty()) return@forEach
                    runCatching { onEvent(JSONObject(text)) }
                        .onFailure { AppLog.w(TAG, "事件解析失败: $text") }
                }
                newOffset
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "读取事件文件失败: ${e.message}")
            from
        }
    }

    /** 版本指纹：包版本 + 所有资产文件大小之和（原地换 DLL 也能触发重新展开） */
    private fun assetVersion(context: Context): String {
        val longVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        }.getOrDefault(0L)
        return "$longVersion-${assetSize(context, ASSET_ROOT)}"
    }

    private fun assetSize(context: Context, path: String): Long {
        val children = context.assets.list(path) ?: return 0
        if (children.isEmpty()) {
            return runCatching {
                context.assets.open(path).use { it.available().toLong() }
            }.getOrDefault(0L)
        }
        return children.sumOf { child ->
            val childPath = if (path.isEmpty()) child else "$path/$child"
            assetSize(context, childPath)
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            try {
                context.assets.open(assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                target.mkdirs()
            }
            return
        }
        target.mkdirs()
        for (child in children) {
            copyAssetDir(context, "$assetPath/$child", File(target, child))
        }
    }
}
