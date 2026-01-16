package com.app.ralaunch.installer.plugins

import android.util.Log
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.core.AssemblyPatcher
import com.app.ralaunch.core.GameLauncher
import com.app.ralaunch.installer.GameDetectResult
import com.app.ralaunch.installer.GameExtractorUtils
import com.app.ralaunch.installer.GameInstallPlugin
import com.app.ralaunch.installer.InstallCallback
import com.app.ralaunch.installer.ModLoaderDetectResult
import com.app.ralib.icon.IconExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipFile

class CelesteInstallPlugin : GameInstallPlugin {
    private val TAG = "CelesteInstallPlugin"

    override val pluginId = "celeste"
    override val displayName = "Celeste / Everest"
    override val supportedGameTypes = listOf("celeste", "everest")

    private var installJob: Job? = null
    private var isCancelled = false

    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()

        // 检测到 Celeste 游戏压缩包
        if (fileName.endsWith(".zip") && fileName.contains("celeste")) {
            return GameDetectResult(
                gameName = "Celeste",
                gameType = "celeste",
                launchTarget = "Celeste.exe"
            )
        }

        return null
    }

    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        val fileName = modLoaderFile.name.lowercase()

        ZipFile(modLoaderFile).use { zip ->
            val everestLibEntry = zip.getEntry("main/everest-lib")

            // 检测到 Everest Mod Loader
            if (everestLibEntry != null) {
                return ModLoaderDetectResult(
                    name = "Everest",
                    type = "everest",
                    launchTarget = "Celeste.dll"
                )
            }
        }

        return null
    }

    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        outputDir: File,
        callback: InstallCallback
    ) {
        isCancelled = false

        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress("开始安装...", 0)
                }

                // 创建输出目录
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                // 使用工具类解压游戏本体
                val extractResult = GameExtractorUtils.extractZip(
                    zipFile = gameFile,
                    outputDir = outputDir,
                    progressCallback = { msg, progress ->
                        if (!isCancelled) {
                            val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onProgress(msg, progressInt)
                            }
                        }
                    }
                )
                when (extractResult) {
                    is GameExtractorUtils.ExtractResult.Error -> {
                        withContext(Dispatchers.Main) {
                            callback.onError(extractResult.message)
                        }
                        return@launch
                    }
                    is GameExtractorUtils.ExtractResult.Success -> {
                        // 解压成功，继续后续步骤
                    }
                }

                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }

                var finalGameName = "Celeste"
                var launchTarget = "Celeste.exe"
                var iconPath: String? = null

                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress("安装 Everest...", 55)
                    }

                    // 同时安装 MonoMod 库
                    installEverest(modLoaderFile, outputDir, callback)

                    finalGameName = "Everest"
                    launchTarget = "Celeste.dll"
                }

                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress("提取图标...", 92)
                }
                iconPath = extractIcon(outputDir, launchTarget)

                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }

                createGameInfo(outputDir, finalGameName, launchTarget, iconPath)

                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(
                        gamePath = outputDir.absolutePath,
                        gameBasePath = outputDir.absolutePath,  // 根安装目录，用于删除
                        gameName = finalGameName,
                        launchTarget = launchTarget,
                        iconPath = iconPath
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "安装失败")
                }
            }
        }
    }

    override fun cancel() {
        isCancelled = true
        installJob?.cancel()
    }

    /**
     * 解压 MonoMod 库并应用到游戏目录
     * 1. 解压 MonoMod 到 monomod 目录
     * 2. 从该目录读取 DLL 并替换游戏目录中的对应文件
     */
    private fun extractMonoModLibraries(gameDir: File) {
        try {
            val context = RaLaunchApplication.getAppContext()

            // 1. 解压 MonoMod 到目录
            val extractSuccess = AssemblyPatcher.extractMonoMod(context)
            if (!extractSuccess) {
                Log.w(TAG, "MonoMod 解压失败")
                return
            }

            // 2. 从 MonoMod 目录应用补丁到游戏目录
            val patchedCount = AssemblyPatcher.applyMonoModPatches(
                context, gameDir.absolutePath, true)

            if (patchedCount >= 0) {
                Log.i(TAG,
                    "MonoMod 已应用，替换了 $patchedCount 个文件")
            } else {
                Log.w(TAG, "MonoMod 应用失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MonoMod 安装异常", e)
        }
    }

    /**
     * 从 DLL/EXE 文件提取图标
     */
    private fun extractIcon(outputDir: File, launchTarget: String): String? {
        try {
            // 优先使用预设图标
            if (outputDir.resolve("Celeste.png").exists()) {
                return outputDir.resolve("Celeste.png").absolutePath
            }

            // 确定要提取图标的文件
            val targetFile = File(outputDir, launchTarget)
            var iconSourceFile = targetFile

            // 如果启动目标是 DLL，尝试找对应的 EXE
            if (launchTarget.lowercase().endsWith(".dll")) {
                val baseName = launchTarget.substringBeforeLast(".")
                val exeFile = File(outputDir, "$baseName.exe")
                if (exeFile.exists()) {
                    iconSourceFile = exeFile
                }
            }

            // 如果目标文件不存在，尝试搜索游戏目录
            if (!iconSourceFile.exists()) {
                // 搜索可能的 EXE 文件
                val exeFiles = outputDir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() == "exe" }
                    .toList()

                if (exeFiles.isNotEmpty()) {
                    // 优先选择名称包含游戏名的
                    iconSourceFile = exeFiles.find {
                        it.name.lowercase().contains("celeste")
                    } ?: exeFiles.first()
                }
            }

            if (!iconSourceFile.exists()) {
                return null
            }

            // 提取图标
            val iconOutputPath = File(outputDir, "icon.png").absolutePath
            val success = IconExtractor.extractIconToPng(iconSourceFile.absolutePath, iconOutputPath)

            if (success && File(iconOutputPath).exists() && File(iconOutputPath).length() > 0) {
                return iconOutputPath
            }

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun createGameInfo(outputDir: File, gameName: String, launchTarget: String, iconPath: String?) {
        val infoFile = File(outputDir, "game_info.json")
        val iconField = if (iconPath != null) {
            """,
  "icon_path": "${iconPath.replace("\\", "\\\\")}" """
        } else ""

        val json = """
{
  "game_name": "$gameName",
  "game_type": "${if (gameName == "Celeste") "celeste" else "everest"}",
  "launch_target": "$launchTarget",
  "install_time": "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}"$iconField
}
        """.trimIndent()
        infoFile.writeText(json)
    }

    private suspend fun installEverest(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        val extractResult = GameExtractorUtils.extractZip(
            zipFile = modLoaderFile,
            outputDir = outputDir,
            sourcePrefix = "main",
            progressCallback = { msg, progress ->
                if (!isCancelled) {
                    val progressInt = 55 + (progress * 25).toInt().coerceIn(0, 25)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onProgress("安装 Everest: $msg", progressInt)
                    }
                }
            }
        )

        when (extractResult) {
            is GameExtractorUtils.ExtractResult.Error -> {
                throw Exception(extractResult.message)
            }
            is GameExtractorUtils.ExtractResult.Success -> {
                // 继续安装 Everest
            }
        }

        // 解压 MonoMod 库到游戏目录
        withContext(Dispatchers.Main) {
            callback.onProgress("安装 MonoMod 库...", 85)
        }
        extractMonoModLibraries(outputDir)

        // 解压 MonoMod 库到游戏目录
        withContext(Dispatchers.Main) {
            callback.onProgress("执行 Everest MiniInstaller...", 90)
        }

        val patches = RaLaunchApplication.getPatchManager().getPatchesByIds(
            listOf("com.app.ralaunch.everest.miniinstaller.fix")
        )

        if (patches.size != 1) {
            throw Exception("未找到 Everest MiniInstaller 修补程序，或者存在多个同 ID 修补程序")
        }

        val patchResult = GameLauncher.launchDotNetAssembly(
            outputDir.resolve("MiniInstaller.dll").toString(),
            arrayOf(),
            patches)

        outputDir.resolve("everest-launch.txt").writeText("# Splash screen disabled by Rotating Art Launcher\n--disable-splash\n")

        if (patchResult != 0) {
            throw Exception("Everest MiniInstaller 执行失败，错误码：$patchResult")
        }
    }
}