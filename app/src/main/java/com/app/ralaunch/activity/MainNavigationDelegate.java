package com.app.ralaunch.activity;

import android.view.View;
import androidx.fragment.app.FragmentManager;
import com.app.ralaunch.R;
import com.app.ralaunch.fragment.ControlLayoutFragment;
import com.app.ralaunch.fragment.FileBrowserFragment;
import com.app.ralaunch.fragment.ModManagerFragment;
import com.app.ralaunch.fragment.SettingsFragment;
import com.app.ralaunch.fragment.LocalImportFragment;
import com.app.ralaunch.fragment.GameImportFragment;
import com.google.android.material.navigationrail.NavigationRailView;

/**
 * 负责 MainActivity 的页面导航和切换逻辑。
 * 包括所有页面的显示/隐藏、Fragment 初始化、NavigationRail 监听等。
 */
public class MainNavigationDelegate {

    private static final String TAG = "MainNavigationDelegate";

    private final MainActivity activity;
    private final FragmentManager fragmentManager;
    private final FileBrowserFragment.OnPermissionRequestListener permissionRequestListener;
    
    // 页面 View 引用
    private View gameListPage;
    private View fileManagerPage;
    private View controlPage;
    private View downloadPage;
    private View settingsPage;
    private View importPage;
    
    // TopAppBar 引用
    private com.google.android.material.appbar.MaterialToolbar topAppBar;

    public MainNavigationDelegate(MainActivity activity, FragmentManager fragmentManager, 
                                  FileBrowserFragment.OnPermissionRequestListener permissionRequestListener) {
        this.activity = activity;
        this.fragmentManager = fragmentManager;
        this.permissionRequestListener = permissionRequestListener;
        initializeViews();
    }

    /**
     * 初始化所有页面 View 引用
     */
    private void initializeViews() {
        gameListPage = activity.findViewById(R.id.gameListPage);
        fileManagerPage = activity.findViewById(R.id.fileManagerPage);
        controlPage = activity.findViewById(R.id.controlPage);
        downloadPage = activity.findViewById(R.id.downloadPage);
        settingsPage = activity.findViewById(R.id.settingsPage);
        importPage = activity.findViewById(R.id.importPage);
        topAppBar = activity.findViewById(R.id.topAppBar);
    }

