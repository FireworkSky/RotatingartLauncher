package com.app.ralaunch.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.app.ralaunch.ui.screens.LauncherSetupScreen
import com.app.ralaunch.ui.screens.MainScreen
import com.app.ralaunch.ui.theme.RotatingartLauncherTheme
import com.app.ralaunch.utils.ConfigManager
import com.app.ralaunch.utils.ConfigManager.ThemeMode


class MainActivity : ComponentActivity() {

    companion object {
        private var themeSeedColor = mutableStateOf(Color(ConfigManager.getInstance().getConfig().themeSeedColor.toULong()))
        private var dynamicColor = mutableStateOf(ConfigManager.getInstance().getConfig().dynamicColor)
        private var appTheme = mutableStateOf(ConfigManager.getInstance().getConfig().themeMode)

        fun setThemeSeedColor(color: Color) {
            ConfigManager.getInstance().updateConfig {
                themeSeedColor.value = color
                it.themeSeedColor = color.value.toLong()
            }
        }
        fun getThemeSeedColor() : Color = themeSeedColor.value

        fun setDynamicColor(enable : Boolean) {
            ConfigManager.getInstance().updateConfig {
                dynamicColor.value = enable
                it.dynamicColor = enable
            }
        }
        fun getDynamicColor() : Boolean = dynamicColor.value

        fun setAppTheme(mode: ThemeMode) {
            ConfigManager.getInstance().updateConfig {
                appTheme.value = mode
                it.themeMode = mode
            }
        }
        fun getAppTheme() = appTheme.value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val showMainScreen = getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("initialized", false)

        enableEdgeToEdge()

        setContent {
            RotatingartLauncherTheme(darkTheme = (
                    if(getAppTheme() == ThemeMode.SYSTEM) isSystemInDarkTheme()
                    else getAppTheme() == ThemeMode.DARK),
                dynamicColor = getDynamicColor(), seedColor = getThemeSeedColor()) {
                if (showMainScreen) MainScreen()
                else LauncherSetupScreen({
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit { putBoolean("initialized", true) }
                    restartApp()
                })
            }
        }
    }


    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)

        // 结束当前Activity
        finish()
    }
}
