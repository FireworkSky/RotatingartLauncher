package com.app.ralaunch.manager;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.google.android.material.button.MaterialButton;
import com.app.ralaunch.manager.common.AnimationHelper;

/**
 * UI 管理器
 * 负责管理 UI 初始化和状态
 */
public class UIManager {
    private final Activity activity;
    private View mainLayout;
    private MaterialButton settingsButton;
    private MaterialButton addGameButton;
    private MaterialButton refreshButton;
    private MaterialButton gogButton;
    private MaterialButton launchGameButton;
    
    public UIManager(Activity activity) {
        this.activity = activity;
    }
    
    /**
     * 初始化 UI
     */
    public void initialize(View mainLayoutView, MaterialButton settingsBtn, 
                         MaterialButton addGameBtn, MaterialButton refreshBtn,
                         MaterialButton gogBtn, MaterialButton launchBtn) {
        this.mainLayout = mainLayoutView;
        this.settingsButton = settingsBtn;
        this.addGameButton = addGameBtn;
        this.refreshButton = refreshBtn;
        this.gogButton = gogBtn;
        this.launchGameButton = launchBtn;
    }
    
    /**
     * 设置全屏模式（必须在 setContentView() 之前调用）
     */
    public void setupFullscreen() {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    
    /**
     * 隐藏系统 UI
     */
    public void hideSystemUI() {
        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    
    /**
     * 显示主布局
     */
    public void showMainLayout() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 隐藏主布局
     */
    public void hideMainLayout() {
        if (mainLayout != null) {
            mainLayout.setVisibility(View.GONE);
        }
    }
    
    /**
     * 设置设置按钮点击监听器
     */
    public void setSettingsButtonListener(View.OnClickListener listener) {
        AnimationHelper.setClickListenerWithBounce(settingsButton, listener);
    }
    
    /**
     * 设置添加游戏按钮点击监听器
     */
    public void setAddGameButtonListener(View.OnClickListener listener) {
        AnimationHelper.setClickListenerWithBounce(addGameButton, listener);
    }
    
    /**
     * 设置刷新按钮点击监听器
     */
    public void setRefreshButtonListener(View.OnClickListener listener) {
        AnimationHelper.setClickListener(refreshButton, listener);
    }
    
    /**
     * 设置 GOG 按钮点击监听器
     */
    public void setGogButtonListener(View.OnClickListener listener) {
        AnimationHelper.setClickListenerWithBounce(gogButton, listener);
    }
    
    /**
     * 设置启动游戏按钮点击监听器
     */
    public void setLaunchGameButtonListener(View.OnClickListener listener) {
        AnimationHelper.setClickListenerWithPulse(launchGameButton, listener);
    }
    
    /**
     * 显示启动游戏按钮
     */
    public void showLaunchGameButton() {
        if (launchGameButton != null) {
            launchGameButton.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 隐藏启动游戏按钮
     */
    public void hideLaunchGameButton() {
        if (launchGameButton != null) {
            launchGameButton.setVisibility(View.GONE);
        }
    }
    
    /**
     * 检查主布局是否可见
     */
    public boolean isMainLayoutVisible() {
        return mainLayout != null && mainLayout.getVisibility() == View.VISIBLE;
    }
}

