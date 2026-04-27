package com.app.ralaunch.core.error

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Process
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.error.ui.CrashReportActivity
import com.app.ralaunch.core.logging.AppLog
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash-only process error handler.
 */
object ErrorHandler {
    private const val TAG = "ErrorHandler"
    private const val MAX_STACK_TRACE_SIZE = 100000

    @Volatile
    private var installed = false
    private var currentActivity: WeakReference<Activity>? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    @JvmStatic
    fun init(activity: Activity) {
        currentActivity = WeakReference(activity)

        if (installed) return
        synchronized(this) {
            if (installed) return
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                handleUncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        val activity = currentActivity?.get()
        val context = activity ?: getApplicationContext()

        if (context == null) {
            delegateOrKill(thread, throwable)
            return
        }

        try {
            val stackTrace = getStackTrace(context, throwable)
            CrashReportActivity.launch(
                context = context,
                stackTrace = stackTrace,
                errorDetails = buildErrorDetails(context, throwable, stackTrace),
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch crash report", e)
            delegateOrKill(thread, throwable)
            return
        }

        killProcess()
    }

    private fun getStackTrace(context: Context, throwable: Throwable): String {
        val stackTrace = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()

        if (stackTrace.length <= MAX_STACK_TRACE_SIZE) return stackTrace

        val truncatedLabel = getLocalizedString(
            context,
            R.string.crash_logcat_truncated_prefix,
            "...[Logs truncated]..."
        )
        return stackTrace.substring(0, MAX_STACK_TRACE_SIZE - 50) + "\n$truncatedLabel"
    }

    private fun buildErrorDetails(context: Context, throwable: Throwable, stackTrace: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val occurredAtLabel = getLocalizedString(context, R.string.crash_time_occurred, "Occurred At")
        val appVersionLabel = getLocalizedString(context, R.string.crash_app_version, "App Version")
        val unknownLabel = getLocalizedString(context, R.string.crash_unknown, "Unknown")
        val deviceModelLabel = getLocalizedString(context, R.string.crash_device_model, "Device Model")
        val androidLabel = getLocalizedString(context, R.string.crash_android_version, "Android")
        val errorTypeLabel = getLocalizedString(context, R.string.crash_error_type_label, "Type")
        val errorMessageLabel = getLocalizedString(context, R.string.crash_error_message_label, "Message")
        val stackTraceLabel = getLocalizedString(context, R.string.crash_stacktrace_title, "Stack Trace")

        return buildString {
            append("$occurredAtLabel: ${sdf.format(Date())}\n\n")

            val versionName = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull()
            append("$appVersionLabel: ${versionName ?: unknownLabel}\n")

            append("$deviceModelLabel: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("$androidLabel: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\n")
            append("$errorTypeLabel: ${throwable.javaClass.name}\n")
            throwable.message?.let { append("$errorMessageLabel: $it\n") }
            append("\n$stackTraceLabel:\n$stackTrace")
        }
    }

    private fun getLocalizedString(context: Context, resId: Int, defaultValue: String): String {
        return runCatching {
            (LocaleManager.applyLanguage(context) ?: context).getString(resId)
        }.getOrElse {
            AppLog.w(TAG, "Failed to get localized string for $resId, using default: $defaultValue")
            defaultValue
        }
    }

    private fun getApplicationContext(): Context? {
        return runCatching { RaLaunchApp.getAppContext() }.getOrNull()
    }

    private fun delegateOrKill(thread: Thread, throwable: Throwable) {
        defaultHandler?.uncaughtException(thread, throwable) ?: killProcess()
    }

    private fun killProcess() {
        Process.killProcess(Process.myPid())
        System.exit(10)
    }
}
