package com.app.ralaunch.core.common.util

import android.content.Context
import com.app.ralaunch.core.platform.runtime.AssemblyPatcher
import com.app.ralaunch.shared.core.contract.repository.GameRepositoryV2
import org.koin.java.KoinJavaComponent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
// ... standard Java Zip import ...
import java.util.zip.ZipInputStream

/**
 * 补丁提取工具
 */
object PatchExtractor {
    private const val TAG = "PatchExtractor"
    private const val PREFS_NAME = "patch_extractor_prefs"
    private const val KEY_MONOMOD_EXTRACTED = "monomod_extracted"

    @JvmStatic
    fun extractPatchesIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var needExtractMonoMod = !prefs.getBoolean(KEY_MONOMOD_EXTRACTED, false)

        if (!needExtractMonoMod) {
            val monoModDir = File(context.filesDir, "MonoMod")
            needExtractMonoMod = !monoModDir.exists() || !monoModDir.isDirectory ||
                monoModDir.listFiles()?.isEmpty() != false
        }

        if (!needExtractMonoMod) return

        Thread {
            try {
                extractAndApplyMonoMod(context)
                prefs.edit().putBoolean(KEY_MONOMOD_EXTRACTED, true).apply()
            } catch (e: Exception) {
                AppLogger.error(TAG, "提取失败", e)
            }
        }.start()
    }

    private fun extractAndApplyMonoMod(context: Context) {
        val monoModDir = File(context.filesDir, "MonoMod")
        if (monoModDir.exists()) FileUtils.deleteDirectoryRecursively(monoModDir)
        monoModDir.mkdirs()

        // ... use standard Android ZipInputStream ...
        context.assets.open("MonoMod.zip").use { inputStream ->
            BufferedInputStream(inputStream, 16384).use { bis ->
                ZipInputStream(bis).use { zis ->
                    // ... use while(true) and break on null to avoid Kotlin mutable smart-cast compilation errors ...
                    while (true) {
                        // ... val ensures it is immutable and safe for smart-casting ...
                        val entry = zis.nextEntry ?: break
                        
                        var entryName = entry.name
                        
                        // ... strip the root folder name from the zip entry ...
                        if (entryName.startsWith("MonoMod/") || entryName.startsWith("MonoMod\\")) {
                            entryName = entryName.substring(8)
                        }
                        
                        if (entryName.isNotEmpty()) {
                            val targetFile = File(monoModDir, entryName)
                            val canonicalDestPath = monoModDir.canonicalPath
                            val canonicalEntryPath = targetFile.canonicalPath
                            
                            // ... security check to prevent Zip Path Traversal vulnerability ...
                            if (canonicalEntryPath.startsWith("$canonicalDestPath${File.separator}")) {
                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { fos ->
                                        BufferedOutputStream(fos).use { bos ->
                                            // ... standard stream copy ...
                                            zis.copyTo(bos, 8192)
                                        }
                                    }
                                }
                            }
                        }
                        // ... safely close current entry ...
                        zis.closeEntry()
                    }
                }
            }
        }

        applyMonoModToAllGames(context, monoModDir)
    }

    private fun applyMonoModToAllGames(context: Context, monoModDir: File) {
        try {
            val gameRepository: GameRepositoryV2? = try {
                KoinJavaComponent.getOrNull(GameRepositoryV2::class.java)
            } catch (e: Exception) { null }
            if (gameRepository == null) return
            val games = gameRepository.games.value
            if (games.isEmpty()) return

            games.forEach { game ->
                val gameDir = getGameDirectory(game.gameExePathRelative) ?: return@forEach
                AssemblyPatcher.applyMonoModPatches(context, gameDir, false)
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "应用 MonoMod 补丁失败", e)
        }
    }

    private fun getGameDirectory(gamePath: String?): String? {
        if (gamePath.isNullOrEmpty()) return null
        return File(gamePath).parentFile?.takeIf { it.exists() }?.absolutePath
    }

    @JvmStatic
    fun resetExtractionStatus(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_MONOMOD_EXTRACTED)
            .apply()
    }
}
