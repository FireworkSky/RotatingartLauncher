package com.app.ralaunch.installer.plugins

import android.util.Log
import com.app.ralaunch.RaLaunchApplication
import com.app.ralib.icon.IconExtractor
import com.app.ralaunch.core.AssemblyPatcher
import com.app.ralaunch.installer.GameDetectResult
import com.app.ralaunch.installer.GameExtractorUtils
import com.app.ralaunch.installer.GameInstallPlugin
import com.app.ralaunch.installer.InstallCallback
import com.app.ralaunch.installer.ModLoaderDetectResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipInputStream

/**
 * Stardew Valley / SMAPI 安装插件
 */
class SmapiInstallPlugin : GameInstallPlugin {
    
    override val pluginId = "smapi"
    override val displayName = "Stardew Valley / SMAPI"
    override val supportedGameTypes = listOf("stardew_valley", "smapi")
    
    private var installJob: Job? = null
    private var isCancelled = false
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        // 检测 Stardew Valley GOG .sh 文件
        if (fileName.endsWith(".sh") && (fileName.contains("stardew") || fileName.contains("valley"))) {
            return GameDetectResult(
                gameName = "Stardew Valley",
                gameType = "stardew_valley",
                launchTarget = "Stardew Valley.exe"
            )
        }
        
        // 检测 Stardew Valley ZIP
        if (fileName.endsWith(".zip") && (fileName.contains("stardew") || fileName.contains("valley"))) {
            return GameDetectResult(
                gameName = "Stardew Valley",
                gameType = "stardew_valley",
                launchTarget = "Stardew Valley.exe"
            )
        }
        
        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        val fileName = modLoaderFile.name.lowercase()
        
        // 检测 SMAPI
        if (fileName.contains("smapi") && fileName.endsWith(".zip")) {
            // 检查是否是 SMAPI Installer（包含 .dat 文件）
            var isInstaller = false
            
            try {
                ZipInputStream(FileInputStream(modLoaderFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (entryName.endsWith(".dat")) {
                            isInstaller = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                // 忽略错误
            }
            
            return ModLoaderDetectResult(
                name = "SMAPI",
                type = if (isInstaller) "smapi_installer" else "smapi",
                launchTarget = "StardewModdingAPI.dll"
            )
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
                
                // 实际的游戏目录（GOG 解压可能创建子目录）
                var actualGameDir = outputDir
                
                // 使用工具类解压游戏本体
                if (gameFile.name.lowercase().endsWith(".sh")) {
                    val result = GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
                        if (!isCancelled) {
                            val progressInt = (progress * 50).toInt().coerceIn(0, 50)
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onProgress(msg, progressInt)
                            }
                        }
                    }
                    
                    when (result) {
                        is GameExtractorUtils.ExtractResult.Error -> {
                            withContext(Dispatchers.Main) {
                                callback.onError(result.message)
                            }
                            return@launch
                        }
                        is GameExtractorUtils.ExtractResult.Success -> {
                            // 使用返回的实际游戏目录
                            actualGameDir = result.outputDir
                        }
                    }
                } else if (gameFile.name.lowercase().endsWith(".zip")) {
                    val result = GameExtractorUtils.extractZip(
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
                    
                    when (result) {
                        is GameExtractorUtils.ExtractResult.Error -> {
                            withContext(Dispatchers.Main) {
                                callback.onError(result.message)
                            }
                            return@launch
                        }
                        is GameExtractorUtils.ExtractResult.Success -> {
                            actualGameDir = result.outputDir
                        }
                    }
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                var finalGameName = "Stardew Valley"
                var launchTarget = "Stardew Valley.exe"
                var iconPath: String? = null
                
                // 安装 SMAPI
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress("安装 SMAPI...", 55)
                    }
                    
                    val modLoaderInfo = detectModLoader(modLoaderFile)
                    if (modLoaderInfo?.type == "smapi_installer") {
                        installSmapiFromInstaller(modLoaderFile, actualGameDir, callback)
                    } else {
                        installSmapi(modLoaderFile, actualGameDir, callback)
                    }
                    
                    finalGameName = "SMAPI"
                    launchTarget = "StardewModdingAPI.dll"
                    
                    // 创建 Mods 目录
                    File(actualGameDir, "Mods").mkdirs()
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress("提取图标...", 92)
                }
                iconPath = extractIcon(actualGameDir, launchTarget)
                
                // 创建游戏信息文件
                withContext(Dispatchers.Main) {
                    callback.onProgress("完成安装...", 98)
                }
                
                createGameInfo(actualGameDir, finalGameName, launchTarget, iconPath)
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("安装完成!", 100)
                    callback.onComplete(
                        gamePath = actualGameDir.absolutePath,
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
            is GameExtractorUtils.ExtractResult.Error -> {
                throw Exception(result.message)
            }
            is GameExtractorUtils.ExtractResult.Success -> {
                // 成功后应用 MonoMod 补丁和 ARM64 修补
                withContext(Dispatchers.Main) {
                    callback.onProgress("应用 MonoMod 补丁...", 86)
                }
                applyMonoModPatches(outputDir)
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("修补 ARM64 架构...", 88)
                }
                patchDllsToArm64(outputDir)
                
                withContext(Dispatchers.Main) {
                    callback.onProgress("修补配置文件...", 90)
                }
                patchJsonConfigs(outputDir)
            }
        }
    }
    
