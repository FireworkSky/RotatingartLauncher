package com.app.ralaunch.activity;

import androidx.annotation.Nullable;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.Toast;
import android.os.Bundle;

import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.dotnet.DotNetLauncher;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralaunch.manager.GameFullscreenManager;

import com.app.ralaunch.core.GameLauncher;
import com.app.ralib.patch.Patch;
import com.app.ralib.patch.PatchManager;
import com.app.ralaunch.renderer.RendererConfig;
import com.app.ralaunch.renderer.RendererLoader;
import com.app.ralaunch.renderer.OSMSurface;

import org.libsdl.app.SDLActivity;

import com.app.ralib.error.ErrorHandler;

import java.io.File;
import java.util.ArrayList;


public class GameActivity extends SDLActivity {
    private static final String TAG = "GameActivity";
    private static final int CONTROL_EDITOR_REQUEST_CODE = 2001;
    public static GameActivity mainActivity;

    // 统一管理器
    private GameFullscreenManager mFullscreenManager;
    private GameVirtualControlsManager virtualControlsManager = new GameVirtualControlsManager();
    private GameMenuController gameMenuController = new GameMenuController();
    private final GameTouchBridge touchBridge = new GameTouchBridge();

    @Override
    protected void attachBaseContext(Context newBase) {
        // 应用语言设置
        super.attachBaseContext(com.app.ralaunch.utils.LocaleManager.applyLanguage(newBase));
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

        com.app.ralaunch.utils.DensityAdapter.adapt(this, true);

        // 应用主题设置（必须在 super.onCreate 之前）
        // GameActivity 继承自 SDLActivity，不是 AppCompatActivity，所以直接应用主题
        com.app.ralaunch.data.SettingsManager settingsManager =
                com.app.ralaunch.data.SettingsManager.getInstance();
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

        String runtimePref = getIntent().getStringExtra("DOTNET_FRAMEWORK");

        if (runtimePref != null && !runtimePref.isEmpty()) {
            try {
                RuntimePreference.setDotnetFramework(this, runtimePref);
            }
            catch (Throwable t) {
            }
        }
    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        super.setOrientationBis(w, h, resizable, "LandscapeLeft LandscapeRight");
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }

    @Override
    protected void Main(String[] args) {
        launchDotNetGame();
    }

