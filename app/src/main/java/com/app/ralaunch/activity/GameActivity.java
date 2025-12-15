package com.app.ralaunch.activity;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;
import android.os.Bundle;
import android.util.Log;
import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralaunch.utils.RendererPreference;
import com.app.ralaunch.manager.GameFullscreenManager;

import com.app.ralaunch.core.GameLauncher;
import com.app.ralib.patch.Patch;
import com.app.ralib.patch.PatchManager;
import com.app.ralaunch.renderer.OSMRenderer;
import com.app.ralaunch.renderer.RendererConfig;
import com.app.ralaunch.renderer.RendererLoader;
import com.app.ralaunch.renderer.OSMSurface;

import org.libsdl.app.SDLActivity;
import android.view.Surface;

import com.app.ralib.error.ErrorHandler;


public class GameActivity extends SDLActivity {
    private static final String TAG = "GameActivity";
    private static final int CONTROL_EDITOR_REQUEST_CODE = 2001;
    public static GameActivity mainActivity;
    
    // 统一管理器
    private GameFullscreenManager mFullscreenManager;
    private GameVirtualControlsManager virtualControlsManager = new GameVirtualControlsManager();
    private GameMenuController gameMenuController = new GameMenuController();
    private final GameLaunchDelegate launchDelegate = new GameLaunchDelegate();
    private final GameTouchBridge touchBridge = new GameTouchBridge();
    public LongPressRightClickDetector longPressDetector; // public 以便GameMenuController可以访问

    @Override
    protected void attachBaseContext(Context newBase) {
        // 应用语言设置
        super.attachBaseContext(com.app.ralaunch.utils.LocaleManager.applyLanguage(newBase));
    }

    @Override
    public void loadLibraries() {
        try {
            RendererPreference.applyRendererEnvironment(this);

            com.app.ralaunch.data.SettingsManager settingsManager =
                com.app.ralaunch.data.SettingsManager.getInstance(this);
            
            // 设置 FNA 触屏相关环境变量
            setupTouchEnvironment(settingsManager);
            
        } catch (Exception e) {
        }
        super.loadLibraries();
    }
    

    private void setupTouchEnvironment(com.app.ralaunch.data.SettingsManager settingsManager) {
        try {
            android.system.Os.setenv("SDL_TOUCH_MOUSE_EVENTS", "1", true);
            
            boolean multitouch = settingsManager.isTouchMultitouchEnabled();
            if (multitouch) {
                android.system.Os.setenv("SDL_TOUCH_MOUSE_MULTITOUCH", "1", true);
            } else {
                android.system.Os.setenv("SDL_TOUCH_MOUSE_MULTITOUCH", "0", true);
            }
            
            boolean mouseRightStick = settingsManager.isMouseRightStickEnabled();
            if (mouseRightStick) {
                android.system.Os.setenv("RALCORE_MOUSE_RIGHT_STICK", "1", true);
            } else {
                android.system.Os.unsetenv("RALCORE_MOUSE_RIGHT_STICK");
            }
            
        } catch (Exception e) {
        }
    }

    /**
     * 创建 SDL Surface（使用 OSMesa-aware Surface）
     */
    @Override
    protected org.libsdl.app.SDLSurface createSDLSurface(Context context) {
        try {
            String currentRenderer = RendererLoader.getCurrentRenderer();
            boolean isZink = RendererConfig.RENDERER_ZINK.equals(currentRenderer) ||
                            "vulkan_zink".equals(currentRenderer);

            if (isZink) {
                return new OSMSurface(context);
            }
        } catch (Exception e) {
        }

        // 默认使用标准 SDL Surface
        return super.createSDLSurface(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题设置（必须在 super.onCreate 之前）
        // GameActivity 继承自 SDLActivity，不是 AppCompatActivity，所以直接应用主题
        com.app.ralaunch.data.SettingsManager settingsManager = 
            com.app.ralaunch.data.SettingsManager.getInstance(this);
        int themeMode = settingsManager.getThemeMode();
        
        switch (themeMode) {
            case 0: // 跟随系统
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1: // 深色模式
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2: // 浅色模式
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
        
        super.onCreate(savedInstanceState);

        mainActivity = this;
        // 初始化错误处理器（用于显示错误弹窗）
        try {
            ErrorHandler.setCurrentActivity(this);
        } catch (Exception e) {
            AppLogger.error("GameActivity", "设置 ErrorHandler 失败: " + e.getMessage());
        }
        // 强制横屏，防止 SDL 在运行时将方向改为 FULL_SENSOR 导致旋转为竖屏
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } catch (Exception e) {
        }
        // 初始化全屏管理器
        mFullscreenManager = new GameFullscreenManager(this);
        mFullscreenManager.enableFullscreen();
        mFullscreenManager.configureIME();

        // 初始化虚拟控制系统
        virtualControlsManager.initialize(
            this,
            (ViewGroup) mLayout,
            mSurface,
            () -> disableSDLTextInput()
        );

        // 设置游戏内菜单（需要在虚拟控制初始化后）
        gameMenuController.setup(this, (ViewGroup) mLayout, virtualControlsManager);

        // 初始化长按右键检测器
        if (virtualControlsManager.getInputBridge() != null) {
            longPressDetector = new LongPressRightClickDetector(virtualControlsManager.getInputBridge());
            longPressDetector.setEnabled(settingsManager.isLongPressRightClickEnabled());
        }

        String runtimePref = getIntent().getStringExtra("DOTNET_FRAMEWORK");

        if (runtimePref != null && !runtimePref.isEmpty()) {
            try {
                RuntimePreference.setDotnetFramework(this, runtimePref);
            }
            catch (Throwable t) {
            }
        }

        setLaunchParams();

    }
    




    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        super.setOrientationBis(w, h, resizable, "LandscapeLeft LandscapeRight");
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }

