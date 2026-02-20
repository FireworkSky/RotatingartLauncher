package com.app.ralaunch.core.platform.install.plugins

import android.os.Environment
import android.util.Log
import com.app.ralaunch.core.platform.install.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream

/**
 * Stardew Valley / SMAPI 安装插件
 */
class SmapiInstallPlugin : BaseInstallPlugin() {
    
    companion object {
        private const val TAG = "SmapiInstallPlugin"
        private const val SMAPI_MODS_PATH_ENV_KEY = "SMAPI_MODS_PATH"
        private const val SMAPI_MODS_PATH_VALUE_TEMPLATE = "{XDG_DATA_HOME}/Stardew Valley/Mods"
        
        /** RALauncher 外部存储目录名 */
        private const val RALAUNCHER_DIR = "RALauncher"
        
        /** SMAPI 模组子目录 */
        private const val SMAPI_MODS_SUBDIR = "Stardew Valley/Mods"
        
        /**
         * 获取 SMAPI 模组目录（外部存储）
         * @return /storage/emulated/0/RALauncher/Stardew Valley/Mods
         */
        fun getSmapiModsDirectory(): File {
            return File(Environment.getExternalStorageDirectory(), "$RALAUNCHER_DIR/$SMAPI_MODS_SUBDIR")
        }
    }
    
    override val pluginId = "smapi"
    override val displayName = "Stardew Valley / SMAPI"
    override val supportedGames = listOf(GameDefinition.STARDEW_VALLEY, GameDefinition.SMAPI)
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        if (fileName.endsWith(".sh") && (fileName.contains("stardew") || fileName.contains("valley"))) {
            return GameDetectResult(GameDefinition.STARDEW_VALLEY)
        }
        
        if (fileName.endsWith(".zip") && (fileName.contains("stardew") || fileName.contains("valley"))) {
            return GameDetectResult(GameDefinition.STARDEW_VALLEY)
        }
        
        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        val fileName = modLoaderFile.name.lowercase()
        
        if (fileName.contains("smapi") && fileName.endsWith(".zip")) {
            return ModLoaderDetectResult(GameDefinition.SMAPI)
        }
        
