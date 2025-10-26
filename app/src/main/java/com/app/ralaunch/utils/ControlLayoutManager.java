// ControlLayoutManager.java
package com.app.ralaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;

import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.model.ControlElement;

import org.json.JSONException;

import java.io.*;
import java.util.*;

/**
 * 控制布局管理器 - 参考 PojavLauncher 设计
 * 提供布局的创建、保存、加载、导入、导出等功能
 */
public class ControlLayoutManager {
    private static final String TAG = "ControlLayoutManager";
    private static final String PREF_NAME = "control_layouts";
    private static final String KEY_CURRENT_LAYOUT = "current_layout";
    private static final String LAYOUTS_DIR = "control_layouts";
    private static final String PRESETS_DIR = "preset_layouts";
    
    private Context context;
    private SharedPreferences preferences;
    private File layoutsDirectory;
    private File presetsDirectory;
    private List<ControlLayout> layouts;
    private String currentLayoutName;

    public ControlLayoutManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // 创建布局目录
        this.layoutsDirectory = new File(context.getFilesDir(), LAYOUTS_DIR);
        this.presetsDirectory = new File(context.getFilesDir(), PRESETS_DIR);
        
        if (!layoutsDirectory.exists()) {
            layoutsDirectory.mkdirs();
        }
        if (!presetsDirectory.exists()) {
            presetsDirectory.mkdirs();
        }
        
        loadLayouts();
        
