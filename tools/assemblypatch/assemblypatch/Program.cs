using HarmonyLib;
using System.Reflection;
using System.Runtime.InteropServices;

namespace AssemblyPatch
{
    public class PatchManager
    {
        private static bool _patchesApplied = false;

        /// <summary>
        /// Hostfxr 可调用的入口点
        /// </summary>
        [UnmanagedCallersOnly(EntryPoint = "ApplyPatches")]
        public static int ApplyPatches(IntPtr assemblyPathPtr, int pathLength)
        {
            try
            {
                string assemblyPath = Marshal.PtrToStringUTF8(assemblyPathPtr, pathLength) ?? string.Empty;

                // 检测是否为 tModLoader.dll
                if (!IsTModLoader(assemblyPath))
                {
                    Console.WriteLine($"[AssemblyPatch] Skipping: Not a tModLoader assembly - {Path.GetFileName(assemblyPath)}");
                    return 0; // 跳过补丁
                }

                Console.WriteLine($"[AssemblyPatch] Detected tModLoader.dll, applying patches...");
                ApplyPatchesInternal(assemblyPath);
                return 1; // 成功
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[AssemblyPatch] Error: {ex.Message}");
                Console.WriteLine($"[AssemblyPatch] StackTrace: {ex.StackTrace}");
                return -1; // 失败
            }
        }

        /// <summary>
        /// 检测是否为 tModLoader 程序集
        /// </summary>
        private static bool IsTModLoader(string assemblyPath)
        {
            string fileName = Path.GetFileName(assemblyPath);

            // 检查文件名
            if (fileName.Equals("tModLoader.dll", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            // 尝试加载并检查类型
            try
            {
                Assembly assembly = Assembly.LoadFrom(assemblyPath);
                // 检查是否包含 Terraria.ModLoader 相关类型
                bool hasTMLTypes = assembly.GetType("Terraria.ModLoader.Engine.LoggingHooks") != null ||
                                  assembly.GetType("Terraria.ModLoader.Engine.TMLContentManager") != null;
                return hasTMLTypes;
            }
            catch
            {
                return false;
            }
        }

        /// <summary>
        /// 内部补丁应用方法
        /// </summary>
        private static void ApplyPatchesInternal(string assemblyPath)
        {
            if (_patchesApplied)
            {
                Console.WriteLine("[AssemblyPatch] Patches already applied, skipping.");
                return;
            }

            LoggingHooksHarmonyPatch(assemblyPath);
            TMLContentManagerPatch(assemblyPath);

            _patchesApplied = true;
            Console.WriteLine("[AssemblyPatch] All patches applied successfully!");
        }

        /// <summary>
        /// 传统入口点（向后兼容）
        /// </summary>
        public static void Main(string assembly)
        {
            ApplyPatchesInternal(assembly);
        }

        public static void LoggingHooksHarmonyPatch(string assembly)
        {
            // Load the external assembly dynamically
            Assembly externalAssembly = Assembly.LoadFrom(assembly);

            // Get the type for LoggingHooks from the external assembly
            System.Type? loggingHooksType = externalAssembly.GetType("Terraria.ModLoader.Engine.LoggingHooks");

            if (loggingHooksType == null)
            {
                Console.WriteLine("[AssemblyPatch] LoggingHooks class not found in the external assembly.");
                return;
            }

            // Get the MethodInfo for the method you want to patch
            MethodInfo? originalMethod = loggingHooksType.GetMethod("Init", BindingFlags.Static | BindingFlags.NonPublic);

            if (originalMethod == null)
            {
                Console.WriteLine("[AssemblyPatch] Init method not found in LoggingHooks.");
                return;
            }

            // Create a Harmony instance
            Harmony harmony = new Harmony("com.ralaunch.tmodloader.patch");

            // Create the HarmonyMethod for the prefix
            HarmonyMethod prefix = new HarmonyMethod(typeof(PatchManager), nameof(LoggingPatch_Prefix));

            // Apply the patch
            harmony.Patch(originalMethod, prefix);

            Console.WriteLine("[AssemblyPatch] LoggingHooks patch applied successfully!");
        }

        public static bool LoggingPatch_Prefix()
        {
            Console.WriteLine("[AssemblyPatch] LoggingHooks.Init method is now a no-op.");
            return false; // Skip the original method
        }

        public static void TMLContentManagerPatch(string assembly)
        {
            Assembly externalAssembly = Assembly.LoadFrom(assembly);

            // Get the type for TMLContentManager from the external assembly
            System.Type? tmlContentManagerType = externalAssembly.GetType("Terraria.ModLoader.Engine.TMLContentManager");

            if (tmlContentManagerType == null)
            {
                Console.WriteLine("[AssemblyPatch] TMLContentManager class not found in the external assembly.");
                return;
            }

            // Get the MethodInfo for the method you want to patch
            MethodInfo? originalMethod = tmlContentManagerType.GetMethod("TryFixFileCasings", BindingFlags.Static | BindingFlags.NonPublic);

            if (originalMethod == null)
            {
                Console.WriteLine("[AssemblyPatch] TryFixFileCasings method not found in TMLContentManager.");
                return;
            }

            // Create a Harmony instance
            Harmony harmony = new Harmony("com.ralaunch.tmodloader.patch");

            // Create the HarmonyMethod for the prefix
            HarmonyMethod prefix = new HarmonyMethod(typeof(PatchManager), nameof(TMLContentManagerPatch_Prefix));

            // Apply the patch
            harmony.Patch(originalMethod, prefix);

            Console.WriteLine("[AssemblyPatch] TMLContentManager patch applied successfully!");
        }

        public static bool TMLContentManagerPatch_Prefix()
        {
            Console.WriteLine("[AssemblyPatch] TMLContentManager.TryFixFileCasings is now a no-op.");
            return false;
        }
    }
}
