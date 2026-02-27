package com.app.ralaunch.feature.crash

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.DynamicColorManager
import com.app.ralaunch.shared.core.theme.RaLaunchTheme
import com.app.ralaunch.core.common.util.DensityAdapter

/**
 * 崩溃报告 Activity - 纯 Compose 版本
 */
class CrashReportActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"
        const val EXTRA_ERROR_DETAILS = "error_details"
        const val EXTRA_EXCEPTION_CLASS = "exception_class"
        const val EXTRA_EXCEPTION_MESSAGE = "exception_message"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DensityAdapter.adapt(this, true)

        try {
            DynamicColorManager.getInstance()
                .applyCustomThemeColor(this, SettingsAccess.themeColor)
        } catch (_: Exception) { }

        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE)
        val errorDetails = intent.getStringExtra(EXTRA_ERROR_DETAILS)

        setContent {
            RaLaunchTheme {
                CrashReportScreen(
                    errorDetails = errorDetails,
                    stackTrace = stackTrace,
                    onReturnToApp = { returnToApp() },
                    onClose = { finishApp() },
                    onRestart = { restartApp() }
                )
            }
        }
    }

    private fun returnToApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            startActivity(intent)
        }
        finish()
    }

    private fun finishApp() {
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(10)
    }

    private fun restartApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(10)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishApp()
    }
}
