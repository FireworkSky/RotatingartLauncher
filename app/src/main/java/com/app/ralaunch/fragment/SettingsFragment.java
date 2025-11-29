package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.settings.SettingsModule;
import com.app.ralaunch.settings.AppearanceSettingsModule;
import com.app.ralaunch.settings.ControlsSettingsModule;
import com.app.ralaunch.settings.GameSettingsModule;
import com.app.ralaunch.settings.DeveloperSettingsModule;
import com.google.android.material.card.MaterialCardView;

/**
 * 设置Fragment - 使用简单的 View 切换
 */
public class SettingsFragment extends BaseFragment {

    private static final String TAG = "SettingsFragment";

    private OnSettingsBackListener backListener;
    private LinearLayout settingsCategoryLinearLayout;
    
    // 分类项视图
    private View categoryAppearance;
    private View categoryControls;
    private View categoryGame;
    private View categoryLauncher;
    private View categoryDeveloper;
    
    // 内容面板
    private View contentAppearance;
    private View contentControls;
    private View contentGame;
    private View contentLauncher;
    private View contentDeveloper;
    
    // 设置模块
    private SettingsModule appearanceModule;
    private SettingsModule controlsModule;
    private SettingsModule gameModule;
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
        contentDeveloper = view.findViewById(R.id.contentDeveloper);

        // 获取分类列表容器
        settingsCategoryLinearLayout = view.findViewById(R.id.settingsCategoryLinearLayout);
        
        // 获取分类项视图
        categoryAppearance = view.findViewById(R.id.categoryAppearance);
        categoryControls = view.findViewById(R.id.categoryControls);
        categoryGame = view.findViewById(R.id.categoryGame);
        categoryLauncher = view.findViewById(R.id.categoryLauncher);
        categoryDeveloper = view.findViewById(R.id.categoryDeveloper);

        // 设置分类项的内容和点击事件
        setupCategoryItem(categoryAppearance, R.drawable.ic_settings, R.string.settings_appearance, 0);
        setupCategoryItem(categoryControls, R.drawable.ic_controller, R.string.settings_control, 1);
        setupCategoryItem(categoryGame, R.drawable.ic_game, R.string.settings_game, 2);
        setupCategoryItem(categoryLauncher, R.drawable.ic_launcher_foreground, R.string.settings_launcher, 3);
        setupCategoryItem(categoryDeveloper, R.drawable.ic_bug, R.string.settings_developer, 4);

        // 默认选中第一项
        selectCategory(0);
        switchToCategory(0);
        
        // 初始化所有设置模块
        appearanceModule = new AppearanceSettingsModule();
        controlsModule = new ControlsSettingsModule();
        gameModule = new GameSettingsModule();
        developerModule = new DeveloperSettingsModule();
        
        // 设置各个模块
        appearanceModule.setup(this, view);
        controlsModule.setup(this, view);
        gameModule.setup(this, view);
        developerModule.setup(this, view);
    }
    
    /**
     * 设置分类项的内容和点击事件
     */
    private void setupCategoryItem(View itemView, int iconRes, int nameRes, final int position) {
        android.widget.ImageView icon = itemView.findViewById(R.id.icon);
        android.widget.TextView name = itemView.findViewById(R.id.category_name);
        
        icon.setImageResource(iconRes);
        name.setText(getString(nameRes));
        
        // 设置点击事件
        itemView.setOnClickListener(v -> {
            selectCategory(position);
            switchToCategory(position);
        });
    }
    
    /**
     * 选中指定分类（更新背景色）
     */
    private void selectCategory(int position) {
        // 获取所有分类项
        View[] categories = {
            categoryAppearance,
            categoryControls,
            categoryGame,
            categoryLauncher,
            categoryDeveloper
        };
        
        // 更新所有 item 的背景色
        for (int i = 0; i < categories.length; i++) {
            View itemView = categories[i];
            if (itemView instanceof com.google.android.material.card.MaterialCardView) {
                com.google.android.material.card.MaterialCardView cardView =
                    (com.google.android.material.card.MaterialCardView) itemView;
                if (i == position) {
                    // 选中状态 - 使用主题色带透明度
                    cardView.setCardBackgroundColor(
                        getResources().getColor(R.color.accent_primary_light, null));
                } else {
                    // 未选中状态 - 透明
                    cardView.setCardBackgroundColor(
                        getResources().getColor(android.R.color.transparent, null));
                }
            }
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
        else if (contentDeveloper.getVisibility() == View.VISIBLE) currentView = contentDeveloper;
        
        // 选择要显示的内容
        View nextView = null;
        switch (position) {
            case 0: nextView = contentAppearance; break;
            case 1: nextView = contentControls; break;
            case 2: nextView = contentGame; break;
            case 3: nextView = contentLauncher; break;
            case 4: nextView = contentDeveloper; break;
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
