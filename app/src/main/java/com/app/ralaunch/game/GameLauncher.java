package com.app.ralaunch.game;

import android.content.Context;
import android.util.Log;

import java.io.File;

public class GameLauncher {
    private static final String TAG = "GameLauncher";

    // 设置启动参数的本地方法
    private static native void setLaunchParams(String appPath, String dotnetPath);

    /**
     * 使用应用程序主机模式启动 .NET 应用（通过 SDL_main）
     */
    public static int launchDotnetAppHost(Context context, String assemblyPath,String assemblyName) {
        try {
            Log.d(TAG, "Preparing to launch app in host mode: " + assemblyPath);

            // 检查传入的是否是完整路径
            File potentialAssembly = new File(assemblyPath);
            if (potentialAssembly.exists() && potentialAssembly.isFile()) {
                // 如果传入的是文件路径，直接使用它
                Log.d(TAG, "Using direct assembly path: " + assemblyPath);
                return launchAssemblyDirect(context, assemblyPath);
            }

            // 定义路径
            File appDir = context.getFilesDir();
            File appsDir = context.getExternalFilesDir(assemblyPath);
            if (appsDir == null) {
                appsDir = new File(context.getExternalFilesDir(null), assemblyPath);
            }

            // dotnet 运行时目录
            File runtimeDir = new File(appDir, "dotnet");

            // 应用程序文件
            File dllFile = new File(assemblyPath ,assemblyName + ".dll");

            // 打印路径以供调试
            Log.d(TAG, "App DLL: " + dllFile.getAbsolutePath());
            Log.d(TAG, "Dotnet Runtime: " + runtimeDir.getAbsolutePath());

            // 校验关键文件
            if (!dllFile.exists()) {
                Log.e(TAG, "App DLL not found: " + dllFile.getAbsolutePath());
                return -1;
            }

            if (!runtimeDir.exists()) {
                Log.e(TAG, "Dotnet runtime not found: " + runtimeDir.getAbsolutePath());
                return -1;
            }

            // 设置启动参数
            Log.d(TAG, "Setting launch parameters...");
            setLaunchParams(dllFile.getAbsolutePath(), runtimeDir.getAbsolutePath());

            Log.d(TAG, "Launch parameters set successfully");
            return 0; // 返回0表示参数设置成功，实际执行在SDL_main中

        } catch (Exception e) {
            Log.e(TAG, "Error in launchDotnetAppHost: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 直接启动指定的程序集文件
     */
    public static int launchAssemblyDirect(Context context, String assemblyPath) {
        try {
            Log.d(TAG, "Preparing to launch assembly directly: " + assemblyPath);

            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists()) {
                Log.e(TAG, "Assembly file not found: " + assemblyPath);
                return -1;
            }

            // dotnet 运行时目录
            File runtimeDir = new File(context.getFilesDir(), "dotnet");

            // 打印路径以供调试
            Log.d(TAG, "Assembly: " + assemblyFile.getAbsolutePath());
            Log.d(TAG, "Dotnet Runtime: " + runtimeDir.getAbsolutePath());

            if (!runtimeDir.exists()) {
                Log.e(TAG, "Dotnet runtime not found: " + runtimeDir.getAbsolutePath());
                return -1;
            }

            // 设置启动参数
            Log.d(TAG, "Setting launch parameters...");
            setLaunchParams(assemblyFile.getAbsolutePath(), runtimeDir.getAbsolutePath());

            Log.d(TAG, "Launch parameters set successfully");
            return 0;

        } catch (Exception e) {
            Log.e(TAG, "Error in launchAssemblyDirect: " + e.getMessage(), e);
            return -1;
        }
    }
}