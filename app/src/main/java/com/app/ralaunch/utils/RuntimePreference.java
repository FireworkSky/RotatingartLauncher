package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.app.ralaunch.data.SettingsManager;

public final class RuntimePreference {
    private static final String TAG = "RuntimePreference";
    private static final String PREFS = "app_prefs";
    private static final String KEY_DOTNET = "dotnet_framework";

    private RuntimePreference() {}

    public static void setDotnetFramework(Context context, String value) {
        if (value == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DOTNET, value)
                .apply();
    }

    public static String getDotnetFramework(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_DOTNET, "auto");
    }

    public static void setVerboseLogging(Context context, boolean enabled) {
        SettingsManager.getInstance().setVerboseLogging(enabled);
    }

    public static boolean isVerboseLogging(Context context) {
        return SettingsManager.getInstance().isVerboseLogging();
    }
    public static String getDotnetRootPath() {
        try {
            Context appContext = com.app.ralaunch.RaLaunchApplication.getAppContext();
            if (appContext == null) {
                android.util.Log.w(TAG, "Application context is null, cannot get dotnet root path");
                return null;
            }
            
            String dotnetDir = "dotnet";
            String dotnetPath = appContext.getFilesDir().getAbsolutePath() + "/" + dotnetDir;
            android.util.Log.d(TAG, "Dotnet root path: " + dotnetPath);
            
            return dotnetPath;
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to get dotnet root path", e);
            return null;
        }
    }
    
    public static int getPreferredFrameworkMajor() {
        try {
            Context appContext = com.app.ralaunch.RaLaunchApplication.getAppContext();
            if (appContext == null) {
                android.util.Log.w(TAG, "Application context is null, using auto framework");
                return 0;
            }
            
            String framework = getDotnetFramework(appContext);
            
            switch (framework) {
                case "net6":
                    return 6;
                case "net7":
                    return 7;
                case "net8":
                    return 8;
                case "net9":
                    return 9;
                case "net10":
                    return 10;
                case "auto":
                default:
                    return 0;
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to get preferred framework major", e);
            return 0;
        }
    }
}

