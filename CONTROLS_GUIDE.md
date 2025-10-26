# 自定义控制布局系统使用指南

## 📖 简介

Rotating Art Launcher 提供了一个完整的虚拟控制器系统，参考 PojavLauncher 的设计理念，让你可以在 Android 设备上流畅地玩 FNA/XNA 游戏。

## 🎮 控制元素类型

### 1. BUTTON (普通按钮)
- 用途：触发单个按键或鼠标点击
- 支持：键位绑定、连按、Toggle 模式
- 示例：跳跃、攻击、确认

### 2. JOYSTICK (虚拟摇杆)
- 用途：模拟游戏手柄摇杆
- 支持：死区设置、灵敏度调节、方向锁定
- 示例：角色移动、视角控制

### 3. CROSS_KEY (十字键)
- 用途：方向控制，四个独立按键组合
- 支持：上下左右键位绑定
- 示例：Stardew Valley 移动

### 4. TRIGGER_BUTTON (扳机键)
- 用途：L/R 扳机按键
- 支持：模拟游戏手柄扳机
- 示例：瞄准、加速

### 5. TOUCHPAD (触摸板)
- 用途：模拟鼠标滚轮/触摸板
- 支持：灵敏度、反转轴向
- 示例：地图缩放、菜单滚动

### 6. MOUSE_AREA (鼠标区域)
- 用途：模拟鼠标移动
- 支持：相对移动、绝对定位
- 示例：第一人称视角控制

### 7. MACRO_BUTTON (宏按钮)
- 用途：执行一系列按键序列
- 支持：自定义宏脚本
- 示例：技能连招、快速建造

### 8. GROUP (元素组)
- 用途：将多个控制元素组合在一起
- 支持：统一显示/隐藏、批量操作

## 🛠️ 控制属性

### 位置和大小
- **x, y**: 相对位置 (0-1)
- **width, height**: 像素大小
- **rotation**: 旋转角度
- **cornerRadius**: 圆角半径

### 外观设置
- **backgroundColor**: 背景颜色
- **pressedColor**: 按下时颜色
- **borderColor**: 边框颜色
- **textColor**: 文字颜色
- **opacity**: 不透明度 (0-1)
- **textSize**: 文字大小

### 行为设置
- **toggle**: Toggle 模式（按一次保持，再按释放）
- **passthrough**: 穿透模式（不拦截触摸事件）
- **swipeClick**: 滑动点击
- **repeatEnabled**: 启用连按
- **repeatDelay**: 连按延迟 (ms)

### 显示设置
- **visibility**: 可见性
  - `ALWAYS`: 始终显示
  - `IN_GAME`: 仅游戏中
  - `IN_MENU`: 仅菜单中
  - `HIDDEN`: 隐藏

### 摇杆特有
- **deadzone**: 死区 (0-1)
- **sensitivity**: 灵敏度
- **lockDirection**: 锁定方向（4/8方向）

### 触摸板特有
- **scrollSensitivity**: 滚动灵敏度
- **invertX**: X 轴反转
- **invertY**: Y 轴反转

## 📦 预设布局

### 1. tModLoader 默认
```
特点：
- 左侧摇杆控制移动
- 屏幕中央触摸板控制视角
- 右侧按钮组：跳跃、钩爪、治疗
- 顶部攻击按钮
- 底部快捷栏 (1-5)

适合：横板动作游戏、Terraria
```

### 2. Stardew Valley
```
特点：
- 左侧十字键控制移动
- 右侧 A/B/X/Y 按钮布局
- 顶部地图按钮

适合：像素风 RPG、农场模拟游戏
```

### 3. 通用 FNA 游戏
```
特点：
- 双摇杆布局（移动+视角）
- Xbox 风格按钮布局 (A/B/X/Y)
- L/R 扳机按钮

适合：大多数 3D 游戏、第三人称游戏
```

## 🎨 创建自定义布局

### 方法 1：使用编辑器

1. 打开**控制布局管理器**
2. 点击**新建布局**
3. 输入布局名称和描述
4. 进入**编辑模式**
5. 从**组件库**拖拽控件到屏幕
6. 长按控件进入**属性编辑**
7. 保存布局

### 方法 2：复制现有布局

1. 选择一个预设布局
2. 点击**复制**
3. 修改副本
4. 保存为新布局

### 方法 3：导入布局文件

1. 获取 `.json` 布局文件
2. 打开**布局管理器**
3. 点击**导入**
4. 选择文件

## 💡 布局设计建议

### 摇杆位置
- **左摇杆**：推荐放在左下角 (0.05-0.15, 0.55-0.65)
- **右摇杆**：推荐放在右下角 (0.70-0.80, 0.55-0.65)

### 按钮大小
- **主要按钮** (如跳跃)：80-100px
- **次要按钮**：60-80px
- **快捷栏按钮**：50-70px

### 透明度设置
- **触摸板/鼠标区域**：0.1-0.3 (高透明)
- **常用按钮**：0.6-0.8
- **重要按钮**：0.8-1.0

### 颜色建议
- **移动控件**：蓝色系
- **攻击按钮**：红色系
- **功能按钮**：绿色系
- **菜单按钮**：灰色系

## 📱 游戏内使用

### 启用控制覆盖层

