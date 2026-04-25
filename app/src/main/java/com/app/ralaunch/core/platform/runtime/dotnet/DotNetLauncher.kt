package com.app.ralaunch.core.platform.runtime.dotnet

import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import org.koin.java.KoinJavaComponent

object DotNetLauncher {
    const val TAG = "DotNetLauncher"
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
    fun hostfxrLaunch(
        assemblyPath: String,
        args: Array<String>,
        dotNetRuntimeVersionOverride: String? = null
    ): Int {
        val runtimeManager: IRuntimeManagerServiceV2 =
            KoinJavaComponent.get(IRuntimeManagerServiceV2::class.java)
        val dotnetRuntime = resolveDotNetRuntime(
            runtimeManager = runtimeManager,
            versionOverride = dotNetRuntimeVersionOverride
        ) ?: run {
            AppLog.e(TAG, "Failed to resolve selected dotnet runtime")
            return -1
        }
        val dotnetRoot = dotnetRuntime.rootPath.toString()

        // Implementation to launch .NET assembly
        AppLog.i(TAG, "Launching .NET assembly at $assemblyPath with arguments: ${args.joinToString(", ")}")
        AppLog.i(TAG, "Using .NET root path: $dotnetRoot")
        AppLog.i(TAG, "Using .NET runtime version: ${dotnetRuntime.version}")

        EnvVarsManager.quickSetEnvVar("DOTNET_ROOT", dotnetRoot)
        CoreCLRConfig.applyConfigAndInitHooking()
        val compatEnabled = SettingsAccess.isCoreClrXiaomiCompatEnabled
        if (compatEnabled) {
            CoreHostHooks.initCompatHooks()
        }

        val compatEnvSnapshot = if (compatEnabled) {
            AppLog.w(
                TAG,
                "Applying Xiaomi CoreCLR compatibility env before first hostfxr initialization"
            )
            captureXiaomiCoreClrCompatEnv()
        } else {
            null
        }

        if (compatEnabled) {
            applyXiaomiCoreClrCompatEnv()
        } else {
            EnvVarsManager.quickSetEnvVar("RAL_CORECLR_XIAOMI_COMPAT", null)
        }

        DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot, dotnetRuntime.version)

        try {
            val exitCode = nativeDotNetLauncherHostfxrLaunch(assemblyPath, args, dotnetRoot)
            if (exitCode == 0) {
                AppLog.i(TAG, "Successfully launched .NET assembly.")
            } else {
                val errorMsg = getNativeDotNetLauncherHostfxrLastErrorMsg()
                AppLog.e(
                    TAG,
                    "Failed to launch .NET assembly. Exit code: $exitCode, Error: $errorMsg"
                )
            }
            return exitCode
        } finally {
            if (compatEnabled && compatEnvSnapshot != null) {
                restoreXiaomiCoreClrCompatEnv(compatEnvSnapshot)
            }
        }
    }

    private fun applyXiaomiCoreClrCompatEnv() {
        EnvVarsManager.quickSetEnvVars(
            "RAL_CORECLR_XIAOMI_COMPAT" to "1",

            // Keep diagnostics simple and reduce runtime init variance on affected devices.
            "DOTNET_EnableDiagnostics" to "0",
            "DOTNET_gcConcurrent" to "0",
            "DOTNET_TieredCompilation" to "0",
            "DOTNET_TC_QuickJit" to "0",
            // Thread_DefaultStackSize is parsed as hex by CoreCLR PAL.
            // "100000" (hex) == 1 MiB. Avoid decimal "1048576" (treated as 0x1048576 ~= 16 MiB).
            "DOTNET_Thread_DefaultStackSize" to "100000",
        )
    }

    private fun captureXiaomiCoreClrCompatEnv(): Map<String, String?> {
        return XIAOMI_COMPAT_ENV_KEYS.associateWith { EnvVarsManager.getEnvVar(it) }
    }

    private fun restoreXiaomiCoreClrCompatEnv(snapshot: Map<String, String?>) {
        EnvVarsManager.quickSetEnvVars(snapshot)
    }

    private fun resolveDotNetRuntime(
        runtimeManager: IRuntimeManagerServiceV2,
        versionOverride: String?
    ): IRuntimeManagerServiceV2.InstalledRuntime? {
        val normalizedOverride = versionOverride?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedOverride != null) {
            val overriddenRuntime = runtimeManager
                .getInstalledRuntimes(IRuntimeManagerServiceV2.RuntimeType.DOTNET)
                .firstOrNull { it.version == normalizedOverride }
            if (overriddenRuntime != null) {
                AppLog.i(TAG, "Using per-game .NET runtime override: $normalizedOverride")
                return overriddenRuntime
            }
            AppLog.w(
                TAG,
                "Requested .NET runtime override is not installed: $normalizedOverride, falling back to selected runtime"
            )
        }
        return runtimeManager.getSelectedRuntime(IRuntimeManagerServiceV2.RuntimeType.DOTNET)
    }

    private external fun getNativeDotNetLauncherHostfxrLastErrorMsg(): String
    private external fun nativeDotNetLauncherHostfxrLaunch(assemblyPath: String, args: Array<String>, dotnetRoot: String): Int
}