    // 设置启动参数
    private void setLaunchParams() {
        int result = launchDelegate.apply(this, getIntent());
        if (result != 0) {
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (mFullscreenManager != null) {
            mFullscreenManager.onWindowFocusChanged(hasFocus);
        }
    }

    /**
     * 初始化虚拟控制系统
     */
    private void initializeVirtualControls() {
        virtualControlsManager.initialize(this, (ViewGroup) mLayout, mSurface, () -> disableSDLTextInput());
    }

    /**
     * 切换虚拟控制显示/隐藏
     */
    public void toggleVirtualControls() {
        virtualControlsManager.toggle(this);
    }

    /**
     * 设置虚拟控制显示状态
     */
    public void setVirtualControlsVisible(boolean visible) {
        virtualControlsManager.setVisible(visible);
    }

    /**
     * 设置游戏内菜单
     */
    private void setupGameMenu() {
        gameMenuController.setup(this, (ViewGroup) mLayout, virtualControlsManager);
    }

    


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONTROL_EDITOR_REQUEST_CODE && resultCode == RESULT_OK) {
            virtualControlsManager.onActivityResultReload();
        }
    }

    /**
     * 处理返回键按下事件
     * 注意: SDLActivity 的 onBackPressed() 会调用 super.onBackPressed() 导致直接退出
     * 我们在这里完全覆盖这个行为,不调用 super,而是显示确认对话框
     */
    @Override
    public void onBackPressed() {
        gameMenuController.handleBack(virtualControlsManager);
    }
    @Override
    protected void onDestroy() {
        virtualControlsManager.stop();
        super.onDestroy();
    }
    /**
     * 将文本发送到SDL游戏
     * 直接发送SDL_TEXTINPUT事件（这是Terraria/FNA正确接收文本的方式）
     */
    public static void sendTextToGame(String text) {
        GameImeHelper.sendTextToGame(text);
    }
    /**
     * 发送Backspace删除操作到SDL游戏
     * 通过发送Backspace按键事件实现删除功能
     */
    public static void sendBackspace() {
        GameImeHelper.sendBackspaceToGame();
    }

    public static void enableSDLTextInputForIME() {
        GameImeHelper.enableSDLTextInputForIME();
    }
    /**
     * 禁用SDL文本输入
     * 防止SDL自动显示文本框
     */
    public static void disableSDLTextInput() {
        GameImeHelper.disableSDLTextInput();
    }

    public static void onGameExit(int exitCode) {
        onGameExitWithMessage(exitCode, null);
    }

    /**
     * Native 退出回调(带错误消息)
     * 从 native 代码调用以通知游戏退出
     */
    public static void onGameExitWithMessage(int exitCode, String errorMessage) {
        mainActivity.runOnUiThread(() -> {
            if (exitCode == 0) {
                Toast.makeText(mainActivity, mainActivity.getString(R.string.game_completed_successfully), Toast.LENGTH_LONG).show();
            } else {
                String message;
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    message = errorMessage + "\n" + mainActivity.getString(R.string.game_exit_code, exitCode);
                } else {
                    message = mainActivity.getString(R.string.game_exit_code, exitCode);
                }
                ErrorHandler.showWarning(mainActivity.getString(R.string.game_run_failed), message);
            }
            mainActivity.finish();
        });
    }

    // Touch bridge native methods（提供给 GameTouchBridge 调用）
    static void nativeSetTouchDataBridge(int count, float[] x, float[] y, int screenWidth, int screenHeight) {
        nativeSetTouchData(count, x, y, screenWidth, screenHeight);
    }

    static void nativeClearTouchDataBridge() {
        nativeClearTouchData();
    }

    private static native void nativeSetTouchData(int count, float[] x, float[] y, int screenWidth, int screenHeight);
    private static native void nativeClearTouchData();

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        // Ignore certain back key
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                onBackPressed();
            }
            return false;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 拦截所有触摸事件，传递给 touch bridge
     * 先让虚拟控件处理触摸，然后排除被占用的触摸点
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean result = super.dispatchTouchEvent(event);
        
        // 处理长按右键检测
        if (longPressDetector != null) {
            longPressDetector.handleTouchEvent(event);
        }
        
        touchBridge.handleMotionEvent(event, getResources());
        return result;
    }

}