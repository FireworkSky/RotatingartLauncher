package com.app.ralaunch.locales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.app.ralaunch.utils.ConfigManager
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "system"),
    EN("en", "English"),
    ZH("zh", "简体中文");

    companion object {
        fun fromSystemLocale(): AppLanguage {
            val systemLocale = Locale.getDefault()
            return when {
                systemLocale.language.startsWith("zh") -> ZH
                systemLocale.language.startsWith("en") -> EN
                else -> EN // 默认英语
            }
        }

        fun fromDisplayName(displayName: String): AppLanguage {
            return entries.find {
                it.displayName.equals(displayName, ignoreCase = true)
            } ?: SYSTEM
        }
    }
}

object LocaleManager {
    private var _currentLanguage: AppLanguage by mutableStateOf(AppLanguage.SYSTEM)
    private var _strings by mutableStateOf<LocaleStrings>(ZhStrings)


    val strings : LocaleStrings
        get() = _strings

    var currentLanguage: AppLanguage
        get() = _currentLanguage
        set(value) {
            _currentLanguage = value
            setStrings(value)
        }

    fun setLanguage(language: AppLanguage) {
        ConfigManager.getInstance().updateConfig { config ->
            currentLanguage = language
            config.language = language
        }
    }

    private fun setStrings(language : AppLanguage) {
        val language = if (language == AppLanguage.SYSTEM) AppLanguage.fromSystemLocale() else language
        _strings = when (language) {
            AppLanguage.ZH -> ZhStrings
            AppLanguage.EN -> EnString
            else -> EnString
        }
    }
}