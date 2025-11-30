# 外部补丁程序集使用指南

## 概述

RA Launcher 现在支持从外部存储加载补丁程序集和配置文件，允许用户在不重新编译 APK 的情况下添加或更新补丁。

## 外部补丁目录结构

```
/sdcard/Android/data/com.app.ralaunch/files/patches/
├── patch_metadata.json      # 补丁元数据配置
├── ExamplePatch.dll          # 补丁程序集 1
├── MyCustomPatch.dll         # 补丁程序集 2
└── ...                       # 其他补丁
```

## 工作原理

### 加载优先级

系统采用**外部优先**策略：

1. **补丁元数据**：
   - 首先检查 `/sdcard/Android/data/com.app.ralaunch/files/patches/patch_metadata.json`
   - 如果不存在，使用内置的 `assets/patches/patch_metadata.json`

2. **补丁程序集**：
   - 首先检查外部存储 `/sdcard/Android/data/com.app.ralaunch/files/patches/xxx.dll`
   - 如果不存在，使用内置的 `assets/patches/xxx.dll`

这意味着您可以：
- 完全自定义补丁配置
- 覆盖内置补丁程序集
- 添加新的补丁程序集

## 使用步骤

### 1. 创建补丁目录

通过 ADB 或文件管理器创建目录：

```bash
adb shell mkdir -p /sdcard/Android/data/com.app.ralaunch/files/patches/
```

### 2. 编写补丁元数据

创建 `patch_metadata.json`：

```json
{
  "patches": [
    {
      "id": "my_custom_patch",
      "name": "我的自定义补丁",
      "description": "修复特定问题的补丁",
      "version": "1.0.0",
      "author": "Your Name",
      "dllFileName": "MyCustomPatch.dll",
      "entryPoint": {
        "typeName": "MyCustomPatch.Patcher",
        "methodName": "Initialize"
      },
      "targetGames": [
        "terraria",
        "tmodloader"
      ],
      "priority": 100,
      "enabled": true
    }
  ]
}
```

### 3. 编译补丁程序集

使用 .NET 8.0 编译补丁：

```bash
cd D:\Rotating-art-Launcher\tools
mkdir MyCustomPatch
cd MyCustomPatch

# 创建项目
dotnet new classlib -n MyCustomPatch -f net8.0

# 编写代码 (Patcher.cs)
# 编译
dotnet build -c Release
```

补丁代码示例：

```csharp
using System;

namespace MyCustomPatch
{
    public class Patcher
    {
        /// <summary>
        /// 补丁入口方法
        /// 使用 ComponentEntryPoint 签名
        /// </summary>
        public static int Initialize(IntPtr arg, int argSize)
        {
            try
            {
                Console.WriteLine("[MyCustomPatch] Initializing...");

                // 在这里编写补丁逻辑
                // 例如：注册事件、Hook 方法等

                Console.WriteLine("[MyCustomPatch] Initialized successfully");
                return 0; // 成功
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[MyCustomPatch] ERROR: {ex.Message}");
                return -1; // 失败
            }
        }
    }
}
```

### 4. 推送补丁到设备

```bash
# 推送元数据
adb push patch_metadata.json /sdcard/Android/data/com.app.ralaunch/files/patches/

# 推送补丁程序集
adb push bin/Release/net8.0/MyCustomPatch.dll /sdcard/Android/data/com.app.ralaunch/files/patches/
```

### 5. 重启应用

重启 RA Launcher，新的补丁将自动被识别和加载。

## 补丁元数据字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 补丁唯一标识符 |
| `name` | string | 是 | 补丁显示名称 |
| `description` | string | 否 | 补丁描述 |
| `version` | string | 是 | 版本号 (语义化版本) |
| `author` | string | 否 | 作者名称 |
| `dllFileName` | string | 是 | DLL 文件名 |
| `entryPoint.typeName` | string | 是 | 入口类的完整类型名 |
| `entryPoint.methodName` | string | 是 | 入口方法名 |
| `targetGames` | array | 是 | 目标游戏列表 (`["all"]` 表示所有游戏) |
| `priority` | number | 是 | 优先级 (数字越大越先执行) |
| `enabled` | boolean | 是 | 是否启用 |

