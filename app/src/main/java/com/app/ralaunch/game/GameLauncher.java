package com.app.ralaunch.game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

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
            System.loadLibrary("SDL2");
            System.loadLibrary("main");
            Log.i(TAG, "✅ Native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "❌ Failed to load native libraries: " + e.getMessage());
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
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.i(TAG, "🚀 准备直接启动程序集");
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.i(TAG, "  程序集路径: " + assemblyPath);
        
        try {
            File assemblyFile = new File(assemblyPath);
            
            // 验证程序集文件存在
            if (!assemblyFile.exists()) {
                Log.e(TAG, "❌ 程序集文件不存在: " + assemblyPath);
                return -1;
            }
            
            // 获取应用目录和主程序集名称
            String appDir = assemblyFile.getParent();
            String mainAssembly = assemblyFile.getName();
            
            Log.i(TAG, "  应用目录: " + appDir);
            Log.i(TAG, "  主程序集: " + mainAssembly);
            
            // Step 1: 应用 MonoMod 补丁
            Log.i(TAG, "");
            Log.i(TAG, "⏳ 步骤 1/2: 应用 MonoMod 补丁");
            int patchedCount = AssemblyPatcher.applyPatches(context, appDir);
            
            if (patchedCount < 0) {
                Log.w(TAG, "⚠️  补丁应用失败，但将继续启动");
            } else if (patchedCount == 0) {
                Log.i(TAG, "ℹ️  没有需要替换的程序集");
            } else {
                Log.i(TAG, "✅ 成功替换 " + patchedCount + " 个程序集");
            }
            
            // Step 2: 设置启动参数
            Log.i(TAG, "");
            Log.i(TAG, "⏳ 步骤 2/2: 配置运行时");
            
            // 获取 .NET 运行时路径
            String dotnetRoot = RuntimePreference.getDotnetRootPath();
            int frameworkMajor = RuntimePreference.getPreferredFrameworkMajor();
            
            Log.i(TAG, "  .NET路径: " + (dotnetRoot != null ? dotnetRoot : "(自动检测)"));
            Log.i(TAG, "  框架版本: " + frameworkMajor + ".x");
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 加载 Crypto 库
            // TODO: 根据不同平台加载对应的库文件
            Log.i(TAG, "⏳ 加载加密库 libSystem.Security.Cryptography.Native.Android.so ...");
            System.load(Paths.get(
                    dotnetRoot,
                    "shared/Microsoft.NETCore.App/10.0.0-rc.2.25502.107/libSystem.Security.Cryptography.Native.Android.so").toString());
            Log.i(TAG, "✅ 加密库加载成功");
            
            // 设置启动参数（简化版 - 4个参数）
            int result = netcorehostSetParams(appDir, mainAssembly, dotnetRoot, frameworkMajor);
            
            if (result != 0) {
                Log.e(TAG, "❌ 设置启动参数失败: " + result);
                return -1;
            }
            
            Log.i(TAG, "✅ 启动参数设置成功");
            return 0;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 启动程序集失败", e);
            return -1;
        }
    }
}
