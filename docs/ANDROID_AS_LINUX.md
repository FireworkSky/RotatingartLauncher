# Android 平台识别为 Linux 的方法

## 概述

Android 使用 Linux 内核，但在 .NET 运行时中可能被识别为独立的平台。本文档说明如何让 MonoMod 和应用程序将 Android 正确识别为 Linux。

## MonoMod 的平台检测

### 已内置支持

MonoMod 已经**原生支持** Android 并将其识别为 Linux 的子集：

```csharp
// 在 MonoMod.Utils/OSKind.cs 中
Android = 0x01 << 5 | Linux,  // Android 是 Linux 的子集
```

### 检测逻辑

```csharp
// 检测步骤：
// 1. uname() 返回 "Linux" 内核名称
// 2. 检查 Android 特有文件系统
if (os == OSKind.Linux &&
    Directory.Exists("/data") && 
    File.Exists("/system/build.prop"))
{
    os = OSKind.Android;
}

// 3. 使用 Is() 扩展方法检查
OSKind.Android.Is(OSKind.Linux)  // ✅ 返回 true
OSKind.Android.Is(OSKind.Posix)  // ✅ 返回 true
```

## .NET RuntimeInformation API

### 问题

`RuntimeInformation.IsOSPlatform()` 在 Android 上可能不返回 `OSPlatform.Linux`：

```csharp
// 在 Android 上可能返回 false
RuntimeInformation.IsOSPlatform(OSPlatform.Linux)  // ❌ false
```

### 解决方案 1：使用 MonoMod 的 API

**推荐方法**：使用 MonoMod 的 `PlatformDetection.OS`：

```csharp
using MonoMod.Utils;

// 检查是否是 Linux（包括 Android）
if (PlatformDetection.OS.Is(OSKind.Linux))
{
    // 这会在 Android 上返回 true
    Console.WriteLine("Running on Linux or Android");
}

// 特别检查是否是 Android
if (PlatformDetection.OS == OSKind.Android)
{
    Console.WriteLine("Running on Android specifically");
}
```

### 解决方案 2：环境变量伪装

在 C++ launcher 中设置环境变量（已在 `netcorehost_launcher.cpp` 中实现）：

```cpp
// 简化 Globalization（减少对平台特定功能的依赖）
setenv("DOTNET_SYSTEM_GLOBALIZATION_INVARIANT", "1", 0);

// 禁用某些 Android 特定的检查
setenv("DOTNET_RUNNING_IN_CONTAINER", "0", 1);
```

### 解决方案 3：运行时 Hook

在应用启动时 hook `RuntimeInformation.IsOSPlatform`：

```csharp
using MonoMod.RuntimeDetour;
using System.Runtime.InteropServices;

public static class PlatformHook
{
    private static Hook? _platformHook;
    
    public static void Initialize()
    {
        // Hook IsOSPlatform 方法
        var original = typeof(RuntimeInformation)
            .GetMethod("IsOSPlatform", new[] { typeof(OSPlatform) });
        var detour = typeof(PlatformHook)
            .GetMethod(nameof(IsOSPlatform_Hook), 
                BindingFlags.Static | BindingFlags.NonPublic);
        
        _platformHook = new Hook(original, detour);
    }
    
    private static bool IsOSPlatform_Hook(
        Func<OSPlatform, bool> orig, 
        OSPlatform osPlatform)
    {
        // 在 Android 上，Linux 检查也返回 true
        if (osPlatform == OSPlatform.Linux && 
            Directory.Exists("/system") && 
            File.Exists("/system/build.prop"))
        {
            return true;  // Android 是 Linux
        }
        
        return orig(osPlatform);
    }
}

// 在程序入口调用
PlatformHook.Initialize();
```

## Bootstrap 代码中的实现

在 `build-tools/bootstrap/Program.cs` 中检查平台：