## 调试和日志

### 查看补丁加载日志

```bash
adb logcat | grep -E "PatchManager|AssemblyPatcher|ExamplePatch"
```

### 常见日志消息

- `Loading patches from external storage` - 从外部存储加载配置
- `从外部存储加载: xxx.dll` - 从外部存储加载补丁程序集
- `从 assets 加载: xxx.dll` - 从内置 assets 加载补丁程序集
- `Failed to load external patch metadata` - 外部元数据加载失败，使用内置配置

## 示例：覆盖内置补丁

如果您想修改内置的 `ExamplePatch.dll`：

1. 编译新版本的补丁
2. 推送到外部存储：
   ```bash
   adb push ExamplePatch.dll /sdcard/Android/data/com.app.ralaunch/files/patches/
   ```
3. 重启应用

系统会自动使用外部版本的补丁，而不是内置版本。

## 示例：完全自定义补丁配置

创建包含多个补丁的配置：

```json
{
  "patches": [
    {
      "id": "performance_patch",
      "name": "性能优化补丁",
      "version": "1.0.0",
      "dllFileName": "PerformancePatch.dll",
      "entryPoint": {
        "typeName": "PerformancePatch.Optimizer",
        "methodName": "Initialize"
      },
      "targetGames": ["all"],
      "priority": 200,
      "enabled": true
    },
    {
      "id": "bugfix_patch",
      "name": "Bug修复补丁",
      "version": "1.0.0",
      "dllFileName": "BugFixPatch.dll",
      "entryPoint": {
        "typeName": "BugFixPatch.Fixer",
        "methodName": "Initialize"
      },
      "targetGames": ["terraria"],
      "priority": 100,
      "enabled": true
    }
  ]
}
```

## 故障排除

### 补丁未被加载

1. 检查文件路径是否正确：
   ```bash
   adb shell ls -l /sdcard/Android/data/com.app.ralaunch/files/patches/
   ```

2. 检查文件权限：
   ```bash
   adb shell chmod 644 /sdcard/Android/data/com.app.ralaunch/files/patches/*
   ```

3. 查看日志确认错误：
   ```bash
   adb logcat | grep -E "PatchManager|ERROR"
   ```

### 补丁加载但未执行

1. 检查 `enabled` 字段是否为 `true`
2. 检查 `targetGames` 是否匹配当前游戏
3. 确认补丁方法签名正确：`public static int MethodName(IntPtr arg, int argSize)`

### JSON 解析错误

使用在线 JSON 验证器检查 `patch_metadata.json` 格式：
- https://jsonlint.com/

## 高级用法

### 条件启用补丁

通过修改外部 `patch_metadata.json`，您可以快速启用/禁用补丁，无需重新编译 APK：

```bash
# 禁用某个补丁
adb shell 'sed -i "s/\"enabled\": true/\"enabled\": false/" /sdcard/Android/data/com.app.ralaunch/files/patches/patch_metadata.json'

# 重启应用
adb shell am force-stop com.app.ralaunch
```

### 补丁热更新

1. 编译新版本补丁
2. 推送到外部存储（覆盖旧版本）
3. 重启应用或游戏

无需重新安装 APK！

## 安全提示

⚠️ **注意**：
- 只加载可信来源的补丁程序集
- 补丁程序集具有完整的运行时访问权限
- 恶意补丁可能导致游戏崩溃或数据丢失
- 建议在测试环境中先验证补丁

## 下一步

- 查看 [PATCH_GUIDE.md](tools/PATCH_GUIDE.md) 了解如何编写补丁
- 查看 [ExamplePatch](tools/ExamplePatch/) 参考示例代码
- 查看 [PATCH_SYSTEM.md](PATCH_SYSTEM.md) 了解系统架构
