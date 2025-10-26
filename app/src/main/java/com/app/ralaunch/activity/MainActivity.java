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
import com.app.ralaunch.fragment.ControlLayoutFragment;
import com.app.ralaunch.fragment.DownloadOptionsFragment;
import com.app.ralaunch.fragment.FileBrowserFragment;
import com.app.ralaunch.fragment.InitializationFragment;
import com.app.ralaunch.fragment.LocalImportFragment;
import com.app.ralaunch.fragment.SelectGameFragment;
import com.app.ralaunch.adapter.GameAdapter;
import com.app.ralaunch.adapter.GameItem;
import com.app.ralaunch.fragment.SettingsFragment;
import com.app.ralaunch.utils.GameDataManager;
import com.app.ralaunch.utils.PageManager;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        GameAdapter.OnGameClickListener,
        GameAdapter.OnGameDeleteListener,
        SelectGameFragment.OnGameSelectedListener,
        DownloadOptionsFragment.OnDownloadOptionSelectedListener,
        SettingsFragment.OnSettingsBackListener,
        FileBrowserFragment.OnPermissionRequestListener,
        LocalImportFragment.OnImportCompleteListener {

    private PageManager pageManager;
    public static MainActivity mainActivity;
    private RecyclerView gameRecyclerView;
    private GameAdapter gameAdapter;
    private List<GameItem> gameList;
    private GameDataManager gameDataManager;

    // 权限请求
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> manageAllFilesLauncher;
    private PermissionCallback currentPermissionCallback;

    // 界面控件
    private View selectedGameInfo;
    private ImageView selectedGameImage;
    private TextView selectedGameName;
    private TextView selectedGameDescription;
    private TextView selectedGamePath;
    private Button launchGameButton;
    private CardView emptySelectionText;
    private LinearLayout mainLayout;
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

        // 初始化权限请求
        initializePermissionLaunchers();

        // 初始化游戏数据管理器
        gameDataManager = new GameDataManager(this);

        initializeGameData();
        setupUI();

        // 检查初始化状态
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean legalAgreed = prefs.getBoolean("legal_agreed", false);
        boolean componentsExtracted = prefs.getBoolean("components_extracted", false);

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

        SettingsFragment.applySavedSettings(this);
    }

    /**
     * 初始化权限请求
     */
    private void initializePermissionLaunchers() {
        // 初始化存储权限请求
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean allGranted = true;
                    for (Boolean granted : permissions.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        // 权限已授予
                        if (currentPermissionCallback != null) {
                            currentPermissionCallback.onPermissionsGranted();
                        }
                    } else {
                        // 权限被拒绝
                        if (currentPermissionCallback != null) {
                            currentPermissionCallback.onPermissionsDenied();
                        }
                    }
                    currentPermissionCallback = null;
                }
        );

        // 初始化所有文件访问权限请求
        manageAllFilesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (Environment.isExternalStorageManager()) {
                            // 所有文件访问权限已授予
                            if (currentPermissionCallback != null) {
                                currentPermissionCallback.onPermissionsGranted();
                            }
                        } else {
                            // 所有文件访问权限被拒绝
                            if (currentPermissionCallback != null) {
                                currentPermissionCallback.onPermissionsDenied();
                            }
                        }
                        currentPermissionCallback = null;
                    }
                }
        );
    }

    /**
     * 检查是否具有必要的权限
     */
    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要所有文件访问权限
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 及以下需要读写权限
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 请求必要的权限
     */
    public void requestRequiredPermissions(PermissionCallback callback) {
        this.currentPermissionCallback = callback;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 请求所有文件访问权限
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageAllFilesLauncher.launch(intent);
            } catch (Exception e) {
                // 如果上面的Intent失败，使用备用方案
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                manageAllFilesLauncher.launch(intent);
            }
        } else {
            // Android 10 及以下请求读写权限
            requestPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
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
        gameList = gameDataManager.loadGameList();
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

        SelectGameFragment selectGameFragment = new SelectGameFragment();
        selectGameFragment.setOnGameSelectedListener(this);
        selectGameFragment.setOnBackToMainListener(this::hideAddGameFragment);

        pageManager.showPage(selectGameFragment, "select_game");
    }

    @Override
    public void onGameSelected(String gameType, String gameName, String engineType) {
        DownloadOptionsFragment downloadFragment = new DownloadOptionsFragment();
        downloadFragment.setGameInfo(gameType, gameName, engineType);
        downloadFragment.setOnDownloadOptionSelectedListener(this);
        downloadFragment.setOnBackListener(this::onBackFromDownload);

        pageManager.showPage(downloadFragment, "download_options");
    }

    @Override
    public void onDownloadOptionSelected(String gameType, String gameName, String engineType, String optionType) {
        if ("local".equals(optionType)) {
            // 直接跳转到本地导入页面
            LocalImportFragment localImportFragment = new LocalImportFragment();
            localImportFragment.setGameInfo(gameType, gameName, engineType);
            localImportFragment.setOnImportCompleteListener(this);
            localImportFragment.setOnBackListener(this::onBackFromLocalImport);

            pageManager.showPage(localImportFragment, "local_import");
        } else if ("download".equals(optionType)) {
            // 下载完成后直接视为安装完成
            // 这里可以添加下载完成后的处理逻辑
            showToast("下载完成，游戏已准备就绪");
            // 可以在这里添加游戏到列表
        }
    }

    // 实现 OnImportCompleteListener
    @Override
    public void onImportComplete(String gameType, String gameName, String gamePath, String engineType) {
        // 导入完成后直接添加游戏到列表
        addGameToList(gameType, gameName, gamePath, engineType);
    }

    private void onBackFromDownload() {
        pageManager.goBack();
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

    private void addGameToList(String gameType, String gameName, String gamePath, String engineType) {
        int iconResId = R.drawable.ic_game_default;

        switch (gameType) {
            case "tmodloader":
                iconResId = R.drawable.ic_tmodloader;
                break;
            case "stardew":
                iconResId = R.drawable.ic_stardew_valley;
                break;
        }

        File gameFile = new File(gamePath);
        if (!gameFile.exists()) {
            showToast("警告: 游戏文件路径不存在: " + gamePath);
        }

        GameItem newGame = new GameItem(gameName, gamePath, iconResId);
        newGame.setEngineType(engineType);

        gameList.add(0, newGame);
        gameAdapter.updateGameList(gameList);
        gameDataManager.addGame(newGame);

        showToast("游戏添加成功！");
        hideAddGameFragment();
    }

    private void refreshGameList() {
        YoYo.with(Techniques.Flash)
                .duration(600)
                .playOn(gameRecyclerView);

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
                .setMessage("确定要删除 " + game.getGameName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    gameAdapter.removeGame(position);
                    gameDataManager.removeGame(position);
                    showToast("游戏已删除");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSelectedGame(GameItem game) {
        selectedGame = game;

        selectedGameInfo.setVisibility(View.VISIBLE);
        launchGameButton.setVisibility(View.VISIBLE);
        emptySelectionText.setVisibility(View.GONE);

        selectedGameName.setText(game.getGameName());
        selectedGameDescription.setText(game.getGameDescription());
        selectedGamePath.setText(game.getGamePath());

        TextView selectedGameEngine = findViewById(R.id.selectedGameEngine);
        if (game.getEngineType() != null) {
            selectedGameEngine.setText(game.getEngineType() + " 引擎");
            selectedGameEngine.setVisibility(View.VISIBLE);
        } else {
            selectedGameEngine.setVisibility(View.GONE);
        }

        if (game.getIconResId() != 0) {
            selectedGameImage.setImageResource(game.getIconResId());
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
        intent.putExtra("ENGINE_TYPE", game.getEngineType());

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

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