```csharp
using MonoMod.Utils;

public static class PlatformChecker
{
    public static bool IsAndroid()
    {
        return PlatformDetection.OS == OSKind.Android;
    }
    
    public static bool IsLinuxOrAndroid()
    {
        return PlatformDetection.OS.Is(OSKind.Linux);
    }
    
    public static bool RequiresLinuxWorkarounds()
    {
        // Android 和 Linux 都需要相同的 workarounds
        return IsLinuxOrAndroid();
    }
}
```

## 环境变量配置（已实现）

在 `netcorehost_launcher.cpp` 中的配置：

```cpp
// ✅ 已实现：平台伪装配置
setenv("DOTNET_SYSTEM_GLOBALIZATION_INVARIANT", "1", 0);

// 可选：根据需要添加更多配置
// setenv("DOTNET_EnableDiagnostics", "0", 1);  // 禁用诊断
// setenv("DOTNET_DbgMiniDumpType", "0", 1);   // 禁用 dump
```

## 检查清单

使用以下代码检查平台识别是否正确：

```csharp
using System;
using System.IO;
using System.Runtime.InteropServices;
using MonoMod.Utils;

public static class PlatformDiagnostics
{
    public static void PrintPlatformInfo()
    {
        Console.WriteLine("=== Platform Detection ===");
        
        // .NET RuntimeInformation
        Console.WriteLine($"RuntimeInformation.IsOSPlatform(OSPlatform.Linux): " +
            RuntimeInformation.IsOSPlatform(OSPlatform.Linux));
        Console.WriteLine($"RuntimeInformation.OSDescription: " +
            RuntimeInformation.OSDescription);
        
        // MonoMod PlatformDetection
        Console.WriteLine($"MonoMod PlatformDetection.OS: " +
            PlatformDetection.OS);
        Console.WriteLine($"MonoMod OS.Is(OSKind.Linux): " +
            PlatformDetection.OS.Is(OSKind.Linux));
        Console.WriteLine($"MonoMod OS.Is(OSKind.Posix): " +
            PlatformDetection.OS.Is(OSKind.Posix));
        
        // Android 特征检测
        bool hasAndroidFS = Directory.Exists("/data") && 
                           Directory.Exists("/system");
        Console.WriteLine($"Has Android filesystem: {hasAndroidFS}");
        
        // 环境变量
        Console.WriteLine($"ANDROID_ROOT: {Environment.GetEnvironmentVariable("ANDROID_ROOT")}");
        Console.WriteLine($"ANDROID_DATA: {Environment.GetEnvironmentVariable("ANDROID_DATA")}");
        
        Console.WriteLine("=========================");
    }
}
```

## 测试

运行诊断程序：

```bash
# 启动应用后查看日志
adb logcat | grep -i "platform\|android\|linux"
```

预期输出：

```
MonoMod PlatformDetection.OS: Android
MonoMod OS.Is(OSKind.Linux): True
MonoMod OS.Is(OSKind.Posix): True
Has Android filesystem: True
```

## 参考资料

1. **MonoMod 文档**：
   - `D:\MonoMod-reorganize\docs\AndroidBionicSupport.md`
   - MonoMod 完整支持 Android Bionic ARM64

2. **MonoMod 源码**：
   - `src/MonoMod.Utils/OSKind.cs` - 平台枚举定义
   - `src/MonoMod.Utils/PlatformDetection.cs` - 平台检测逻辑
   - `src/MonoMod.Core/Platforms/Systems/LinuxSystem.cs` - Linux/Android 系统实现

3. **本项目实现**：
   - `app/src/main/cpp/netcorehost_launcher.cpp` - C++ launcher 环境配置
   - `build-tools/bootstrap/SdlAndroidPatch.cs` - Android 平台检测示例

## 总结

- ✅ **MonoMod 已原生支持** Android 作为 Linux 子集
- ✅ **使用 MonoMod API** 进行平台检查（推荐）
- ✅ **已配置环境变量** 来简化平台差异
- ⚠️ **避免直接使用** `RuntimeInformation.IsOSPlatform(OSPlatform.Linux)`，因为它可能在 Android 上返回 false
- ✅ **优先使用** `PlatformDetection.OS.Is(OSKind.Linux)` 来检查 Linux 兼容性





