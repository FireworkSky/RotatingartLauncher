package com.app.ralaunch.core.platform.runtime.dotnet

import android.os.Build
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.RuntimePreference
import java.util.Locale

object DotNetLauncher {
    const val TAG = "DotNetLauncher"
    private const val CORECLR_INIT_FAILURE_EXIT_CODE = -2147450743
    private val XIAOMI_COMPAT_ENV_KEYS = arrayOf(
        "RAL_CORECLR_XIAOMI_COMPAT",
        "DOTNET_EnableDiagnostics",
        "DOTNET_gcConcurrent",
        "DOTNET_TieredCompilation",
        "DOTNET_TC_QuickJit",
        "DOTNET_Thread_DefaultStackSize",
    )

    val hostfxrLastErrorMsg: String
        get() = getNativeDotNetLauncherHostfxrLastErrorMsg()

    /**
     * 启动 .NET 程序集
     * 在这里负责设置底层 runtime 环境变量并调用底层启动器
     * 如果需要进行游戏相关环境准备，请在 GameLauncher.launchDotNetAssembly 中进行
     * 不要在这里进行游戏相关环境准备，以免影响其他程序集的运行
     * @param assemblyPath 程序集路径
     * @param args 传递给程序集的参数
     * @return 程序集退出代码
     */
    fun hostfxrLaunch(assemblyPath: String, args: Array<String>): Int {
        val dotnetRoot = RuntimePreference.getDotnetRootPath() ?: run {
            AppLogger.error(TAG, "Failed to get dotnet root path")
            return -1
        }

        // Implementation to launch .NET assembly
        AppLogger.info(TAG, "Launching .NET assembly at $assemblyPath with arguments: ${args.joinToString(", ")}")
        AppLogger.info(TAG, "Using .NET root path: $dotnetRoot")

        EnvVarsManager.quickSetEnvVar("DOTNET_ROOT", dotnetRoot)
        CoreCLRConfig.applyConfigAndInitHooking()
        val compatEnabled = SettingsAccess.isCoreClrXiaomiCompatEnabled
        if (compatEnabled) {
            CoreHostHooks.initCompatHooks()
        }
        DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot)

        var exitCode = nativeDotNetLauncherHostfxrLaunch(assemblyPath, args, dotnetRoot)
        if (exitCode == 0) {
            AppLogger.info(TAG, "Successfully launched .NET assembly.")
            return exitCode
        }

        var errorMsg = getNativeDotNetLauncherHostfxrLastErrorMsg()
        AppLogger.error(TAG, "Failed to launch .NET assembly. Exit code: $exitCode, Error: $errorMsg")

        if (shouldRetryWithXiaomiCompat(assemblyPath, exitCode, errorMsg, compatEnabled)) {
            AppLogger.warn(TAG, "Detected Xiaomi CoreCLR init failure on tModLoader, retrying with compat hooks and conservative runtime env")
            val compatEnvSnapshot = captureXiaomiCoreClrCompatEnv()
            applyXiaomiCoreClrCompatEnv()

            try {
                exitCode = nativeDotNetLauncherHostfxrLaunch(assemblyPath, args, dotnetRoot)
                if (exitCode == 0) {
                    AppLogger.info(TAG, "Compatibility retry succeeded.")
                    return exitCode
                }

                errorMsg = getNativeDotNetLauncherHostfxrLastErrorMsg()
                AppLogger.error(TAG, "Compatibility retry failed. Exit code: $exitCode, Error: $errorMsg")
            } finally {
                restoreXiaomiCoreClrCompatEnv(compatEnvSnapshot)
            }
        }

        return exitCode
    }

    private fun shouldRetryWithXiaomiCompat(
        assemblyPath: String,
        exitCode: Int,
        errorMsg: String,
        compatEnabled: Boolean
    ): Boolean {
        if (!compatEnabled) return false
        if (!isTModLoaderAssembly(assemblyPath)) return false
        if (!isXiaomiFamilyDevice()) return false
        if (exitCode != CORECLR_INIT_FAILURE_EXIT_CODE) return false

        val normalizedError = errorMsg.lowercase(Locale.ROOT)
        return normalizedError.contains("coreclr_initialize failed") ||
            normalizedError.contains("failed to create coreclr") ||
            normalizedError.contains("0x8007054f")
    }

    private fun isTModLoaderAssembly(assemblyPath: String): Boolean {
        return assemblyPath.lowercase(Locale.ROOT).contains("tmodloader")
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        val brand = Build.BRAND.orEmpty().lowercase(Locale.ROOT)
        return manufacturer.contains("xiaomi") ||
            brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco")
    }

    private fun applyXiaomiCoreClrCompatEnv() {
        EnvVarsManager.quickSetEnvVars(
            "RAL_CORECLR_XIAOMI_COMPAT" to "1",

            // Keep diagnostics simple and reduce runtime init variance on affected devices.
            "DOTNET_EnableDiagnostics" to "0",
            "DOTNET_gcConcurrent" to "0",
            "DOTNET_TieredCompilation" to "0",
            "DOTNET_TC_QuickJit" to "0",
            "DOTNET_Thread_DefaultStackSize" to "1048576",
        )
    }

    private fun captureXiaomiCoreClrCompatEnv(): Map<String, String?> {
        return XIAOMI_COMPAT_ENV_KEYS.associateWith { EnvVarsManager.getEnvVar(it) }
    }

    private fun restoreXiaomiCoreClrCompatEnv(snapshot: Map<String, String?>) {
        EnvVarsManager.quickSetEnvVars(snapshot)
    }

    private external fun getNativeDotNetLauncherHostfxrLastErrorMsg(): String
    private external fun nativeDotNetLauncherHostfxrLaunch(assemblyPath: String, args: Array<String>, dotnetRoot: String): Int
}
