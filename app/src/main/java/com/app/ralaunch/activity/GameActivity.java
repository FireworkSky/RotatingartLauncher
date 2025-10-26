package com.app.ralaunch.activity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.app.ralaunch.game.GameLauncher;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.app.ralaunch.view.OverlayControlView;

import org.libsdl.app.SDLActivity;

public class GameActivity extends SDLActivity {
    private static final String TAG = "GameActivity";
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1001;
    public static GameActivity mainActivity;
    private OverlayControlView controlView;
    private ControlLayoutManager layoutManager;

    static {
        try {
            System.loadLibrary("coreclr");
            System.loadLibrary("rustcorehost");
            System.loadLibrary("c++_shared");
            System.loadLibrary("System.Security.Cryptography.Native.Android");
            Log.d(TAG, "All libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load library: " + e.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: started");
        mainActivity = this;

        // 设置全屏和隐藏刘海屏
        setupFullscreenMode();

        // 获取传递的游戏信息
        String gameName = getIntent().getStringExtra("GAME_NAME");
        String assemblyPath = getIntent().getStringExtra("GAME_PATH"); // 游戏目录路径
        String engineType = getIntent().getStringExtra("ENGINE_TYPE");

        Log.d(TAG, "启动游戏: " + gameName);
        Log.d(TAG, "游戏目录: " + assemblyPath);
        Log.d(TAG, "引擎类型: " + engineType);
        Log.d(TAG, "将从 assets 解压并启动 Assembly-Main.dll");

        // 初始化控制布局管理器
        initializeControlLayout();

        checkAndRequestStoragePermissions();
    }

    /**
     * 设置全屏模式并隐藏刘海屏
     */
    private void setupFullscreenMode() {
        Window window = getWindow();
        View decorView = window.getDecorView();

        // 设置全屏标志
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Android P (API 28) 及以上：设置刘海屏显示模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        // 使用 WindowInsetsController 隐藏系统栏（推荐方式，适用于 API 30+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
            if (controller != null) {
                // 隐藏状态栏和导航栏
                controller.hide(WindowInsetsCompat.Type.systemBars());
                // 设置沉浸式模式行为
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            // 对于 API 30 以下，使用传统方式
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }

        Log.d(TAG, "Fullscreen mode with notch cutout enabled");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 重新应用全屏设置，防止系统栏重新出现
            setupFullscreenMode();
        }
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }

    // 检查并请求存储权限
    private void checkAndRequestStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST_CODE);
        } else {
            // 已经有权限，设置启动参数
            setLaunchParams();
        }
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

    /**
     * 初始化控制布局
     */
    private void initializeControlLayout() {
        try {
            layoutManager = new ControlLayoutManager(this);
            
            // 获取当前控制布局（默认使用 tModLoader 默认布局）
            ControlLayout layout = layoutManager.getCurrentLayout();
            if (layout == null) {
                // 尝试设置为 tModLoader 默认布局
                layoutManager.setCurrentLayout("tModLoader 默认");
                layout = layoutManager.getCurrentLayout();
                
                if (layout == null && !layoutManager.getAllLayouts().isEmpty()) {
                    // 如果还是没有，使用第一个可用布局
                    layout = layoutManager.getAllLayouts().get(0);
                    layoutManager.setCurrentLayout(layout.getName());
                    Log.w(TAG, "使用第一个可用布局: " + layout.getName());
                }
            }
            
            if (layout != null) {
                Log.d(TAG, "已加载控制布局: " + layout.getName());
                // 创建 final 引用以便在 lambda 中使用
                final ControlLayout finalLayout = layout;
                
                // 延迟设置控制覆盖层，等待 SDL Surface 创建完成
                Thread setupThread = new Thread(() -> {
                    try {
                        Thread.sleep(1000); // 等待 SDL Surface 初始化
                        runOnUiThread(() -> setupControlOverlay(finalLayout));
                    } catch (InterruptedException e) {
                        Log.e(TAG, "线程中断", e);
                    }
                }, "SetupControlsThread");
                setupThread.start();
            } else {
                Log.w(TAG, "没有可用的控制布局");
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化控制布局失败", e);
        }
    }

    /**
     * 设置控制覆盖层
     */
    private void setupControlOverlay(ControlLayout layout) {
        try {
            // 创建控制视图
            controlView = new OverlayControlView(this);
            controlView.setControlLayout(layout);
            
            // 设置事件监听器，将虚拟控制映射到 SDL 输入
            controlView.setOnControlEventListener(new OverlayControlView.OnControlEventListener() {
                @Override
                public void onButtonDown(ControlElement element) {
                    int keyCode = element.getKeyCode();
                    if (keyCode > 0) {
                        Log.d(TAG, "虚拟按键按下: " + element.getName() + " -> KeyCode: " + keyCode);
                        SDLActivity.onNativeKeyDown(keyCode);
                    }
                }

                @Override
                public void onButtonUp(ControlElement element) {
                    int keyCode = element.getKeyCode();
                    if (keyCode > 0) {
                        Log.d(TAG, "虚拟按键抬起: " + element.getName() + " -> KeyCode: " + keyCode);
                        SDLActivity.onNativeKeyUp(keyCode);
                    }
                }

                @Override
                public void onJoystickMove(ControlElement element, float deltaX, float deltaY) {
                    // 摇杆移动映射到方向键或WASD
                    // tModLoader/Terraria 使用方向键或WASD进行移动
                    
                    // 处理水平方向
                    if (Math.abs(deltaX) > 0.3f) {
                        if (deltaX > 0) {
                            // 向右 - 模拟 D 键
                            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_D);
                        } else {
                            // 向左 - 模拟 A 键
                            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_A);
                        }
                    } else {
                        // 释放水平方向键
                        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_D);
                        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_A);
                    }
                    
                    // 处理垂直方向
                    if (Math.abs(deltaY) > 0.3f) {
                        if (deltaY > 0) {
                            // 向下 - 模拟 S 键
                            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_S);
                        } else {
                            // 向上 - 模拟 W 键
                            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_W);
                        }
                    } else {
                        // 释放垂直方向键
                        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_W);
                        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_S);
                    }
                }

                @Override
                public void onMouseMove(float deltaX, float deltaY) {
                    // 鼠标移动
                    SDLActivity.onNativeMouse(0, 2, deltaX, deltaY, true);
                }

                @Override
                public void onMouseScroll(float deltaX, float deltaY) {
                    // 鼠标滚轮（如果需要）
                    // 可以映射到特定按键或鼠标事件
                }
            });
            
            // 将控制视图添加到根布局
            ViewGroup rootView = (ViewGroup) mLayout;
            if (rootView != null) {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );
                controlView.setLayoutParams(params);
                
                // 确保控制视图在最上层
                controlView.setZ(Float.MAX_VALUE);
                rootView.addView(controlView);
                
                Log.d(TAG, "控制覆盖层已添加到视图");
            } else {
                Log.e(TAG, "无法获取根视图");
            }
        } catch (Exception e) {
            Log.e(TAG, "设置控制覆盖层失败", e);
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