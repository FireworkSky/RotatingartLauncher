package com.app.ralaunch.core.common.util

import android.content.Context
import com.app.ralaunch.R
import com.app.ralaunch.core.platform.runtime.RuntimeLibraryLoader
import com.app.ralaunch.shared.core.platform.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val criticalFiles: List<String>,
        val minSizeBytes: Long = 1024  // 最小文件大小，防止空文件
    )

    /**
     * 需要检查的关键组件列表
     * 
     * 注意：CoreCLR 位于 dotnet/shared/Microsoft.NETCore.App/VERSION/ 目录
     * FNA 通常由游戏自带，不在 filesDir 中检查
     */
    private val CRITICAL_COMPONENTS = listOf(
        CriticalComponent(
            nameResId = R.string.asset_check_component_dotnet_runtime,
            dirName = "dotnet",
            criticalFiles = listOf(
                "apphost"  // 只检查 dotnet 目录存在和基本文件
            ),
            minSizeBytes = 1024
        )
        // FNA 由游戏自带，不在此处检查
    )

    /**
     * 运行时库关键文件（从 runtime_libs.tar.xz 解压）
     * 注意：大小阈值根据实际打包的文件调整
     */
    private val RUNTIME_LIBS_CRITICAL = listOf(
        "libGL_gl4es.so" to 1_000_000L,      // GL4ES ~4.7MB
        "libEGL_gl4es.so" to 50_000L         // GL4ES EGL ~79KB
    )

    /**
     * 执行完整性检查
     */
    suspend fun checkIntegrity(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<CheckResult.Issue>()
        val filesDir = context.filesDir

        AppLogger.info(TAG, "开始资产完整性检查...")

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
            for (fileName in component.criticalFiles) {
                val file = File(componentDir, fileName)
                val issue = checkFile(context, file, componentName, component.minSizeBytes)
                if (issue != null) {
                    issues.add(issue)
                }
            }
        }

        // 2. 检查运行时库
        val runtimeDir = RuntimeLibraryLoader.getRuntimeLibsDir(context)
        if (!runtimeDir.exists()) {
            issues.add(CheckResult.Issue(
                type = CheckResult.IssueType.DIRECTORY_MISSING,
                description = context.getString(
                    R.string.asset_check_issue_directory_missing,
                    context.getString(R.string.asset_check_component_runtime_libs)
                ),
                filePath = runtimeDir.absolutePath,
                canAutoFix = true
            ))
        } else {
            for ((fileName, minSize) in RUNTIME_LIBS_CRITICAL) {
                val file = File(runtimeDir, fileName)
                val issue = checkFile(
                    context,
                    file,
                    context.getString(R.string.asset_check_component_runtime_libs),
                    minSize
                )
                if (issue != null) {
                    issues.add(issue)
                }
            }

            // 检查版本文件
            val versionFile = File(runtimeDir, ".version")
            if (!versionFile.exists()) {
                issues.add(CheckResult.Issue(
                    type = CheckResult.IssueType.VERSION_MISMATCH,
                    description = context.getString(R.string.asset_check_issue_runtime_version_missing),
                    filePath = versionFile.absolutePath,
                    canAutoFix = true
                ))
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

        AppLogger.info(TAG, "资产完整性检查完成: $summary")
        issues.forEach { issue ->
            AppLogger.warn(TAG, "  - [${issue.type}] ${issue.description}: ${issue.filePath}")
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
     * 快速检查（仅检查是否需要重新初始化）
     */
    fun needsReinitialization(context: Context): Boolean {
        val filesDir = context.filesDir

        // 检查 .NET Runtime (dotnet 目录)
        val dotnetDir = File(filesDir, "dotnet")
        if (!dotnetDir.exists()) return true
        val apphost = File(dotnetDir, "apphost")
        if (!apphost.exists() || apphost.length() < 1000) return true

        // 检查运行时库
        if (!RuntimeLibraryLoader.isExtracted(context)) return true

        return false
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

        AppLogger.info(TAG, "开始自动修复 ${fixableIssues.size} 个问题...")
        progressCallback?.invoke(0, context.getString(R.string.asset_fix_progress_prepare))

        var fixedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        val runtimeDirPath = RuntimeLibraryLoader.getRuntimeLibsDir(context).absolutePath

        // 按类型分组处理
        val needsRuntimeLibsExtract = fixableIssues.any { 
            val filePath = it.filePath
            filePath?.startsWith(runtimeDirPath) == true || filePath?.contains("runtime_libs") == true
        }

        val needsComponentExtract = fixableIssues.any {
            it.filePath?.contains("coreclr") == true ||
            it.filePath?.contains("fna") == true
        }

        // 重新解压运行时库
        if (needsRuntimeLibsExtract) {
            progressCallback?.invoke(20, context.getString(R.string.asset_fix_progress_reextract_runtime))
            try {
                val result = RuntimeLibraryLoader.forceReExtract(context) { progress, msg ->
                    progressCallback?.invoke(20 + progress * 40 / 100, msg)
                }
                if (result) {
                    fixedCount++
                    AppLogger.info(TAG, "运行时库重新解压成功")
                } else {
                    failedCount++
                    errors.add(context.getString(R.string.asset_fix_runtime_reextract_failed))
                }
            } catch (e: Exception) {
                failedCount++
                errors.add(
                    context.getString(
                        R.string.asset_fix_runtime_extract_exception,
                        e.message ?: context.getString(R.string.common_unknown_error)
                    )
                )
                AppLogger.error(TAG, "运行时库重新解压失败", e)
            }
        }

        // 重新解压核心组件需要清除初始化标记
        if (needsComponentExtract) {
            progressCallback?.invoke(70, context.getString(R.string.asset_fix_progress_mark_reinit))
            try {
                val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, 0)
                prefs.edit()
                    .putBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, false)
                    .apply()
                fixedCount++
                AppLogger.info(TAG, "已标记需要重新初始化，下次启动将重新解压组件")
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
        val filesDir = context.filesDir
        val sb = StringBuilder()

        // .NET Runtime 状态
        val dotnetDir = File(filesDir, "dotnet")
        val dotnetStatus = if (dotnetDir.exists() && File(dotnetDir, "apphost").exists()) {
            val sharedDir = File(dotnetDir, "shared/Microsoft.NETCore.App")
            val versions = sharedDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
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

        // 运行时库状态
        val runtimeStatus = if (RuntimeLibraryLoader.isExtracted(context)) {
            val libs = RuntimeLibraryLoader.listExtractedLibraries(context)
            context.getString(R.string.asset_check_status_runtime_libs_installed, libs.size)
        } else {
            context.getString(R.string.asset_check_status_runtime_libs_not_extracted)
        }
        sb.appendLine(runtimeStatus)

        sb.toString()
    }
}
