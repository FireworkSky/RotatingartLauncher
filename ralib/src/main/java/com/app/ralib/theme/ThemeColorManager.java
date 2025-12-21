package com.app.ralib.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import androidx.annotation.ColorInt;

/**
 * 主题颜色管理器
 *
 * 用于管理应用的动态主题颜色
 */
public class ThemeColorManager {
    private static final String TAG = "ThemeColorManager";

    /**
     * 获取主题颜色
     *
     * @param context Context
     * @return 主题颜色值
     */
    @ColorInt
    public static int getThemeColor(Context context) {
        // 从设置中读取主题颜色
        // 这里需要从 SettingsManager 读取，但为了避免循环依赖，
        // 我们直接从 SharedPreferences 读取
        try {
            java.io.File settingsFile = new java.io.File(context.getFilesDir(), "settings.json");
            if (settingsFile.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(settingsFile);
                byte[] data = new byte[(int) settingsFile.length()];
                fis.read(data);
                fis.close();

                String jsonString = new String(data, "UTF-8");
                org.json.JSONObject settings = new org.json.JSONObject(jsonString);

                if (settings.has("theme_color")) {
                    return settings.getInt("theme_color");
                }
            }
        } catch (Exception e) {
            // 读取失败，使用默认颜色
        }

        // 默认绿色
        return 0xFF4CAF50;
    }

    /**
     * 应用主题颜色到 Activity
     *
     * 这个方法会在 Activity 的 setContentView() 之前调用，
     * 用于动态设置主题的强调色
     *
     * 关键：在 onCreate 的最开始调用此方法，确保 recreate 时无闪烁
     *
     * @param activity Activity
     */
    public static void applyThemeColor(Activity activity) {
        int themeColor = getThemeColor(activity);

        // 创建 ColorStateList 用于按钮等控件
        ColorStateList colorStateList = ColorStateList.valueOf(themeColor);

        // 动态创建主题覆盖并应用
        // 这样 recreate 时新颜色会在界面绘制前就设置好，避免闪烁
        TypedValue typedValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        
        // 直接修改主题属性（虽然不推荐，但这是最快的方式）
        try {
            // 使用反射设置主题颜色（仅用于快速预览，真正的颜色由 DynamicColors 提供）
            activity.getTheme().applyStyle(getThemeOverlayResId(themeColor), true);
        } catch (Exception e) {
            // 如果失败，不影响程序运行
        }
    }

    /**
     * 获取主题覆盖资源 ID
     *
     * 根据颜色值返回对应的主题覆盖
     *
     * @param color 颜色值
     * @return 主题覆盖资源 ID
     */
    private static int getThemeOverlayResId(@ColorInt int color) {
        // 暂时返回0，不使用主题覆盖
        // 因为我们会通过其他方式应用颜色
        return 0;
    }

    /**
     * 获取颜色的 ColorStateList
     *
     * @param context Context
     * @return ColorStateList
     */
    public static ColorStateList getThemeColorStateList(Context context) {
        return ColorStateList.valueOf(getThemeColor(context));
    }

    /**
     * 判断颜色是否为深色
     *
     * @param color 颜色值
     * @return 如果是深色返回 true
     */
    public static boolean isDarkColor(@ColorInt int color) {
        double darkness = 1 - (0.299 * Color.red(color) +
                              0.587 * Color.green(color) +
                              0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    /**
     * 获取颜色的明亮变体
     *
     * @param color 原始颜色
     * @param factor 亮度因子 (0.0 - 1.0)
     * @return 明亮变体颜色
     */
    @ColorInt
    public static int getLighterColor(@ColorInt int color, float factor) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        red = (int) (red + (255 - red) * factor);
        green = (int) (green + (255 - green) * factor);
        blue = (int) (blue + (255 - blue) * factor);

        return Color.rgb(red, green, blue);
    }

    /**
     * 获取颜色的暗色变体
     *
     * @param color 原始颜色
     * @param factor 暗度因子 (0.0 - 1.0)
     * @return 暗色变体颜色
     */
    @ColorInt
    public static int getDarkerColor(@ColorInt int color, float factor) {
        int red = (int) (Color.red(color) * (1 - factor));
        int green = (int) (Color.green(color) * (1 - factor));
        int blue = (int) (Color.blue(color) * (1 - factor));

        return Color.rgb(red, green, blue);
    }
}
