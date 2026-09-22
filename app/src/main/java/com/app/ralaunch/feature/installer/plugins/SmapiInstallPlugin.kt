package com.app.ralaunch.feature.installer.plugins

import android.os.Environment
import timber.log.Timber
import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.core.extractor.GogShFileExtractor
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.feature.installer.*
import com.app.ralaunch.feature.installer.GameInstallPlugin.Event
import com.app.ralaunch.feature.installer.GameInstallPlugin.Result
import java.io.File
import java.io.RandomAccessFile
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream

/**
 * Stardew Valley / SMAPI 安装插件
 */
class SmapiInstallPlugin : BaseInstallPlugin() {

    companion object {
        private const val SMAPI_MODS_PATH_ENV_KEY = "SMAPI_MODS_PATH"
        private const val SMAPI_MODS_PATH_VALUE_TEMPLATE = "{XDG_DATA_HOME}/Stardew Valley/Mods"
        private val SMAPI_VERSION_PATTERN = Regex("""(?i)^smapi[ _-]?v?(\d+(?:\.\d+)+)""")

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
    override val displayName: String
        get() = Strings.installer.smapi.name
    override val supportedGames = listOf(GameDefinition.STARDEW_VALLEY, GameDefinition.SMAPI)

    override fun detectGame(gameFile: GameFile): GameDetectResult? {
        // GOG 安装器：game_data.zip 内 gameinfo 首行为游戏名
        val gogInstaller = gameFile.container as? GameFileInspector.Container.GogInstaller
        if (gogInstaller != null) {
            return if (gogInstaller.gameInfo.id.equals("Stardew Valley", ignoreCase = true)) {
                GameDetectResult(GameDefinition.STARDEW_VALLEY, gogInstaller.gameInfo.version.orEmpty())
            } else {
                null
            }
        }

        // ZIP：包含 Stardew Valley 主程序
        val zip = gameFile.container as? GameFileInspector.Container.Zip ?: return null
        return if (zip.hasEntryNamed("Stardew Valley.exe")) {
            GameDetectResult(GameDefinition.STARDEW_VALLEY)
        } else {
            null
        }
    }

    override fun detectModLoader(modLoaderFile: GameFile): ModLoaderDetectResult? {
        val zip = modLoaderFile.container as? GameFileInspector.Container.Zip ?: return null

        // 安装器格式：internal/<平台>/install.dat 或 SMAPI.Installer.dll
        val isInstallerFormat = zip.findEntryNamed("install.dat")?.contains("/internal/") == true ||
            zip.hasEntryNamed("SMAPI.Installer.dll")
        // 已安装格式：StardewModdingAPI 主程序
        if (isInstallerFormat || zip.hasEntryNamed("StardewModdingAPI.dll")) {
            return ModLoaderDetectResult(GameDefinition.SMAPI, detectSmapiVersion(zip))
        }

        return null
    }

    /**
     * 检测 SMAPI 是否为安装器格式（包含 internal/&lt;平台&gt;/install.dat）
     */
    private fun isSmapiInstaller(modLoaderFile: GameFile): Boolean {
        val zip = modLoaderFile.container as? GameFileInspector.Container.Zip ?: return false
        return zip.findEntryNamed("install.dat")?.contains("/internal/") == true
    }

    /** 安装包根目录形如 "SMAPI 4.5.2 installer/"，从中提取版本号 */
    private fun detectSmapiVersion(zip: GameFileInspector.Container.Zip): String {
        return zip.entryPaths.firstNotNullOfOrNull { path ->
            SMAPI_VERSION_PATTERN.find(path.substringBefore('/'))?.groupValues?.get(1)
        }.orEmpty()
    }

    override suspend fun performInstall(
        gameFile: GameFile?,
        modLoaderFile: GameFile?,
        callback: ((Event) -> Unit)?
    ): Result {
        callback?.invoke(
            Event.Progress(
                Strings.installer.starting,
                0
            )
        )

        val gameFile = gameFile ?: throw Exception(Strings.installer.extractGameFailed)

        // 创建存储根目录
        val gameStorageRoot = createStorageRoot(gameFile, modLoaderFile)

        // 解压游戏本体（GOG 安装器或 ZIP，按容器特征分派）
        val actualGameDir: File? = when {
            gameFile.container is GameFileInspector.Container.GogInstaller ->
                when (
                    val result = GogShFileExtractor.builder()
                        .from(gameFile.source)
                        .to(gameStorageRoot.toPath())
                        .callback { event ->
                            if (event is GogShFileExtractor.Event.Progress && !isCancelled) {
                                val progressInt = (event.progress * 50).toInt().coerceIn(0, 50)
                                callback?.invoke(Event.Progress(event.message, progressInt))
                            }
                        }
                        .build()
                        .extract()
                ) {
                    is GogShFileExtractor.Result.Success -> result.gamePath.toFile()
                    is GogShFileExtractor.Result.Failure -> null
                }

            gameFile.container is GameFileInspector.Container.Zip ->
                when (
                    val result = ArchiveExtractor.builder()
                        .from(gameFile.source)
                        .to(gameStorageRoot.toPath())
                        .callback { event ->
                            if (event is ArchiveExtractor.Event.Progress && !isCancelled) {
                                val progressInt = (event.progress * 50).toInt().coerceIn(0, 50)
                                callback?.invoke(Event.Progress(event.message, progressInt))
                            }
                        }
                        .build()
                        .extract()
                ) {
                    is ArchiveExtractor.Result.Success -> result.destinationPath.toFile()
                    is ArchiveExtractor.Result.Failure -> null
                }

            else -> null
        }

        if (actualGameDir == null) {
            throw Exception(
                Strings.installer.extractGameFailed
            )
        }

        if (isCancelled) return cancelledInstall(callback)

        var definition = GameDefinition.STARDEW_VALLEY

        // 安装 SMAPI
        if (modLoaderFile != null) {
            callback?.invoke(
                Event.Progress(
                    Strings.installer.smapi.installing,
                    55
                )
            )

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
                Timber.i("SMAPI 模组目录已创建 / SMAPI mods directory created: ${externalModsDir.absolutePath}")
            } else {
                Timber.w("无法创建 SMAPI 模组目录 / Failed to create SMAPI mods directory: ${externalModsDir.absolutePath}")
                // 回退到游戏目录下的 Mods 文件夹
                File(actualGameDir, "Mods").mkdirs()
            }
        }

        if (isCancelled) return cancelledInstall(callback)

        // 提取图标
        callback?.invoke(
            Event.Progress(
                Strings.installer.extractIcon,
                92
            )
        )
        val iconPath = extractIcon(actualGameDir, definition)

        // 创建 GameItem，由基类保存并发出完成事件
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

        return finishInstall(finalGameItem, callback)
    }

