package com.app.ralaunch.activity;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ListView;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.controls.ControlConfig;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.editor.ControlEditorActivity;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralib.error.ErrorHandler;
import com.app.ralaunch.manager.GameControlEditorManager;
import com.app.ralaunch.manager.GameMenuManager;
import com.app.ralaunch.manager.GameFullscreenManager;

import com.app.ralaunch.core.GameLauncher;
import com.app.ralaunch.controls.ControlLayout;
import com.app.ralaunch.controls.SDLInputBridge;

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
    private static final int CONTROL_EDITOR_REQUEST_CODE = 2001;
    public static GameActivity mainActivity;
    
    private ControlLayout mControlLayout;
    private SDLInputBridge mInputBridge;
    private DrawerLayout mDrawerLayout;
    private ListView mGameMenu;
    private View mDrawerButton; // 改为 View，因为现在是 MaterialCardView
    private View mEditorSettingsButton; // 改为 View，因为现在是 MaterialCardView
    private FrameLayout mContentFrame; // 内容框架
    
    // 统一管理器
    private GameControlEditorManager mControlEditorManager;
    private GameMenuManager mMenuManager;
    private GameFullscreenManager mFullscreenManager;

    // gl4es加载：已禁用静态预加载以避免EGL冲突
    // 现在由SDL通过SDL_VIDEO_GL_DRIVER环境变量在需要时延迟加载
    // 原因：过早加载会导致gl4es和SDL的EGL同时初始化，引发pthread_mutex_lock错误

    @Override
    public void loadLibraries() {
        try {
            RuntimePreference.applyRendererEnvironment(this);
            AppLogger.info(TAG, "Renderer environment applied before native library load");
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to apply renderer environment before loading libraries: " + e.getMessage());
        }
        super.loadLibraries();
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

        // 记录 GameActivity 启动
        AppLogger.info(TAG, "================================================");
        AppLogger.info(TAG, "GameActivity.onCreate() started");
        AppLogger.info(TAG, "================================================");

        // 强制横屏，防止 SDL 在运行时将方向改为 FULL_SENSOR 导致旋转为竖屏
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            AppLogger.info(TAG, "Screen orientation set to landscape");
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to set orientation onCreate: " + e.getMessage());
        }

        // 初始化全屏管理器
        mFullscreenManager = new GameFullscreenManager(this);
        mFullscreenManager.enableFullscreen();
        mFullscreenManager.configureIME();

        // 键盘交互改由 SDL/MonoGame 层处理，这里不再添加任何 UI 或手动逻辑

        // 初始化虚拟控制系统
        initializeVirtualControls();

        // 设置游戏内菜单（需要在虚拟控制初始化后）
        setupGameMenu();
        
        // 初始化控件编辑器管理器（需要在菜单设置后，因为需要 contentFrame）
        // 注意：这里只是确保管理器存在，实际初始化在 setupGameMenu 中完成

        // 获取传递的游戏信息
        String gameName = getIntent().getStringExtra("GAME_NAME");
        String assemblyPath = getIntent().getStringExtra("GAME_PATH"); // 程序集路径（ModLoader.dll）
        String gameBodyPath = getIntent().getStringExtra("GAME_BODY_PATH"); // 游戏本体路径（Terraria.exe）
        boolean modLoaderEnabled = getIntent().getBooleanExtra("MOD_LOADER_ENABLED", true); // ModLoader 开关状态
        String runtimePref = getIntent().getStringExtra("DOTNET_FRAMEWORK"); // 可选："net6"、"net8"、"net10"、"auto"

        boolean isBootstrapper = getIntent().getBooleanExtra("IS_BOOTSTRAPPER", false); // 是否为引导程序
        String gameBasePath = getIntent().getStringExtra("GAME_BASE_PATH"); // 游戏根目录路径（仅引导程序使用）
        String bootstrapperAssemblyPath = getIntent().getStringExtra("BOOTSTRAPPER_ASSEMBLY_PATH"); // 引导程序程序集路径（仅引导程序使用）
        String bootstrapperEntryPoint = getIntent().getStringExtra("BOOTSTRAPPER_ENTRY_POINT"); // 引导程序入口点（仅引导程序使用）
        String bootstrapperCurrentDir = getIntent().getStringExtra("BOOTSTRAPPER_CURRENT_DIR"); // 引导程序工作目录（仅引导程序使用）

        if (!isBootstrapper){
            AppLogger.info(TAG, "Normal game launch mode");

            // 如有按次覆盖的运行时偏好，从 Intent 写入到 app_prefs 供本次启动解析
            if (runtimePref != null && !runtimePref.isEmpty()) {
                try {
                    RuntimePreference.setDotnetFramework(this, runtimePref);
                    AppLogger.info(TAG, "Runtime preference set: " + runtimePref);
                }
                catch (Throwable t) {
                    AppLogger.warn(TAG, "Failed to apply runtime preference from intent: " + t.getMessage());
                }
            }

            setLaunchParams();
        } else {
            AppLogger.info(TAG, "Bootstrapper launch mode");

            if (gameBasePath == null || bootstrapperAssemblyPath == null || bootstrapperEntryPoint == null || bootstrapperCurrentDir == null) {
                AppLogger.error(TAG, "Bootstrapper parameters incomplete");
                runOnUiThread(() -> ErrorHandler.showWarning("Bootstrapper 启动失败", "启动参数不完整"));
                finish();
            }

            setBootstrapperParams(gameBasePath, bootstrapperAssemblyPath, bootstrapperEntryPoint, bootstrapperCurrentDir);
        }

        // 设置返回键处理器（使用新的 OnBackPressedCallback API）
        setupBackPressedHandler();
    }
    
    /**
     * 设置返回键处理器
     * 注意: SDLActivity 继承自 Activity (不是 AppCompatActivity),
     * 所以我们使用传统的 onBackPressed() 重写方法
     */
    private void setupBackPressedHandler() {
        // 对于 SDLActivity, 我们在 onBackPressed() 方法中处理
        // 这里只是一个占位方法,实际逻辑在 onBackPressed() 中
        AppLogger.info(TAG, "Back pressed handler ready (using onBackPressed override)");
    }

