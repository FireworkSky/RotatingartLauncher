package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.controls.ControlConfig;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.ControlDataConverter;
import com.app.ralaunch.utils.AppLogger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 控制布局管理器
 * 
 * 管理游戏控制布局的保存和加载，提供：
 * - 保存和加载自定义控制布局
 * - 创建和删除布局
 * - 切换当前使用的布局
 * - 布局列表管理
 * 
 * 使用 SharedPreferences 持久化存储布局数据
 */
public class ControlLayoutManager {
    private static final String PREF_NAME = "control_layouts";
    private static final String KEY_LAYOUTS = "saved_layouts";
    private static final String KEY_CURRENT_LAYOUT = "current_layout";

    // 使用固定的内部标识符，不随语言变化
    private static final String INTERNAL_KEYBOARD_MODE = "keyboard_mode";
    private static final String INTERNAL_GAMEPAD_MODE = "gamepad_mode";
    private static final String INTERNAL_DEFAULT_LAYOUT = "default_layout";

    private SharedPreferences preferences;    private Gson gson;
    private List<ControlLayout> layouts;
    private String currentLayoutName;
    private Context context;
    private static final String KEY_DEFAULT_LAYOUTS_DELETED = "default_layouts_deleted";

    public ControlLayoutManager(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 配置 Gson 序列化空值和空字符串，确保 displayText 等字段即使为空也能正确保存和加载
        // 注册 ControlElement 的自定义 TypeAdapter
        gson = new com.google.gson.GsonBuilder()
                .serializeNulls()
                .registerTypeAdapter(com.app.ralaunch.model.ControlElement.class, new com.app.ralaunch.model.ControlElementTypeAdapter())
                .create();
        loadLayouts();
    }

    private void loadLayouts() {
        String layoutsJson = preferences.getString(KEY_LAYOUTS, null);
        if (layoutsJson != null) {
            Type listType = new TypeToken<List<ControlLayout>>(){}.getType();
            layouts = gson.fromJson(layoutsJson, listType);
        } else {
            layouts = new ArrayList<>();
        }

        // 检查并添加默认布局（如果不存在）
        ensureDefaultLayoutsExist();

        currentLayoutName = preferences.getString(KEY_CURRENT_LAYOUT, INTERNAL_KEYBOARD_MODE);
    }

    /**
     * 确保默认布局存在，如果不存在则添加
     * 但如果用户明确删除了默认布局，则不自动重新创建
     */
    private void ensureDefaultLayoutsExist() {
        // 检查用户是否明确删除了默认布局
        boolean defaultLayoutsDeleted = preferences.getBoolean(KEY_DEFAULT_LAYOUTS_DELETED, false);
        if (defaultLayoutsDeleted) {
            // 用户已删除默认布局，不自动重新创建
            AppLogger.info("ControlLayoutManager", "Default layouts were deleted by user, skipping auto-creation");
            return;
        }
        
        boolean hasKeyboardLayout = false;
        boolean hasGamepadLayout = false;
        boolean hasOldDefaultLayout = false;
        String oldDefaultLayoutName = context.getString(R.string.control_layout_default_name);

        // 检查是否已存在键盘模式和手柄模式布局（使用内部标识符）
        for (ControlLayout layout : layouts) {
            String name = layout.getName();
            // 检查内部标识符
            if (INTERNAL_KEYBOARD_MODE.equals(name)) {
                hasKeyboardLayout = true;
            } else if (INTERNAL_GAMEPAD_MODE.equals(name)) {
                hasGamepadLayout = true;
            } else if (INTERNAL_DEFAULT_LAYOUT.equals(name)) {
                hasOldDefaultLayout = true;
            } 
            // 兼容旧版本：检查旧的中文名称（如果存在）
            else if (context.getString(R.string.control_layout_keyboard_mode).equals(name)) {
                // 旧版本的中文名称，更新为内部标识符
                layout.setName(INTERNAL_KEYBOARD_MODE);
                hasKeyboardLayout = true;
            } else if (context.getString(R.string.control_layout_gamepad_mode).equals(name)) {
                // 旧版本的英文名称，更新为内部标识符
                layout.setName(INTERNAL_GAMEPAD_MODE);
                hasGamepadLayout = true;
            } else if (oldDefaultLayoutName.equals(name)) {
                // 旧的"默认布局"，更新为内部标识符
                hasOldDefaultLayout = true;
            }
        }

        // 如果有旧的"默认布局"，将其更新为内部标识符
        if (hasOldDefaultLayout && !hasKeyboardLayout) {
            for (ControlLayout layout : layouts) {
                if (INTERNAL_DEFAULT_LAYOUT.equals(layout.getName()) || oldDefaultLayoutName.equals(layout.getName())) {
                    layout.setName(INTERNAL_KEYBOARD_MODE);
                    hasKeyboardLayout = true;
                    break;
                }
            }
        }

        // 如果不存在，则创建默认布局
        if (!hasKeyboardLayout || !hasGamepadLayout) {
            createDefaultLayouts(hasKeyboardLayout, hasGamepadLayout);
            saveLayouts();
        }
    }

