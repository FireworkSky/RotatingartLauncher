package com.app.ralaunch.data;

import android.content.Context;

import com.app.ralaunch.utils.AppLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 设置管理器 - 使用 JSON 文件保存所有应用设置
 * 
 * 提供统一的设置存取接口，所有设置保存在 settings.json 文件中
 * 
 * 设置分类：
 * - 主题设置（theme_mode, app_language）
 * - 运行时设置（dotnet_framework, runtime_architecture）
 * - 开发者设置（verbose_logging）
 * - FNA设置（fna_renderer）
 */
public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String SETTINGS_FILE = "settings.json";
    
    private final Context context;
    private JSONObject settings;
    private final File settingsFile;
    
    // 单例
    private static SettingsManager instance;
    
    // 设置键常量
    public static class Keys {
        // 主题设置
        public static final String THEME_MODE = "theme_mode";
        public static final String APP_LANGUAGE = "app_language";
        public static final String THEME_COLOR = "theme_color";
        public static final String BACKGROUND_TYPE = "background_type"; // "default", "color", "image", "video"
        public static final String BACKGROUND_COLOR = "background_color"; // 颜色值（int）
        public static final String BACKGROUND_IMAGE_PATH = "background_image_path"; // 图片路径
        public static final String BACKGROUND_VIDEO_PATH = "background_video_path"; // 视频路径
        public static final String BACKGROUND_OPACITY = "background_opacity"; // 背景透明度 (0-100)

        // 运行时设置
        public static final String DOTNET_FRAMEWORK = "dotnet_framework";
        public static final String RUNTIME_ARCHITECTURE = "runtime_architecture";

        // 开发者设置
        public static final String VERBOSE_LOGGING = "verbose_logging";
        // FNA设置
        public static final String FNA_RENDERER = "fna_renderer";

        // CoreCLR 运行时配置
        public static final String CORECLR_SERVER_GC = "coreclr_server_gc";
        public static final String CORECLR_CONCURRENT_GC = "coreclr_concurrent_gc";
        public static final String CORECLR_GC_HEAP_COUNT = "coreclr_gc_heap_count";
        public static final String CORECLR_TIERED_COMPILATION = "coreclr_tiered_compilation";
        public static final String CORECLR_QUICK_JIT = "coreclr_quick_jit";
        public static final String CORECLR_JIT_OPTIMIZE_TYPE = "coreclr_jit_optimize_type";
        public static final String CORECLR_RETAIN_VM = "coreclr_retain_vm";
    }
    
    // 默认值
    public static class Defaults {
        public static final int THEME_MODE = 2; // 亮色主题
        public static final int APP_LANGUAGE = 0; // 跟随系统
        public static final int THEME_COLOR = 0xFF4CAF50; // 默认绿色
        public static final String BACKGROUND_TYPE = "default"; // 默认背景
        public static final int BACKGROUND_COLOR = 0xFFFFFFFF; // 默认白色
        public static final String BACKGROUND_IMAGE_PATH = ""; // 默认无图片
        public static final String BACKGROUND_VIDEO_PATH = ""; // 默认无视频
        public static final int BACKGROUND_OPACITY = 100; // 默认完全不透明
        public static final String DOTNET_FRAMEWORK = "auto";
        public static final String RUNTIME_ARCHITECTURE = "auto";
        public static final boolean VERBOSE_LOGGING = false;
        public static final String FNA_RENDERER = "auto";

        // CoreCLR 默认值
        public static final boolean CORECLR_SERVER_GC = false; // 移动端默认关闭 Server GC
        public static final boolean CORECLR_CONCURRENT_GC = true; // 默认启用并发 GC
        public static final String CORECLR_GC_HEAP_COUNT = "auto"; // 自动检测
        public static final boolean CORECLR_TIERED_COMPILATION = true; // 默认启用分层编译
        public static final boolean CORECLR_QUICK_JIT = true; // 默认启用快速 JIT
        public static final int CORECLR_JIT_OPTIMIZE_TYPE = 0; // 0=混合, 1=体积, 2=速度
        public static final boolean CORECLR_RETAIN_VM = false; // 默认不保留虚拟内存
    }
    
    private SettingsManager(Context context) {
        this.context = context.getApplicationContext();
        this.settingsFile = new File(this.context.getFilesDir(), SETTINGS_FILE);
        loadSettings();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }
    
    /**
     * 加载设置
     */
    private void loadSettings() {
        if (settingsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(settingsFile)) {
                byte[] data = new byte[(int) settingsFile.length()];
                fis.read(data);
                String jsonString = new String(data, "UTF-8");
                settings = new JSONObject(jsonString);
            } catch (IOException | JSONException e) {
                AppLogger.error(TAG, "Failed to load settings: " + e.getMessage());
                settings = new JSONObject();
            }
        } else {
            settings = new JSONObject();
        }
    }
    
    /**
     * 保存设置
     */
    private synchronized void saveSettings() {
        try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
            String jsonString = settings.toString(2); // 格式化输出，便于调试
            fos.write(jsonString.getBytes("UTF-8"));
            fos.flush();
        } catch (IOException | JSONException e) {
            AppLogger.error(TAG, "Failed to save settings: " + e.getMessage());
        }
    }
    
    // ==================== 通用存取方法 ====================
    
    public String getString(String key, String defaultValue) {
        try {
            return settings.optString(key, defaultValue);
        } catch (Exception e) {
            AppLogger.error(TAG, "Error getting string for key: " + key + ": " + e.getMessage());
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        try {
            return settings.optInt(key, defaultValue);
        } catch (Exception e) {
            AppLogger.error(TAG, "Error getting int for key: " + key + ": " + e.getMessage());
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        try {
            boolean value = settings.optBoolean(key, defaultValue);

            return value;
        } catch (Exception e) {
            AppLogger.error(TAG, "Error getting boolean for key: " + key + ": " + e.getMessage());
            return defaultValue;
        }
    }
    
    public void putString(String key, String value) {
        try {
            settings.put(key, value);
            saveSettings();
        } catch (JSONException e) {
            AppLogger.error(TAG, "Error setting string for key: " + key + ": " + e.getMessage());
        }
    }

    public void putInt(String key, int value) {
        try {
            settings.put(key, value);
            saveSettings();
        } catch (JSONException e) {
            AppLogger.error(TAG, "Error setting int for key: " + key + ": " + e.getMessage());
        }
    }

    public void putBoolean(String key, boolean value) {
        try {
            settings.put(key, value);

            saveSettings();

        } catch (JSONException e) {
            AppLogger.error(TAG, "Error setting boolean for key: " + key + ": " + e.getMessage());
        }
    }
    
    // ==================== 便捷方法 ====================
    
    // 主题设置
    public int getThemeMode() {
        return getInt(Keys.THEME_MODE, Defaults.THEME_MODE);
    }
    
    public void setThemeMode(int mode) {
        putInt(Keys.THEME_MODE, mode);
    }
    
    public int getAppLanguage() {
        return getInt(Keys.APP_LANGUAGE, Defaults.APP_LANGUAGE);
    }
    
    public void setAppLanguage(int language) {
        putInt(Keys.APP_LANGUAGE, language);
    }

    public int getThemeColor() {
        return getInt(Keys.THEME_COLOR, Defaults.THEME_COLOR);
    }

    public void setThemeColor(int color) {
        putInt(Keys.THEME_COLOR, color);
    }

    // 背景设置
    public String getBackgroundType() {
        return getString(Keys.BACKGROUND_TYPE, Defaults.BACKGROUND_TYPE);
    }

    public void setBackgroundType(String type) {
        putString(Keys.BACKGROUND_TYPE, type);
    }

    public int getBackgroundColor() {
        return getInt(Keys.BACKGROUND_COLOR, Defaults.BACKGROUND_COLOR);
    }

    public void setBackgroundColor(int color) {
        putInt(Keys.BACKGROUND_COLOR, color);
    }

    public String getBackgroundImagePath() {
        return getString(Keys.BACKGROUND_IMAGE_PATH, Defaults.BACKGROUND_IMAGE_PATH);
    }

    public void setBackgroundImagePath(String path) {
        putString(Keys.BACKGROUND_IMAGE_PATH, path);
    }

    public String getBackgroundVideoPath() {
        return getString(Keys.BACKGROUND_VIDEO_PATH, Defaults.BACKGROUND_VIDEO_PATH);
    }

    public void setBackgroundVideoPath(String path) {
        putString(Keys.BACKGROUND_VIDEO_PATH, path);
    }

    public int getBackgroundOpacity() {
        return getInt(Keys.BACKGROUND_OPACITY, Defaults.BACKGROUND_OPACITY);
    }

    public void setBackgroundOpacity(int opacity) {
        putInt(Keys.BACKGROUND_OPACITY, opacity);
    }

    // 运行时设置
    public String getDotnetFramework() {
        return getString(Keys.DOTNET_FRAMEWORK, Defaults.DOTNET_FRAMEWORK);
    }
    
    public void setDotnetFramework(String framework) {
        putString(Keys.DOTNET_FRAMEWORK, framework);
    }
    
    public String getRuntimeArchitecture() {
        return getString(Keys.RUNTIME_ARCHITECTURE, Defaults.RUNTIME_ARCHITECTURE);
    }
    
    public void setRuntimeArchitecture(String architecture) {
        putString(Keys.RUNTIME_ARCHITECTURE, architecture);
    }
    
    // 开发者设置
    public boolean isVerboseLogging() {
        return getBoolean(Keys.VERBOSE_LOGGING, Defaults.VERBOSE_LOGGING);
    }
    
    public void setVerboseLogging(boolean enabled) {
        putBoolean(Keys.VERBOSE_LOGGING, enabled);
    }
    
    // FNA设置
    public String getFnaRenderer() {
        return getString(Keys.FNA_RENDERER, Defaults.FNA_RENDERER);
    }

    public void setFnaRenderer(String renderer) {
        putString(Keys.FNA_RENDERER, renderer);
    }

    // CoreCLR GC 设置
    public boolean isServerGC() {
        return getBoolean(Keys.CORECLR_SERVER_GC, Defaults.CORECLR_SERVER_GC);
    }

    public void setServerGC(boolean enabled) {
        putBoolean(Keys.CORECLR_SERVER_GC, enabled);
    }

    public boolean isConcurrentGC() {
        return getBoolean(Keys.CORECLR_CONCURRENT_GC, Defaults.CORECLR_CONCURRENT_GC);
    }

    public void setConcurrentGC(boolean enabled) {
        putBoolean(Keys.CORECLR_CONCURRENT_GC, enabled);
    }

    public String getGCHeapCount() {
        return getString(Keys.CORECLR_GC_HEAP_COUNT, Defaults.CORECLR_GC_HEAP_COUNT);
    }

    public void setGCHeapCount(String count) {
        putString(Keys.CORECLR_GC_HEAP_COUNT, count);
    }

    public boolean isRetainVM() {
        return getBoolean(Keys.CORECLR_RETAIN_VM, Defaults.CORECLR_RETAIN_VM);
    }

    public void setRetainVM(boolean enabled) {
        putBoolean(Keys.CORECLR_RETAIN_VM, enabled);
    }

    // CoreCLR JIT 设置
    public boolean isTieredCompilation() {
        return getBoolean(Keys.CORECLR_TIERED_COMPILATION, Defaults.CORECLR_TIERED_COMPILATION);
    }

    public void setTieredCompilation(boolean enabled) {
        putBoolean(Keys.CORECLR_TIERED_COMPILATION, enabled);
    }

    public boolean isQuickJIT() {
        return getBoolean(Keys.CORECLR_QUICK_JIT, Defaults.CORECLR_QUICK_JIT);
    }

    public void setQuickJIT(boolean enabled) {
        putBoolean(Keys.CORECLR_QUICK_JIT, enabled);
    }

    public int getJitOptimizeType() {
        return getInt(Keys.CORECLR_JIT_OPTIMIZE_TYPE, Defaults.CORECLR_JIT_OPTIMIZE_TYPE);
    }

    public void setJitOptimizeType(int type) {
        putInt(Keys.CORECLR_JIT_OPTIMIZE_TYPE, type);
    }

    /**
     * 获取设置文件路径（用于调试）
     */
    public String getSettingsFilePath() {
        return settingsFile.getAbsolutePath();
    }

    /**
     * 重新加载设置（用于调试）
     */
    public void reload() {
        loadSettings();
    }
}
