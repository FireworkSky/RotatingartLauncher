package com.app.ralaunch.feature.patch.data

import android.content.Context
import android.util.Log
import com.app.ralaunch.core.platform.install.extractors.BasicSevenZipExtractor
import com.app.ralaunch.core.platform.install.extractors.ExtractorCollection
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.IOException

class PatchManager @JvmOverloads constructor(
    customStoragePath: String? = null,
    installPatchesImmediately: Boolean = false
) {
    private var patchStorageDir: File? = null
    private var configFile: File? = null
    private var config: PatchManagerConfig = PatchManagerConfig()

    init {
        try {
            val dir = getDefaultPatchStorageDirectories(customStoragePath)
            patchStorageDir = dir
            
            if (!dir.exists() || !dir.isDirectory) {
                dir.deleteRecursively()
                dir.mkdirs()
            }
            
            val cfgFile = File(dir, PatchManagerConfig.CONFIG_FILE_NAME)
            configFile = cfgFile
            config = loadConfig(cfgFile)

            cleanLegacySharedDlls(dir)

            if (installPatchesImmediately) {
                installBuiltInPatches(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "FATAL ERROR during PatchManager initialization: ${e.message}", e)
        }
    }

    fun getApplicableAndEnabledPatches(gameId: String, gameAsmPath: File): ArrayList<Patch> {
        return installedPatches
            .filter { isPatchApplicableToGame(it, gameId) }
            .filter { config.isPatchEnabled(gameAsmPath, it.manifest.id) }
            .sortedByDescending { it.manifest.priority }
            .toCollection(ArrayList())
    }

    fun getApplicablePatches(gameId: String): ArrayList<Patch> {
        return installedPatches
            .filter { isPatchApplicableToGame(it, gameId) }
            .sortedByDescending { it.manifest.priority }
            .toCollection(ArrayList())
    }

    private fun isPatchApplicableToGame(patch: Patch, gameId: String): Boolean {
        val targetGames = patch.manifest.targetGames
        if (targetGames.isNullOrEmpty()) return false
        return targetGames.contains("*") || targetGames.contains(gameId)
    }

    fun getEnabledPatches(gameAsmPath: File): ArrayList<Patch> {
        return installedPatches
            .filter { config.isPatchEnabled(gameAsmPath, it.manifest.id) }
            .sortedByDescending { it.manifest.priority }
            .toCollection(ArrayList())
    }

    fun getEnabledPatchIds(gameAsmPath: File): ArrayList<String> {
        return config.getEnabledPatchIds(gameAsmPath)
    }

    val installedPatches: ArrayList<Patch>
        get() {
            val dir = patchStorageDir ?: return ArrayList()
            return try {
                dir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.mapNotNull { Patch.fromPatchPath(it) }
                    ?.toCollection(ArrayList())
                    ?: ArrayList()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading installed patches: ${e.message}")
                ArrayList()
            }
        }

    fun getPatchesByIds(patchIds: List<String>): ArrayList<Patch> {
        return installedPatches
            .filter { patchIds.contains(it.manifest.id) }
            .sortedBy { patchIds.indexOf(it.manifest.id) }
            .toCollection(ArrayList())
    }

    fun installPatch(patchZipFile: File): Boolean {
        val storageDir = patchStorageDir ?: return false
        
        if (!patchZipFile.exists() || !patchZipFile.isFile) {
            Log.w(TAG, "Patch install failed: file not found: $patchZipFile")
            return false
        }

        val manifest = PatchManifest.fromZip(patchZipFile)
        if (manifest == null) {
            Log.w(TAG, "Patch install failed: cannot read manifest: $patchZipFile")
            return false
        }

        val patchDir = File(storageDir, manifest.id)

        if (patchDir.exists()) {
            Log.i(TAG, "Patch exists, reinstalling: ${manifest.id}")
            if (!FileUtils.deleteDirectoryRecursively(patchDir)) {
                Log.w(TAG, "Failed to delete old patch dir")
                return false
            }
        } else {
            Log.i(TAG, "Installing new patch: ${manifest.id}")
        }

        Log.i(TAG, "Extracting patch...")
        return try {
            BasicSevenZipExtractor(
                patchZipFile,
                File(""),
                patchDir,
                object : ExtractorCollection.ExtractionListener {
                    override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {}
                    override fun onComplete(message: String, state: HashMap<String, Any?>?) {}
                    override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                        Log.e(TAG, "Extraction error: $message", ex)
                    }
                }
            ).extract()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract patch", e)
            false
        }
    }

    fun setPatchEnabled(gameAsmPath: File, patchId: String, enabled: Boolean) {
        config.setPatchEnabled(gameAsmPath, patchId, enabled)
        saveConfig()
    }

    fun isPatchEnabled(gameAsmPath: File, patchId: String): Boolean {
        return config.isPatchEnabled(gameAsmPath, patchId)
    }

    private fun loadConfig(cfgFile: File): PatchManagerConfig {
        return try {
            val loadedConfig = PatchManagerConfig.fromJson(cfgFile)
            if (loadedConfig == null) {
                Log.i(TAG, "Config not found, creating new")
                val newCfg = PatchManagerConfig()
                newCfg.saveToJson(cfgFile)
                newCfg
            } else {
                Log.i(TAG, "Config loaded")
                loadedConfig
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config: ${e.message}")
            PatchManagerConfig() 
        }
    }

    private fun saveConfig() {
        val cfgFile = configFile ?: return
        if (!config.saveToJson(cfgFile)) {
            Log.w(TAG, "Failed to save config")
        }
    }

    private fun cleanLegacySharedDlls(dir: File) {
        for (dllName in LEGACY_SHARED_DLLS) {
            val dllFile = File(dir, dllName)
            try {
                if (dllFile.exists()) {
                    dllFile.delete()
                    Log.i(TAG, "Cleaned legacy DLL: $dllName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean $dllName: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PatchManager"
        private const val IS_DEFAULT_PATCH_STORAGE_DIR_EXTERNAL = true
        private const val PATCH_STORAGE_DIR = "patches"

        private val LEGACY_SHARED_DLLS = arrayOf(
            "0Harmony.dll",
            "MonoMod.Common.dll",
            "Mono.Cecil.dll"
        )

        private fun getDefaultPatchStorageDirectories(customStoragePath: String?): File {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            val baseDir = customStoragePath ?: if (IS_DEFAULT_PATCH_STORAGE_DIR_EXTERNAL) {
                val extDir = context.getExternalFilesDir(null)
                extDir?.absolutePath ?: context.filesDir.absolutePath
            } else {
                context.filesDir.absolutePath
            }
            
            // CHANGED: Use absoluteFile instead of canonicalFile to prevent IOException crash on Android 7
            return File(baseDir, PATCH_STORAGE_DIR).absoluteFile
        }

        @JvmStatic
        fun installBuiltInPatches(patchManager: PatchManager) {
            installBuiltInPatches(patchManager, false)
        }

        @JvmStatic
        fun installBuiltInPatches(patchManager: PatchManager, forceReinstall: Boolean) {
            try {
                val context: Context = KoinJavaComponent.get(Context::class.java)
                val apkFile = File(context.applicationInfo.sourceDir)

                TemporaryFileAcquirer().use { tfa ->
                    val extractedPatches = tfa.acquireTempFilePath("extracted_patches")

                    BasicSevenZipExtractor(
                        apkFile,
                        File("assets/patches"),
                        extractedPatches,
                        object : ExtractorCollection.ExtractionListener {
                            override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {}
                            override fun onComplete(message: String, state: HashMap<String, Any?>?) {}
                            override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                                Log.e(TAG, "Built-in extraction error: $message", ex)
                            }
                        }
                    ).extract()

                    val installedPatchMap = patchManager.installedPatches
                        .associateBy { it.manifest.id }

                    extractedPatches.listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".zip") }
                        ?.forEach { patchZip ->
                            try {
                                val manifest = PatchManifest.fromZip(patchZip)
                                    ?: return@forEach

                                val installedPatch = installedPatchMap[manifest.id]

                                when {
                                    forceReinstall -> {
                                        Log.i(TAG, "Force reinstalling: ${patchZip.name}")
                                        patchManager.installPatch(patchZip)
                                    }
                                    installedPatch == null -> {
                                        Log.i(TAG, "Installing: ${patchZip.name}")
                                        patchManager.installPatch(patchZip)
                                    }
                                    else -> {
                                        val installedVersion = installedPatch.manifest.version
                                        val bundledVersion = manifest.version
                                        val cmp = PatchManifest.compareVersions(bundledVersion, installedVersion)
                                        if (cmp > 0) {
                                            Log.i(TAG, "Updating patch: ${manifest.id} ($installedVersion -> $bundledVersion)")
                                            patchManager.installPatch(patchZip)
                                        } else {
                                            Log.d(TAG, "Patch up to date: ${manifest.id}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error installing individual patch: ${patchZip.name}", e)
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FATAL ERROR during built-in patch installation: ${e.message}", e)
            }
        }

        @JvmStatic
        fun constructStartupHooksEnvVar(patches: List<Patch>): String {
            return try {
                val seenPatchIds = linkedSetOf<String>()
                patches
                    .filter { seenPatchIds.add(it.manifest.id) }
                    .map { it.getEntryAssemblyAbsolutePath().absolutePath }
                    .distinct()
                    .joinToString(":")
            } catch (e: Exception) {
                Log.e(TAG, "Error constructing startup hooks", e)
                ""
            }
        }
    }
}

