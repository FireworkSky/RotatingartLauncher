package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置Fragment - 使用简单的 View 切换
 */
public class SettingsFragment extends BaseFragment {

    private static final String TAG = "SettingsFragment";

    private OnSettingsBackListener backListener;
    private ListView settingsCategoryListView;
    
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

        // 设置分类列表 - 手动添加 item
        settingsCategoryListView = view.findViewById(R.id.settingsCategoryListView);

        // TODO: 这一整个何意味，意味何

        // 找到 ListView 的父容器
        ViewGroup listViewParent = (ViewGroup) settingsCategoryListView.getParent();
        int listViewIndex = listViewParent.indexOfChild(settingsCategoryListView);
        
        // 移除 ListView
        listViewParent.removeView(settingsCategoryListView);
        
        // 创建 ScrollView 来包裹分类列表
        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(requireContext());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        );
        scrollParams.weight = 1;
        scrollView.setLayoutParams(scrollParams);
        scrollView.setFillViewport(false);

        // 创建新的容器来替代 ListView
        LinearLayout categoriesLinearLayout = new LinearLayout(requireContext());
        categoriesLinearLayout.setOrientation(LinearLayout.VERTICAL);
        categoriesLinearLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        // 将 LinearLayout 添加到 ScrollView
        scrollView.addView(categoriesLinearLayout);

        // 添加 ScrollView 到原位置
        listViewParent.addView(scrollView, listViewIndex);

        // 手动创建分类按钮
        List<Map<String, Object>> categories = getCategories();
        for (int i = 0; i < categories.size(); i++) {
            final int position = i;
            Map<String, Object> category = categories.get(i);
            
            View itemView = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_settings_category, categoriesLinearLayout, false);
            
            android.widget.ImageView icon = itemView.findViewById(R.id.icon);
            android.widget.TextView name = itemView.findViewById(R.id.category_name);
            
            icon.setImageResource((Integer) category.get("icon"));
            name.setText((String) category.get("category_name"));
            
            // 设置点击事件
            itemView.setOnClickListener(v -> {
                // 更新所有 item 的背景色 - 使用Material 3主题色
                for (int j = 0; j < categoriesLinearLayout.getChildCount(); j++) {
                    View child = categoriesLinearLayout.getChildAt(j);
                    if (child instanceof com.google.android.material.card.MaterialCardView) {
                        com.google.android.material.card.MaterialCardView cardView =
                            (com.google.android.material.card.MaterialCardView) child;
                        if (j == position) {
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

                // 切换内容面板
                switchToCategory(position);
            });

            categoriesLinearLayout.addView(itemView);
        }

        // 默认选中第一项
        if (categoriesLinearLayout.getChildCount() > 0) {
            View firstChild = categoriesLinearLayout.getChildAt(0);
            if (firstChild instanceof com.google.android.material.card.MaterialCardView) {
                ((com.google.android.material.card.MaterialCardView) firstChild).setCardBackgroundColor(
                    getResources().getColor(R.color.accent_primary_light, null));
            }
            switchToCategory(0);
        }
        
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

    private List<Map<String, Object>> getCategories() {
        List<Map<String, Object>> list = new ArrayList<>();

        // 外观设置
        Map<String, Object> item1 = new HashMap<>();
        item1.put("icon", R.drawable.ic_settings);
        item1.put("category_name", getString(R.string.settings_appearance));
        list.add(item1);

        // 控制设置
        Map<String, Object> item2 = new HashMap<>();
        item2.put("icon", R.drawable.ic_controller);
        item2.put("category_name", getString(R.string.settings_control));
        list.add(item2);

        // 游戏设置
        Map<String, Object> item3 = new HashMap<>();
        item3.put("icon", R.drawable.ic_game);
        item3.put("category_name", getString(R.string.settings_game));
        list.add(item3);

        // 启动器设置
        Map<String, Object> item4 = new HashMap<>();
        item4.put("icon", R.drawable.ic_launcher_foreground);
        item4.put("category_name", getString(R.string.settings_launcher));
        list.add(item4);

        // 实验性设置
        Map<String, Object> item5 = new HashMap<>();
        item5.put("icon", R.drawable.ic_bug);
        item5.put("category_name", getString(R.string.settings_developer));
        list.add(item5);

        return list;
    }
    




}
