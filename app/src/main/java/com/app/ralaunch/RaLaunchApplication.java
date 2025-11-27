package com.app.ralaunch;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import com.app.ralaunch.data.GameDataManager;
import com.app.ralaunch.manager.VibrationManager;
import com.app.ralaunch.utils.GamePathResolver;
import com.app.ralaunch.utils.LocaleManager;
import com.app.ralib.patch.PatchManager;
import com.kyant.fishnet.Fishnet;
import java.io.File;

/**
 * 应用程序全局 Application 类
 *
 * 提供全局的应用程序 Context,用于在静态方法中访问 Context
 */
public class RaLaunchApplication extends Application {
    private static final String TAG = "RaLaunchApplication";

    private static Context appContext;
    private static GameDataManager gameDataManager;
    private static PatchManager patchManager;
    private static VibrationManager vibrationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 在应用启动时应用主题设置，确保所有Activity都使用正确的主题
        com.app.ralaunch.data.SettingsManager settingsManager = 
            com.app.ralaunch.data.SettingsManager.getInstance(this);
        int themeMode = settingsManager.getThemeMode();
        
        switch (themeMode) {
            case 0: // 跟随系统
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1: // 深色模式
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2: // 浅色模式
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
        appContext = getApplicationContext();

        // 初始化 Fishnet 崩溃捕捉
        File logDir = new File(getFilesDir(), "crash_logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        Fishnet.init(appContext, logDir.getAbsolutePath());

        // 初始化 ralib
        com.app.ralib.Shared.init(appContext);

        // 初始化 GameDataManager
        gameDataManager = new GameDataManager(appContext);

        // 初始化 PatchManager
        try {
            patchManager = new PatchManager(null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PatchManager", e);
        }

        vibrationManager = new VibrationManager(appContext);

        // 初始化 GamePathResolver
        GamePathResolver.initialize(appContext);
    }

    @Override
    protected void attachBaseContext(Context base) {
        // 应用语言设置
        super.attachBaseContext(LocaleManager.applyLanguage(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 当系统语言改变时重新应用语言设置
        LocaleManager.applyLanguage(this);
    }

    /**
     * 获取全局应用程序 Context
     *
     * @return 应用程序 Context
     */
    public static Context getAppContext() {
        return appContext;
    }

    /**
     * 获取全局 GameDataManager 实例
     *
     * @return GameDataManager 实例
     */
    public static GameDataManager getGameDataManager() {
        return gameDataManager;
    }

    /**
     * 获取全局 PatchManager 实例
     *
     * @return PatchManager 实例
     */
    public static PatchManager getPatchManager() {
        return patchManager;
    }

    /**
     * 获取全局 VibrationManager 实例
     *
     * @return VibrationManager 实例
     */
    public static VibrationManager getVibrationManager() {
        return vibrationManager;
    }
}
