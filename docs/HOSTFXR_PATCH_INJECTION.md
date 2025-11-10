# 在 hostfxr_run_app 运行程序集前插入补丁方法

## 概述

本实现允许在 `hostfxr_run_app` 运行应用程序之前，加载并调用 MonoMod 补丁程序集来应用补丁。

## 工作流程

```
1. hostfxr 初始化上下文
   ↓
2. 获取委托加载器 (get_delegate_loader)
   ↓
3. 加载补丁程序集 (bootstrap.dll)
   ↓
4. 调用补丁方法 (ApplyPatches)
   ↓
5. 补丁方法应用 MonoMod 补丁到目标程序集
   ↓
6. 运行应用程序 (run_app)
```

## C++ 实现

### 实现位置

`app/src/main/cpp/netcorehost_launcher.cpp` 中的 `apply_monomod_patches()` 函数：

```cpp
static bool apply_monomod_patches(
    netcorehost::HostfxrContext* context, 
    const char* app_path
);
```

### 补丁程序集查找

函数会按以下顺序查找补丁程序集：

1. `{app_dir}/bootstrap.dll`
2. `{app_dir}/MonoMod.Patcher.dll`
3. `{app_dir}/AssemblyMain.dll`
4. `{app_dir}/patches/bootstrap.dll`

### 方法查找

函数会尝试加载以下类型和方法：

1. `AssemblyMain.Program::ApplyPatches`
2. `AssemblyMain.PatchLoader::ApplyPatches`
3. `Bootstrap.Program::ApplyPatches`
4. `MonoMod.Patcher.Patcher::Apply`

## C# Bootstrap 程序集实现

### 方法 1: 使用 UnmanagedCallersOnly（推荐）

```csharp
using System;
using System.Runtime.InteropServices;
using MonoMod.RuntimeDetour;

namespace AssemblyMain
{
    public static class Program
    {
        /// <summary>
        /// 应用 MonoMod 补丁到目标程序集
        /// </summary>
        /// <param name="appAssemblyPath">应用程序集路径</param>
        [UnmanagedCallersOnly]
        public static void ApplyPatches(IntPtr appAssemblyPathPtr)
        {
            // 将 IntPtr 转换为字符串
            string appAssemblyPath = Marshal.PtrToStringAnsi(appAssemblyPathPtr) ?? "";
            
            Console.WriteLine($"[Bootstrap] Applying patches to: {appAssemblyPath}");
            
            try
            {
                // 加载目标程序集
                var targetAssembly = System.Reflection.Assembly.LoadFrom(appAssemblyPath);
                
                // 应用 MonoMod 补丁
                // 这里可以使用 MonoMod.Patcher 或其他补丁方法
                ApplyMonoModPatches(targetAssembly);
                
                Console.WriteLine("[Bootstrap] Patches applied successfully");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Bootstrap] Error applying patches: {ex}");
                throw;
            }
        }
        
        private static void ApplyMonoModPatches(System.Reflection.Assembly targetAssembly)
        {
            // 实现 MonoMod 补丁逻辑
            // 例如：加载补丁程序集并应用
            var patcher = new MonoMod.Patcher.Patcher(targetAssembly);
            patcher.Apply();
        }
    }
}
```

### 方法 2: 使用委托类型

```csharp
using System;
using System.Runtime.InteropServices;

namespace AssemblyMain
{
    // 定义委托类型
    public delegate void ApplyPatchesDelegate(string appAssemblyPath);
    
    public static class Program
    {
        /// <summary>
        /// 应用 MonoMod 补丁到目标程序集
        /// </summary>
        /// <param name="appAssemblyPath">应用程序集路径</param>
        public static void ApplyPatches(string appAssemblyPath)
        {
            Console.WriteLine($"[Bootstrap] Applying patches to: {appAssemblyPath}");
            
            try
            {
                // 加载目标程序集
                var targetAssembly = System.Reflection.Assembly.LoadFrom(appAssemblyPath);
                
                // 应用 MonoMod 补丁
                ApplyMonoModPatches(targetAssembly);
                
                Console.WriteLine("[Bootstrap] Patches applied successfully");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Bootstrap] Error applying patches: {ex}");
                throw;
            }
        }
        
        private static void ApplyMonoModPatches(System.Reflection.Assembly targetAssembly)
        {
            // 实现 MonoMod 补丁逻辑
        }
    }
}
```

## 完整的 Bootstrap 实现示例

### Program.cs

