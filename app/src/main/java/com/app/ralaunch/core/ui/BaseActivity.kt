package com.app.ralaunch.core.ui

import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.app.ralaunch.core.common.util.LocaleManager

/**
 * Activity 基类
 * 提供通用功能：全屏模式、系统UI隐藏、语言设置
 */
abstract class BaseActivity : AppCompatActivity() {

    companion object {
        /**
         * 隐藏系统 UI（供 Fragment 或其他地方使用）
         */
        @JvmStatic
        fun hideSystemUI(activity: android.app.Activity?) {
            activity?.let {
                WindowCompat.setDecorFitsSystemWindows(it.window, false)
                WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupFullscreen()
        super.onCreate(savedInstanceState)
        hideSystemUI()
    }

    /**
     * 设置全屏模式（在 setContentView 之前调用）
     */
    protected open fun setupFullscreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /**
     * 隐藏系统UI
     */
    protected fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    /**
     * 显示 Toast
     */
    protected open fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示长 Toast
     */
    protected fun showLongToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