```java
// 在 GameActivity 中
OverlayControlView overlayView = new OverlayControlView(this);
ControlLayoutManager layoutManager = new ControlLayoutManager(this);
overlayView.setControlLayout(layoutManager.getCurrentLayout());

// 添加到视图层级
addContentView(overlayView, new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.MATCH_PARENT
));

// 设置事件监听
overlayView.setOnControlEventListener(new OverlayControlView.OnControlEventListener() {
    @Override
    public void onButtonDown(ControlElement element) {
        // 处理按钮按下
        int keyCode = element.getKeyCode();
        // 发送按键事件到游戏
    }
    
    @Override
    public void onButtonUp(ControlElement element) {
        // 处理按钮释放
    }
    
    @Override
    public void onJoystickMove(ControlElement element, float deltaX, float deltaY) {
        // 处理摇杆移动
        // deltaX, deltaY 范围：-1 到 1
    }
    
    @Override
    public void onMouseMove(float deltaX, float deltaY) {
        // 处理鼠标移动
    }
    
    @Override
    public void onMouseScroll(float deltaX, float deltaY) {
        // 处理鼠标滚轮
    }
});
```

## 🔧 高级功能

### 布局导出/导入

```java
ControlLayoutManager manager = new ControlLayoutManager(context);

// 导出布局
ControlLayout layout = manager.getCurrentLayout();
File exportDir = new File(Environment.getExternalStorageDirectory(), "控制布局");
File exportedFile = manager.exportLayout(layout, exportDir);

// 导入布局
File layoutFile = new File(exportDir, "my_layout.json");
ControlLayout imported = manager.importLayout(layoutFile);
```

### 布局复制

```java
ControlLayout original = manager.getLayoutByName("tModLoader 默认");
ControlLayout copy = manager.duplicateLayout(original);
copy.setName("我的 tModLoader 布局");
manager.saveLayout(copy);
```

### 批量修改元素

```java
ControlLayout layout = manager.getCurrentLayout();

// 调整所有按钮的透明度
for (ControlElement element : layout.getElements()) {
    if (element.getType() == ControlElement.ElementType.BUTTON) {
        element.setOpacity(0.8f);
    }
}

manager.saveLayout(layout);
```

## 🎯 最佳实践

### 1. 测试流程
1. 先使用预设布局游玩
2. 记录不便操作的地方
3. 进入编辑器调整
4. 重新测试
5. 迭代优化

### 2. 设备适配
- 不同屏幕尺寸需要不同布局
- 使用相对坐标而非绝对像素
- 为平板和手机分别优化

### 3. 游戏适配
- FPS 游戏：大触摸板 + 精准按钮
- RPG 游戏：虚拟十字键 + 多快捷栏
- 平台游戏：摇杆 + 跳跃按钮优先

### 4. 性能优化
- 减少不必要的元素
- 降低不重要按钮的更新频率
- 使用合适的透明度

## 🐛 故障排除

### 控制不响应
- 检查元素是否被遮挡
- 确认 `visibility` 设置正确
- 验证 `passthrough` 未错误启用

### 摇杆漂移
- 增大 `deadzone` 值 (推荐 0.15)
- 调整 `sensitivity`

### 按钮位置不对
- 确认使用相对坐标 (0-1)
- 检查元素 `width` 和 `height`
- 验证无旋转角度影响

## 📚 API 参考

### ControlElement 主要方法

```java
// 创建元素
ControlElement element = new ControlElement(
    "unique_id",
    ControlElement.ElementType.BUTTON,
    "Display Name"
);

// 设置位置和大小
element.setX(0.5f);              // 屏幕宽度的 50%
element.setY(0.8f);              // 屏幕高度的 80%
element.setWidth(100);           // 100 像素
element.setHeight(100);

// 设置外观
element.setBackgroundColor(Color.argb(128, 100, 100, 100));
element.setBorderColor(Color.WHITE);
element.setOpacity(0.7f);

// 设置键位
element.setKeyCode(KeyEvent.KEYCODE_SPACE);

// JSON 序列化
JSONObject json = element.toJSON();
ControlElement fromJson = ControlElement.fromJSON(json);
```

### ControlLayout 主要方法

```java
// 创建布局
ControlLayout layout = new ControlLayout("My Layout");
layout.setDescription("适合横版游戏");
layout.setAuthor("我的名字");

// 添加元素
layout.addElement(element);

// 保存/加载
File file = new File(context.getFilesDir(), "my_layout.json");
layout.saveToFile(file);
ControlLayout loaded = ControlLayout.fromFile(file);

// 查找元素
ControlElement found = layout.getElementById("unique_id");

// 复制布局
ControlLayout copy = layout.copy("New Name");
```

## 🔗 相关资源

- [KeyEvent KeyCodes](https://developer.android.com/reference/android/view/KeyEvent)
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
- [FNA Wiki](https://github.com/FNA-XNA/FNA/wiki)

## 📞 反馈和支持

如遇到问题或有改进建议：
- 提交 Issue：[GitHub Issues](https://github.com/Fireworkshh/Rotating-art-Launcher/issues)
- 分享布局：[Discussions](https://github.com/Fireworkshh/Rotating-art-Launcher/discussions)

---

**享受你的游戏时光！** 🎮✨

