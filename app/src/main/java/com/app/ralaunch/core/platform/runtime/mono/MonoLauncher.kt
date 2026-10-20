package com.app.ralaunch.core.platform.runtime.mono

import android.content.Context
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
            // RAL: mono 的 System.Drawing 走 GDI+（tModLoader 1.3 会用到），需要 libgdiplus。
            // 与 FNA3D/FAudio 同理，游戏侧 P/Invoke 是 dlopen("libgdiplus.so")，
            // bionic 按文件名找不到时会回退到「已加载库的 SONAME 匹配」，故先预载。
            System.loadLibrary("fontconfig")
            AppLog.i(TAG, "  ✓ libfontconfig.so 加载成功")
            System.loadLibrary("gdiplus")
            AppLog.i(TAG, "  ✓ libgdiplus.so 加载成功")
            // RAL: System.IO.Compression（DeflateStream/GZipStream）在 mono 上走
            // MonoPosixHelper 的 zlib 封装（CalamityMod 读 .csc 结构图时会用到）。
            // 随包分发一个精简版（只含 zlib 那几个导出），dllmap 把 MonoPosixHelper 指过来。
            System.loadLibrary("MonoPosixHelper")
            AppLog.i(TAG, "  ✓ libMonoPosixHelper.so 加载成功")
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

        // 写入 mono 的 DllMap 配置（含 System.Drawing → libgdiplus 的映射）
        ensureMonoDllMapConfig(runtime.rootPath.toFile())

        // libgdiplus 用的 fontconfig 配置（Android 上没有 /etc/fonts）
        ensureFontconfigConfig(runtime.rootPath.toFile())

        // 补齐 BCL 缺的 WPF/反射相关程序集（tModLoader 1.3 的模组配置与 !Localizer 会用到）
        ensureBclExtras(runtime.rootPath.toFile(), bclDir)

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

            // RAL: 需要的 dllmap。不能整体覆盖已有 config —— 运行时目录里如果已有
            // 自带 config（含 libc/libm 等映射），覆盖会连带丢掉它们。
            val requiredMaps = listOf(
                // mono BCL 的 System.Native（GUID/DNS 等 Interop+Sys）
                Triple("System.Native", "libmono-native.so", null),
                // System.Drawing → 交叉编译的 libgdiplus（GDI+），tModLoader 1.3 依赖
                Triple("gdiplus", "libgdiplus.so", "!windows"),
                Triple("gdiplus.dll", "libgdiplus.so", "!windows"),
                Triple("gdi32", "libgdiplus.so", "!windows"),
                Triple("gdi32.dll", "libgdiplus.so", "!windows"),
                // Mono.Posix：只有提供 helper 库时才能解析，缺失只影响用到它的游戏
                Triple("MonoPosixHelper", "libMonoPosixHelper.so", "!windows"),
            )

            fun render(maps: List<Triple<String, String, String?>>): String =
                maps.joinToString("\n") { (dll, target, os) ->
                    val osAttr = os?.let { " os=\"$it\"" } ?: ""
                    "  <dllmap dll=\"$dll\" target=\"$target\"$osAttr />"
                }

            val existing = if (configFile.exists()) runCatching { configFile.readText() }.getOrDefault("") else ""
            val missing = requiredMaps.filter { it.first.let { dll -> !existing.contains("dll=\"$dll\"") } }

            if (existing.isBlank()) {
                configFile.writeText("<configuration>\n${render(requiredMaps)}\n</configuration>\n")
                AppLog.i(TAG, "已写入 mono DllMap 配置: ${configFile.absolutePath}")
                return
            }
            if (missing.isEmpty()) {
                AppLog.i(TAG, "mono DllMap 配置已包含所需映射: ${configFile.absolutePath}")
                return
            }
            val additions = render(missing)
            val merged = if (existing.contains("</configuration>")) {
                existing.replace("</configuration>", "$additions\n</configuration>")
            } else {
                existing + "\n<configuration>\n$additions\n</configuration>\n"
            }
            configFile.writeText(merged)
            AppLog.i(TAG, "已向 mono DllMap 配置补写 ${missing.size} 条映射: ${configFile.absolutePath}")
        } catch (e: Exception) {
            AppLog.w(TAG, "写入 mono DllMap 配置失败（将依赖 APK 内的 libSystem.Native.so 兜底）", e)
        }
    }

    /**
     * 写入 fontconfig 运行期配置并设置 FONTCONFIG_FILE
     *
     * libgdiplus 依赖 fontconfig 定位字体；Android 没有 /etc/fonts，fontconfig 的
     * 编译期默认路径在设备上不存在，FcInit 会失败（文本渲染相关 API 直接报错）。
     * 这里生成一份只指向 /system/fonts 的配置，缓存目录走 fontconfig 默认逻辑
     * （$XDG_CACHE_HOME/fontconfig），由 GameLauncher 保证 XDG_CACHE_HOME 可写。
     */
    private fun ensureFontconfigConfig(monoRoot: File) {
        try {
            if (!System.getenv("FONTCONFIG_FILE").isNullOrBlank()) {
                AppLog.i(TAG, "FONTCONFIG_FILE 已由外部设置，跳过默认字体配置")
                return
            }
            val confDir = File(monoRoot, "etc/fonts")
            if (!confDir.exists() && !confDir.mkdirs()) {
                AppLog.w(TAG, "无法创建 fontconfig 配置目录: $confDir")
                return
            }
            val confFile = File(confDir, "fonts.conf")
            confFile.writeText(
                """<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
<!-- RAL: libgdiplus 的 fontconfig 配置（Android 无 /etc/fonts） -->
<fontconfig>
  <dir>/system/fonts</dir>
</fontconfig>
"""
            )
            EnvVarsManager.quickSetEnvVars("FONTCONFIG_FILE" to confFile.absolutePath)
            AppLog.i(TAG, "已设置 FONTCONFIG_FILE = ${confFile.absolutePath}")
        } catch (e: Exception) {
            AppLog.w(TAG, "写入 fontconfig 配置失败（System.Drawing 字体渲染可能不可用）", e)
        }
    }

    /**
     * 补齐 Mono BCL 里缺失的程序集
     *
     * 随包分发的 mono-bcl 只覆盖了基础 BCL；而 tModLoader 1.3 的模组配置（Newtonsoft 会
     * 通过 TypeConverter 转换 FNA 的 Color）需要 System.ComponentModel.TypeConverter，
     * !Localizer 这类模组还会触发 WindowsBase/System.Xaml 链。缺这些程序集时表现为
     * FileNotFoundException / 配置反序列化失败，模组会被自动禁用。
     *
     * assets/bcl-extra 里的程序集取自同版本（mono 6.14）的 mono 构建，按需复制进 bcl 目录。
     */
    private fun ensureBclExtras(monoRoot: File, bclDir: String) {
        try {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            val names = context.assets.list("bcl-extra").orEmpty()
            if (names.isEmpty()) return
            val bcl = File(bclDir)
            if (!bcl.isDirectory) return
            var copied = 0
            names.filter { it.endsWith(".dll", ignoreCase = true) }.forEach { name ->
                val target = File(bcl, name)
                if (target.exists()) return@forEach
                try {
                    context.assets.open("bcl-extra/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    copied++
                } catch (e: Exception) {
                    AppLog.w(TAG, "补齐 BCL 程序集失败 $name: ${e.message}")
                }
            }
            if (copied > 0) {
                AppLog.i(TAG, "已补齐 $copied 个 BCL 程序集到 $bclDir")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "补齐 BCL 程序集失败: ${e.message}")
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

        // RAL: SGen 默认 nursery 只有 4MB，游戏里一帧分配量稍大就触发 minor GC，
        // 表现为「一段一段地卡」（每次卡一小下）。这里放大 nursery 并让 major GC
        // 走并发 + 并行标记，明显减少停顿；用户/游戏的 gameEnvVars 里已经指定时以他们为准。
        //   - nursery-size：越大 minor GC 越少（进世界/保存时的大块分配不会再几帧一次回收）
        //   - minor=split：每线程独立 nursery，多核（8 Elite）上分配不再抢同一把锁
        //   - concurrent-sweep：清扫与程序并发，缩短 major GC 的停顿
        //   - major=marksweep-conc-par：major 标记并发 + 并行
        val currentGcParams = System.getenv("MONO_GC_PARAMS")?.trim().orEmpty()
        if (currentGcParams.isEmpty()) {
            val gcParams = "major=marksweep-conc-par,nursery-size=128M"
            EnvVarsManager.quickSetEnvVar("MONO_GC_PARAMS", gcParams)
            AppLog.i(TAG, "MONO_GC_PARAMS = $gcParams")
        } else {
            AppLog.i(TAG, "MONO_GC_PARAMS 已由外部设置: $currentGcParams")
        }

        // 注：minor=split / concurrent-sweep / MONO_THREADS_SUSPEND=coop 在这份 mono
        // （Android 嵌入式 + 大量 detour）上会偶发 native 崩溃（FORTIFY mutex destroyed），
        // 不要默认打开；只保留最稳的 concurrent-par major + 大 nursery。
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
