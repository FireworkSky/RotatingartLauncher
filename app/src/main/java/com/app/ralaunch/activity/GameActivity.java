package com.app.ralaunch.activity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.app.ralaunch.utils.RuntimePreference;

import com.app.ralaunch.game.GameLauncher;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * 游戏运行Activity
 * 
 * 继承自 SDLActivity，负责实际运行游戏，提供：
 * - SDL 环境初始化
 * - .NET 运行时加载
 * - 游戏程序集启动
 * - 强制横屏显示
 * - 权限管理和输入法支持
 * 
 * 通过 JNI 与 C/C++ 层交互，调用 dotnet host 启动游戏
 */
public class GameActivity extends SDLActivity {
    private static final String TAG = "GameActivity";
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1001;
    public static GameActivity mainActivity;
    

    // 不再在 Java 层显式加载任何 jnilibs，改为完全依赖 NATIVE_DLL_SEARCH_DIRECTORIES
    static { }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: started");
        mainActivity = this;

        // 强制横屏，防止 SDL 在运行时将方向改为 FULL_SENSOR 导致旋转为竖屏
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set orientation onCreate: " + e.getMessage());
        }

        // 允许窗口与输入法交互，避免 SurfaceView 阻止 IME（清除 ALT_FOCUSABLE_IM）
        try {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to adjust window flags for IME: " + t.getMessage());
        }

        // 键盘交互改由 SDL/MonoGame 层处理，这里不再添加任何 UI 或手动逻辑

        // 获取传递的游戏信息
        String gameName = getIntent().getStringExtra("GAME_NAME");
        String assemblyPath = getIntent().getStringExtra("GAME_PATH"); // 程序集路径（ModLoader.dll）
        String gameBodyPath = getIntent().getStringExtra("GAME_BODY_PATH"); // 游戏本体路径（Terraria.exe）
        boolean modLoaderEnabled = getIntent().getBooleanExtra("MOD_LOADER_ENABLED", true); // ModLoader 开关状态
        String runtimePref = getIntent().getStringExtra("DOTNET_FRAMEWORK"); // 可选："net6"、"net8"、"net10"、"auto"

        Log.d(TAG, "启动游戏: " + gameName);
        Log.d(TAG, "程序集路径: " + assemblyPath);
        Log.d(TAG, "游戏本体路径: " + gameBodyPath);
        Log.d(TAG, "ModLoader 启用: " + modLoaderEnabled);

        // 如有按次覆盖的运行时偏好，从 Intent 写入到 app_prefs 供本次启动解析
        if (runtimePref != null && !runtimePref.isEmpty()) {
            try { RuntimePreference.setDotnetFramework(this, runtimePref); }
            catch (Throwable t) { Log.w(TAG, "Failed to apply runtime preference from intent: " + t.getMessage()); }
        }

        setLaunchParams();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 若系统因为 SDL 的请求发生了旋转，这里立即拉回横屏
        try {
            if (newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                Log.d(TAG, "onConfigurationChanged: force back to landscape");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to enforce landscape in onConfigurationChanged: " + e.getMessage());
        }
    }

    

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }



    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show();
                setLaunchParams();
            } else {
                Toast.makeText(this, "需要存储权限才能运行游戏", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // 设置启动参数
    private void setLaunchParams() {
        try {
            Log.d(TAG, "Setting launch parameters for SDL_main...");

            // 获取程序集路径和游戏本体路径
            String assemblyPath = getIntent().getStringExtra("GAME_PATH");
            String gameBodyPath = getIntent().getStringExtra("GAME_BODY_PATH");
            boolean modLoaderEnabled = getIntent().getBooleanExtra("MOD_LOADER_ENABLED", true);
            String assemblyName = getIntent().getStringExtra("GAME_NAME");

            // 根据 ModLoader 开关状态选择正确的程序集路径
            final String finalAssemblyPath;
            if (gameBodyPath != null && !gameBodyPath.isEmpty()) {
                // 有游戏本体路径，说明是 modloader 游戏
                if (!modLoaderEnabled) {
                    // ModLoader 关闭，使用游戏本体 DLL
                    // gameBodyPath 格式: /path/to/Terraria.exe
                    // 需要转换为: /path/to/Terraria.dll
                    File gameBodyFile = new File(gameBodyPath);
                    File gameBodyDir = gameBodyFile.getParentFile();
                    String gameBodyName = gameBodyFile.getName().replace(".exe", ".dll");
                    finalAssemblyPath = new File(gameBodyDir, gameBodyName).getAbsolutePath();
                    Log.d(TAG, "ModLoader disabled, using game body DLL: " + finalAssemblyPath);
                } else {
                    finalAssemblyPath = assemblyPath;
                    Log.d(TAG, "ModLoader enabled, using ModLoader DLL: " + finalAssemblyPath);
                }
            } else {
                finalAssemblyPath = assemblyPath;
            }

            // 验证程序集文件是否存在
            File assemblyFile = new File(finalAssemblyPath);
            if (!assemblyFile.exists() || !assemblyFile.isFile()) {
                Log.e(TAG, "Assembly file not found: " + finalAssemblyPath);
                runOnUiThread(() -> {
                    Toast.makeText(this, "程序集文件不存在: " + finalAssemblyPath, Toast.LENGTH_LONG).show();
                });
                return;
            }

            // 直接启动 .NET 程序集
            int result = GameLauncher.launchAssemblyDirect(this, finalAssemblyPath);

            if (result == 0) {
                Log.d(TAG, "Launch parameters set successfully, SDL_main will handle the execution");
            } else {
                Log.e(TAG, "Failed to set launch parameters: " + result);
                runOnUiThread(() -> {
                    Toast.makeText(this, "设置启动参数失败: " + result, Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting launch parameters: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(this, "设置启动参数失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    public static void onGameExit(int exitCode) {
        Log.d(TAG, "onGameExit: " + exitCode);
        mainActivity.runOnUiThread(() -> {
            if (exitCode == 0) {
                Toast.makeText(mainActivity, "游戏已成功运行完成", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(mainActivity, "游戏运行失败，退出代码: " + exitCode, Toast.LENGTH_LONG).show();
            }
            mainActivity.finish();
        });
    }
}