# Material Design 3 迁移指南

## 概述

本项目已成功升级至 Material Design 3 (Material You) 设计系统,并移除了在线下载功能,统一使用弹窗式UI体验。

## 主要变更

### 1. 依赖升级

#### App 模块 (`app/build.gradle`)
- `androidx.core:core`: 1.9.0 → 1.13.1
- `androidx.appcompat:appcompat`: 1.6.1 → 1.7.0
- `androidx.recyclerview:recyclerview`: 1.3.0 → 1.3.2

#### Ralib 模块 (`ralib/build.gradle`)
- `androidx.appcompat:appcompat`: 1.6.1 → 1.7.0
- `com.google.android.material:material`: 1.11.0 → 1.12.0

### 2. 主题系统升级

#### 主题父类变更
- **之前**: `Theme.MaterialComponents.DayNight.NoActionBar`
- **现在**: `Theme.Material3.DayNight.NoActionBar`

#### 新增 Material 3 颜色属性

##### 浅色主题 (`values/colors.xml`)
```xml
<!-- Material 3 主要颜色 -->
- md_theme_light_primary: #BB86FC
- md_theme_light_primaryContainer: #E8DAFF
- md_theme_light_secondary: #9965F4
- md_theme_light_tertiary: #4CAF50
- md_theme_light_surface: #FAFAFA
- md_theme_light_outline: #A4B4D8
```

##### 暗色主题 (`values-night/colors.xml`)
```xml
<!-- Material 3 暗色颜色 -->
- md_theme_dark_primary: #D0BCFF
- md_theme_dark_primaryContainer: #4F378B
- md_theme_dark_secondary: #CCC2DC
- md_theme_dark_surface: #1C1B1F
- md_theme_dark_outline: #938F99
```

### 3. UI组件升级

#### 添加游戏功能重构
- **移除**: `AddGameOptionsFragment` (Fragment页面)
- **新增**: `AddGameDialog` (Material 3对话框)
- **位置**: `app/src/main/java/com/app/ralaunch/dialog/AddGameDialog.java`
- **布局**: `app/src/main/res/layout/dialog_add_game.xml`

#### 功能变更
- ✅ 保留: 本地导入游戏
- ❌ 移除: 在线下载功能
- ✨ 新增: 弹窗式Material 3对话框

### 4. 新增样式

#### 按钮样式 (`values/styles.xml`)
- `Widget.App.Button.Filled` - 填充按钮 (默认)
- `Widget.App.Button.Outlined` - 描边按钮
- `Widget.App.Button.Text` - 文本按钮
- `Widget.App.Button.Elevated` - 提升按钮 (带阴影)

#### 卡片样式
- `Widget.App.CardView.Elevated` - 提升卡片
- `Widget.App.CardView.Outlined` - 描边卡片

#### 对话框样式
- `AddGameDialogStyle` - 添加游戏对话框
- `SettingsDialogStyle` - 设置对话框
- `RuntimeDialogStyle` - 运行时选择对话框

所有对话框样式均基于 `Theme.Material3.DayNight.Dialog`

## 使用指南

### 使用 Material 3 按钮

```xml
<!-- 填充按钮 -->
<com.google.android.material.button.MaterialButton
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="确认"
    style="@style/Widget.App.Button.Filled" />

<!-- 描边按钮 -->
<com.google.android.material.button.MaterialButton
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="取消"
    style="@style/Widget.App.Button.Outlined" />

<!-- 提升按钮 (用于主要操作) -->
<com.google.android.material.button.MaterialButton
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="选择文件"
    app:icon="@drawable/ic_folder"
    style="@style/Widget.App.Button.Elevated" />
```

### 使用 Material 3 卡片

```xml
<!-- 提升卡片 -->
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    style="@style/Widget.App.CardView.Elevated">
    <!-- 内容 -->
</com.google.android.material.card.MaterialCardView>

<!-- 描边卡片 -->
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    style="@style/Widget.App.CardView.Outlined">
    <!-- 内容 -->
</com.google.android.material.card.MaterialCardView>
```

### 使用添加游戏对话框

```java
AddGameDialog addGameDialog = new AddGameDialog();
addGameDialog.setOnLocalImportClickListener(() -> {
    // 处理本地导入
    showLocalImportFragment();
});
addGameDialog.show(getSupportFragmentManager(), "add_game_dialog");
```

## Material 3 设计原则

### 1. 颜色系统
- **Primary**: 应用主色调 (紫色 #BB86FC)
- **Secondary**: 次要色调 (深紫 #9965F4)
- **Tertiary**: 三级色 (绿色 #4CAF50)
- **Surface**: 表面色 (卡片、对话框背景)
- **Outline**: 轮廓色 (边框、分隔线)

### 2. 形状系统
- **小组件**: 中等圆角 (12dp)
- **中等组件**: 大圆角 (16dp)
- **大组件**: 超大圆角 (20-28dp)

### 3. 动态配色
Material 3 支持从用户壁纸提取颜色,实现个性化主题。当前版本使用固定配色方案,后续可启用动态配色。

## 向后兼容

为保证现有代码正常运行,保留了以下兼容性颜色:
- `background_primary`, `background_secondary`, `background_card`
- `text_primary`, `text_secondary`, `text_hint`
- `accent_primary`, `accent_secondary`
- `divider`, `button_secondary`

建议逐步迁移到 Material 3 颜色属性:
- `?attr/colorPrimary` 替代 `@color/accent_primary`
- `?attr/colorSurface` 替代 `@color/background_card`
- `?attr/colorOnSurface` 替代 `@color/text_primary`

## 迁移检查清单

- [x] 升级 Material 依赖到 1.12.0
- [x] 更新主题为 Material3.DayNight
- [x] 创建 Material 3 颜色资源
- [x] 添加 Material 3 按钮样式
- [x] 添加 Material 3 卡片样式
- [x] 移除在线下载功能
- [x] 创建添加游戏对话框
- [x] 更新对话框样式

## 已知问题

无

## 下一步计划

1. 逐步将现有布局文件中的 `Button` 替换为 `MaterialButton`
2. 将 `CardView` 替换为 `MaterialCardView`
3. 考虑启用 Material 3 动态配色 (Android 12+)
4. 添加更多 Material 3 组件 (如 NavigationBar, TopAppBar)

## 参考资料

- [Material Design 3 官方文档](https://m3.material.io/)
- [Material Components for Android](https://github.com/material-components/material-components-android)
- [迁移指南](https://material.io/blog/migrating-material-3)
