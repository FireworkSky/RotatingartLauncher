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
import com.app.ralaunch.controls.editor.ControlEditorManager;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralib.error.ErrorHandler;
import com.app.ralaunch.manager.GameMenuManager;
import com.app.ralaunch.manager.GameFullscreenManager;

import com.app.ralaunch.core.GameLauncher;
import com.app.ralaunch.controls.ControlLayout;
import com.app.ralaunch.controls.SDLInputBridge;
import com.app.ralib.patch.Patch;
import com.app.ralib.patch.PatchManager;
import com.app.ralaunch.renderer.OSMRenderer;
import com.app.ralaunch.renderer.RendererConfig;
import com.app.ralaunch.renderer.RendererLoader;
import com.app.ralaunch.renderer.OSMSurface;

import org.libsdl.app.SDLActivity;
import android.view.Surface;

import java.io.File;
import java.util.ArrayList;

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
    private ControlEditorManager mControlEditorManager;
    private GameMenuManager mMenuManager;
    private GameFullscreenManager mFullscreenManager;

    @Override
    public void loadLibraries() {
        try {
            RuntimePreference.applyRendererEnvironment(this);
            AppLogger.info(TAG, "Renderer environment applied before native library load");
            
            // 设置隐藏鼠标光标环境变量
            com.app.ralaunch.data.SettingsManager settingsManager = 
                com.app.ralaunch.data.SettingsManager.getInstance(this);
            boolean hideCursor = settingsManager.isHideCursorEnabled();
            if (hideCursor) {
                try {
                    android.system.Os.setenv("RALCORE_HIDE_CURSOR", "1", true);
                    AppLogger.info(TAG, "Hide cursor enabled (RALCORE_HIDE_CURSOR=1)");
                } catch (Exception e) {
                    AppLogger.warn(TAG, "Failed to set RALCORE_HIDE_CURSOR: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to apply renderer environment before loading libraries: " + e.getMessage());
        }
        super.loadLibraries();
    }

    /**
     * 创建 SDL Surface（使用 OSMesa-aware Surface）
     * 当选择 zink 渲染器时，使用 OSMSurface 以自动初始化 OSMesa
     */
    @Override
    protected org.libsdl.app.SDLSurface createSDLSurface(Context context) {
        try {
            String currentRenderer = RendererLoader.getCurrentRenderer();
            AppLogger.info(TAG, "Current renderer from environment: " + currentRenderer);

            // 检查是否是 zink 渲染器（RALCORE_RENDERER 可能是 "vulkan_zink"）
            boolean isZink = RendererConfig.RENDERER_ZINK.equals(currentRenderer) ||
                            "vulkan_zink".equals(currentRenderer);

            if (isZink) {
                AppLogger.info(TAG, "Creating OSMesa-aware SDL Surface for zink renderer");
                return new OSMSurface(context);
            } else {
                AppLogger.info(TAG, "Using standard SDL Surface for renderer: " + currentRenderer);
            }
        } catch (Exception e) {
            AppLogger.warn(TAG, "Failed to check renderer, using default SDL Surface: " + e.getMessage());
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

        // 初始化虚拟控制系统
        initializeVirtualControls();

        // 设置游戏内菜单（需要在虚拟控制初始化后）
        setupGameMenu();

        String runtimePref = getIntent().getStringExtra("DOTNET_FRAMEWORK");

        AppLogger.info(TAG, "Normal game launch mode");

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

    }
    




    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
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

            AppLogger.info(TAG, "Starting game: " + (gameName != null ? gameName : "Unknown"));
            AppLogger.info(TAG, "Assembly: " + assemblyPath);

            java.util.ArrayList<String> enabledPatchIds = getIntent().getStringArrayListExtra("ENABLED_PATCH_IDS");

            @Nullable ArrayList<Patch> enabledPatches = null;
            if (enabledPatchIds != null && !enabledPatchIds.isEmpty()) {
                PatchManager patchManager = RaLaunchApplication.getPatchManager();
                enabledPatches = patchManager.getPatchesByIds(enabledPatchIds);

                AppLogger.info(TAG, "Enabled patches: " + enabledPatches.size());
                for (Patch patch : enabledPatches) {
                    AppLogger.info(TAG, String.format("  - %s (id: %s)", patch.manifest.name, patch.manifest.id));
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
            
            // 初始化屏幕尺寸（用于虚拟触屏功能）
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            SDLInputBridge.setScreenSize(metrics.widthPixels, metrics.heightPixels);
            AppLogger.info(TAG, "Screen size initialized: " + metrics.widthPixels + "x" + metrics.heightPixels);

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

            // 设置可拖动的悬浮按钮（与外部编辑器一致，直接打开编辑器设置对话框）
            setupDraggableButton(mDrawerButton, () -> {
                if (mControlEditorManager != null) {
                    mControlEditorManager.showSettingsDialog();
                }
            });

            // 延迟显示菜单按钮（等待布局完成）
            mDrawerButton.postDelayed(() -> mDrawerButton.setVisibility(View.VISIBLE), 500);

            // 初始化编辑模式设置按钮（现在与 game_drawer_button 功能相同，统一使用同一个按钮）
            mEditorSettingsButton = drawerView.findViewById(R.id.game_editor_settings_button);
            // 不再单独设置点击事件，因为 game_drawer_button 已经处理了

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
                    // 快速设置已废弃，直接显示编辑器设置
                    if (mControlEditorManager != null) {
                        mControlEditorManager.showSettingsDialog();
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
        
        // 使用统一的 ControlEditorManager，游戏内模式需要手动进入编辑模式
        mControlEditorManager = new ControlEditorManager(
            this, mControlLayout, mContentFrame, ControlEditorManager.MODE_IN_GAME);
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
            private float mInitialTouchX;
            private float mInitialTouchY;
            private float mInitialButtonX;
            private float mInitialButtonY;
            private boolean mIsDragging = false;
            private static final float DRAG_THRESHOLD = 10f; // 拖动阈值（像素）
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastX = event.getRawX();
                        mLastY = event.getRawY();
                        mInitialTouchX = event.getRawX();
                        mInitialTouchY = event.getRawY();
                        
                        // 获取按钮的初始屏幕位置
                        int[] location = new int[2];
                        v.getLocationOnScreen(location);
                        mInitialButtonX = location[0];
                        mInitialButtonY = location[1];
                        
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
                            
                            // 计算按钮的新屏幕位置
                            float newScreenX = mInitialButtonX + (event.getRawX() - mInitialTouchX);
                            float newScreenY = mInitialButtonY + (event.getRawY() - mInitialTouchY);
                            
                            // 限制在屏幕范围内（允许拖动到最左边x=0）
                            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int maxX = metrics.widthPixels - v.getWidth();
                            int maxY = metrics.heightPixels - v.getHeight();
                            newScreenX = Math.max(0, Math.min(newScreenX, maxX));
                            newScreenY = Math.max(0, Math.min(newScreenY, maxY));
                            
                            // 将屏幕坐标转换为相对于父容器的坐标
                            int[] parentLocation = new int[2];
                            ((View) v.getParent()).getLocationOnScreen(parentLocation);
                            float newX = newScreenX - parentLocation[0];
                            float newY = newScreenY - parentLocation[1];
                            
                            // 更新位置（优先使用setX/setY，如果父容器是FrameLayout）
                            if (v.getParent() instanceof android.widget.FrameLayout) {
                                v.setX(newX);
                                v.setY(newY);
                            } else {
                                // 使用MarginLayoutParams
                                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                                params.leftMargin = (int) newX;
                                params.topMargin = (int) newY;
                                v.setLayoutParams(params);
                            }
                            
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

        AppLogger.debug(TAG, "Showing exit confirmation dialog");
        if (mMenuManager != null) {
            mMenuManager.showExitConfirmDialog();
        }
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

            Class<?> sdlInputConnectionClass = Class.forName("org.libsdl.app.SDLInputConnection");
            java.lang.reflect.Method nativeCommitText = sdlInputConnectionClass.getDeclaredMethod(
                "nativeCommitText", String.class, int.class);
            nativeCommitText.setAccessible(true);
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

    // Touch bridge native methods
    private static native void nativeSetTouchData(int count, float[] x, float[] y, int screenWidth, int screenHeight);
    private static native void nativeClearTouchData();
    
    private static Boolean sTouchBridgeAvailable = null;
    private float[] mTouchX = new float[10];
    private float[] mTouchY = new float[10];
    
    private static boolean isTouchBridgeAvailable() {
        if (sTouchBridgeAvailable == null) {
            try {
                nativeClearTouchData();
                sTouchBridgeAvailable = true;
                AppLogger.info(TAG, "Touch bridge available in GameActivity");
            } catch (UnsatisfiedLinkError e) {
                sTouchBridgeAvailable = false;
            }
        }
        return sTouchBridgeAvailable;
    }

    /**
     * 拦截所有触摸事件，传递给 touch bridge
     * 先让虚拟控件处理触摸，然后排除被占用的触摸点
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // 先正常分发事件，让虚拟控件标记占用的触摸点
        boolean result = super.dispatchTouchEvent(event);
        // 然后更新 touch bridge（此时 TouchPointerTracker 已更新）
        updateTouchBridge(event);
        return result;
    }
    
    private void updateTouchBridge(MotionEvent event) {
        if (!isTouchBridgeAvailable()) {
            return;
        }
        
        try {
            int action = event.getActionMasked();
            
            // 获取屏幕尺寸
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            
            // 处理 ACTION_UP（最后一个触摸点抬起）
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                nativeClearTouchData();
                AppLogger.debug(TAG, "Touch bridge: cleared (ACTION_UP/CANCEL)");
                return;
            }
            
            int pointerCount = event.getPointerCount();
            int actionIndex = event.getActionIndex();
            boolean isPointerUp = (action == MotionEvent.ACTION_POINTER_UP);
            
            int validCount = 0;
            for (int i = 0; i < pointerCount && validCount < 10; i++) {
                // 跳过正在抬起的触摸点（ACTION_POINTER_UP）
                if (isPointerUp && i == actionIndex) {
                    continue;
                }
                
                // 跳过被虚拟控件占用的触摸点
                int pointerId = event.getPointerId(i);
                if (com.app.ralaunch.controls.TouchPointerTracker.isPointerConsumed(pointerId)) {
                    continue;
                }
                
                // 使用归一化坐标（0-1）
                mTouchX[validCount] = event.getX(i) / screenWidth;
                mTouchY[validCount] = event.getY(i) / screenHeight;
                validCount++;
            }
            
            // 更新触摸数据（即使 validCount == 0，也要调用以清除之前的数据）
            nativeSetTouchData(validCount, mTouchX, mTouchY, screenWidth, screenHeight);
            
            // 调试日志
            if (validCount > 0) {
                int consumedCount = com.app.ralaunch.controls.TouchPointerTracker.getConsumedCount();
                AppLogger.debug(TAG, "Touch bridge: game=" + validCount + ", controls=" + consumedCount + 
                    ", action=" + actionToString(action));
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Error in updateTouchBridge: " + e.getMessage(), e);
        }
    }
    
    private String actionToString(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN: return "DOWN";
            case MotionEvent.ACTION_UP: return "UP";
            case MotionEvent.ACTION_MOVE: return "MOVE";
            case MotionEvent.ACTION_POINTER_DOWN: return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP: return "POINTER_UP";
            case MotionEvent.ACTION_CANCEL: return "CANCEL";
            default: return "UNKNOWN(" + action + ")";
        }
    }

}