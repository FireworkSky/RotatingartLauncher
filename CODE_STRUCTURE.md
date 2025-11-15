# Rotating-art-Launcher 代码结构文档

## 📁 项目结构概览

```
app/src/main/java/com/app/ralaunch/
├── activity/          # Activity层 - UI入口
├── adapter/           # RecyclerView适配器
├── console/           # 控制台相关
├── controls/          # 游戏控制器
│   └── editor/        # 控制器编辑器
├── core/              # 核心业务逻辑 ⭐新增
│   └── importer/      # 游戏导入服务
├── data/              # 数据管理层 ⭐新增
├── dialog/            # 对话框
├── fragment/          # Fragment组件
├── game/              # 游戏相关
├── icon/              # 图标提取
├── model/             # 数据模型 ⭐优化
├── ui/                # 自定义View ⭐新增
├── utils/             # 工具类
└── RaLaunchApplication.java
```

## 🎯 架构分层

### 1. UI层
- **activity/** - Activity入口
  - `MainActivity.java` - 主界面
  - `GameActivity.java` - 游戏运行界面
  - `DebugActivity.java` - 调试界面

- **fragment/** - Fragment组件
  - `LocalImportFragment.java` - 本地导入
  - `SettingsFragment.java` - 设置页面
  - `FileBrowserFragment.java` - 文件浏览器
  - `InitializationFragment.java` - 初始化页面

- **dialog/** - Material Design 3对话框
  - `LocalImportDialog.java` - 本地导入对话框 ⭐MD3
  - `RuntimeSelectorDialog.java` - 运行时选择

- **adapter/** - 列表适配器
  - `GameAdapter.java` - 游戏列表
  - `FileBrowserAdapter.java` - 文件浏览
  - `ControlLayoutAdapter.java` - 控制布局

- **ui/** - 自定义View组件 ⭐新增包
  - `OverlayControlView.java` - 覆盖层控制视图

### 2. 数据层 ⭐新增包

- **data/** - 数据管理
  - `GameDataManager.java` - 游戏数据持久化
  - `SettingsManager.java` - 设置管理

- **model/** - 数据模型 ⭐优化包结构
  - `GameItem.java` - 游戏项模型(从adapter移动)
  - `FileItem.java` - 文件项模型
  - `ControlElement.java` - 控制元素模型
  - `ComponentItem.java` - 组件项模型

### 3. 核心业务层 ⭐新增包

- **core/** - 核心业务逻辑
  - `GameLauncher.java` - 游戏启动器(从game移动)

- **core/importer/** - 游戏导入服务 ⭐新增子包
  - `GameImportService.java` - 导入服务主类
  - `ImportTask.java` - 导入任务封装
  - `ImportProgressListener.java` - 进度监听接口

### 4. 工具层

- **utils/** - 通用工具
  - `GameExtractor.java` - 游戏解压器
  - `AppLogger.java` - 日志工具
  - `DotNetConfigPatcher.java` - .NET配置补丁
  - `GameInfoParser.java` - 游戏信息解析
  - `IconExtractorHelper.java` - 图标提取助手
  - `RuntimeManager.java` - 运行时管理
  - `PageManager.java` - 页面管理
  - `PermissionHelper.java` - 权限助手
  - `ControlLayoutManager.java` - 控制布局管理

- **icon/** - 图标处理
  - `IconExtractor.java` - 图标提取器
  - `BmpDecoder.java` - BMP解码器
  - `PeReader.java` - PE文件读取器

- **game/** - 游戏处理
  - `AssemblyPatcher.java` - 程序集补丁

### 5. 功能模块

- **console/** - 控制台系统
  - `ConsoleManager.java` - 控制台管理器
  - `ConsoleService.java` - 控制台服务
  - `ConsoleMessage.java` - 控制台消息
  - `FloatingConsoleView.java` - 浮动控制台

- **controls/** - 游戏控制器
  - `ControlLayout.java` - 控制布局
  - `VirtualButton.java` - 虚拟按钮
  - `VirtualJoystick.java` - 虚拟摇杆
  - `SDLInputBridge.java` - SDL输入桥接
  - **editor/** - 控制器编辑器
    - `ControlEditorActivity.java`
    - `EditControlDialog.java`
    - `SideEditDialog.java`

## 🔄 最近优化 (2025-01-15)

### 1. 包结构重组

#### ✅ 已完成
- `GameItem` 从 `adapter` 移动到 `model`
- `GameDataManager` 从 `utils` 移动到 `data`
- `SettingsManager` 从 `utils` 移动到 `data`
- `GameLauncher` 从 `game` 移动到 `core`
- `OverlayControlView` 从 `view` 移动到 `ui`

#### 新增包
- `com.app.ralaunch.data` - 数据管理层
- `com.app.ralaunch.ui` - 自定义View层
- `com.app.ralaunch.core` - 核心业务逻辑层
- `com.app.ralaunch.core.importer` - 导入服务子包

### 2. Material Design 3 升级

#### 主题系统
- 升级到 `Theme.Material3.DayNight`
- 完整的MD3颜色系统(浅色+暗色)
- 动态配色基础支持

#### UI组件
- ✅ LocalImportDialog - 弹窗式本地导入
- ✅ Snackbar - 统一MD3主题色
- ✅ Material 3 Button样式库
- ✅ Material 3 CardView样式

#### 颜色规范
```
浅色主题:
- 信息提示: #E8DAFF (紫色容器)
- 成功提示: #C8E6C9 (绿色容器)
- 错误提示: #FFDAD6 (红色容器)

暗色主题:
- 信息提示: #4F378B (深紫容器)
- 成功提示: #388E3C (深绿容器)
- 错误提示: #8C1D18 (深红容器)
```

### 3. 导入流程优化 ⭐新增

#### 旧架构问题
- GameExtractor太臃肿(382行)
- 职责不清晰
- 难以测试和维护

#### 新架构方案
```
GameImportService (服务层)
    ↓
ImportTask (任务封装)
    ↓
ImportProgressListener (进度回调)
    ↓
GameExtractor (底层解压)
```

#### 优势
- **单一职责**: 每个类功能明确
- **易于测试**: 接口清晰,便于单元测试
- **Builder模式**: ImportTask使用构建器
- **异步支持**: 内置线程池管理

## 📊 依赖关系

```
UI层 (Activity/Fragment/Dialog)
  ↓
核心业务层 (core/)
  ↓
数据层 (data/ + model/)
  ↓
工具层 (utils/)
```

## 🎨 Material Design 3 组件

### 按钮样式
- `Widget.App.Button.Filled` - 填充按钮
- `Widget.App.Button.Outlined` - 描边按钮
- `Widget.App.Button.Text` - 文本按钮
- `Widget.App.Button.Elevated` - 提升按钮

### 卡片样式
- `Widget.App.CardView.Elevated` - 提升卡片(20dp圆角)
- `Widget.App.CardView.Outlined` - 描边卡片

### 对话框样式
- `AddGameDialogStyle` - 添加游戏对话框
- `SettingsDialogStyle` - 设置对话框
- `RuntimeDialogStyle` - 运行时选择对话框

## 🔧 开发规范

### 1. 包命名规范
- **activity** - Activity类
- **fragment** - Fragment类
- **dialog** - DialogFragment类
- **adapter** - RecyclerView.Adapter类
- **model** - 数据模型类(POJO)
- **data** - 数据管理类(Manager/Repository)
- **core** - 核心业务逻辑
- **ui** - 自定义View组件
- **utils** - 静态工具类

### 2. 类命名规范
- Activity: `*Activity.java`
- Fragment: `*Fragment.java`
- Dialog: `*Dialog.java`
- Adapter: `*Adapter.java`
- Manager: `*Manager.java`
- Service: `*Service.java`
- Helper: `*Helper.java`

### 3. 新增功能指南

#### 添加新的数据模型
```java
// 放在 model/ 包
package com.app.ralaunch.model;
public class NewModel { ... }
```

#### 添加数据管理类
```java
// 放在 data/ 包
package com.app.ralaunch.data;
public class NewDataManager { ... }
```

#### 添加业务逻辑
```java
// 放在 core/ 包或子包
package com.app.ralaunch.core;
public class NewService { ... }
```

#### 添加自定义View
```java
// 放在 ui/ 包
package com.app.ralaunch.ui;
public class NewCustomView extends View { ... }
```

## 📚 ralib 公共库

位于 `ralib/` 模块,提供通用组件:

### UI组件
- `SnackbarHelper` - Snackbar助手(MD3主题)
- `ModernProgressBar` - 现代化进度条
- `ModernButton` - 现代化按钮
- `GameFileBrowser` - 游戏文件浏览器

### 工具类
- `ErrorHandler` - 错误处理器
- `OptionSelectorDialog` - 选项选择对话框

### 解压器
- `GogShFileExtractor` - GOG .sh文件提取器
- `BasicSevenZipExtractor` - 7-Zip解压器
- `ExtractorCollection` - 解压器集合

## 🚀 未来优化方向

1. **引入依赖注入** - 使用Dagger/Hilt
2. **Repository模式** - 统一数据访问
3. **ViewModel** - 引入MVVM架构
4. **协程支持** - 异步操作优化
5. **单元测试** - 提高代码覆盖率

## 📝 更新日志

### 2025-01-15
- ✅ 重组包结构(data/ui/core)
- ✅ Material Design 3全面升级
- ✅ Snackbar主题色统一
- ✅ 游戏导入服务重构(GameImportService)
- ✅ 移除在线下载功能
- ✅ 添加LocalImportDialog

---

**最后更新**: 2025-01-15
**维护者**: Claude Code
