package com.app.ralaunch.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;

import java.util.Locale;

public class SettingsFragment extends Fragment {

    private OnSettingsBackListener backListener;

    // 界面控件
    private RadioGroup themeRadioGroup;
    private RadioGroup languageRadioGroup;

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
        // 返回按钮
        ImageButton backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onSettingsBack();
            }
        });

        // 初始化控件
        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        languageRadioGroup = view.findViewById(R.id.languageRadioGroup);

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
}