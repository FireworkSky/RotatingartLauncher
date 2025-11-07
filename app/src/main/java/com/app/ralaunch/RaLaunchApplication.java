package com.app.ralaunch;

import android.app.Application;
import android.content.Context;
import com.app.ralaunch.utils.GameDataManager;

/**
 * 应用程序全局 Application 类
 *
 * 提供全局的应用程序 Context，用于在静态方法中访问 Context
 */
public class RaLaunchApplication extends Application {
    private static Context appContext;
    private static GameDataManager gameDataManager;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();

        // 初始化 GameDataManager
        gameDataManager = new GameDataManager(appContext);

        // 初始化 ralib
        com.app.ralib.Shared.init(appContext);
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
}

