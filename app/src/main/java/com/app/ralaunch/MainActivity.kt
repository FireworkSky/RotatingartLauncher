package com.app.ralaunch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.ui.screen.InitializationScreen
import com.app.ralaunch.ui.screen.MainScreen
import com.app.ralaunch.ui.theme.RaLaunchTheme
import org.koin.mp.KoinPlatform.getKoin
import androidx.core.content.edit

/*******************************************************************************
 * RotatingArtLauncher - MainActivity
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

class MainActivity : ComponentActivity() {

    private lateinit var runtimeManager: IRuntimeManagerServiceV2
    private lateinit var prefs: SharedPreferences

    // 使用Compose状态来实时跟踪权限
    companion object {
        var hasStoragePermission by mutableStateOf(false)
            private set

        @SuppressLint("StaticFieldLeak")
        var context: Activity? = null
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        context = this;
        setupFullscreen()
        super.onCreate(savedInstanceState)

        runtimeManager = getKoin().get<IRuntimeManagerServiceV2>()
        prefs = getSharedPreferences(AppConstants.PREFS_NAME, 0)

        // 初始化权限状态
        hasStoragePermission = checkManageStoragePermission()

        setContent {
            var shouldShowMainScreen by remember { mutableStateOf(prefs.getBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, false)) }

            RaLaunchTheme {
                if (shouldShowMainScreen) {
                    MainScreen()
                } else {
                    InitializationScreen(
                        appContext = applicationContext,
                        prefs = prefs,
                        onNavigateToMain = { shouldShowMainScreen = true }
                    )
                }
            }
        }
    }

    /**
     * 检查是否拥有管理所有文件的权限
     */
    private fun checkManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * 当Activity从设置页面返回时，重新检查权限
     */
    override fun onResume() {
        super.onResume()
        // 更新权限状态，Compose会自动重组
        hasStoragePermission = checkManageStoragePermission()
        if (hasStoragePermission) {
            prefs.edit { putBoolean(AppConstants.InitKeys.PERMISSIONS_GRANTED, true) }
        }
        hideSystemUI()
    }

    /**
     * 设置全屏模式
     */
    private fun setupFullscreen() {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /**
     * 隐藏系统UI（状态栏与导航栏，沉浸式粘性全屏）
     *
     * 使用 WindowInsetsControllerCompat 替代已废弃的 systemUiVisibility 标志位，
     * 在 Android 15 强制 edge-to-edge 后仍可正常隐藏系统栏
     */
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}