    private suspend fun installSmapi(modLoaderFile: GameFile, outputDir: File, callback: ((Event) -> Unit)?) {
        when (
            val result = ArchiveExtractor.builder()
                .from(modLoaderFile.source)
                .to(outputDir.toPath())
                .callback { event ->
                    if (event is ArchiveExtractor.Event.Progress && !isCancelled) {
                        val progressInt = 55 + (event.progress * 30).toInt().coerceIn(0, 30)
                        callback?.invoke(
                            Event.Progress(
                                Strings.installer.smapi.withDetail(event.message),
                                progressInt
                            )
                        )
                    }
                }
                .build()
                .extract()
        ) {
            is ArchiveExtractor.Result.Failure -> throw Exception(result.message)
            is ArchiveExtractor.Result.Success -> {
                callback?.invoke(
                    Event.Progress(
                        Strings.installer.smapi.applyMonoModPatch,
                        86
                    )
                )
                installMonoMod(outputDir)

                callback?.invoke(
                    Event.Progress(
                        Strings.installer.smapi.patchArm64,
                        88
                    )
                )
                patchDllsToArm64(outputDir)

                callback?.invoke(
                    Event.Progress(
                        Strings.installer.smapi.patchConfig,
                        90
                    )
                )
                patchJsonConfigs(outputDir)
            }
        }
    }

