package com.app.ralaunch.core.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.common.util.ErrorDialog
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.feature.crash.ui.CrashReportActivity
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局错误处理器
 */
class ErrorHandler private constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    /**
     * 错误监听器接口
     */
    fun interface ErrorListener {
        fun onError(throwable: Throwable, isFatal: Boolean)
    }

    private fun setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val activity = getActivity()
            val context: Context? = activity ?: getApplicationContext()

            if (context == null) {
                defaultHandler?.uncaughtException(Thread.currentThread(), throwable)
                    ?: run {
                        Process.killProcess(Process.myPid())
                        System.exit(1)
                    }
                return@setDefaultUncaughtExceptionHandler
            }

            try {
                val stackTrace = getStackTrace(context, throwable)
                val errorDetails = buildErrorDetails(context, throwable, stackTrace)

                val intent = Intent(context, CrashReportActivity::class.java).apply {
                    putExtra("stack_trace", stackTrace)
                    putExtra("error_details", errorDetails)
                    putExtra("exception_class", throwable.javaClass.name)
                    putExtra("exception_message", throwable.message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }

                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                }

                context.startActivity(intent)
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to show crash activity", e)
                defaultHandler?.uncaughtException(Thread.currentThread(), throwable)
                    ?: run {
                        Process.killProcess(Process.myPid())
                        System.exit(1)
                    }
            }

            killProcess()
        }
    }

    private fun getStackTrace(context: Context, throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        var stackTrace = sw.toString()

        val maxSize = 100000
        if (stackTrace.length > maxSize) {
            val truncatedLabel = getLocalizedString(
                context,
                R.string.crash_logcat_truncated_prefix,
                "...[Logs truncated]..."
            )
            stackTrace = stackTrace.substring(0, maxSize - 50) + "\n$truncatedLabel"
        }

        return stackTrace
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

            try {
                val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                append("$appVersionLabel: $versionName\n")
            } catch (e: Exception) {
                append("$appVersionLabel: $unknownLabel\n")
            }

            append("$deviceModelLabel: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("$androidLabel: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\n")
            append("$errorTypeLabel: ${throwable.javaClass.name}\n")
            throwable.message?.let { append("$errorMessageLabel: $it\n") }
            append("\n$stackTraceLabel:\n$stackTrace")
        }
    }

    private fun getApplicationContext(): Context? {
        return runCatching { RaLaunchApp.getAppContext() }.getOrNull()
    }

    private fun killProcess() {
        Process.killProcess(Process.myPid())
        System.exit(10)
    }

    private fun processError(title: String, throwable: Throwable, isFatal: Boolean) {
        if (logErrors) {
            logError(title, throwable, isFatal)
        }

        globalErrorListener?.runCatching { onError(throwable, isFatal) }

        if (autoShowDialog) {
            showErrorDialog(title, throwable, isFatal)
        }
    }

    private fun showErrorDialog(title: String, throwable: Throwable, isFatal: Boolean) {
        mainHandler.post {
            val activity = getActivity() ?: return@post
            if (activity.isFinishing || activity.isDestroyed) return@post

            try {
                ErrorDialog.create(activity, title, throwable, isFatal).show()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to show error dialog, using log instead", e)
                if (isFatal) {
                    activity.finishAffinity()
                    System.exit(1)
                }
            }
        }
    }

    private fun showWarningDialog(title: String, message: String) {
        mainHandler.post {
            val activity = getActivity() ?: return@post
            if (activity.isFinishing || activity.isDestroyed) return@post

            try {
                ErrorDialog.create(activity, title, message).show()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to show warning dialog, using log instead", e)
                logError(title, RuntimeException(message), false)
            }
        }
    }

    private fun logError(title: String, throwable: Throwable, isFatal: Boolean) {
        try {
            val tag = if (isFatal) "FatalError" else "Error"
            AppLog.e(tag, title, throwable)
        } catch (e: Exception) {
            AppLog.e(TAG, title, throwable)
        }
    }

    private fun getActivity(): Activity? {
        currentFragmentActivity?.get()?.let { return it }
        return currentActivity?.get()
    }

    private fun getLocalizedString(context: Context?, @StringRes resId: Int, defaultValue: String): String {
        if (context == null) return defaultValue

        return try {
            val localizedContext = LocaleManager.applyLanguage(context) ?: context
            localizedContext.getString(resId)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to get localized string for $resId, using default: $defaultValue")
            defaultValue
        }
    }

    private fun showNativeErrorDialog(title: String, message: String, isFatal: Boolean) {
        val exception = RuntimeException(message)
        logError(title, exception, isFatal)

        mainHandler.post {
            val activity = getActivity()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                if (isFatal && activity != null) {
                    activity.finishAffinity()
                    System.exit(1)
                }
                return@post
            }

            try {
                ErrorDialog.create(activity, title, message, exception, isFatal).show()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to show native error dialog, using log instead", e)
                if (isFatal) {
                    activity.finishAffinity()
                    System.exit(1)
                }
            }
        }
    }

    /**
     * 可抛出异常的Callable接口
     */
    fun interface ErrorCallable<T> {
        @Throws(Exception::class)
        fun call(): T
    }

    companion object {
        private const val TAG = "ErrorHandler"

        @Volatile
        private var instance: ErrorHandler? = null

        private var currentFragmentActivity: WeakReference<FragmentActivity>? = null
        private var currentActivity: WeakReference<Activity>? = null
        private var globalErrorListener: ErrorListener? = null
        private var autoShowDialog = true
        private var logErrors = true

        @JvmStatic
        fun getInstance(): ErrorHandler {
            return instance ?: synchronized(this) {
                instance ?: ErrorHandler().also { instance = it }
            }
        }

        @JvmStatic
        fun init(activity: FragmentActivity) {
            currentFragmentActivity = WeakReference(activity)
            currentActivity = WeakReference(activity)
            getInstance().setupUncaughtExceptionHandler()
        }

        @JvmStatic
        fun setCurrentActivity(activity: FragmentActivity) {
            currentFragmentActivity = WeakReference(activity)
            currentActivity = WeakReference(activity)
        }

        @JvmStatic
        fun setCurrentActivity(activity: Activity) {
            currentActivity = WeakReference(activity)
            if (activity is FragmentActivity) {
                currentFragmentActivity = WeakReference(activity)
            }
        }

        @JvmStatic
        fun setGlobalErrorListener(listener: ErrorListener?) {
            globalErrorListener = listener
        }

        @JvmStatic
        fun setAutoShowDialog(autoShow: Boolean) {
            autoShowDialog = autoShow
        }

        @JvmStatic
        fun setLogErrors(log: Boolean) {
            logErrors = log
        }

        @JvmStatic
        fun handleError(throwable: Throwable) {
            val inst = getInstance()
            val activity = inst.getActivity()
            val title = inst.getLocalizedString(
                activity ?: inst.getApplicationContext(),
                R.string.error_title_default,
                "Error"
            )
            handleError(title, throwable, false)
        }

        @JvmStatic
        fun handleError(title: String, throwable: Throwable) {
            handleError(title, throwable, false)
        }

        @JvmStatic
        fun handleError(title: String, throwable: Throwable, isFatal: Boolean) {
            getInstance().processError(title, throwable, isFatal)
        }

        @JvmStatic
        fun showWarning(title: String, message: String) {
            getInstance().showWarningDialog(title, message)
        }

        @JvmStatic
        fun showNativeError(title: String, message: String, isFatal: Boolean) {
            getInstance().showNativeErrorDialog(title, message, isFatal)
        }

        @JvmStatic
        fun tryCatch(action: Runnable) {
            tryCatch(action, null)
        }

        @JvmStatic
        fun tryCatch(action: Runnable, errorTitle: String?) {
            try {
                action.run()
            } catch (e: Exception) {
                val title = errorTitle ?: run {
                    val inst = getInstance()
                    val activity = inst.getActivity()
                    inst.getLocalizedString(
                        activity ?: inst.getApplicationContext(),
                        R.string.error_operation_failed,
                        "Operation failed"
                    )
                }
                handleError(title, e, false)
            }
        }

        @JvmStatic
        fun <T> tryCatch(callable: ErrorCallable<T>, defaultValue: T): T {
            return tryCatch(callable, defaultValue, null)
        }

        @JvmStatic
        fun <T> tryCatch(callable: ErrorCallable<T>, defaultValue: T, errorTitle: String?): T {
            return try {
                callable.call()
            } catch (e: Exception) {
                val title = errorTitle ?: run {
                    val inst = getInstance()
                    val activity = inst.getActivity()
                    inst.getLocalizedString(
                        activity ?: inst.getApplicationContext(),
                        R.string.error_operation_failed,
                        "Operation failed"
                    )
                }
                handleError(title, e, false)
                defaultValue
            }
        }
    }
}
