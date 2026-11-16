package com.app.ralaunch.core.platform.runtime.dotnet

import android.content.Context
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.di.service.StoragePathsProviderServiceV1
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.core.logging.AppLog
import java.io.File
import org.koin.java.KoinJavaComponent

/**
 * CoreCLR 配置工具类
 *
 * 负责将用户设置的 CoreCLR 配置应用到环境变量中，
 * 这些环境变量会在 .NET 运行时启动时被读取。
 *
 * 支持的配置项：
 * - GC 配置：Server GC、Concurrent GC、Heap Count、Retain VM
 * - JIT 配置：Tiered Compilation、Quick JIT、Optimize Type
 */
object CoreCLRConfig {
    private const val TAG = "CoreCLRConfig"

    /**
     * 应用 CoreCLR 配置到 native 层
     * 此方法需要在启动 .NET 运行时之前调用
     *
     * @param context Android Context
     */
    fun applyConfigAndInitHooking() {
        val settings = SettingsAccess
        val paths: StoragePathsProviderServiceV1 = KoinJavaComponent.get(StoragePathsProviderServiceV1::class.java)
        val logDirectory = File(paths.logsDirPathFull()).apply { mkdirs() }
        val verbose = settings.isLogSystemEnabled && settings.isVerboseLogging
        if (verbose) {
            val trace = File(logDirectory, LogFilePolicy.COREHOST_TRACE_FILE)
            runCatching {
                if (trace.exists()) trace.copyTo(File(logDirectory, "${trace.name}.previous"), overwrite = true)
                trace.delete()
            }.onFailure { AppLog.w(TAG, "Cannot rotate host trace; game launch will continue", it) }
        }
        EnvVarsManager.quickSetEnvVars(loggingEnvironment(verbose, logDirectory.absolutePath))
        EnvVarsManager.quickSetEnvVar("RAL_DOTNET_OUTPUT_FILE", if (settings.isLogSystemEnabled) {
            File(logDirectory, LogFilePolicy.DOTNET_OUTPUT_FILE).absolutePath
        } else null)
        EnvVarsManager.quickSetEnvVars(
            // 应用 GC 配置
            "DOTNET_gcServer" to if (settings.isServerGC) "1" else "0",
            "DOTNET_gcConcurrent" to if (settings.isConcurrentGC) "1" else "0",
            "DOTNET_GCHeapCount" to settings.gcHeapCount.takeIf { it != "auto" },
            "DOTNET_GCRetainVM" to if (settings.isRetainVM) "1" else "0",

            // 应用 JIT 配置
            "DOTNET_TieredCompilation" to if (settings.isTieredCompilation) "1" else "0",
            "DOTNET_TC_QuickJit" to if (settings.isTieredCompilation && settings.isQuickJIT) "1" else "0",
            "DOTNET_JitOptimizeType" to settings.jitOptimizeType.toString(),

            // 应用版本前滚策略
            "DOTNET_ROLL_FORWARD" to "LatestMajor",
            "DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX" to "2",
            "DOTNET_ROLL_FORWARD_TO_PRERELEASE" to "1",
        )

        AppLog.i(TAG, "Runtime diagnostics: host/CoreCLR trace=$verbose")
    }

    internal fun loggingEnvironment(verbose: Boolean, directory: String): Map<String, String?> = buildMap {
        for (prefix in listOf("COREHOST_", "DOTNET_HOST_")) {
            put("${prefix}TRACE", if (verbose) "1" else "0")
            put("${prefix}TRACE_VERBOSITY", if (verbose) "4" else null)
            put("${prefix}TRACEFILE", if (verbose) File(directory, LogFilePolicy.COREHOST_TRACE_FILE).absolutePath else null)
        }
        for (prefix in listOf("DOTNET_", "COMPlus_")) {
            put("${prefix}LogEnable", if (verbose) "1" else "0")
            put("${prefix}LogToConsole", if (verbose) "1" else "0")
            put("${prefix}LogLevel", if (verbose) "a" else null) // CoreCLR DWORD values use hexadecimal: a = 10.
            put("${prefix}LogFacility", if (verbose) "ffffffff" else null)
            put("${prefix}LogFacility2", if (verbose) "ffffffff" else null)
            put("${prefix}LogToFile", "0")
        }
        put("COMPlus_DebugWriteToStdErr", if (verbose) "1" else "0")
    }

    /**
     * 获取当前 CoreCLR 配置的摘要信息
     *
     * @param context Android Context
     * @return 配置摘要字符串
     */
    fun getConfigSummary(context: Context?): String {
        val settings = SettingsAccess
        val sb = StringBuilder()

        sb.append("CoreCLR 配置摘要:\n")
        sb.append("  GC:\n")
        sb.append("    Server GC: ").append(if (settings.isServerGC) "启用" else "关闭")
            .append("\n")
        sb.append("    Concurrent GC: ").append(if (settings.isConcurrentGC) "启用" else "关闭")
            .append("\n")
        sb.append("    Heap Count: ").append(settings.gcHeapCount).append("\n")
        sb.append("    Retain VM: ").append(if (settings.isRetainVM) "启用" else "关闭")
            .append("\n")
        sb.append("  JIT:\n")
        sb.append("    Tiered Compilation: ")
            .append(if (settings.isTieredCompilation) "启用" else "关闭").append("\n")
        sb.append("    Quick JIT: ").append(if (settings.isQuickJIT) "启用" else "关闭")
            .append("\n")

        val optimizeType = settings.jitOptimizeType
        val optimizeTypeName: String?
        when (optimizeType) {
            1 -> optimizeTypeName = "体积优先"
            2 -> optimizeTypeName = "速度优先"
            else -> optimizeTypeName = "混合"
        }
        sb.append("    Optimize Type: ").append(optimizeTypeName).append("\n")

        return sb.toString()
    }
}