        // 如果没有布局，创建默认预设
        if (layouts.isEmpty()) {
            createDefaultPresets();
            loadLayouts();
        }
    }

    // 加载所有布局
    private void loadLayouts() {
        layouts = new ArrayList<>();
        
        File[] layoutFiles = layoutsDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        if (layoutFiles != null) {
            for (File file : layoutFiles) {
                try {
                    ControlLayout layout = ControlLayout.fromFile(file);
                    layouts.add(layout);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Failed to load layout: " + file.getName(), e);
                }
            }
        }
        
        currentLayoutName = preferences.getString(KEY_CURRENT_LAYOUT, null);
        if (currentLayoutName == null && !layouts.isEmpty()) {
            currentLayoutName = layouts.get(0).getName();
        }
    }

    // 创建默认预设布局
    private void createDefaultPresets() {
        try {
            // 1. tModLoader 默认布局
            createTModLoaderPreset();
            
            // 2. Stardew Valley 布局
            createStardewValleyPreset();
            
            // 3. 通用 FNA 游戏布局
            createGenericFNAPreset();
            
            Log.i(TAG, "Default presets created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create default presets", e);
        }
    }
    
    // tModLoader 预设
    private void createTModLoaderPreset() throws IOException, JSONException {
        ControlLayout layout = new ControlLayout("tModLoader 默认");
        layout.setDescription("适合 tModLoader 的控制布局");
        layout.setAuthor("Rotating Art Launcher");
        layout.setDefault(true);
        
        // 左侧摇杆 (移动)
        ControlElement leftJoystick = new ControlElement("left_joystick", ControlElement.ElementType.JOYSTICK, "移动");
        leftJoystick.setX(0.05f);
        leftJoystick.setY(0.55f);
        leftJoystick.setWidth(150);
        leftJoystick.setHeight(150);
        leftJoystick.setDeadzone(0.15f);
        leftJoystick.setSensitivity(1.2f);
        layout.addElement(leftJoystick);
        
        // 右侧按钮组
        ControlElement jumpButton = new ControlElement("jump", ControlElement.ElementType.BUTTON, "跳跃");
        jumpButton.setX(0.85f);
        jumpButton.setY(0.60f);
        jumpButton.setWidth(80);
        jumpButton.setHeight(80);
        jumpButton.setKeyCode(KeyEvent.KEYCODE_SPACE);
        jumpButton.setDisplayText("跳");
        layout.addElement(jumpButton);
        
        ControlElement hookButton = new ControlElement("hook", ControlElement.ElementType.BUTTON, "钩爪");
        hookButton.setX(0.75f);
        hookButton.setY(0.70f);
        hookButton.setWidth(75);
        hookButton.setHeight(75);
        hookButton.setKeyCode(KeyEvent.KEYCODE_E);
        hookButton.setDisplayText("钩");
        layout.addElement(hookButton);
        
        ControlElement healButton = new ControlElement("heal", ControlElement.ElementType.BUTTON, "治疗");
        healButton.setX(0.75f);
        healButton.setY(0.50f);
        hookButton.setWidth(75);
        hookButton.setHeight(75);
        healButton.setKeyCode(KeyEvent.KEYCODE_H);
        healButton.setDisplayText("治");
        layout.addElement(healButton);
        
        // 触摸板区域（屏幕中央）
        ControlElement touchpad = new ControlElement("mouse_area", ControlElement.ElementType.MOUSE_AREA, "视角");
        touchpad.setX(0.25f);
        touchpad.setY(0.20f);
        touchpad.setWidth(300);
        touchpad.setHeight(350);
        touchpad.setOpacity(0.1f);
        touchpad.setScrollSensitivity(1.0f);
        layout.addElement(touchpad);
        
        // 攻击按钮（屏幕右上）
        ControlElement attackButton = new ControlElement("attack", ControlElement.ElementType.BUTTON, "攻击");
        attackButton.setX(0.90f);
        attackButton.setY(0.15f);
        attackButton.setWidth(70);
        attackButton.setHeight(70);
        attackButton.setKeyCode(KeyEvent.KEYCODE_BUTTON_L1);  // 左键
        attackButton.setDisplayText("攻");
        layout.addElement(attackButton);
        
        // 快捷栏按钮
        for (int i = 1; i <= 5; i++) {
            ControlElement hotkey = new ControlElement("hotkey_" + i, ControlElement.ElementType.BUTTON, "" + i);
            hotkey.setX(0.25f + (i - 1) * 0.12f);
            hotkey.setY(0.85f);
            hotkey.setWidth(60);
            hotkey.setHeight(60);
            hotkey.setKeyCode(KeyEvent.KEYCODE_1 - 1 + i);
            hotkey.setDisplayText("" + i);
            layout.addElement(hotkey);
        }
        
        // 保存布局
        File file = new File(layoutsDirectory, sanitizeFileName(layout.getName()) + ".json");
        layout.saveToFile(file);
    }
    
    // Stardew Valley 预设
    private void createStardewValleyPreset() throws IOException, JSONException {
        ControlLayout layout = new ControlLayout("Stardew Valley");
        layout.setDescription("适合星露谷物语的控制布局");
        layout.setAuthor("Rotating Art Launcher");
        
        // 左侧十字键
        ControlElement crossKey = new ControlElement("cross_key", ControlElement.ElementType.CROSS_KEY, "方向");
        crossKey.setX(0.05f);
        crossKey.setY(0.60f);
        crossKey.setWidth(140);
        crossKey.setHeight(140);
        layout.addElement(crossKey);
        
        // A 按钮（确认/使用工具）
        ControlElement aButton = new ControlElement("a_button", ControlElement.ElementType.BUTTON, "确认");
        aButton.setX(0.85f);
        aButton.setY(0.65f);
        aButton.setWidth(80);
        aButton.setHeight(80);
        aButton.setKeyCode(KeyEvent.KEYCODE_ENTER);
        aButton.setDisplayText("A");
        layout.addElement(aButton);
        
        // B 按钮（取消）
        ControlElement bButton = new ControlElement("b_button", ControlElement.ElementType.BUTTON, "取消");
        bButton.setX(0.75f);
        bButton.setY(0.55f);
        bButton.setWidth(75);
        bButton.setHeight(75);
        bButton.setKeyCode(KeyEvent.KEYCODE_ESCAPE);
        bButton.setDisplayText("B");
        layout.addElement(bButton);
        
        // X 按钮（菜单）
        ControlElement xButton = new ControlElement("x_button", ControlElement.ElementType.BUTTON, "菜单");
        xButton.setX(0.75f);
        xButton.setY(0.75f);
        xButton.setWidth(75);
        xButton.setHeight(75);
        xButton.setKeyCode(KeyEvent.KEYCODE_E);
        xButton.setDisplayText("X");
        layout.addElement(xButton);
        
        // Y 按钮（工具栏）
        ControlElement yButton = new ControlElement("y_button", ControlElement.ElementType.BUTTON, "工具");
        yButton.setX(0.95f);
        yButton.setY(0.55f);
        yButton.setWidth(75);
        yButton.setHeight(75);
        yButton.setKeyCode(KeyEvent.KEYCODE_T);
        yButton.setDisplayText("Y");
        layout.addElement(yButton);
        
        // 地图按钮
        ControlElement mapButton = new ControlElement("map", ControlElement.ElementType.BUTTON, "地图");
        mapButton.setX(0.02f);
        mapButton.setY(0.02f);
        mapButton.setWidth(65);
        mapButton.setHeight(65);
        mapButton.setKeyCode(KeyEvent.KEYCODE_M);
        mapButton.setDisplayText("地图");
        layout.addElement(mapButton);
        
        File file = new File(layoutsDirectory, sanitizeFileName(layout.getName()) + ".json");
        layout.saveToFile(file);
    }
    
    // 通用 FNA 游戏预设
    private void createGenericFNAPreset() throws IOException, JSONException {
        ControlLayout layout = new ControlLayout("通用FNA游戏");
        layout.setDescription("适合大多数 FNA/XNA 游戏");
        layout.setAuthor("Rotating Art Launcher");
        
        // 左摇杆
        ControlElement leftStick = new ControlElement("left_stick", ControlElement.ElementType.JOYSTICK, "移动");
        leftStick.setX(0.08f);
        leftStick.setY(0.60f);
        leftStick.setWidth(140);
        leftStick.setHeight(140);
        layout.addElement(leftStick);
        
        // 右摇杆
        ControlElement rightStick = new ControlElement("right_stick", ControlElement.ElementType.JOYSTICK, "视角");
        rightStick.setX(0.75f);
        rightStick.setY(0.60f);
        rightStick.setWidth(140);
        rightStick.setHeight(140);
        layout.addElement(rightStick);
        
        // 按钮组
        String[] buttonNames = {"A", "B", "X", "Y"};
        int[] keyCodes = {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, 
                         KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y};
        float[] xPos = {0.90f, 0.82f, 0.82f, 0.98f};
        float[] yPos = {0.40f, 0.48f, 0.32f, 0.32f};
        
        for (int i = 0; i < buttonNames.length; i++) {
            ControlElement button = new ControlElement("button_" + buttonNames[i].toLowerCase(), 
                    ControlElement.ElementType.BUTTON, buttonNames[i]);
            button.setX(xPos[i]);
            button.setY(yPos[i]);
            button.setWidth(70);
            button.setHeight(70);
            button.setKeyCode(keyCodes[i]);
            button.setDisplayText(buttonNames[i]);
            layout.addElement(button);
        }
        
        // L/R 扳机
        ControlElement lTrigger = new ControlElement("l_trigger", ControlElement.ElementType.TRIGGER_BUTTON, "L");
        lTrigger.setX(0.02f);
        lTrigger.setY(0.05f);
        lTrigger.setWidth(100);
        lTrigger.setHeight(60);
        lTrigger.setKeyCode(KeyEvent.KEYCODE_BUTTON_L1);
        layout.addElement(lTrigger);
        
        ControlElement rTrigger = new ControlElement("r_trigger", ControlElement.ElementType.TRIGGER_BUTTON, "R");
        rTrigger.setX(0.88f);
        rTrigger.setY(0.05f);
        rTrigger.setWidth(100);
        rTrigger.setHeight(60);
        rTrigger.setKeyCode(KeyEvent.KEYCODE_BUTTON_R1);
        layout.addElement(rTrigger);
        
        File file = new File(layoutsDirectory, sanitizeFileName(layout.getName()) + ".json");
        layout.saveToFile(file);
    }

    // 保存布局
    public void saveLayout(ControlLayout layout) {
        try {
            File file = new File(layoutsDirectory, sanitizeFileName(layout.getName()) + ".json");
            layout.saveToFile(file);
            
            // 更新内存中的布局列表
            boolean found = false;
            for (int i = 0; i < layouts.size(); i++) {
                if (layouts.get(i).getName().equals(layout.getName())) {
                    layouts.set(i, layout);
                    found = true;
                    break;
                }
            }
            if (!found) {
                layouts.add(layout);
            }
            
            Log.i(TAG, "Layout saved: " + layout.getName());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to save layout", e);
        }
    }

    // 删除布局
    public boolean deleteLayout(String layoutName) {
        File file = new File(layoutsDirectory, sanitizeFileName(layoutName) + ".json");
        boolean deleted = file.delete();
        
        if (deleted) {
            layouts.removeIf(layout -> layout.getName().equals(layoutName));
            
            // 如果删除的是当前布局，切换到第一个
            if (layoutName.equals(currentLayoutName) && !layouts.isEmpty()) {
                setCurrentLayout(layouts.get(0).getName());
            }
            
            Log.i(TAG, "Layout deleted: " + layoutName);
        }
        
        return deleted;
    }

    // 导出布局
    public File exportLayout(ControlLayout layout, File exportDir) throws IOException, JSONException {
        File exportFile = new File(exportDir, sanitizeFileName(layout.getName()) + ".json");
        layout.saveToFile(exportFile);
        return exportFile;
    }

    // 导入布局
    public ControlLayout importLayout(File file) throws IOException, JSONException {
        ControlLayout layout = ControlLayout.fromFile(file);
        
        // 检查是否已存在同名布局
        String originalName = layout.getName();
        int suffix = 1;
        while (getLayoutByName(layout.getName()) != null) {
            layout.setName(originalName + " (" + suffix + ")");
            suffix++;
        }
        
        saveLayout(layout);
        return layout;
    }

    // 复制布局
    public ControlLayout duplicateLayout(ControlLayout layout) {
        String newName = layout.getName() + " (副本)";
        int suffix = 1;
        while (getLayoutByName(newName) != null) {
            newName = layout.getName() + " (副本" + suffix + ")";
            suffix++;
        }
        
        ControlLayout copy = layout.copy(newName);
        if (copy != null) {
            saveLayout(copy);
        }
        return copy;
    }

    // 获取所有布局
    public List<ControlLayout> getAllLayouts() {
        return new ArrayList<>(layouts);
    }

    // 根据名称获取布局
    public ControlLayout getLayoutByName(String name) {
        for (ControlLayout layout : layouts) {
            if (layout.getName().equals(name)) {
                return layout;
            }
        }
        return null;
    }

    // 获取当前布局
    public ControlLayout getCurrentLayout() {
        ControlLayout layout = getLayoutByName(currentLayoutName);
        if (layout == null && !layouts.isEmpty()) {
            layout = layouts.get(0);
            setCurrentLayout(layout.getName());
        }
        return layout;
    }

    // 设置当前布局
    public void setCurrentLayout(String layoutName) {
        this.currentLayoutName = layoutName;
        preferences.edit()
                .putString(KEY_CURRENT_LAYOUT, layoutName)
                .apply();
        Log.i(TAG, "Current layout set to: " + layoutName);
    }

    // 获取布局数量
    public int getLayoutCount() {
        return layouts.size();
    }

    // 布局是否存在
    public boolean layoutExists(String name) {
        return getLayoutByName(name) != null;
    }

    // 重新加载所有布局
    public void reloadLayouts() {
        loadLayouts();
    }

    // 清理文件名
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\-_]", "_");
    }

    // 获取布局文件
    public File getLayoutFile(String layoutName) {
        return new File(layoutsDirectory, sanitizeFileName(layoutName) + ".json");
    }
    
    // 获取布局目录
    public File getLayoutsDirectory() {
        return layoutsDirectory;
    }
}