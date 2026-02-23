package com.app.ralaunch.core.common

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.fragment.app.FragmentActivity
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

                val errorActivityClass = getErrorActivityClass(context) ?: return@setDefaultUncaughtExceptionHandler

                val intent = Intent(context, errorActivityClass).apply {
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
                Log.e(TAG, "Failed to show crash activity", e)
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
                "crash_logcat_truncated_prefix",
                "...[Logs truncated]..."
            )
            stackTrace = stackTrace.substring(0, maxSize - 50) + "\n$truncatedLabel"
        }

        return stackTrace
    }

    private fun buildErrorDetails(context: Context, throwable: Throwable, stackTrace: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val occurredAtLabel = getLocalizedString(context, "crash_time_occurred", "Occurred At")
        val appVersionLabel = getLocalizedString(context, "crash_app_version", "App Version")
        val unknownLabel = getLocalizedString(context, "crash_unknown", "Unknown")
        val deviceModelLabel = getLocalizedString(context, "crash_device_model", "Device Model")
        val androidLabel = getLocalizedString(context, "crash_android_version", "Android")
        val errorTypeLabel = getLocalizedString(context, "crash_error_type_label", "Type")
        val errorMessageLabel = getLocalizedString(context, "crash_error_message_label", "Message")
        val stackTraceLabel = getLocalizedString(context, "crash_stacktrace_title", "Stack Trace")

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

    @Suppress("UNCHECKED_CAST")
    private fun getErrorActivityClass(context: Context): Class<out Activity>? {
        return try {
            Class.forName("com.app.ralaunch.feature.crash.CrashReportActivity") as Class<out Activity>
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "CrashReportActivity not found", e)
            null
        }
    }

    private fun getApplicationContext(): Context? {
        return try {
            val appClass = Class.forName("com.app.ralaunch.RaLaunchApp")
            val method = appClass.getMethod("getAppContext")
            method.invoke(null) as? Context
        } catch (e: Exception) {
            null
        }
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
                val errorDialogClass = Class.forName("com.app.ralaunch.core.common.util.ErrorDialog")
                val createMethod = errorDialogClass.getMethod(
                    "create",
                    Context::class.java,
                    String::class.java,
                    Throwable::class.java,
                    Boolean::class.javaPrimitiveType
                )
                val dialog = createMethod.invoke(null, activity, title, throwable, isFatal)

                if (dialog is Dialog) {
                    dialog.show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show error dialog, using log instead", e)
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
                val errorDialogClass = Class.forName("com.app.ralaunch.core.common.util.ErrorDialog")
                val createMethod = errorDialogClass.getMethod(
                    "create",
                    Context::class.java,
                    String::class.java,
                    String::class.java
                )
                val dialog = createMethod.invoke(null, activity, title, message)

                if (dialog is Dialog) {
                    dialog.show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show warning dialog, using log instead", e)
                logError(title, RuntimeException(message), false)
            }
        }
    }

    private fun logError(title: String, throwable: Throwable, isFatal: Boolean) {
        try {
            val loggerClass = Class.forName("com.app.ralaunch.core.common.util.AppLogger")
            val errorMethod = loggerClass.getMethod("error", String::class.java, String::class.java, Throwable::class.java)
            val tag = if (isFatal) "FatalError" else "Error"
            errorMethod.invoke(null, tag, title, throwable)
        } catch (e: Exception) {
            Log.e(TAG, title, throwable)
        }
    }

    private fun getActivity(): Activity? {
        currentFragmentActivity?.get()?.let { return it }
        return currentActivity?.get()
    }

    private fun getLocalizedString(context: Context?, resId: String, defaultValue: String): String {
        if (context == null) return defaultValue

        return try {
            val rClass = Class.forName("${context.packageName}.R\$string")
            val field = rClass.getField(resId)
            val stringResId = field.getInt(null)

            var localizedContext: Context = context
            try {
                val localeManagerClass = Class.forName("com.app.ralaunch.core.common.util.LocaleManager")
                val applyLanguageMethod = localeManagerClass.getMethod("applyLanguage", Context::class.java)
                localizedContext = applyLanguageMethod.invoke(null, context) as Context
            } catch (e: Exception) {
                // LocaleManager 不可用，使用原始 Context
            }

            localizedContext.getString(stringResId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get localized string for $resId, using default: $defaultValue")
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
                val errorDialogClass = Class.forName("com.app.ralaunch.core.common.util.ErrorDialog")
                val createMethod = errorDialogClass.getMethod(
                    "create",
                    Context::class.java,
                    String::class.java,
                    String::class.java,
                    Throwable::class.java,
                    Boolean::class.javaPrimitiveType
                )
                val dialog = createMethod.invoke(null, activity, title, message, exception, isFatal)

                if (dialog is Dialog) {
                    dialog.show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show native error dialog, using log instead", e)
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
                "error_title_default",
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
                        "error_operation_failed",
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
                        "error_operation_failed",
                        "Operation failed"
                    )
                }
                handleError(title, e, false)
                defaultValue
            }
        }
    }
}
