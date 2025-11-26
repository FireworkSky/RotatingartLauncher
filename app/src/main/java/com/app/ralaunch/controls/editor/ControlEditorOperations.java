package com.app.ralaunch.controls.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.util.DisplayMetrics;
import android.widget.Toast;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlConfig;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.ControlDataConverter;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.app.ralaunch.utils.AppLogger;

/**
 * 控件编辑器操作类
 * 
 * 统一管理控件编辑器的共同操作，包括：
 * - 添加按钮/摇杆
 * - 保存/加载布局
 * - 摇杆模式设置
 * - 重置为默认布局
 * 
 * 供 GameControlEditorManager 和 ControlEditorActivity 共同使用
 */
public class ControlEditorOperations {
    private static final String TAG = "ControlEditorOperations";
    
    /**
     * 添加按钮到配置
     * 
     * @param config 控件配置
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 新创建的按钮数据
     */
    public static ControlData addButton(ControlConfig config, int screenWidth, int screenHeight) {
        if (config == null || config.controls == null) {
            return null;
        }
        
        ControlData button = new ControlData("按钮", ControlData.TYPE_BUTTON);
        button.x = screenWidth / 2f;
        button.y = screenHeight / 2f;
        button.width = 100;
        button.height = 100;
        button.opacity = 0.7f;
        button.visible = true;
        button.keycode = 62; // Space键
        
        config.controls.add(button);
        return button;
    }
    
    /**
     * 添加摇杆到配置
     * 
     * @param config 控件配置
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 新创建的摇杆数据
     */
    public static ControlData addJoystick(ControlConfig config, int screenWidth, int screenHeight) {
        if (config == null || config.controls == null) {
            return null;
        }
        
        ControlData joystick = ControlData.createDefaultJoystick();
        joystick.x = screenWidth / 2f;
        joystick.y = screenHeight / 2f;
        
        config.controls.add(joystick);
        return joystick;
    }
    
    /**
     * 添加文本控件到配置
     * 
     * @param config 控件配置
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 新创建的文本控件数据
     */
    public static ControlData addText(ControlConfig config, int screenWidth, int screenHeight) {
        if (config == null || config.controls == null) {
            return null;
        }
        
        ControlData text = new ControlData("文本", ControlData.TYPE_TEXT);
        text.x = screenWidth / 2f;
        text.y = screenHeight / 2f;
        text.width = 150;
        text.height = 150;
        text.opacity = 0.7f;
        text.bgColor = 0xFF808080; // 灰色背景（更清晰可见）
        text.visible = true;
        text.shape = ControlData.SHAPE_RECTANGLE; // 默认方形
        text.displayText = "文本"; // 默认文本
        text.keycode = ControlData.SDL_SCANCODE_UNKNOWN; // 文本控件不支持按键映射
        
        config.controls.add(text);
        return text;
    }
    
