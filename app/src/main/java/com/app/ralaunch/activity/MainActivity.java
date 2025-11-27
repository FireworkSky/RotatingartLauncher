package com.app.ralaunch.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.dialog.LocalImportDialog;
import com.app.ralaunch.fragment.ControlLayoutFragment;
import com.app.ralaunch.fragment.FileBrowserFragment;
import com.app.ralaunch.fragment.InitializationFragment;
import com.app.ralaunch.fragment.LocalImportFragment;
import com.app.ralaunch.adapter.GameAdapter;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.fragment.SettingsFragment;
import com.app.ralaunch.fragment.SettingsDialogFragment;
import com.app.ralaunch.utils.PageManager;
import com.daimajia.androidanimations.library.Techniques;
import com.app.ralaunch.utils.PermissionHelper;
import com.daimajia.androidanimations.library.YoYo;
import com.app.ralaunch.utils.RuntimeManager;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.LocaleManager;
import com.app.ralib.error.ErrorHandler;
import android.content.Context;
import com.app.ralaunch.manager.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    private GameImportManager gameImportManager;
    private GameDeletionManager gameDeletionManager;
    private FileBrowserManager fileBrowserManager;
    private UIManager uiManager;
    private ThemeManager themeManager;
    private GameLaunchManager gameLaunchManager;
    
    // 保留必要的字段
    public static MainActivity mainActivity;
    private LinearLayout mainLayout;
    
    // UI 控件（仅用于初始化管理器）
    private View selectedGameInfo;
    private ImageView selectedGameImage;
    private TextView selectedGameName;
    private TextView selectedGameDescription;
    private com.app.ralib.ui.ModernButton launchGameButton;
    private CardView emptySelectionText;
    private RecyclerView gameRecyclerView;
    private View runtimeSelectContainer;
    private View btnRuntimeSelector;
    private TextView tvCurrentRuntime;
    private MaterialButton settingsButton;
    private MaterialButton addGameButton;
    private MaterialButton refreshButton;
    private MaterialButton gogButton;
    private com.app.ralib.ui.ViewSwitcherButton viewSwitchButton;
    private View fileBrowserContainer;

    // 权限回调接口
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // 应用语言设置
        super.attachBaseContext(LocaleManager.applyLanguage(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 初始化主题管理器并应用主题（必须在 super.onCreate 之前）
        themeManager = new ThemeManager(this);
        themeManager.applyThemeFromSettings();

        super.onCreate(savedInstanceState);
        mainActivity = this;

        // 初始化日志系统
        initializeLogger();

        // 初始化错误处理器
        ErrorHandler.init(this);

        // 初始化管理器（部分管理器需要在 setContentView 之前设置窗口特性）
        initializeManagersBeforeContentView();
        
        // 设置全屏模式（必须在 setContentView 之前）
        uiManager.setupFullscreen();

        setContentView(R.layout.activity_main);

        // 应用背景设置
        themeManager.applyBackgroundFromSettings();

        // 初始化视频背景
        updateVideoBackground();

        // 先初始化基本 UI 控件（管理器需要这些控件）
        mainLayout = findViewById(R.id.mainLayout);

        // 初始化需要 View 的管理器（在 setContentView 之后）
        initializeManagersAfterContentView();

        // 初始化界面
        setupUI();

        // 检查初始化状态
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean componentsExtracted = prefs.getBoolean("components_extracted", false);
        boolean legalAgreed = prefs.getBoolean("legal_agreed", false);
        if (!legalAgreed || !componentsExtracted) {
            showInitializationFragment();
        } else {
            initializeApp();
        }
    }
    
    /**
     * 初始化所有管理器
     */
    /**
     * 在 setContentView 之前初始化的管理器
     */
    private void initializeManagersBeforeContentView() {
        // 初始化权限管理器
        permissionManager = new PermissionManager(this);
        permissionManager.initialize();
        
        // 初始化 UI 管理器
        uiManager = new UIManager(this);
        
        // 初始化游戏列表管理器
        gameListManager = new GameListManager(this);
        
        // 初始化运行时选择器管理器
        runtimeSelectorManager = new RuntimeSelectorManager(this);
    }
    
    /**
     * 在 setContentView 之后初始化的管理器（需要访问 View）
     */
    private void initializeManagersAfterContentView() {
        // 初始化 Fragment 导航器（需要 mainLayout）
        fragmentNavigator = new FragmentNavigator(getSupportFragmentManager(), R.id.fragmentContainer, mainLayout);
        
        // 初始化游戏导入管理器（需要 fragmentNavigator）
        gameImportManager = new GameImportManager(this, fragmentNavigator);
        
        // 初始化游戏删除管理器
        gameDeletionManager = new GameDeletionManager(this);
        
        // 初始化文件浏览器管理器
        fileBrowserManager = new FileBrowserManager(this);
        
        // 初始化游戏启动管理器
        gameLaunchManager = new GameLaunchManager(this);
        
        // 初始化主题管理器
        themeManager = new ThemeManager(this);
        
        // 初始化游戏删除管理器
        gameDeletionManager = new GameDeletionManager(this);
        
        // 初始化文件浏览器管理器
        fileBrowserManager = new FileBrowserManager(this);
        
        // 初始化游戏启动管理器
        gameLaunchManager = new GameLaunchManager(this);
    }

    private void showInitializationFragment() {
        if (fragmentNavigator != null) {
            InitializationFragment initFragment = new InitializationFragment();
            initFragment.setOnInitializationCompleteListener(this::onInitializationComplete);
            
            // 初始化 Fragment 不使用回退栈
            fragmentNavigator.getFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, initFragment)
                    .commit();
            
            // 手动隐藏主布局
            if (uiManager != null) {
                uiManager.hideMainLayout();
            }
            View fragmentContainer = findViewById(R.id.fragmentContainer);
            if (fragmentContainer != null) {
                fragmentContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void onInitializationComplete() {
        // 初始化完成后显示主界面
        if (uiManager != null) {
            uiManager.showMainLayout();
        }
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
        initializeApp();
    }

    private void initializeApp() {
        // 初始化游戏数据
        initializeGameData();

        // 游戏列表已由 gameListManager 管理，无需手动刷新

        // 初始化完成后重新设置运行时选择器
        if (runtimeSelectorManager != null && fragmentNavigator != null) {
            if (btnRuntimeSelector != null) {
                btnRuntimeSelector.setOnClickListener(v -> {
                    runtimeSelectorManager.showRuntimeSelectorDialog(fragmentNavigator.getFragmentManager());
                });
            }
        }
    }

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

    private void initializeGameData() {
        // 游戏列表管理器会自动加载数据，无需手动初始化
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
        fileBrowserContainer = findViewById(R.id.fileBrowserContainer);
        
        // 初始化游戏列表管理器
        gameListManager.initialize(gameRecyclerView, selectedGameInfo, selectedGameImage,
                selectedGameName, selectedGameDescription, emptySelectionText);
        gameListManager.setOnGameSelectedListener(game -> {
            // 游戏选择处理 - 显示启动按钮
            uiManager.showLaunchGameButton();
        });
        gameListManager.setOnGameDeleteListener(this);

        // 初始化游戏导入管理器
        gameImportManager.setOnImportCompleteListener(this::onImportComplete);
        
        // 初始化 UI 管理器
        settingsButton = findViewById(R.id.settingsButton);
        addGameButton = findViewById(R.id.addGameButton);
        refreshButton = findViewById(R.id.refreshButton);
        gogButton = findViewById(R.id.gogButton);
        uiManager.initialize(mainLayout, settingsButton, addGameButton, refreshButton, gogButton, launchGameButton);
        
        // 初始化文件浏览器管理器
        fileBrowserManager.initialize(fileBrowserContainer, gameRecyclerView, selectedGameInfo,
                selectedGameImage, selectedGameName, selectedGameDescription, emptySelectionText);
        
        // 初始化运行时选择器管理器
        runtimeSelectorManager.initialize(runtimeSelectContainer, btnRuntimeSelector, tvCurrentRuntime);
        runtimeSelectorManager.setOnVersionChangedListener(version -> {
            showSuccessSnackbar("已切换到 .NET " + version);
        });

        // 设置按钮监听器（通过 UI 管理器）
        uiManager.setSettingsButtonListener(v -> showSettingsFragment());
        uiManager.setAddGameButtonListener(v -> showAddGameFragment());
        uiManager.setRefreshButtonListener(v -> {
            showToast("刷新游戏列表");
            gameListManager.refreshGameList();
        });
        uiManager.setGogButtonListener(v -> showGogClientFragment());

        // 视图切换按钮 (ralib 组件)
        viewSwitchButton = findViewById(R.id.viewSwitchButton);
        if (viewSwitchButton != null) {
            viewSwitchButton.setOnStateChangedListener(isSecondary -> {
                if (isSecondary) {
                    fileBrowserManager.showFileBrowserMode();
                    showInfoSnackbar("文件浏览器模式 - 选择 DLL/EXE 文件启动");
                } else {
                    fileBrowserManager.showGameListMode();
                    showInfoSnackbar("游戏列表模式");
                }
            });
        }
        
        // 设置文件浏览器文件选择监听器
        fileBrowserManager.setOnFileSelectedListener(file -> {
            uiManager.showLaunchGameButton();
        });

        // 启动游戏按钮监听器
        uiManager.setLaunchGameButtonListener(v -> {
            if (fileBrowserManager.isFileBrowserMode()) {
                // 文件浏览器模式：启动选中的程序集
                File assemblyFile = fileBrowserManager.getSelectedAssemblyFile();
                if (assemblyFile != null) {
                    gameLaunchManager.launchAssembly(assemblyFile);
                } else {
                    showToast("请先选择一个 DLL 或 EXE 文件");
                }
            } else {
                // 游戏列表模式：启动选中的游戏
                GameItem selectedGame = gameListManager.getSelectedGame();
                if (selectedGame != null) {
                    gameLaunchManager.launchGame(selectedGame);
                } else {
                    showToast("请先选择一个游戏");
                }
            }
        });

        // ModLoader 开关监听已移除 - 现在直接启动选中的程序集

        // 控制布局按钮
        MaterialButton controlLayoutButton = findViewById(R.id.controlLayoutButton);
        com.app.ralaunch.manager.common.ButtonAnimationManager.setClickListenerWithBounce(
            controlLayoutButton, v -> showControlLayoutFragment());

        // 默认显示主界面
        showMainLayout();

        // 初始化运行时版本选择（已在 runtimeSelectorManager.initialize 中设置）
    }

    // setupRuntimeSelector 方法已移除，逻辑在 runtimeSelectorManager.initialize 中

    private void showGogClientFragment() {
        if (fragmentNavigator != null) {
            com.app.ralaunch.gog.GogClientFragment gogFragment = new com.app.ralaunch.gog.GogClientFragment();
            fragmentNavigator.showPage(gogFragment, "gog_client");
        }
    }

    private void showControlLayoutFragment() {
        // 确保文件浏览器模式已关闭，避免残影
        if (fileBrowserManager != null && fileBrowserManager.isFileBrowserMode()) {
            fileBrowserManager.showGameListMode();
        }
        
        // 确保主布局显示
        if (uiManager != null) {
            uiManager.showMainLayout();
        }
        
        if (fragmentNavigator != null) {
            ControlLayoutFragment controlFragment = new ControlLayoutFragment();
            controlFragment.setOnControlLayoutBackListener(this::hideControlLayoutFragment);
            fragmentNavigator.showPage(controlFragment, "control_layout");
        }
    }

    private void hideControlLayoutFragment() {
        if (fragmentNavigator != null) {
            fragmentNavigator.hideFragmentWithCleanup("control_layout");
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

    private void showAddGameFragment() {
        if (gameImportManager != null && fragmentNavigator != null) {
            gameImportManager.showAddGameDialog(fragmentNavigator.getFragmentManager());
        }
    }

    // 实现 OnImportCompleteListener
    @Override
    public void onImportComplete(String gameType, GameItem newGame) {
        // 导入完成后直接添加游戏到列表
        if (gameListManager != null) {
            gameListManager.addGame(newGame);
            showToast("游戏添加成功！");
            fragmentNavigator.hideFragment("local_import");
        }
    }

    // 文件浏览器相关方法已迁移到 FileBrowserManager
    
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
                    
                    if (filesDeleted) {
                        showSuccessSnackbar("游戏及文件已删除");
                    } else {
                        showInfoSnackbar("游戏已从列表删除，但部分文件可能未删除");
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

    private void showSettingsFragment() {
        if (fragmentNavigator != null) {
            SettingsDialogFragment dialog = SettingsDialogFragment.newInstance();
            dialog.show(fragmentNavigator.getFragmentManager(), "settings_dialog");
        }
    }

    @Override
    public void onSettingsBack() {
        hideSettingsFragment();
    }

    private void hideSettingsFragment() {
        if (fragmentNavigator != null) {
            fragmentNavigator.hideFragment("settings");
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
                hideControlLayoutFragment();
            } else if (fragmentNavigator.getBackStackCount() > 1) {
                fragmentNavigator.goBack();
            } else {
                fragmentNavigator.hideFragment();
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
     * 更新视频背景
     */
    public void updateVideoBackground() {
        try {
            com.app.ralaunch.view.VideoBackgroundView videoBackgroundView = findViewById(R.id.videoBackgroundView);
            if (videoBackgroundView == null) {
                return;
            }

            com.app.ralaunch.data.SettingsManager settingsManager = com.app.ralaunch.data.SettingsManager.getInstance(this);
            
            if (themeManager.isVideoBackground()) {
                String videoPath = themeManager.getVideoBackgroundPath();
                if (videoPath != null && !videoPath.isEmpty()) {
                    videoBackgroundView.setVisibility(View.VISIBLE);
                    videoBackgroundView.setVideoPath(videoPath);
                    videoBackgroundView.setOpacity(100); // 背景始终完全不透明
                    videoBackgroundView.start();
                } else {
                    videoBackgroundView.setVisibility(View.GONE);
                    videoBackgroundView.release();
                }
            } else {
                videoBackgroundView.setVisibility(View.GONE);
                videoBackgroundView.release();
            }
        } catch (Exception e) {
            AppLogger.error("MainActivity", "更新视频背景失败: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放视频背景资源
        com.app.ralaunch.view.VideoBackgroundView videoBackgroundView = findViewById(R.id.videoBackgroundView);
        if (videoBackgroundView != null) {
            videoBackgroundView.release();
        }
        
        // 在应用退出时保存游戏列表
        // gameDataManager.updateGameList(gameList);

        // 只在应用真正退出时关闭日志系统（不是因为配置变化如旋转或主题切换）
        if (!isChangingConfigurations()) {
            AppLogger.info("MainActivity", "RALaunch stopped");
            AppLogger.close();
        } else {
            AppLogger.info("MainActivity", "MainActivity recreating due to configuration change");
        }
    }
}