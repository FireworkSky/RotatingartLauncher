package com.app.ralaunch.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.app.ralaunch.shared.domain.repository.SettingsRepositoryV2
import com.app.ralaunch.shared.util.LocaleHelper
import com.app.ralaunch.shared.util.LocaleManager as ILocaleManager
import com.app.ralaunch.shared.util.SupportedLanguage
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import java.util.Locale

/**
 * 多语言管理器 - Android 实现
 * 支持中文、英文等多种语言的动态切换
 *
 * 实现 shared 模块的 LocaleManager 接口
 */
object LocaleManager : ILocaleManager {

    // 使用 shared 模块的常量
    const val LANGUAGE_AUTO = LocaleHelper.LANGUAGE_AUTO
    const val LANGUAGE_ZH = LocaleHelper.LANGUAGE_ZH
    const val LANGUAGE_EN = LocaleHelper.LANGUAGE_EN
    const val LANGUAGE_RU = LocaleHelper.LANGUAGE_RU
    const val LANGUAGE_ES = LocaleHelper.LANGUAGE_ES

    private val LOCALE_RUSSIAN = Locale("ru")
    private val LOCALE_SPANISH = Locale("es")

    private var currentLanguage: String = LANGUAGE_EN

    @JvmStatic
    fun getLanguage(context: Context): String {
        val language = readLanguageFromRepository()
        currentLanguage = language
        return language
    }

    @JvmStatic
    fun setLanguage(context: Context, language: String) {
        persistLanguage(language)
        currentLanguage = language
    }

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

    override fun getLanguageDisplayName(languageCode: String): String = when (languageCode) {
        LANGUAGE_AUTO -> "Follow System"
        LANGUAGE_ZH -> "简体中文"
        LANGUAGE_EN -> "English"
        LANGUAGE_RU -> "Русский"
        LANGUAGE_ES -> "Español"
        else -> languageCode
    }

    @JvmStatic
    fun getDisplayName(language: String): String = getLanguageDisplayName(language)

    @JvmStatic
    @Deprecated("使用 getSupportedLanguages(): List<SupportedLanguage>")
    fun getSupportedLanguagesArray(): Array<String> = arrayOf(
        LANGUAGE_AUTO,
        LANGUAGE_ZH,
        LANGUAGE_EN,
        LANGUAGE_RU,
        LANGUAGE_ES
    )

    override fun getCurrentLanguage(): String {
        val language = readLanguageFromRepository()
        currentLanguage = language
        return language
    }

    override fun setLanguage(languageCode: String) {
        persistLanguage(languageCode)
        currentLanguage = languageCode
    }

    override fun getSupportedLanguages(): List<SupportedLanguage> {
        return SupportedLanguage.primaryLanguages()
    }

    private fun readLanguageFromRepository(): String {
        val language = runCatching {
            KoinJavaComponent.get<SettingsRepositoryV2>(SettingsRepositoryV2::class.java).Settings.language
        }.getOrNull()

        return language?.takeIf { it.isNotBlank() }
            ?: currentLanguage.takeIf { it.isNotBlank() }
            ?: LANGUAGE_EN
    }

    private fun persistLanguage(language: String) {
        runCatching {
            val repository = KoinJavaComponent.get<SettingsRepositoryV2>(SettingsRepositoryV2::class.java)
            runBlocking {
                repository.update { this.language = language }
            }
        }
    }
}