    /**
     * 显示摇杆模式批量设置对话框
     * 
     * @param context 上下文
     * @param config 控件配置
     * @param onLayoutUpdated 布局更新回调
     */
    public static void showJoystickModeDialog(Context context, ControlConfig config, 
                                            Runnable onLayoutUpdated) {
        if (config == null || config.controls == null) return;
        
        // 统计当前布局中的摇杆数量
        int joystickCount = 0;
        for (ControlData control : config.controls) {
            if (control.type == ControlData.TYPE_JOYSTICK) {
                joystickCount++;
            }
        }
        
        if (joystickCount == 0) {
            Toast.makeText(context, "当前布局中没有摇杆", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final String[] modes = {
            "键盘按键模式\n使用 WASD 等按键控制移动",
            "鼠标移动模式\n控制鼠标指针移动（用于瞄准）",
            "SDL 控制器模式\n模拟真实游戏手柄摇杆"
        };
        
        new AlertDialog.Builder(context)
            .setTitle("摇杆模式设置")
            .setMessage("将为所有 " + joystickCount + " 个摇杆设置统一模式")
            .setItems(modes, (dialog, which) -> {
                int newMode;
                String modeName;
                
                switch (which) {
                    case 0:
                        newMode = ControlData.JOYSTICK_MODE_KEYBOARD;
                        modeName = "键盘按键模式";
                        break;
                    case 1:
                        newMode = ControlData.JOYSTICK_MODE_MOUSE;
                        modeName = "鼠标移动模式";
                        break;
                    case 2:
                        newMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
                        modeName = "SDL控制器模式";
                        break;
                    default:
                        return;
                }
                
                // 批量更新所有摇杆的模式
                int updatedCount = updateJoystickModes(config, newMode);
                
                // 通知布局已更新
                if (onLayoutUpdated != null) {
                    onLayoutUpdated.run();
                }
                
                Toast.makeText(context,
                    "已将 " + updatedCount + " 个摇杆设置为" + modeName,
                    Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 批量更新摇杆模式
     * 
     * @param config 控件配置
     * @param newMode 新模式
     * @return 更新的摇杆数量
     */
    private static int updateJoystickModes(ControlConfig config, int newMode) {
        int updatedCount = 0;
        
        for (ControlData control : config.controls) {
            if (control.type == ControlData.TYPE_JOYSTICK) {
                control.joystickMode = newMode;
                
                // 根据模式设置合适的默认值
                if (newMode == ControlData.JOYSTICK_MODE_KEYBOARD) {
                    // 键盘模式：确保有按键映射
                    if (control.joystickKeys == null || control.joystickKeys.length < 4) {
                        control.joystickKeys = new int[]{
                            ControlData.SDL_SCANCODE_W,  // up
                            ControlData.SDL_SCANCODE_D,  // right
                            ControlData.SDL_SCANCODE_S,  // down
                            ControlData.SDL_SCANCODE_A   // left
                        };
                    }
                } else if (newMode == ControlData.JOYSTICK_MODE_MOUSE) {
                    // 鼠标模式：清除按键映射
                    control.joystickKeys = null;
                } else {
                    // SDL控制器模式：清除按键映射，设置默认为左摇杆
                    control.joystickKeys = null;
                    if (!control.name.contains("右")) {
                        control.xboxUseRightStick = false;
                    }
                }
                updatedCount++;
            }
        }
        
        return updatedCount;
    }
    
    /**
     * 保存布局到 ControlLayoutManager
     * 
     * @param context 上下文
     * @param config 控件配置
     * @param layoutName 布局名称
     * @return 是否保存成功
     */
    public static boolean saveLayout(Context context, ControlConfig config, String layoutName) {
        if (config == null) {
            Toast.makeText(context, "没有布局可保存", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        try {
            ControlLayoutManager manager = new ControlLayoutManager(context);
            com.app.ralaunch.model.ControlLayout layout = manager.getCurrentLayout();
            if (layout == null || !layout.getName().equals(layoutName)) {
                layout = new com.app.ralaunch.model.ControlLayout(layoutName != null ? layoutName : manager.getCurrentLayoutName());
            }
            
            // 清空现有元素
            layout.getElements().clear();
            
            // 转换 ControlData 为 ControlElement
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            for (ControlData data : config.controls) {
                ControlElement element = ControlDataConverter.dataToElement(data, 
                    metrics.widthPixels, metrics.heightPixels);
                if (element != null) {
                    layout.addElement(element);
                }
            }
            
            // 保存到管理器
            manager.saveLayout(layout);
            
            Toast.makeText(context, "布局已保存", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to save layout", e);
            Toast.makeText(context, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    
    /**
     * 从 ControlLayoutManager 加载布局
     * 
     * @param context 上下文
     * @param layoutName 布局名称（null 表示使用当前布局）
     * @return 加载的配置，如果失败返回 null
     */
    public static ControlConfig loadLayout(Context context, String layoutName) {
        try {
            ControlLayoutManager manager = new ControlLayoutManager(context);
            com.app.ralaunch.model.ControlLayout layout;
            
            if (layoutName != null) {
                // 查找指定名称的布局
                layout = null;
                for (com.app.ralaunch.model.ControlLayout l : manager.getLayouts()) {
                    if (l.getName().equals(layoutName)) {
                        layout = l;
                        break;
                    }
                }
            } else {
                // 使用当前布局
                layout = manager.getCurrentLayout();
            }
            
            if (layout == null || layout.getElements().isEmpty()) {
                return null;
            }
            
            // 转换 ControlElement 列表为 ControlConfig
            ControlConfig config = new ControlConfig();
            config.name = layout.getName();
            config.version = 1;
            config.controls = new java.util.ArrayList<>();
            
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            for (ControlElement element : layout.getElements()) {
                ControlData data = ControlDataConverter.elementToData(element, 
                    metrics.widthPixels, metrics.heightPixels);
                if (data != null) {
                    config.controls.add(data);
                }
            }
            
            return config;
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to load layout", e);
            return null;
        }
    }
    
    /**
     * 重置为默认布局
     * 
     * @param context 上下文
     * @param controlLayout ControlLayout 实例（用于加载默认布局）
     * @param onResetComplete 重置完成回调
     */
    public static void resetToDefaultLayout(Context context, com.app.ralaunch.controls.ControlLayout controlLayout, 
                                           Runnable onResetComplete) {
        new AlertDialog.Builder(context)
            .setTitle("重置为默认布局")
            .setMessage("确定要重置为默认布局吗？")
            .setPositiveButton(R.string.game_menu_yes, (dialog, which) -> {
                if (controlLayout != null) {
                    controlLayout.loadDefaultLayout();
                    Toast.makeText(context, "已重置为默认布局", Toast.LENGTH_SHORT).show();
                }
                if (onResetComplete != null) {
                    onResetComplete.run();
                }
            })
            .setNegativeButton(R.string.game_menu_no, null)
            .show();
    }
}

