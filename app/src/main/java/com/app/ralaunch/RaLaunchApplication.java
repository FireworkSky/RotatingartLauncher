package com.app.ralaunch;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.app.ralaunch.controls.packs.ControlPackManager;
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
    private static ControlPackManager controlPackManager;

    @Override
    public void onCreate() {
        super.onCreate();
        

        // 必须在最开始初始化，确保所有Activity都能正确适配
        com.app.ralaunch.utils.DensityAdapter.init(this);
        
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

        // 初始化 PatchManager（不在构造函数中安装补丁，避免主线程阻塞）
        try {
            patchManager = new PatchManager(null, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PatchManager", e);
        }
        
        // 在后台线程安装内置补丁（避免 ANR）
        if (patchManager != null) {
            new Thread(() -> {
                try {
                    // 提取并安装内置MonoMod补丁（如果需要）
                    com.app.ralaunch.utils.PatchExtractor.extractPatchesIfNeeded(appContext);
                    // 安装内置补丁
                    PatchManager.installBuiltInPatches(patchManager, false);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to install built-in patches in background", e);
                }
            }, "PatchInstaller").start();
        }

        vibrationManager = new VibrationManager(appContext);

        // 初始化控件包管理器
        controlPackManager = new ControlPackManager(appContext);

        // 初始化 GamePathResolver
        GamePathResolver.initialize(appContext);

        // 设置环境变量
        try {
            Os.setenv("PACKAGE_NAME", appContext.getPackageName(), true);
            
            // 设置外部存储目录环境变量
            File externalStorageDir = appContext.getExternalFilesDir(null);
            if (externalStorageDir != null) {
                // 获取外部存储的根目录（去掉 /Android/data/com.app.ralaunch/files 部分）
                String externalStoragePath = externalStorageDir.getAbsolutePath();
                // 移除 /Android/data/包名/files 部分，获取外部存储根目录
                if (externalStoragePath.contains("/Android/data/")) {
                    externalStoragePath = externalStoragePath.substring(0, externalStoragePath.indexOf("/Android/data/"));
                }
                Os.setenv("EXTERNAL_STORAGE_DIRECTORY", externalStoragePath, true);
                Log.d(TAG, "EXTERNAL_STORAGE_DIRECTORY set to: " + externalStoragePath);
            } else {
                // 备用方案：使用 getExternalStorageDirectory()（已弃用但可用）
                File externalStorage = android.os.Environment.getExternalStorageDirectory();
                if (externalStorage != null) {
                    Os.setenv("EXTERNAL_STORAGE_DIRECTORY", externalStorage.getAbsolutePath(), true);
                    Log.d(TAG, "EXTERNAL_STORAGE_DIRECTORY set to (fallback): " + externalStorage.getAbsolutePath());
                } else {
                    Log.e(TAG, "Failed to get external storage directory");
                }
            }
        } catch (ErrnoException e) {
            throw new RuntimeException(e);
        }
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
    
    /**
     * 获取全局 ControlPackManager 实例
     *
     * @return ControlPackManager 实例
     */
    public static ControlPackManager getControlPackManager() {
        return controlPackManager;
    }
}
