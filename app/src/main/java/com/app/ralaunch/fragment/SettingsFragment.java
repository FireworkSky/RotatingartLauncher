package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.settings.SettingsModule;
import com.app.ralaunch.settings.AppearanceSettingsModule;
import com.app.ralaunch.settings.ControlsSettingsModule;
import com.app.ralaunch.settings.GameSettingsModule;
import com.app.ralaunch.settings.AboutSettingsModule;
import com.app.ralaunch.settings.DeveloperSettingsModule;

/**
 * 设置Fragment - 使用简单的 View 切换
 */
public class SettingsFragment extends BaseFragment {

    private static final String TAG = "SettingsFragment";

    private OnSettingsBackListener backListener;
    private com.google.android.material.tabs.TabLayout settingsTabLayout;
    
    // 内容面板
    private View contentAppearance;
    private View contentControls;
    private View contentGame;
    private View contentLauncher;
    private View contentAbout;
    private View contentDeveloper;
    
    // 设置模块
    private SettingsModule appearanceModule;
    private SettingsModule controlsModule;
    private SettingsModule gameModule;
    private SettingsModule aboutModule;
    private SettingsModule developerModule;

    public interface OnSettingsBackListener {
        void onSettingsBack();
    }

    public void setOnSettingsBackListener(OnSettingsBackListener listener) {
        this.backListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        setupUI(view);
        return view;
    }

    private void setupUI(View view) {
        // 初始化内容面板
        contentAppearance = view.findViewById(R.id.contentAppearance);
        contentControls = view.findViewById(R.id.contentControls);
        contentGame = view.findViewById(R.id.contentGame);
        contentLauncher = view.findViewById(R.id.contentLauncher);
        contentAbout = view.findViewById(R.id.contentAbout);
        contentDeveloper = view.findViewById(R.id.contentDeveloper);

        // 初始化 TabLayout
        settingsTabLayout = view.findViewById(R.id.settingsTabLayout);
        
        // 设置 TabLayout
        setupTabLayout();
        
        // 默认选中第一项
        switchToCategory(0);
        
        // 初始化所有设置模块
        appearanceModule = new AppearanceSettingsModule();
        controlsModule = new ControlsSettingsModule();
        gameModule = new GameSettingsModule();
        aboutModule = new AboutSettingsModule();
        developerModule = new DeveloperSettingsModule();
        
        // 设置各个模块
        appearanceModule.setup(this, view);
        controlsModule.setup(this, view);
        gameModule.setup(this, view);
        aboutModule.setup(this, view);
        developerModule.setup(this, view);
    }
    
    /**
     * 设置 TabLayout
     */
    private void setupTabLayout() {
        // 添加标签页 - Underline Tabs 风格，只显示文字，不显示图标
        settingsTabLayout.addTab(settingsTabLayout.newTab()
            .setText(getString(R.string.settings_appearance)));
        
        settingsTabLayout.addTab(settingsTabLayout.newTab()
            .setText(getString(R.string.settings_control)));
        
        settingsTabLayout.addTab(settingsTabLayout.newTab()
            .setText(getString(R.string.settings_game)));
        
        settingsTabLayout.addTab(settingsTabLayout.newTab()
            .setText(getString(R.string.settings_launcher)));
        
        settingsTabLayout.addTab(settingsTabLayout.newTab()
            .setText("关于"));
        
        settingsTabLayout.addTab(settingsTabLayout.newTab()
            .setText(getString(R.string.settings_developer)));
        
        // 设置标签选中监听器
        settingsTabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                int position = tab.getPosition();
                switchToCategory(position);
            }
            
            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                // 不需要处理
            }
            
            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                // 不需要处理
            }
        });
        
        // 默认选中第一项，确保下划线显示
        com.google.android.material.tabs.TabLayout.Tab firstTab = settingsTabLayout.getTabAt(0);
        if (firstTab != null) {
            firstTab.select();
        }
    }
    

    /**
     * 切换到指定分类 - 带淡入淡出动画
     */
    private void switchToCategory(int position) {
        // 获取当前显示的内容
        View currentView = null;
        if (contentAppearance.getVisibility() == View.VISIBLE) currentView = contentAppearance;
        else if (contentControls.getVisibility() == View.VISIBLE) currentView = contentControls;
        else if (contentGame.getVisibility() == View.VISIBLE) currentView = contentGame;
        else if (contentLauncher.getVisibility() == View.VISIBLE) currentView = contentLauncher;
        else if (contentAbout.getVisibility() == View.VISIBLE) currentView = contentAbout;
        else if (contentDeveloper.getVisibility() == View.VISIBLE) currentView = contentDeveloper;
        
        // 选择要显示的内容
        View nextView = null;
        switch (position) {
            case 0: nextView = contentAppearance; break;
            case 1: nextView = contentControls; break;
            case 2: nextView = contentGame; break;
            case 3: nextView = contentLauncher; break;
            case 4: nextView = contentAbout; break;
            case 5: nextView = contentDeveloper; break;
        }

        // 如果是同一个内容，不需要切换
        if (currentView == nextView) {
            return;
        }
        
        final View finalCurrentView = currentView;
        final View finalNextView = nextView;
        
        if (finalCurrentView != null) {
            // 淡出当前内容
            finalCurrentView.animate()
                .alpha(0f)
                .setDuration(150)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    finalCurrentView.setVisibility(View.GONE);
                    finalCurrentView.setAlpha(1f); // 重置 alpha
                    
                    // 淡入新内容
                    if (finalNextView != null) {
                        finalNextView.setAlpha(0f);
                        finalNextView.setVisibility(View.VISIBLE);
                        finalNextView.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    }
                })
                .start();
        } else {
            // 直接显示新内容（首次加载）
            if (finalNextView != null) {
                finalNextView.setAlpha(0f);
                finalNextView.setVisibility(View.VISIBLE);
                finalNextView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            }
        }
    }





}