//    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//        // 若系统因为 SDL 的请求发生了旋转，这里立即拉回横屏
//        try {
//            if (newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE) {
//                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
//                AppLogger.info(TAG, "Orientation enforced back to landscape");
//            }
//        } catch (Exception e) {
//            AppLogger.warn(TAG, "Failed to enforce landscape in onConfigurationChanged: " + e.getMessage());
//        }
//    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        // lets force orientation to be landscape
        super.setOrientationBis(w, h, resizable, "LandscapeLeft LandscapeRight");
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
                ErrorHandler.showWarning("权限不足", "需要存储权限才能运行游戏");
                finish();
            }
        }
    }

    // 设置启动参数
    private void setLaunchParams() {
        try {
            // 获取程序集路径
            String assemblyPath = getIntent().getStringExtra("ASSEMBLY_PATH");
            String gameName = getIntent().getStringExtra("GAME_NAME");
            String gameId = getIntent().getStringExtra("GAME_ID");

            if (assemblyPath == null || assemblyPath.isEmpty()) {
                AppLogger.error(TAG, "Assembly path is null or empty");
                runOnUiThread(() -> ErrorHandler.showWarning("启动失败", "程序集路径为空"));
                finish();
                return;
            }

            // 验证程序集文件是否存在
            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists() || !assemblyFile.isFile()) {
                AppLogger.error(TAG, "Assembly file not found: " + assemblyPath);
                runOnUiThread(() -> ErrorHandler.showWarning("启动失败", "程序集文件不存在: " + assemblyPath));
                finish();
                return;
            }

            AppLogger.info(TAG, "================================================");
            AppLogger.info(TAG, "Starting game: " + (gameName != null ? gameName : "Unknown"));
            AppLogger.info(TAG, "Assembly: " + assemblyPath);
            AppLogger.info(TAG, "================================================");

            // 获取启用的补丁配置
            java.util.ArrayList<String> patchIds = getIntent().getStringArrayListExtra("ENABLED_PATCH_IDS");

            java.util.List<com.app.ralaunch.model.PatchInfo> enabledPatches = null;
            if (patchIds != null && !patchIds.isEmpty()) {
                // 从游戏路径重新加载 GameItem
                String gamePath = getIntent().getStringExtra("GAME_PATH");
                if (gamePath != null) {
                    // 从游戏列表中查找 GameItem
                    java.util.List<com.app.ralaunch.model.GameItem> gameList =
                        RaLaunchApplication.getGameDataManager().loadGameList();
                    com.app.ralaunch.model.GameItem gameItem = null;
                    for (com.app.ralaunch.model.GameItem item : gameList) {
                        if (item.getGamePath().equals(gamePath)) {
                            gameItem = item;
                            break;
                        }
                    }

                    if (gameItem != null) {
                        // 从 PatchManager 重新加载完整的补丁信息
                        com.app.ralaunch.utils.PatchManager patchManager = new com.app.ralaunch.utils.PatchManager(this);
                        enabledPatches = patchManager.getEnabledPatches(gameItem);

                        AppLogger.info(TAG, "Enabled patches: " + enabledPatches.size());
                        for (com.app.ralaunch.model.PatchInfo patch : enabledPatches) {
                            AppLogger.info(TAG, "  - " + patch.getPatchName());
                            if (patch.hasEntryPoint()) {
                                AppLogger.info(TAG, "    Entry: " + patch.getEntryTypeName() + "." + patch.getEntryMethodName());
                            }
                        }
                    }
                }
            }

            // 启动程序集（带补丁配置）
            int result = GameLauncher.launchAssemblyDirect(this, assemblyPath, enabledPatches);

            if (result == 0) {
                AppLogger.info(TAG, "Launch parameters set successfully");
            } else {
                AppLogger.error(TAG, "Failed to set launch parameters: " + result);
                runOnUiThread(() -> ErrorHandler.showWarning("启动失败", "设置启动参数失败: " + result));
                finish();
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Exception in setLaunchParams: " + e.getMessage(), e);
            runOnUiThread(() -> ErrorHandler.handleError("启动失败", e, false));
            finish();
        }
    }

    private void setBootstrapperParams(String gameBasePath, String bootstrapperAssemblyPath, String bootstrapperEntryPoint, String bootstrapperCurrentDir) {
        try {
            // 验证引导程序程序集文件是否存在
            File assemblyFile = new File(bootstrapperAssemblyPath);
            if (!assemblyFile.exists() || !assemblyFile.isFile()) {
                AppLogger.error(TAG, "Bootstrapper assembly file not found: " + bootstrapperAssemblyPath);
                runOnUiThread(() -> ErrorHandler.showWarning("引导程序启动失败", "程序集文件不存在: " + bootstrapperAssemblyPath));
                return;
            }

            AppLogger.info(TAG, "Launching bootstrapper: " + bootstrapperAssemblyPath);

            // 直接启动引导程序程序集
            int result = GameLauncher.launchAssemblyDirect(this, bootstrapperAssemblyPath);

            if (result == 0) {
                AppLogger.info(TAG, "Bootstrapper parameters set successfully");
            } else {
                AppLogger.error(TAG, "Failed to set bootstrapper parameters: " + result);
                runOnUiThread(() -> ErrorHandler.showWarning("引导程序启动失败", "设置参数失败: " + result));
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Error setting bootstrapper parameters: " + e.getMessage(), e);
            runOnUiThread(() -> ErrorHandler.handleError("引导程序启动失败", e, false));
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
        try {
            AppLogger.info(TAG, "Initializing virtual controls...");

            // 创建输入桥接
            mInputBridge = new SDLInputBridge();

            // 创建控制布局
            mControlLayout = new ControlLayout(this);
            mControlLayout.setInputBridge(mInputBridge);

            // 统一从 ControlLayoutManager 加载布局
            mControlLayout.loadLayoutFromManager();

            // 统一禁用所有视图裁剪，确保控件边框完整显示
            disableClippingRecursive(mControlLayout);

            // 添加到SDL Surface上（延迟到SDL Surface创建后）
            runOnUiThread(() -> {
                try {
                    ViewGroup contentView = (ViewGroup) mLayout;
                    if (contentView != null) {
                        // 添加控制层到SDL Surface之上
                        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        );
                        contentView.addView(mControlLayout, params);
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, "Failed to add virtual controls to layout", e);
                }
            });

            // 确保SDL文本输入在启动时是禁用的
            // 延迟执行，等待SDL初始化完成
            if (mLayout != null) {
                mLayout.postDelayed(() -> {
                    disableSDLTextInput();
                }, 2000); // 等待2秒让SDL完全初始化
            }

            AppLogger.info(TAG, "Virtual controls initialized successfully");
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to initialize virtual controls", e);
        }
    }

    /**
     * 切换虚拟控制显示/隐藏
     */
    public void toggleVirtualControls() {
        if (mControlLayout != null) {
            boolean visible = !mControlLayout.isControlsVisible();
            mControlLayout.setControlsVisible(visible);
            Toast.makeText(this, visible ? R.string.game_menu_controls_on : R.string.game_menu_controls_off,
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 设置虚拟控制显示状态
     */
    public void setVirtualControlsVisible(boolean visible) {
        if (mControlLayout != null) {
            mControlLayout.setControlsVisible(visible);
        }
    }

    /**
     * 设置游戏内菜单
     */
    private void setupGameMenu() {
        try {
            // 获取SDL创建的根布局
            ViewGroup rootView = (ViewGroup) mLayout;
            if (rootView == null) {
                Log.e(TAG, "SDL layout not found, cannot setup game menu");
                return;
            }

            // 加载DrawerLayout布局
            LayoutInflater inflater = LayoutInflater.from(this);
            View drawerView = inflater.inflate(R.layout.activity_game, null);

            mDrawerLayout = drawerView.findViewById(R.id.game_drawer_layout);
            mGameMenu = drawerView.findViewById(R.id.game_navigation_view);
            mDrawerButton = drawerView.findViewById(R.id.game_drawer_button);
            mContentFrame = drawerView.findViewById(R.id.game_content_frame);

            // 将SDL的内容移到DrawerLayout的内容区域
            if (rootView.getParent() != null) {
                ((ViewGroup) rootView.getParent()).removeView(rootView);
            }
            mContentFrame.addView(rootView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // 将DrawerLayout设置为Activity的内容视图
            ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
            ViewGroup androidContentView = decorView.findViewById(android.R.id.content);
            androidContentView.removeAllViews();
            androidContentView.addView(mDrawerLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // 锁定抽屉，只能通过按钮打开
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

            // 设置可拖动的悬浮按钮
            setupDraggableButton(mDrawerButton, () -> {
                if (mDrawerLayout != null && mGameMenu != null) {
                    mDrawerLayout.openDrawer(mGameMenu);
                }
            });

            // 延迟显示菜单按钮（等待布局完成）
            mDrawerButton.postDelayed(() -> mDrawerButton.setVisibility(View.VISIBLE), 500);

            // 初始化编辑模式设置按钮
            mEditorSettingsButton = drawerView.findViewById(R.id.game_editor_settings_button);
            setupDraggableButton(mEditorSettingsButton, () -> {
                if (mControlEditorManager != null) {
                    mControlEditorManager.showEditorSettingsDialog();
                }
            });

            // 初始化控件编辑器管理器（需要在控件布局初始化后）
            initializeControlEditorManager();

            // 初始化菜单管理器
            mMenuManager = new GameMenuManager(this, mDrawerLayout, mGameMenu);
            mMenuManager.setOnMenuItemClickListener(new GameMenuManager.OnMenuItemClickListener() {
                @Override
                public void onToggleControls() {
                    toggleVirtualControls();
                }

                @Override
                public void onEditControls() {
                    if (mControlEditorManager != null) {
                        mControlEditorManager.enterEditMode();
                    }
                }

                @Override
                public void onQuickSettings() {
                    if (mMenuManager != null) {
                        mMenuManager.showQuickSettings();
                    }
                }

                @Override
                public void onExitGame() {
                    if (mMenuManager != null) {
                        mMenuManager.showExitConfirmDialog();
                    }
                }
            });
            mMenuManager.setupMenu();
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to setup game menu", e);
        }
    }

    /**
     * 初始化控件编辑器管理器
     */
    private void initializeControlEditorManager() {
        if (mControlLayout == null || mContentFrame == null) return;
        
        mControlEditorManager = new GameControlEditorManager(
            this, mControlLayout, mContentFrame, mEditorSettingsButton, mDrawerButton);
    }
    
    /**
     * 设置可拖动的悬浮按钮
     */
    private void setupDraggableButton(View button, Runnable onClickAction) {
        if (button == null) return;
        
        // 点击事件
        button.setOnClickListener(v -> {
            if (onClickAction != null) {
                onClickAction.run();
            }
        });
        
        // 拖动功能
        button.setOnTouchListener(new View.OnTouchListener() {
            private float mLastX;
            private float mLastY;
            private boolean mIsDragging = false;
            private static final float DRAG_THRESHOLD = 10f; // 拖动阈值（像素）
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastX = event.getRawX();
                        mLastY = event.getRawY();
                        mIsDragging = false;
                        return false; // 允许点击事件继续
                        
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - mLastX;
                        float deltaY = event.getRawY() - mLastY;
                        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        
                        // 如果移动距离超过阈值，开始拖动
                        if (distance > DRAG_THRESHOLD) {
                            if (!mIsDragging) {
                                mIsDragging = true;
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            
                            // 更新按钮位置
                            MarginLayoutParams params = (MarginLayoutParams) v.getLayoutParams();
                            float newX = params.leftMargin + deltaX;
                            float newY = params.topMargin + deltaY;
                            
                            // 限制在屏幕范围内
                            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int maxX = metrics.widthPixels - v.getWidth();
                            int maxY = metrics.heightPixels - v.getHeight();
                            newX = Math.max(0, Math.min(newX, maxX));
                            newY = Math.max(0, Math.min(newY, maxY));
                            
                            params.leftMargin = (int) newX;
                            params.topMargin = (int) newY;
                            v.setLayoutParams(params);
                            
                            mLastX = event.getRawX();
                            mLastY = event.getRawY();
                            return true;
                        }
                        return false;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (mIsDragging) {
                            mIsDragging = false;
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                        }
                        // 如果没有拖动，触发点击事件
                        if (onClickAction != null) {
                            onClickAction.run();
                        }
                        return false;
                }
                return false;
            }
        });
    }
    
    /**
     * 进入编辑模式（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void enterEditMode() {
        if (mControlEditorManager != null) {
            mControlEditorManager.enterEditMode();
        }
    }
    

    /**
     * 退出编辑模式（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void exitEditMode() {
        if (mControlEditorManager != null) {
            mControlEditorManager.exitEditMode();
        }
    }

    /**
     * 添加按钮（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void addButton() {
        if (mControlEditorManager != null) {
            mControlEditorManager.addButton();
        }
    }

    /**
     * 添加摇杆（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void addJoystick() {
        if (mControlEditorManager != null) {
            mControlEditorManager.addJoystick();
        }
    }

    /**
     * 显示摇杆模式批量设置对话框（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void showJoystickModeDialog() {
        if (mControlEditorManager != null) {
            mControlEditorManager.showJoystickModeDialog();
        }
    }

    /**
     * 保存控制布局（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void saveControlLayout() {
        if (mControlEditorManager != null) {
            mControlEditorManager.saveControlLayout();
        }
    }

    /**
     * 加载控制布局（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void loadControlLayout() {
        if (mControlEditorManager != null) {
            mControlEditorManager.loadControlLayout();
        }
    }

    /**
     * 重置为默认控制布局（已废弃，使用 GameControlEditorManager）
     */
    @Deprecated
    private void resetToDefaultLayout() {
        if (mControlEditorManager != null) {
            mControlEditorManager.resetToDefaultLayout();
        }
    }

    /**
     * 打开控制布局编辑器（已废弃，现在使用游戏内编辑）
     */
    @Deprecated
    private void openControlEditor() {
        try {
            Intent intent = new Intent(this, ControlEditorActivity.class);
            startActivityForResult(intent, CONTROL_EDITOR_REQUEST_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open control editor", e);
            ErrorHandler.handleError("无法打开控制编辑器", e, false);
        }
    }

    /**
     * 显示快速设置（已废弃，使用 GameMenuManager）
     */
    @Deprecated
    private void showQuickSettings() {
        if (mMenuManager != null) {
            mMenuManager.showQuickSettings();
        }
    }

    /**
     * 显示退出确认对话框（已废弃，使用 GameMenuManager）
     */
    @Deprecated
    private void showExitConfirmDialog() {
        if (mMenuManager != null) {
            mMenuManager.showExitConfirmDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONTROL_EDITOR_REQUEST_CODE && resultCode == RESULT_OK) {
            // 从控制编辑器返回，重新加载布局
            if (mControlLayout != null) {
                mControlLayout.loadLayoutFromManager();

                // 统一禁用所有视图裁剪，确保控件边框完整显示
                disableClippingRecursive(mControlLayout);
            }
        }
    }

    /**
     * 处理返回键按下事件
     * 注意: SDLActivity 的 onBackPressed() 会调用 super.onBackPressed() 导致直接退出
     * 我们在这里完全覆盖这个行为,不调用 super,而是显示确认对话框
     */
    @Override
    public void onBackPressed() {
        AppLogger.debug(TAG, "onBackPressed() called - handling back button");

        // 如果抽屉菜单打开,先关闭它
        if (mMenuManager != null && mMenuManager.isMenuOpen()) {
            AppLogger.debug(TAG, "Closing drawer menu");
            mMenuManager.closeMenu();
            return;
        }

        // 如果在编辑模式,提示退出编辑
        if (mControlEditorManager != null && mControlEditorManager.isInEditor()) {
            AppLogger.debug(TAG, "Exiting edit mode");
            mControlEditorManager.exitEditMode();
            return;
        }

        // 否则显示退出确认对话框
        // 关键: 这里不调用 super.onBackPressed(), 这样可以阻止 SDLActivity 和 Activity 的默认 finish() 行为
        AppLogger.debug(TAG, "Showing exit confirmation dialog");
        if (mMenuManager != null) {
            mMenuManager.showExitConfirmDialog();
        }
        // 绝对不要调用 super.onBackPressed() !!!
    }

    @Override
    protected void onDestroy() {
        AppLogger.info(TAG, "GameActivity.onDestroy() called");
        super.onDestroy();
    }

    /**
     * 将文本发送到SDL游戏
     * 直接发送SDL_TEXTINPUT事件（这是Terraria/FNA正确接收文本的方式）
     */
    public static void sendTextToGame(String text) {
        try {

            // 直接调用SDLInputConnection.nativeCommitText
            // 这会触发SDL_TEXTINPUT事件，FNA会接收并转发给Terraria
            Class<?> sdlInputConnectionClass = Class.forName("org.libsdl.app.SDLInputConnection");
            java.lang.reflect.Method nativeCommitText = sdlInputConnectionClass.getDeclaredMethod(
                "nativeCommitText", String.class, int.class);
            nativeCommitText.setAccessible(true);

            // 发送文本，newCursorPosition设为1（光标移到文本末尾）
            nativeCommitText.invoke(null, text, 1);

        } catch (Exception e) {
            Log.e(TAG, "发送文本失败", e);
            Log.e(TAG, "错误详情: " + e.getMessage());
        }
    }

    /**
     * 发送Backspace删除操作到SDL游戏
     * 通过发送Backspace按键事件实现删除功能
     */
    public static void sendBackspace() {
        try {
            // 发送Backspace按键事件（SDL_KEYDOWN + SDL_KEYUP）
            Class<?> sdlActivityClass = Class.forName("org.libsdl.app.SDLActivity");
            java.lang.reflect.Method onNativeKeyDown = sdlActivityClass.getDeclaredMethod(
                "onNativeKeyDown", int.class);
            java.lang.reflect.Method onNativeKeyUp = sdlActivityClass.getDeclaredMethod(
                "onNativeKeyUp", int.class);
            onNativeKeyDown.setAccessible(true);
            onNativeKeyUp.setAccessible(true);

            // SDL_SCANCODE_BACKSPACE = 42
            int SDL_SCANCODE_BACKSPACE = 42;

            // 按下Backspace
            onNativeKeyDown.invoke(null, SDL_SCANCODE_BACKSPACE);
            // 释放Backspace
            onNativeKeyUp.invoke(null, SDL_SCANCODE_BACKSPACE);

        } catch (Exception e) {
            Log.e(TAG, "发送Backspace失败", e);
        }
    }

    /**
     * 递归禁用所有子视图的裁剪
     * 确保控件边框等绘制内容不会被父容器裁剪
     */
    private void disableClippingRecursive(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            viewGroup.setClipChildren(false);
            viewGroup.setClipToPadding(false);

            // 递归处理所有子视图
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                disableClippingRecursive(viewGroup.getChildAt(i));
            }
        }

        // 对所有视图禁用裁剪边界和轮廓裁剪
        view.setClipToOutline(false);
        view.setClipBounds(null);
    }

    /**
     * 启用SDL文本输入以支持Terraria的IME
     *
     * 工作原理：
     * 1. SDL.showTextInput() 会启动SDL的文本输入模式
     * 2. FNA的TextInputEXT会接收SDL_TEXTINPUT事件
     * 3. Terraria的FnaIme会接收TextInput事件
     * 4. FnaIme.OnCharCallback()检查IsEnabled，只有启用时才转发
     * 5. 通过调用SDL.showTextInput()，让Terraria的IME服务启用
     *
     * 这个方法会在用户点击"键盘"按钮时被调用。
     */
    public static void enableSDLTextInputForIME() {
        try {

            // 方法1：调用SDL的showTextInput方法（这是正确的方法！）
            try {
                Class<?> sdlActivityClass = Class.forName("org.libsdl.app.SDLActivity");
                java.lang.reflect.Method showTextInput = sdlActivityClass.getDeclaredMethod(
                    "showTextInput", int.class, int.class, int.class, int.class);
                showTextInput.setAccessible(true);
                // 设置整个屏幕为输入区域
                boolean result = (Boolean) showTextInput.invoke(null, 0, 0, 1920, 1080);
                if (result) {
                    return;
                } else {
                    Log.w(TAG, "SDLActivity.showTextInput()返回false");
                }
            } catch (Exception e) {

            }

            // 如果showTextInput失败，记录警告
            // （不再尝试其他备用方案，避免副作用）

            Log.w(TAG, "所有SDL文本输入启用方法都失败了");
            Log.w(TAG, "文本输入功能可能无法正常工作");
            Log.w(TAG, "建议：在游戏内使用内置虚拟键盘或连接蓝牙键盘");

        } catch (Exception e) {
            Log.e(TAG, "启用SDL文本输入时发生异常", e);
        }
    }

    /**
     * 禁用SDL文本输入
     * 防止SDL自动显示文本框
     */
    public static void disableSDLTextInput() {
        try {

            // 调用SDL的hideTextInput方法
            Class<?> sdlActivityClass = Class.forName("org.libsdl.app.SDLActivity");
            java.lang.reflect.Method hideTextInput = sdlActivityClass.getDeclaredMethod("hideTextInput");
            hideTextInput.setAccessible(true);
            hideTextInput.invoke(null);

        } catch (Exception e) {

        }
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
                Toast.makeText(mainActivity, "游戏已成功运行完成", Toast.LENGTH_LONG).show();
            } else {
                String message;
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    message = errorMessage + "\n退出代码: " + exitCode;
                } else {
                    message = "退出代码: " + exitCode;
                }
                ErrorHandler.showWarning("游戏运行失败", message);
            }
            mainActivity.finish();
        });
    }


}