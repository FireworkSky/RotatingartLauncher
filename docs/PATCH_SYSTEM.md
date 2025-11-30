# 游戏补丁系统使用文档

## 概述

本项目实现了一个灵活的游戏补丁系统,允许通过 JSON 配置来管理和控制补丁程序集的加载和执行。

## 功能特性

1. **JSON配置管理** - 通过 `patch_metadata.json` 配置补丁信息
2. **动态加载** - 自动从 `assets/patches/` 目录加载补丁程序集
3. **入口点调用** - 在游戏启动前自动调用补丁程序集的指定方法
4. **优先级控制** - 支持多个补丁按优先级顺序执行
5. **开关控制** - 每个补丁可以单独启用/禁用

## 工作流程

```
启动游戏
  ↓
Step 1: 应用补丁文件
  - 从 MonoMod_Patch.zip 替换程序集
  - 从 assets/patches/ 复制自定义补丁程序集
  ↓
Step 2: 执行补丁入口点
  - 按优先级排序补丁
  - 依次调用每个补丁的入口方法
  ↓
Step 3: 启动游戏主程序集
  - 加载并运行游戏
```

## 配置文件结构

### patch_metadata.json

位置: `app/src/main/assets/patches/patch_metadata.json`

```json
{
  "patches": [
    {
      "id": "assemblypatch",
      "name": "tModLoader 补丁",
      "description": "修复 tModLoader 在 Android 上的兼容性问题",
      "version": "1.0.0",
      "author": "RA Launcher Team",
      "dllFileName": "assemblypatch.dll",
      "entryPoint": {
        "typeName": "AssemblyPatch.Patcher",
        "methodName": "Initialize"
      },
      "targetGames": [
        "tmodloader",
        "terraria"
      ],
      "priority": 100,
      "enabled": true
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | String | ✅ | 补丁唯一标识符 |
| `name` | String | ✅ | 补丁显示名称 |
| `description` | String | ❌ | 补丁描述信息 |
| `version` | String | ❌ | 补丁版本号 (默认: "1.0.0") |
| `author` | String | ❌ | 补丁作者 (默认: "Unknown") |
| `dllFileName` | String | ✅ | DLL文件名 |
| `entryPoint` | Object | ❌ | 入口点配置 |
| `entryPoint.typeName` | String | ❌ | 完整的类型名称 |
| `entryPoint.methodName` | String | ❌ | 静态方法名称 |
| `targetGames` | Array | ❌ | 目标游戏列表 |
| `priority` | Number | ❌ | 优先级 (默认: 0, 越大越先执行) |
| `enabled` | Boolean | ❌ | 是否启用 (默认: true) |

## 创建自定义补丁

### 1. 创建 C# 补丁项目

```csharp
using System;

namespace MyGamePatch
{
    public class PatchEntry
    {
        // 入口方法必须是静态的，无参数
        public static void Initialize()
        {
            Console.WriteLine("MyGamePatch: Initializing...");

            // 在这里执行补丁逻辑
            // 例如: Hook游戏方法, 修改配置, 注入代码等

            Console.WriteLine("MyGamePatch: Initialized successfully");
        }
    }
}
```

### 2. 编译补丁

```bash
dotnet build -c Release
```

### 3. 放置补丁文件

将编译好的 `.dll` 文件放到:
```
app/src/main/assets/patches/MyGamePatch.dll
```

### 4. 配置补丁元数据

在 `patch_metadata.json` 中添加:

```json
{
  "id": "mygame_patch",
  "name": "我的游戏补丁",
  "description": "修复某些问题",
  "version": "1.0.0",
  "author": "Your Name",
  "dllFileName": "MyGamePatch.dll",
  "entryPoint": {
    "typeName": "MyGamePatch.PatchEntry",
    "methodName": "Initialize"
  },
  "targetGames": ["mygame"],
  "priority": 50,
  "enabled": true
}
```

## 补丁管理

### 在代码中启用/禁用补丁

```java
PatchManager patchManager = new PatchManager(context);

