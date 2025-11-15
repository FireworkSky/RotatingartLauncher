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
import android.widget.ImageButton;
import android.widget.ImageView;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        GameAdapter.OnGameClickListener,
        GameAdapter.OnGameDeleteListener,
        SettingsFragment.OnSettingsBackListener,
        FileBrowserFragment.OnPermissionRequestListener,
        LocalImportFragment.OnImportCompleteListener {

    private PageManager pageManager;
    public static MainActivity mainActivity;
    private RecyclerView gameRecyclerView;
    private GameAdapter gameAdapter;
    private List<GameItem> gameList;
    private ActivityResultLauncher<Intent> manageAllFilesLauncher;
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private PermissionCallback currentPermissionCallback;

    // 界面控件
    private View selectedGameInfo;
    private ImageView selectedGameImage;
    private TextView selectedGameName;
    private TextView selectedGameDescription;
    private android.widget.EditText selectedGamePath;
    private com.app.ralib.ui.ModernButton launchGameButton;
    private CardView emptySelectionText;
    private LinearLayout mainLayout;
    private LinearLayout modLoaderSwitchContainer;
    private androidx.appcompat.widget.SwitchCompat modLoaderSwitch;
    private View runtimeSelectContainer;
    private View btnRuntimeSelector;
    private TextView tvCurrentRuntime;
    private ImageButton settingsButton;
    private ImageButton addGameButton;
    private ImageButton refreshButton;
    private com.app.ralib.ui.ViewSwitcherButton viewSwitchButton;
    private View fileBrowserContainer;
    private com.app.ralib.ui.GameFileBrowser gameFileBrowser;
    private GameItem selectedGame;
    private File selectedAssemblyFile;
    private boolean isFileBrowserMode = false;

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
        // 应用保存的主题设置（必须在 super.onCreate 之前）
        applyThemeFromSettings();

        super.onCreate(savedInstanceState);
        mainActivity = this;

        // 初始化日志系统
        initializeLogger();

        // 初始化错误处理器
        ErrorHandler.init(this);

        // 设置全屏和横屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        // 初始化界面
        setupUI();

        // 初始化权限请求
        initializePermissionLaunchers();

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

    private void showInitializationFragment() {
        mainLayout.setVisibility(View.GONE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);

        InitializationFragment initFragment = new InitializationFragment();
        initFragment.setOnInitializationCompleteListener(this::onInitializationComplete);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, initFragment)
                .commit();
    }

    private void onInitializationComplete() {
        // 初始化完成后显示主界面
        mainLayout.setVisibility(View.VISIBLE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.GONE);

        initializeApp();
    }

    private void initializeApp() {
        // 初始化游戏数据
        initializeGameData();

        // 刷新游戏列表显示
        if (gameAdapter != null) {
            gameAdapter.updateGameList(gameList);
        }

        // 初始化完成后重新设置运行时选择器
        setupRuntimeSelector();
    }

    /**
     * 初始化权限请求
     */
    private void initializePermissionLaunchers() {
        requestPermissionLauncher = PermissionHelper.registerStoragePermissions(this, new PermissionHelper.Callback() {
            @Override public void onGranted() { if (currentPermissionCallback != null) currentPermissionCallback.onPermissionsGranted(); currentPermissionCallback = null; }
            @Override public void onDenied() { if (currentPermissionCallback != null) currentPermissionCallback.onPermissionsDenied(); currentPermissionCallback = null; }
        });
        manageAllFilesLauncher = PermissionHelper.registerAllFilesAccess(this, new PermissionHelper.Callback() {
            @Override public void onGranted() { if (currentPermissionCallback != null) currentPermissionCallback.onPermissionsGranted(); currentPermissionCallback = null; }
            @Override public void onDenied() { if (currentPermissionCallback != null) currentPermissionCallback.onPermissionsDenied(); currentPermissionCallback = null; }
        });
    }

    /**
     * 检查是否具有必要的权限
     */
    public boolean hasRequiredPermissions() { return PermissionHelper.hasStorageAccess(this); }

    /**
     * 请求必要的权限
     */
    public void requestRequiredPermissions(PermissionCallback callback) {
        this.currentPermissionCallback = callback;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PermissionHelper.requestStorage(this, requestPermissionLauncher, manageAllFilesLauncher);
        } else {
            PermissionHelper.requestStorage(this, requestPermissionLauncher, manageAllFilesLauncher);
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
        gameList = RaLaunchApplication.getGameDataManager().loadGameList();
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
        selectedGamePath = findViewById(R.id.selectedGamePath);
        launchGameButton = findViewById(R.id.launchGameButton);
        emptySelectionText = findViewById(R.id.emptySelectionText);
        modLoaderSwitchContainer = findViewById(R.id.modLoaderSwitchContainer);
        modLoaderSwitch = findViewById(R.id.modLoaderSwitch);
        runtimeSelectContainer = findViewById(R.id.runtimeSelectContainer);
        btnRuntimeSelector = findViewById(R.id.btnRuntimeSelector);
        tvCurrentRuntime = findViewById(R.id.tvCurrentRuntime);
        Button saveGamePathButton = findViewById(R.id.saveGamePathButton);

        // 初始化RecyclerView
        gameRecyclerView = findViewById(R.id.gameRecyclerView);
        gameRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        fileBrowserContainer = findViewById(R.id.fileBrowserContainer);
        pageManager = new PageManager(getSupportFragmentManager(), R.id.fragmentContainer);

        gameAdapter = new GameAdapter(gameList, this, this);
        gameRecyclerView.setAdapter(gameAdapter);

        // 设置按钮
        settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            YoYo.with(Techniques.BounceIn)
                    .duration(700)
                    .playOn(settingsButton);
            showSettingsFragment();
        });

        // 添加游戏按钮
        addGameButton = findViewById(R.id.addGameButton);
        addGameButton.setOnClickListener(v -> {
            YoYo.with(Techniques.BounceIn)
                    .duration(700)
                    .playOn(addGameButton);
            showAddGameFragment();
        });

        // 刷新按钮
        refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> {
            showToast("刷新游戏列表");
            refreshGameList();
        });

        // 视图切换按钮 (ralib 组件)
        viewSwitchButton = findViewById(R.id.viewSwitchButton);
        if (viewSwitchButton != null) {
            viewSwitchButton.setOnStateChangedListener(isSecondary -> {
                if (isSecondary) {
                    showFileBrowserMode();
                } else {
                    showGameListMode();
                }
            });
        }

        // 启动游戏按钮
        launchGameButton.setOnClickListener(v -> {
            YoYo.with(Techniques.Pulse)
                    .duration(200)
                    .playOn(v);
            
            if (isFileBrowserMode) {
                // 文件浏览器模式：启动选中的程序集
                launchSelectedAssembly();
            } else if (selectedGame != null) {
                // 游戏列表模式：启动选中的游戏
                launchGame(selectedGame);
            } else {
                showToast("请先选择一个游戏");
            }
        });

        // ModLoader 开关监听已移除 - 现在直接启动选中的程序集

        // 保存游戏路径按钮
        saveGamePathButton.setOnClickListener(v -> {
            if (selectedGame != null) {
                String newPath = selectedGamePath.getText().toString().trim();
                if (newPath.isEmpty()) {
                    showToast("程序集路径不能为空");
                    return;
                }
                
                // 验证文件是否存在
                File assemblyFile = new File(newPath);
                if (!assemblyFile.exists()) {
                    showToast("警告: 文件不存在，但已保存路径");
                }
                
                // 更新游戏路径
                selectedGame.setGamePath(newPath);
                // 保存到配置文件
                RaLaunchApplication.getGameDataManager().saveGameList(gameList);
                showToast("程序集路径已保存");

            } else {
                showToast("请先选择一个游戏");
            }
        });

        // 控制布局按钮
        ImageButton controlLayoutButton = findViewById(R.id.controlLayoutButton);
        controlLayoutButton.setOnClickListener(v -> {
            YoYo.with(Techniques.BounceIn)
                    .duration(700)
                    .playOn(controlLayoutButton);
            showControlLayoutFragment();
        });

        // 默认显示主界面
        showMainLayout();

        // 初始化运行时版本选择
        setupRuntimeSelector();
    }

    /**
     * 设置运行时版本选择器
     */
    private void setupRuntimeSelector() {
        if (btnRuntimeSelector == null || runtimeSelectContainer == null || tvCurrentRuntime == null) {
            AppLogger.warn("MainActivity", "Runtime selector widgets not initialized yet");
            return;
        }

        java.util.List<String> versions = RuntimeManager.listInstalledVersions(this);

        if (versions.isEmpty()) {
            runtimeSelectContainer.setVisibility(View.GONE);
            AppLogger.warn("MainActivity", "Runtime selector hidden - no versions found");
            return;
        }
        
        runtimeSelectContainer.setVisibility(View.VISIBLE);
        
        // 显示当前版本
        String selectedVersion = RuntimeManager.getSelectedVersion(this);
        if (selectedVersion != null) {
            tvCurrentRuntime.setText(".NET " + selectedVersion);
        }
        
        // 设置点击事件
        btnRuntimeSelector.setOnClickListener(v -> {
            // 添加点击动画
            com.daimajia.androidanimations.library.YoYo.with(com.daimajia.androidanimations.library.Techniques.Pulse)
                    .duration(300)
                    .playOn(v);
            
            // 显示运行时选择对话框
            showRuntimeSelectorDialog();
        });

    }
    
    /**
     * 显示运行时选择对话框 - 使用通用选择器
     */
    private void showRuntimeSelectorDialog() {
        // 获取可用的运行时版本
        java.util.List<String> versions = RuntimeManager.listInstalledVersions(this);
        String currentVersion = RuntimeManager.getSelectedVersion(this);
        
        // 构建选项列表
        java.util.List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new java.util.ArrayList<>();
        for (String version : versions) {
            String description = getVersionDescription(version);
            options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
                version,
                ".NET " + version,
                description
            ));
        }
        
        // 创建并配置对话框
        com.app.ralib.dialog.OptionSelectorDialog dialog = new com.app.ralib.dialog.OptionSelectorDialog()
            .setTitle(".NET 运行时版本")
            .setIcon(R.drawable.ic_settings)
            .setOptions(options)
            .setCurrentValue(currentVersion)
            .setShowCurrentValue(true)
            .setAutoCloseOnSelect(true)
            .setAutoCloseDelay(300)
            .setOnOptionSelectedListener(version -> {

                // 保存选择
                RuntimeManager.setSelectedVersion(this, version);
                
                // 更新显示的版本
                tvCurrentRuntime.setText(".NET " + version);

                // 验证保存的版本
                String savedVersion = RuntimeManager.getSelectedVersion(this);

                // 显示提示 - 使用 Snackbar
                showSuccessSnackbar("已切换到 .NET " + version);
                
                // 添加更新动画
                tvCurrentRuntime.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            tvCurrentRuntime.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(150)
                                    .start();
                        })
                        .start();
            });
        
        dialog.show(getSupportFragmentManager(), "RuntimeSelectorDialog");
    }
    
    /**
     * 获取运行时版本的描述
     */
    private String getVersionDescription(String version) {
        if (version.startsWith("10.")) {
            return "最新版本 - 推荐使用";
        } else if (version.startsWith("9.")) {
            return "稳定版本 - 推荐使用";
        } else if (version.startsWith("8.")) {
            return "长期支持版本 (LTS)";
        } else if (version.startsWith("7.")) {
            return "长期支持版本 (LTS)";
        }
        return "稳定版本";
    }

    private void showControlLayoutFragment() {
        mainLayout.setVisibility(View.GONE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);

        ControlLayoutFragment controlFragment = new ControlLayoutFragment();
        controlFragment.setOnControlLayoutBackListener(this::hideControlLayoutFragment);

        pageManager.showPage(controlFragment, "control_layout");
    }

    private void hideControlLayoutFragment() {
        AppLogger.info("MainActivity", "hideControlLayoutFragment called");

        // 获取Fragment容器
        View fragmentContainer = findViewById(R.id.fragmentContainer);

        // 先移除Fragment
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null) {
            AppLogger.info("MainActivity", "Removing fragment: " + fragment.getClass().getSimpleName());
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(fragment);
            transaction.commitNow(); // 使用 commitNow 立即执行
        }

        // 清除回退栈
        try {
            getSupportFragmentManager().popBackStack("control_layout", FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch (Exception e) {
            AppLogger.warn("MainActivity", "Failed to pop back stack: " + e.getMessage());
        }

        // 清除Fragment容器的背景并隐藏
        if (fragmentContainer != null) {
            fragmentContainer.setBackground(null);
            fragmentContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            fragmentContainer.setVisibility(View.GONE);
            AppLogger.info("MainActivity", "Fragment container hidden and cleared");
        }

        // 显示主布局
        mainLayout.setVisibility(View.VISIBLE);
        mainLayout.bringToFront(); // 确保主布局在最前面
        mainLayout.requestLayout();
        mainLayout.invalidate();

        // 强制刷新整个Activity视图
        View rootView = getWindow().getDecorView();
        rootView.requestLayout();
        rootView.postInvalidate();

        AppLogger.info("MainActivity", "Main layout visibility restored");
    }

    private void showMainLayout() {
        mainLayout.setVisibility(View.VISIBLE);
        showNoGameSelected();
    }

    /**
     * 显示成功提示 Snackbar
     */
    private void showSuccessSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        com.app.ralib.ui.SnackbarHelper.showSuccess(rootView, message);
    }

    /**
     * 显示错误提示 Snackbar
     */
    private void showErrorSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        com.app.ralib.ui.SnackbarHelper.showError(rootView, message);
    }

    /**
     * 显示信息提示 Snackbar
     */
    private void showInfoSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        com.app.ralib.ui.SnackbarHelper.showInfo(rootView, message);
    }

    private void showAddGameFragment() {
        // 直接显示本地导入对话框 - Material Design 3 风格
        LocalImportDialog localImportDialog = new LocalImportDialog();

        // 设置文件选择监听器
        localImportDialog.setOnFileSelectionListener(new LocalImportDialog.OnFileSelectionListener() {
            @Override
            public void onSelectGameFile(LocalImportDialog dialog) {
                // 打开文件浏览器选择游戏文件
                showFileBrowserForSelection("game", new String[]{".sh"}, filePath -> {
                    dialog.setGameFile(filePath);
                    // 恢复对话框显示
                    dialog.showDialog();
                });
            }

            @Override
            public void onSelectModLoaderFile(LocalImportDialog dialog) {
                // 打开文件浏览器选择ModLoader文件
                showFileBrowserForSelection("modloader", new String[]{".zip"}, filePath -> {
                    dialog.setModLoaderFile(filePath);
                    // 恢复对话框显示
                    dialog.showDialog();
                });
            }
        });

        // 设置导入开始监听器
        localImportDialog.setOnImportStartListener((gameFilePath, modLoaderFilePath, gameName, gameVersion) -> {
            // 开始导入流程
            startGameImport(gameFilePath, modLoaderFilePath, gameName, gameVersion);
        });

        localImportDialog.show(getSupportFragmentManager(), "local_import_dialog");
    }

    /**
     * 显示文件浏览器选择文件
     */
    private void showFileBrowserForSelection(String fileType, String[] extensions, FileSelectionCallback callback) {
        mainLayout.setVisibility(View.GONE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);


        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        fileBrowserFragment.setFileType(fileType, extensions);
        fileBrowserFragment.setOnFileSelectedListener((filePath, selectedFileType) -> {
            callback.onFileSelected(filePath);
            hideAddGameFragment();
        });
        fileBrowserFragment.setOnBackListener(this::hideAddGameFragment);

        pageManager.showPage(fileBrowserFragment, "file_browser");
    }

    /**
     * 开始游戏导入
     */
    private void startGameImport(String gameFilePath, String modLoaderFilePath, String gameName, String gameVersion) {
        mainLayout.setVisibility(View.GONE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);

        LocalImportFragment localImportFragment = new LocalImportFragment();
        localImportFragment.setOnImportCompleteListener(this);
        localImportFragment.setOnBackListener(this::onBackFromLocalImport);

        // 传递文件路径给Fragment
        Bundle args = new Bundle();
        args.putString("gameFilePath", gameFilePath);
        args.putString("modLoaderFilePath", modLoaderFilePath);
        args.putString("gameName", gameName);
        args.putString("gameVersion", gameVersion);
        localImportFragment.setArguments(args);

        pageManager.showPage(localImportFragment, "local_import");
    }

    /**
     * 文件选择回调接口
     */
    private interface FileSelectionCallback {
        void onFileSelected(String filePath);
    }

    // 实现 OnImportCompleteListener
    @Override
    public void onImportComplete(String gameType, GameItem newGame) {
        // 导入完成后直接添加游戏到列表
        addGameToList(gameType, newGame);
    }

    private void onBackFromLocalImport() {
        pageManager.goBack();
    }

    private void hideAddGameFragment() {
        mainLayout.setVisibility(View.VISIBLE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.GONE);

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(fragment);
            transaction.commit();
        }

        getSupportFragmentManager().popBackStack("add_game", FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void addGameToList(String gameType, GameItem newGame) {
        // 验证游戏文件是否存在
        File gameFile = new File(newGame.getGamePath());
        if (!gameFile.exists()) {
            showToast("警告: 游戏文件路径不存在: " + newGame.getGamePath());
        }
        
        // 检查自定义图标路径是否有效
        if (newGame.getIconPath() == null || !new File(newGame.getIconPath()).exists()) {
            newGame.setIconPath(null);
        }
        
        // 如果没有图标路径且没有设置图标资源ID，使用默认图标
        if ((newGame.getIconPath() == null || newGame.getIconPath().isEmpty()) && newGame.getIconResId() == 0) {
            newGame.setIconResId(R.drawable.ic_game_default);
        }

        gameList.add(0, newGame);
        gameAdapter.updateGameList(gameList);
        RaLaunchApplication.getGameDataManager().addGame(newGame);

        showToast("游戏添加成功！");
        hideAddGameFragment();
    }

    /**
     * 显示文件浏览器模式 - 在游戏列表区域显示文件浏览器
     */
    private void showFileBrowserMode() {
        isFileBrowserMode = true;
        
        YoYo.with(Techniques.FadeOut)
                .duration(200)
                .onEnd(animator -> {
                    gameRecyclerView.setVisibility(View.GONE);
                    fileBrowserContainer.setVisibility(View.VISIBLE);
                    
                    // 在游戏列表区域嵌入文件浏览器
                    showGameFileBrowserInContainer();
                    
                    // 右侧显示提示
                    selectedGameInfo.setVisibility(View.GONE);
                    emptySelectionText.setVisibility(View.VISIBLE);
                    launchGameButton.setVisibility(View.GONE);
                    
                    YoYo.with(Techniques.FadeIn)
                            .duration(200)
                            .playOn(fileBrowserContainer);
                })
                .playOn(gameRecyclerView);
        
        showInfoSnackbar("文件浏览器模式 - 选择 DLL/EXE 文件启动");
    }
    
    /**
     * 显示游戏列表模式
     */
    private void showGameListMode() {
        isFileBrowserMode = false;
        
        YoYo.with(Techniques.FadeOut)
                .duration(200)
                .onEnd(animator -> {
                    fileBrowserContainer.setVisibility(View.GONE);
                    ((ViewGroup) fileBrowserContainer).removeAllViews();
                    
                    gameRecyclerView.setVisibility(View.VISIBLE);
                    showNoGameSelected();
                    
                    YoYo.with(Techniques.FadeIn)
                            .duration(200)
                            .playOn(gameRecyclerView);
                })
                .playOn(fileBrowserContainer);
        
        showInfoSnackbar("游戏列表模式");
    }
    
    /**
     * 在容器中显示游戏文件浏览器
     */
    private void showGameFileBrowserInContainer() {
        // 移除旧的视图
        if (fileBrowserContainer != null) {
            ((ViewGroup) fileBrowserContainer).removeAllViews();
        }
        
        // 创建新的文件浏览器
        gameFileBrowser = new com.app.ralib.ui.GameFileBrowser(this);
        
        // 设置起始目录为游戏目录
        String gamesDir = getExternalFilesDir(null).getAbsolutePath() + "/games";
        gameFileBrowser.setDirectory(gamesDir);
        
        // 设置文件选择监听器
        gameFileBrowser.setOnFileSelectedListener(this::onGameFileSelected);
        
        // 添加到容器 (fileBrowserContainer 是 FrameLayout)
        ((ViewGroup) fileBrowserContainer).addView(gameFileBrowser,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }
    
    /**
     * 游戏文件浏览器选择了文件
     */
    private void onGameFileSelected(File file) {
        selectedAssemblyFile = file;
        
        // 显示右侧启动面板
        showFileBrowserSelection(file);
    }
    
    /**
     * 显示文件浏览器选择的文件信息
     */
    private void showFileBrowserSelection(File file) {
        selectedGameInfo.setVisibility(View.VISIBLE);
        emptySelectionText.setVisibility(View.GONE);
        launchGameButton.setVisibility(View.VISIBLE);
        
        // 设置文件名
        selectedGameName.setText(file.getName());
        
        // 设置文件路径
        selectedGameDescription.setText(file.getParent());
        selectedGamePath.setText(file.getAbsolutePath());
        
        // 设置默认图标
        selectedGameImage.setImageResource(R.drawable.ic_game_default);
        
        // 尝试提取图标
        new Thread(() -> {
            String iconPath = com.app.ralaunch.utils.IconExtractorHelper
                    .extractGameIcon(this, file.getAbsolutePath());
            if (iconPath != null && !iconPath.isEmpty()) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(iconPath);
                if (bitmap != null) {
                    runOnUiThread(() -> selectedGameImage.setImageBitmap(bitmap));
                }
            }
        }).start();
        
        // 添加动画
        YoYo.with(Techniques.SlideInRight)
                .duration(300)
                .playOn(selectedGameInfo);
    }
    
    /**
     * 从文件浏览器启动选中的文件
     */
    private void launchSelectedAssembly() {
        if (selectedAssemblyFile == null) {
            showToast("请先选择一个 DLL 或 EXE 文件");
            return;
        }
        
        // 启动游戏
        Intent intent = new Intent(MainActivity.this, GameActivity.class);
        intent.putExtra("ASSEMBLY_PATH", selectedAssemblyFile.getAbsolutePath());
        intent.putExtra("GAME_NAME", selectedAssemblyFile.getName());
        
        startActivity(intent);
    }
    
    private void refreshGameList() {
        YoYo.with(Techniques.Flash)
                .duration(600)
                .playOn(gameRecyclerView);
        
        // 重新加载游戏列表
        gameList = RaLaunchApplication.getGameDataManager().loadGameList();
        gameAdapter.updateGameList(gameList);
        
        showInfoSnackbar("已刷新游戏列表");
    }

    @Override
    public void onGameClick(GameItem game) {
        showSelectedGame(game);
    }

    @Override
    public void onGameDelete(GameItem game, int position) {
        if (selectedGame == game) {
            showNoGameSelected();
        }

        // 使用通用对话框确认删除
        com.app.ralib.dialog.OptionSelectorDialog dialog = 
            new com.app.ralib.dialog.OptionSelectorDialog();
        
        // 创建选项列表
        java.util.List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new java.util.ArrayList<>();
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "confirm", 
            "删除", 
            "确定要删除 " + game.getGameName() + " 吗？\n\n注意：这将同时删除游戏文件"
        ));
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "cancel", 
            "取消", 
            ""
        ));
        
        dialog.setTitle("删除游戏")
              .setIcon(R.drawable.ic_close)
              .setOptions(options)
              .setCurrentValue("cancel")
              .setShowCurrentValue(false)
              .setAutoCloseOnSelect(false); // 手动控制关闭
        
        dialog.setOnOptionSelectedListener(optionValue -> {
            if ("confirm".equals(optionValue)) {
                // 删除游戏文件夹
                boolean filesDeleted = deleteGameFiles(game);

                // 从列表中删除
                gameAdapter.removeGame(position);
                RaLaunchApplication.getGameDataManager().removeGame(position);

                if (filesDeleted) {
                    showSuccessSnackbar("游戏及文件已删除");
                } else {
                    showInfoSnackbar("游戏已从列表删除，但部分文件可能未删除");
                }
                dialog.dismiss();
            } else {
                // 取消
                dialog.dismiss();
            }
        });
        
        dialog.show(getSupportFragmentManager(), "DeleteGameDialog");
    }

    /**
     * 删除游戏文件夹
     * @param game 要删除的游戏项
     * @return 是否成功删除
     */
    private boolean deleteGameFiles(GameItem game) {
        try {
            // 优先使用 gameBasePath（游戏根目录）
            String gameBasePath = game.getGameBasePath();
            File gameDir = null;
            
            if (gameBasePath != null && !gameBasePath.isEmpty()) {
                gameDir = new File(gameBasePath);
                AppLogger.info("MainActivity", "使用游戏根目录: " + gameBasePath);
            } else {
                // 如果没有 gameBasePath，尝试从 gamePath 推断
                String gamePath = game.getGamePath();
                if (gamePath == null || gamePath.isEmpty()) {
                    AppLogger.warn("MainActivity", "游戏路径为空，无法删除文件");
                    return false;
                }
                
                File gameFile = new File(gamePath);
                // 尝试找到 /games/ 目录下的第一级子目录作为游戏根目录
                File parent = gameFile.getParentFile();
                while (parent != null && !parent.getName().equals("games")) {
                    gameDir = parent;
                    parent = parent.getParentFile();
                }

                if (gameDir == null) {
                    gameDir = gameFile.getParentFile();
                }

                AppLogger.info("MainActivity", "从游戏路径推断根目录: " + (gameDir != null ? gameDir.getAbsolutePath() : "null"));
            }

            if (gameDir == null || !gameDir.exists()) {
                AppLogger.warn("MainActivity", "游戏目录不存在: " + (gameDir != null ? gameDir.getAbsolutePath() : "null"));
                return false;
            }

            // 确认这是一个游戏目录（在 files/games/ 下）
            String dirPath = gameDir.getAbsolutePath();
            if (!dirPath.contains("/files/games/") && !dirPath.contains("/files/imported_games/")) {
                AppLogger.warn("MainActivity", "路径不在游戏目录中，跳过删除: " + dirPath);
                return false;
            }

            AppLogger.info("MainActivity", "准备删除游戏目录: " + gameDir.getAbsolutePath());
            
            // 递归删除目录
            boolean success = deleteDirectory(gameDir);

            if (success) {
                AppLogger.info("MainActivity", "游戏目录删除成功: " + gameDir.getName());
            } else {
                AppLogger.warn("MainActivity", "删除游戏目录失败: " + gameDir.getName());
            }

            return success;

        } catch (Exception e) {
            AppLogger.error("MainActivity", "删除游戏文件时发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 递归删除目录及其内容
     * @param dir 要删除的目录
     * @return 是否成功删除
     */
    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return false;
        }

        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    boolean success = deleteDirectory(child);
                    if (!success) {
                        AppLogger.warn("MainActivity", "无法删除: " + child.getAbsolutePath());
                    }
                }
            }
        }

        // 删除文件或空目录
        boolean deleted = dir.delete();
        if (deleted) {

        }
        return deleted;
    }

    private void showSelectedGame(GameItem game) {
        selectedGame = game;

        selectedGameInfo.setVisibility(View.VISIBLE);
        launchGameButton.setVisibility(View.VISIBLE);
        emptySelectionText.setVisibility(View.GONE);

        selectedGameName.setText(game.getGameName());
        selectedGameDescription.setText(game.getGameDescription());
        selectedGamePath.setText(game.getGamePath());

        // 加载游戏图标 - 优先使用自定义图标路径，否则使用资源ID
        if (game.getIconPath() != null && !game.getIconPath().isEmpty()) {
            // 从文件加载图标
            File iconFile = new File(game.getIconPath());
            if (iconFile.exists()) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(game.getIconPath());
                if (bitmap != null) {
                    selectedGameImage.setImageBitmap(bitmap);
                } else {
                    // 如果加载失败，使用资源ID或默认图标
                    if (game.getIconResId() != 0) {
                        selectedGameImage.setImageResource(game.getIconResId());
                    } else {
                        selectedGameImage.setImageResource(R.drawable.ic_game_default);
                    }
                }
            } else {
                // 文件不存在，使用资源ID或默认图标
                if (game.getIconResId() != 0) {
                    selectedGameImage.setImageResource(game.getIconResId());
                } else {
                    selectedGameImage.setImageResource(R.drawable.ic_game_default);
                }
            }
        } else if (game.getIconResId() != 0) {
            selectedGameImage.setImageResource(game.getIconResId());
        } else {
            // 没有任何图标信息，使用默认图标
            selectedGameImage.setImageResource(R.drawable.ic_game_default);
        }

        // ModLoader 开关已移除 - 直接启动选中的程序集
        modLoaderSwitchContainer.setVisibility(View.GONE);

        YoYo.with(Techniques.Tada)
                .duration(800)
                .playOn(selectedGameImage);
    }

    private void showNoGameSelected() {
        selectedGame = null;

        selectedGameInfo.setVisibility(View.GONE);
        launchGameButton.setVisibility(View.GONE);
        emptySelectionText.setVisibility(View.VISIBLE);
    }

    private void launchGame(GameItem game) {
        YoYo.with(Techniques.Tada)
                .duration(800)
                .playOn(launchGameButton);

        // 验证程序集文件是否存在
        String assemblyPath = game.getGamePath();
        File assemblyFile = new File(assemblyPath);

        if (!assemblyFile.exists() || !assemblyFile.isFile()) {
            showErrorSnackbar("程序集文件不存在: " + assemblyPath);
            AppLogger.error("MainActivity", "Assembly file not found: " + assemblyPath);
            return;
        }

        showToast("启动游戏: " + game.getGameName());
        AppLogger.info("MainActivity", "Launching game: " + game.getGameName());
        AppLogger.info("MainActivity", "Assembly path: " + assemblyPath);

        // 直接传递程序集路径给 GameActivity
        Intent intent = new Intent(MainActivity.this, GameActivity.class);
        intent.putExtra("GAME_NAME", game.getGameName());
        intent.putExtra("ASSEMBLY_PATH", assemblyPath);  // 使用新的参数名
        
        // 如果有 Bootstrapper 配置，传递相关参数
        if (game.isBootstrapperPresent() && game.getBootstrapperBasePath() != null) {
            intent.putExtra("USE_BOOTSTRAPPER", true);
            intent.putExtra("GAME_BASE_PATH", game.getGameBasePath());
            intent.putExtra("BOOTSTRAPPER_BASE_PATH", game.getBootstrapperBasePath());
        }

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public void showToast(String message) { 
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void showSettingsFragment() {
        SettingsDialogFragment dialog = SettingsDialogFragment.newInstance();
        dialog.show(getSupportFragmentManager(), "settings_dialog");
    }

    @Override
    public void onSettingsBack() {
        hideSettingsFragment();
    }

    private void hideSettingsFragment() {
        mainLayout.setVisibility(View.VISIBLE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.GONE);

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(fragment);
            transaction.commit();
        }

        getSupportFragmentManager().popBackStack("settings", FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onBackPressed() {
        if (mainLayout.getVisibility() != View.VISIBLE) {
            // 检查当前Fragment是否是ControlLayoutFragment
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (currentFragment instanceof ControlLayoutFragment) {
                hideControlLayoutFragment();
            } else if (pageManager.getBackStackCount() > 1) {
                pageManager.goBack();
            } else {
                hideAddGameFragment();
            }
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // 检查是否是 UI 模式改变（深色/浅色模式）
        int currentNightMode = newConfig.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

        AppLogger.debug("MainActivity", "配置改变: nightMode=" + currentNightMode);

        // 检查设置
        com.app.ralaunch.data.SettingsManager settingsManager =
            com.app.ralaunch.data.SettingsManager.getInstance(this);

        // 如果设置为"跟随系统"，立即重建Activity以应用主题
        if (settingsManager.getThemeMode() == 0) {
            AppLogger.debug("MainActivity", "跟随系统模式，重建Activity");
            
            // 先关闭所有对话框，防止recreate后被恢复
            androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
            for (androidx.fragment.app.Fragment fragment : fm.getFragments()) {
                if (fragment instanceof androidx.fragment.app.DialogFragment) {
                    ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
                }
            }
            
            // 延迟一点点，确保对话框关闭
            new android.os.Handler().postDelayed(() -> {
                // 重建Activity以应用新主题
                recreate();
            }, 50);
        }
    }

    /**
     * 从设置中应用主题
     */
    private void applyThemeFromSettings() {
        com.app.ralaunch.data.SettingsManager settingsManager = 
            com.app.ralaunch.data.SettingsManager.getInstance(this);
        int themeMode = settingsManager.getThemeMode(); // 0=跟随系统, 1=深色, 2=浅色（默认浅色）
        
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 更新ErrorHandler的Activity引用
        ErrorHandler.setCurrentActivity(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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