package com.app.ralaunch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.app.ralaunch.ConfigurationState
import com.materialkolor.rememberDynamicColorScheme

/*******************************************************************************
 * RotatingArtLauncher - ThemeMode
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/4)
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

@Composable
fun RaLaunchTheme(
    content: @Composable () -> Unit
) {
    val themeColor: Color = ConfigurationState.themeSeedColor
    val dynamicColor: Boolean = ConfigurationState.dynamicColor
    val themeMode: ConfigurationState.AppConfig.ThemeMode = ConfigurationState.themeMode

    val darkTheme = when (themeMode) {
        ConfigurationState.AppConfig.ThemeMode.LIGHT -> false
        ConfigurationState.AppConfig.ThemeMode.DARK -> true
        ConfigurationState.AppConfig.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                MaterialTheme.colorScheme
            }
        }
        else -> {
            rememberDynamicColorScheme(seedColor = themeColor, isDark = darkTheme)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}