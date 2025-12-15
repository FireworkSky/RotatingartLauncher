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


public class GameLauncher {
    private static final String TAG = "GameLauncher";

    // 静态加载 native 库
    static {
        try {
            // [WARN] 重要：根据渲染器设置预加载正确的 GL 库
            // 必须在 SDL2 之前加载，这样 SDL 才能找到正确的 EGL 实现
            preloadRendererLibrary();

            System.loadLibrary("netcorehost");
            System.loadLibrary("FAudio");
            System.loadLibrary("theorafile");
            System.loadLibrary("SDL2");
            System.loadLibrary("main");
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

            String renderer = System.getProperty("fna.renderer", "auto");

            if ("opengl_gl4es".equals(renderer)) {
                System.loadLibrary("GL");
            }
        } catch (UnsatisfiedLinkError e) {
            // Fallback to system native renderer
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
        try {
            File assemblyFile = new File(assemblyPath);

            if (!assemblyFile.exists()) {
                AppLogger.error(TAG, "Assembly file not found: " + assemblyPath);
                return -1;
            }
            String appDir = assemblyFile.getParent();
            String mainAssembly = assemblyFile.getName();

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
                frameworkMajor = 10;
            }

            if (!com.app.ralaunch.netcore.DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot, selectedVersion)) {
                AppLogger.error(TAG, ".NET Native library loading failed! Network and crypto features may not work");
            }

            if (enabledPatches != null && !enabledPatches.isEmpty()) {
                var startupHooksEnvVar = PatchManager.constructStartupHooksEnvVar(enabledPatches);
                netcorehostSetStartupHooks(startupHooksEnvVar);
            } else {
                netcorehostSetStartupHooks("");
            }

            com.app.ralaunch.utils.CoreCLRConfig.applyConfig(context);

            com.app.ralaunch.data.SettingsManager settingsManager = com.app.ralaunch.data.SettingsManager.getInstance(context);
            boolean enableVerboseLogging = settingsManager.isVerboseLogging();
            netcorehostSetCorehostTrace(enableVerboseLogging);

            com.app.ralaunch.data.SettingsManager dataSettingsManager = com.app.ralaunch.data.SettingsManager.getInstance(context);
            if (dataSettingsManager.getSetThreadAffinityToBigCoreEnabled()) {
                Os.setenv("SET_THREAD_AFFINITY_TO_BIG_CORE", "1", true);
            } else {
                Os.unsetenv("SET_THREAD_AFFINITY_TO_BIG_CORE");
            }

            int result = netcorehostSetParams(appDir, mainAssembly, dotnetRoot, frameworkMajor);

            if (result != 0) {
                AppLogger.error(TAG, "Failed to set launch parameters: " + result);
                return -1;
            }

            return 0;

        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to launch assembly", e);
            return -1;
        }
    }
}