    private suspend fun installSmapiFromInstaller(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        // 创建临时目录
        val tempDir = File(outputDir, "_smapi_temp")
        tempDir.mkdirs()
        
        try {
            // 解压 SMAPI Installer
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
                is GameExtractorUtils.ExtractResult.Error -> {
                    throw Exception(result.message)
                }
                is GameExtractorUtils.ExtractResult.Success -> {
                    // 继续处理
                }
            }
            
            withContext(Dispatchers.Main) {
                callback.onProgress("处理 SMAPI 文件...", 75)
            }
            
            // 处理 .dat 文件和 DLL
            processInstallerFiles(tempDir, outputDir, callback)
            
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    private suspend fun processInstallerFiles(tempDir: File, outputDir: File, callback: InstallCallback) {
        // 查找 install.dat 文件 (优先 linux 目录，因为 Android 兼容性更好)
        val installDat = findInstallDat(tempDir)
        
        if (installDat != null && installDat.exists()) {
            withContext(Dispatchers.Main) {
                callback.onProgress("解压 SMAPI 核心文件...", 80)
            }
            
            // install.dat 实际上是一个 ZIP 文件，包含 SMAPI 需要安装到游戏目录的所有文件
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
                is GameExtractorUtils.ExtractResult.Error -> {
                    throw Exception("解压 install.dat 失败: ${datResult.message}")
                }
                is GameExtractorUtils.ExtractResult.Success -> {
                    // 成功解压
                }
            }
        } else {
            // 如果没有找到 install.dat，尝试直接复制 SMAPI 相关文件
            withContext(Dispatchers.Main) {
                callback.onProgress("复制 SMAPI 文件...", 80)
            }
            
            tempDir.walkTopDown().forEach { file ->
                if (isCancelled) return
                
                val relativePath = file.relativeTo(tempDir).path
                // 跳过安装器自身的文件
                if (relativePath.contains("internal/windows") || 
                    relativePath.contains("internal/macOS") ||
                    file.name.lowercase() == "smapi.installer.dll" ||
                    file.name.lowercase() == "smapi.installer.exe") {
                    return@forEach
                }
                
                when {
                    file.extension.lowercase() == "dat" -> {
                        // 尝试解压 .dat 文件
                        try {
                            GameExtractorUtils.extractZip(
                                zipFile = file,
                                outputDir = outputDir,
                                progressCallback = { _, _ -> }
                            )
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
        
        // 复制 Stardew Valley.deps.json 为 StardewModdingAPI.deps.json (SMAPI 需要这个)
        val gameDepsJson = File(outputDir, "Stardew Valley.deps.json")
        val smapiDepsJson = File(outputDir, "StardewModdingAPI.deps.json")
        if (gameDepsJson.exists() && !smapiDepsJson.exists()) {
            withContext(Dispatchers.Main) {
                callback.onProgress("配置 SMAPI...", 88)
            }
            gameDepsJson.copyTo(smapiDepsJson, overwrite = true)
        }
        
        // 应用 MonoMod 补丁 - 从 assets 中提取的 MonoMod.zip 覆盖游戏 DLL
        withContext(Dispatchers.Main) {
            callback.onProgress("应用 MonoMod 补丁...", 89)
        }
        applyMonoModPatches(outputDir)
        
        // 将 x64 DLL 修补为 ARM64 架构 (ValleyCore 补丁)
        withContext(Dispatchers.Main) {
            callback.onProgress("修补 ARM64 架构...", 90)
        }
        patchDllsToArm64(outputDir)
        
        // 修补 JSON 配置文件 (移除 linux-x64 架构限定, 修正 framework 配置)
        withContext(Dispatchers.Main) {
            callback.onProgress("修补配置文件...", 93)
        }
        patchJsonConfigs(outputDir)
    }
    
    /**
     * 修补 JSON 配置文件
     * 1. 修补 .deps.json - 移除 linux-x64 架构限定
     * 2. 修补 .runtimeconfig.json - 将 includedFrameworks 改为 framework
     */
    private fun patchJsonConfigs(gameDir: File) {
        // 修补所有 .deps.json 文件
        gameDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".deps.json") }
            .forEach { patchDepsJson(it) }
        
        // 修补所有 .runtimeconfig.json 文件
        gameDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".runtimeconfig.json") }
            .forEach { patchRuntimeConfigJson(it) }
    }
    
    /**
     * 修补 .deps.json 文件
     * 移除架构限定，将 linux-x64 改为无架构
     */
    private fun patchDepsJson(file: File) {
        try {
            var content = file.readText()
            
            // 替换 runtimeTarget 中的架构限定
            // ".NETCoreApp,Version=v6.0/linux-x64" -> ".NETCoreApp,Version=v6.0"
            content = content.replace(Regex("/linux-x64"), "")
            content = content.replace(Regex("/win-x64"), "")
            content = content.replace(Regex("/osx-x64"), "")
            
            // 替换 targets 中的架构键名
            // ".NETCoreApp,Version=v6.0/linux-x64" -> ".NETCoreApp,Version=v6.0"
            
            // 替换 runtimepack 引用
            // "runtimepack.Microsoft.NETCore.App.Runtime.linux-x64" -> "runtimepack.Microsoft.NETCore.App.Runtime"
            content = content.replace(Regex("runtimepack\\.Microsoft\\.NETCore\\.App\\.Runtime\\.linux-x64"), 
                "runtimepack.Microsoft.NETCore.App.Runtime")
            content = content.replace(Regex("runtimepack\\.Microsoft\\.NETCore\\.App\\.Runtime\\.win-x64"), 
                "runtimepack.Microsoft.NETCore.App.Runtime")
            
            file.writeText(content)
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    /**
     * 修补 .runtimeconfig.json 文件
     * 1. 将 includedFrameworks 改为 framework
     * 2. 添加 rollForward: "latestMajor" 允许使用更高版本的 .NET
     */
    private fun patchRuntimeConfigJson(file: File) {
        try {
            val content = file.readText()
            
            // 如果使用 includedFrameworks，需要转换为 framework
            if (content.contains("includedFrameworks")) {
                // 简单的 JSON 转换
                // 从:
                // "includedFrameworks": [{ "name": "...", "version": "..." }]
                // 到:
                // "framework": { "name": "...", "version": "..." }, "rollForward": "latestMajor"
                
                // 使用正则提取 name 和 version
                val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(content)
                val versionMatch = Regex("\"includedFrameworks\"[^\\]]*\"version\"\\s*:\\s*\"([^\"]+)\"").find(content)
                
                if (nameMatch != null && versionMatch != null) {
                    val frameworkName = nameMatch.groupValues[1]
                    val frameworkVersion = versionMatch.groupValues[1]
                    
                    // 构建新的 runtimeconfig.json
                    val newContent = """
{
  "runtimeOptions": {
    "tfm": "net6.0",
    "framework": {
      "name": "$frameworkName",
      "version": "$frameworkVersion"
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
                // 如果没有 rollForward，添加它
                val newContent = content.replace(
                    Regex("(\"framework\"\\s*:\\s*\\{[^}]+\\})"),
                    "$1,\n    \"rollForward\": \"latestMajor\""
                )
                if (newContent != content) {
                    file.writeText(newContent)
                }
            }
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    /**
     * 应用 MonoMod 补丁
     * 强制使用新版本的 MonoMod 覆盖游戏自带的旧版本
     */
    private fun applyMonoModPatches(gameDir: File) {
        try {
            val context = RaLaunchApplication.getAppContext()
            
            // 解压 MonoMod 到目录
            val extractSuccess = AssemblyPatcher.extractMonoMod(context)
            if (!extractSuccess) {
                Log.w("SmapiInstallPlugin", "MonoMod 解压失败")
                return
            }
            
            // 从 MonoMod 目录应用补丁到游戏目录
            val patchedCount = AssemblyPatcher.applyMonoModPatches(
                context, gameDir.absolutePath, true)
            
            if (patchedCount >= 0) {
                Log.i("SmapiInstallPlugin", "MonoMod 已应用，替换了 $patchedCount 个文件")
            } else {
                Log.w("SmapiInstallPlugin", "MonoMod 应用失败")
            }
        } catch (e: Exception) {
            Log.e("SmapiInstallPlugin", "MonoMod 安装异常", e)
        }
    }
    
    /**
     * 将目录中的 x64 DLL 修补为 ARM64 架构
     * 基于 ValleyCore 的 patch.sh 逻辑
     * 
     * PE 文件头在偏移 0x84-0x85 存储机器架构：
     * - 0x8664 = x86-64 (AMD64)
     * - 0xAA64 = ARM64
     * 
     * 脚本修改偏移 0x85 的字节从 0x86 改为 0xAA
     */
    private fun patchDllsToArm64(gameDir: File) {
        // 需要修补的核心 DLL 列表
        val coreDlls = listOf(
            "Stardew Valley.dll",
            "MonoGame.Framework.dll",
            "xTile.dll",
            "StardewValley.GameData.dll",
            "BmFont.dll",
            "Lidgren.Network.dll",
            "Steamworks.NET.dll",
            "StardewModdingAPI.dll"
        )
        
        // 修补核心 DLL
        for (dllName in coreDlls) {
            val dllFile = File(gameDir, dllName)
            if (dllFile.exists()) {
                patchPeArchitecture(dllFile)
            }
        }
        
        // 修补 Mods 目录下的所有 DLL
        val modsDir = File(gameDir, "Mods")
        if (modsDir.exists() && modsDir.isDirectory) {
            modsDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() == "dll" }
                .forEach { patchPeArchitecture(it) }
        }
        
        // 修补 smapi-internal 目录下的 DLL
        val smapiInternalDir = File(gameDir, "smapi-internal")
        if (smapiInternalDir.exists() && smapiInternalDir.isDirectory) {
            smapiInternalDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() == "dll" }
                .forEach { patchPeArchitecture(it) }
        }
    }
    
    /**
     * 修补单个 PE 文件的架构头
     * 将 x64 (0x86) 修改为 ARM64 (0xAA)
     */
    private fun patchPeArchitecture(file: File) {
        try {
            val archPos = 0x85  // 架构字节位置 (133 decimal)
            
            RandomAccessFile(file, "rw").use { raf ->
                // 读取架构字节
                raf.seek(archPos.toLong())
                val archByte = raf.readByte().toInt() and 0xFF
                
                // 如果是 x64 (0x86)，修改为 ARM64 (0xAA)
                if (archByte == 0x86) {
                    raf.seek(archPos.toLong())
                    raf.writeByte(0xAA)
                }
                // 如果已经是 ARM64 (0xAA) 或其他架构，跳过
            }
        } catch (e: Exception) {
            // 忽略无法修补的文件（可能不是有效的 PE 文件）
        }
    }
    
    /**
     * 查找 install.dat 文件
     * 优先查找 linux 目录（Android 兼容性更好）
     */
    private fun findInstallDat(tempDir: File): File? {
        // 优先级: linux > macOS > windows
        val searchPaths = listOf(
            "internal/linux/install.dat"
          
        )
        
        for (path in searchPaths) {
            val datFile = File(tempDir, path)
            if (datFile.exists()) {
                return datFile
            }
        }
        
        // 递归查找任何 install.dat 文件
        return tempDir.walkTopDown().firstOrNull { 
            it.name.lowercase() == "install.dat" 
        }
    }
    
    /**
     * 从 DLL/EXE 文件提取图标
     */
    private fun extractIcon(outputDir: File, launchTarget: String): String? {
        try {
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
                
                // 对于 SMAPI，优先使用 Stardew Valley.exe
                val stardewExe = File(outputDir, "Stardew Valley.exe")
                if (stardewExe.exists()) {
                    iconSourceFile = stardewExe
                }
            }
            
            // 如果目标文件不存在，尝试搜索游戏目录
            if (!iconSourceFile.exists()) {
                val exeFiles = outputDir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() == "exe" }
                    .toList()
                
                if (exeFiles.isNotEmpty()) {
                    // 优先选择名称包含游戏名的
                    iconSourceFile = exeFiles.find { 
                        it.name.lowercase().contains("stardew") || 
                        it.name.lowercase().contains("valley")
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
  "game_type": "${if (gameName == "SMAPI") "smapi" else "stardew_valley"}",
  "launch_target": "$launchTarget",
  "install_time": "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}"$iconField
}
        """.trimIndent()
        infoFile.writeText(json)
    }
}
