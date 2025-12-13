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

    /**
     * 从文件浏览器选择 DLL/EXE 并添加到游戏列表
     */
    public void showAddAssemblyFromFileBrowser() {
        if (fragmentNavigator == null) {
            return;
        }
        
        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        fileBrowserFragment.setMode(FileBrowserFragment.MODE_SELECT_ASSEMBLY);
        fileBrowserFragment.setFileType("assembly", new String[]{".dll", ".exe"});
        
        // 设置程序集选择监听器
        fileBrowserFragment.setOnAssemblySelectedListener(assemblyPath -> {
            addAssemblyToGameList(assemblyPath);
            fragmentNavigator.hideFragment("file_browser");
        });
        
        // 设置添加到游戏列表监听器（长按 DLL/EXE 时使用）
        fileBrowserFragment.setOnAddToGameListListener(assemblyPath -> {
            addAssemblyToGameList(assemblyPath);
            showToast(activity.getString(R.string.import_added_to_list));
        });
        
        fileBrowserFragment.setOnBackListener(() -> fragmentNavigator.hideFragment("file_browser"));
        fileBrowserFragment.setOnPermissionRequestListener(permissionRequestListener);
        
        fragmentNavigator.showFragment(fileBrowserFragment, "file_browser");
    }

    /**
     * 将选中的程序集添加到游戏列表
     */
    public void addAssemblyToGameList(String assemblyPath) {
        try {
            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists() || !assemblyFile.isFile()) {
                showToast(activity.getString(R.string.import_file_not_exist));
                return;
            }
            
            // 获取文件名（不含扩展名）作为游戏名称
            String fileName = assemblyFile.getName();
            String gameName = fileName;
            if (fileName.contains(".")) {
                gameName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            
            // 创建 GameItem
            GameItem gameItem = new GameItem();
            gameItem.setGameName(gameName);
            gameItem.setGamePath(assemblyPath);
            gameItem.setGameDescription(activity.getString(R.string.import_assembly_desc, fileName));
            gameItem.setEngineType("FNA"); // 默认引擎类型
            gameItem.setShortcut(true); // 标记为快捷方式，删除时不会删除实际文件
            
            // 设置基础路径为空，表示这不是一个完整的游戏安装
            gameItem.setGameBasePath("");
            
            // 尝试提取图标（支持 EXE 和 DLL）
            String iconPath = null;
            String iconSourcePath = assemblyPath;
            
            // 如果是 EXE，设置为游戏本体路径，直接尝试提取图标
            if (fileName.toLowerCase().endsWith(".exe")) {
                gameItem.setGameBodyPath(assemblyPath);
                // 尝试从 EXE 提取图标
                try {
                    iconPath = IconExtractorHelper.extractGameIcon(activity, assemblyPath);
                } catch (Exception e) {
                    AppLogger.warn(TAG, "无法从 EXE 提取图标: " + e.getMessage());
                }
            } else {
                // DLL 文件：先尝试从 DLL 本身提取图标
                gameItem.setGameBodyPath(""); // DLL 作为主程序集
                try {
                    iconPath = IconExtractorHelper.extractGameIcon(activity, assemblyPath);
                    if (iconPath == null) {
                        // 如果 DLL 没有图标，尝试查找同目录下的同名 EXE 文件
                        String exePath = assemblyPath.replaceAll("\\.[^.]+$", ".exe");
                        File exeFile = new File(exePath);
                        if (exeFile.exists()) {
                            AppLogger.info(TAG, "尝试从关联的 EXE 提取图标: " + exePath);
                            iconSourcePath = exePath;
                            gameItem.setGameBodyPath(exePath); // 设置关联的 EXE 为游戏本体
                            iconPath = IconExtractorHelper.extractGameIcon(activity, exePath);
                        }
                    }
                } catch (Exception e) {
                    AppLogger.warn(TAG, "无法从程序集提取图标: " + e.getMessage());
                }
            }
            
            // 如果成功提取到图标，设置图标路径
            if (iconPath != null && !iconPath.isEmpty()) {
                gameItem.setIconPath(iconPath);
                AppLogger.info(TAG, "成功提取图标: " + iconPath);
            } else {
                AppLogger.warn(TAG, "未能提取图标，使用默认图标");
            }
            
            // 添加到游戏列表
            if (gameListManager != null) {
                gameListManager.addGame(gameItem);
                showToast(activity.getString(R.string.import_game_added, gameName));
            } else {
                // 如果 gameListManager 未初始化，直接使用 GameDataManager
                com.app.ralaunch.RaLaunchApplication.getGameDataManager().addGame(gameItem);
                showToast(activity.getString(R.string.import_game_added, gameName));
                // 刷新列表
                if (gameListManager != null) {
                    gameListManager.refreshGameList();
                }
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "添加程序集失败: " + e.getMessage(), e);
            showToast(activity.getString(R.string.import_add_failed, e.getMessage()));
        }
    }

    /**
     * 显示文件操作菜单（用于文件浏览器视图）
     */
    public void showFileActionMenu(File file) {
        String fileName = file.getName();
        String filePath = file.getAbsolutePath();
        
        // 创建选项菜单
        List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new ArrayList<>();
        
        // 添加到游戏列表选项
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "add_to_game_list", 
            activity.getString(R.string.import_add_to_list), 
            activity.getString(R.string.import_add_to_list_desc)
        ));
        
        // 显示选项对话框
        new com.app.ralib.dialog.OptionSelectorDialog()
            .setTitle(activity.getString(R.string.import_file_action, fileName))
            .setIcon(R.drawable.ic_file)
            .setOptions(options)
            .setOnOptionSelectedListener(value -> {
                if ("add_to_game_list".equals(value)) {
                    addAssemblyToGameList(filePath);
                }
            })
            .show(fragmentManager, "file_action_menu");
    }

    private void showToast(String message) {
        com.app.ralaunch.manager.common.MessageHelper.showToast(activity, message);
    }
}

