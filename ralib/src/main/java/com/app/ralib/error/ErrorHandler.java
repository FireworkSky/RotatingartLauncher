package com.app.ralib.error;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;


import java.lang.ref.WeakReference;

/**
 * 全局错误处理器
 * 特性：
 * - 捕捉未处理的异常
 * - 记录错误日志
 * - 支持自定义错误处理逻辑
 * - 线程安全
 */
public class ErrorHandler {

    private static volatile ErrorHandler instance;
    private static WeakReference<FragmentActivity> currentFragmentActivity;
    private static WeakReference<Activity> currentActivity;
    private static ErrorListener globalErrorListener;
    private static boolean autoShowDialog = true;
    private static boolean logErrors = true;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread.UncaughtExceptionHandler defaultHandler;

    /**
     * 错误监听器接口
     */
    public interface ErrorListener {
        /**
         * 当发生错误时调用
         *
         * @param throwable 异常对象
         * @param isFatal   是否为致命错误（导致应用崩溃）
         */
        void onError(Throwable throwable, boolean isFatal);
    }

    private ErrorHandler() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /**
     * 获取单例实例
     */
    public static ErrorHandler getInstance() {
        if (instance == null) {
            synchronized (ErrorHandler.class) {
                if (instance == null) {
                    instance = new ErrorHandler();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化错误处理器
     *
     * @param activity 当前Activity（用于显示对话框）
     */
    public static void init(@NonNull FragmentActivity activity) {
        currentFragmentActivity = new WeakReference<>(activity);
        currentActivity = new WeakReference<>(activity);
        getInstance().setupUncaughtExceptionHandler();
    }

    /**
     * 设置当前Activity（FragmentActivity）
     */
    public static void setCurrentActivity(@NonNull FragmentActivity activity) {
        currentFragmentActivity = new WeakReference<>(activity);
        currentActivity = new WeakReference<>(activity);
    }

    /**
     * 设置当前Activity（普通Activity，用于 GameActivity 等）
     */
    public static void setCurrentActivity(@NonNull Activity activity) {
        currentActivity = new WeakReference<>(activity);
        if (activity instanceof FragmentActivity) {
            currentFragmentActivity = new WeakReference<>((FragmentActivity) activity);
        }
    }

    /**
     * 设置全局错误监听器
     */
    public static void setGlobalErrorListener(ErrorListener listener) {
        globalErrorListener = listener;
    }

    /**
     * 设置是否自动显示错误对话框
     */
    public static void setAutoShowDialog(boolean autoShow) {
        autoShowDialog = autoShow;
    }

    /**
     * 设置是否记录错误日志
     */
    public static void setLogErrors(boolean log) {
        logErrors = log;
    }

    /**
     * 手动处理错误（非致命）
     */
    public static void handleError(@NonNull Throwable throwable) {
        ErrorHandler instance = getInstance();
        Activity activity = instance.getActivity();
        String title = activity != null 
            ? instance.getLocalizedString(activity, "error_title_default", "Error")
            : "Error";
        handleError(title, throwable, false);
    }

    /**
     * 手动处理错误（带标题）
     */
    public static void handleError(@NonNull String title, @NonNull Throwable throwable) {
        handleError(title, throwable, false);
    }

    /**
     * 手动处理错误（完整参数）
     */
    public static void handleError(@NonNull String title, @NonNull Throwable throwable, boolean isFatal) {
        getInstance().processError(title, throwable, isFatal);
    }

    /**
     * 显示警告对话框
     */
    public static void showWarning(@NonNull String title, @NonNull String message) {
        getInstance().showWarningDialog(title, message);
    }

    /**
     * 设置未捕获异常处理器
     */
    private void setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // 先处理错误（记录日志并显示对话框）
            Activity activity = getActivity();
            String title = activity != null
                ? getLocalizedString(activity, "error_crash_title", "Application Crash")
                : "Application Crash";
            processError(title, throwable, true);

            // 延迟调用原始处理器，给对话框显示时间
            // 注意：对于未捕获异常，我们仍然需要让系统处理，以便生成崩溃报告
            // 但先尝试显示错误弹窗
            mainHandler.postDelayed(() -> {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                } else {
                    // 如果没有默认处理器，直接退出
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
            }, 500); // 给对话框 500ms 的显示时间
        });
    }

    /**
     * 处理错误
     */
    private void processError(String title, Throwable throwable, boolean isFatal) {
        // 记录日志
        if (logErrors) {
            logError(title, throwable, isFatal);
        }

        // 通知监听器
        if (globalErrorListener != null) {
            try {
                globalErrorListener.onError(throwable, isFatal);
            } catch (Exception e) {
                // 忽略监听器中的异常
            }
        }

        // 显示对话框
        if (autoShowDialog) {
            showErrorDialog(title, throwable, isFatal);
        }
    }

    /**
     * 显示错误对话框
     */
    private void showErrorDialog(String title, Throwable throwable, boolean isFatal) {
        mainHandler.post(() -> {
            Activity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            try {
                // 使用反射创建 ErrorDialog（ErrorDialog 在 app 模块中）
                Class<?> errorDialogClass = Class.forName("com.app.ralaunch.utils.ErrorDialog");
                java.lang.reflect.Method createMethod = errorDialogClass.getMethod(
                    "create", 
                    android.content.Context.class, 
                    String.class, 
                    Throwable.class, 
                    boolean.class
                );
                Object dialog = createMethod.invoke(null, activity, title, throwable, isFatal);
                
                // 显示对话框
                if (dialog instanceof android.app.Dialog) {
                    ((android.app.Dialog) dialog).show();
                }
            } catch (Exception e) {
                // 如果 ErrorDialog 不可用，使用 Android Log 记录
                android.util.Log.e("RALib/ErrorHandler", "Failed to show error dialog, using log instead", e);
                
                // 对于致命错误，仍然需要退出应用
                if (isFatal) {
                    activity.finishAffinity();
                    System.exit(1);
                }
            }
        });
    }

    /**
     * 显示警告对话框
     */
    private void showWarningDialog(String title, String message) {
        mainHandler.post(() -> {
            Activity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            try {
                // 使用反射创建 ErrorDialog（ErrorDialog 在 app 模块中）
                Class<?> errorDialogClass = Class.forName("com.app.ralaunch.utils.ErrorDialog");
                java.lang.reflect.Method createMethod = errorDialogClass.getMethod(
                    "create", 
                    android.content.Context.class, 
                    String.class, 
                    String.class
                );
                Object dialog = createMethod.invoke(null, activity, title, message);
                
                // 显示对话框
                if (dialog instanceof android.app.Dialog) {
                    ((android.app.Dialog) dialog).show();
                }
            } catch (Exception e) {
                // 如果 ErrorDialog 不可用，使用 Android Log 记录
                android.util.Log.e("RALib/ErrorHandler", "Failed to show warning dialog, using log instead", e);
                logError(title, new RuntimeException(message), false);
            }
        });
    }

    /**
     * 记录错误日志
     */
    private void logError(String title, Throwable throwable, boolean isFatal) {
        try {
            // 尝试使用AppLogger（如果可用）
            Class<?> loggerClass = Class.forName("com.app.ralaunch.utils.AppLogger");
            java.lang.reflect.Method errorMethod = loggerClass.getMethod("error", String.class, String.class, Throwable.class);
            String tag = isFatal ? "FatalError" : "Error";
            errorMethod.invoke(null, tag, title, throwable);
        } catch (Exception e) {
            // AppLogger不可用，使用Android Log
            android.util.Log.e("RALib/ErrorHandler", title, throwable);
        }
    }

    /**
     * 获取当前Activity（优先返回 FragmentActivity，否则返回普通 Activity）
     */
    private Activity getActivity() {
        if (currentFragmentActivity != null) {
            FragmentActivity fa = currentFragmentActivity.get();
            if (fa != null) return fa;
        }
        return currentActivity != null ? currentActivity.get() : null;
    }

    /**
     * 获取当前 FragmentActivity（如果存在）
     */
    private FragmentActivity getFragmentActivity() {
        return currentFragmentActivity != null ? currentFragmentActivity.get() : null;
    }

    /**
     * 获取本地化的字符串资源
     * 通过反射调用 Context.getString() 来获取字符串，支持多语言
     * 
     * @param context Context
     * @param resId 资源ID名称（例如 "error_title_default"）
     * @param defaultValue 默认值（如果获取失败时返回）
     * @return 本地化的字符串
     */
    private String getLocalizedString(android.content.Context context, String resId, String defaultValue) {
        if (context == null) {
            return defaultValue;
        }

        try {
            // 获取 R.string 类
            Class<?> rClass = Class.forName(context.getPackageName() + ".R$string");
            java.lang.reflect.Field field = rClass.getField(resId);
            int stringResId = field.getInt(null);

            // 调用 getString 方法获取本地化字符串
            // 尝试应用语言设置（如果 LocaleManager 可用）
            android.content.Context localizedContext = context;
            try {
                Class<?> localeManagerClass = Class.forName("com.app.ralaunch.utils.LocaleManager");
                java.lang.reflect.Method applyLanguageMethod = localeManagerClass.getMethod("applyLanguage", android.content.Context.class);
                localizedContext = (android.content.Context) applyLanguageMethod.invoke(null, context);
            } catch (Exception e) {
                // LocaleManager 不可用，使用原始 Context
            }

            return localizedContext.getString(stringResId);
        } catch (Exception e) {
            // 如果获取失败，返回默认值
            android.util.Log.w("RALib/ErrorHandler", "Failed to get localized string for " + resId + ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Show native error (called from C/C++ via JNI)
     *
     * @param title Error title
     * @param message Error message
     * @param isFatal Whether this is a fatal error
     */
    public static void showNativeError(String title, String message, boolean isFatal) {
        getInstance().showNativeErrorDialog(title, message, isFatal);
    }

    /**
     * Show native error dialog implementation
     */
    private void showNativeErrorDialog(String title, String message, boolean isFatal) {
        RuntimeException exception = new RuntimeException(message);
        logError(title, exception, isFatal);
        
        mainHandler.post(() -> {
            Activity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                // 对于致命错误，仍然需要退出应用
                if (isFatal && activity != null) {
                    activity.finishAffinity();
                    System.exit(1);
                }
                return;
            }

            try {
                // 使用反射创建 ErrorDialog（ErrorDialog 在 app 模块中）
                Class<?> errorDialogClass = Class.forName("com.app.ralaunch.utils.ErrorDialog");
                java.lang.reflect.Method createMethod = errorDialogClass.getMethod(
                    "create", 
                    android.content.Context.class, 
                    String.class, 
                    String.class, 
                    Throwable.class, 
                    boolean.class
                );
                Object dialog = createMethod.invoke(null, activity, title, message, exception, isFatal);
                
                // 显示对话框
                if (dialog instanceof android.app.Dialog) {
                    ((android.app.Dialog) dialog).show();
                }
            } catch (Exception e) {
                // 如果 ErrorDialog 不可用，使用 Android Log 记录
                android.util.Log.e("RALib/ErrorHandler", "Failed to show native error dialog, using log instead", e);
                
                // 对于致命错误，仍然需要退出应用
                if (isFatal) {
                    activity.finishAffinity();
                    System.exit(1);
                }
            }
        });
    }

    /**
     * Try-Catch辅助方法
     */
    public static void tryCatch(Runnable action) {
        tryCatch(action, null);
    }

    /**
     * Try-Catch辅助方法（带错误标题）
     */
    public static void tryCatch(Runnable action, String errorTitle) {
        try {
            action.run();
        } catch (Exception e) {
            if (errorTitle != null) {
                handleError(errorTitle, e, false);
            } else {
                Activity activity = getInstance().getActivity();
                String defaultTitle = activity != null
                    ? getInstance().getLocalizedString(activity, "error_operation_failed", "Operation failed")
                    : "Operation failed";
                handleError(defaultTitle, e, false);
            }
        }
    }

    /**
     * Try-Catch辅助方法（带返回值）
     */
    public static <T> T tryCatch(ErrorCallable<T> callable, T defaultValue) {
        return tryCatch(callable, defaultValue, null);
    }

    /**
     * Try-Catch辅助方法（完整参数）
     */
    public static <T> T tryCatch(ErrorCallable<T> callable, T defaultValue, String errorTitle) {
        try {
            return callable.call();
        } catch (Exception e) {
            if (errorTitle != null) {
                handleError(errorTitle, e, false);
            } else {
                Activity activity = getInstance().getActivity();
                String defaultTitle = activity != null
                    ? getInstance().getLocalizedString(activity, "error_operation_failed", "Operation failed")
                    : "Operation failed";
                handleError(defaultTitle, e, false);
            }
            return defaultValue;
        }
    }

    /**
     * 可抛出异常的Callable接口
     */
    public interface ErrorCallable<T> {
        T call() throws Exception;
    }
}
