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
        if (code != 0 && eventsFile.exists()) {
            runCatching { eventsFile.copyTo(File(dir, "last-events.jsonl"), overwrite = true) }
        }
        requestFile.delete()
        doneFile.delete()
        eventsFile.delete()
        AppLog.i(TAG, "Steam 请求结束，退出码 $code")
        code
    }

    /**
     * 把 Steamworks.NET shim 放进 Steam 下载的游戏目录。
     *
     * 游戏侧 `SteamAPI.Init()` 会去加载 `Steamworks.NET.dll`；没有真 Steam 客户端时它会弹
     * “Please launch the game from your Steam client” 并直接退出，所以安装/启动前必须用我们
     * 这份（把原生层换成 SteamKit2 会话的实现）覆盖上去。大小不同才重写，避免每次都拷 400KB。
     */
    fun ensureSteamworksShim(context: Context, gameDir: File): Boolean {
        val target = File(gameDir, "Steamworks.NET.dll")
        return try {
            val assetSize = context.assets.open(ASSET_SHIM).use { it.available().toLong() }
            if (target.exists() && target.length() == assetSize) return true
            context.assets.open(ASSET_SHIM).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            AppLog.i(TAG, "已部署 Steamworks.NET shim -> ${target.absolutePath}")
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "部署 Steamworks.NET shim 失败: ${e.message}")
            false
        }
    }

    private const val ASSET_SHIM = "prebuilt/steam-shim/Steamworks.NET.dll"


    /**
     * 现查一个 app 的 Steam 分支（PICS 的 depots.branches）。
     * 受 beta 密码保护的分支（tModLoader 的 1.4.5-dev）在公开 appinfo 里看不到，
     * 必须把密码发给 Steam（--beta-code）才会被返回。
     */
    suspend fun listBranches(context: Context, appId: Int, betaCode: String? = null): List<SteamBranch> =
        withContext(Dispatchers.IO) {
            val branches = mutableListOf<SteamBranch>()
            val args = buildList {
                if (!betaCode.isNullOrBlank()) {
                    add("--beta-code"); add(betaCode)
                } else {
                    add("--list-branches")
                }
                add("--app"); add(appId.toString())
                if (hasCredentials(context)) {
                    add("--credentials"); add(credentialsFile(context).absolutePath)
                }
            }
            val code = run(context, args) { event ->
                if (event.optString("type") == "branch") {
                    val name = event.optString("name")
                    if (name.isNotBlank()) {
                        branches += SteamBranch(
                            id = name,
                            label = SteamCatalog.branchLabel(name),
                            gated = !betaCode.isNullOrBlank(),
                        )
                    }
                }
            }
            AppLog.i(TAG, "Steam 分支查询：app=$appId code=${if (betaCode.isNullOrBlank()) "none" else "***"} " +
                "exit=$code branches=${branches.map { it.id }}")
            branches
        }

    /**
     * 把登录账号的身份（SteamID/昵称）写成游戏目录下的 steam-shim/identity.json，
     * 供 Steamworks.NET shim 读取。没登录或失败时返回 false（不阻塞启动）。
     */
    suspend fun writeIdentityFile(context: Context, gameDir: File): Boolean {
        if (!hasCredentials(context)) return false
        val shimDir = File(gameDir, "steam-shim")
        shimDir.mkdirs()
        val out = File(shimDir, "identity.json")
        val appId = File(gameDir, "steam_appid.txt").takeIf { it.exists() }
            ?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val args = buildList {
            add("--identity"); add(out.absolutePath)
            add("--credentials"); add(credentialsFile(context).absolutePath)
            if (appId != null) { add("--app"); add(appId) }
        }
        val code = run(context, args) { }
        return if (code == 0 && out.exists()) {
            AppLog.i(TAG, "Steam 身份已写入 ${out.absolutePath}")
            copyShimPort(context, shimDir)
            true
        } else {
            AppLog.w(TAG, "Steam 身份写入失败，退出码 $code")
            false
        }
    }

    /** 把 CLI 服务的 shim 端口写给游戏侧（steam-shim/port.txt） */
    private fun copyShimPort(context: Context, shimDir: File) {
        val source = File(controlDir(context), "shim-port.txt")
        if (!source.exists()) {
            AppLog.w(TAG, "shim 端口文件不存在：${source.absolutePath}")
            return
        }
        val port = source.readText().trim()
        if (port.isEmpty()) return
        File(shimDir, "port.txt").writeText(port)
        AppLog.i(TAG, "shim 端口已写给游戏：$port")
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

    /** 版本指纹：包版本 + SteamContentCli/TmlAndroidContent 两个 dll 的 SHA-256 前缀 */
    private fun assetVersion(context: Context): String {
        val longVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        }.getOrDefault(0L)
        val hashes = listOf("SteamContentCli.dll", "TmlAndroidContent.dll").joinToString("-") { name ->
            runCatching {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                context.assets.open("$ASSET_ROOT/$name").use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().take(6).joinToString("") { "%02x".format(it) }
            }.getOrDefault("x")
        }
        return "$longVersion-$hashes"
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
