package com.app.ralaunch.activity;

import android.os.Bundle;
import android.view.View;
import androidx.fragment.app.FragmentManager;
import com.app.ralaunch.R;
import com.app.ralaunch.fragment.FileBrowserFragment;
import com.app.ralaunch.fragment.GameImportFragment;
import com.app.ralaunch.fragment.LocalImportFragment;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.manager.GameListManager;
import com.app.ralaunch.manager.FragmentNavigator;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.IconExtractorHelper;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * 负责 MainActivity 的游戏导入相关逻辑。
 * 包括手动导入、从文件浏览器添加程序集、导入完成处理等。
 */
public class MainImportDelegate {

    private static final String TAG = "MainImportDelegate";

    private final MainActivity activity;
    private final FragmentManager fragmentManager;
    private final FragmentNavigator fragmentNavigator;
    private final GameListManager gameListManager;
    private final FileBrowserFragment.OnPermissionRequestListener permissionRequestListener;
    private final MainNavigationDelegate navigationDelegate;
    
    // 导入完成回调
    private LocalImportFragment.OnImportCompleteListener onImportCompleteListener;

    public MainImportDelegate(MainActivity activity, FragmentManager fragmentManager,
                             FragmentNavigator fragmentNavigator, GameListManager gameListManager,
                             FileBrowserFragment.OnPermissionRequestListener permissionRequestListener, 
                             MainNavigationDelegate navigationDelegate) {
        this.activity = activity;
        this.fragmentManager = fragmentManager;
        this.fragmentNavigator = fragmentNavigator;
        this.gameListManager = gameListManager;
        this.permissionRequestListener = permissionRequestListener;
        this.navigationDelegate = navigationDelegate;
    }

    /**
     * 设置导入完成监听器
     */
    public void setOnImportCompleteListener(LocalImportFragment.OnImportCompleteListener listener) {
        this.onImportCompleteListener = listener;
    }

    /**
     * 显示导入游戏页面
     */
    public void showAddGameFragment() {
        View importPage = navigationDelegate.getImportPage();
        if (importPage == null) {
            return;
        }
        
        navigationDelegate.showImportPage();
        
        // 初始化导入 Fragment（如果尚未初始化）
        if (fragmentManager.findFragmentById(R.id.importPage) == null) {
            GameImportFragment importFragment = new GameImportFragment();
            importFragment.setOnImportStartListener((gameFilePath, modLoaderFilePath, gameName, gameVersion) -> {
                // 开始导入，切换到 LocalImportFragment
                startGameImport(gameFilePath, modLoaderFilePath, gameName, gameVersion);
            });
            importFragment.setOnBackListener(() -> {
                // 导入返回时，切换到游戏页面
                navigationDelegate.showGamePage();
            });
            
            fragmentManager.beginTransaction()
                    .replace(R.id.importPage, importFragment, "game_import")
                    .commit();
        }
    }

    /**
     * 开始游戏导入
     */
    public void startGameImport(String gameFilePath, String modLoaderFilePath, 
                                String gameName, String gameVersion) {
        View importPage = navigationDelegate.getImportPage();
        if (importPage == null) return;
        
        LocalImportFragment localImportFragment = new LocalImportFragment();
        localImportFragment.setOnImportCompleteListener((gameType, newGame) -> {
            if (onImportCompleteListener != null) {
                onImportCompleteListener.onImportComplete(gameType, newGame);
            }
        });
        localImportFragment.setOnBackListener(() -> {
            // 导入完成或取消后，切换到游戏页面
            navigationDelegate.showGamePage();
        });
        
        // 传递文件路径给Fragment
        Bundle args = new Bundle();
        args.putString("gameFilePath", gameFilePath);
        args.putString("modLoaderFilePath", modLoaderFilePath);
        args.putString("gameName", gameName);
        args.putString("gameVersion", gameVersion);
        localImportFragment.setArguments(args);
        
        fragmentManager.beginTransaction()
                .replace(R.id.importPage, localImportFragment, "local_import")
                .addToBackStack("game_import")
                .commit();
    }

   


}

