# Xbox手柄模式实现 - 虚拟摇杆和虚拟按钮

## 概述

本文档描述了为虚拟摇杆添加Xbox手柄模式以及虚拟按钮支持Xbox按键的实现。

## 功能实现

### 1. 虚拟摇杆 - Xbox手柄模式

虚拟摇杆现在支持三种模式：
- **键盘模式** (JOYSTICK_MODE_KEYBOARD): 映射为WASD等按键
- **鼠标模式** (JOYSTICK_MODE_MOUSE): 控制鼠标移动（用于瞄准）
- **Xbox手柄模式** (JOYSTICK_MODE_SDL_CONTROLLER): 模拟真实Xbox手柄摇杆

#### Xbox手柄模式特性

在Xbox手柄模式下，虚拟摇杆可以配置为：
- **左摇杆** (sdlUseRightStick = false): 通常用于角色移动
- **右摇杆** (sdlUseRightStick = true): 通常用于视角控制

#### 工作原理

1. 用户拖动虚拟摇杆时，`VirtualJoystick`计算摇杆的偏移量
2. 将偏移量转换为归一化的值 (-1.0 到 1.0)
3. 通过`ControlInputBridge.sendXboxLeftStick()`或`sendXboxRightStick()`发送到SDL
4. SDL将其转换为虚拟Xbox控制器的摇杆输入
5. 游戏接收到真实的模拟摇杆输入

### 2. 虚拟按钮 - Xbox按键支持

虚拟按钮现已完全支持Xbox控制器的所有按键，包括：

#### 标准按钮
- **A按钮** (XBOX_BUTTON_A = -200)
- **B按钮** (XBOX_BUTTON_B = -201)
- **X按钮** (XBOX_BUTTON_X = -202)
- **Y按钮** (XBOX_BUTTON_Y = -203)
- **Back按钮** (XBOX_BUTTON_BACK = -204)
- **Guide按钮** (XBOX_BUTTON_GUIDE = -205)
- **Start按钮** (XBOX_BUTTON_START = -206)

#### 摇杆按钮
- **L3 左摇杆按下** (XBOX_BUTTON_LEFT_STICK = -207)
- **R3 右摇杆按下** (XBOX_BUTTON_RIGHT_STICK = -208)

#### 肩部按钮
- **LB 左肩键** (XBOX_BUTTON_LB = -209)
- **RB 右肩键** (XBOX_BUTTON_RB = -210)

#### 方向键
- **D-Pad 上** (XBOX_BUTTON_DPAD_UP = -211)
- **D-Pad 下** (XBOX_BUTTON_DPAD_DOWN = -212)
- **D-Pad 左** (XBOX_BUTTON_DPAD_LEFT = -213)
- **D-Pad 右** (XBOX_BUTTON_DPAD_RIGHT = -214)

#### 扳机键
- **LT 左扳机** (XBOX_TRIGGER_LEFT = -220)
- **RT 右扳机** (XBOX_TRIGGER_RIGHT = -221)

## 代码改动

### 1. 布局文件修改

**文件**: `app/src/main/res/layout/dialog_side_edit.xml`

添加了Xbox手柄模式的UI元素：

```xml
<!-- 新增：Xbox手柄模式选项 -->
<RadioButton
    android:id="@+id/rb_sdl_controller_mode"
    android:layout_width="match_parent"
    android:layout_height="48dp"
    android:text="🎮 Xbox手柄模式（模拟真实摇杆）"
    android:textColor="?attr/textPrimary"
    android:textSize="14sp"
    android:paddingStart="12dp"
    android:paddingEnd="12dp" />

<!-- 新增：摇杆选择（左/右） -->
<TextView
    android:id="@+id/tv_sdl_stick_label"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Xbox摇杆选择"
    android:textColor="?attr/textSecondary"
    android:textSize="12sp"
    android:layout_marginTop="16dp"
    android:visibility="gone" />

<android.widget.RadioGroup
    android:id="@+id/rg_sdl_stick"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:visibility="gone"
    android:orientation="horizontal">

    <RadioButton
        android:id="@+id/rb_left_stick"
        android:layout_width="0dp"
        android:layout_height="48dp"
        android:layout_weight="1"
        android:text="🕹️ 左摇杆"
        android:textColor="?attr/textPrimary"
        android:textSize="14sp"
        android:paddingStart="12dp"
        android:paddingEnd="12dp" />

    <RadioButton
        android:id="@+id/rb_right_stick"
        android:layout_width="0dp"
        android:layout_height="48dp"
        android:layout_weight="1"
        android:text="🕹️ 右摇杆"
        android:textColor="?attr/textPrimary"
        android:textSize="14sp"
        android:paddingStart="12dp"
        android:paddingEnd="12dp" />
</android.widget.RadioGroup>
```