        return null
    }
    
    /**
     * 检测 SMAPI 是否为安装器格式（包含 .dat 文件）
     */
    private fun isSmapiInstaller(modLoaderFile: File): Boolean {
        try {
            ZipInputStream(FileInputStream(modLoaderFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.lowercase().endsWith(".dat")) return true
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) { /* 忽略 */ }
        return false
    }
    
    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        gameStorageRoot: File,
        callback: InstallCallback
    ) {
        isCancelled = false
        
        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress("开始安装...", 0)
                }
                
                if (!gameStorageRoot.exists()) gameStorageRoot.mkdirs()
                
                // 解压游戏本体
                var actualGameDir = extractGameFile(gameFile, gameStorageRoot, callback)
                if (actualGameDir == null) {
                    withContext(Dispatchers.Main) { callback.onError("游戏解压失败") }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                var definition = GameDefinition.STARDEW_VALLEY
                
                // 安装 SMAPI
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress("安装 SMAPI...", 55)
                    }
                    
                    if (isSmapiInstaller(modLoaderFile)) {
                        installSmapiFromInstaller(modLoaderFile, actualGameDir, callback)
                    } else {
                        installSmapi(modLoaderFile, actualGameDir, callback)
                    }
                    
                    definition = GameDefinition.SMAPI
                    
                    // 在外部存储 RALauncher 目录创建模组文件夹
                    // Create mods folder in external storage RALauncher directory
                    val externalModsDir = getSmapiModsDirectory()
                    if (externalModsDir.mkdirs() || externalModsDir.exists()) {
                        Log.i(TAG, "SMAPI 模组目录已创建 / SMAPI mods directory created: ${externalModsDir.absolutePath}")
                    } else {
                        Log.w(TAG, "无法创建 SMAPI 模组目录 / Failed to create SMAPI mods directory: ${externalModsDir.absolutePath}")
                        // 回退到游戏目录下的 Mods 文件夹
                        File(actualGameDir, "Mods").mkdirs()
                    }
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress("提取图标...", 92)
                }
                val iconPath = extractIcon(actualGameDir, definition)
                
                // 创建游戏信息文件 - 使用 outputDir 作为存储根目录
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                createGameInfo(gameStorageRoot, actualGameDir, definition, iconPath)

                // 创建 GameItem 并回调
                val gameItem = createGameItem(
                    definition = definition,
                    storageRootDir = gameStorageRoot,
                    actualGameDir = actualGameDir,
                    iconPath = iconPath
                )
                val finalGameItem = if (definition == GameDefinition.SMAPI) {
                    gameItem.copy(
                        gameEnvVars = gameItem.gameEnvVars + (SMAPI_MODS_PATH_ENV_KEY to SMAPI_MODS_PATH_VALUE_TEMPLATE)
                    )
                } else {
                    gameItem
                }
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(finalGameItem)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "安装失败")
                }
            }
        }
    }
    
    private suspend fun extractGameFile(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val fileName = gameFile.name.lowercase()
        
        val result = if (fileName.endsWith(".sh")) {
            GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
                if (!isCancelled) {
                    val progressInt = (progress * 50).toInt().coerceIn(0, 50)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onProgress(msg, progressInt)
                    }
                }
            }
        } else {
            GameExtractorUtils.extractZip(
                zipFile = gameFile,
                outputDir = outputDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = (progress * 50).toInt().coerceIn(0, 50)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(msg, progressInt)
                        }
                    }
                }
            )
        }
        
        return when (result) {
            is GameExtractorUtils.ExtractResult.Error -> null
            is GameExtractorUtils.ExtractResult.Success -> result.outputDir
        }
    }
    
    private suspend fun installSmapi(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        val result = GameExtractorUtils.extractZip(
            zipFile = modLoaderFile,
            outputDir = outputDir,
            progressCallback = { msg, progress ->
                if (!isCancelled) {
                    val progressInt = 55 + (progress * 30).toInt().coerceIn(0, 30)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onProgress("安装 SMAPI: $msg", progressInt)
                    }
                }
            }
        )
        
        when (result) {
            is GameExtractorUtils.ExtractResult.Error -> throw Exception(result.message)
            is GameExtractorUtils.ExtractResult.Success -> {
                withContext(Dispatchers.Main) { callback.onProgress("应用 MonoMod 补丁...", 86) }
                installMonoMod(outputDir)
                
                withContext(Dispatchers.Main) { callback.onProgress("修补 ARM64 架构...", 88) }
                patchDllsToArm64(outputDir)
                
                withContext(Dispatchers.Main) { callback.onProgress("修补配置文件...", 90) }
                patchJsonConfigs(outputDir)
            }
        }
    }
    
    private suspend fun installSmapiFromInstaller(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        val tempDir = File(outputDir, "_smapi_temp")
        tempDir.mkdirs()
        
        try {
            val result = GameExtractorUtils.extractZip(
                zipFile = modLoaderFile,
                outputDir = tempDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = 55 + (progress * 20).toInt().coerceIn(0, 20)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress("解压 SMAPI: $msg", progressInt)
                        }
                    }
                }
            )
            
            when (result) {
                is GameExtractorUtils.ExtractResult.Error -> throw Exception(result.message)
                is GameExtractorUtils.ExtractResult.Success -> { /* 继续 */ }
            }
            
            withContext(Dispatchers.Main) { callback.onProgress("处理 SMAPI 文件...", 75) }
            processInstallerFiles(tempDir, outputDir, callback)
            
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    private suspend fun processInstallerFiles(tempDir: File, outputDir: File, callback: InstallCallback) {
        val installDat = findInstallDat(tempDir)
        
        if (installDat != null && installDat.exists()) {
            withContext(Dispatchers.Main) { callback.onProgress("解压 SMAPI 核心文件...", 80) }
            
            val datResult = GameExtractorUtils.extractZip(
                zipFile = installDat,
                outputDir = outputDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = 80 + (progress * 10).toInt().coerceIn(0, 10)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress("安装 SMAPI: $msg", progressInt)
                        }
                    }
                }
            )
            
            when (datResult) {
                is GameExtractorUtils.ExtractResult.Error -> 
                    throw Exception("解压 install.dat 失败: ${datResult.message}")
                is GameExtractorUtils.ExtractResult.Success -> { /* 继续 */ }
            }
        } else {
            withContext(Dispatchers.Main) { callback.onProgress("复制 SMAPI 文件...", 80) }
            copyInstallerFiles(tempDir, outputDir)
        }
        
        // 复制 deps.json
        val gameDepsJson = File(outputDir, "Stardew Valley.deps.json")
        val smapiDepsJson = File(outputDir, "StardewModdingAPI.deps.json")
        if (gameDepsJson.exists() && !smapiDepsJson.exists()) {
            withContext(Dispatchers.Main) { callback.onProgress("配置 SMAPI...", 88) }
            gameDepsJson.copyTo(smapiDepsJson, overwrite = true)
        }
        
        withContext(Dispatchers.Main) { callback.onProgress("应用 MonoMod 补丁...", 89) }
        installMonoMod(outputDir)
        
        withContext(Dispatchers.Main) { callback.onProgress("修补 ARM64 架构...", 90) }
        patchDllsToArm64(outputDir)
        
        withContext(Dispatchers.Main) { callback.onProgress("修补配置文件...", 93) }
        patchJsonConfigs(outputDir)
    }
    
    private fun copyInstallerFiles(tempDir: File, outputDir: File) {
        tempDir.walkTopDown().forEach { file ->
            if (isCancelled) return
            
            val relativePath = file.relativeTo(tempDir).path
            if (relativePath.contains("internal/windows") || 
                relativePath.contains("internal/macOS") ||
                file.name.lowercase() in listOf("smapi.installer.dll", "smapi.installer.exe")) {
                return@forEach
            }
            
            when {
                file.extension.lowercase() == "dat" -> {
                    try {
                        // .dat 文件是 zip 格式，直接用 ZipInputStream 解压
                        java.util.zip.ZipInputStream(java.io.FileInputStream(file)).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val targetFile = File(outputDir, entry.name)
                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    targetFile.outputStream().use { out -> zis.copyTo(out) }
                                }
                                entry = zis.nextEntry
                            }
                        }
                    } catch (e: Exception) {
                        file.copyTo(File(outputDir, file.name), overwrite = true)
                    }
                }
                file.name.lowercase() == "stardewmoddingapi.dll" -> {
                    file.copyTo(File(outputDir, file.name), overwrite = true)
                }
                file.extension.lowercase() in listOf("dll", "config", "json") && 
                !file.name.lowercase().contains("smapi.installer") -> {
                    file.copyTo(File(outputDir, file.name), overwrite = true)
                }
            }
        }
    }
    
    private fun findInstallDat(tempDir: File): File? {
        val linuxDat = File(tempDir, "internal/linux/install.dat")
        if (linuxDat.exists()) return linuxDat
        
        return tempDir.walkTopDown().firstOrNull { it.name.lowercase() == "install.dat" }
    }
    
    // ==================== ARM64 修补逻辑 ====================
    
    private fun patchDllsToArm64(gameDir: File) {
        val coreDlls = listOf(
            "Stardew Valley.dll", "MonoGame.Framework.dll", "xTile.dll",
            "StardewValley.GameData.dll", "BmFont.dll", "Lidgren.Network.dll",
            "Steamworks.NET.dll", "StardewModdingAPI.dll"
        )
        
        coreDlls.forEach { dllName ->
            val dllFile = File(gameDir, dllName)
            if (dllFile.exists()) patchPeArchitecture(dllFile)
        }
        
        listOf("Mods", "smapi-internal").forEach { subDir ->
            File(gameDir, subDir).takeIf { it.exists() && it.isDirectory }
                ?.walkTopDown()
                ?.filter { it.isFile && it.extension.lowercase() == "dll" }
                ?.forEach { patchPeArchitecture(it) }
        }
    }
    
    private fun patchPeArchitecture(file: File) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0x85)
                val archByte = raf.readByte().toInt() and 0xFF
                if (archByte == 0x86) {
                    raf.seek(0x85)
                    raf.writeByte(0xAA)
                }
            }
        } catch (e: Exception) { /* 忽略 */ }
    }
    
    // ==================== JSON 配置修补 ====================
    
    private fun patchJsonConfigs(gameDir: File) {
        gameDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".deps.json") }
            .forEach { patchDepsJson(it) }
        
        gameDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".runtimeconfig.json") }
            .forEach { patchRuntimeConfigJson(it) }
    }
    
    private fun patchDepsJson(file: File) {
        try {
            var content = file.readText()
            content = content.replace(Regex("/linux-x64"), "")
            content = content.replace(Regex("/win-x64"), "")
            content = content.replace(Regex("/osx-x64"), "")
            content = content.replace(
                Regex("runtimepack\\.Microsoft\\.NETCore\\.App\\.Runtime\\.(linux|win|osx)-x64"),
                "runtimepack.Microsoft.NETCore.App.Runtime"
            )
            file.writeText(content)
        } catch (e: Exception) { /* 忽略 */ }
    }
    
    private fun patchRuntimeConfigJson(file: File) {
        try {
            val content = file.readText()
            
            if (content.contains("includedFrameworks")) {
                val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(content)
                val versionMatch = Regex("\"includedFrameworks\"[^\\]]*\"version\"\\s*:\\s*\"([^\"]+)\"").find(content)
                
                if (nameMatch != null && versionMatch != null) {
                    val newContent = """
{
  "runtimeOptions": {
    "tfm": "net6.0",
    "framework": {
      "name": "${nameMatch.groupValues[1]}",
      "version": "${versionMatch.groupValues[1]}"
    },
    "rollForward": "latestMajor",
    "configProperties": {
      "System.Reflection.Metadata.MetadataUpdater.IsSupported": false,
      "System.Runtime.TieredCompilation": false
    }
  }
}
""".trimIndent()
                    file.writeText(newContent)
                }
            } else if (!content.contains("rollForward")) {
                val newContent = content.replace(
                    Regex("(\"framework\"\\s*:\\s*\\{[^}]+\\})"),
                    "$1,\n    \"rollForward\": \"latestMajor\""
                )
                if (newContent != content) file.writeText(newContent)
            }
        } catch (e: Exception) { /* 忽略 */ }
    }
}
