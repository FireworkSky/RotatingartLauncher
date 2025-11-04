package com.app.ralaunch.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;
import com.app.ralaunch.activity.DebugActivity;
import com.app.ralaunch.utils.RuntimePreference;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

/**
 * 设置Fragment
 * 
 * 提供应用设置功能：
 * - 主题切换（浅色/深色/跟随系统）
 * - 语言切换（中文/英文）
 * - 设置持久化保存
 * - 实时应用设置更改
 * 
 * 设置保存在 SharedPreferences 中
 */
public class SettingsFragment extends Fragment {

    private OnSettingsBackListener backListener;

    // 界面控件
    private RadioGroup themeRadioGroup;
    private RadioGroup languageRadioGroup;
    private RadioGroup architectureRadioGroup;
    private RadioGroup rendererRadioGroup;
    private SwitchCompat switchVerboseLogging;
    private SwitchCompat switchPerformanceMonitor;
    private Button debugButton;

    // 设置键值
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_LANGUAGE = "app_language";

    // 主题模式
    private static final int THEME_SYSTEM = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_LIGHT = 2;

    // 语言模式
    private static final int LANGUAGE_SYSTEM = 0;
    private static final int LANGUAGE_ENGLISH = 1;
    private static final int LANGUAGE_CHINESE = 2;

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
        loadSettings();
        return view;
    }

    private void setupUI(View view) {
        // 工具栏返回按钮
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (backListener != null) {
                backListener.onSettingsBack();
            }
        });

        // 初始化控件
        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        languageRadioGroup = view.findViewById(R.id.languageRadioGroup);
        architectureRadioGroup = view.findViewById(R.id.architectureRadioGroup);
        rendererRadioGroup = view.findViewById(R.id.rendererRadioGroup);
        switchVerboseLogging = view.findViewById(R.id.switchVerboseLogging);
        switchPerformanceMonitor = view.findViewById(R.id.switchPerformanceMonitor);
        View verboseLoggingContainer = view.findViewById(R.id.verboseLoggingContainer);
        View performanceMonitorContainer = view.findViewById(R.id.performanceMonitorContainer);
        debugButton = view.findViewById(R.id.debugButton);

        // 主题选择监听
        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int themeMode;
            if (checkedId == R.id.themeSystem) {
                themeMode = THEME_SYSTEM;
            } else if (checkedId == R.id.themeDark) {
                themeMode = THEME_DARK;
            } else {
                themeMode = THEME_LIGHT;
            }
            saveThemeSetting(themeMode);
            applyTheme(themeMode);
        });

        // 语言选择监听
        languageRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int languageMode;
            if (checkedId == R.id.languageSystem) {
                languageMode = LANGUAGE_SYSTEM;
            } else if (checkedId == R.id.languageEnglish) {
                languageMode = LANGUAGE_ENGLISH;
            } else {
                languageMode = LANGUAGE_CHINESE;
            }
            saveLanguageSetting(languageMode);
            applyLanguage(languageMode);
        });

        // 架构选择监听
        architectureRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String architecture;
            if (checkedId == R.id.archAuto) {
                architecture = RuntimePreference.ARCH_AUTO;
            } else if (checkedId == R.id.archArm64) {
                architecture = RuntimePreference.ARCH_ARM64;
            } else {
                architecture = RuntimePreference.ARCH_X86_64;
            }
            RuntimePreference.setArchitecture(requireContext(), architecture);
            Toast.makeText(requireContext(), "CPU 架构已设置为：" + getArchitectureName(architecture), Toast.LENGTH_SHORT).show();
        });

        // 渲染器选择监听
        rendererRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String renderer;
            if (checkedId == R.id.rendererAuto) {
                renderer = RuntimePreference.RENDERER_AUTO;
            } else if (checkedId == R.id.rendererOpenGLES3) {
                renderer = RuntimePreference.RENDERER_OPENGLES3;
            } else if (checkedId == R.id.rendererOpenGL) {
                renderer = RuntimePreference.RENDERER_OPENGL_GL4ES;
            } else if (checkedId == R.id.rendererVulkan) {
                renderer = RuntimePreference.RENDERER_VULKAN;
            } else {
                renderer = RuntimePreference.RENDERER_AUTO; // 默认自动选择
            }
            RuntimePreference.setRenderer(requireContext(), renderer);

            String effectiveRenderer = RuntimePreference.getEffectiveRenderer(requireContext());
            String message = "FNA 渲染器：" + getRendererName(renderer);
            if (!renderer.equals(effectiveRenderer)) {
                message += " (实际: " + getRendererName(effectiveRenderer) + ")";
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // 详细日志开关监听
        switchVerboseLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RuntimePreference.setVerboseLogging(requireContext(), isChecked);
            String message = isChecked ?
                "已启用详细日志，重启应用后生效" :
                "已禁用详细日志";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // 点击整个容器时切换开关
        verboseLoggingContainer.setOnClickListener(v -> {
            switchVerboseLogging.setChecked(!switchVerboseLogging.isChecked());
        });

        // 性能监控开关监听
        switchPerformanceMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RuntimePreference.setPerformanceMonitorEnabled(requireContext(), isChecked);
            String message = isChecked ?
                "已启用性能监控，进入游戏后生效" :
                "已禁用性能监控";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // 点击整个容器时切换开关
        if (performanceMonitorContainer != null) {
            performanceMonitorContainer.setOnClickListener(v -> {
                switchPerformanceMonitor.setChecked(!switchPerformanceMonitor.isChecked());
            });
        }

        // 调试按钮监听
        debugButton.setOnClickListener(v -> {
            // 启动调试活动
             Intent intent = new Intent(getActivity(), DebugActivity.class);
             startActivity(intent);
        });
    }

    private void loadSettings() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 加载主题设置
        int themeMode = prefs.getInt(KEY_THEME, THEME_SYSTEM);
        switch (themeMode) {
            case THEME_SYSTEM:
                themeRadioGroup.check(R.id.themeSystem);
                break;
            case THEME_DARK:
                themeRadioGroup.check(R.id.themeDark);
                break;
            case THEME_LIGHT:
                themeRadioGroup.check(R.id.themeLight);
                break;
        }

        // 加载语言设置
        int languageMode = prefs.getInt(KEY_LANGUAGE, LANGUAGE_SYSTEM);
        switch (languageMode) {
            case LANGUAGE_SYSTEM:
                languageRadioGroup.check(R.id.languageSystem);
                break;
            case LANGUAGE_ENGLISH:
                languageRadioGroup.check(R.id.languageEnglish);
                break;
            case LANGUAGE_CHINESE:
                languageRadioGroup.check(R.id.languageChinese);
                break;
        }

        // 加载架构设置
        String architecture = RuntimePreference.getArchitecture(requireContext());
        switch (architecture) {
            case RuntimePreference.ARCH_AUTO:
                architectureRadioGroup.check(R.id.archAuto);
                break;
            case RuntimePreference.ARCH_ARM64:
                architectureRadioGroup.check(R.id.archArm64);
                break;
            case RuntimePreference.ARCH_X86_64:
                architectureRadioGroup.check(R.id.archX86_64);
                break;
            default:
                architectureRadioGroup.check(R.id.archArm64); // 默认 ARM64
                break;
        }

        // 加载渲染器设置
        String renderer = RuntimePreference.getRenderer(requireContext());
        switch (renderer) {
            case RuntimePreference.RENDERER_AUTO:
                rendererRadioGroup.check(R.id.rendererAuto);
                break;
            case RuntimePreference.RENDERER_OPENGLES3:
                rendererRadioGroup.check(R.id.rendererOpenGLES3);
                break;
            case RuntimePreference.RENDERER_OPENGL_GL4ES:
                rendererRadioGroup.check(R.id.rendererOpenGL);
                break;
            case RuntimePreference.RENDERER_VULKAN:
                rendererRadioGroup.check(R.id.rendererVulkan);
                break;
            default:
                rendererRadioGroup.check(R.id.rendererAuto); // 默认自动
                break;
        }

        // 加载详细日志设置
        boolean verboseLogging = RuntimePreference.isVerboseLogging(requireContext());
        switchVerboseLogging.setChecked(verboseLogging);

        // 加载性能监控设置
        boolean performanceMonitor = RuntimePreference.isPerformanceMonitorEnabled(requireContext());
        switchPerformanceMonitor.setChecked(performanceMonitor);
    }

    private void saveThemeSetting(int themeMode) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_THEME, themeMode);
        editor.apply();
    }

    private void saveLanguageSetting(int languageMode) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_LANGUAGE, languageMode);
        editor.apply();
    }

    private void applyTheme(int themeMode) {
        switch (themeMode) {
            case THEME_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    private void applyLanguage(int languageMode) {
        Locale locale;
        switch (languageMode) {
            case LANGUAGE_SYSTEM:
                locale = Locale.getDefault();
                break;
            case LANGUAGE_ENGLISH:
                locale = Locale.ENGLISH;
                break;
            case LANGUAGE_CHINESE:
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
            default:
                locale = Locale.getDefault();
        }

        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requireActivity().createConfigurationContext(configuration);
        }

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());


    }

    // 静态方法：在应用启动时应用保存的设置
    public static void applySavedSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 应用主题
        int themeMode = prefs.getInt(KEY_THEME, THEME_SYSTEM);
        switch (themeMode) {
            case THEME_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }

        // 应用语言
        int languageMode = prefs.getInt(KEY_LANGUAGE, LANGUAGE_SYSTEM);
        Locale locale;
        switch (languageMode) {
            case LANGUAGE_SYSTEM:
                locale = Locale.getDefault();
                break;
            case LANGUAGE_ENGLISH:
                locale = Locale.ENGLISH;
                break;
            case LANGUAGE_CHINESE:
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
            default:
                locale = Locale.getDefault();
        }

        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration);
        }

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }

    /**
     * 获取架构名称
     */
    private String getArchitectureName(String architecture) {
        switch (architecture) {
            case RuntimePreference.ARCH_AUTO:
                return "自动检测";
            case RuntimePreference.ARCH_ARM64:
                return "ARM64";
            case RuntimePreference.ARCH_X86_64:
                return "x86_64";
            default:
                return "未知";
        }
    }

    /**
     * 获取渲染器名称
     */
    private String getRendererName(String renderer) {
        switch (renderer) {
            case RuntimePreference.RENDERER_AUTO:
                return "自动选择";
            case RuntimePreference.RENDERER_OPENGLES3:
                return "OpenGL ES 3（原生）";
            case RuntimePreference.RENDERER_OPENGL_GL4ES:
                return "OpenGL (gl4es)";
            case RuntimePreference.RENDERER_VULKAN:
                return "Vulkan";
            default:
                return "未知";
        }
    }
}