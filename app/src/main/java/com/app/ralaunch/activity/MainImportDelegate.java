package com.app.ralaunch.activity;

import android.view.View;
import androidx.fragment.app.FragmentManager;
import com.app.ralaunch.R;
import com.app.ralaunch.fragment.GameImportFragment;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.manager.FragmentNavigator;

/**
 * 负责 MainActivity 的游戏导入相关逻辑。
 * 
 * 简化的导入流程：
 * - 选择游戏本体 ZIP 文件
 * - 可选选择模组加载器 ZIP 文件
 * - 使用 Python 脚本处理安装
 */
public class MainImportDelegate {

    private static final String TAG = "MainImportDelegate";

    private final MainActivity activity;
    private final FragmentManager fragmentManager;
    private final FragmentNavigator fragmentNavigator;
    private final MainNavigationDelegate navigationDelegate;
    
    // 导入完成回调
    private OnImportCompleteListener onImportCompleteListener;

    /**
     * 导入完成回调接口
     */
    public interface OnImportCompleteListener {
        void onImportComplete(String gameType, GameItem newGame);
    }

    public MainImportDelegate(MainActivity activity, FragmentManager fragmentManager,
                             FragmentNavigator fragmentNavigator,
                             MainNavigationDelegate navigationDelegate) {
        this.activity = activity;
        this.fragmentManager = fragmentManager;
        this.fragmentNavigator = fragmentNavigator;
        this.navigationDelegate = navigationDelegate;
    }

    /**
     * 设置导入完成监听器
     */
    public void setOnImportCompleteListener(OnImportCompleteListener listener) {
        this.onImportCompleteListener = listener;
    }

    /**
     * 显示游戏导入页面
     */
    public void showAddGameFragment() {
        View importPage = navigationDelegate.getImportPage();
        if (importPage == null) {
            return;
        }
        
        navigationDelegate.showImportPage();
        
        // 检查是否已有导入 Fragment
        if (fragmentManager.findFragmentById(R.id.importPage) == null) {
            GameImportFragment importFragment = new GameImportFragment();
            
            // 设置导入完成监听
            importFragment.setOnImportCompleteListener((gameType, newGame) -> {
                if (onImportCompleteListener != null) {
                    onImportCompleteListener.onImportComplete(gameType, newGame);
                }
                // 导入完成后返回游戏页面
                navigationDelegate.showGamePage();
            });
            
            // 设置返回监听
            importFragment.setOnBackListener(() -> 
                navigationDelegate.showGamePage()
            );
            
            fragmentManager.beginTransaction()
                    .replace(R.id.importPage, importFragment, "game_import")
                    .commit();
        }
    }

    /**
     * 刷新导入页面
     */
    public void refreshImportPage() {
        // 不再需要刷新插件列表
    }

    /**
     * 开始游戏导入（从外部调用，如 GOG 下载完成后）
     */
    public void startGameImport(String gameFilePath, String gameName, String gameVersion) {
        View importPage = navigationDelegate.getImportPage();
        if (importPage == null) {
            return;
        }
        
        navigationDelegate.showImportPage();
        
        // 创建带参数的 LocalImportFragment
        com.app.ralaunch.fragment.LocalImportFragment importFragment = 
            new com.app.ralaunch.fragment.LocalImportFragment();
        
        android.os.Bundle args = new android.os.Bundle();
        args.putString("gameFilePath", gameFilePath);
        args.putString("gameName", gameName);
        args.putString("gameVersion", gameVersion);
        importFragment.setArguments(args);
        
        // 设置导入完成监听
        importFragment.setOnImportCompleteListener((gameType, newGame) -> {
            if (onImportCompleteListener != null) {
                onImportCompleteListener.onImportComplete(gameType, newGame);
            }
            navigationDelegate.showGamePage();
        });
        
        // 设置返回监听
        importFragment.setOnBackListener(() -> 
            navigationDelegate.showGamePage()
        );
        
        fragmentManager.beginTransaction()
                .replace(R.id.importPage, importFragment, "local_import")
                .commit();
    }
}
