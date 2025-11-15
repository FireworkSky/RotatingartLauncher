package com.app.ralaunch.console;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import com.app.ralaunch.utils.AppLogger;

/**
 * 控制台管理器
 * 负责控制台UI的生命周期和配置
 */
public class ConsoleManager {
    private static final String TAG = "ConsoleManager";
    private static final String PREF_CONSOLE_ENABLED = "console_enabled";
    
    private static ConsoleManager instance;
    private final Context context;
    private final SharedPreferences preferences;
    private FloatingConsoleView consoleView;
    private final ConsoleService consoleService;

    private ConsoleManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.consoleService = ConsoleService.getInstance();
    }

    public static synchronized ConsoleManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConsoleManager(context);
        }
        return instance;
    }

    /**
     * 检查控制台是否已启用
     */
    public boolean isConsoleEnabled() {
        return preferences.getBoolean(PREF_CONSOLE_ENABLED, false);
    }

    /**
     * 设置控制台启用状态
     */
    public void setConsoleEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_CONSOLE_ENABLED, enabled).apply();
        AppLogger.info(TAG, "Console enabled: " + enabled);
    }

    /**
     * 显示控制台（如果已启用）
     * 在游戏 Activity 中直接添加到布局,不使用悬浮窗
     */
    public void showConsole(Activity activity) {
        if (!isConsoleEnabled()) {
            AppLogger.debug(TAG, "Console is disabled");
            return;
        }

        try {
            if (consoleView == null) {
                consoleView = new FloatingConsoleView(activity);
            }

            if (!consoleView.isShowing()) {
                // 获取 Activity 的根视图
                android.view.ViewGroup rootView = (android.view.ViewGroup) 
                    activity.getWindow().getDecorView().findViewById(android.R.id.content);
                
                if (rootView != null) {
                    // 直接添加到 Activity 的内容视图
                    consoleView.showInActivity(rootView);
                    AppLogger.info(TAG, "Console shown in activity");
                } else {
                    AppLogger.error(TAG, "Failed to get activity root view");
                }
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to show console: " + e.getMessage(), e);
        }
    }

    /**
     * 隐藏控制台
     */
    public void hideConsole() {
        if (consoleView != null && consoleView.isShowing()) {
            consoleView.hide();
            AppLogger.info(TAG, "Console hidden");
        }
    }
    
    /**
     * 切换控制台显示/隐藏
     */
    public void toggleConsole(Activity activity) {
        if (consoleView != null && consoleView.isShowing()) {
            hideConsole();
        } else {
            showConsole(activity);
        }
    }

    /**
     * 初始化控制台服务
     */
    public void initializeService() {
        if (isConsoleEnabled()) {
            consoleService.start();
            AppLogger.info(TAG, "Console service started");
        }
    }

    /**
     * 停止控制台服务
     */
    public void shutdown() {
        if (consoleView != null) {
            consoleView.cleanup();
            consoleView = null;
        }
        consoleService.stop();
        AppLogger.info(TAG, "Console manager shutdown");
    }

    /**
     * 获取 ConsoleService 实例
     */
    public ConsoleService getService() {
        return consoleService;
    }
}

