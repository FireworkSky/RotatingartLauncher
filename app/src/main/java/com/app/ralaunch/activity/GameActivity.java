package com.app.ralaunch.activity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.app.ralaunch.game.GameLauncher;

import org.libsdl.app.SDLActivity;

public class GameActivity extends SDLActivity {
    private static final String TAG = "GameActivity";
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1001;
    public static GameActivity mainActivity;

    static {
        try {
            // CoreCLR 将通过 dlopen 动态加载，不需要在这里加载
            System.loadLibrary("c++_shared");
            System.loadLibrary("System.Security.Cryptography.Native.Android");
            Log.d(TAG, "Libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load library: " + e.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: started");
        mainActivity = this;

        // 获取传递的游戏信息
        String gameName = getIntent().getStringExtra("GAME_NAME");
        String assemblyPath = getIntent().getStringExtra("GAME_PATH"); // 现在这是程序集路径
        String engineType = getIntent().getStringExtra("ENGINE_TYPE");

        Log.d(TAG, "启动游戏: " + gameName);
        Log.d(TAG, "程序集路径: " + assemblyPath);
        Log.d(TAG, "引擎类型: " + engineType);

        setLaunchParams();
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

            // 获取程序集路径
            String assemblyPath = getIntent().getStringExtra("GAME_PATH");
            String assemblyName = getIntent().getStringExtra("GAME_NAME");



            // 直接启动 .NET 程序集
            int result = GameLauncher.launchDotnetAppHost(this, assemblyPath,assemblyName);

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