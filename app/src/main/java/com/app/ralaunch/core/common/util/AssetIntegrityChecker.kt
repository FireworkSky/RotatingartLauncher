package com.app.ralaunch.core.common.util

import android.content.Context
import com.app.ralaunch.R
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.platform.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File

/**
 * 资产完整性检查器
 * 
 * 检查初始化解压的库和资产文件是否完整，防止游戏启动失败。
 */
object AssetIntegrityChecker {

    private const val TAG = "AssetIntegrityChecker"

    /**
     * 检查结果
     */
    data class CheckResult(
        val isValid: Boolean,
        val issues: List<Issue>,
        val summary: String
    ) {
        data class Issue(
            val type: IssueType,
            val description: String,
            val filePath: String? = null,
            val canAutoFix: Boolean = false
        )

        enum class IssueType {
            MISSING_FILE,           // 文件缺失
            EMPTY_FILE,             // 文件为空
            VERSION_MISMATCH,       // 版本不匹配
            CORRUPTED_FILE,         // 文件损坏
            PERMISSION_ERROR,       // 权限错误
            DIRECTORY_MISSING       // 目录缺失
        }
    }

    /**
     * 关键组件定义
     */
    private data class CriticalComponent(
        val nameResId: Int,
        val dirName: String,
        val criticalFiles: List<String> = emptyList(),
        val minSizeBytes: Long = 1024  // 最小文件大小，防止空文件
    )

    /**
     * 需要检查的关键组件列表
     * 
     * 注意：当前 dotnet 资产包不再包含 apphost，完整性改为检查
     * host/fxr/<version>/libhostfxr.so 与 shared/Microsoft.NETCore.App/<version>/ 结构。
     */
    private val CRITICAL_COMPONENTS = listOf(
        CriticalComponent(
            nameResId = R.string.asset_check_component_dotnet_runtime,
            dirName = "${AppConstants.Dirs.RUNTIMES}/dotnet"
        )
    )

    /**
     * 执行完整性检查
     */
    suspend fun checkIntegrity(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<CheckResult.Issue>()
        val filesDir = context.filesDir

        AppLog.i(TAG, "开始资产完整性检查...")

        // 1. 检查关键组件目录
        for (component in CRITICAL_COMPONENTS) {
            val componentName = context.getString(component.nameResId)
            val componentDir = File(filesDir, component.dirName)
            
            if (!componentDir.exists()) {
                issues.add(CheckResult.Issue(
                    type = CheckResult.IssueType.DIRECTORY_MISSING,
                    description = context.getString(R.string.asset_check_issue_directory_missing, componentName),
                    filePath = componentDir.absolutePath,
                    canAutoFix = true
                ))
                continue
            }

            // 检查关键文件
            if (component.dirName.endsWith("/dotnet")) {
                issues.addAll(checkDotNetComponent(context, componentDir, componentName))
            } else {
                for (fileName in component.criticalFiles) {
                    val file = File(componentDir, fileName)
                    val issue = checkFile(context, file, componentName, component.minSizeBytes)
                    if (issue != null) {
                        issues.add(issue)
                    }
                }
            }
        }

        // 生成摘要
        val summary = if (issues.isEmpty()) {
            context.getString(R.string.asset_check_summary_all_passed)
        } else {
            val criticalCount = issues.count { 
                it.type == CheckResult.IssueType.MISSING_FILE || 
                it.type == CheckResult.IssueType.DIRECTORY_MISSING 
            }
            val warningCount = issues.size - criticalCount
            buildString {
                append(context.getString(R.string.asset_check_summary_issues_found, issues.size))
                if (criticalCount > 0) {
                    append(" ")
                    append(context.getString(R.string.asset_check_summary_critical_count, criticalCount))
                }
                if (warningCount > 0) {
                    append(" ")
                    append(context.getString(R.string.asset_check_summary_warning_count, warningCount))
                }
            }
        }

        AppLog.i(TAG, "资产完整性检查完成: $summary")
        issues.forEach { issue ->
            AppLog.w(TAG, "  - [${issue.type}] ${issue.description}: ${issue.filePath}")
        }

        CheckResult(
            isValid = issues.isEmpty(),
            issues = issues,
            summary = summary
        )
    }

    /**
     * 检查单个文件
     */
    private fun checkFile(context: Context, file: File, componentName: String, minSize: Long): CheckResult.Issue? {
        return when {
            !file.exists() -> CheckResult.Issue(
                type = CheckResult.IssueType.MISSING_FILE,
                description = context.getString(R.string.asset_check_issue_missing_file, componentName, file.name),
                filePath = file.absolutePath,
                canAutoFix = true
            )
            file.length() == 0L -> CheckResult.Issue(
                type = CheckResult.IssueType.EMPTY_FILE,
                description = context.getString(R.string.asset_check_issue_empty_file, componentName, file.name),
                filePath = file.absolutePath,
                canAutoFix = true
            )
            file.length() < minSize -> CheckResult.Issue(
                type = CheckResult.IssueType.CORRUPTED_FILE,
                description = context.getString(
                    R.string.asset_check_issue_corrupted_file,
                    componentName,
                    file.name,
                    file.length(),
                    minSize
                ),
                filePath = file.absolutePath,
                canAutoFix = true
            )
            !file.canRead() -> CheckResult.Issue(
                type = CheckResult.IssueType.PERMISSION_ERROR,
                description = context.getString(R.string.asset_check_issue_permission_error, componentName, file.name),
                filePath = file.absolutePath,
                canAutoFix = false
            )
            else -> null
        }
    }

