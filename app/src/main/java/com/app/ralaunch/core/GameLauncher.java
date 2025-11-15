package com.app.ralaunch.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.app.ralaunch.game.AssemblyPatcher;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.RuntimeManager;
import com.app.ralaunch.utils.RuntimePreference;

import java.io.File;
import java.nio.file.Paths;

/**
 * .NET 游戏启动器（简化版）
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
     * netcorehost API：启动应用
     *
     * @return 应用退出码
     */
    public static native int netcorehostLaunch();

    /**
     * netcorehost API：清理资源
     */
    public static native void netcorehostCleanup();

    /**
     * 直接启动 .NET 程序集（简化版 + MonoMod补丁）
     *
     * <p>此方法直接启动指定的 .NET 程序集，并在启动前自动应用 MonoMod_Patch.zip 中的补丁
     *
     * @param context Android 上下文
     * @param assemblyPath 程序集完整路径
     * @return 0 表示参数设置成功，-1 表示失败
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static int launchAssemblyDirect(Context context, String assemblyPath) {
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

            // Step 1: 应用 MonoMod 补丁
            AppLogger.info(TAG, "");
            AppLogger.info(TAG, "Step 1/2: Applying MonoMod patches");
            int patchedCount = AssemblyPatcher.applyPatches(context, appDir);

            if (patchedCount < 0) {
                AppLogger.warn(TAG, "Patch application failed, but will continue to launch");
            } else if (patchedCount == 0) {
                AppLogger.info(TAG, "No assemblies need to be replaced");
            } else {
                AppLogger.info(TAG, "Successfully replaced " + patchedCount + " assemblies");
            }

            // Step 2: 设置启动参数
            AppLogger.info(TAG, "");
            AppLogger.info(TAG, "Step 2/2: Configuring runtime");

            // 获取 .NET 运行时路径
            String dotnetRoot = RuntimePreference.getDotnetRootPath();

            // 获取完整版本号并解析主版本号
            String selectedVersion = RuntimeManager.getSelectedVersion(context);
            int frameworkMajor = 0;
            if (selectedVersion != null && !selectedVersion.isEmpty()) {
                try {
                    // 从 "8.0.11" 格式中提取主版本号 "8"
                    frameworkMajor = Integer.parseInt(selectedVersion.split("\\.")[0]);
                } catch (Exception e) {
                    AppLogger.error(TAG, "Failed to parse framework version: " + selectedVersion, e);
                    frameworkMajor = 8; // 默认使用 .NET 8
                }
            } else {
                AppLogger.warn(TAG, "No runtime version selected, using default .NET 8");
                frameworkMajor = 8;
            }

            AppLogger.info(TAG, ".NET path: " + (dotnetRoot != null ? dotnetRoot : "(auto-detect)"));
            AppLogger.info(TAG, "Selected version: " + selectedVersion);
            AppLogger.info(TAG, "Framework major: " + frameworkMajor);
            AppLogger.info(TAG, "================================================");

            // 加载 Crypto 库
            String cryptoLibPath = Paths.get(
                    dotnetRoot,
                    "shared/Microsoft.NETCore.App/" + selectedVersion + "/libSystem.Security.Cryptography.Native.Android.so").toString();
            AppLogger.info(TAG, "Loading crypto library from: " + cryptoLibPath);
            System.load(cryptoLibPath);
            AppLogger.info(TAG, "Crypto library loaded successfully");

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