```csharp
using System;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using MonoMod.RuntimeDetour;
using MonoMod.Utils;

namespace AssemblyMain
{
    public static class Program
    {
        [UnmanagedCallersOnly]
        public static void ApplyPatches(IntPtr appAssemblyPathPtr)
        {
            string appAssemblyPath = Marshal.PtrToStringAnsi(appAssemblyPathPtr) ?? "";
            
            Console.WriteLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Console.WriteLine("🔧 Applying MonoMod Patches");
            Console.WriteLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Console.WriteLine($"Target Assembly: {appAssemblyPath}");
            
            try
            {
                // 1. 加载目标程序集
                var targetAssembly = Assembly.LoadFrom(appAssemblyPath);
                Console.WriteLine($"✓ Loaded target assembly: {targetAssembly.FullName}");
                
                // 2. 查找补丁程序集
                string appDir = Path.GetDirectoryName(appAssemblyPath) ?? "";
                string[] patchAssemblyPaths = {
                    Path.Combine(appDir, "patches", "*.dll"),
                    Path.Combine(appDir, "*.mm.dll"),  // MonoMod 补丁文件
                };
                
                foreach (var pattern in patchAssemblyPaths)
                {
                    var patchFiles = Directory.GetFiles(appDir, "*.mm.dll", SearchOption.TopDirectoryOnly);
                    foreach (var patchFile in patchFiles)
                    {
                        Console.WriteLine($"⏳ Loading patch: {patchFile}");
                        ApplyPatch(targetAssembly, patchFile);
                    }
                }
                
                // 3. 应用内存中的补丁（如果有）
                ApplyInMemoryPatches(targetAssembly);
                
                Console.WriteLine("✅ All patches applied successfully");
                Console.WriteLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"❌ Error applying patches: {ex}");
                Console.WriteLine($"Stack trace: {ex.StackTrace}");
                throw;
            }
        }
        
        private static void ApplyPatch(Assembly targetAssembly, string patchAssemblyPath)
        {
            try
            {
                // 加载补丁程序集
                var patchAssembly = Assembly.LoadFrom(patchAssemblyPath);
                Console.WriteLine($"✓ Loaded patch assembly: {patchAssembly.FullName}");
                
                // 使用 MonoMod.Patcher 应用补丁
                // 注意：这需要 MonoMod.Patcher 已加载
                var patcherType = patchAssembly.GetType("MonoMod.Patcher.Patcher");
                if (patcherType != null)
                {
                    var patcher = Activator.CreateInstance(patcherType, targetAssembly);
                    var applyMethod = patcherType.GetMethod("Apply");
                    applyMethod?.Invoke(patcher, null);
                    Console.WriteLine($"✓ Applied patch: {patchAssembly.FullName}");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"⚠️  Failed to apply patch {patchAssemblyPath}: {ex.Message}");
                // 继续处理其他补丁
            }
        }
        
        private static void ApplyInMemoryPatches(Assembly targetAssembly)
        {
            // 应用运行时补丁（例如：Hook、Detour 等）
            // 这里可以使用 MonoMod.RuntimeDetour
            try
            {
                // 示例：Hook 一个方法
                // var originalMethod = targetAssembly.GetType("MyNamespace.MyClass")
                //     .GetMethod("MyMethod");
                // var hook = new Hook(originalMethod, MyHookMethod);
                // Console.WriteLine("✓ Applied runtime hook");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"⚠️  Failed to apply in-memory patches: {ex.Message}");
            }
        }
    }
}
```

### 项目文件 (bootstrap.csproj)

```xml
<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <AllowUnsafeBlocks>true</AllowUnsafeBlocks>
    <OutputType>Library</OutputType>
    <AssemblyName>bootstrap</AssemblyName>
  </PropertyGroup>

  <ItemGroup>
    <PackageReference Include="MonoMod.RuntimeDetour" Version="23.12.14" />
    <PackageReference Include="MonoMod.Utils" Version="23.12.14" />
  </ItemGroup>

</Project>
```

## 编译和部署

### 1. 编译 Bootstrap 程序集

```bash
dotnet build bootstrap.csproj -c Release
```

### 2. 复制到应用目录

将编译后的 `bootstrap.dll` 复制到应用程序目录：

```
{app_dir}/
├── tModLoader.dll          # 主程序集
├── bootstrap.dll           # 补丁程序集
└── patches/                # 可选的补丁目录
    └── *.mm.dll            # MonoMod 补丁文件
```

### 3. 确保依赖项可用

确保以下依赖项在应用目录或运行时可用：

- `MonoMod.RuntimeDetour.dll`
- `MonoMod.Utils.dll`
- `MonoMod.Patcher.dll` (如果需要)
- 其他 MonoMod 相关程序集

## 调试

### 启用详细日志

在 `netcorehost_launcher.cpp` 中已启用详细日志，查看 logcat：

```bash
adb logcat | grep -i "bootstrap\|patch\|monomod"
```

### 常见问题

1. **补丁程序集未找到**
   - 检查 `bootstrap.dll` 是否在应用目录中
   - 检查文件权限

2. **方法加载失败**
   - 确保方法有 `[UnmanagedCallersOnly]` 属性
   - 或定义正确的委托类型
   - 检查方法签名是否匹配

3. **补丁应用失败**
   - 检查目标程序集是否正确加载
   - 检查 MonoMod 依赖项是否可用
   - 查看详细错误日志

## 高级用法

### 使用 AssemblyLoadContext

```csharp
using System.Runtime.Loader;

private static void LoadPatchAssembly(string patchPath)
{
    var alc = new AssemblyLoadContext("PatchContext", isCollectible: true);
    var patchAssembly = alc.LoadFromAssemblyPath(patchPath);
    // 应用补丁...
    // alc.Unload();  // 如果需要卸载
}
```

### 延迟补丁应用

可以在应用程序启动后再应用某些补丁：

```csharp
[UnmanagedCallersOnly]
public static void ApplyPatches(IntPtr appAssemblyPathPtr)
{
    // 立即应用必要的补丁
    ApplyCriticalPatches(appAssemblyPathPtr);
    
    // 注册应用程序启动后的补丁
    AppDomain.CurrentDomain.AssemblyLoad += (sender, args) =>
    {
        if (args.LoadedAssembly.FullName.Contains("TargetAssembly"))
        {
            ApplyLatePatches(args.LoadedAssembly);
        }
    };
}
```

## 参考资料

- [.NET Native Hosting](https://docs.microsoft.com/en-us/dotnet/core/tutorials/netcore-hosting)
- [MonoMod Documentation](https://github.com/MonoMod/MonoMod)
- [UnmanagedCallersOnly Attribute](https://docs.microsoft.com/en-us/dotnet/api/system.runtime.interopservices.unmanagedcallersonlyattribute)