    /**
     * 自动修复问题
     * 
     * @param context Android Context
     * @param issues 要修复的问题列表
     * @param progressCallback 进度回调
     * @return 修复结果
     */
    suspend fun autoFix(
        context: Context,
        issues: List<CheckResult.Issue>,
        progressCallback: ((Int, String) -> Unit)? = null
    ): FixResult = withContext(Dispatchers.IO) {
        val fixableIssues = issues.filter { it.canAutoFix }
        if (fixableIssues.isEmpty()) {
            return@withContext FixResult(
                success = true,
                message = context.getString(R.string.asset_fix_no_auto_fixable_issues)
            )
        }

        AppLog.i(TAG, "开始自动修复 ${fixableIssues.size} 个问题...")
        progressCallback?.invoke(0, context.getString(R.string.asset_fix_progress_prepare))

        var fixedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        val componentsToReextract = resolveAffectedComponents(context, fixableIssues)
        var needsComponentExtract = false

        if (componentsToReextract.isNotEmpty()) {
            componentsToReextract.forEachIndexed { index, component ->
                val componentDir = File(context.filesDir, component.dirName)
                val componentName = context.getString(component.nameResId)

                val deleteSucceeded = !componentDir.exists() ||
                    FileUtils.deleteDirectoryRecursivelyWithinRoot(componentDir, context.filesDir)

                if (deleteSucceeded) {
                    fixedCount++
                    AppLog.i(TAG, "已清理旧组件目录: ${componentDir.absolutePath}")
                } else {
                    failedCount++
                    errors.add("Failed to remove old $componentName directory: ${componentDir.absolutePath}")
                    AppLog.w(TAG, "清理旧组件目录失败: ${componentDir.absolutePath}")
                }

                val progress = 20 + ((index + 1) * 40 / componentsToReextract.size)
                progressCallback?.invoke(progress, context.getString(R.string.asset_fix_progress_prepare))
            }
        }

        // 重新解压核心组件需要清除初始化标记
        if (componentsToReextract.isNotEmpty()) {
            progressCallback?.invoke(70, context.getString(R.string.asset_fix_progress_mark_reinit))
            try {
                val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, 0)
                prefs.edit()
                    .putBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, false)
                    .apply()
                needsComponentExtract = true
                AppLog.i(TAG, "已标记需要重新初始化，下次启动将重新解压组件")
            } catch (e: Exception) {
                failedCount++
                errors.add(
                    context.getString(
                        R.string.asset_fix_mark_reinit_failed,
                        e.message ?: context.getString(R.string.common_unknown_error)
                    )
                )
            }
        }

        progressCallback?.invoke(100, context.getString(R.string.asset_fix_progress_completed))

        val message = buildString {
            append(context.getString(R.string.asset_fix_result_prefix))
            if (fixedCount > 0) append(context.getString(R.string.asset_fix_result_success_count, fixedCount))
            if (failedCount > 0) {
                if (fixedCount > 0) append(", ")
                append(context.getString(R.string.asset_fix_result_failed_count, failedCount))
            }
            if (needsComponentExtract && fixedCount > 0) {
                append("\n\n")
                append(context.getString(R.string.asset_fix_restart_required))
            }
        }

        FixResult(
            success = failedCount == 0,
            message = message,
            errors = errors,
            needsRestart = needsComponentExtract
        )
    }

    /**
     * 强制重新安装全部核心资产。
     *
     * 当前实现复用自动修复路径：删除已安装组件目录并清除初始化完成标记，
     * 让下次启动重新进入初始化提取流程。
     */
    suspend fun forceReinstall(context: Context): FixResult {
        val reinstallIssues = CRITICAL_COMPONENTS.map { component ->
            CheckResult.Issue(
                type = CheckResult.IssueType.DIRECTORY_MISSING,
                description = context.getString(
                    R.string.asset_check_issue_directory_missing,
                    context.getString(component.nameResId)
                ),
                filePath = File(context.filesDir, component.dirName).absolutePath,
                canAutoFix = true
            )
        }
        return autoFix(context, reinstallIssues)
    }

    private fun resolveAffectedComponents(
        context: Context,
        issues: List<CheckResult.Issue>
    ): List<CriticalComponent> {
        val filesDir = context.filesDir.absoluteFile.normalize()
        return CRITICAL_COMPONENTS.filter { component ->
            val componentDir = File(filesDir, component.dirName).absoluteFile.normalize()
            val componentPath = componentDir.path
            issues.any { issue ->
                val filePath = issue.filePath ?: return@any false
                val normalizedIssuePath = File(filePath).absoluteFile.normalize().path
                normalizedIssuePath == componentPath ||
                    normalizedIssuePath.startsWith(componentPath + File.separator)
            }
        }
    }

    /**
     * 修复结果
     */
    data class FixResult(
        val success: Boolean,
        val message: String,
        val errors: List<String> = emptyList(),
        val needsRestart: Boolean = false
    )

    /**
     * 获取资产状态摘要（用于显示）
     */
    suspend fun getStatusSummary(context: Context): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        // .NET Runtime 状态
        val dotnetRuntime = runtimeManager().getSelectedRuntime(IRuntimeManagerServiceV2.RuntimeType.DOTNET)
        val dotnetStatus = if (dotnetRuntime != null && hasValidDotNetLayout(dotnetRuntime.rootPath.toFile())) {
            val versions = runtimeManager().getInstalledVersions(IRuntimeManagerServiceV2.RuntimeType.DOTNET)
            if (versions.isNotEmpty()) {
                context.getString(
                    R.string.asset_check_status_dotnet_installed_versions,
                    versions.joinToString()
                )
            } else {
                context.getString(R.string.asset_check_status_dotnet_installed)
            }
        } else {
            context.getString(R.string.asset_check_status_dotnet_not_installed)
        }
        sb.appendLine(dotnetStatus)

        sb.toString()
    }

    private fun checkDotNetComponent(
        context: Context,
        dotnetDir: File,
        componentName: String
    ): List<CheckResult.Issue> {
        val issues = mutableListOf<CheckResult.Issue>()
        val selectedRuntime = runtimeManager().getSelectedRuntime(IRuntimeManagerServiceV2.RuntimeType.DOTNET)
        if (selectedRuntime == null) {
            issues.add(
                CheckResult.Issue(
                    type = CheckResult.IssueType.DIRECTORY_MISSING,
                    description = context.getString(
                        R.string.asset_check_issue_directory_missing,
                        "$componentName runtime"
                    ),
                    filePath = dotnetDir.absolutePath,
                    canAutoFix = true
                )
            )
            return issues
        }

        val runtimeRootPath = selectedRuntime.rootPath
        val dotnetRuntimeRoot = runtimeRootPath.toFile()

        val hostFxrVersionDir = getHostFxrVersionDir(dotnetRuntimeRoot)
        val hostFxrLib = hostFxrVersionDir?.let { File(it, "libhostfxr.so") }
        issues.addIfNotNull(
            checkFile(
                context = context,
                file = hostFxrLib ?: File(dotnetRuntimeRoot, "host/fxr/libhostfxr.so"),
                componentName = componentName,
                minSize = 100_000
            )
        )

        val runtimeRoot = File(dotnetRuntimeRoot, "shared/Microsoft.NETCore.App")
        val runtimeVersionDir = getDotNetRuntimeVersionDir(dotnetRuntimeRoot)
        if (runtimeVersionDir == null) {
            issues.add(
                CheckResult.Issue(
                    type = CheckResult.IssueType.DIRECTORY_MISSING,
                    description = context.getString(
                        R.string.asset_check_issue_directory_missing,
                        "$componentName runtime"
                    ),
                    filePath = runtimeRoot.absolutePath,
                    canAutoFix = true
                )
            )
            return issues
        }

        listOf(
            "libcoreclr.so" to 1_000_000L,
            "libclrjit.so" to 1_000_000L,
            "libhostpolicy.so" to 100_000L
        ).forEach { (fileName, minSize) ->
            issues.addIfNotNull(
                checkFile(
                    context = context,
                    file = File(runtimeVersionDir, fileName),
                    componentName = componentName,
                    minSize = minSize
                )
            )
        }

        return issues
    }

    private fun hasValidDotNetLayout(dotnetDir: File): Boolean {
        val hostFxrLib = getHostFxrVersionDir(dotnetDir)?.let { File(it, "libhostfxr.so") }
        if (hostFxrLib == null || !hostFxrLib.exists() || hostFxrLib.length() <= 100_000) {
            return false
        }

        val runtimeVersionDir = getDotNetRuntimeVersionDir(dotnetDir) ?: return false
        return listOf("libcoreclr.so", "libclrjit.so", "libhostpolicy.so").all { fileName ->
            val file = File(runtimeVersionDir, fileName)
            file.exists() && file.length() > 0
        }
    }

    private fun getDotNetRuntimeVersions(dotnetDir: File): List<String> {
        val runtimeDir = File(dotnetDir, "shared/Microsoft.NETCore.App")
        return runtimeDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            .orEmpty()
    }

    private fun getDotNetRuntimeVersionDir(dotnetDir: File): File? {
        return File(dotnetDir, "shared/Microsoft.NETCore.App")
            .listFiles()
            ?.firstOrNull { it.isDirectory }
    }

    private fun getHostFxrVersionDir(dotnetDir: File): File? {
        return File(dotnetDir, "host/fxr")
            .listFiles()
            ?.firstOrNull { it.isDirectory }
    }

    private fun MutableList<CheckResult.Issue>.addIfNotNull(issue: CheckResult.Issue?) {
        if (issue != null) add(issue)
    }

    private fun runtimeManager(): IRuntimeManagerServiceV2 =
        KoinJavaComponent.get(IRuntimeManagerServiceV2::class.java)
}
