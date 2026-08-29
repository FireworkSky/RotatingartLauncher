package com.app.ralaunch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.core.config.AppConfig
import com.app.ralaunch.core.model.AppSettings
import com.app.ralaunch.core.model.ThemeMode
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
    val themeColorInt by AppConfig.flowOf(AppSettings::themeColor)
        .collectAsStateWithLifecycle(0xFF6750A4.toInt())
    val dynamicColor by AppConfig.flowOf(AppSettings::dynamicColor)
        .collectAsStateWithLifecycle(true)
    val themeMode by AppConfig.flowOf(AppSettings::themeMode)
        .collectAsStateWithLifecycle(ThemeMode.LIGHT)

    val themeColor: Color = Color(themeColorInt)

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
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