package com.app.ralaunch.feature.patch.data

import android.content.Context
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.extractor.BasicSevenZipExtractor
import com.app.ralaunch.core.extractor.ExtractorCollection
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import org.koin.java.KoinJavaComponent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Objects
import java.util.stream.Collectors

/**
 * 补丁管理器
 */
class PatchManager @JvmOverloads constructor(
    customStoragePath: String? = null,
    installPatchesImmediately: Boolean = false
) {
    private val patchStoragePath: Path
    private val configFilePath: Path
    private var config: PatchManagerConfig

    init {
        val patchStorageBasePath = getPatchStorageBaseDirectory(customStoragePath)
        patchStoragePath = patchStorageBasePath.resolve(PATCH_STORAGE_DIR).normalize()
        if (!Files.isDirectory(patchStoragePath) || !Files.exists(patchStoragePath)) {
            FileUtils.deleteFileWithinRoot(patchStoragePath, patchStorageBasePath)
            Files.createDirectories(patchStoragePath)
        }
        configFilePath = patchStoragePath.resolve(PatchManagerConfig.CONFIG_FILE_NAME)
        config = loadConfig()

        // 清理旧的共享 DLL 文件 (MonoMod/Harmony 现在在游戏目录中按版本管理)
        cleanLegacySharedDlls()

        // 如果指定立即安装，则在当前线程安装补丁（用于向后兼容）
        if (installPatchesImmediately) {
            installBuiltInPatches(this)
        }
    }

    //region Patch Querying

    /**
     * Returns all patches that are applicable to the specified game and are enabled for
     * the provided game assembly path. Results are sorted by priority in descending order.
     */
    fun getApplicableAndEnabledPatches(gameId: String, gameAsmPath: Path): ArrayList<Patch> {
        val installedPatches = installedPatches

        return installedPatches
            .filter { isPatchApplicableToGame(it, gameId) }
            .filter { config.isPatchEnabled(gameAsmPath, it.manifest.id) }
            .sortedByDescending { it.manifest.priority }
            .toCollection(ArrayList())
    }

    /**
     * Returns all patches that are applicable to the specified game, regardless of enabled status.
     */
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

    /**
     * Returns all patches that are enabled for the specified game assembly path.
     */
    fun getEnabledPatches(gameAsmPath: Path): ArrayList<Patch> {
        return installedPatches
            .filter { config.isPatchEnabled(gameAsmPath, it.manifest.id) }
            .sortedByDescending { it.manifest.priority }
            .toCollection(ArrayList())
    }

    /**
     * Get IDs of all patches that are enabled for the specified game assembly path.
     */
    fun getEnabledPatchIds(gameAsmPath: Path): ArrayList<String> {
        return config.getEnabledPatchIds(gameAsmPath)
    }

    /**
     * Scans the patch storage directory and returns all currently installed (valid) patches.
     */
    val installedPatches: ArrayList<Patch>
        get() {
            return try {
                Files.list(patchStoragePath).use { pathsStream ->
                    pathsStream
                        .filter { Files.isDirectory(it) }
                        .map { Patch.fromPatchPath(it) }
                        .filter { it != null }
                        .map { it!! }
                        .collect(Collectors.toCollection(::ArrayList))
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

    /**
     * Returns patches from the installed patches that match the provided IDs.
     */
    fun getPatchesByIds(patchIds: List<String>): ArrayList<Patch> {
        return installedPatches
            .filter { patchIds.contains(it.manifest.id) }
            .sortedBy { patchIds.indexOf(it.manifest.id) }
            .toCollection(ArrayList())
    }

    //endregion

    //region Patch Installation

    /**
     * Install a patch archive (ZIP/7z).
     */
    fun installPatch(patchZipPath: Path): Boolean {
        if (!Files.exists(patchZipPath) || !Files.isRegularFile(patchZipPath)) {
            AppLog.w(TAG, "补丁安装失败: 补丁文件不存在或不是一个有效的文件, path: $patchZipPath")
            return false
        }

        val manifest = PatchManifest.fromZip(patchZipPath)
        if (manifest == null) {
            AppLog.w(TAG, "补丁安装失败: 无法读取补丁清单, path: $patchZipPath")
            return false
        }

        val patchPath = patchStoragePath.resolve(manifest.id)

        if (Files.exists(patchPath)) {
            AppLog.i(TAG, "补丁已存在, 将删除原补丁目录，重新安装, patch id: ${manifest.id}")
            if (!FileUtils.deleteDirectoryRecursivelyWithinRoot(patchPath, patchStoragePath)) {
                AppLog.w(TAG, "删除原补丁目录时发生错误")
                return false
            }
        } else {
            AppLog.i(TAG, "正在安装新补丁, patch id: ${manifest.id}")
        }

        AppLog.i(TAG, "正在解压补丁文件到补丁目录...")
        BasicSevenZipExtractor(
            patchZipPath,
            Paths.get(""),
            patchPath,
            object : ExtractorCollection.ExtractionListener {
                override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {}
                override fun onComplete(message: String, state: HashMap<String, Any?>?) {}
                override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                    throw RuntimeException(message, ex)
                }
            }
        ).extract()

        return true
    }

    //endregion

    //region Config Value Setting and Getting

    /**
     * Set whether a patch is enabled for a specific game.
     */
    fun setPatchEnabled(gameAsmPath: Path, patchId: String, enabled: Boolean) {
        config.setPatchEnabled(gameAsmPath, patchId, enabled)
        saveConfig()
    }

    /**
     * Check if a patch is enabled for a specific game.
     */
    fun isPatchEnabled(gameAsmPath: Path, patchId: String): Boolean {
        return config.isPatchEnabled(gameAsmPath, patchId)
    }

    //endregion

    //region Configuration Management

    private fun loadConfig(): PatchManagerConfig {
        val loadedConfig = PatchManagerConfig.fromJson(configFilePath)
        return if (loadedConfig == null) {
            AppLog.i(TAG, "配置文件不存在或加载失败，创建新配置")
            PatchManagerConfig().also { it.saveToJson(configFilePath) }
        } else {
            AppLog.i(TAG, "配置文件加载成功")
            loadedConfig
        }
    }

    private fun saveConfig() {
        if (!config.saveToJson(configFilePath)) {
            AppLog.w(TAG, "保存配置文件失败")
        }
    }

    //endregion

    //region Legacy Cleanup

    private fun cleanLegacySharedDlls() {
        for (dllName in LEGACY_SHARED_DLLS) {
            val dllPath = patchStoragePath.resolve(dllName)
            try {
                if (Files.exists(dllPath)) {
                    FileUtils.deleteFileWithinRoot(dllPath, patchStoragePath)
                    AppLog.i(TAG, "已清理旧的共享 DLL: $dllName")
                }
            } catch (e: IOException) {
                AppLog.w(TAG, "清理 $dllName 失败: ${e.message}")
            }
        }
    }

    //endregion

    companion object {
        private const val TAG = "PatchManager"
        private const val IS_DEFAULT_PATCH_STORAGE_DIR_EXTERNAL = true
        private const val PATCH_STORAGE_DIR = "patches"

        private val LEGACY_SHARED_DLLS = arrayOf(
            "0Harmony.dll",
            "MonoMod.Common.dll",
            "Mono.Cecil.dll"
        )

        @Throws(IOException::class)
        private fun getPatchStorageBaseDirectory(customStoragePath: String?): Path {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            val baseDir = customStoragePath ?: if (IS_DEFAULT_PATCH_STORAGE_DIR_EXTERNAL) {
                Objects.requireNonNull(context.getExternalFilesDir(null))?.absolutePath
                    ?: context.filesDir.absolutePath
            } else {
                context.filesDir.absolutePath
            }
            return Paths.get(baseDir).normalize()
        }

        @JvmStatic
        fun installBuiltInPatches(patchManager: PatchManager) {
            installBuiltInPatches(patchManager, false)
        }

        @JvmStatic
        fun installBuiltInPatches(patchManager: PatchManager, forceReinstall: Boolean) {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            val apkPath = Paths.get(context.applicationInfo.sourceDir)

            TemporaryFileAcquirer().use { tfa ->
                val extractedPatches = tfa.acquireTempFilePath("extracted_patches")

                BasicSevenZipExtractor(
                    apkPath,
                    Paths.get("assets/patches"),
                    extractedPatches,
                    object : ExtractorCollection.ExtractionListener {
                        override fun onProgress(message: String, progress: Float, state: HashMap<String, Any?>?) {}
                        override fun onComplete(message: String, state: HashMap<String, Any?>?) {}
                        override fun onError(message: String, ex: Exception?, state: HashMap<String, Any?>?) {
                            throw RuntimeException(message, ex)
                        }
                    }
                ).extract()

                // 获取已安装补丁的 ID -> 清单映射（用于版本比较）
                val installedPatchMap = patchManager.installedPatches
                    .associateBy { it.manifest.id }

                // 安装缺失或版本更新的内置补丁
                try {
                    Files.list(extractedPatches).use { pathsStream ->
                        pathsStream
                            .filter { Files.isRegularFile(it) && it.toString().endsWith(".zip") }
                            .forEach { patchZip ->
                                val manifest = PatchManifest.fromZip(patchZip) ?: return@forEach

                                val installedPatch = installedPatchMap[manifest.id]

                                when {
                                    forceReinstall -> {
                                        AppLog.i(TAG, "正在强制重新安装内置补丁: ${patchZip.fileName} (id: ${manifest.id})")
                                        patchManager.installPatch(patchZip)
                                    }
                                    installedPatch == null -> {
                                        AppLog.i(TAG, "正在安装内置补丁: ${patchZip.fileName} (id: ${manifest.id}, version: ${manifest.version})")
                                        patchManager.installPatch(patchZip)
                                    }
                                    else -> {
                                        val installedVersion = installedPatch.manifest.version
                                        val bundledVersion = manifest.version
                                        val cmp = PatchManifest.compareVersions(bundledVersion, installedVersion)
                                        if (cmp > 0) {
                                            AppLog.i(TAG, "检测到补丁更新: ${manifest.id} (${installedVersion} -> ${bundledVersion})，正在自动更新...")
                                            patchManager.installPatch(patchZip)
                                        } else {
                                            AppLog.i(TAG, "补丁已是最新版本，跳过: ${manifest.id} (installed: ${installedVersion}, bundled: ${bundledVersion})")
                                        }
                                    }
                                }
                            }
                    }
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }

        /**
         * Construct the environment variable string for startup hooks from a list of patches.
         */
        @JvmStatic
        fun constructStartupHooksEnvVar(patches: List<Patch>): String {
            AppLog.d(TAG, "constructStartupHooksEnvVar: Input patches count = ${patches.size}")
            patches.forEachIndexed { index, p ->
                AppLog.d(TAG, "  [$index] id=${p.manifest.id}, path=${p.getEntryAssemblyAbsolutePath()}")
            }

            val seenPatchIds = linkedSetOf<String>()
            val result = patches
                .filter { p ->
                    val isNew = seenPatchIds.add(p.manifest.id)
                    if (!isNew) {
                        AppLog.w(TAG, "constructStartupHooksEnvVar: Duplicate patch ID filtered: ${p.manifest.id}")
                    }
                    isNew
                }
                .map { it.getEntryAssemblyAbsolutePath().toString() }
                .distinct()
                .joinToString(":")

            AppLog.d(TAG, "constructStartupHooksEnvVar: Result = $result")
            return result
        }
    }
}
