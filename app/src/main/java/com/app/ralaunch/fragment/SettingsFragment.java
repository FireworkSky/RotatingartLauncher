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
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.dialog.PatchManagementDialog;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralaunch.utils.LocaleManager;
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
        setupGameSettings(view);
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
                    new OptionSelectorDialog.Option("0",
                        getString(R.string.theme_system),
                        getString(R.string.settings_theme_desc_auto)),
                    new OptionSelectorDialog.Option("1",
                        getString(R.string.theme_dark),
                        getString(R.string.settings_theme_desc_dark)),
                    new OptionSelectorDialog.Option("2",
                        getString(R.string.theme_light),
                        getString(R.string.settings_theme_desc_light))
                );

                OptionSelectorDialog dialog = new OptionSelectorDialog()
                    .setTitle(getString(R.string.theme_settings))
                    .setIcon(R.drawable.ic_settings)
                    .setOptions(options)
                    .setCurrentValue(String.valueOf(settingsManager.getThemeMode()))
                    .setAutoCloseOnSelect(true)  // 启用自动关闭
                    .setOnOptionSelectedListener(value -> {
                        int themeMode = Integer.parseInt(value);
                        int oldThemeMode = settingsManager.getThemeMode();

                        // 如果主题有变化，刷新Activity
                        if (themeMode != oldThemeMode) {
                            settingsManager.setThemeMode(themeMode);

                            // 延迟刷新Activity，等待对话框关闭动画完成
                            new android.os.Handler().postDelayed(() -> {
                                if (isAdded() && getActivity() != null) {
                                    // 确保所有对话框都已关闭
                                    androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
                                    for (androidx.fragment.app.Fragment fragment : fm.getFragments()) {
                                        if (fragment instanceof androidx.fragment.app.DialogFragment) {
                                            ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
                                        }
                                    }

                                    // 再延迟一点点，确保对话框完全移除
                                    new android.os.Handler().postDelayed(() -> {
                                        if (isAdded() && getActivity() != null) {
                                            requireActivity().recreate();
                                        }
                                    }, 50);
                                }
                            }, 300);
                        }
                    });
                dialog.show(getParentFragmentManager(), "theme_dialog");
            });
        }
        
        // 语言设置
        MaterialCardView languageCard = rootView.findViewById(R.id.languageCard);
        TextView tvLanguageValue = rootView.findViewById(R.id.tvLanguageValue);

        if (languageCard != null && tvLanguageValue != null) {
            updateLanguageDisplay(tvLanguageValue);

            languageCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option(LocaleManager.LANGUAGE_AUTO,
                        getString(R.string.language_system),
                        getString(R.string.settings_language_desc_auto)),
                    new OptionSelectorDialog.Option(LocaleManager.LANGUAGE_EN,
                        getString(R.string.language_english),
                        getString(R.string.settings_language_desc_en)),
                    new OptionSelectorDialog.Option(LocaleManager.LANGUAGE_ZH,
                        getString(R.string.language_chinese),
                        getString(R.string.settings_language_desc_zh))
                );

                String currentLanguage = LocaleManager.getLanguage(requireContext());

                OptionSelectorDialog dialog = new OptionSelectorDialog()
                    .setTitle(getString(R.string.language_settings))
                    .setIcon(R.drawable.ic_language)
                    .setOptions(options)
                    .setCurrentValue(currentLanguage)
                    .setAutoCloseOnSelect(false)  // 禁用自动关闭，手动控制
                    .setOnOptionSelectedListener(value -> {
                        String oldLanguage = LocaleManager.getLanguage(requireContext());

                        // 强制关闭所有对话框（防止recreate后被恢复）
                        androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
                        for (androidx.fragment.app.Fragment fragment : fm.getFragments()) {
                            if (fragment instanceof androidx.fragment.app.DialogFragment) {
                                ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
                            }
                        }

                        // 如果语言有变化，刷新Activity
                        if (!value.equals(oldLanguage)) {
                            LocaleManager.setLanguage(requireContext(), value);

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
        
    }

    /**
     * 设置游戏设置面板
     */
    private void setupGameSettings(View rootView) {
        SettingsManager settingsManager = SettingsManager.getInstance(requireContext());

        // 渲染器设置
        MaterialCardView rendererCard = rootView.findViewById(R.id.rendererCard);
        TextView tvRendererValue = rootView.findViewById(R.id.tvRendererValue);

        if (rendererCard != null && tvRendererValue != null) {
            updateRendererDisplay(settingsManager, tvRendererValue);

            rendererCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option(RuntimePreference.RENDERER_AUTO,
                        getString(R.string.renderer_auto),
                        getString(R.string.renderer_auto_desc)),
                    new OptionSelectorDialog.Option(RuntimePreference.RENDERER_OPENGLES3,
                        getString(R.string.renderer_opengles3),
                        getString(R.string.renderer_opengles3_desc)),
                    new OptionSelectorDialog.Option(RuntimePreference.RENDERER_OPENGL_GL4ES,
                        getString(R.string.renderer_opengl),
                        getString(R.string.renderer_opengl_desc)),
                    new OptionSelectorDialog.Option(RuntimePreference.RENDERER_VULKAN,
                        getString(R.string.renderer_vulkan),
                        getString(R.string.renderer_vulkan_desc))
                );

                new OptionSelectorDialog()
                    .setTitle(getString(R.string.renderer_title))
                    .setIcon(R.drawable.ic_game)
                    .setOptions(options)
                    .setCurrentValue(RuntimePreference.normalizeRendererValue(settingsManager.getFnaRenderer()))
                    .setOnOptionSelectedListener(value -> {
                        settingsManager.setFnaRenderer(RuntimePreference.normalizeRendererValue(value));
                        updateRendererDisplay(settingsManager, tvRendererValue);
                        Toast.makeText(requireContext(), getString(R.string.renderer_changed), Toast.LENGTH_SHORT).show();
                    })
                    .show(getParentFragmentManager(), "renderer");
            });
        }

        // 补丁管理
        MaterialCardView patchManagementCard = rootView.findViewById(R.id.patchManagementCard);
        if (patchManagementCard != null) {
            patchManagementCard.setOnClickListener(v -> {
                new PatchManagementDialog(requireContext()).show();
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
    
    /**
     * 设置开发者设置面板
     */
    private void setupDeveloperSettings(View rootView) {
        // 获取设置管理器
        SettingsManager settingsManager = SettingsManager.getInstance(requireContext());
        com.app.ralaunch.console.ConsoleManager consoleManager = 
            com.app.ralaunch.console.ConsoleManager.getInstance(requireContext());
        
        // 找到卡片和显示文本
        MaterialCardView consoleEnabledCard = rootView.findViewById(R.id.consoleEnabledCard);
        TextView tvConsoleEnabledValue = rootView.findViewById(R.id.tvConsoleEnabledValue);
        MaterialCardView verboseLoggingCard = rootView.findViewById(R.id.verboseLoggingCard);
        TextView tvVerboseLoggingValue = rootView.findViewById(R.id.tvVerboseLoggingValue);
        Button btn_enter_debug_page = rootView.findViewById(R.id.btn_enter_debug_page);
        
        if (verboseLoggingCard == null) {
            return;
        }
        
        // 更新显示值
        updateConsoleEnabledDisplay(consoleManager, tvConsoleEnabledValue);
        updateVerboseLoggingDisplay(settingsManager, tvVerboseLoggingValue);
        
        // 设置开发者控制台点击事件
        if (consoleEnabledCard != null) {
            consoleEnabledCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option("true",
                        getString(R.string.developer_console_on),
                        getString(R.string.developer_console_desc_on)),
                    new OptionSelectorDialog.Option("false",
                        getString(R.string.developer_console_off),
                        getString(R.string.developer_console_desc_off))
                );

                new OptionSelectorDialog()
                    .setTitle(getString(R.string.developer_console))
                    .setIcon(R.drawable.ic_bug)
                    .setOptions(options)
                    .setCurrentValue(String.valueOf(consoleManager.isConsoleEnabled()))
                    .setOnOptionSelectedListener(value -> {
                        boolean enabled = Boolean.parseBoolean(value);
                        consoleManager.setConsoleEnabled(enabled);
                        updateConsoleEnabledDisplay(consoleManager, tvConsoleEnabledValue);

                        String message = enabled ?
                                getString(R.string.developer_console_enabled) :
                                getString(R.string.developer_console_disabled);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    })
                    .show(getParentFragmentManager(), "console_enabled");
            });
        }
        
        // 设置详细日志点击事件
        verboseLoggingCard.setOnClickListener(v -> {
            List<OptionSelectorDialog.Option> options = Arrays.asList(
                new OptionSelectorDialog.Option("true",
                    getString(R.string.verbose_logging_on),
                    getString(R.string.verbose_logging_desc_on)),
                new OptionSelectorDialog.Option("false",
                    getString(R.string.verbose_logging_off),
                    getString(R.string.verbose_logging_desc_off))
            );

            new OptionSelectorDialog()
                .setTitle(getString(R.string.verbose_logging))
                .setIcon(R.drawable.ic_bug)
                .setOptions(options)
                .setCurrentValue(String.valueOf(settingsManager.isVerboseLogging()))
                .setOnOptionSelectedListener(value -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    settingsManager.setVerboseLogging(enabled);
                    updateVerboseLoggingDisplay(settingsManager, tvVerboseLoggingValue);

                    String message = enabled ?
                            getString(R.string.verbose_logging_enabled) :
                            getString(R.string.verbose_logging_disabled);
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
    
    private void updateConsoleEnabledDisplay(com.app.ralaunch.console.ConsoleManager consoleManager, TextView textView) {
        textView.setText(consoleManager.isConsoleEnabled() ?
            getString(R.string.developer_console_on) :
            getString(R.string.developer_console_off));
    }

    private void updateVerboseLoggingDisplay(SettingsManager settingsManager, TextView textView) {
        textView.setText(settingsManager.isVerboseLogging() ?
            getString(R.string.verbose_logging_on) :
            getString(R.string.verbose_logging_off));
    }

    private void updateThemeDisplay(SettingsManager settingsManager, TextView textView) {
        int theme = settingsManager.getThemeMode();
        String display;
        switch (theme) {
            case 0:
                display = getString(R.string.theme_system);
                break;
            case 1:
                display = getString(R.string.theme_dark);
                break;
            case 2:
                display = getString(R.string.theme_light);
                break;
            default:
                display = getString(R.string.theme_system);
                break;
        }
        textView.setText(display);
    }
    
    private void updateLanguageDisplay(TextView textView) {
        String language = LocaleManager.getLanguage(requireContext());
        String displayName = LocaleManager.getLanguageDisplayName(language);
        textView.setText(displayName);
    }
    
    private void updateRendererDisplay(SettingsManager settingsManager, TextView textView) {
        String renderer = RuntimePreference.normalizeRendererValue(settingsManager.getFnaRenderer());
        String display;
        switch (renderer) {
            case RuntimePreference.RENDERER_OPENGLES3:
                display = getString(R.string.renderer_opengles3);
                break;
            case RuntimePreference.RENDERER_OPENGL_GL4ES:
                display = getString(R.string.renderer_opengl);
                break;
            case RuntimePreference.RENDERER_VULKAN:
                display = getString(R.string.renderer_vulkan);
                break;
            case RuntimePreference.RENDERER_AUTO:
            default:
                display = getString(R.string.renderer_auto);
                break;
        }
        textView.setText(display);
    }
    
}
