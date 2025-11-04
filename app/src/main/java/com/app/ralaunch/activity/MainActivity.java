package com.app.ralaunch.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.fragment.AddGameOptionsFragment;
import com.app.ralaunch.fragment.ControlLayoutFragment;
import com.app.ralaunch.fragment.FileBrowserFragment;
import com.app.ralaunch.fragment.InitializationFragment;
import com.app.ralaunch.fragment.LocalImportFragment;
import com.app.ralaunch.adapter.GameAdapter;
import com.app.ralaunch.adapter.GameItem;
import com.app.ralaunch.fragment.SettingsFragment;
import com.app.ralaunch.utils.PageManager;
import com.daimajia.androidanimations.library.Techniques;
import com.app.ralaunch.utils.PermissionHelper;
import com.app.ralaunch.utils.UiUtils;
import com.daimajia.androidanimations.library.YoYo;
import com.app.ralaunch.utils.RuntimeManager;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        GameAdapter.OnGameClickListener,
        GameAdapter.OnGameDeleteListener,
        AddGameOptionsFragment.OnGameSourceSelectedListener,
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
    private Button launchGameButton;
    private CardView emptySelectionText;
    private LinearLayout mainLayout;
    private LinearLayout modLoaderSwitchContainer;
    private androidx.appcompat.widget.SwitchCompat modLoaderSwitch;
    private View runtimeSelectContainer;
    private android.widget.Spinner spinnerRuntimeVersion;
    private ImageButton settingsButton;
    private ImageButton addGameButton;
    private ImageButton refreshButton;
    private GameItem selectedGame;

    // 权限回调接口
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = this;

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
        SettingsFragment.applySavedSettings(this);
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
        setupRuntimeSpinner();

        SettingsFragment.applySavedSettings(this);
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
        spinnerRuntimeVersion = findViewById(R.id.spinnerRuntimeVersion);
        Button saveGamePathButton = findViewById(R.id.saveGamePathButton);

        // 初始化RecyclerView
        gameRecyclerView = findViewById(R.id.gameRecyclerView);
        gameRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
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

        // 启动游戏按钮
        launchGameButton.setOnClickListener(v -> {
            if (selectedGame != null) {
                launchGame(selectedGame);
            } else {
                showToast("请先选择一个游戏");
            }
        });

        // ModLoader 开关监听
        modLoaderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (selectedGame != null) {
                selectedGame.setModLoaderEnabled(isChecked);
                // 保存状态到配置文件
                RaLaunchApplication.getGameDataManager().saveGameList(gameList);
                showToast(isChecked ? "已启用 ModLoader" : "已禁用 ModLoader");
            }
        });

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
                
                Log.d("MainActivity", "Updated game path: " + newPath);
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
        setupRuntimeSpinner();
    }

    /**
     * 设置运行时版本选择器
     */
    private void setupRuntimeSpinner() {
        // 检查控件是否已初始化
        if (spinnerRuntimeVersion == null || runtimeSelectContainer == null) {
            Log.w("MainActivity", "Runtime spinner widgets not initialized yet");
            return;
        }
        
        // 输出运行时目录信息
        File dotnetRoot = RuntimeManager.getDotnetRoot(this);
        File sharedRoot = RuntimeManager.getSharedRoot(this);
        Log.d("MainActivity", "Dotnet root: " + dotnetRoot.getAbsolutePath() + " (exists: " + dotnetRoot.exists() + ")");
        Log.d("MainActivity", "Shared root: " + sharedRoot.getAbsolutePath() + " (exists: " + sharedRoot.exists() + ")");
        
        if (sharedRoot.exists()) {
            File[] files = sharedRoot.listFiles();
            if (files != null) {
                Log.d("MainActivity", "Shared root contains " + files.length + " items:");
                for (File f : files) {
                    Log.d("MainActivity", "  - " + f.getName() + " (isDirectory: " + f.isDirectory() + ")");
                }
            } else {
                Log.d("MainActivity", "Shared root listFiles returned null");
            }
        }
        
        java.util.List<String> versions = RuntimeManager.listInstalledVersions(this);
        
        Log.d("MainActivity", "setupRuntimeSpinner called, found " + versions.size() + " versions");
        
        if (versions.isEmpty()) {
            runtimeSelectContainer.setVisibility(View.GONE);
            Log.w("MainActivity", "Runtime selector hidden - no versions found");
            // 显示提示信息
            showToast("未检测到 .NET 运行时，请先完成初始化");
            return;
        }
        
        runtimeSelectContainer.setVisibility(View.VISIBLE);
        Log.i("MainActivity", "Runtime selector visible - " + versions.size() + " versions found");
        showToast("已检测到 " + versions.size() + " 个 .NET 运行时版本");
        
        // 创建显示名称列表（.NET 7.0.0, .NET 8.0.1 等）
        java.util.List<String> displayNames = new java.util.ArrayList<>();
        for (String version : versions) {
            displayNames.add(".NET " + version);
        }
        
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRuntimeVersion.setAdapter(adapter);
        
        // 选中当前版本
        String selectedVersion = RuntimeManager.getSelectedVersion(this);
        if (selectedVersion != null) {
            int idx = versions.indexOf(selectedVersion);
            if (idx >= 0) {
                spinnerRuntimeVersion.setSelection(idx);
                Log.d("MainActivity", "Selected runtime version: " + selectedVersion);
            }
        }
        
        // 设置选择监听器
        spinnerRuntimeVersion.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override 
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < versions.size()) {
                    String version = versions.get(position);
                    RuntimeManager.setSelectedVersion(MainActivity.this, version);
                    Toast.makeText(MainActivity.this, 
                                 "已切换到 .NET " + version, 
                                 Toast.LENGTH_SHORT).show();
                    Log.d("MainActivity", "Runtime version changed to: " + version);
                }
            }
            
            @Override 
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // 不需要处理
            }
        });
        
        Log.d("MainActivity", "Runtime spinner setup complete with " + versions.size() + " versions");
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
        mainLayout.setVisibility(View.VISIBLE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.GONE);

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(fragment);
            transaction.commit();
        }
        getSupportFragmentManager().popBackStack("control_layout", FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void showMainLayout() {
        mainLayout.setVisibility(View.VISIBLE);
        showNoGameSelected();
    }

    private void showAddGameFragment() {
        mainLayout.setVisibility(View.GONE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);

        // 显示添加游戏选项页面
        AddGameOptionsFragment addGameOptionsFragment = new AddGameOptionsFragment();
        addGameOptionsFragment.setOnGameSourceSelectedListener(this);
        addGameOptionsFragment.setOnBackListener(this::hideAddGameFragment);

        pageManager.showPage(addGameOptionsFragment, "add_game_options");
    }

    @Override
    public void onGameSourceSelected(String sourceType) {
        if ("local".equals(sourceType)) {
            // 跳转到本地导入页面
            LocalImportFragment localImportFragment = new LocalImportFragment();
            localImportFragment.setOnImportCompleteListener(this);
            localImportFragment.setOnBackListener(this::onBackFromLocalImport);

            pageManager.showPage(localImportFragment, "local_import");
        } else if ("download".equals(sourceType)) {
            // TODO: 跳转到在线下载页面
            // 这里可以创建一个专门的下载页面
            showToast("在线下载功能即将开放");
            // 暂时返回主界面
            hideAddGameFragment();
        }
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
        int iconResId = R.drawable.ic_game_default;

        switch (gameType) {
            case "modloader":
                // 使用动态图标，不再使用硬编码资源
                iconResId = R.drawable.ic_game_default;
                break;
            case "stardew":
                iconResId = R.drawable.ic_stardew_valley;
                break;
        }

        newGame.setIconResId(iconResId);

        File gameFile = new File(newGame.getGamePath());
        if (!gameFile.exists()) {
            showToast("警告: 游戏文件路径不存在: " + newGame.getGamePath());
        }
        
        // 检查自定义图标路径
        if (newGame.getIconPath() == null || !new File(newGame.getIconPath()).exists()) {
            newGame.setIconPath(null);
            Log.d("MainActivity", "invalid icon path: " + newGame.getIconPath());
        }

        gameList.add(0, newGame);
        gameAdapter.updateGameList(gameList);
        RaLaunchApplication.getGameDataManager().addGame(newGame);

        showToast("游戏添加成功！");
        hideAddGameFragment();
    }

    private void refreshGameList() {
        YoYo.with(Techniques.Flash)
                .duration(600)
                .playOn(gameRecyclerView);
        gameList = RaLaunchApplication.getGameDataManager().loadGameList();
        gameAdapter.updateGameList(gameList);
        showToast("游戏列表已刷新");
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

        new AlertDialog.Builder(this)
                .setTitle("删除游戏")
                .setMessage("确定要删除 " + game.getGameName() + " 吗？\n\n注意：这将同时删除游戏文件")
                .setPositiveButton("删除", (dialog, which) -> {
                    // 删除游戏文件夹
                    boolean filesDeleted = deleteGameFiles(game);

                    // 从列表中删除
                    gameAdapter.removeGame(position);
                    RaLaunchApplication.getGameDataManager().removeGame(position);

                    if (filesDeleted) {
                        showToast("游戏及文件已删除");
                    } else {
                        showToast("游戏已从列表删除，但部分文件可能未删除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 删除游戏文件夹
     * @param game 要删除的游戏项
     * @return 是否成功删除
     */
    private boolean deleteGameFiles(GameItem game) {
        try {
            // 从游戏路径获取游戏根目录
            String gamePath = game.getGamePath();
            if (gamePath == null || gamePath.isEmpty()) {
                Log.w("MainActivity", "游戏路径为空，无法删除文件");
                return false;
            }

            // 获取游戏根目录 (假设路径类似: /data/.../files/games/GameName/game.dll)
            File gameFile = new File(gamePath);
            File gameDir = gameFile.getParentFile(); // 获取游戏目录

            if (gameDir == null || !gameDir.exists()) {
                Log.w("MainActivity", "游戏目录不存在: " + (gameDir != null ? gameDir.getAbsolutePath() : "null"));
                return false;
            }

            // 确认这是一个游戏目录（在 files/games/ 下）
            String dirPath = gameDir.getAbsolutePath();
            if (!dirPath.contains("/files/games/") && !dirPath.contains("/files/imported_games/")) {
                Log.w("MainActivity", "路径不在游戏目录中，跳过删除: " + dirPath);
                return false;
            }

            Log.d("MainActivity", "删除游戏目录: " + gameDir.getAbsolutePath());

            // 递归删除目录
            boolean success = deleteDirectory(gameDir);

            if (success) {
                Log.i("MainActivity", "成功删除游戏目录: " + gameDir.getName());
            } else {
                Log.w("MainActivity", "删除游戏目录失败: " + gameDir.getName());
            }

            return success;

        } catch (Exception e) {
            Log.e("MainActivity", "删除游戏文件时发生错误: " + e.getMessage());
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
                        Log.w("MainActivity", "无法删除: " + child.getAbsolutePath());
                    }
                }
            }
        }

        // 删除文件或空目录
        boolean deleted = dir.delete();
        if (deleted) {
            Log.d("MainActivity", "已删除: " + dir.getName());
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

        // 检查是否是 modloader 类型游戏，如果是则显示开关
        if (game.getGameBodyPath() != null && !game.getGameBodyPath().isEmpty()) {
            // 有 gameBodyPath 说明是 modloader 游戏
            modLoaderSwitchContainer.setVisibility(View.VISIBLE);
            modLoaderSwitch.setChecked(game.isModLoaderEnabled());
        } else {
            modLoaderSwitchContainer.setVisibility(View.GONE);
        }

        

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

        showToast("启动游戏: " + game.getGameName());

        Intent intent = new Intent(MainActivity.this, GameActivity.class);
        intent.putExtra("GAME_NAME", game.getGameName());
        intent.putExtra("GAME_PATH", game.getGamePath());
        intent.putExtra("GAME_BODY_PATH", game.getGameBodyPath()); // 添加游戏本体路径
        intent.putExtra("MOD_LOADER_ENABLED", game.isModLoaderEnabled()); // 添加 ModLoader 开关状态
        // 不再传递引擎类型，避免误导性的日志或行为

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public void showToast(String message) { UiUtils.toast(this, message); }

    private void showSettingsFragment() {
        mainLayout.setVisibility(View.GONE);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);

        SettingsFragment settingsFragment = new SettingsFragment();
        settingsFragment.setOnSettingsBackListener(this);

        pageManager.showPage(settingsFragment, "settings");
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
            if (pageManager.getBackStackCount() > 1) {
                pageManager.goBack();
            } else {
                hideAddGameFragment();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在应用退出时保存游戏列表
        // gameDataManager.updateGameList(gameList);
    }
}