    public int launchDotNetGame() {
        try {
            var intent = getIntent();

            // 获取程序集路径
            String assemblyPath = intent.getStringExtra("ASSEMBLY_PATH");
            String gameName = intent.getStringExtra("GAME_NAME");
            String runtime = intent.getStringExtra("RUNTIME"); // "dotnet" or "box64"
            
            if (assemblyPath == null || assemblyPath.isEmpty()) {
                AppLogger.error(TAG, "Assembly path is null or empty");
                runOnUiThread(() ->
                        ErrorHandler.showWarning(getString(R.string.game_launch_failed),
                                getString(R.string.game_launch_assembly_path_empty)));
                return -1;
            }

            // 验证程序集文件是否存在
            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists() || !assemblyFile.isFile()) {
                AppLogger.error(TAG, "Assembly file not found: " + assemblyPath);
                runOnUiThread(() ->
                        ErrorHandler.showWarning(getString(R.string.game_launch_failed),
                                getString(R.string.game_launch_assembly_not_exist, assemblyPath)));
                return -2;
            }

            int exitCode;
            
            // 根据运行时类型选择启动方式
            if ("box64".equals(runtime)) {
                // Box64 启动 x86_64 Linux 游戏 (如 Starbound)
                AppLogger.info(TAG, "Launching with Box64: " + assemblyPath);
                exitCode = GameLauncher.INSTANCE.launchBox64Game(this, assemblyPath);
                onGameExitWithMessage(exitCode, exitCode != 0 ? "Box64 启动失败" : null);
            } else {
                // .NET 启动
                ArrayList<String> enabledPatchIds = intent.getStringArrayListExtra("ENABLED_PATCH_IDS");

                @Nullable ArrayList<Patch> enabledPatches = null;
                if (enabledPatchIds != null && !enabledPatchIds.isEmpty()) {
                    PatchManager patchManager = RaLaunchApplication.getPatchManager();
                    enabledPatches = patchManager.getPatchesByIds(enabledPatchIds);
                }

                exitCode = GameLauncher.INSTANCE.launchDotNetAssembly(assemblyPath, new String[] {}, enabledPatches);
                onGameExitWithMessage(exitCode, GameLauncher.INSTANCE.getLastErrorMessage());
            }

            if (exitCode == 0) {
                AppLogger.info(TAG, "Game exited successfully.");
            } else {
                AppLogger.error(TAG, "Failed to launch game: " + exitCode);
                return exitCode;
            }
            return 0;
        } catch (Exception e) {
            AppLogger.error(TAG, "Exception in launchDotNetGame: " + e.getMessage(), e);
            runOnUiThread(() -> ErrorHandler.handleError(getString(R.string.game_launch_failed), e, false));
            return -3;
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
    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        gameMenuController.handleBack(virtualControlsManager);
    }
    @Override
    protected void onDestroy() {
        android.util.Log.d(TAG, "GameActivity.onDestroy() called");

        // 清理虚拟控件
        virtualControlsManager.stop();

        super.onDestroy();

        // [重要] .NET runtime (hostfxr) 不支持在同一进程中多次初始化
        // GameActivity 运行在独立进程 (:game)，终止此进程不会影响主应用
        // 延迟终止，确保所有清理工作完成
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.util.Log.d(TAG, "Terminating game process to ensure clean .NET runtime state");
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }, 100);
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

    /**
     * Native 退出回调(带错误消息)
     * 从 native 代码调用以通知游戏退出
     */
    public static void onGameExitWithMessage(int exitCode, String errorMessage) {
        mainActivity.runOnUiThread(() -> {
            if (exitCode == 0) {
                Toast.makeText(mainActivity, mainActivity.getString(R.string.game_completed_successfully), Toast.LENGTH_LONG).show();
                mainActivity.finish();
            } else {
                // 游戏异常退出，使用崩溃捕捉界面
                showGameCrashReport(exitCode, errorMessage);
            }
        });
    }

    /**
     * 显示游戏崩溃报告界面
     */
    private static void showGameCrashReport(int exitCode, String errorMessage) {
        try {
            // 从 C 层获取详细的错误信息
            String nativeError = null;
            try {
                nativeError = GameLauncher.INSTANCE.getLastErrorMessage();
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to get native error", e);
            }

            // 获取 logcat 日志（最近的错误日志）
            String logcatLogs = getRecentLogcatLogs();

            String title = mainActivity.getString(R.string.game_run_failed);
            String message;
            if (errorMessage != null && !errorMessage.isEmpty()) {
                message = errorMessage + "\n" + mainActivity.getString(R.string.game_exit_code, exitCode);
            } else {
                message = mainActivity.getString(R.string.game_exit_code, exitCode);
            }

            // 构建错误详情
            StringBuilder errorDetails = new StringBuilder();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            errorDetails.append("发生时间: ").append(sdf.format(new java.util.Date())).append("\n\n");

            try {
                String versionName = mainActivity.getPackageManager()
                        .getPackageInfo(mainActivity.getPackageName(), 0).versionName;
                errorDetails.append("应用版本: ").append(versionName).append("\n");
            } catch (Exception e) {
                errorDetails.append("应用版本: 未知\n");
            }

            errorDetails.append("设备型号: ").append(android.os.Build.MANUFACTURER).append(" ")
                    .append(android.os.Build.MODEL).append("\n");
            errorDetails.append("Android 版本: ").append(android.os.Build.VERSION.RELEASE)
                    .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");

            errorDetails.append("错误类型: 游戏异常退出\n");
            errorDetails.append("退出代码: ").append(exitCode).append("\n");

            if (nativeError != null && !nativeError.isEmpty()) {
                errorDetails.append("C层错误: ").append(nativeError).append("\n");
            }

            if (errorMessage != null && !errorMessage.isEmpty()) {
                errorDetails.append("错误信息: ").append(errorMessage).append("\n");
            }

            // 构建堆栈跟踪（包含 C 层错误和 logcat 日志）
            StringBuilder stackTrace = new StringBuilder();
            stackTrace.append("游戏进程异常退出\n");
            stackTrace.append("退出代码: ").append(exitCode).append("\n\n");

            if (nativeError != null && !nativeError.isEmpty()) {
                stackTrace.append("=== C层错误信息 ===\n");
                stackTrace.append(nativeError).append("\n\n");
            }

            if (logcatLogs != null && !logcatLogs.isEmpty()) {
                stackTrace.append("=== Logcat 日志（最近错误） ===\n");
                stackTrace.append(logcatLogs).append("\n\n");
            }

            if (errorMessage != null && !errorMessage.isEmpty()) {
                stackTrace.append("=== 错误详情 ===\n");
                stackTrace.append(errorMessage);
            }

            // 启动崩溃报告界面
            Intent intent = new Intent(mainActivity, com.app.ralaunch.crash.CrashReportActivity.class);
            intent.putExtra("stack_trace", stackTrace.toString());
            intent.putExtra("error_details", errorDetails.toString());
            intent.putExtra("exception_class", "GameExitException");
            intent.putExtra("exception_message", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            mainActivity.startActivity(intent);
            mainActivity.finish();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to show crash report", e);
            // 如果启动崩溃界面失败，使用旧的警告方式作为后备
            String message;
            if (errorMessage != null && !errorMessage.isEmpty()) {
                message = errorMessage + "\n" + mainActivity.getString(R.string.game_exit_code, exitCode);
            } else {
                message = mainActivity.getString(R.string.game_exit_code, exitCode);
            }
            ErrorHandler.showWarning(mainActivity.getString(R.string.game_run_failed), message);
            mainActivity.finish();
        }
    }

    /**
     * 获取最近的 logcat 日志（错误和警告级别）
     */
    private static String getRecentLogcatLogs() {
        try {
            java.lang.Process process = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-d", "-v", "time", "*:E", "*:W", "NetCoreHost:E", "GameLauncher:E", "SDL:E", "FNA3D:E"}
            );

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );

            StringBuilder logs = new StringBuilder();
            String line;
            int lineCount = 0;
            int maxLines = 200; // 限制最多200行

            while ((line = reader.readLine()) != null && lineCount < maxLines) {
                // 只保留包含错误关键词的行
                if (line.contains("ERROR") || line.contains("FATAL") ||
                        line.contains("Exception") || line.contains("Error") ||
                        line.contains("NetCoreHost") || line.contains("GameLauncher") ||
                        line.contains("SDL") || line.contains("FNA3D")) {
                    logs.append(line).append("\n");
                    lineCount++;
                }
            }

            reader.close();
            process.destroy();

            // 如果日志太长，只保留最后的部分
            String result = logs.toString();
            if (result.length() > 50000) {
                result = "...[日志已截断，仅显示最后部分]...\n" +
                        result.substring(result.length() - 50000);
            }

            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to get logcat logs", e);
            return null;
        }
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

        touchBridge.handleMotionEvent(event, getResources());
        return result;
    }

}