    private suspend fun installSmapiFromInstaller(modLoaderFile: GameFile, outputDir: File, callback: ((Event) -> Unit)?) {
        val tempDir = File(outputDir, "_smapi_temp")
        tempDir.mkdirs()

        try {
            when (
                val result = ArchiveExtractor.builder()
                    .from(modLoaderFile.source)
                    .to(tempDir.toPath())
                    .callback { event ->
                        if (event is ArchiveExtractor.Event.Progress && !isCancelled) {
                            val progressInt = 55 + (event.progress * 20).toInt().coerceIn(0, 20)
                            callback?.invoke(
                                Event.Progress(
                                    Strings.installer.smapi.extractWithDetail(event.message),
                                    progressInt
                                )
                            )
                        }
                    }
                    .build()
                    .extract()
            ) {
                is ArchiveExtractor.Result.Failure -> throw Exception(result.message)
                is ArchiveExtractor.Result.Success -> { /* 继续 */ }
            }

            callback?.invoke(
                Event.Progress(
                    Strings.installer.smapi.processFiles,
                    75
                )
            )
            processInstallerFiles(tempDir, outputDir, callback)

        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun processInstallerFiles(tempDir: File, outputDir: File, callback: ((Event) -> Unit)?) {
        val installDat = findInstallDat(tempDir)

        if (installDat != null && installDat.exists()) {
            callback?.invoke(
                Event.Progress(
                    Strings.installer.smapi.extractCoreFiles,
                    80
                )
            )

            when (
                val datResult = ArchiveExtractor.builder()
                    .from(installDat.toPath())
                    .to(outputDir.toPath())
                    .callback { event ->
                        if (event is ArchiveExtractor.Event.Progress && !isCancelled) {
                            val progressInt = 80 + (event.progress * 10).toInt().coerceIn(0, 10)
                            callback?.invoke(
                                Event.Progress(
                                    Strings.installer.smapi.withDetail(event.message),
                                    progressInt
                                )
                            )
                        }
                    }
                    .build()
                    .extract()
            ) {
                is ArchiveExtractor.Result.Failure ->
                    throw Exception(
                        Strings.installer.smapi.installDatFailed(datResult.message)
                    )
                is ArchiveExtractor.Result.Success -> { /* 继续 */ }
            }
        } else {
            callback?.invoke(
                Event.Progress(
                    Strings.installer.smapi.copyFiles,
                    80
                )
            )
            copyInstallerFiles(tempDir, outputDir)
        }

        // 复制 deps.json
        val gameDepsJson = File(outputDir, "Stardew Valley.deps.json")
        val smapiDepsJson = File(outputDir, "StardewModdingAPI.deps.json")
        if (gameDepsJson.exists() && !smapiDepsJson.exists()) {
            callback?.invoke(
                Event.Progress(
                    Strings.installer.smapi.configure,
                    88
                )
            )
            gameDepsJson.copyTo(smapiDepsJson, overwrite = true)
        }

        callback?.invoke(
            Event.Progress(
                Strings.installer.smapi.applyMonoModPatch,
                89
            )
        )
        installMonoMod(outputDir)

        callback?.invoke(
            Event.Progress(
                Strings.installer.smapi.patchArm64,
                90
            )
        )
        patchDllsToArm64(outputDir)

        callback?.invoke(
            Event.Progress(
                Strings.installer.smapi.patchConfig,
                93
            )
        )
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
                        // .dat 文件是 zip 格式，直接解压
                        ZipArchiveInputStream(java.io.FileInputStream(file)).use { zis ->
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
