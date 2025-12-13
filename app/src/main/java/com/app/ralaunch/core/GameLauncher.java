package com.app.ralaunch.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.system.Os;

import com.app.ralaunch.game.AssemblyPatcher;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.RuntimeManager;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralib.patch.Patch;
import com.app.ralib.patch.PatchManager;

import java.io.File;
import java.util.List;

/**
 *
 * <p>此类负责启动 .NET 应用程序（游戏），支持以下特性：
 * <ul>
 *   <li>使用 netcorehost API 直接启动程序集</li>
 *   <li>多版本运行时支持（.NET 6/7/8/9/10）</li>
 *   <li>运行时版本自动选择和手动指定</li>
 *   <li>程序集自动替换（通过MonoMod_Patch.zip）</li>
 * </ul>
 *
 * <p>启动流程：
 * <ol>
 *   <li>Java 层设置启动参数（程序集路径、运行时路径等）</li>
 *   <li>参数通过 JNI 传递给 Native 层</li>
 *   <li>Native 层使用 netcorehost 加载 hostfxr</li>
 *   <li>hostfxr 初始化 .NET 运行时并执行程序集</li>
 * </ol>
 *
 * @author RA Launcher Team
 * @see com.app.ralaunch.utils.RuntimeManager
 */
public class GameLauncher {
    private static final String TAG = "GameLauncher";

