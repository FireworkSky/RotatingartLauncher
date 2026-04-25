package com.app.ralaunch.core.common.util

import android.content.Context
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.platform.runtime.AssemblyPatcher
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.koin.java.KoinJavaComponent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

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
                AppLog.e(TAG, "提取失败", e)
            }
        }.start()
    }

    private fun extractAndApplyMonoMod(context: Context) {
        val monoModDir = File(context.filesDir, "MonoMod")
        if (monoModDir.exists()) FileUtils.deleteDirectoryRecursivelyWithinRoot(monoModDir, context.filesDir)
        monoModDir.mkdirs()

        context.assets.open("MonoMod.zip").use { inputStream ->
            BufferedInputStream(inputStream, 16384).use { bis ->
                ZipArchiveInputStream(bis, "UTF-8", true, true).use { zis ->
                    generateSequence { zis.nextZipEntry }.forEach { entry ->
                        var entryName = entry.name
                        if (entryName.startsWith("MonoMod/") || entryName.startsWith("MonoMod\\")) {
                            entryName = entryName.substring(8)
                        }
                        if (entryName.isEmpty()) return@forEach

                        val targetFile = File(monoModDir, entryName)
                        val canonicalDestPath = monoModDir.canonicalPath
                        val canonicalEntryPath = targetFile.canonicalPath
                        if (!canonicalEntryPath.startsWith("$canonicalDestPath${File.separator}")) return@forEach

                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    zis.copyTo(bos, 8192)
                                }
                            }
                        }
                    }
                }
            }
        }

        applyMonoModToAllGames(context, monoModDir)
    }

    private fun applyMonoModToAllGames(context: Context, monoModDir: File) {
        try {
            val gameRepository: IGameRepositoryServiceV3? = try {
                KoinJavaComponent.getOrNull(IGameRepositoryServiceV3::class.java)
            } catch (e: Exception) { null }
            if (gameRepository == null) return
            val games = gameRepository.games.value
            if (games.isEmpty()) return

            games.forEach { game ->
                val gameDir = getGameDirectory(game.gameExePathRelative) ?: return@forEach
                AssemblyPatcher.applyMonoModPatches(context, gameDir, false)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "应用 MonoMod 补丁失败", e)
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
