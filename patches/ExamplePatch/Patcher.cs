using System;
using System.Reflection;

namespace ExamplePatch
{
    /// <summary>
    /// 示例补丁程序集
    /// 在游戏启动前被调用，可以修改游戏行为
    /// </summary>
    public class Patcher
    {
        /// <summary>
        /// 补丁初始化方法
        /// 会在游戏程序集加载前被自动调用
        /// 使用 ComponentEntryPoint 签名（兼容性签名）
        /// </summary>
        public static int Initialize(IntPtr arg, int argSize)
        {
            try
            {
                Console.WriteLine("========================================");
                Console.WriteLine("[ExamplePatch] Initializing patch...");
                Console.WriteLine("========================================");

                // 打印补丁信息
                PrintPatchInfo();

                // 注册程序集解析事件
                RegisterAssemblyResolve();

                // 应用补丁逻辑
                ApplyPatches();

                Console.WriteLine("[ExamplePatch] Patch initialized successfully");
                Console.WriteLine("========================================");

                return 0; // 成功
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ExamplePatch] ERROR: {ex.Message}");
                Console.WriteLine($"[ExamplePatch] Stack: {ex.StackTrace}");
                return -1; // 失败
            }
        }

        /// <summary>
        /// 打印补丁信息
        /// </summary>
        private static void PrintPatchInfo()
        {
            var assembly = Assembly.GetExecutingAssembly();
            var version = assembly.GetName().Version;

            Console.WriteLine($"Patch Assembly: {assembly.GetName().Name}");
            Console.WriteLine($"Version: {version}");
            Console.WriteLine($"Location: {assembly.Location}");
            Console.WriteLine($".NET Version: {Environment.Version}");
        }

        /// <summary>
        /// 注册程序集解析事件
        /// </summary>
        private static void RegisterAssemblyResolve()
        {
            AppDomain.CurrentDomain.AssemblyResolve += (sender, args) =>
            {
                Console.WriteLine($"[ExamplePatch] Assembly resolve: {args.Name}");
                return null;
            };

            Console.WriteLine("[ExamplePatch] AssemblyResolve handler registered");
        }

        /// <summary>
        /// 应用补丁逻辑
        /// </summary>
        private static void ApplyPatches()
        {
            Console.WriteLine("[ExamplePatch] Applying patches...");

            // 示例1: 设置环境变量
            Environment.SetEnvironmentVariable("EXAMPLE_PATCH_LOADED", "true");
            Console.WriteLine("  ✓ Environment variable set");

            // 示例2: 注册 AppDomain 事件
            AppDomain.CurrentDomain.AssemblyLoad += (sender, args) =>
            {
                var assemblyName = args.LoadedAssembly.GetName().Name;
                if (assemblyName != null &&
                    !assemblyName.StartsWith("System") &&
                    !assemblyName.StartsWith("Microsoft"))
                {
                    Console.WriteLine($"[ExamplePatch] Assembly loaded: {assemblyName}");
                }
            };
            Console.WriteLine("  ✓ AssemblyLoad handler registered");

            // 示例3: 在这里可以使用 Harmony 或 MonoMod 进行运行时修改
            // 例如:
            // var harmony = new Harmony("com.example.patch");
            // harmony.PatchAll();

            Console.WriteLine("[ExamplePatch] Patches applied successfully");
        }

        /// <summary>
        /// 可选的清理方法
        /// </summary>
        public static void Cleanup()
        {
            Console.WriteLine("[ExamplePatch] Cleaning up...");
            Environment.SetEnvironmentVariable("EXAMPLE_PATCH_LOADED", null);
        }
    }
}
