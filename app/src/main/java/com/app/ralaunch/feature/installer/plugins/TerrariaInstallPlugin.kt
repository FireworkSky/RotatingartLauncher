package com.app.ralaunch.feature.installer.plugins

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.feature.installer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Terraria/tModLoader 安装插件
 */
class TerrariaInstallPlugin : BaseInstallPlugin() {

    private val TAG = "TerrariaInstallPlugin"

    override val pluginId = "terraria"
    override val displayName: String
        get() = RaLaunchApp.getInstance().getString(R.string.install_plugin_display_name_terraria_tmodloader)
    override val supportedGames = listOf(GameDefinition.TERRARIA, GameDefinition.TMODLOADER)
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        // 检测 Terraria GOG .sh 文件
        if (fileName.endsWith(".sh") && fileName.contains("terraria")) {
            return GameDetectResult(GameDefinition.TERRARIA)
        }
        
        // 检测 Terraria ZIP
        if (fileName.endsWith(".zip") && fileName.contains("terraria")) {
            return GameDetectResult(GameDefinition.TERRARIA)
        }
        
        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        val fileName = modLoaderFile.name.lowercase()
        if (!fileName.endsWith(".zip")) return null

        // tModLoader 1.3：发行包里只有 tModLoader.exe（mono + FNA），没有 1.4 的
        // tModLoader.dll / tModLoader.deps.json。以内容判定，不依赖文件名。
        if (isTModLoaderLegacy(modLoaderFile)) {
            return ModLoaderDetectResult(GameDefinition.TMODLOADER, version = LEGACY_TML_VERSION)
        }

        // 检测 tModLoader
        if (fileName.contains("tmodloader")) {
            return ModLoaderDetectResult(GameDefinition.TMODLOADER)
        }

