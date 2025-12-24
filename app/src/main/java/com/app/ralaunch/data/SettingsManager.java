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
        public static final String VIDEO_PLAYBACK_SPEED = "video_playback_speed"; // 视频播放速度 (0.5-2.0)

        // 运行时设置
        public static final String DOTNET_FRAMEWORK = "dotnet_framework";
        public static final String RUNTIME_ARCHITECTURE = "runtime_architecture";

        // 控制设置
        public static final String CONTROLS_VIBRATION_ENABLED = "controls_vibration_enabled";
        public static final String TOUCH_MULTITOUCH_ENABLED = "touch_multitouch_enabled"; // 多点触控模拟鼠标
        public static final String FPS_DISPLAY_ENABLED = "fps_display_enabled"; // FPS 显示开关
        public static final String FPS_DISPLAY_X = "fps_display_x"; // FPS 显示位置 X
        public static final String FPS_DISPLAY_Y = "fps_display_y"; // FPS 显示位置 Y
        public static final String KEYBOARD_TYPE = "keyboard_type"; // 键盘类型: "system" 或 "virtual"
        
        // 触屏设置
        public static final String MOUSE_RIGHT_STICK_ENABLED = "mouse_right_stick_enabled"; // 鼠标模式右摇杆
        public static final String MOUSE_RIGHT_STICK_ATTACK_MODE = "mouse_right_stick_attack_mode"; // 右摇杆攻击模式
        public static final String MOUSE_RIGHT_STICK_SPEED = "mouse_right_stick_speed"; // 鼠标移动速度
        public static final String MOUSE_RIGHT_STICK_RANGE_LEFT = "mouse_right_stick_range_left"; // 鼠标范围左边界
        public static final String MOUSE_RIGHT_STICK_RANGE_TOP = "mouse_right_stick_range_top"; // 鼠标范围上边界
        public static final String MOUSE_RIGHT_STICK_RANGE_RIGHT = "mouse_right_stick_range_right"; // 鼠标范围右边界
        public static final String MOUSE_RIGHT_STICK_RANGE_BOTTOM = "mouse_right_stick_range_bottom"; // 鼠标范围下边界

        // 开发者设置
        public static final String ENABLE_LOG_SYSTEM = "enable_log_system";
        public static final String VERBOSE_LOGGING = "verbose_logging";
        public static final String SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED = "set_thread_affinity_to_big_core_enabled";
        public static final String DISABLE_VSYNC = "disable_vsync"; // 禁用垂直同步
        public static final String UNLOCK_FPS = "unlock_fps"; // 解锁帧率限制
        // FNA设置
        public static final String FNA_RENDERER = "fna_renderer";
        public static final String FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE = "fna_enable_map_buffer_range_optimization_if_available"; // 启用 MapBufferRange 优化（如果支持）

        // Vulkan 驱动设置
        public static final String VULKAN_DRIVER_TURNIP = "vulkan_driver_turnip"; // 是否使用 Turnip 驱动（Adreno GPU）

        // CoreCLR 运行时配置
        public static final String CORECLR_SERVER_GC = "coreclr_server_gc";
        public static final String CORECLR_CONCURRENT_GC = "coreclr_concurrent_gc";
        public static final String CORECLR_GC_HEAP_COUNT = "coreclr_gc_heap_count";
        public static final String CORECLR_TIERED_COMPILATION = "coreclr_tiered_compilation";
        public static final String CORECLR_QUICK_JIT = "coreclr_quick_jit";
        public static final String CORECLR_JIT_OPTIMIZE_TYPE = "coreclr_jit_optimize_type";
        public static final String CORECLR_RETAIN_VM = "coreclr_retain_vm";

        // 内存优化设置
        public static final String KILL_LAUNCHER_UI_AFTER_LAUNCH = "kill_launcher_ui_after_launch";
    }
    
    // 默认值
    public static class Defaults {
        public static final int THEME_MODE = 2; // 亮色主题
        public static final int APP_LANGUAGE = 0; // 跟随系统
        public static final int THEME_COLOR = 0xFF6750A4; // Material 3 默认紫色（动态主题种子色）
        public static final String BACKGROUND_TYPE = "default"; // 默认背景
        public static final int BACKGROUND_COLOR = 0xFFFFFFFF; // 默认白色
        public static final String BACKGROUND_IMAGE_PATH = ""; // 默认无图片
        public static final String BACKGROUND_VIDEO_PATH = ""; // 默认无视频
        public static final int BACKGROUND_OPACITY = 0; // 默认透明度0%（无背景时）
        public static final float VIDEO_PLAYBACK_SPEED = 1.0f; // 默认播放速度 1.0x
        public static final String DOTNET_FRAMEWORK = "auto";
        public static final String RUNTIME_ARCHITECTURE = "auto";
        public static final boolean ENABLE_LOG_SYSTEM = true;
        public static final boolean VERBOSE_LOGGING = false;
        public static final boolean SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED = true;
        public static final boolean DISABLE_VSYNC = false; // 默认不禁用 VSync
        public static final boolean UNLOCK_FPS = false; // 默认不解锁帧率
        public static final String FNA_RENDERER = "auto";
        public static final boolean FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE = true; // 默认启用 MapBufferRange 优化

        // Vulkan 驱动默认值
        public static final boolean VULKAN_DRIVER_TURNIP = true; // 默认启用 Turnip（如果支持）

        // 控制设置
        public static final boolean CONTROLS_VIBRATION_ENABLED = true; // 默认开启振动反馈
        public static final boolean TOUCH_MULTITOUCH_ENABLED = true; // 默认开启多点触控（不可更改）
        public static final boolean FPS_DISPLAY_ENABLED = false; // 默认关闭 FPS 显示
        public static final float FPS_DISPLAY_X = -1f; // 默认自动位置（跟随鼠标）
        public static final float FPS_DISPLAY_Y = -1f; // 默认自动位置（跟随鼠标）
        public static final String KEYBOARD_TYPE = "virtual"; // 默认使用虚拟键盘
        
        // 触屏设置
        public static final boolean MOUSE_RIGHT_STICK_ENABLED = true; // 默认开启鼠标模式右摇杆（不可更改）
        public static final int MOUSE_RIGHT_STICK_ATTACK_MODE = 0; // 默认长按模式
        public static final int MOUSE_RIGHT_STICK_SPEED = 80; // 默认速度80（范围60-200）
        // 鼠标移动范围（从中心扩展模式）：0.0=中心点, 1.0=全屏（最大）
        public static final float MOUSE_RIGHT_STICK_RANGE_LEFT = 1.0f; // 默认100%（全屏）
        public static final float MOUSE_RIGHT_STICK_RANGE_TOP = 1.0f; // 默认100%（全屏）
        public static final float MOUSE_RIGHT_STICK_RANGE_RIGHT = 1.0f; // 默认100%（全屏）
        public static final float MOUSE_RIGHT_STICK_RANGE_BOTTOM = 1.0f; // 默认100%（全屏）

        // CoreCLR 默认值
        public static final boolean CORECLR_SERVER_GC = false; // 移动端默认关闭 Server GC
        public static final boolean CORECLR_CONCURRENT_GC = true; // 默认启用并发 GC
        public static final String CORECLR_GC_HEAP_COUNT = "auto"; // 自动检测
        public static final boolean CORECLR_TIERED_COMPILATION = true; // 默认启用分层编译
        public static final boolean CORECLR_QUICK_JIT = true; // 默认启用快速 JIT
        public static final int CORECLR_JIT_OPTIMIZE_TYPE = 0; // 0=混合, 1=体积, 2=速度
        public static final boolean CORECLR_RETAIN_VM = false; // 默认不保留虚拟内存

        // 内存优化默认值
        public static final boolean KILL_LAUNCHER_UI_AFTER_LAUNCH = false; // 默认不杀死启动器UI进程
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

    public double getDouble(String key, double defaultValue) {
        try {
            return settings.optDouble(key, defaultValue);
        } catch (Exception e) {
            AppLogger.error(TAG, "Error getting double for key: " + key + ": " + e.getMessage());
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

    public void putDouble(String key, double value) {
        try {
            settings.put(key, value);
            saveSettings();
        } catch (JSONException e) {
            AppLogger.error(TAG, "Error setting double for key: " + key + ": " + e.getMessage());
        }
    }

    public float getFloat(String key, float defaultValue) {
        try {
            if (settings.has(key)) {
                return (float) settings.getDouble(key);
            }
        } catch (JSONException e) {
            AppLogger.error(TAG, "Error getting float for key: " + key + ": " + e.getMessage());
        }
        return defaultValue;
    }

    public void putFloat(String key, float value) {
        try {
            settings.put(key, value);
            saveSettings();
        } catch (JSONException e) {
            AppLogger.error(TAG, "Error setting float for key: " + key + ": " + e.getMessage());
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

    public float getVideoPlaybackSpeed() {
        return (float) getDouble(Keys.VIDEO_PLAYBACK_SPEED, Defaults.VIDEO_PLAYBACK_SPEED);
    }

    public void setVideoPlaybackSpeed(float speed) {
        putDouble(Keys.VIDEO_PLAYBACK_SPEED, speed);
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

    // 控制设置
    public boolean getVibrationEnabled() {
        return getBoolean(Keys.CONTROLS_VIBRATION_ENABLED, Defaults.CONTROLS_VIBRATION_ENABLED);
    }

    public void setVibrationEnabled(boolean enabled) {
        putBoolean(Keys.CONTROLS_VIBRATION_ENABLED, enabled);
    }
    
    /**
     * 是否启用多点触控鼠标模拟
     * 启用后：
     * - 每个触摸点独立发送鼠标按下/释放事件
     * - 最新触摸的手指控制鼠标移动
     * - 支持同时多指点击不同屏幕位置
     */
    public boolean isTouchMultitouchEnabled() {
        return getBoolean(Keys.TOUCH_MULTITOUCH_ENABLED, Defaults.TOUCH_MULTITOUCH_ENABLED);
    }
    
    public void setTouchMultitouchEnabled(boolean enabled) {
        putBoolean(Keys.TOUCH_MULTITOUCH_ENABLED, enabled);
    }
    
    
    // FNA 触屏设置
    
    /**
     * 是否启用鼠标模式右摇杆
     * 启用后：虚拟右摇杆控制鼠标移动，适合需要精确鼠标控制的游戏
     */
    public boolean isMouseRightStickEnabled() {
        return getBoolean(Keys.MOUSE_RIGHT_STICK_ENABLED, Defaults.MOUSE_RIGHT_STICK_ENABLED);
    }
    
    public void setMouseRightStickEnabled(boolean enabled) {
        putBoolean(Keys.MOUSE_RIGHT_STICK_ENABLED, enabled);
    }
    
    /**
     * 右摇杆攻击模式
     * 0 = 长按模式（默认）：按住鼠标左键不放
     * 1 = 点击模式：快速点击（按下-释放循环）
     * 2 = 持续模式：保持按住状态
     */
    public static final int ATTACK_MODE_HOLD = 0;      // 长按
    public static final int ATTACK_MODE_CLICK = 1;     // 点击
    public static final int ATTACK_MODE_CONTINUOUS = 2; // 持续
    
    public int getMouseRightStickAttackMode() {
        return getInt(Keys.MOUSE_RIGHT_STICK_ATTACK_MODE, Defaults.MOUSE_RIGHT_STICK_ATTACK_MODE);
    }
    
    public void setMouseRightStickAttackMode(int mode) {
        putInt(Keys.MOUSE_RIGHT_STICK_ATTACK_MODE, mode);
    }
    
    /**
     * 鼠标移动速度（1-100）
     */
    public int getMouseRightStickSpeed() {
        return getInt(Keys.MOUSE_RIGHT_STICK_SPEED, Defaults.MOUSE_RIGHT_STICK_SPEED);
    }
    
    public void setMouseRightStickSpeed(int speed) {
        putInt(Keys.MOUSE_RIGHT_STICK_SPEED, speed);
    }
    
    /**
     * 鼠标移动范围（屏幕百分比 0.0-1.0）
     */
    public float getMouseRightStickRangeLeft() {
        return getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_LEFT, Defaults.MOUSE_RIGHT_STICK_RANGE_LEFT);
    }
    
    public void setMouseRightStickRangeLeft(float value) {
        putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_LEFT, value);
    }
    
    public float getMouseRightStickRangeTop() {
        return getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_TOP, Defaults.MOUSE_RIGHT_STICK_RANGE_TOP);
    }
    
    public void setMouseRightStickRangeTop(float value) {
        putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_TOP, value);
    }
    
    public float getMouseRightStickRangeRight() {
        return getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_RIGHT, Defaults.MOUSE_RIGHT_STICK_RANGE_RIGHT);
    }
    
    public void setMouseRightStickRangeRight(float value) {
        putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_RIGHT, value);
    }
    
    public float getMouseRightStickRangeBottom() {
        return getFloat(Keys.MOUSE_RIGHT_STICK_RANGE_BOTTOM, Defaults.MOUSE_RIGHT_STICK_RANGE_BOTTOM);
    }
    
    public void setMouseRightStickRangeBottom(float value) {
        putFloat(Keys.MOUSE_RIGHT_STICK_RANGE_BOTTOM, value);
    }
    
    // 开发者设置
    public boolean isLogSystemEnabled() {
        return getBoolean(Keys.ENABLE_LOG_SYSTEM, Defaults.ENABLE_LOG_SYSTEM);
    }

    public void setLogSystemEnabled(boolean enabled) {
        putBoolean(Keys.ENABLE_LOG_SYSTEM, enabled);
    }

    public boolean isVerboseLogging() {
        return getBoolean(Keys.VERBOSE_LOGGING, Defaults.VERBOSE_LOGGING);
    }
    
    public void setVerboseLogging(boolean enabled) {
        putBoolean(Keys.VERBOSE_LOGGING, enabled);
    }

    public boolean getSetThreadAffinityToBigCoreEnabled() {
        return getBoolean(Keys.SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED, Defaults.SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED);
    }

    public void setSetThreadAffinityToBigCoreEnabled(boolean enabled) {
        putBoolean(Keys.SET_THREAD_AFFINITY_TO_BIG_CORE_ENABLED, enabled);
    }

    public boolean isDisableVSyncEnabled() {
        return getBoolean(Keys.DISABLE_VSYNC, Defaults.DISABLE_VSYNC);
    }

    public void setDisableVSyncEnabled(boolean enabled) {
        putBoolean(Keys.DISABLE_VSYNC, enabled);
    }

    public boolean isUnlockFPSEnabled() {
        return getBoolean(Keys.UNLOCK_FPS, Defaults.UNLOCK_FPS);
    }

    public void setUnlockFPSEnabled(boolean enabled) {
        putBoolean(Keys.UNLOCK_FPS, enabled);
    }

    // FNA设置
    public String getFnaRenderer() {
        return getString(Keys.FNA_RENDERER, Defaults.FNA_RENDERER);
    }

    public void setFnaRenderer(String renderer) {
        putString(Keys.FNA_RENDERER, renderer);
    }

    public boolean isFnaEnableMapBufferRangeOptimization() {
        return getBoolean(Keys.FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE, Defaults.FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE);
    }

    public void setFnaEnableMapBufferRangeOptimization(boolean enabled) {
        putBoolean(Keys.FNA_ENABLE_MAP_BUFFER_RANGE_OPTIMIZATION_IF_AVAILABLE, enabled);
    }

    // Vulkan 驱动设置
    public boolean isVulkanDriverTurnip() {
        return getBoolean(Keys.VULKAN_DRIVER_TURNIP, Defaults.VULKAN_DRIVER_TURNIP);
    }

    public void setVulkanDriverTurnip(boolean enabled) {
        putBoolean(Keys.VULKAN_DRIVER_TURNIP, enabled);
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
     * 是否显示 FPS
     */
    public boolean isFPSDisplayEnabled() {
        return getBoolean(Keys.FPS_DISPLAY_ENABLED, Defaults.FPS_DISPLAY_ENABLED);
    }

    /**
     * 设置是否显示 FPS
     */
    public void setFPSDisplayEnabled(boolean enabled) {
        putBoolean(Keys.FPS_DISPLAY_ENABLED, enabled);
    }

    /**
     * 获取 FPS 显示位置 X（-1 表示跟随鼠标）
     */
    public float getFPSDisplayX() {
        return getFloat(Keys.FPS_DISPLAY_X, Defaults.FPS_DISPLAY_X);
    }

    /**
     * 设置 FPS 显示位置 X（-1 表示跟随鼠标）
     */
    public void setFPSDisplayX(float x) {
        putFloat(Keys.FPS_DISPLAY_X, x);
    }

    /**
     * 获取 FPS 显示位置 Y（-1 表示跟随鼠标）
     */
    public float getFPSDisplayY() {
        return getFloat(Keys.FPS_DISPLAY_Y, Defaults.FPS_DISPLAY_Y);
    }

    /**
     * 设置 FPS 显示位置 Y（-1 表示跟随鼠标）
     */
    public void setFPSDisplayY(float y) {
        putFloat(Keys.FPS_DISPLAY_Y, y);
    }

    /**
     * 获取设置文件路径（用于调试）
     */
    public String getSettingsFilePath() {
        return settingsFile.getAbsolutePath();
    }

    // 键盘类型设置
    public String getKeyboardType() {
        return getString(Keys.KEYBOARD_TYPE, Defaults.KEYBOARD_TYPE);
    }
    
    public void setKeyboardType(String type) {
        putString(Keys.KEYBOARD_TYPE, type);
    }
    
    public boolean isVirtualKeyboard() {
        return "virtual".equals(getKeyboardType());
    }

    // 内存优化设置
    public boolean isKillLauncherUIAfterLaunch() {
        return getBoolean(Keys.KILL_LAUNCHER_UI_AFTER_LAUNCH, Defaults.KILL_LAUNCHER_UI_AFTER_LAUNCH);
    }

    public void setKillLauncherUIAfterLaunch(boolean enabled) {
        putBoolean(Keys.KILL_LAUNCHER_UI_AFTER_LAUNCH, enabled);
    }

    /**
     * 重新加载设置（用于调试）
     */
    public void reload() {
        loadSettings();
    }
}
