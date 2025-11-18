# 补丁程序集系统使用指南

## 概述

补丁程序集系统允许你在游戏启动前自动加载和执行补丁代码，用于修改游戏行为、修复兼容性问题等。

## 快速开始

### 1. 创建补丁程序集

```bash
# 创建新的补丁项目
cd D:\Rotating-art-Launcher\tools
mkdir MyPatch
cd MyPatch

# 创建项目文件
dotnet new classlib -n MyPatch -f net8.0
```

### 2. 编写补丁代码

创建 `Patcher.cs`：

```csharp
using System;

namespace MyPatch
{
    public class Patcher
    {
        /// <summary>
        /// 补丁入口方法 - 会在游戏启动前被自动调用
        /// </summary>
        public static void Initialize()
        {
            Console.WriteLine("[MyPatch] Initializing...");

            // 在这里编写你的补丁逻辑
            // 例如：
            // - 注册 AppDomain 事件
            // - 修改环境变量
            // - Hook 游戏方法

            Console.WriteLine("[MyPatch] Initialized successfully");
        }
    }
}
```

### 3. 编译补丁

```bash
dotnet build -c Release
```

输出文件位于：`bin/Release/net8.0/MyPatch.dll`

### 4. 配置补丁元数据

创建 `patch.json`：

```json
{
  "id": "my_patch",
  "name": "我的补丁",
  "description": "补丁说明",
  "version": "1.0.0",
  "author": "Your Name",
  "dllFileName": "MyPatch.dll",
  "entryPoint": {
    "typeName": "MyPatch.Patcher",
    "methodName": "Initialize"
  },
  "targetGames": [
    "all"
  ],
  "priority": 100,
  "enabled": true
}
```

### 5. 部署到 Android Assets

#### 方法1：手动复制

```bash
# 复制 DLL
copy bin\Release\net8.0\MyPatch.dll ..\app\src\main\assets\patches\

# 复制元数据
copy patch.json ..\app\src\main\assets\patches\
```

#### 方法2：添加到构建脚本

编辑 `tools/build_patches.bat`，添加你的补丁。

### 6. 注册到补丁系统

编辑 `app/src/main/assets/patches/patch_metadata.json`，添加你的补丁配置：

```json
{
  "patches": [
    {
      "id": "my_patch",
      "name": "我的补丁",
      "description": "补丁说明",
      "version": "1.0.0",
      "author": "Your Name",
      "dllFileName": "MyPatch.dll",
      "entryPoint": {
        "typeName": "MyPatch.Patcher",
        "methodName": "Initialize"
      },
      "targetGames": [
        "all"
      ],
      "priority": 100,
      "enabled": true
    }
  ]
}
```

### 7. 重新编译 APK

```bash
cd D:\Rotating-art-Launcher
gradlew assembleDebug
```

## 补丁元数据说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 补丁唯一标识符 |
| `name` | string | 补丁显示名称 |
| `description` | string | 补丁说明 |
| `version` | string | 补丁版本号 |
| `author` | string | 作者名称 |
| `dllFileName` | string | DLL 文件名 |
| `entryPoint.typeName` | string | 入口类的完整类型名 |
| `entryPoint.methodName` | string | 入口方法名 |
| `targetGames` | array | 目标游戏列表，`["all"]` 表示所有游戏 |
| `priority` | number | 优先级（数字越大越先执行） |
| `enabled` | boolean | 是否启用 |

## 补丁执行顺序

1. 补丁按 `priority` 从大到小排序
2. 相同优先级的补丁按配置文件中的顺序执行
3. 所有补丁执行完成后，才会启动游戏程序集

## 常见用例

### 示例1：修改环境变量

```csharp
public static void Initialize()
{
    Environment.SetEnvironmentVariable("MY_CUSTOM_VAR", "value");
}
```

### 示例2：注册程序集加载事件

```csharp
public static void Initialize()
{
    AppDomain.CurrentDomain.AssemblyLoad += (sender, args) =>
    {
        Console.WriteLine($"Assembly loaded: {args.LoadedAssembly.GetName().Name}");
    };
}
```

### 示例3：Hook 游戏方法（使用 Harmony）

首先添加 Harmony 依赖到 `.csproj`：

```xml
<ItemGroup>
  <PackageReference Include="Lib.Harmony" Version="2.3.0" />
</ItemGroup>
```

然后编写 Hook 代码：

```csharp
using HarmonyLib;

public static void Initialize()
{
    var harmony = new Harmony("com.mypatch.id");

    // Hook 示例
    var original = AccessTools.Method(typeof(TargetClass), "TargetMethod");
    var prefix = AccessTools.Method(typeof(Patcher), "PrefixMethod");

    harmony.Patch(original, new HarmonyMethod(prefix));
}

static bool PrefixMethod(/* 参数 */)
{
    Console.WriteLine("Method hooked!");
    return true; // 返回 true 继续执行原方法
}
```

## 调试

### 查看日志

```bash
adb logcat | grep -E "ExamplePatch|NetCoreHost"
```

### 常见错误

1. **程序集加载失败**
   - 检查 DLL 文件是否存在于 `assets/patches/` 目录
   - 检查 `patch_metadata.json` 中的 `dllFileName` 是否正确

2. **方法调用失败**
   - 检查 `typeName` 和 `methodName` 是否正确
   - 确保方法是 `public static void` 且无参数

3. **补丁未执行**
   - 检查 `enabled` 是否为 `true`
   - 检查 `targetGames` 是否匹配当前游戏

## 目录结构

```
tools/
├── ExamplePatch/           # 示例补丁
│   ├── ExamplePatch.csproj
│   ├── Patcher.cs
│   └── patch.json
├── build_patches.bat       # Windows 构建脚本
└── build_patches.sh        # Linux/Mac 构建脚本

app/src/main/assets/patches/
├── patch_metadata.json     # 补丁元数据配置
├── ExamplePatch.dll        # 示例补丁 DLL
└── patch.json              # 示例补丁配置
```

## 工作流程

1. **开发补丁**：在 `tools/` 目录下创建补丁项目
2. **编译补丁**：使用 `dotnet build` 或运行 `build_patches.bat`
3. **配置元数据**：在 `patch_metadata.json` 中添加配置
4. **编译 APK**：运行 `gradlew assembleDebug`
5. **安装测试**：`adb install -r app-debug.apk`
6. **查看日志**：`adb logcat`

## 下一步

- 查看 [ExamplePatch](ExamplePatch/) 了解完整示例
- 阅读 [PATCH_SYSTEM.md](../PATCH_SYSTEM.md) 了解系统架构
- 学习 [Harmony](https://github.com/pardeike/Harmony) 进行运行时修改
