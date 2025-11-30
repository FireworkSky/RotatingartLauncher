# 多语言支持迁移指南

## 📋 已完成的工作

### ✅ 1. 基础架构
- [x] 创建 `LocaleManager.java` - 多语言管理器
- [x] 更新 `RaLaunchApplication` - 应用语言设置
- [x] 更新 `MainActivity` - attachBaseContext
- [x] 创建 `values/strings.xml` (中文)
- [x] 创建 `values-en/strings.xml` (English)

### ✅ 2. 设置界面
- [x] `SettingsFragment` - 语言切换选项

## 🔄 需要迁移的文件列表

### 高优先级（用户可见文本）

#### Fragment
- [ ] `LocalImportFragment.java` - 导入界面
- [ ] `InitializationFragment.java` - 初始化界面
- [ ] `ControlLayoutFragment.java` - 控制布局
- [ ] `FileBrowserFragment.java` - 文件浏览器

#### Activity
- [ ] `MainActivity.java` - 主界面 Toast/Snackbar
- [ ] `GameActivity.java` - 游戏界面菜单
- [ ] `ControlEditorActivity.java` - 控制编辑器

#### Dialog
- [ ] `LocalImportDialog.java` - 导入对话框

### 中优先级（提示信息）

#### Utils
- [ ] `GameExtractor.java` - 解压提示
- [ ] `GameImportService.java` - 导入服务提示

#### Adapter
- [ ] `GameAdapter.java` - 游戏列表
- [ ] `ControlLayoutAdapter.java` - 控制布局列表

### 低优先级（日志和内部文本）

#### 日志相关
- 保持英文即可，便于调试

## 📝 迁移步骤

### 对于每个文件：

1. **识别硬编码中文**
   ```bash
   grep -n "[\u4e00-\u9fa5]" 文件名.java
   ```

2. **添加字符串资源**
   - 中文: `app/src/main/res/values/strings.xml`
   - 英文: `app/src/main/res/values-en/strings.xml`

3. **替换硬编码**
   ```java
   // 旧代码
   textView.setText("游戏名称");

   // 新代码
   textView.setText(getString(R.string.game_name));
   ```

4. **格式化字符串**
   ```java
   // 旧代码
   String msg = "导入失败: " + error;

   // 新代码
   String msg = getString(R.string.import_error, error);
   ```

## 🎯 当前进度

- 基础框架: 100%
- 字符串资源: 60%
- 代码迁移: 5%

## 📚 字符串命名规范

### 前缀规则
- `main_` - 主界面相关
- `game_` - 游戏相关
- `import_` - 导入相关
- `settings_` - 设置相关
- `control_` - 控制相关
- `editor_` - 编辑器相关
- `error_` - 错误信息
- `msg_` - 通用消息

### 示例
```xml
<string name="main_add_game">添加游戏</string>
<string name="game_launch_success">游戏启动成功</string>
<string name="import_in_progress">正在导入...</string>
<string name="error_file_not_found">文件未找到</string>
```

## 🔧 测试清单

- [ ] 中文环境测试
- [ ] 英文环境测试
- [ ] 切换语言测试
- [ ] Toast/Snackbar 显示
- [ ] 对话框文本
- [ ] 列表项文本
- [ ] 错误提示
