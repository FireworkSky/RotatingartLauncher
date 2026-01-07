package com.app.ralaunch.activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.google.android.material.navigationrail.NavigationRailView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.fragment.ControlLayoutFragment;
import com.app.ralaunch.fragment.FileBrowserFragment;
import com.app.ralaunch.fragment.GameImportFragment;
import com.app.ralaunch.fragment.InitializationFragment;
import com.app.ralaunch.fragment.LocalImportFragment;
import com.app.ralaunch.adapter.GameAdapter;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.fragment.SettingsFragment;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.LocaleManager;
import com.app.ralib.error.ErrorHandler;
import android.content.Context;
import com.app.ralaunch.manager.*;
import java.io.File;

public class MainActivity extends AppCompatActivity implements
        GameAdapter.OnGameClickListener,
        GameAdapter.OnGameDeleteListener,
        SettingsFragment.OnSettingsBackListener,
        FileBrowserFragment.OnPermissionRequestListener,
        LocalImportFragment.OnImportCompleteListener {

    // 管理器
    private GameListManager gameListManager;
    private PermissionManager permissionManager;
    private FragmentNavigator fragmentNavigator;
    private RuntimeSelectorManager runtimeSelectorManager;
    private GameDeletionManager gameDeletionManager;
    private UIManager uiManager;
    private ThemeManager themeManager;
    private GameLaunchManager gameLaunchManager;
    private final MainInitializationDelegate initDelegate = new MainInitializationDelegate();
    private final MainUiDelegate uiDelegate = new MainUiDelegate();
    private MainNavigationDelegate navigationDelegate;
    private MainImportDelegate importDelegate;
    private MainControlFragmentDelegate controlFragmentDelegate;
    
    // 保留必要的字段
    public static MainActivity mainActivity;
    private LinearLayout mainLayout;
    
    // UI 控件（仅用于初始化管理器）
    private View selectedGameInfo;
    private ImageView selectedGameImage;
    private TextView selectedGameName;
    private TextView selectedGameDescription;
    private com.google.android.material.button.MaterialButton launchGameButton;
    private LinearLayout emptySelectionText;
    private RecyclerView gameRecyclerView;
    private View runtimeSelectContainer;
    private View btnRuntimeSelector;
    private TextView tvCurrentRuntime;

    // 权限回调接口
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }

    // 提供给初始化 Fragment 回调
    public void onInitializationCompleteDelegate() {
        initDelegate.onInitializationComplete(this, uiManager, fragmentNavigator, runtimeSelectorManager, btnRuntimeSelector);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // 应用语言设置
        super.attachBaseContext(LocaleManager.applyLanguage(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        com.app.ralaunch.utils.DensityAdapter.adapt(this, true);
        
      
        com.app.ralib.theme.ThemeColorManager.applyThemeColor(this);
        
        // 初始化主题管理器并应用主题（必须在 super.onCreate 之前）
        themeManager = new ThemeManager(this);
        themeManager.applyThemeFromSettings();
        super.onCreate(savedInstanceState);
        mainActivity = this;
        // 初始化日志系统
        initializeLogger();
        // 初始化错误处理器
        ErrorHandler.init(this);
        // 初始化管理器
        MainInitializationDelegate.InitBeforeResult beforeResult = initDelegate.initBeforeContent(this);
        permissionManager = beforeResult.permissionManager;
        uiManager = beforeResult.uiManager;
        gameListManager = beforeResult.gameListManager;
        runtimeSelectorManager = beforeResult.runtimeSelectorManager;
        // 设置全屏模式
        uiManager.setupFullscreen();

        setContentView(R.layout.activity_main);

        // 应用背景设置
        uiDelegate.applyBackground(this, themeManager);

        // 初始化视频背景
        uiDelegate.updateVideoBackground(this, themeManager);

        // 先初始化基本 UI 控件
        mainLayout = findViewById(R.id.mainLayout);
        
        // 应用UI透明度设置
        applyUiOpacity();

        // 初始化需要 View 的管理器
        MainInitializationDelegate.InitAfterResult afterResult =
                initDelegate.initAfterContent(this, mainLayout);
        fragmentNavigator = afterResult.fragmentNavigator;
        gameDeletionManager = afterResult.gameDeletionManager;
        gameLaunchManager = afterResult.gameLaunchManager;
        themeManager = afterResult.themeManager;

        // 初始化 delegate（必须在 setupUI 之前，因为 NavigationRail 需要 navigationDelegate）
        FileBrowserFragment.OnPermissionRequestListener permissionListener = callback -> {
            if (hasRequiredPermissions()) {
                callback.onPermissionsGranted();
            } else {
                requestRequiredPermissions(callback);
            }
        };
        navigationDelegate = new MainNavigationDelegate(this, getSupportFragmentManager(), permissionListener);
        importDelegate = new MainImportDelegate(this, getSupportFragmentManager(), fragmentNavigator, navigationDelegate);
        importDelegate.setOnImportCompleteListener(this::onImportComplete);
        controlFragmentDelegate = new MainControlFragmentDelegate(this, fragmentNavigator, uiManager);

        // 【关键】在初始化界面之前检查是否需要恢复到设置页面
        // 这样可以避免先显示游戏主页再跳转到设置的闪烁
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean restoreSettings = prefs.getBoolean("restore_settings_after_recreate", false);
        if (restoreSettings) {
            // 清除标志
            prefs.edit().putBoolean("restore_settings_after_recreate", false).apply();
        }
        
        // 初始化界面
        setupUI();
        
        // 检查初始化状态
        if (initDelegate.needInitialization(this)) {
            initDelegate.showInitializationFragment(this, fragmentNavigator, uiManager);
        } else {
            initDelegate.initializeApp(this, runtimeSelectorManager, fragmentNavigator, btnRuntimeSelector);
        }
        
        // 如果需要恢复设置页面，在 UI 初始化完成后立即显示（无延迟）
        if (restoreSettings && navigationDelegate != null) {
            // 立即显示设置页面，避免先显示游戏页面造成的闪烁
            navigationDelegate.showSettingsPage();
        }
    }
    
    /**
     * 初始化所有管理器
     */
    /**
     * 在 setContentView 之前初始化的管理器
     */
    /**
     * 检查是否具有必要的权限
     */
    public boolean hasRequiredPermissions() {
        return permissionManager != null && permissionManager.hasRequiredPermissions();
    }

    /**
     * 请求必要的权限
     */
    public void requestRequiredPermissions(PermissionCallback callback) {
        if (permissionManager != null) {
            permissionManager.requestRequiredPermissions(new PermissionManager.PermissionCallback() {
                @Override
                public void onPermissionsGranted() {
                    callback.onPermissionsGranted();
                }
                
                @Override
                public void onPermissionsDenied() {
                    callback.onPermissionsDenied();
                }
            });
        }
    }

    // 实现 FileBrowserFragment.OnPermissionRequestListener
    @Override
    public void onPermissionRequest(PermissionCallback callback) {
        if (hasRequiredPermissions()) {
            callback.onPermissionsGranted();
        } else {
            requestRequiredPermissions(callback);
        }
    }

    /**
     * Initialize logging system
     */
    private void initializeLogger() {
        try {
            File logDir = new File(getExternalFilesDir(null), "logs");
            AppLogger.init(logDir);


        } catch (Exception e) {
            // 日志系统初始化失败时使用系统日志
            Log.e("MainActivity", "Failed to initialize logger", e);
        }
    }

    private void setupUI() {
        // 初始化主界面控件
        mainLayout = findViewById(R.id.mainLayout);
        selectedGameInfo = findViewById(R.id.selectedGameInfo);
        selectedGameImage = findViewById(R.id.selectedGameImage);
        selectedGameName = findViewById(R.id.selectedGameName);
        selectedGameDescription = findViewById(R.id.selectedGameDescription);
        launchGameButton = findViewById(R.id.launchGameButton);
        emptySelectionText = findViewById(R.id.emptySelectionText);
        runtimeSelectContainer = findViewById(R.id.runtimeSelectContainer);
        btnRuntimeSelector = findViewById(R.id.btnRuntimeSelector);
        tvCurrentRuntime = findViewById(R.id.tvCurrentRuntime);

        // 初始化RecyclerView
        gameRecyclerView = findViewById(R.id.gameRecyclerView);
        gameRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        
        // 初始化游戏列表管理器
        gameListManager.initialize(gameRecyclerView, selectedGameInfo, selectedGameImage,
                selectedGameName, selectedGameDescription, emptySelectionText);
        gameListManager.setOnGameSelectedListener(game -> {
            // 游戏选择处理 - 显示启动按钮
            uiManager.showLaunchGameButton();
        });
        gameListManager.setOnGameDeleteListener(this);

        
        // 初始化 UI 管理器（刷新和添加游戏按钮已移至 NavigationRail）
        // settingsButton 和 gogButton 已移至 NavigationRail，不再需要
        uiManager.initialize(mainLayout, null, null, null, null, launchGameButton);
        
        // 初始化运行时选择器管理器
        runtimeSelectorManager.initialize(runtimeSelectContainer, btnRuntimeSelector, tvCurrentRuntime);
        runtimeSelectorManager.setOnVersionChangedListener(version -> {
            showSuccessSnackbar(getString(R.string.main_runtime_switched, version));
        });

        // NavigationRail 点击监听器 - 由 MainNavigationDelegate 处理
        NavigationRailView navigationRail = findViewById(R.id.navigationRail);
        if (navigationRail != null && navigationDelegate != null) {
            navigationDelegate.setupNavigationRail(navigationRail, 
                    this::showGamePage, 
                    this::showAddGameFragment);
        }

        // 启动游戏按钮监听器
        uiManager.setLaunchGameButtonListener(v -> {
        
            // 启动选中的游戏
                GameItem selectedGame = gameListManager.getSelectedGame();
                if (selectedGame != null) {
                    var isSuccess = gameLaunchManager.launchGame(selectedGame);
                    var settingsManager = com.app.ralaunch.data.SettingsManager.getInstance(this);
                    if (isSuccess && settingsManager.isKillLauncherUIAfterLaunch()) {
                        // Force kill this ui process after launching to save memory
                        System.exit(0);
                    }
                } else {
                    showToast(getString(R.string.main_select_game_first));
            }
        });

        // 默认显示主界面
        showMainLayout();


    }


    /**
     * 显示下载页面
     */
    private void showDownloadPage() {
        if (navigationDelegate != null) {
            navigationDelegate.showDownloadPage();
        }
    }
    
    /**
     * 显示游戏页面
     */
    private void showGamePage() {
        if (navigationDelegate != null) {
            navigationDelegate.showGamePage();
        }
    }

    /**
     * 显示文件管理器页面
     */
    private void showFilePage() {
        if (navigationDelegate != null) {
            navigationDelegate.showFilePage();
        }
    }
    
    /**
     * 显示控制布局页面
     */
    private void showControlPage() {
        if (navigationDelegate != null) {
            navigationDelegate.showControlPage();
        }
    }

    private void showControlLayoutFragment() {
        if (controlFragmentDelegate != null) {
            controlFragmentDelegate.showControlLayoutFragment();
        }
    }

    private void hideControlLayoutFragment() {
        if (controlFragmentDelegate != null) {
            controlFragmentDelegate.hideControlLayoutFragment();
        }
    }

    private void showMainLayout() {
        if (uiManager != null) {
            uiManager.showMainLayout();
        }
        showNoGameSelected();
    }

    // 消息提示方法已迁移到 MessageHelper
    private void showSuccessSnackbar(String message) {
        com.app.ralaunch.manager.common.MessageHelper.showSuccess(this, message);
    }

    private void showErrorSnackbar(String message) {
        com.app.ralaunch.manager.common.MessageHelper.showError(this, message);
    }

    private void showInfoSnackbar(String message) {
        com.app.ralaunch.manager.common.MessageHelper.showInfo(this, message);
    }

    /**
     * 显示导入游戏页面
     */
    private void showAddGameFragment() {
        if (importDelegate != null) {
            importDelegate.showAddGameFragment();
        }
    }
    
    /**
     * 开始游戏导入（供其他类调用）
     */
    public void startGameImport(String gameFilePath, String gameName, String gameVersion) {
        if (importDelegate != null) {
            importDelegate.startGameImport(gameFilePath, gameName, gameVersion);
        }
    }
    

    // 实现 OnImportCompleteListener
    @Override
    public void onImportComplete(String gameType, GameItem newGame) {
        onGameImportComplete(gameType, newGame);
    }

    /**
     * 游戏导入完成回调（供 GamePluginImportFragment 和其他导入方式使用）
     */
    public void onGameImportComplete(String gameType, GameItem newGame) {
        // 导入完成后直接添加游戏到列表
        if (gameListManager != null) {
            gameListManager.addGame(newGame);
            showToast(getString(R.string.game_added_success));
            // 切换到游戏页面
            showGamePage();
        }
    }

    
    private void refreshGameList() {
        if (gameListManager != null) {
            gameListManager.refreshGameList();
        }
    }

    @Override
    public void onGameClick(GameItem game) {
        showSelectedGame(game);
    }

    @Override
    public void onGameDelete(GameItem game, int position) {
        if (gameDeletionManager != null && gameListManager != null) {
            GameItem selectedGame = gameListManager.getSelectedGame();
            if (selectedGame == game) {
                gameListManager.showNoGameSelected();
            }
            
            gameDeletionManager.showDeleteConfirmDialog(fragmentNavigator.getFragmentManager(), game, position,
                (deletedGame, deletedPosition, filesDeleted) -> {
                    // 从列表中删除
                    gameListManager.removeGame(deletedPosition);
                    
                    if (deletedGame.isShortcut()) {
                        showSuccessSnackbar(getString(R.string.main_shortcut_removed));
                    } else if (filesDeleted) {
                        showSuccessSnackbar(getString(R.string.main_game_deleted));
                    } else {
                        showInfoSnackbar(getString(R.string.main_game_deleted_partial));
                    }
                });
        }
    }

    // 这些方法已迁移到管理器，保留空实现以兼容接口
    private void showSelectedGame(GameItem game) {
        if (gameListManager != null) {
            gameListManager.showSelectedGame(game);
            uiManager.showLaunchGameButton();
        }
    }

    private void showNoGameSelected() {
        if (gameListManager != null) {
            gameListManager.showNoGameSelected();
            uiManager.hideLaunchGameButton();
        }
    }

    // launchGame 方法已迁移到 GameLaunchManager

    public void showToast(String message) {
        com.app.ralaunch.manager.common.MessageHelper.showToast(this, message);
    }

    /**
     * 显示设置页面
     */
    private void showSettingsPage() {
        if (navigationDelegate != null) {
            navigationDelegate.showSettingsPage();
        }
    }

    @Override
    public void onSettingsBack() {
        // 设置返回时，切换到游戏页面
        if (navigationDelegate != null) {
            navigationDelegate.showGamePage();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && uiManager != null) {
            uiManager.hideSystemUI();
        }
    }

    @Override
    public void onBackPressed() {
        if (fragmentNavigator != null && !fragmentNavigator.isMainLayoutVisible()) {
            // 检查当前Fragment是否是ControlLayoutFragment
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (currentFragment instanceof ControlLayoutFragment) {
                if (controlFragmentDelegate != null) {
                    controlFragmentDelegate.hideControlLayoutFragment();
                }
            } else {
                fragmentNavigator.goBack();
            }
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (themeManager != null) {
            themeManager.handleConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onResume() {
        // 注意：MIUI 等定制系统在配置变化时可能会抛出 ClassCastException
        // 这是系统框架层面的问题，不影响应用功能
        try {
            super.onResume();
        } catch (Exception e) {
            AppLogger.error("MainActivity", "onResume 系统错误: " + e.getMessage());
        }
        
        // 更新ErrorHandler的Activity引用
        try {
            ErrorHandler.setCurrentActivity(this);
        } catch (Exception e) {
            AppLogger.error("MainActivity", "设置 ErrorHandler 失败: " + e.getMessage());
        }
        
        // 延迟恢复视频背景播放，确保 Activity 完全恢复
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            try {
                com.app.ralaunch.view.VideoBackgroundView videoBackgroundView = findViewById(R.id.videoBackgroundView);
                if (videoBackgroundView != null && videoBackgroundView.getVisibility() == View.VISIBLE) {
                    videoBackgroundView.start();
                }
            } catch (Exception e) {
                AppLogger.error("MainActivity", "恢复视频背景失败: " + e.getMessage());
            }
        }, 200);
    }

    @Override
    protected void onPause() {
        // 先暂停视频背景播放
        try {
            com.app.ralaunch.view.VideoBackgroundView videoBackgroundView = findViewById(R.id.videoBackgroundView);
            if (videoBackgroundView != null && videoBackgroundView.getVisibility() == View.VISIBLE) {
                videoBackgroundView.pause();
            }
        } catch (Exception e) {
            AppLogger.error("MainActivity", "暂停视频背景失败: " + e.getMessage());
        }
        
        super.onPause();
    }
    
    /**
     * 获取主题管理器
     */
    public ThemeManager getThemeManager() {
        return themeManager;
    }

    /**
     * 获取 Fragment 导航器
     */
    public FragmentNavigator getFragmentNavigator() {
        return fragmentNavigator;
    }

    /**
     * Fragment 返回
     */
    public void onFragmentBack() {
        if (fragmentNavigator != null) {
            fragmentNavigator.hideFragment();
        }
    }

    /**
     * 更新视频背景
     */
    public void updateVideoBackground() {
        uiDelegate.updateVideoBackground(this, themeManager);
    }

    /**
     * 更新视频背景播放速度
     */
    public void updateVideoBackgroundSpeed(float speed) {
        uiDelegate.updateVideoBackgroundSpeed(this, speed);
    }

    /**
     * 更新视频背景透明度
     */
    public void updateVideoBackgroundOpacity(int opacity) {
        uiDelegate.updateVideoBackgroundOpacity(this, opacity);
    }
    
    /**
     * 应用UI透明度设置 
     */
    private void applyUiOpacity() {
        if (mainLayout != null) {
            boolean hasBackground = hasBackgroundSet();
            
            // 使用统一工具类计算透明度
            float uiAlpha = com.app.ralaunch.utils.OpacityHelper.getUiAlphaFromSettings(this, hasBackground);
            
            mainLayout.setAlpha(uiAlpha);
            AppLogger.info("MainActivity", "初始UI透明度: uiAlpha=" + uiAlpha);
        }
    }
    
    /**
     * 检查是否设置了背景
     */
    private boolean hasBackgroundSet() {
        com.app.ralaunch.data.SettingsManager settingsManager = 
            com.app.ralaunch.data.SettingsManager.getInstance(this);
        String imagePath = settingsManager.getBackgroundImagePath();
        String videoPath = settingsManager.getBackgroundVideoPath();
        return (imagePath != null && !imagePath.isEmpty()) || 
               (videoPath != null && !videoPath.isEmpty());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放视频背景资源
        uiDelegate.releaseVideoBackground(this);
        
        // 在应用退出时保存游戏列表
        // gameDataManager.updateGameList(gameList);

        if (!isChangingConfigurations()) {
            AppLogger.close();
        }
    }
}