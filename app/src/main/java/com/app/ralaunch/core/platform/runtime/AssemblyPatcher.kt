package com.app.ralaunch.core.platform.runtime

import android.content.Context
import timber.log.Timber
import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * 程序集补丁工具
 */
object AssemblyPatcher {
    const val MONOMOD_DIR = "monomod"
    private const val ASSETS_MONOMOD_ZIP = "MonoMod.zip"

    @JvmStatic
    fun getMonoModInstallPath(): Path {
        val context: Context = KoinJavaComponent.get(Context::class.java)
        val externalFilesDir = context.getExternalFilesDir(null)
        return Paths.get(externalFilesDir?.absolutePath ?: "", MONOMOD_DIR)
    }

    @JvmStatic
    fun extractMonoMod(context: Context): Boolean {
        val targetDir = getMonoModInstallPath()
        Timber.i("正在解压 MonoMod 到 $targetDir")

        return try {
            TemporaryFileAcquirer().use { tfa ->
                Files.createDirectories(targetDir)
                val tempZip = tfa.acquireTempFilePath("monomod.zip")

                context.assets.open(ASSETS_MONOMOD_ZIP).use { input ->
                    Files.copy(input, tempZip, StandardCopyOption.REPLACE_EXISTING)
                }

                when (val result = ArchiveExtractor.builder()
                    .from(tempZip)
                    .to(targetDir)
                    .callback { event ->
                        when (event) {
                            is ArchiveExtractor.Event.Progress -> {
                                Timber.d("解压中: ${event.message} (${(event.progress * 100).toInt()}%)")
                            }
                            is ArchiveExtractor.Event.Complete -> Timber.i("MonoMod 解压完成")
                            is ArchiveExtractor.Event.Error -> {
                                Timber.e(event.cause, "解压错误: ${event.message}")
                            }
                        }
                    }
                    .build()
                    .extract()
                ) {
                    is ArchiveExtractor.Result.Success -> Unit
                    is ArchiveExtractor.Result.Failure -> return false
                }

                Timber.i("MonoMod 已解压到 $targetDir")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "解压 MonoMod 失败")
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
                if (verboseLog) Timber.w("MonoMod 目录为空或不存在")
                return 0
            }

            val gameDir = File(gameDirectory)
            val gameAssemblies = findGameAssemblies(gameDir)

            var patchedCount = 0
            for (assemblyFile in gameAssemblies) {
                val assemblyName = assemblyFile.name
                patchAssemblies[assemblyName]?.let { data ->
                    if (replaceAssembly(assemblyFile, data)) {
                        if (verboseLog) Timber.d("已替换: $assemblyName")
                        patchedCount++
                    }
                }
            }

            if (verboseLog) Timber.i("已应用 MonoMod 补丁，替换了 $patchedCount 个文件")
            patchedCount
        } catch (e: Exception) {
            Timber.e(e, "应用补丁失败")
            -1
        }
    }

    private fun loadPatchArchive(context: Context): Map<String, ByteArray> {
        val assemblies = mutableMapOf<String, ByteArray>()
        try {
            val monoModPath = getMonoModInstallPath()
            val monoModDir = monoModPath.toFile()

            if (!monoModDir.exists() || !monoModDir.isDirectory) {
                Timber.w("MonoMod 目录不存在: $monoModPath")
                return assemblies
            }

            val dllFiles = findDllFiles(monoModDir)
            Timber.d("从 $monoModPath 找到 ${dllFiles.size} 个 DLL 文件")

            for (dllFile in dllFiles) {
                try {
                    val assemblyData = Files.readAllBytes(dllFile.toPath())
                    assemblies[dllFile.name] = assemblyData
                } catch (e: Exception) {
                    Timber.w(e, "读取 DLL 失败: ${dllFile.name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "加载 MonoMod 补丁失败")
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
            Timber.e(e, "替换失败: ${targetFile.name}")
            false
        }
    }
}
