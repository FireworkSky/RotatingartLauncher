package com.app.ralib.error;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.app.ralib.dialog.ErrorDialog;

import java.lang.ref.WeakReference;

/**
 * 全局错误处理器
 * 特性：
 * - 捕捉未处理的异常
 * - 自动显示错误弹窗
 * - 支持自定义错误处理逻辑
 * - 线程安全
 */
public class ErrorHandler {

    private static volatile ErrorHandler instance;
    private static WeakReference<FragmentActivity> currentActivity;
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
        currentActivity = new WeakReference<>(activity);
        getInstance().setupUncaughtExceptionHandler();
    }

    /**
     * 设置当前Activity
     */
    public static void setCurrentActivity(@NonNull FragmentActivity activity) {
        currentActivity = new WeakReference<>(activity);
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
        handleError("错误", throwable, false);
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
            processError("应用崩溃", throwable, true);

            // 调用原始处理器
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
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
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            ErrorDialog dialog = isFatal
                ? ErrorDialog.createFatal(title, throwable)
                : ErrorDialog.create(title, throwable);

            // 致命错误添加退出按钮
            if (isFatal) {
                dialog.setCustomAction("退出应用", () -> {
                    activity.finishAffinity();
                    System.exit(1);
                });
            }

            try {
                dialog.show(activity.getSupportFragmentManager(), "ErrorDialog");
            } catch (Exception e) {
                // 如果无法显示对话框，至少记录日志
                logError("Failed to show error dialog", e, false);
            }
        });
    }

    /**
     * 显示警告对话框
     */
    private void showWarningDialog(String title, String message) {
        mainHandler.post(() -> {
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            ErrorDialog dialog = ErrorDialog.createWarning(title, message);

            try {
                dialog.show(activity.getSupportFragmentManager(), "WarningDialog");
            } catch (Exception e) {
                logError("Failed to show warning dialog", e, false);
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
     * 获取当前Activity
     */
    private FragmentActivity getActivity() {
        return currentActivity != null ? currentActivity.get() : null;
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
        mainHandler.post(() -> {
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            ErrorDialog dialog = isFatal
                ? ErrorDialog.createFatal(title, new RuntimeException(message))
                : ErrorDialog.create(title, new RuntimeException(message));

            // 致命错误添加退出按钮
            if (isFatal) {
                dialog.setCustomAction("退出应用", () -> {
                    activity.finishAffinity();
                    System.exit(1);
                });
            }

            try {
                dialog.show(activity.getSupportFragmentManager(), "NativeErrorDialog");
            } catch (Exception e) {
                // 记录异常
                logError("Failed to show native error dialog", e, false);
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
            handleError(errorTitle != null ? errorTitle : "操作失败", e, false);
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
            handleError(errorTitle != null ? errorTitle : "操作失败", e, false);
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
