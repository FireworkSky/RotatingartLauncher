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
            // 创建默认布局
            createDefaultLayouts();
        }

        currentLayoutName = preferences.getString(KEY_CURRENT_LAYOUT, "默认布局");
    }

    private void createDefaultLayouts() {
        // 创建类似 Pojav 的默认布局
        ControlLayout defaultLayout = new ControlLayout("默认布局");

        // 添加方向键
        ControlElement crossKey = new ControlElement("cross", ControlElement.ElementType.CROSS_KEY, "方向键");
        crossKey.setX(0.1f);
        crossKey.setY(0.6f);
        crossKey.setWidth(200);
        crossKey.setHeight(200);
        defaultLayout.addElement(crossKey);

        // 添加跳跃按钮
        ControlElement jumpButton = new ControlElement("jump", ControlElement.ElementType.BUTTON, "跳跃");
        jumpButton.setX(0.8f);
        jumpButton.setY(0.6f);
        jumpButton.setWidth(120);
        jumpButton.setHeight(120);
        jumpButton.setKeyCode(32); // 空格键
        defaultLayout.addElement(jumpButton);

        // 添加攻击按钮
        ControlElement attackButton = new ControlElement("attack", ControlElement.ElementType.BUTTON, "攻击");
        attackButton.setX(0.7f);
        attackButton.setY(0.4f);
        attackButton.setWidth(100);
        attackButton.setHeight(100);
        attackButton.setKeyCode(29); // Ctrl 键
        defaultLayout.addElement(attackButton);

        layouts.add(defaultLayout);
        saveLayouts();
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