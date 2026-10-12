package com.app.ralaunch.core.platform.runtime.mono

import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import org.koin.java.KoinJavaComponent
import java.io.File

/**
 * Mono 运行时启动器
 *
 * 与 [com.app.ralaunch.core.platform.runtime.dotnet.DotNetLauncher]（走 hostfxr/CoreCLR）并列，
 * 这里负责准备 Mono 需要的程序集搜索路径并调用 native 侧的 Mono 嵌入 API。
 */
object MonoLauncher {

    const val TAG = "MonoLauncher"

    /** BCL 安装目录名（runtimes/mono/<version>/bcl） */
    const val BCL_DIR_NAME = "bcl"

    /** BCL 中必须存在的核心程序集 */
    private const val CORE_LIBRARY = "mscorlib.dll"

    val lastErrorMsg: String
        get() = nativeMonoLauncherLastError()

    @Volatile
    private var isNativeLibrariesLoaded = false

    /**
     * 加载 Mono 原生库
     *
     * libmono-native.so 提供 mono 的 System.Native 等 DllImport 目标，
     * libmonosgen-2.0.so 是 JIT/运行时本体，两者都在 jniLibs 中随 APK 分发。
     */
    @Synchronized
    fun ensureNativeLibrariesLoaded(): Boolean {
        if (isNativeLibrariesLoaded) return true
        return try {
            System.loadLibrary("mono-native")
            AppLog.i(TAG, "  ✓ libmono-native.so 加载成功")
            System.loadLibrary("monosgen-2.0")
            AppLog.i(TAG, "  ✓ libmonosgen-2.0.so 加载成功")
            isNativeLibrariesLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            AppLog.e(TAG, "✗ 加载 Mono 原生库失败", e)
            false
        }
    }

    /**
     * 启动一个托管程序集
     *
     * @param assemblyPath 程序集路径
     * @param args 传递给程序集的参数
     * @param monoRuntimeVersionOverride 可选的每条目运行时版本覆盖
     * @return 程序集退出码，负数表示启动失败
     */
    fun launchAssembly(
        assemblyPath: String,
        args: Array<String>,
        monoRuntimeVersionOverride: String? = null
    ): Int {
        val runtimeManager: IRuntimeManagerServiceV2 =
            KoinJavaComponent.get(IRuntimeManagerServiceV2::class.java)

        val runtime = resolveMonoRuntime(runtimeManager, monoRuntimeVersionOverride) ?: run {
            AppLog.e(TAG, "无法解析可用的 Mono 运行时（请确认已完成初始化安装）")
            return -1
        }

        val monoRoot = runtime.rootPath.toString()
        val bclDir = File(runtime.rootPath.toFile(), BCL_DIR_NAME).absolutePath

        if (!File(bclDir, CORE_LIBRARY).exists()) {
            AppLog.e(TAG, "✗ Mono BCL 不完整，缺少 $CORE_LIBRARY: $bclDir")
        }

        AppLog.i(TAG, "========================================")
        AppLog.i(TAG, "使用 Mono 运行时")
        AppLog.i(TAG, "  Mono root: $monoRoot")
        AppLog.i(TAG, "  BCL 目录 : $bclDir")
        AppLog.i(TAG, "  程序集   : $assemblyPath")
        AppLog.i(TAG, "========================================")

        if (!ensureNativeLibrariesLoaded()) {
            return -1
        }

        // mono 在初始化早期会读取 MONO_PATH 查找 BCL 程序集
        EnvVarsManager.quickSetEnvVars(
            "MONO_PATH" to bclDir,
            "MONO_GAC_PREFIX" to monoRoot
        )

        // 写入 mono 的 DllMap 配置
        ensureMonoDllMapConfig(runtime.rootPath.toFile())

        // 应用设置里的 Mono 开关（LLVM / 详细日志）
        applyMonoRuntimeEnvVars()

        return try {
            val exitCode = nativeMonoLauncherLaunch(assemblyPath, args, monoRoot, bclDir)
            if (exitCode == 0) {
                AppLog.i(TAG, "Mono 程序集正常结束")
            } else {
                AppLog.e(TAG, "Mono 程序集结束，退出码: $exitCode，错误: ${lastErrorMsg}")
            }
            exitCode
        } catch (e: Exception) {
            AppLog.e(TAG, "启动 Mono 程序集失败", e)
            -1
        }
    }

