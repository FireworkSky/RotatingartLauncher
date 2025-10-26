package com.app.ralaunch.game;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GameLauncher {
    private static final String TAG = "GameLauncher";

    // 设置启动参数的本地方法
    private static native void setLaunchParams(String appPath, String dotnetPath);

    /**
     * 使用应用程序主机模式启动 .NET 应用（通过 SDL_main）
     * 先从 assets 解压 Assembly-Main.zip 到游戏目录，然后启动 Assembly-Main.dll
     */
    public static int launchDotnetAppHost(Context context, String assemblyPath, String assemblyName) {
        try {
            Log.d(TAG, "==================================================");
            Log.d(TAG, "启动流程: 将从 assets 解压 Assembly-Main.dll 并启动");
            Log.d(TAG, "游戏目录路径: " + assemblyPath);
            Log.d(TAG, "==================================================");

            File gameDir = null;
            
            // 确定游戏目录
            File potentialFile = new File(assemblyPath);
            if (potentialFile.exists() && potentialFile.isFile()) {
                // 如果传入的是文件路径，使用父目录
                gameDir = potentialFile.getParentFile();
            } else if (potentialFile.exists() && potentialFile.isDirectory()) {
                // 如果传入的是目录路径，直接使用
                gameDir = potentialFile;
            } else {
                // 尝试作为目录处理
                gameDir = new File(assemblyPath);
            }

            if (gameDir == null || !gameDir.exists()) {
                Log.e(TAG, "Game directory not found: " + assemblyPath);
                return -1;
            }

            Log.d(TAG, "Game directory: " + gameDir.getAbsolutePath());

            // 步骤1: 从 assets 解压 Assembly-Main.zip 到游戏目录
            Log.d(TAG, "Extracting Assembly-Main.zip from assets...");
            if (!extractAssemblyMainFromAssets(context, gameDir)) {
                Log.e(TAG, "Failed to extract Assembly-Main.zip");
                return -1;
            }

            // 步骤2: 查找 Assembly-Main.dll
            File assemblyFile = new File(gameDir, "Assembly-Main.dll");
            if (!assemblyFile.exists()) {
                Log.e(TAG, "Assembly-Main.dll not found after extraction at: " + assemblyFile.getAbsolutePath());
                return -1;
            }

            // dotnet 运行时目录
            File runtimeDir = new File(context.getFilesDir(), "dotnet");

            // 打印路径以供调试
            Log.d(TAG, "Assembly-Main.dll: " + assemblyFile.getAbsolutePath());
            Log.d(TAG, "Dotnet Runtime: " + runtimeDir.getAbsolutePath());

            // 校验运行时目录
            if (!runtimeDir.exists()) {
                Log.e(TAG, "Dotnet runtime not found: " + runtimeDir.getAbsolutePath());
                return -1;
            }

            // 设置启动参数
            Log.d(TAG, "Setting launch parameters for Assembly-Main.dll...");
            setLaunchParams(assemblyFile.getAbsolutePath(), runtimeDir.getAbsolutePath());

            Log.d(TAG, "Launch parameters set successfully");
            return 0; // 返回0表示参数设置成功，实际执行在SDL_main中

        } catch (Exception e) {
            Log.e(TAG, "Error in launchDotnetAppHost: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 从 assets 中解压 Assembly-Main.zip 到指定目录
     */
    private static boolean extractAssemblyMainFromAssets(Context context, File targetDir) {
        AssetManager assetManager = context.getAssets();
        
        try {
            Log.d(TAG, "Starting to extract Assembly-Main.zip to: " + targetDir.getAbsolutePath());
            
            // 检查 Assembly-Main.zip 是否存在于 assets
            InputStream assetInput = assetManager.open("Assembly-Main.zip");
            ZipInputStream zipInputStream = new ZipInputStream(assetInput);
            
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            int extractedCount = 0;
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                Log.d(TAG, "Processing entry: " + entryName);
                
                // 跳过目录条目
                if (entry.isDirectory()) {
                    File dir = new File(targetDir, entryName);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    zipInputStream.closeEntry();
                    continue;
                }
                
                // 创建目标文件
                File targetFile = new File(targetDir, entryName);
                
                // 安全检查：确保目标文件在目标目录内
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator) &&
                    !canonicalEntryPath.equals(canonicalDestPath)) {
                    Log.w(TAG, "Skipping entry outside target directory: " + entryName);
                    zipInputStream.closeEntry();
                    continue;
                }
                
                // 创建父目录
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                
                // 解压文件
                try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                    int length;
                    while ((length = zipInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    extractedCount++;
                    Log.d(TAG, "Extracted: " + targetFile.getAbsolutePath());
                }
                
                zipInputStream.closeEntry();
            }
            
            zipInputStream.close();
            assetInput.close();
            
            Log.d(TAG, "Successfully extracted " + extractedCount + " files from Assembly-Main.zip");
            return extractedCount > 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting Assembly-Main.zip: " + e.getMessage(), e);
            return false;
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