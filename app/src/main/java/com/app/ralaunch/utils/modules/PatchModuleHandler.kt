package com.app.ralaunch.utils.modules

import android.content.Context
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.core.extractor.AssetExtractor
import com.app.ralaunch.core.common.util.TemporaryFileAcquirer
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.models.ModuleItem
import com.app.ralaunch.utils.GameManager
import com.app.ralaunch.utils.ModuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * 补丁模块处理器：无状态的补丁查询/安装门面（单例，直接调用）。
 *
 * 安装、登记、启停（总开关）与删除统一走 [ModuleManager]；本 handler 只从
 * [ModuleItem.meta] 解析补丁专属字段并提供查询。补丁按游戏的启用状态存于各游戏
 * [com.app.ralaunch.core.model.GameItem.enabledModules]（显式开启模型，默认空 = 未启用），
 * handler 不持有任何自有配置文件。
 *
 * 补丁生效条件 = targetGames 匹配 ∧ id ∈ 游戏 enabledModules ∧ [ModuleItem.enabled]。
 */
object PatchModuleHandler : ModuleHandler {

    override val type = TYPE
    const val TYPE = "patch"
    private const val BUILT_IN_PATCH_ASSET_DIR = "patches"
    private const val BOOTSTRAP_ASSEMBLY_NAME = "RAL.Bootstrap.CoreCLR.dll"
    private const val BOOTSTRAP_ASSET_PATH = "bootstrap/RAL.Bootstrap.CoreCLR.dll"

    /** 全部已安装补丁（无法解码清单的模块跳过）。 */
    fun patches(): List<Patch> =
        ModuleManager.currentModules.filter { it.type == TYPE }.mapNotNull(Patch::fromModule)

    /** 对指定游戏适用的补丁（targetGames 含 "*" 或该 gameId），按 priority 降序。 */
    fun getApplicablePatches(gameId: String): List<Patch> =
        patches()
            .filter { it.manifest.targetGames.any { g -> g == "*" || g == gameId } }
            .sortedByDescending { it.manifest.priority }

    /**
     * 启动链路查询（签名与旧 PatchManager 一致，调用方零改动）。
     * 按 (gameId, gameAsmPath 绝对路径归一化) 匹配 GameItem，取其 enabledModules；
     * 匹配不到游戏（未登记的直接启动）或列表为空 → 无补丁生效。
     */
    fun getApplicableAndEnabledPatches(gameId: String, gameAsmPath: Path): List<Patch> {
        val asmKey = gameAsmPath.toAbsolutePath().normalize()
        val enabledIds = GameManager.currentGames.firstOrNull { game ->
            game.gameId == gameId && game.gameExePathFull?.let {
                Paths.get(it).toAbsolutePath().normalize() == asmKey
            } == true
        }?.enabledModules?.toSet() ?: emptySet()
        return getApplicablePatches(gameId).filter { it.module.enabled && it.id in enabledIds }
    }

    /** 返回 id 命中的补丁，顺序跟随 [patchIds]。 */
    fun getPatchesByIds(patchIds: List<String>): List<Patch> =
        patches().filter { patchIds.contains(it.id) }.sortedBy { patchIds.indexOf(it.id) }

    /**
     * 从 APK assets/patches 抽取内置补丁 zip 并逐个经 [ModuleManager.install] 安装
     * （未登记安装、版本更高更新，[forceReinstall] 为 true 时强制重装）。
     */
    @OptIn(ExperimentalPathApi::class)
    suspend fun installBuiltInPatches(context: Context, forceReinstall: Boolean = false) =
        withContext(Dispatchers.IO) {
            val apkPath = Paths.get(context.applicationInfo.sourceDir)

            TemporaryFileAcquirer().use { tfa ->
                val extractedPatches = tfa.acquireTempFilePath("extracted_patches")

                when (
                    val result = ArchiveExtractor.builder()
                        .from(apkPath)
                        .prefix(Paths.get("assets/$BUILT_IN_PATCH_ASSET_DIR"))
                        .to(extractedPatches)
                        .build()
                        .extract()
                ) {
                    is ArchiveExtractor.Result.Success -> Unit
                    is ArchiveExtractor.Result.Failure ->
                        throw RuntimeException(result.message, result.cause)
                }

                extractedPatches.listDirectoryEntries()
                    .filter { it.isRegularFile() && it.extension.equals("zip", ignoreCase = true) }
                    .sorted()
                    .forEach { patchZip ->
                        try {
                            val module = ModuleManager.install(patchZip, forceReinstall)
                            Timber.i(
                                "Built-in patch processed: %s (id: %s, version: %s)",
                                patchZip.fileName, module.id, module.version
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to install built-in patch: %s", patchZip.fileName)
                        }
                    }
            }
        }

    /**
     * Construct the RAL_PATCHES environment variable string from a list of patches.
     */
    fun constructPatchesEnvVar(patches: List<Patch>): String {
        Timber.d("constructPatchesEnvVar: Input patches count = ${patches.size}")
        patches.forEachIndexed { index, p ->
            Timber.d("  [$index] id=${p.id}, path=${p.getEntryAssemblyAbsolutePath()}")
        }

        val seenPatchIds = linkedSetOf<String>()
        val result = patches
            .filter { p ->
                val isNew = seenPatchIds.add(p.id)
                if (!isNew) {
                    Timber.w("constructPatchesEnvVar: Duplicate patch ID filtered: ${p.id}")
                }
                isNew
            }
            .map { it.getEntryAssemblyAbsolutePath().toString() }
            .distinct()
            .joinToString(":")

        Timber.d("constructPatchesEnvVar: Result = $result")
        return result
    }

    /**
     * Bootstrap 程序集落盘路径（filesDir/bootstrap/RAL.Bootstrap.CoreCLR.dll，
     * 由 [deployBootstrapAssembly] 在应用初始化时写入）。
     */
    fun bootstrapAssemblyFile(): Path =
        Paths.get(
            RaLaunchApp.getInstance().filesDir.absolutePath,
            AppConstants.Dirs.BOOTSTRAP,
            BOOTSTRAP_ASSEMBLY_NAME
        )

    /**
     * 从 APK assets/bootstrap 部署 RAL.Bootstrap.CoreCLR 程序集到 filesDir
     * （应用初始化时调用，不在游戏启动链路）。
     * 每次覆盖写入以跟随 APK 更新；失败打日志返回 false（游戏届时无补丁启动）。
     */
    fun deployBootstrapAssembly(): Boolean = try {
        val target = bootstrapAssemblyFile()
        target.parent?.createDirectories()
        AssetExtractor.copyAssetToFile(RaLaunchApp.getInstance(), BOOTSTRAP_ASSET_PATH, target)
        Timber.i("Bootstrap assembly deployed: $target")
        true
    } catch (e: Exception) {
        Timber.e(e, "Failed to deploy RAL.Bootstrap.CoreCLR.dll")
        false
    }
}