    /**
     * 写入 mono 的 DllMap 配置
     *
     * Mono 的 BCL 会 P/Invoke `System.Native`（例如 System.Guid.NewGuid 走的
     * Interop+Sys）。桌面 mono 依赖 <config_dir>/etc/mono/config 里的 dllmap
     * 把它映射到 libmono-native.so；缺失时 mono 会按字面去找 libSystem.Native.so
     * 并抛 DllNotFoundException。这里补上该配置，同时 APK 内也提供了同名库作为兜底。
     */
    private fun ensureMonoDllMapConfig(monoRoot: File) {
        try {
            val configDir = File(monoRoot, "etc/mono")
            if (!configDir.exists() && !configDir.mkdirs()) {
                AppLog.w(TAG, "无法创建 mono 配置目录: $configDir")
                return
            }
            val configFile = File(configDir, "config")
            configFile.writeText(
                """<configuration>
  <!-- DllImport("System.Native") 解析到 mono 自带的 native 辅助库 -->
  <dllmap dll="System.Native" target="libmono-native.so" />
</configuration>
"""
            )
            AppLog.i(TAG, "已写入 mono DllMap 配置: ${configFile.absolutePath}")
        } catch (e: Exception) {
            AppLog.w(TAG, "写入 mono DllMap 配置失败（将依赖 APK 内的 libSystem.Native.so 兜底）", e)
        }
    }

    /**
     * 应用设置里的 Mono 可选环境变量
     *
     * - 启用 LLVM：MONO_ENV_OPTIONS=--llvm，让 Mono JIT 走 LLVM 后端
     * - 启用详细日志：MONO_LOG_LEVEL=debug + MONO_LOG_MASK=all
     */
    private fun applyMonoRuntimeEnvVars() {
        val settings = try {
            val repository: ISettingsRepositoryServiceV2 =
                KoinJavaComponent.get(ISettingsRepositoryServiceV2::class.java)
            repository.Settings
        } catch (e: Exception) {
            AppLog.w(TAG, "读取 Mono 环境变量设置失败，使用默认值", e)
            null
        } ?: return

        val llvmEnabled = settings.monoLlvmEnabled
        val verboseLogging = settings.monoVerboseLoggingEnabled

        AppLog.i(TAG, "Mono 环境变量 / Mono env: llvm=$llvmEnabled, verboseLog=$verboseLogging")

        EnvVarsManager.quickSetEnvVars(
            "MONO_ENV_OPTIONS" to if (llvmEnabled) "--llvm" else null,
            "MONO_LOG_LEVEL" to if (verboseLogging) "debug" else null,
            "MONO_LOG_MASK" to if (verboseLogging) "all" else null
        )
    }

    private fun resolveMonoRuntime(
        runtimeManager: IRuntimeManagerServiceV2,
        versionOverride: String?
    ): IRuntimeManagerServiceV2.InstalledRuntime? {
        val installed = runtimeManager.getInstalledRuntimes(IRuntimeManagerServiceV2.RuntimeType.MONO)

        val normalizedOverride = versionOverride?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedOverride != null) {
            val overridden = installed.firstOrNull { it.version == normalizedOverride }
            if (overridden != null) {
                AppLog.i(TAG, "使用每条目的 Mono 运行时覆盖: $normalizedOverride")
                return overridden
            }
            AppLog.w(TAG, "请求的 Mono 运行时覆盖未安装: $normalizedOverride，回退到已选运行时")
        }
        return runtimeManager.getSelectedRuntime(IRuntimeManagerServiceV2.RuntimeType.MONO)
    }

    private external fun nativeMonoLauncherLastError(): String

    private external fun nativeMonoLauncherLaunch(
        assemblyPath: String,
        args: Array<String>,
        monoRoot: String,
        bclDir: String
    ): Int
}
