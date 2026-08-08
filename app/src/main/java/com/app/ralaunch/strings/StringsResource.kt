package com.app.ralaunch.strings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.app.ralaunch.ConfigurationState
import com.app.ralaunch.strings.generated.*

/*******************************************************************************
 * RotatingArtLauncher - StringsResource
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/5)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

object StringsResource {
    var Strings by mutableStateOf<LocaleStrings>(ZhHans)

    init {
        setLanguage(ConfigurationState.language)
    }

    fun setLanguage(code: Language) {
        ConfigurationState.language = code
        val code = if (code == Language.System) fromSystemLocale() else code
        Strings = when (code) {
            Language.ZhHans -> ZhHans
            Language.En -> En
            else -> ZhHans
        }
    }

    enum class Language {
        System, ZhHans, En;
        fun string(): String {
            return when(this) {
                System -> Strings.settings.system
                ZhHans -> "简体中文"
                En -> "English"
            }
        }
    }

    fun fromSystemLocale(): Language {
        val locale = android.os.LocaleList.getDefault()[0] ?: java.util.Locale.getDefault()

        val language = locale.language.lowercase()
        val country = locale.country.lowercase()

        // 更完善的简体中文判断
        return when {
            // 使用 IETF BCP 47 语言标签
            locale.toLanguageTag().matches(Regex("zh-Hans(-.*)?")) -> Language.ZhHans
            // 中国大陆、新加坡等使用简体中文的地区
            language == "zh" && country in listOf("cn", "sg", "my") -> Language.ZhHans
            // 台湾、香港、澳门等使用繁体中文的地区返回简体（根据需求调整）
            language == "zh" -> Language.ZhHans // 默认返回简体
            language == "en" -> Language.En

            else -> Language.En
        }
    }

    fun getCurrentLanguage(): Language = ConfigurationState.language
}