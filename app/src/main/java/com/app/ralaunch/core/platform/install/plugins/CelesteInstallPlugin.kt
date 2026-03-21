package com.app.ralaunch.core.platform.install.plugins

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.platform.runtime.GameLauncher
import com.app.ralaunch.core.platform.install.*
import com.app.ralaunch.feature.patch.data.PatchManager
import org.koin.java.KoinJavaComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class CelesteInstallPlugin : BaseInstallPlugin() {

    override val pluginId = "celeste"
    override val displayName: String
        get() = RaLaunchApp.getInstance().getString(R.string.install_plugin_display_name_celeste_everest)
    override val supportedGames = listOf(GameDefinition.CELESTE, GameDefinition.EVEREST)

    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        if (fileName.endsWith(".zip") && fileName.contains("celeste")) {
            return GameDetectResult(GameDefinition.CELESTE)
        }
        return null
    }

    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        ZipFile(modLoaderFile).use { zip ->
            val everestLibEntry = zip.getEntry("main/everest-lib")
            if (everestLibEntry != null) {
                return ModLoaderDetectResult(GameDefinition.EVEREST)
            }
        }
        return null
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
                    callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_starting), 0)
                }

                if (!gameStorageRoot.exists()) gameStorageRoot.mkdirs()

                val extractResult = GameExtractorUtils.extractZip(
                    zipFile = gameFile,
                    outputDir = gameStorageRoot,
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
                        withContext(Dispatchers.Main) { callback.onError(extractResult.message) }
                        return@launch
                    }
                    is GameExtractorUtils.ExtractResult.Success -> { }
                }

                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }

                val configFile = File(gameStorageRoot, "Celeste.runtimeconfig.json")
                if (!configFile.exists()) {
                    val jsonContent = """
                    {
                      "runtimeOptions": {
                        "tfm": "net6.0",
                        "rollForward": "LatestMajor",
                        "framework": {
                          "name": "Microsoft.NETCore.App",
                          "version": "6.0.0"
                        },
                        "configProperties": {
                          "System.GC.Server": false,
                          "System.GC.Concurrent": true,
                          "System.Runtime.TieredCompilation": true
                        }
                      }
                    }
                    """.trimIndent()
                    configFile.writeText(jsonContent)
                }

                var definition = GameDefinition.CELESTE

                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_everest), 55)
                    }
                    installEverest(modLoaderFile, gameStorageRoot, callback)
                    definition = GameDefinition.EVEREST
                }

                withContext(Dispatchers.Main) {
                    callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_extract_icon), 92)
                }
                val iconPath = extractIcon(gameStorageRoot, definition)

                withContext(Dispatchers.Main) {
                    callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_finishing), 98)
                }
                createGameInfo(gameStorageRoot, definition, iconPath)

                val gameItem = createGameItem(definition, gameStorageRoot, iconPath)

                withContext(Dispatchers.Main) {
                    callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_complete), 100)
                    callback.onComplete(gameItem)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: RaLaunchApp.getInstance().getString(R.string.install_failed))
                }
            }
        }
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
                        callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_everest_with_detail, msg), progressInt)
                    }
                }
            }
        )

        when (extractResult) {
            is GameExtractorUtils.ExtractResult.Error -> throw Exception(extractResult.message)
            is GameExtractorUtils.ExtractResult.Success -> { }
        }

        withContext(Dispatchers.Main) {
            callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_monomod), 85)
        }
        installMonoMod(outputDir)

        withContext(Dispatchers.Main) {
            callback.onProgress(RaLaunchApp.getInstance().getString(R.string.install_everest_miniinstaller), 90)
        }

        val patchManager: PatchManager? = try {
            KoinJavaComponent.getOrNull(PatchManager::class.java)
        } catch (e: Exception) { null }
        val patches = patchManager?.getPatchesByIds(listOf("com.app.ralaunch.everest.miniinstaller.fix")) ?: emptyList()

        if (patches.size != 1) {
            throw Exception(RaLaunchApp.getInstance().getString(R.string.install_everest_miniinstaller_patch_missing))
        }

        val patchResult = GameLauncher.launchDotNetAssembly(outputDir.resolve("MiniInstaller.dll").toString(), arrayOf(), patches)

        outputDir.resolve("everest-launch.txt").writeText("# Splash screen disabled by Rotating Art Launcher\n--disable-splash\n")
        outputDir.resolve("EverestXDGFlag").writeText("") 

        if (patchResult != 0) {
            throw Exception(RaLaunchApp.getInstance().getString(R.string.install_everest_miniinstaller_failed, patchResult))
        }
    }
}