        return null
    }

    /** ZIP 里是否带 tModLoader 1.3 的 tModLoader.exe */
    private fun isTModLoaderLegacy(zipFile: File): Boolean = try {
        ZipFile(zipFile).use { zip ->
            zip.getEntry("tModLoader.exe") != null || zip.getEntry("tModLoader.bin.x86_64") != null
        }
    } catch (e: Exception) {
        AppLog.w(TAG, "检测 tModLoader 1.3 失败: ${e.message}")
        false
    }
    
    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        gameStorageRootFull: File,
        callback: InstallCallback
    ) {
        isCancelled = false
        
        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_starting),
                        0
                    )
                }
                
                if (!gameStorageRootFull.exists()) gameStorageRootFull.mkdirs()
                
                // 解压游戏本体
                var terrariaExeParent: File? = extractGameFile(gameFile, gameStorageRootFull, callback)
                
                if (terrariaExeParent == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError(
                            RaLaunchApp.getInstance().getString(R.string.install_extract_game_failed)
                        )
                    }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 确定最终的游戏定义
                var definition = GameDefinition.TERRARIA
                var finalExeParent = terrariaExeParent
                var legacyTml13 = false
                
                // 安装 tModLoader
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader_prepare_dir),
                            48
                        )
                    }

                    val tml13 = isTModLoaderLegacy(modLoaderFile)
                    legacyTml13 = tml13
                    if (tml13) {
                        // RAL: tModLoader 1.3 是把 tModLoader.exe 平铺到 Terraria.exe 同级目录的
                        // 覆盖式安装（1.4 才是独立的 tModLoader/ 子目录 + tModLoader.dll）。
                        withContext(Dispatchers.Main) {
                            callback.onProgress(
                                RaLaunchApp.getInstance().getString(R.string.install_tmodloader),
                                55
                            )
                        }
                        installTModLoaderLegacy(modLoaderFile, terrariaExeParent, callback)
                        definition = GameDefinition.TMODLOADER
                        finalExeParent = terrariaExeParent
                    } else {
                        val gogGamesDir = terrariaExeParent.parentFile
                        val tModLoaderExeParent = File(gogGamesDir, "tModLoader")
                        tModLoaderExeParent.mkdirs()

                        withContext(Dispatchers.Main) {
                            callback.onProgress(
                                RaLaunchApp.getInstance().getString(R.string.install_tmodloader),
                                55
                            )
                        }
                        installTModLoader(modLoaderFile, tModLoaderExeParent, callback)

                        definition = GameDefinition.TMODLOADER
                        finalExeParent = tModLoaderExeParent
                    }
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 安装 MonoMod 库
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_monomod),
                        90
                    )
                }
                if (legacyTml13) {
                    // RAL: tModLoader 1.3 走 mono 运行时，MonoMod 25.x 在 mono 4.5 profile 上
                    // 无法加载（net8/netstandard2.0 引用 System.Runtime 8.0+），因此换成
                    // 包装 exe + MonoMod 19（Android 可用版本），不再做 MonoMod 程序集替换。
                    installLegacyBootstrap(finalExeParent)
                } else {
                    installMonoMod(finalExeParent)
                }

                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_extract_icon),
                        92
                    )
                }
                val iconPath = extractIcon(finalExeParent, definition)

                // 创建游戏信息文件 - 使用 outputDir 作为存储根目录
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_finishing),
                        98
                    )
                }
                createGameInfo(
                    gameStorageRootFull, finalExeParent, definition, iconPath,
                    launchTargetOverride = if (legacyTml13) LEGACY_LAUNCHER else null,
                    runtimeOverride = if (legacyTml13) LEGACY_RUNTIME else null
                )

                // 创建 GameItem 并回调
                val gameItem = createGameItem(
                    definition = definition,
                    storageRootDir = gameStorageRootFull,
                    actualGameDir = finalExeParent,
                    iconPath = iconPath,
                    launchTargetOverride = if (legacyTml13) LEGACY_LAUNCHER else null,
                    runtimeOverride = if (legacyTml13) LEGACY_RUNTIME else null
                )
                
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_complete),
                        100
                    )
                    callback.onComplete(gameItem)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(
                        e.message ?: RaLaunchApp.getInstance().getString(R.string.install_failed)
                    )
                }
            }
        }
    }
    
    private suspend fun extractGameFile(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val fileName = gameFile.name.lowercase()
        
        return if (fileName.endsWith(".sh")) {
            extractGogSh(gameFile, outputDir, callback)
        } else if (fileName.endsWith(".zip")) {
            extractZip(gameFile, outputDir, callback)
        } else null
    }
    
    private suspend fun extractGogSh(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val result = GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
            if (!isCancelled) {
                val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onProgress(msg, progressInt)
                }
            }
        }
        
        return when (result) {
            is GameExtractorUtils.ExtractResult.Error -> null
            is GameExtractorUtils.ExtractResult.Success -> result.outputDir
        }
    }
    
    private suspend fun extractZip(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val result = GameExtractorUtils.extractZip(
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
        
        return when (result) {
            is GameExtractorUtils.ExtractResult.Error -> null
            is GameExtractorUtils.ExtractResult.Success -> result.outputDir
        }
    }
    
    private suspend fun installTModLoader(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        val tempDir = File(outputDir.parentFile, "temp_tmodloader_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            val result = GameExtractorUtils.extractZip(
                zipFile = modLoaderFile,
                outputDir = tempDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = 55 + (progress * 30).toInt().coerceIn(0, 30)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(
                                RaLaunchApp.getInstance().getString(
                                    R.string.install_tmodloader_with_detail,
                                    msg
                                ),
                                progressInt
                            )
                        }
                    }
                }
            )
            
            when (result) {
                is GameExtractorUtils.ExtractResult.Error -> throw Exception(result.message)
                is GameExtractorUtils.ExtractResult.Success -> {
                    val sourceDir = findTModLoaderRoot(tempDir)
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader_copy_files),
                            88
                        )
                    }
                    copyDirectory(sourceDir, outputDir)
                }
            }
        } finally {
            FileUtils.deleteDirectoryRecursivelyWithinRoot(tempDir, outputDir)
        }
    }
    
    // ===== tModLoader 1.3（mono + FNA）=====

    /** tModLoader 1.3 的启动入口：包装 exe（安装时写入，game_info 指向它） */
    private val LEGACY_LAUNCHER = "TML13Launcher.exe"

    /** tModLoader 1.3 必须走 mono 运行时（mono:<版本>） */
    private val LEGACY_RUNTIME = "mono:4.5"

    private val LEGACY_TML_VERSION = "0.11.x"

    private suspend fun installTModLoaderLegacy(
        modLoaderFile: File,
        terrariaExeParent: File,
        callback: InstallCallback
    ) {
        val tempDir = File(terrariaExeParent.parentFile, "temp_tml13_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val result = GameExtractorUtils.extractZip(
                zipFile = modLoaderFile,
                outputDir = tempDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = 55 + (progress * 30).toInt().coerceIn(0, 30)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(
                                RaLaunchApp.getInstance().getString(
                                    R.string.install_tmodloader_with_detail,
                                    msg
                                ),
                                progressInt
                            )
                        }
                    }
                }
            )

            when (result) {
                is GameExtractorUtils.ExtractResult.Error -> throw Exception(result.message)
                is GameExtractorUtils.ExtractResult.Success -> {
                    val sourceDir = findLegacyRoot(tempDir)
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader_copy_files),
                            88
                        )
                    }
                    copyLegacyOverlay(sourceDir, terrariaExeParent)
                }
            }
        } finally {
            FileUtils.deleteDirectoryRecursivelyWithinRoot(tempDir, terrariaExeParent)
        }
    }

    /** tModLoader 1.3 发行包的根目录（含 tModLoader.exe 的那一层） */
    private fun findLegacyRoot(extractedDir: File): File {
        if (File(extractedDir, "tModLoader.exe").exists()) return extractedDir
        val subdirs = extractedDir.listFiles { file -> file.isDirectory } ?: return extractedDir
        for (subdir in subdirs) {
            if (File(subdir, "tModLoader.exe").exists()) return subdir
        }
        return if (subdirs.size == 1) subdirs[0] else extractedDir
    }

    /**
     * 把 tModLoader 1.3 发行包平铺复制到 Terraria.exe 同级目录。
     *
     * 必须跳过的内容：
     * - mscorlib/System.* 等 mono BCL：会抢在 RAL 自带 BCL 之前被加载，导致运行时版本错乱
     * - lib/、lib64/：Linux glibc 平台库（带版本号的 .so），Android/bionic 用不到
     * - FNA.dll / FNA.dll.config：旧版 FNA（自带 mojoshader/OpenAL 路径），安装时会换成
     *   与新 FNA3D 配套的 FNA（见 [installLegacyBootstrap]）
     * - mono 启动脚本 / x86 宿主 / 调试符号
     */
    private fun copyLegacyOverlay(source: File, destination: File) {
        source.walkTopDown().forEach { file ->
            if (file.isDirectory) return@forEach
            val rel = file.toRelativeString(source).replace('\\', '/')
            if (shouldSkipLegacyEntry(rel)) return@forEach
            val target = File(destination, rel)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
    }

    private fun shouldSkipLegacyEntry(rel: String): Boolean {
        val lower = rel.lowercase()
        if (lower.startsWith("lib/") || lower.startsWith("lib64/")) return true
        val name = lower.substringAfterLast('/')
        if (name == "mscorlib.dll") return true
        if (name == "system.dll" || name.startsWith("system.")) return true
        if (name == "mono.posix.dll" || name == "mono.security.dll") return true
        if (name == "monoconfig" || name == "monomachineconfig") return true
        if (name == "fna.dll" || name == "fna.dll.config") return true
        if (name == "open-folder") return true
        if (name.startsWith("tmodloader.bin") || name.startsWith("tmodloaderserver.bin")) return true
        if (name == "tmodloader" || name == "tmodloader-kick" || name == "tmodloader-mono") return true
        if (name.startsWith("tmodloaderserver")) return true
        if (name.endsWith(".pdb")) return true
        return false
    }

    /**
     * 安装 tModLoader 1.3 的运行期引导文件（来自 assets/prebuilt/tml13）：
     * 包装 exe、配套 FNA（FNA3D/FAudio 版）、MonoMod 19（Android 可用的 detour 实现）。
     */
    private fun installLegacyBootstrap(gameDir: File) {
        // 说明：不再往游戏目录塞 MonoMod —— tModLoader 1.3 自带内嵌的 MonoMod 20.11，
        // 包装 exe 通过 AssemblyResolve 复用同一份，避免版本打架（19/25 都不适配 mono）。
        val assets = listOf(
            "TML13Launcher.exe",
            "FNA.dll",
        )
        assets.forEach { name ->
            try {
                RaLaunchApp.getInstance().assets.open("prebuilt/tml13/$name").use { input ->
                    File(gameDir, name).outputStream().use { output -> input.copyTo(output) }
                }
                AppLog.i(TAG, "已安装 tModLoader 1.3 引导文件: $name")
            } catch (e: Exception) {
                AppLog.w(TAG, "安装引导文件失败 $name: ${e.message}")
            }
        }
    }

    private fun findTModLoaderRoot(extractedDir: File): File {
        if (File(extractedDir, "tModLoader.dll").exists()) return extractedDir
        
        val subdirs = extractedDir.listFiles { file -> file.isDirectory } ?: return extractedDir
        
        for (subdir in subdirs) {
            if (File(subdir, "tModLoader.dll").exists()) return subdir
        }
        
        return if (subdirs.size == 1) subdirs[0] else extractedDir
    }
}
