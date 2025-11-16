using System;
using System.IO;
using System.Reflection;
using System.Reflection.Metadata;
using System.Reflection.Metadata.Ecma335;
using System.Reflection.PortableExecutable;
using System.Text.Json;

namespace AssemblyChecker;

/// <summary>
/// 程序集检查器 - 检测 .NET 程序集的入口点和图标
/// </summary>
class Program
{
    static int Main(string[] args)
    {
        if (args.Length < 1)
        {
            Console.Error.WriteLine("用法: AssemblyChecker <assembly_path> [--extract-icon <output_path>]");
            return 2;
        }

        string assemblyPath = args[0];
        string? iconOutputPath = null;

        // 解析参数
        for (int i = 1; i < args.Length; i++)
        {
            if (args[i] == "--extract-icon" && i + 1 < args.Length)
            {
                iconOutputPath = args[i + 1];
                i++;
            }
        }

        try
        {
            var result = CheckAssembly(assemblyPath, iconOutputPath);

            // 输出 JSON 结果
            var json = JsonSerializer.Serialize(result, new JsonSerializerOptions
            {
                WriteIndented = false
            });
            Console.WriteLine(json);

            // 返回退出码
            return result.HasEntryPoint ? 0 : 1;
        }
        catch (Exception ex)
        {
            var errorResult = new CheckResult
            {
                Error = ex.Message,
                HasEntryPoint = false
            };

            Console.WriteLine(JsonSerializer.Serialize(errorResult));
            return 2;
        }
    }

    static CheckResult CheckAssembly(string assemblyPath, string? iconOutputPath)
    {
        var result = new CheckResult
        {
            AssemblyPath = assemblyPath,
            Exists = File.Exists(assemblyPath)
        };

        if (!result.Exists)
        {
            result.Error = "文件不存在";
            return result;
        }

        try
        {
            // 简单检测：直接加载程序集并检查入口点
            var assembly = Assembly.LoadFrom(assemblyPath);
            result.IsNetAssembly = true;

            // 检查入口点
            var entryPoint = assembly.EntryPoint;
            result.HasEntryPoint = entryPoint != null;

            if (result.HasEntryPoint && entryPoint != null)
            {
                result.EntryPointMethod = $"{entryPoint.DeclaringType?.FullName}::{entryPoint.Name}";
            }

            // 获取程序集名称和版本
            var assemblyName = assembly.GetName();
            result.AssemblyName = assemblyName.Name;
            result.AssemblyVersion = assemblyName.Version?.ToString();

            // 检查是否有图标（使用 PEReader 检查资源）
            using var fileStream = File.OpenRead(assemblyPath);
            using var peReader = new PEReader(fileStream);
            result.HasIcon = HasIconResource(peReader);

            // 如果需要提取图标
            if (result.HasIcon && iconOutputPath != null)
            {
                try
                {
                    ExtractIcon(peReader, iconOutputPath);
                    result.IconExtracted = true;
                    result.IconPath = iconOutputPath;
                }
                catch (Exception ex)
                {
                    result.IconExtractionError = ex.Message;
                }
            }

            return result;
        }
        catch (BadImageFormatException)
        {
            result.Error = "不是有效的 .NET 程序集";
            return result;
        }
        catch (Exception ex)
        {
            result.Error = $"读取程序集失败: {ex.Message}";
            return result;
        }
    }

    static bool HasIconResource(PEReader peReader)
    {
        try
        {
            var headers = peReader.PEHeaders;
            var resourcesDirectory = headers.PEHeader?.ResourceTableDirectory;

            return resourcesDirectory.HasValue && resourcesDirectory.Value.Size > 0;
        }
        catch
        {
            return false;
        }
    }

    static void ExtractIcon(PEReader peReader, string outputPath)
    {
        // 简化的图标提取（仅提取第一个图标资源）
        var headers = peReader.PEHeaders;
        var resourcesDirectory = headers.PEHeader?.ResourceTableDirectory;

        if (!resourcesDirectory.HasValue || resourcesDirectory.Value.Size == 0)
        {
            throw new Exception("没有找到图标资源");
        }

        // 注意：完整的图标提取需要解析 Win32 资源树
        // 这里提供一个简化实现的占位符
        throw new NotImplementedException("图标提取功能尚未完全实现");
    }

    class CheckResult
    {
        public string AssemblyPath { get; set; } = "";
        public bool Exists { get; set; }
        public bool IsNetAssembly { get; set; }
        public bool HasEntryPoint { get; set; }
        public string? EntryPointToken { get; set; }
        public string? EntryPointMethod { get; set; }
        public string? AssemblyName { get; set; }
        public string? AssemblyVersion { get; set; }
        public bool HasIcon { get; set; }
        public bool IconExtracted { get; set; }
        public string? IconPath { get; set; }
        public string? IconExtractionError { get; set; }
        public string? Error { get; set; }
    }
}
