# Terraria/tModLoader Android文本输入修复方案

## 🔍 问题根源分析

### 完整的文本输入链路

```
用户输入
  ↓
Android IME (软键盘)
  ↓
SDL_TEXTINPUT事件
  ↓
FNA: TextInputEXT.OnTextInput(char)
  ↓
FNA: TextInputEXT.TextInput事件触发
  ↓
tModLoader: FnaIme.OnCharCallback(char)
  ↓
❌ 检查: if (base.IsEnabled)  ← 这里被阻止！
  ↓
PlatformIme.OnKeyPress(char)
  ↓
Terraria监听器（添加到keyInt/keyString数组）
  ↓
Main.GetInputText() 返回文本
```

### 关键代码位置

1. **FnaIme.cs** (D:\tModLoader\tModLoader\src\tModLoader\ReLogic\Localization\IME\FnaIme.cs)
   ```csharp
   private void OnCharCallback(char key)
   {
       if (base.IsEnabled)  // ← 这里是问题！
           OnKeyPress(key);
   }
   ```

2. **Main.cs** (D:\tModLoader\tModLoader\src\tModLoader\Terraria\Main.cs)
   - 第9689-9694行：注册IME监听器
   - 第16857行：GetInputText()从keyInt/keyString读取文本

3. **TextInputEXT.cs** (D:\tModLoader\tModLoader\FNA\src\Input\TextInputEXT.cs)
   - 第58-61行：StartTextInput()启动SDL文本输入
   - 第82-88行：OnTextInput()触发事件

## 🔧 修复方案

### 方案选择：自动启用SDL文本输入

不修改tModLoader源码，而是在Android层自动调用SDL_StartTextInput()

### 实现位置

**文件：** `app/src/main/java/com/app/ralaunch/activity/GameActivity.java`

**修改点1：** `initializeVirtualControls()`方法
```java
// 延迟3秒调用SDL_StartTextInput()
mLayout.postDelayed(() -> {
    enableSDLTextInputForIME();
}, 3000);
```

**修改点2：** 新增`enableSDLTextInputForIME()`方法
尝试4种反射方法启用SDL文本输入：
1. `SDL.StartTextInput()`
2. `SDLActivity.startTextInput()`
3. `SDL.nativeStartTextInput()`
4. `SDL.nativeSetTextInputRect(0, 0, 1920, 1080)`

## 🎯 工作原理

1. **游戏启动时：**
   ```
   GameActivity.onCreate()
   → initializeVirtualControls()
   → 延迟3秒
   → enableSDLTextInputForIME()
   → SDL_StartTextInput() (通过反射)
   → Terraria的Platform.Get<IImeService>().Enable()
   → FnaIme.IsEnabled = true
   ```

2. **用户点击键盘按钮时：**
   ```
   VirtualButton.showKeyboard()
   → 显示Android IME
   → 用户输入文本
   → TextWatcher.onTextChanged()
   → GameActivity.sendTextToGame()
   → SDL: onNativeKeyDown/Up (模拟按键)
   → SDL_KEYDOWN/KEYUP事件
   → FNA: KeyboardState更新
   → Terraria接收按键输入
   ```

3. **为什么要延迟3秒？**
   - SDL需要时间初始化
   - Terraria需要注册IME监听器
   - 过早调用可能无效

## 📊 测试验证

### 监控日志
```bash
adb logcat -s "GameActivity:I" "GameActivity:D" "SDL:I"
```

### 期望输出
```
GameActivity: === 尝试启用SDL文本输入以支持IME ===
GameActivity: ✓ 通过XXX方法启用成功
GameActivity: ✓ Android IME已显示
GameActivity: Typing: 'a' -> KeyCode 29
```

### 失败情况
```
GameActivity: ⚠ 所有SDL文本输入启用方法都失败了
```

## 🔄 备选方案

### 方案A：修改FnaIme源码（侵入式）

修改 `D:\tModLoader\tModLoader\src\tModLoader\ReLogic\Localization\IME\FnaIme.cs`：

```csharp
private void OnCharCallback(char key)
{
    #if ANDROID
    // Android补丁：无条件转发
    OnKeyPress(key);
    #else
    if (base.IsEnabled)
        OnKeyPress(key);
    #endif
}
```

**优点：** 100%可靠
**缺点：** 需要重新编译tModLoader

### 方案B：C++层补丁（复杂）

在SDL层直接调用 `SDL_StartTextInput()`：

**文件：** `app/src/main/cpp/jni_bridge.c`
```c
// JNI方法：强制启用SDL文本输入
JNIEXPORT void JNICALL
Java_com_app_ralaunch_activity_GameActivity_nativeEnableTextInput(JNIEnv* env, jclass cls) {
    SDL_StartTextInput();
}
```

**优点：** 不依赖反射
**缺点：** 需要修改C++代码和重新编译

## 💡 使用说明

### 对于用户

1. 启动游戏
2. 等待3秒（游戏初始化完成）
3. 点击"键盘"按钮
4. 输入文字
5. 按回车确认

### 对于开发者

如果自动启用失败，可以手动检查：

```bash
# 查看SDL是否加载
adb logcat -s "SDL:I"

# 查看文本输入状态
adb logcat | grep "StartTextInput\|TextInput"

# 查看IME状态
adb logcat | grep "IME\|InputMethod"
```

## ⚙️ 技术细节

### SDL_StartTextInput的作用

```c
// SDL2源码
void SDL_StartTextInput(void)
{
    if (_this && _this->StartTextInput) {
        _this->StartTextInput(_this);
    }
}
```

在Android上会：
1. 显示软键盘（如果需要）
2. 启用SDL_TEXTINPUT事件
3. 激活InputConnection

### FNA的处理

```csharp
// FNA源码
public static void StartTextInput()
{
    FNAPlatform.StartTextInput();
}
```

实际调用SDL_StartTextInput()。

### Terraria的IME服务

```csharp
// Terraria源码
public void HandleIME()
{
    if (_imeToggle != PlayerInput.WritingText) {
        _imeToggle = PlayerInput.WritingText;
        if (_imeToggle)
            Platform.Get<IImeService>().Enable();
    }
}
```

只有在 `PlayerInput.WritingText = true` 时才启用IME。

## 🎯 成功标志

1. **日志显示：**
   - `✓ 通过XXX启用成功`
   - `✓ Android IME已显示`

2. **游戏内测试：**
   - 打开告示牌编辑
   - 游戏自带虚拟键盘正常显示
   - 蓝牙键盘可以输入

3. **自定义键盘按钮：**
   - 点击弹出Android软键盘
   - 输入文字能进入游戏

## 📝 注意事项

1. **延迟时间：** 3秒可能需要根据设备性能调整
2. **多次调用：** SDL_StartTextInput()可以安全地多次调用
3. **状态保持：** 一旦启用，通常会保持到游戏退出
4. **兼容性：** 不同SDL版本API可能略有差异

## 🔗 相关资源

- SDL Wiki: https://wiki.libsdl.org/SDL_StartTextInput
- FNA GitHub: https://github.com/FNA-XNA/FNA
- tModLoader GitHub: https://github.com/tModLoader/tModLoader
- Android IME Guide: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method

---

**版本：** 1.0  
**更新日期：** 2025-11-04  
**状态：** ✅ 实现完成，待测试验证


