package com.app.ralaunch.manager;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import com.app.ralaunch.manager.common.AnimationHelper;
import com.app.ralaunch.manager.common.IconLoader;
import com.app.ralaunch.manager.common.SelectionViewManager;
import com.app.ralaunch.manager.common.ViewTransitionManager;
import java.io.File;

/**
 * 文件浏览器管理器
 * 负责管理文件浏览器模式
 */
public class FileBrowserManager {
    private final Context context;
    private View fileBrowserContainer;
    private View gameRecyclerView;
    private SelectionViewManager selectionViewManager;
    private com.app.ralib.ui.GameFileBrowser gameFileBrowser;
    private File selectedAssemblyFile;
    private boolean isFileBrowserMode = false;
    private OnFileSelectedListener onFileSelectedListener;
    private OnFileLongClickListener onFileLongClickListener;
    
    public interface OnFileSelectedListener {
        void onFileSelected(File file);
    }
    
    public interface OnFileLongClickListener {
        void onFileLongClick(File file);
    }
    
    public FileBrowserManager(Context context) {
        this.context = context;
    }
    
    /**
     * 初始化文件浏览器管理器
     */
    public void initialize(View container, View recyclerView, View gameInfoView,
                          ImageView gameImage, TextView gameName, 
                          TextView gameDescription, CardView emptyText) {
        this.fileBrowserContainer = container;
        this.gameRecyclerView = recyclerView;
        this.selectionViewManager = new SelectionViewManager(
            gameInfoView, gameImage, gameName, gameDescription, emptyText);
    }
    
    /**
     * 显示文件浏览器模式
     */
    public void showFileBrowserMode() {
        isFileBrowserMode = true;
        
        AnimationHelper.animateFadeOut(gameRecyclerView, () -> {
            gameRecyclerView.setVisibility(View.GONE);
            fileBrowserContainer.setVisibility(View.VISIBLE);
            
            // 在游戏列表区域嵌入文件浏览器
            showGameFileBrowserInContainer();
            
            // 右侧显示空状态
            if (selectionViewManager != null) {
                selectionViewManager.showEmpty();
            }
            
            AnimationHelper.animateFadeIn(fileBrowserContainer);
        });
    }
    
    /**
     * 显示游戏列表模式
     */
    public void showGameListMode() {
        isFileBrowserMode = false;
        
        // 立即隐藏文件浏览器容器，避免残影
        if (fileBrowserContainer != null) {
            fileBrowserContainer.setVisibility(View.GONE);
            fileBrowserContainer.setAlpha(0f);
            ViewTransitionManager.clearContainer((ViewGroup) fileBrowserContainer);
        }
        
        // 显示游戏列表
        if (gameRecyclerView != null) {
            gameRecyclerView.setVisibility(View.VISIBLE);
            gameRecyclerView.setAlpha(1f);
        }
    }
    
    /**
     * 在容器中显示游戏文件浏览器
     */
    private void showGameFileBrowserInContainer() {
        // 清除容器
        ViewTransitionManager.clearContainer((ViewGroup) fileBrowserContainer);
        
        // 创建新的文件浏览器
        gameFileBrowser = new com.app.ralib.ui.GameFileBrowser(context);
        
        // 设置起始目录为游戏目录
        String gamesDir = context.getExternalFilesDir(null).getAbsolutePath() + "/games";
        gameFileBrowser.setDirectory(gamesDir);
        
        // 设置文件选择监听器
        gameFileBrowser.setOnFileSelectedListener(this::onGameFileSelected);
        
        // 设置文件长按监听器
        gameFileBrowser.setOnFileLongClickListener(this::onGameFileLongClick);
        
        // 添加到容器
        ViewTransitionManager.addViewToContainer((ViewGroup) fileBrowserContainer, gameFileBrowser);
    }
    
    /**
     * 游戏文件浏览器选择了文件
     */
    private void onGameFileSelected(File file) {
        selectedAssemblyFile = file;
        
        // 显示右侧启动面板
        showFileBrowserSelection(file);
        
        // 通知监听器
        if (onFileSelectedListener != null) {
            onFileSelectedListener.onFileSelected(file);
        }
    }
    
    /**
     * 游戏文件浏览器长按了文件
     */
    private void onGameFileLongClick(File file) {
        // 通知长按监听器
        if (onFileLongClickListener != null) {
            onFileLongClickListener.onFileLongClick(file);
        }
    }
    
    /**
     * 显示文件浏览器选择的文件信息
     */
    private void showFileBrowserSelection(File file) {
        if (selectionViewManager != null) {
            selectionViewManager.showSelection(file.getName(), file.getParent());
            
            // 异步加载文件图标
            IconLoader.loadFileIconAsync(context, selectionViewManager.getImageView(), file.getAbsolutePath());
            
            // 添加滑入动画
            AnimationHelper.animateSlideInRight(selectionViewManager.getInfoView());
        }
    }
    
    /**
     * 获取当前选中的程序集文件
     */
    public File getSelectedAssemblyFile() {
        return selectedAssemblyFile;
    }
    
    /**
     * 检查是否处于文件浏览器模式
     */
    public boolean isFileBrowserMode() {
        return isFileBrowserMode;
    }
    
    /**
     * 设置文件选择监听器
     */
    public void setOnFileSelectedListener(OnFileSelectedListener listener) {
        this.onFileSelectedListener = listener;
    }
    
    /**
     * 设置文件长按监听器
     */
    public void setOnFileLongClickListener(OnFileLongClickListener listener) {
        this.onFileLongClickListener = listener;
    }
}