### 2. SideEditDialog.java 修改

**文件**: `app/src/main/java/com/app/ralaunch/controls/editor/SideEditDialog.java`

#### 新增UI元素字段

```java
private RadioButton mRbSdlControllerMode;
private TextView mTvSdlStickLabel;
private RadioGroup mRgSdlStick;
private RadioButton mRbLeftStick, mRbRightStick;
```

#### show()方法增强

```java
} else {
    mRbSdlControllerMode.setChecked(true);
    // SDL控制器模式：隐藏方向键映射，显示摇杆选择
    mTvJoystickKeysLabel.setVisibility(View.GONE);
    mBtnEditJoystickKeys.setVisibility(View.GONE);
    mTvSdlStickLabel.setVisibility(View.VISIBLE);
    mRgSdlStick.setVisibility(View.VISIBLE);
    
    // 设置左右摇杆选择
    if (data.sdlUseRightStick) {
        mRbRightStick.setChecked(true);
    } else {
        mRbLeftStick.setChecked(true);
    }
}
```

#### setupListeners()方法增强

```java
// 摇杆模式切换监听器 - 添加SDL控制器模式分支
} else {
    mCurrentData.joystickMode = ControlData.JOYSTICK_MODE_SDL_CONTROLLER;
    // 隐藏方向键映射，显示摇杆选择
    mTvJoystickKeysLabel.setVisibility(View.GONE);
    mBtnEditJoystickKeys.setVisibility(View.GONE);
    mTvSdlStickLabel.setVisibility(View.VISIBLE);
    mRgSdlStick.setVisibility(View.VISIBLE);
}

// 新增：SDL摇杆选择切换监听器
mRgSdlStick.setOnCheckedChangeListener((group, checkedId) -> {
    if (mCurrentData == null) return;
    
    if (checkedId == R.id.rb_left_stick) {
        mCurrentData.sdlUseRightStick = false;
    } else if (checkedId == R.id.rb_right_stick) {
        mCurrentData.sdlUseRightStick = true;
    }
    
    // 更新视图
    if (mCurrentView != null) {
        mCurrentView.updateData(mCurrentData);
    }
});
```

### 3. 已有的支持（无需修改）

以下类已经在之前的实现中支持Xbox功能，无需额外修改：

#### VirtualJoystick.java
- 已实现`handleMove()`方法中的SDL控制器模式分支
- 已实现`sendSDLStick()`方法发送摇杆输入
- 已实现`handleRelease()`方法中的摇杆回中逻辑

#### VirtualButton.java
- 已实现`sendInput()`方法中的Xbox按钮分支
- 已实现Xbox触发器（LT/RT）的支持
- 按键范围判断已正确处理Xbox按键码

#### ControlData.java
- 已定义所有Xbox按钮常量
- 已定义`JOYSTICK_MODE_SDL_CONTROLLER`常量
- 已定义`sdlUseRightStick`字段

#### KeyMapper.java
- 已在`getAllKeys()`中添加所有Xbox按键
- 可以通过按键选择对话框选择Xbox按键

## 使用流程

### 编辑虚拟摇杆设置

1. 长按虚拟摇杆进入编辑模式
2. 在侧边编辑面板中，找到"摇杆模式"选项
3. 选择"🎮 Xbox手柄模式（模拟真实摇杆）"
4. 在下方出现的"Xbox摇杆选择"中选择：
   - 🕹️ 左摇杆：用于角色移动
   - 🕹️ 右摇杆：用于视角控制
5. 点击"应用更改"保存

### 编辑虚拟按钮设置

1. 长按虚拟按钮进入编辑模式
2. 在侧边编辑面板中，找到"按键映射"选项
3. 点击按键选择按钮
4. 在弹出的列表中选择Xbox按键，例如：
   - 🎮 Xbox A
   - 🎮 Xbox B
   - 🎮 Xbox X
   - 🎮 Xbox Y
   - 🎮 Xbox LB (左肩)
   - 🎮 Xbox RB (右肩)
   - 🎮 Xbox LT (左扳机)
   - 🎮 Xbox RT (右扳机)
   - 等等...