    /**
     * 从 JSON 文件加载默认布局
     */
    private void createDefaultLayouts(boolean skipKeyboard, boolean skipGamepad) {
        // 创建键盘模式默认布局（从 default_layout.json 加载）
        // 使用固定的内部标识符作为存储名称
        if (!skipKeyboard) {
            ControlLayout keyboardLayout = loadLayoutFromAssets("controls/default_layout.json", INTERNAL_KEYBOARD_MODE);
            if (keyboardLayout != null) {
                layouts.add(keyboardLayout);
            } else {
                AppLogger.warn("ControlLayoutManager", "Layout file not found");
            }
        }

        // 创建手柄模式默认布局（从 gamepad_layout_classic.json 加载，设置为默认）
        // 使用固定的内部标识符作为存储名称
        if (!skipGamepad) {
            // 首先加载经典手柄布局作为默认手柄布局
            ControlLayout classicLayout = loadLayoutFromAssets("controls/gamepad_layout.json", INTERNAL_GAMEPAD_MODE);
            if (classicLayout != null) {
                layouts.add(classicLayout);
            } else {
                AppLogger.warn("ControlLayoutManager", "Layout file not found");
            }
            

        }
    }

    /**
     * 从 assets 中的 JSON 文件加载布局
     */
    private ControlLayout loadLayoutFromAssets(String assetPath, String layoutName) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open(assetPath);
            
            // 读取 JSON 内容
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            String json = new String(buffer, "UTF-8");
            
            // 解析为 ControlConfig
            ControlConfig config = ControlConfig.loadFromJson(json);
            if (config == null || config.controls == null || config.controls.isEmpty()) {
                AppLogger.warn("ControlLayoutManager", "Empty config loaded from: " + assetPath);
                return null;
            }
            
            // 创建 ControlLayout
            ControlLayout layout = new ControlLayout(layoutName);
            
            // 获取屏幕尺寸用于坐标转换
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            
            // 将 ControlData 转换为 ControlElement
            for (ControlData data : config.controls) {
                ControlElement element = ControlDataConverter.dataToElement(data, screenWidth, screenHeight);
                if (element != null) {
                    layout.addElement(element);
                }
            }
            
            AppLogger.info("ControlLayoutManager", "Loaded layout from JSON: " + assetPath + " with " + layout.getElements().size() + " elements");
            return layout;
            
        } catch (Exception e) {
            AppLogger.error("ControlLayoutManager", "Failed to load layout from assets: " + assetPath, e);
            return null;
        }
    }

    public void saveLayouts() {
        String layoutsJson = gson.toJson(layouts);
        preferences.edit()
                .putString(KEY_LAYOUTS, layoutsJson)
                .putString(KEY_CURRENT_LAYOUT, currentLayoutName)
                .apply();
    }

    public List<ControlLayout> getLayouts() {
        return new ArrayList<>(layouts);
    }

    public ControlLayout getCurrentLayout() {
        for (ControlLayout layout : layouts) {
            if (layout.getName().equals(currentLayoutName)) {
                return layout;
            }
        }
        return layouts.isEmpty() ? null : layouts.get(0);
    }

    /**
     * 根据名称获取布局
     */
    public ControlLayout getLayout(String layoutName) {
        for (ControlLayout layout : layouts) {
            if (layout.getName().equals(layoutName)) {
                return layout;
            }
        }
        return null;
    }

    public String getCurrentLayoutName() {
        return currentLayoutName;
    }

    public void setCurrentLayout(String layoutName) {
        this.currentLayoutName = layoutName;
        saveLayouts();
    }

    public void addLayout(ControlLayout layout) {
        layouts.add(layout);
        saveLayouts();
    }

    public void removeLayout(String layoutName) {
        // 如果删除的是当前布局，需要切换到其他布局
        if (layoutName.equals(currentLayoutName)) {
            // 尝试切换到键盘模式，如果不存在则切换到第一个可用布局
            boolean hasKeyboard = layouts.stream().anyMatch(l -> INTERNAL_KEYBOARD_MODE.equals(l.getName()));
            if (hasKeyboard) {
                currentLayoutName = INTERNAL_KEYBOARD_MODE;
            } else if (!layouts.isEmpty()) {
                currentLayoutName = layouts.get(0).getName();
            } else {
                currentLayoutName = INTERNAL_KEYBOARD_MODE; // 如果列表为空，设置为默认名称
            }
        }
        
        // 检查是否是默认布局（使用内部标识符）
        boolean isDefaultLayout = INTERNAL_KEYBOARD_MODE.equals(layoutName) || INTERNAL_GAMEPAD_MODE.equals(layoutName);
        
        // 删除布局
        boolean removed = layouts.removeIf(layout -> layout.getName().equals(layoutName));
        if (removed) {
            if (isDefaultLayout) {
                preferences.edit().putBoolean(KEY_DEFAULT_LAYOUTS_DELETED, true).apply();
                AppLogger.info("ControlLayoutManager", "Default layout deleted: " + layoutName);
            }
            
            saveLayouts();
            AppLogger.info("ControlLayoutManager", "Layout removed: " + layoutName);
        } else {
            AppLogger.warn("ControlLayoutManager", "Layout not found for removal: " + layoutName);
        }
    }

    public void updateLayout(ControlLayout updatedLayout) {
        for (int i = 0; i < layouts.size(); i++) {
            if (layouts.get(i).getName().equals(updatedLayout.getName())) {
                layouts.set(i, updatedLayout);
                saveLayouts();
                break;
            }
        }
    }

    public void saveLayout(ControlLayout layout) {
        updateLayout(layout);
    }

    /**
     * 获取布局的显示名称（用于UI显示）
     * 如果是默认布局，返回本地化的名称；否则返回原始名称
     */
    public String getLayoutDisplayName(String layoutName) {
        if (INTERNAL_KEYBOARD_MODE.equals(layoutName)) {
            return context.getString(R.string.control_layout_keyboard_mode);
        } else if (INTERNAL_GAMEPAD_MODE.equals(layoutName)) {
            return context.getString(R.string.control_layout_gamepad_mode);
        } else {
            return layoutName;
        }
    }
}