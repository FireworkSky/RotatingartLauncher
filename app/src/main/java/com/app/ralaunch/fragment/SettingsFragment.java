package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;
import com.app.ralib.dialog.OptionSelectorDialog;
import com.app.ralaunch.utils.SettingsManager;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置Fragment - 使用简单的 View 切换
 */
public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    private OnSettingsBackListener backListener;
    private ListView settingsCategoryListView;
    
    // 内容面板
    private View contentAppearance;
    private View contentControls;
    private View contentGame;
    private View contentLauncher;
    private View contentDeveloper;

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

        // 返回按钮
        View buttonBack = view.findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onSettingsBack();
            }
        });

        // 设置分类列表 - 手动添加 item
        settingsCategoryListView = view.findViewById(R.id.settingsCategoryListView);
        
        // 找到 ListView 的父容器
        ViewGroup listViewParent = (ViewGroup) settingsCategoryListView.getParent();
        int listViewIndex = listViewParent.indexOfChild(settingsCategoryListView);
        
        // 移除 ListView
        listViewParent.removeView(settingsCategoryListView);
        
        // 创建新的容器来替代 ListView
        LinearLayout categoriesLinearLayout = new LinearLayout(requireContext());
        categoriesLinearLayout.setOrientation(LinearLayout.VERTICAL);
        categoriesLinearLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        // 添加到原位置
        listViewParent.addView(categoriesLinearLayout, listViewIndex);
        
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
                // 更新所有 item 的背景色
                for (int j = 0; j < categoriesLinearLayout.getChildCount(); j++) {
                    View child = categoriesLinearLayout.getChildAt(j);
                    child.setBackgroundColor(j == position ?
                        getResources().getColor(android.R.color.holo_green_dark, null) :
                        getResources().getColor(android.R.color.transparent, null));
                }
                
                // 切换内容面板
                switchToCategory(position);
            });
            
            categoriesLinearLayout.addView(itemView);
        }
        
        // 默认选中第一项
        if (categoriesLinearLayout.getChildCount() > 0) {
            categoriesLinearLayout.getChildAt(0).setBackgroundColor(
                getResources().getColor(android.R.color.holo_green_dark, null));
            switchToCategory(0);
        }
        
        // 初始化所有设置
        setupAppearanceSettings(view);
        setupDeveloperSettings(view);
    }
    
    /**
     * 设置外观设置面板
     */
    private void setupAppearanceSettings(View rootView) {
        SettingsManager settingsManager = SettingsManager.getInstance(requireContext());
        
        // 主题设置
        MaterialCardView themeCard = rootView.findViewById(R.id.themeCard);
        TextView tvThemeValue = rootView.findViewById(R.id.tvThemeValue);
        
        if (themeCard != null && tvThemeValue != null) {
            updateThemeDisplay(settingsManager, tvThemeValue);
            
            themeCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option("0", "跟随系统", "根据系统设置自动切换"),
                    new OptionSelectorDialog.Option("1", "深色主题", "始终使用深色主题"),
                    new OptionSelectorDialog.Option("2", "浅色主题", "始终使用浅色主题")
                );
                
                OptionSelectorDialog dialog = new OptionSelectorDialog()
                    .setTitle("主题设置")
                    .setIcon(R.drawable.ic_settings)
                    .setOptions(options)
                    .setCurrentValue(String.valueOf(settingsManager.getThemeMode()))
                    .setAutoCloseOnSelect(false)  // 禁用自动关闭，手动控制
                    .setOnOptionSelectedListener(value -> {
                        int themeMode = Integer.parseInt(value);
                        int oldThemeMode = settingsManager.getThemeMode();
                        
                        // 强制关闭所有对话框（防止recreate后被恢复）
                        androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
                        for (androidx.fragment.app.Fragment fragment : fm.getFragments()) {
                            if (fragment instanceof androidx.fragment.app.DialogFragment) {
                                ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
                            }
                        }
                        
                        // 如果主题有变化，刷新Activity
                        if (themeMode != oldThemeMode) {
                            settingsManager.setThemeMode(themeMode);
                            
                            // 延迟刷新Activity，确保对话框完全关闭
                            new android.os.Handler().postDelayed(() -> {
                                if (isAdded() && getActivity() != null) {
                                    requireActivity().recreate();
                                }
                            }, 100);
                        }
                    });
                dialog.show(getParentFragmentManager(), "theme_dialog");
            });
        }
        
        // 语言设置
        MaterialCardView languageCard = rootView.findViewById(R.id.languageCard);
        TextView tvLanguageValue = rootView.findViewById(R.id.tvLanguageValue);
        
        if (languageCard != null && tvLanguageValue != null) {
            updateLanguageDisplay(settingsManager, tvLanguageValue);
            
            languageCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option("0", "跟随系统", "使用系统语言设置"),
                    new OptionSelectorDialog.Option("1", "English", "Use English"),
                    new OptionSelectorDialog.Option("2", "简体中文", "使用简体中文")
                );
                
                OptionSelectorDialog dialog = new OptionSelectorDialog()
                    .setTitle("语言设置")
                    .setIcon(R.drawable.ic_language)
                    .setOptions(options)
                    .setCurrentValue(String.valueOf(settingsManager.getAppLanguage()))
                    .setAutoCloseOnSelect(false)  // 禁用自动关闭，手动控制
                    .setOnOptionSelectedListener(value -> {
                        int language = Integer.parseInt(value);
                        int oldLanguage = settingsManager.getAppLanguage();
                        
                        // 强制关闭所有对话框（防止recreate后被恢复）
                        androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
                        for (androidx.fragment.app.Fragment fragment : fm.getFragments()) {
                            if (fragment instanceof androidx.fragment.app.DialogFragment) {
                                ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
                            }
                        }
                        
                        // 如果语言有变化，刷新Activity
                        if (language != oldLanguage) {
                            settingsManager.setAppLanguage(language);
                            
                            // 延迟刷新Activity，确保对话框完全关闭
                            new android.os.Handler().postDelayed(() -> {
                                if (isAdded() && getActivity() != null) {
                                    requireActivity().recreate();
                                }
                            }, 100);
                        }
                    });
                dialog.show(getParentFragmentManager(), "language_dialog");
            });
        }
        
        // CPU架构设置
        MaterialCardView architectureCard = rootView.findViewById(R.id.architectureCard);
        TextView tvArchitectureValue = rootView.findViewById(R.id.tvArchitectureValue);
        
        if (architectureCard != null && tvArchitectureValue != null) {
            updateArchitectureDisplay(settingsManager, tvArchitectureValue);
            
            architectureCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option("auto", "自动检测（推荐）", "根据设备自动选择架构"),
                    new OptionSelectorDialog.Option("arm64", "ARM64", "适用于ARM64设备"),
                    new OptionSelectorDialog.Option("x86_64", "x86_64", "适用于x86_64设备")
                );
                
                new OptionSelectorDialog()
                    .setTitle("CPU 架构")
                    .setIcon(R.drawable.ic_game)
                    .setOptions(options)
                    .setCurrentValue(settingsManager.getRuntimeArchitecture())
                    .setOnOptionSelectedListener(value -> {
                        settingsManager.setRuntimeArchitecture(value);
                        updateArchitectureDisplay(settingsManager, tvArchitectureValue);
                        Toast.makeText(requireContext(), "架构已更改", Toast.LENGTH_SHORT).show();
                    })
                    .show(getParentFragmentManager(), "architecture");
            });
        }
        
        // FNA渲染器设置
        MaterialCardView rendererCard = rootView.findViewById(R.id.rendererCard);
        TextView tvRendererValue = rootView.findViewById(R.id.tvRendererValue);
        
        if (rendererCard != null && tvRendererValue != null) {
            updateRendererDisplay(settingsManager, tvRendererValue);
            
            rendererCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option("auto", "自动选择（推荐）", "自动选择最佳渲染器"),
                    new OptionSelectorDialog.Option("opengl_native", "OpenGL ES 3（原生）", "使用原生OpenGL ES 3"),
                    new OptionSelectorDialog.Option("opengl_gl4es", "OpenGL (gl4es)", "通过gl4es转换层"),
                    new OptionSelectorDialog.Option("vulkan", "Vulkan（实验性）", "使用Vulkan API")
                );
                
                new OptionSelectorDialog()
                    .setTitle("FNA 渲染器")
                    .setIcon(R.drawable.ic_game)
                    .setOptions(options)
                    .setCurrentValue(settingsManager.getFnaRenderer())
                    .setOnOptionSelectedListener(value -> {
                        settingsManager.setFnaRenderer(value);
                        updateRendererDisplay(settingsManager, tvRendererValue);
                        Toast.makeText(requireContext(), "渲染器已更改", Toast.LENGTH_SHORT).show();
                    })
                    .show(getParentFragmentManager(), "renderer");
            });
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

    private List<Map<String, Object>> getCategories() {
        List<Map<String, Object>> list = new ArrayList<>();

        // 外观设置
        Map<String, Object> item1 = new HashMap<>();
        item1.put("icon", R.drawable.ic_settings);
        item1.put("category_name", "外观设置");
        list.add(item1);

        // 控制设置
        Map<String, Object> item2 = new HashMap<>();
        item2.put("icon", R.drawable.ic_controller);
        item2.put("category_name", "控制设置");
        list.add(item2);

        // 游戏设置
        Map<String, Object> item3 = new HashMap<>();
        item3.put("icon", R.drawable.ic_game);
        item3.put("category_name", "游戏设置");
        list.add(item3);

        // 启动器设置
        Map<String, Object> item4 = new HashMap<>();
        item4.put("icon", R.drawable.ic_launcher_foreground);
        item4.put("category_name", "启动器设置");
        list.add(item4);

        // 实验性设置
        Map<String, Object> item5 = new HashMap<>();
        item5.put("icon", R.drawable.ic_bug);
        item5.put("category_name", "实验性设置");
        list.add(item5);

        return list;
    }
    
    /**
     * 设置开发者设置面板
     */
    private void setupDeveloperSettings(View rootView) {
        // 获取设置管理器
        SettingsManager settingsManager = SettingsManager.getInstance(requireContext());
        
        // 找到卡片和显示文本
        MaterialCardView verboseLoggingCard = rootView.findViewById(R.id.verboseLoggingCard);
        TextView tvVerboseLoggingValue = rootView.findViewById(R.id.tvVerboseLoggingValue);
        Button btn_enter_debug_page = rootView.findViewById(R.id.btn_enter_debug_page);
        
        if (verboseLoggingCard == null) {
            return;
        }
        
        // 更新显示值
        updateVerboseLoggingDisplay(settingsManager, tvVerboseLoggingValue);
        
        // 设置详细日志点击事件
        verboseLoggingCard.setOnClickListener(v -> {
            List<OptionSelectorDialog.Option> options = Arrays.asList(
                new OptionSelectorDialog.Option("true", "开启", "输出 CoreCLR 详细调试信息"),
                new OptionSelectorDialog.Option("false", "关闭", "只输出基本信息")
            );
            
            new OptionSelectorDialog()
                .setTitle("详细日志")
                .setIcon(R.drawable.ic_bug)
                .setOptions(options)
                .setCurrentValue(String.valueOf(settingsManager.isVerboseLogging()))
                .setOnOptionSelectedListener(value -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    settingsManager.setVerboseLogging(enabled);
                    updateVerboseLoggingDisplay(settingsManager, tvVerboseLoggingValue);
                    
                    String message = enabled ?
                            "已启用详细日志，重启应用后生效" :
                            "已禁用详细日志";
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                })
                .show(getParentFragmentManager(), "verbose_logging");
        });
        
        // 进入调试页面按钮
        if (btn_enter_debug_page != null) {
            btn_enter_debug_page.setOnClickListener(v -> {
                // 打开调试Activity
                startActivity(new android.content.Intent(requireContext(), com.app.ralaunch.activity.DebugActivity.class));
            });
        }
    }
    
    private void updateVerboseLoggingDisplay(SettingsManager settingsManager, TextView textView) {
        textView.setText(settingsManager.isVerboseLogging() ? "开启" : "关闭");
    }
    
    private void updateThemeDisplay(SettingsManager settingsManager, TextView textView) {
        int theme = settingsManager.getThemeMode();
        String[] themes = {"跟随系统", "深色主题", "浅色主题"};
        textView.setText(theme >= 0 && theme < themes.length ? themes[theme] : "跟随系统");
    }
    
    private void updateLanguageDisplay(SettingsManager settingsManager, TextView textView) {
        int language = settingsManager.getAppLanguage();
        String[] languages = {"跟随系统", "English", "简体中文"};
        textView.setText(language >= 0 && language < languages.length ? languages[language] : "跟随系统");
    }
    
    private void updateArchitectureDisplay(SettingsManager settingsManager, TextView textView) {
        String arch = settingsManager.getRuntimeArchitecture();
        String display = arch.equals("auto") ? "自动检测" : 
                        arch.equals("arm64") ? "ARM64" :
                        arch.equals("x86_64") ? "x86_64" : "自动检测";
        textView.setText(display);
    }
    
    private void updateRendererDisplay(SettingsManager settingsManager, TextView textView) {
        String renderer = settingsManager.getFnaRenderer();
        String display = renderer.equals("auto") ? "自动选择" :
                        renderer.equals("opengl_native") ? "OpenGL ES 3" :
                        renderer.equals("opengl_gl4es") ? "OpenGL (gl4es)" :
                        renderer.equals("vulkan") ? "Vulkan" : "自动选择";
        textView.setText(display);
    }
    
}
