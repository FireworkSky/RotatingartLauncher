package com.app.ralaunch.core.platform.runtime

import android.content.Context
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.platform.install.extractors.BasicSevenZipExtractor
import com.app.ralaunch.core.platform.install.extractors.ExtractorCollection
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileOutputStream

/**
 * Assembly patcher utility
 */
object AssemblyPatcher {
    private const val TAG = "AssemblyPatcher"
    const val MONOMOD_DIR = "monomod"
    private const val ASSETS_MONOMOD_ZIP = "MonoMod.zip"

    @JvmStatic
    fun getMonoModInstallPath(): File {
        val context: Context = KoinJavaComponent.get(Context::class.java)
        val externalFilesDir = context.getExternalFilesDir(null)
        return File(externalFilesDir?.absolutePath ?: "", MONOMOD_DIR)
    }

    @JvmStatic
    fun extractMonoMod(context: Context): Boolean {
        val targetDir = getMonoModInstallPath()
        AppLogger.info(TAG, "Extracting MonoMod to $targetDir")

        return try {
            TemporaryFileAcquirer().use { tfa ->
                targetDir.mkdirs()
                val tempZip = tfa.acquireTempFilePath("monomod.zip")

                context.assets.open(ASSETS_MONOMOD_ZIP).use { input ->
                    FileOutputStream(tempZip).use { output ->
                        input.copyTo(output)
                    }
                }

                BasicSevenZipExtractor(
                    tempZip, File(""), targetDir,
                    object : ExtractorCollection.ExtractionListener {
                        override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {
                            AppLogger.debug(TAG, "Extracting: $message (${(progress * 100).toInt()}%)")
                        }
                        override fun onComplete(message: String, state: HashMap<String, Any?>?) {
                            AppLogger.info(TAG, "MonoMod extraction complete")
                        }
                        override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                            AppLogger.error(TAG, "Extraction error: $message", ex)
                        }
                    }
                ).extract()

                AppLogger.info(TAG, "MonoMod extracted to $targetDir")
                true
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to extract MonoMod", e)
            false
        }
    }

    @JvmStatic
    fun applyMonoModPatches(context: Context, gameDirectory: String): Int {
        return applyMonoModPatches(context, gameDirectory, true)
    }

    @JvmStatic
    fun applyMonoModPatches(context: Context, gameDirectory: String, verboseLog: Boolean): Int {
        return try {
            val patchAssemblies = loadPatchArchive(context)
            if (patchAssemblies.isEmpty()) {
                if (verboseLog) AppLogger.warn(TAG, "MonoMod directory is empty or does not exist")
                return 0
            }

            val gameDir = File(gameDirectory)
            val gameAssemblies = findGameAssemblies(gameDir)

            var patchedCount = 0
            for (assemblyFile in gameAssemblies) {
                val assemblyName = assemblyFile.name
                patchAssemblies[assemblyName]?.let { data ->
                    if (replaceAssembly(assemblyFile, data)) {
                        if (verboseLog) AppLogger.debug(TAG, "Replaced: $assemblyName")
                        patchedCount++
                    }
                }
            }

            if (verboseLog) AppLogger.info(TAG, "MonoMod patches applied, replaced $patchedCount files")
            patchedCount
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to apply patches", e)
            -1
        }
    }

    private fun loadPatchArchive(context: Context): Map<String, ByteArray> {
        val assemblies = mutableMapOf<String, ByteArray>()
        try {
            val monoModDir = getMonoModInstallPath()

            if (!monoModDir.exists() || !monoModDir.isDirectory) {
                AppLogger.warn(TAG, "MonoMod directory does not exist: $monoModDir")
                return assemblies
            }

            val dllFiles = findDllFiles(monoModDir)
            AppLogger.debug(TAG, "Found ${dllFiles.size} DLL files from $monoModDir")

            for (dllFile in dllFiles) {
                try {
                    val assemblyData = dllFile.readBytes()
                    assemblies[dllFile.name] = assemblyData
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "Failed to read DLL: ${dllFile.name}", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to load MonoMod patches", e)
        }
        return assemblies
    }

    private fun findDllFiles(directory: File): List<File> {
        val dllFiles = mutableListOf<File>()
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                dllFiles.addAll(findDllFiles(file))
            } else if (file.name.endsWith(".dll")) {
                dllFiles.add(file)
            }
        }
        return dllFiles
    }

    private fun findGameAssemblies(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val assemblies = mutableListOf<File>()
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                assemblies.addAll(findGameAssemblies(file))
            } else if (file.name.endsWith(".dll")) {
                assemblies.add(file)
            }
        }
        return assemblies
    }

    private fun replaceAssembly(targetFile: File, assemblyData: ByteArray): Boolean {
        return try {
            FileOutputStream(targetFile).use { it.write(assemblyData) }
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Replacement failed: ${targetFile.name}", e)
            false
        }
    }
}
