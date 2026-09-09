package com.app.ralaunch.core.common.util

/**
 * 支持的语言
 */
enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    AUTO("auto", "Follow System", "跟随系统"),
    CHINESE("zh", "Chinese", "简体中文"),
    ENGLISH("en", "English", "English"),
    RUSSIAN("ru", "Russian", "Русский"),
    SPANISH("es", "Spanish", "Español"),
    JAPANESE("ja", "Japanese", "日本語"),
    KOREAN("ko", "Korean", "한국어"),
    FRENCH("fr", "French", "Français"),
    GERMAN("de", "German", "Deutsch"),
    PORTUGUESE_BRAZIL("pt-BR", "Portuguese (Brazil)", "Português (Brasil)");

    companion object {
        fun fromCode(code: String): SupportedLanguage {
            return entries.find { it.code == code } ?: AUTO
        }

        /**
         * 获取主要支持的语言（用于 UI 显示）
         */
        fun primaryLanguages(): List<SupportedLanguage> = listOf(
            AUTO, CHINESE, ENGLISH, RUSSIAN, SPANISH, PORTUGUESE_BRAZIL
        )

        /**
         * 获取所有支持的语言
         */
        fun allLanguages(): List<SupportedLanguage> = entries.toList()
    }
}

/**
 * 语言管理契约
 */
interface AppLocaleManager {
    /**
     * 获取当前语言代码
     */
    fun getCurrentLanguage(): String

    /**
     * 设置语言
     */
    fun setLanguage(languageCode: String)

    /**
     * 获取语言显示名称
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return SupportedLanguage.fromCode(languageCode).nativeName
    }

    /**
     * 获取所有支持的语言
     */
    fun getSupportedLanguages(): List<SupportedLanguage> {
        return SupportedLanguage.primaryLanguages()
    }
}

/**
 * 语言工具函数
 */
object LocaleHelper {
    /**
     * 语言代码常量
     */
    const val LANGUAGE_AUTO = "auto"
    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_RU = "ru"
    const val LANGUAGE_ES = "es"
    const val LANGUAGE_JA = "ja"
    const val LANGUAGE_PT_BR = "pt-BR"

    /**
     * 根据语言代码获取显示名称
     */
    fun getDisplayName(code: String): String {
        return SupportedLanguage.fromCode(code).nativeName
    }

    /**
     * 检查是否为有效的语言代码
     */
    fun isValidLanguageCode(code: String): Boolean {
        return SupportedLanguage.entries.any { it.code == code }
    }
}