5. 点击"应用更改"保存

## 技术细节

### 摇杆值归一化

虚拟摇杆的原始偏移量需要转换为 [-1.0, 1.0] 范围：

```java
private void sendSDLStick(float dx, float dy, float distance, float maxDistance) {
    float normalizedX = 0.0f;
    float normalizedY = 0.0f;

    // 死区处理
    float deadzone = mRadius * DEADZONE_PERCENT;
    if (distance > deadzone) {
        float adjustedDistance = distance - deadzone;
        float adjustedMax = maxDistance - deadzone;
        float ratio = adjustedDistance / adjustedMax;
        if (ratio > 1.0f) ratio = 1.0f;

        normalizedX = (dx / distance) * ratio;
        normalizedY = (dy / distance) * ratio;
    }

    // 发送到对应的摇杆
    if (mData.sdlUseRightStick) {
        mInputBridge.sendXboxRightStick(normalizedX, normalizedY);
    } else {
        mInputBridge.sendXboxLeftStick(normalizedX, normalizedY);
    }
}
```

### 按键类型判断

虚拟按钮通过按键码范围判断输入类型：

```java
private void sendInput(boolean isDown) {
    if (mData.keycode >= 0) {
        // 键盘按键 (0及以上)
        mInputBridge.sendKey(mData.keycode, isDown);
    } else if (mData.keycode >= ControlData.XBOX_TRIGGER_RIGHT && 
               mData.keycode <= ControlData.XBOX_TRIGGER_LEFT) {
        // Xbox触发器 (-220 到 -221)
        float triggerValue = isDown ? 1.0f : 0.0f;
        mInputBridge.sendXboxTrigger(mData.keycode, triggerValue);
    } else if (mData.keycode >= ControlData.XBOX_BUTTON_DPAD_RIGHT && 
               mData.keycode <= ControlData.XBOX_BUTTON_A) {
        // Xbox按钮 (-200 到 -214)
        mInputBridge.sendXboxButton(mData.keycode, isDown);
    } else if (mData.keycode >= ControlData.MOUSE_MIDDLE && 
               mData.keycode <= ControlData.MOUSE_LEFT) {
        // 鼠标按键 (-1 到 -3)
        // ... 鼠标处理代码
    }
}
```

## 优势

### 1. 更好的游戏兼容性
- 一些游戏对手柄摇杆支持更好
- 可以实现平滑的模拟移动，而不是离散的按键
- 适合需要精确控制的游戏（如赛车、飞行游戏）

### 2. 完整的手柄功能
- 支持所有Xbox控制器按键
- 可以模拟完整的手柄操作
- 适合手柄优化的游戏

### 3. 灵活的配置
- 每个虚拟摇杆可以独立配置左/右摇杆
- 可以同时使用键盘、鼠标、手柄模式
- 根据游戏需求灵活组合

## 注意事项

1. **模式选择**: 
   - 键盘模式适合需要精确8方向控制的游戏
   - 鼠标模式适合FPS游戏的瞄准
   - Xbox手柄模式适合手柄优化的游戏

2. **摇杆分配**:
   - 左摇杆通常用于角色移动
   - 右摇杆通常用于视角/瞄准控制
   - 避免两个虚拟摇杆使用相同的摇杆

3. **死区设置**:
   - 默认死区为12%，防止摇杆漂移
   - 在死区内不会触发任何输入

## 测试建议

1. 测试三种摇杆模式的切换
2. 测试左右摇杆的独立控制
3. 测试Xbox按键的响应
4. 测试触发器（LT/RT）的压力值
5. 测试摇杆死区和归一化
6. 测试配置的保存和加载

## 相关文档

- [XBOX_CONTROLLER_ARCHITECTURE.md](XBOX_CONTROLLER_ARCHITECTURE.md) - Xbox控制器架构
- [XBOX_CONTROLLER_EMULATION.md](XBOX_CONTROLLER_EMULATION.md) - Xbox控制器模拟
- [VIRTUAL_JOYSTICK_SDL_MODE.md](VIRTUAL_JOYSTICK_SDL_MODE.md) - 虚拟摇杆SDL模式

## 作者

实现日期: 2025-11-14