    /**
     * 设置 NavigationRail 的点击监听器
     */
    public void setupNavigationRail(NavigationRailView navigationRail, 
                                    Runnable onShowGamePage,
                                    Runnable onShowAddGameFragment) {
        if (navigationRail == null) {
            return;
        }
        
        navigationRail.setOnItemSelectedListener(new NavigationRailView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(android.view.MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_settings) {
                    showSettingsPage();
                    return true;
                } else if (itemId == R.id.nav_download) {
                    showDownloadPage();
                    return true;
                } else if (itemId == R.id.nav_game) {
                    if (onShowGamePage != null) {
                        onShowGamePage.run();
                    } else {
                        showGamePage();
                    }
                    return true;
                } else if (itemId == R.id.nav_file) {
                    showFilePage();
                    return true;
                } else if (itemId == R.id.nav_control) {
                    showControlPage();
                    return true;
                } else if (itemId == R.id.nav_add_game) {
                    if (onShowAddGameFragment != null) {
                        onShowAddGameFragment.run();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * 显示游戏页面
     */
    public void showGamePage() {
        setPageVisibility(true, false, false, false, false, false);
        updateTopAppBar();
    }

    /**
     * 显示文件管理器页面（模组管理器）
     */
    public void showFilePage() {
        setPageVisibility(false, true, false, false, false, false);
        updateTopAppBar();
        
        // 初始化模组管理器 Fragment（如果尚未初始化）
        if (fileManagerPage != null && fragmentManager.findFragmentById(R.id.fileManagerPage) == null) {
            ModManagerFragment modManagerFragment = new ModManagerFragment();
            
            fragmentManager.beginTransaction()
                    .replace(R.id.fileManagerPage, modManagerFragment, "mod_manager")
                    .commit();
        }
        
        hideFragmentTopBar(fileManagerPage);
    }

    /**
     * 显示控制布局页面
     */
    public void showControlPage() {
        setPageVisibility(false, false, true, false, false, false);
        updateTopAppBar();
        
        // 初始化控制布局 Fragment（如果尚未初始化）
        if (controlPage != null && fragmentManager.findFragmentById(R.id.controlPage) == null) {
            ControlLayoutFragment controlFragment = new ControlLayoutFragment();
            controlFragment.setOnControlLayoutBackListener(() -> {
                // 控制布局返回时，切换到游戏页面
                showGamePage();
            });
            
            fragmentManager.beginTransaction()
                    .replace(R.id.controlPage, controlFragment, "control_layout")
                    .commit();
        }
        
        hideFragmentTopBar(controlPage);
    }

    /**
     * 显示下载页面
     */
    public void showDownloadPage() {
        setPageVisibility(false, false, false, true, false, false);
        updateTopAppBar();
        
        // 初始化下载 Fragment（如果尚未初始化）
        if (downloadPage != null && fragmentManager.findFragmentById(R.id.downloadPage) == null) {
            com.app.ralaunch.gog.GogClientFragment gogFragment = new com.app.ralaunch.gog.GogClientFragment();
            
            fragmentManager.beginTransaction()
                    .replace(R.id.downloadPage, gogFragment, "gog_client")
                    .commit();
        }
        
        hideFragmentTopBar(downloadPage);
    }

    /**
     * 显示设置页面
     */
    public void showSettingsPage() {
        setPageVisibility(false, false, false, false, true, false);
        updateTopAppBar();
        
        // 初始化设置 Fragment（如果尚未初始化）
        if (settingsPage != null && fragmentManager.findFragmentById(R.id.settingsPage) == null) {
            SettingsFragment settingsFragment = new SettingsFragment();
            settingsFragment.setOnSettingsBackListener(() -> {
                // 设置返回时，切换到游戏页面
                showGamePage();
            });
            
            fragmentManager.beginTransaction()
                    .replace(R.id.settingsPage, settingsFragment, "settings")
                    .commit();
        }
    }

    /**
     * 显示导入游戏页面（由 MainImportDelegate 调用）
     */
    public void showImportPage() {
        setPageVisibility(false, false, false, false, false, true);
        updateTopAppBar();
    }

    /**
     * 设置页面可见性
     */
    private void setPageVisibility(boolean game, boolean file, boolean control, 
                                  boolean download, boolean settings, boolean importPage) {
        if (gameListPage != null) gameListPage.setVisibility(game ? View.VISIBLE : View.GONE);
        if (fileManagerPage != null) fileManagerPage.setVisibility(file ? View.VISIBLE : View.GONE);
        if (controlPage != null) controlPage.setVisibility(control ? View.VISIBLE : View.GONE);
        if (downloadPage != null) downloadPage.setVisibility(download ? View.VISIBLE : View.GONE);
        if (settingsPage != null) settingsPage.setVisibility(settings ? View.VISIBLE : View.GONE);
        if (this.importPage != null) this.importPage.setVisibility(importPage ? View.VISIBLE : View.GONE);
    }

    /**
     * 更新 TopAppBar（所有页面统一隐藏）
     */
    private void updateTopAppBar() {
        if (topAppBar != null) {
            topAppBar.setVisibility(View.GONE);
        }
    }

    /**
     * 隐藏 Fragment 的顶部栏（统一处理）
     */
    private void hideFragmentTopBar(View container) {
        if (container == null) return;
        
        container.post(() -> {
            // ControlLayoutFragment 的 appBarLayout
            View appBarLayout = container.findViewById(R.id.appBarLayout);
            if (appBarLayout != null) {
                appBarLayout.setVisibility(View.GONE);
            }
            
            // GogClientFragment 的 toolbar 已从布局中移除（横屏设计）
        });
    }

    /**
     * 获取导入页面 View（供 MainImportDelegate 使用）
     */
    public View getImportPage() {
        return importPage;
    }

    /**
     * 获取 FragmentManager（供其他 delegate 使用）
     */
    public FragmentManager getFragmentManager() {
        return fragmentManager;
    }
}

