package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * 多语言管理器
 *
 * 负责管理应用的语言设置和切换:
 * - 支持中文、英文等多种语言
 * - 持久化语言选择
 * - 动态切换语言无需重启应用
 * - 跟随系统语言选项
 */
public class LocaleManager {
    private static final String TAG = "LocaleManager";
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LANGUAGE = "app_language";

    // 支持的语言
    public static final String LANGUAGE_AUTO = "auto";      // 跟随系统
    public static final String LANGUAGE_ZH = "zh";          // 简体中文
    public static final String LANGUAGE_EN = "en";          // English

    /**
     * 获取当前设置的语言
     * @param context Context
     * @return 语言代码 (auto/zh/en)
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_AUTO);
    }

    /**
     * 设置语言
     * @param context Context
     * @param language 语言代码 (auto/zh/en)
     */
    public static void setLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
        AppLogger.info(TAG, "Language set to: " + language);
    }

    /**
     * 应用语言设置到Context
     * @param context 原始Context
     * @return 应用了语言设置的新Context
     */
    public static Context applyLanguage(Context context) {
        String language = getLanguage(context);

        if (LANGUAGE_AUTO.equals(language)) {
            // 跟随系统语言
            AppLogger.debug(TAG, "Using system default language");
            return context;
        }

        return updateContextLocale(context, language);
    }

    /**
     * 更新Context的Locale
     * @param context 原始Context
     * @param language 语言代码
     * @return 更新后的Context
     */
    private static Context updateContextLocale(Context context, String language) {
        Locale locale = getLocaleFromLanguage(language);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+ 使用 LocaleList
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        } else {
            // Android 7.0 以下
            config.setLocale(locale);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            return context.createConfigurationContext(config);
        } else {
            resources.updateConfiguration(config, resources.getDisplayMetrics());
            return context;
        }
    }

    /**
     * 从语言代码获取Locale对象
     * @param language 语言代码
     * @return Locale对象
     */
    private static Locale getLocaleFromLanguage(String language) {
        switch (language) {
            case LANGUAGE_ZH:
                return Locale.SIMPLIFIED_CHINESE;
            case LANGUAGE_EN:
                return Locale.ENGLISH;
            default:
                return Locale.getDefault();
        }
    }

    /**
     * 获取语言的显示名称
     * @param language 语言代码
     * @return 显示名称
     */
    public static String getLanguageDisplayName(String language) {
        switch (language) {
            case LANGUAGE_AUTO:
                return "跟随系统 / Follow System";
            case LANGUAGE_ZH:
                return "简体中文";
            case LANGUAGE_EN:
                return "English";
            default:
                return language;
        }
    }

    /**
     * 获取所有支持的语言列表
     * @return 语言代码数组
     */
    public static String[] getSupportedLanguages() {
        return new String[]{
            LANGUAGE_AUTO,
            LANGUAGE_ZH,
            LANGUAGE_EN
        };
    }
}
