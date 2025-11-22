package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.model.ControlElement;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

    private SharedPreferences preferences;
    private Gson gson;
    private List<ControlLayout> layouts;
    private String currentLayoutName;

    public ControlLayoutManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
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

        currentLayoutName = preferences.getString(KEY_CURRENT_LAYOUT, "键盘模式");
    }

    /**
     * 确保默认布局存在，如果不存在则添加
     */
    private void ensureDefaultLayoutsExist() {
        boolean hasKeyboardLayout = false;
        boolean hasGamepadLayout = false;
        boolean hasOldDefaultLayout = false;

        // 检查是否已存在键盘模式和手柄模式布局
        for (ControlLayout layout : layouts) {
            String name = layout.getName();
            if ("键盘模式".equals(name)) {
                hasKeyboardLayout = true;
            } else if ("手柄模式".equals(name)) {
                hasGamepadLayout = true;
            } else if ("默认布局".equals(name)) {
                hasOldDefaultLayout = true;
            }
        }

        // 如果有旧的"默认布局"，将其重命名为"键盘模式"
        if (hasOldDefaultLayout && !hasKeyboardLayout) {
            for (ControlLayout layout : layouts) {
                if ("默认布局".equals(layout.getName())) {
                    layout.setName("键盘模式");
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

    private void createDefaultLayouts(boolean skipKeyboard, boolean skipGamepad) {
        // 创建键盘模式默认布局
        if (!skipKeyboard) {
            ControlLayout keyboardLayout = new ControlLayout("键盘模式");

            // 添加方向键
            ControlElement crossKey = new ControlElement("cross", ControlElement.ElementType.CROSS_KEY, "方向键");
            crossKey.setX(0.1f);
            crossKey.setY(0.6f);
            crossKey.setWidth(200);
            crossKey.setHeight(200);
            keyboardLayout.addElement(crossKey);

            // 添加跳跃按钮
            ControlElement jumpButton = new ControlElement("jump", ControlElement.ElementType.BUTTON, "跳跃");
            jumpButton.setX(0.8f);
            jumpButton.setY(0.6f);
            jumpButton.setWidth(120);
            jumpButton.setHeight(120);
            jumpButton.setKeyCode(32); // 空格键
            keyboardLayout.addElement(jumpButton);

            // 添加攻击按钮
            ControlElement attackButton = new ControlElement("attack", ControlElement.ElementType.BUTTON, "攻击");
            attackButton.setX(0.7f);
            attackButton.setY(0.4f);
            attackButton.setWidth(100);
            attackButton.setHeight(100);
            attackButton.setKeyCode(29); // Ctrl 键
            keyboardLayout.addElement(attackButton);

            layouts.add(keyboardLayout);
        }

        // 创建手柄模式默认布局
        if (!skipGamepad) {
            ControlLayout gamepadLayout = new ControlLayout("手柄模式");

            // 添加左摇杆
            ControlElement leftStick = new ControlElement("left_stick", ControlElement.ElementType.JOYSTICK, "左摇杆");
            leftStick.setX(0.1f);
            leftStick.setY(0.6f);
            leftStick.setWidth(200);
            leftStick.setHeight(200);
            gamepadLayout.addElement(leftStick);

            // 添加右摇杆
            ControlElement rightStick = new ControlElement("right_stick", ControlElement.ElementType.JOYSTICK, "右摇杆");
            rightStick.setX(0.7f);
            rightStick.setY(0.6f);
            rightStick.setWidth(200);
            rightStick.setHeight(200);
            gamepadLayout.addElement(rightStick);

            // 添加 A 按钮
            ControlElement aButton = new ControlElement("a_button", ControlElement.ElementType.BUTTON, "A");
            aButton.setX(0.85f);
            aButton.setY(0.7f);
            aButton.setWidth(100);
            aButton.setHeight(100);
            gamepadLayout.addElement(aButton);

            layouts.add(gamepadLayout);
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
        layouts.removeIf(layout -> layout.getName().equals(layoutName));
        saveLayouts();
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
}