package com.app.ralaunch.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.app.ralaunch.shared.AppConstants
import com.app.ralaunch.shared.util.LocaleHelper
import com.app.ralaunch.shared.util.SupportedLanguage
import com.app.ralaunch.shared.util.LocaleManager as ILocaleManager
import java.util.Locale

/**
 * 多语言管理器 - Android 实现
 * 支持中文、英文等多种语言的动态切换
 * 
 * 实现 shared 模块的 LocaleManager 接口
 */
object LocaleManager : ILocaleManager {
    private const val PREFS_NAME = AppConstants.PREFS_NAME
    private const val KEY_LANGUAGE = "app_language"

    // 使用 shared 模块的常量
    const val LANGUAGE_AUTO = LocaleHelper.LANGUAGE_AUTO
    const val LANGUAGE_ZH = LocaleHelper.LANGUAGE_ZH
    const val LANGUAGE_EN = LocaleHelper.LANGUAGE_EN
    const val LANGUAGE_RU = LocaleHelper.LANGUAGE_RU
    const val LANGUAGE_ES = LocaleHelper.LANGUAGE_ES

    private val LOCALE_RUSSIAN = Locale("ru")
    private val LOCALE_SPANISH = Locale("es")
    
    // 用于存储当前 Context（弱引用场景下使用）
    private var currentLanguage: String = LANGUAGE_AUTO

    /**
     * 获取当前设置的语言。新安装默认为英文；可在设置中切换「跟随系统 / English / 简体中文」等。
     */
    @JvmStatic
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_EN) ?: LANGUAGE_EN
    }

    /**
     * 设置语言
     */
    @JvmStatic
    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /**
     * 应用语言设置到Context
     */
    @JvmStatic
    fun applyLanguage(context: Context?): Context? {
        context ?: return null
        val language = getLanguage(context)
        return if (language == LANGUAGE_AUTO) context else updateContextLocale(context, language)
    }

    private fun updateContextLocale(context: Context, language: String): Context {
        val locale = getLocaleFromLanguage(language)
        val resources = context.resources
        val config = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            config.setLocale(locale)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            context
        }
    }

    private fun getLocaleFromLanguage(language: String): Locale = when (language) {
        LANGUAGE_ZH -> Locale.SIMPLIFIED_CHINESE
        LANGUAGE_EN -> Locale.ENGLISH
        LANGUAGE_RU -> LOCALE_RUSSIAN
        LANGUAGE_ES -> LOCALE_SPANISH
        else -> Locale.getDefault()
    }

    /**
     * 获取语言的显示名称
     */
    override fun getLanguageDisplayName(languageCode: String): String = when (languageCode) {
        LANGUAGE_AUTO -> "Follow System"
        LANGUAGE_ZH -> "简体中文"
        LANGUAGE_EN -> "English"
        LANGUAGE_RU -> "Русский"
        LANGUAGE_ES -> "Español"
        else -> languageCode
    }
    
    // JvmStatic 版本供 Java 调用
    @JvmStatic
    fun getDisplayName(language: String): String = getLanguageDisplayName(language)

    /**
     * 获取所有支持的语言列表（旧方法，兼容性保留）
     */
    @JvmStatic
    @Deprecated("使用 getSupportedLanguages(): List<SupportedLanguage>")
    fun getSupportedLanguagesArray(): Array<String> = arrayOf(
        LANGUAGE_AUTO,
        LANGUAGE_ZH,
        LANGUAGE_EN,
        LANGUAGE_RU,
        LANGUAGE_ES
    )

    // ==================== ILocaleManager 接口实现 ====================

    /**
     * 获取当前语言代码（接口实现）
     */
    override fun getCurrentLanguage(): String = currentLanguage

    /**
     * 设置语言（接口实现）
     */
    override fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
    }

    /**
     * 获取支持的语言列表（接口实现）
     */
    override fun getSupportedLanguages(): List<SupportedLanguage> {
        return SupportedLanguage.primaryLanguages()
    }
}