    // 静态加载 native 库
    static {
        try {
            // [WARN] 重要：根据渲染器设置预加载正确的 GL 库
            // 必须在 SDL2 之前加载，这样 SDL 才能找到正确的 EGL 实现
            preloadRendererLibrary();

            System.loadLibrary("netcorehost");
            System.loadLibrary("SDL2");

            System.loadLibrary("main");

            AppLogger.info(TAG, "Native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            AppLogger.error(TAG, "Failed to load native libraries: " + e.getMessage());
        }
    }

    /**
     * 根据用户设置预加载渲染器库
     *
     * 这个方法必须在 SDL2 加载之前调用，因为：
     * 1. SDL 在初始化时会查找 EGL 库
     * 2. 如果我们先加载了 gl4es 的 libGL.so，SDL 会优先使用它
     * 3. 否则 SDL 会使用系统的 libEGL.so
     */
    private static void preloadRendererLibrary() {
        try {
            // 获取当前渲染器设置（但不获取Context，避免初始化问题）
            // 我们假设用户在启动游戏前已经通过设置界面配置好了
            // 如果没有配置，默认使用原生渲染器（不需要预加载）

            // 尝试读取渲染器偏好（通过SharedPreferences，不需要Context）
            String renderer = System.getProperty("fna.renderer", "auto");

            if ("opengl_gl4es".equals(renderer)) {
                // 预加载 gl4es 渲染器
                AppLogger.info(TAG, "Preloading gl4es renderer...");
                System.loadLibrary("GL");  // libGL.so (gl4es)
                AppLogger.info(TAG, "gl4es renderer loaded successfully");
            } else {
                // 原生渲染器或自动模式：不需要预加载
                // SDL 会自动使用系统的 libEGL.so
                AppLogger.info(TAG, "Using native renderer (system EGL)");
            }
        } catch (UnsatisfiedLinkError e) {
            AppLogger.warn(TAG, "Failed to preload renderer library: " + e.getMessage());
            AppLogger.warn(TAG, "Will fallback to system native renderer");
        }
    }

    /**
     * netcorehost API：设置启动参数（简化版 - 4个参数）
     *
     * @param appDir 应用程序目录
     * @param mainAssembly 主程序集名称（如 "MyGame.dll"）
     * @param dotnetRoot .NET 运行时根目录（可为 null）
     * @param frameworkMajor 首选框架主版本号（0 = 自动选择最高版本）
     * @return 0 成功，负数失败
     */
    public static native int netcorehostSetParams(
            String appDir,
            String mainAssembly,
            String dotnetRoot,
            int frameworkMajor);

    /**
     * netcorehost API：设置启动参数（带命令行参数）
     *
     * @param appDir 应用程序目录
     * @param mainAssembly 主程序集名称（如 "MyGame.dll"）
     * @param dotnetRoot .NET 运行时根目录（可为 null）
     * @param frameworkMajor 首选框架主版本号（0 = 自动选择最高版本）
     * @param args 命令行参数数组
     * @return 0 成功，负数失败
     */
    public static native int netcorehostSetParamsWithArgs(
            String appDir,
            String mainAssembly,
            String dotnetRoot,
            int frameworkMajor,
            String[] args);
    
    /**
     * netcorehost API：启动应用
     * @return 应用退出码
     */
    public static native int netcorehostLaunch();


    /**
     * netcorehost API：设置 DOTNET_STARTUP_HOOKS 补丁路径
     *
     * @param startupHooksDll 补丁DLL的完整路径 (null 表示清除)
     */
    public static native void netcorehostSetStartupHooks(String startupHooksDll);

    /**
     * netcorehost API：设置是否启用 COREHOST_TRACE
     *
     * @param enabled true 启用详细日志, false 禁用
     */
    public static native void netcorehostSetCorehostTrace(boolean enabled);




    /**
     * 直接启动 .NET 程序集（支持自定义补丁配置）
     *
     * <p>此方法直接启动指定的 .NET 程序集，并在启动前应用 MonoMod 补丁和启用的自定义补丁
     *
     * @param context Android 上下文
     * @param assemblyPath 程序集完整路径
     * @param enabledPatches 启用的补丁列表（如果为null则仅应用MonoMod补丁）
     * @return 0 表示参数设置成功，-1 表示失败
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static int launchAssemblyDirect(Context context, String assemblyPath, List<Patch> enabledPatches) {
        AppLogger.info(TAG, "================================================");
        AppLogger.info(TAG, "Preparing to launch assembly directly");
        AppLogger.info(TAG, "================================================");
        AppLogger.info(TAG, "Assembly path: " + assemblyPath);

        try {
            File assemblyFile = new File(assemblyPath);

            // 验证程序集文件存在
            if (!assemblyFile.exists()) {
                AppLogger.error(TAG, "Assembly file not found: " + assemblyPath);
                return -1;
            }
            // 获取应用目录和主程序集名称
            String appDir = assemblyFile.getParent();
            String mainAssembly = assemblyFile.getName();

            AppLogger.info(TAG, "Application directory: " + appDir);
            AppLogger.info(TAG, "Main assembly: " + mainAssembly);

            // Step 1: 应用补丁文件替换（MonoMod + 自定义补丁）
            AppLogger.info(TAG, "");
            AppLogger.info(TAG, "Step 1/3: Applying patch files");
            if (enabledPatches != null && !enabledPatches.isEmpty()) {
                AppLogger.info(TAG, "Enabled custom patches: " + enabledPatches.size());
                for (Patch patch : enabledPatches) {
                    AppLogger.info(TAG, String.format("  - %s (id: %s)", patch.manifest.name, patch.manifest.id));
                }
            }
            AssemblyPatcher.applyMonoModPatches(context, appDir);
            String dotnetRoot = RuntimePreference.getDotnetRootPath();

            String selectedVersion = RuntimeManager.getSelectedVersion(context);
            int frameworkMajor = 0;
            if (selectedVersion != null && !selectedVersion.isEmpty()) {
                try {

                    frameworkMajor = Integer.parseInt(selectedVersion.split("\\.")[0]);
                } catch (Exception e) {
                    AppLogger.error(TAG, "Failed to parse framework version: " + selectedVersion, e);
                    frameworkMajor = 10;
                }
            } else {
                AppLogger.warn(TAG, "No runtime version selected, using default .NET 10");
                frameworkMajor = 10;
            }

            AppLogger.info(TAG, ".NET path: " + (dotnetRoot != null ? dotnetRoot : "(auto-detect)"));
            AppLogger.info(TAG, "Selected version: " + selectedVersion);
            AppLogger.info(TAG, "Framework major: " + frameworkMajor);
            AppLogger.info(TAG, "================================================");



            // 加载所有必需的 .NET Native 库
            // 包括：System.Native (socket等), Cryptography (TLS/SSL), 等等
            AppLogger.info(TAG, "Loading .NET Native libraries...");

            if (!com.app.ralaunch.netcore.DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot, selectedVersion)) {
                AppLogger.error(TAG, ".NET Native library loading failed! Network and crypto features may not work");
            }

            // 设置 DOTNET_STARTUP_HOOKS 补丁（在 hostfxr 初始化之前）
            if (enabledPatches != null && !enabledPatches.isEmpty()) {
                AppLogger.info(TAG, "Configuring DOTNET_STARTUP_HOOKS patches...");

                var startupHooksEnvVar = PatchManager.constructStartupHooksEnvVar(enabledPatches);

                AppLogger.info(TAG, "DOTNET_STARTUP_HOOKS: " + startupHooksEnvVar);
                netcorehostSetStartupHooks(startupHooksEnvVar);
            } else {
                AppLogger.info(TAG, "No patches to configure for DOTNET_STARTUP_HOOKS");
                netcorehostSetStartupHooks("");
            }

            // 应用 CoreCLR 配置（GC 和 JIT 设置）
            AppLogger.info(TAG, "");
            AppLogger.info(TAG, "Applying CoreCLR configuration...");
            com.app.ralaunch.utils.CoreCLRConfig.applyConfig(context);

            // 设置 COREHOST_TRACE（根据详细日志设置）
            com.app.ralaunch.data.SettingsManager settingsManager = com.app.ralaunch.data.SettingsManager.getInstance(context);
            boolean enableVerboseLogging = settingsManager.isVerboseLogging();
            netcorehostSetCorehostTrace(enableVerboseLogging);
            AppLogger.info(TAG, "COREHOST_TRACE: " + (enableVerboseLogging ? "启用" : "禁用"));

            // 设置 线程亲和性
            com.app.ralaunch.data.SettingsManager dataSettingsManager = com.app.ralaunch.data.SettingsManager.getInstance(context);
            if (dataSettingsManager.getSetThreadAffinityToBigCoreEnabled()) {
                Os.setenv("SET_THREAD_AFFINITY_TO_BIG_CORE", "1", true);
                AppLogger.info(TAG, "SET_THREAD_AFFINITY_TO_BIG_CORE: 启用");
            }
            else {
                Os.unsetenv("SET_THREAD_AFFINITY_TO_BIG_CORE");
                AppLogger.info(TAG, "SET_THREAD_AFFINITY_TO_BIG_CORE: 禁用");
            }

            // 设置启动参数（简化版 - 4个参数）
            int result = netcorehostSetParams(appDir, mainAssembly, dotnetRoot, frameworkMajor);

            if (result != 0) {
                AppLogger.error(TAG, "Failed to set launch parameters: " + result);
                return -1;
            }

            AppLogger.info(TAG, "Launch parameters set successfully");
            return 0;

        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to launch assembly", e);
            return -1;
        }
    }
}