// 启用补丁
patchManager.setPatchEnabled(gameId, "mygame_patch", true);

// 禁用补丁
patchManager.setPatchEnabled(gameId, "mygame_patch", false);

// 检查补丁状态
boolean isEnabled = patchManager.isPatchEnabled(gameId, "mygame_patch");
```

### 获取游戏的启用补丁

```java
PatchManager patchManager = new PatchManager(context);
GameItem game = /* 获取游戏 */;

List<PatchInfo> enabledPatches = patchManager.getEnabledPatches(game);
for (PatchInfo patch : enabledPatches) {
    Log.d(TAG, "Enabled: " + patch.getPatchName() + " v" + patch.getVersion());
}
```

## 补丁执行顺序

补丁按以下规则执行:

1. **优先级排序** - `priority` 值越大越先执行
2. **同优先级** - 按在 JSON 中的定义顺序
3. **跳过无入口点** - 如果补丁没有配置 `entryPoint`, 只复制文件不执行

示例:

```
Priority 100: assemblypatch (tModLoader补丁)
  ↓
Priority 50:  mygame_patch (我的游戏补丁)
  ↓
Priority 0:   other_patch (其他补丁)
```

## 补丁程序集要求

### 入口方法规范

- **必须是静态方法** (`public static`)
- **无参数** (void)
- **返回类型** void 或任意类型
- **异常处理** 建议内部处理所有异常

```csharp
// ✅ 正确
public static void Initialize() { }

// ✅ 正确
public static int Setup() { return 0; }

// ❌ 错误 - 不是静态方法
public void Initialize() { }

// ❌ 错误 - 有参数
public static void Initialize(string arg) { }
```

### 依赖管理

- 补丁程序集可以引用游戏程序集
- 可以使用 .NET 标准库
- 建议使用 MonoMod 或 Harmony 进行代码注入

## 调试和日志

### 查看补丁加载日志

补丁系统会在 Logcat 中输出详细日志:

```
GameLauncher: Step 1/3: Applying patch files
GameLauncher:   - [MonoMod] MonoMod.RuntimeDetour.dll
GameLauncher:   - [自定义] assemblypatch.dll
GameLauncher: Step 2/3: Executing patch entry points
GameLauncher: Executing patch: tModLoader 补丁 (priority: 100)
GameLauncher:   Type: AssemblyPatch.Patcher
GameLauncher:   Method: Initialize
GameLauncher: ✅ Patch executed successfully: tModLoader 补丁
GameLauncher: Step 3/3: Configuring game runtime
```

### 常见问题

#### 补丁未执行

1. 检查 `enabled` 是否为 `true`
2. 检查 `entryPoint` 配置是否正确
3. 查看 Logcat 是否有错误信息

#### 找不到类型或方法

1. 确认 `typeName` 包含完整命名空间
2. 确认方法是 `public static`
3. 检查程序集是否正确放置在 `assets/patches/`

#### 补丁执行失败

1. 查看 Native 层日志
2. 确保补丁程序集与 .NET 运行时版本匹配
3. 检查补丁代码是否有未处理的异常

## Native 层实现 (TODO)

当前 Java 层已完成,需要在 C++ 层实现 `netcorehostCallMethod`:

```cpp
// app/src/main/cpp/main.cpp
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostCallMethod(
    JNIEnv* env,
    jclass clazz,
    jstring appDir,
    jstring assemblyName,
    jstring typeName,
    jstring methodName,
    jstring dotnetRoot,
    jint frameworkMajor) {

    // TODO: 实现通过 hostfxr 加载程序集并调用方法
    // 1. 初始化 .NET 运行时
    // 2. 加载程序集
    // 3. 获取类型和方法
    // 4. 调用方法

    return 0;
}
```

## 许可和贡献

欢迎贡献补丁和改进！请遵循项目的代码规范和提交指南。
