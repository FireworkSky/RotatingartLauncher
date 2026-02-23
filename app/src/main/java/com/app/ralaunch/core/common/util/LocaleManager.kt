package com.app.ralaunch.core.common.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.shared.core.contract.repository.SettingsRepositoryV2
import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.shared.core.util.LocaleHelper
import com.app.ralaunch.shared.core.util.LocaleManager as ILocaleManager
import com.app.ralaunch.shared.core.util.SupportedLanguage
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import org.json.JSONObject
import java.io.File
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

    private val LOCALE_RUSSIAN = Locale.forLanguageTag("ru")
    private val LOCALE_SPANISH = Locale.forLanguageTag("es")

    private var currentLanguage: String = LANGUAGE_AUTO

    @JvmStatic
    fun getLanguage(context: Context): String {
        val language = readLanguageFromRepository(context)
        currentLanguage = language
        return language
    }

    @JvmStatic
    fun setLanguage(context: Context, language: String) {
        val normalizedLanguage = normalizeLanguageCode(language)
        persistLanguage(normalizedLanguage)
        currentLanguage = normalizedLanguage
    }

    @JvmStatic
    fun applyLanguage(context: Context?): Context? {
        context ?: return null
        val language = getLanguage(context)
        return if (language == LANGUAGE_AUTO) context else updateContextLocale(context, language)
    }

    private fun updateContextLocale(context: Context, language: String): Context {
        val locale = getLocaleFromLanguage(normalizeLanguageCode(language))
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

    private fun getLocaleFromLanguage(language: String): Locale = when (normalizeLanguageCode(language)) {
        LANGUAGE_ZH -> Locale.SIMPLIFIED_CHINESE
        LANGUAGE_EN -> Locale.ENGLISH
        LANGUAGE_RU -> LOCALE_RUSSIAN
        LANGUAGE_ES -> LOCALE_SPANISH
        else -> Locale.getDefault()
    }

    override fun getLanguageDisplayName(languageCode: String): String {
        val fallback = SupportedLanguage.primaryLanguages()
            .find { it.code == languageCode }
            ?.nativeName
            ?: languageCode

        val appContext = runCatching { RaLaunchApp.getAppContext() }.getOrNull() ?: return fallback
        val localizedContext = applyLanguage(appContext) ?: appContext

        return when (normalizeLanguageCode(languageCode)) {
            LANGUAGE_AUTO -> localizedContext.getString(R.string.language_system)
            LANGUAGE_ZH -> localizedContext.getString(R.string.language_chinese)
            LANGUAGE_EN -> localizedContext.getString(R.string.language_english)
            LANGUAGE_RU -> localizedContext.getString(R.string.language_russian)
            LANGUAGE_ES -> localizedContext.getString(R.string.language_spanish)
            else -> fallback
        }
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
        val appContext = runCatching { RaLaunchApp.getAppContext() }.getOrNull()
        val language = readLanguageFromRepository(appContext)
        currentLanguage = language
        return language
    }

    override fun setLanguage(languageCode: String) {
        val normalizedLanguage = normalizeLanguageCode(languageCode)
        persistLanguage(normalizedLanguage)
        currentLanguage = normalizedLanguage
    }

    override fun getSupportedLanguages(): List<SupportedLanguage> {
        return SupportedLanguage.primaryLanguages()
    }

    private fun readLanguageFromRepository(context: Context? = null): String {
        val languageFromRepository = runCatching {
            KoinJavaComponent.get<SettingsRepositoryV2>(SettingsRepositoryV2::class.java).Settings.language
        }.getOrNull()

        val language = languageFromRepository
            ?: context?.let { readLanguageFromSettingsFile(it) }

        return language?.takeIf { it.isNotBlank() }
            ?.let(::normalizeLanguageCode)
            ?: currentLanguage.takeIf { it.isNotBlank() }
            ?: LANGUAGE_AUTO
    }

    private fun readLanguageFromSettingsFile(context: Context): String? {
        return runCatching {
            val settingsFile = File(context.filesDir, AppConstants.Files.SETTINGS)
            if (!settingsFile.exists()) return null

            val raw = settingsFile.readText()
            if (raw.isBlank()) return null

            JSONObject(raw).opt("language") as? String
        }.getOrNull()
    }

    private fun normalizeLanguageCode(language: String?): String {
        val value = language
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return when {
            value.isBlank() -> LANGUAGE_AUTO
            value == LANGUAGE_AUTO || value == "follow system" || value == "跟随系统" -> LANGUAGE_AUTO
            value.startsWith("zh") || value == "简体中文" || value == "繁體中文" -> LANGUAGE_ZH
            value.startsWith("en") || value == "english" -> LANGUAGE_EN
            value.startsWith("ru") || value == "русский" -> LANGUAGE_RU
            value.startsWith("es") || value == "español" -> LANGUAGE_ES
            else -> LANGUAGE_AUTO
        }
    }

    private fun persistLanguage(language: String) {
        runCatching {
            val repository = KoinJavaComponent.get<SettingsRepositoryV2>(SettingsRepositoryV2::class.java)
            runBlocking {
                repository.update { this.language = normalizeLanguageCode(language) }
            }
        }
    }
}
