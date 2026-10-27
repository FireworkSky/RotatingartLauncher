using System;
using System.IO;
using System.Reflection;

namespace RAL.Bootstrap.CoreCLR;

/// <summary>
/// 补丁加载器：解析 RAL_PATCHES 环境变量并逐个加载/执行补丁。
/// Patch loader: resolves the RAL_PATCHES environment variable and
/// loads/runs each patch in order.
///
/// RAL_PATCHES 为 ':' 分隔的补丁程序集绝对路径列表。
/// RAL_PATCHES holds absolute paths of patch assemblies separated by the ':' separator
/// </summary>
internal static class PatchLoader
{
    /// <summary>当前接受的补丁 API 版本 / Currently accepted patch API version</summary>
    internal const int SupportedMajor = 1;
    internal const int SupportedMinor = 0;

    internal static void LoadAndRunPatches()
    {
        string? raw = Environment.GetEnvironmentVariable("RAL_PATCHES");
        if (string.IsNullOrWhiteSpace(raw))
        {
            Console.WriteLine("[RAL.Bootstrap] RAL_PATCHES not set, no patches");
            return;
        }

        foreach (string segment in raw.Split(':'))
        {
            string path = segment.Trim();
            if (string.IsNullOrEmpty(path))
            {
                continue;
            }

            if (!File.Exists(path))
            {
                Console.WriteLine($"[RAL.Bootstrap] Patch assembly not found: {path}");
                continue;
            }

            Assembly assembly;
            try
            {
                assembly = Assembly.LoadFrom(path);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[RAL.Bootstrap] Failed to load patch assembly {path}: {ex.Message}");
                continue;
            }

            Console.WriteLine($"[RAL.Bootstrap] Loaded patch assembly: {path}");
            RunPatchAssembly(assembly);
        }
    }

    private static void RunPatchAssembly(Assembly assembly)
    {
        Type?[] types;
        try
        {
            types = assembly.GetTypes();
        }
        catch (ReflectionTypeLoadException ex)
        {
            // 部分类型加载失败时继续处理已加载的部分
            // Continue with the types that did load when some fail
            types = ex.Types;
            Console.WriteLine($"[RAL.Bootstrap] Some types failed to load in {assembly.GetName().Name}: {ex.LoaderExceptions?.Length ?? 0} loader error(s)");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[RAL.Bootstrap] Failed to get types from {assembly.GetName().Name}: {ex.Message}");
            return;
        }

        bool anyPatchType = false;
        foreach (Type? type in types)
        {
            if (type is null)
            {
                continue;
            }
            if (!typeof(PatchBase).IsAssignableFrom(type) || type.IsAbstract)
            {
                continue;
            }

            anyPatchType = true;
            RunPatchType(type);
        }

        if (!anyPatchType)
        {
            Console.WriteLine($"[RAL.Bootstrap] No PatchBase implementation found in {assembly.GetName().Name}");
        }
    }

    private static void RunPatchType(Type type)
    {
        // 版本门禁在实例化之前执行，避免构造副作用
        // Version gate runs before instantiation to avoid constructor side effects
        RALPatchAttribute? attribute = type.GetCustomAttribute<RALPatchAttribute>();
        if (attribute is null)
        {
            Console.WriteLine($"[RAL.Bootstrap] Skipped {type.FullName}: missing [RALPatch] attribute");
            return;
        }
        if (attribute.Major != SupportedMajor || attribute.Minor != SupportedMinor)
        {
            Console.WriteLine(
                $"[RAL.Bootstrap] Skipped {type.FullName}: unsupported API version " +
                $"{attribute.Major}.{attribute.Minor} (supported: {SupportedMajor}.{SupportedMinor})");
            return;
        }

        PatchBase instance;
        try
        {
            instance = (PatchBase)Activator.CreateInstance(type)!;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[RAL.Bootstrap] Failed to instantiate {type.FullName}: {ex.Message}");
            return;
        }

        try
        {
            instance.Initialize();
            Console.WriteLine($"[RAL.Bootstrap] Patch initialized: {type.FullName}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[RAL.Bootstrap] Patch {type.FullName}.Initialize() threw: {ex.Message}");
        }
    }
}
