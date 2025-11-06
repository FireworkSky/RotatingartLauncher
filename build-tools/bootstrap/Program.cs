
using HarmonyLib;
using System;
using System.Collections;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Reflection.Metadata;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using System.Runtime.Loader;
using System.Threading;
// using Terraria.ModLoader.Core; // 移除硬引用，改用反射动态加载

namespace AssemblyMain
{
    public static class Program
    {



        private static readonly Dictionary<string, nint> assemblies = new Dictionary<string, nint>();

        private static readonly ConcurrentDictionary<string, Assembly> _resolvedAssemblies = new();

        private static readonly ConcurrentDictionary<string, string> _assemblyPathCache = new();


        // Native层的coreclr_execute_assembly包装函数指针
        private static IntPtr _executeAssemblyCallbackPtr = IntPtr.Zero;
        
        // 内部使用的委托类型（用于调用Native回调）
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
        private delegate int ExecuteAssemblyDelegate(string assemblyPath);

        /// <summary>
        /// 设置Native层的executeAssembly回调函数（使用UnmanagedCallersOnly）
        /// </summary>
        [UnmanagedCallersOnly(CallConvs = new[] { typeof(CallConvCdecl) })]
        public static void SetExecuteAssemblyCallback(IntPtr callbackPtr)
        {
            _executeAssemblyCallbackPtr = callbackPtr;
            Console.WriteLine("[Bootstrap] ExecuteAssembly callback registered at: 0x" + callbackPtr.ToString("X"));
        }

        /// <summary>
        /// Bootstrap启动入口（使用UnmanagedCallersOnly），供Native层通过coreclr_create_delegate调用
        /// </summary>
        /// <param name="targetGamePathPtr">目标游戏程序集路径（UTF-8字符串指针）</param>
        /// <returns>0表示成功，其他值表示错误码</returns>
        [UnmanagedCallersOnly(CallConvs = new[] { typeof(CallConvCdecl) })]
        public static int LaunchGame(IntPtr targetGamePathPtr)
        {
            try
            {
                // 步骤0: 初始化Console重定向到logcat
                DualWriter.Initialize();
                
                // 步骤1: 测试IntPtr是否有效
                if (targetGamePathPtr == IntPtr.Zero)
                {
                    Console.WriteLine("[Bootstrap] ERROR: targetGamePathPtr is null");
                    return -2;
                }
                
                // 步骤2: 尝试解析字符串
                string targetGamePath = null;
                try
                {
                    targetGamePath = Marshal.PtrToStringUTF8(targetGamePathPtr);
                    Console.WriteLine($"[Bootstrap] Target game path: {targetGamePath}");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ERROR: Failed to parse string: {ex.Message}");
                    return -3; // 字符串解析失败
                }
                
                if (string.IsNullOrEmpty(targetGamePath))
                {
                    Console.WriteLine("[Bootstrap] ERROR: Target game path is empty");
                    return -4; // 字符串为空
                }
                
                // 步骤3: 检查文件是否存在
                if (!File.Exists(targetGamePath))
                {
                    Console.WriteLine($"[Bootstrap] ERROR: File not found: {targetGamePath}");
                    return -5; // 文件不存在
                }
                
                // 步骤4: 检查回调是否设置
                if (_executeAssemblyCallbackPtr == IntPtr.Zero)
                {
                    Console.WriteLine("[Bootstrap] ERROR: Execute assembly callback not set");
                    return -6; // 回调未设置
                }
                
                Console.WriteLine("[Bootstrap] All pre-checks passed, starting initialization...");
                
                // 声明targetAssembly在外层作用域，以便在步骤6中使用
                Assembly targetAssembly = null;
                
                // 步骤5: 执行初始化（细化错误码）
                try
                {
                    string directoryName = Path.GetDirectoryName(targetGamePath);
                    if (string.IsNullOrEmpty(directoryName))
                    {
                        Console.WriteLine("[Bootstrap] ERROR: Cannot get directory name");
                        return -71;
                    }
                    
                    if (!Directory.Exists(directoryName))
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Directory not exist: {directoryName}");
                        return -72;
                    }
                    
                    try
                    {
            Directory.SetCurrentDirectory(directoryName);
                        Console.WriteLine($"[Bootstrap] Working directory set to: {directoryName}");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Cannot set working directory: {ex.Message}");
                        return -73;
                    }
                    
                    try
                    {
            AssemblyLoadContext.Default.Resolving += ResolveManagedAssemblies;
          
                        // 🔧 注册 UnmanagedDll 解析器（必须在加载任何程序集之前！）
                        AssemblyLoadContext.Default.ResolvingUnmanagedDll += SdlAndroidPatch.ResolveUnmanagedDll;

            Thread.CurrentThread.Name = "Entry Thread";

                        // 设置FNA/MonoGame环境变量
                        Environment.SetEnvironmentVariable("FNA_WORKAROUND_WINDOW_RESIZABLE", "1");
                        // 强制FNA使用SDL Scancode而不是Keycode（虚拟控制需要）
                        Environment.SetEnvironmentVariable("FNA_KEYBOARD_USE_SCANCODES", "1");
                        // 强制横屏方向（覆盖FNA默认的"LandscapeLeft LandscapeRight Portrait"）
                        Environment.SetEnvironmentVariable("SDL_ORIENTATIONS", "LandscapeLeft LandscapeRight");
                        
                        // 🔧 Android OpenGL 配置：使用 gl4es 转换层
                        // gl4es 将 desktop OpenGL 调用转换为 OpenGL ES（Android 原生支持）
                        Environment.SetEnvironmentVariable("FNA3D_OPENGL_DRIVER", "gl4es");
                        Environment.SetEnvironmentVariable("LIBGL_ES", "2"); // gl4es: 使用 OpenGL ES 2.0
                        
                        // 🔧 Android 文件系统重定向：避免访问受限路径 (/data/.config 等)
                        // 这些环境变量也在 native 代码中设置，这里是双重保险
                        string gameDir = Directory.GetCurrentDirectory();
                        Environment.SetEnvironmentVariable("HOME", gameDir);
                        Environment.SetEnvironmentVariable("XDG_CONFIG_HOME", Path.Combine(gameDir, ".config"));
                        Environment.SetEnvironmentVariable("XDG_DATA_HOME", Path.Combine(gameDir, ".local", "share"));
                        Environment.SetEnvironmentVariable("XDG_CACHE_HOME", Path.Combine(gameDir, ".cache"));
                        Environment.SetEnvironmentVariable("TMPDIR", Path.Combine(gameDir, "tmp"));
                        
                        Console.WriteLine("[Bootstrap] Environment configured: gl4es + file system redirection");
                        Console.WriteLine($"[Bootstrap] HOME={gameDir}");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Basic environment setup failed: {ex.Message}");
                        return -74;
                    }
                    
                    try
                    {
                        // 预加载Bootstrap目录中的修复版MonoMod
                        string bootstrapDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
                        if (!string.IsNullOrEmpty(bootstrapDir) && Directory.Exists(bootstrapDir))
                        {
                            string[] criticalAssemblies = new[]
                            {
                                "MonoMod.Core.dll",
                                "MonoMod.Utils.dll",
                                "MonoMod.RuntimeDetour.dll",
                                "0Harmony.dll"
                            };
                            
                            foreach (var dllName in criticalAssemblies)
                            {
                                string dllPath = Path.Combine(bootstrapDir, dllName);
                                if (File.Exists(dllPath))
                                {
                                    try
                                    {
                                        Assembly.LoadFrom(dllPath);
                                    }
                                    catch { }
                                }
                            }
                        }
                        
                        _assemblyPathCache.Clear();
                        AddAssembliesToCache(directoryName);
                        
                        string librariesPath = Path.Combine(directoryName, "Libraries");
                        if (Directory.Exists(librariesPath))
                        {
                            AddAssembliesToCache(librariesPath, true);
                        }
                        
                        if (!string.IsNullOrEmpty(bootstrapDir) && Directory.Exists(bootstrapDir))
                        {
                            AddAssembliesToCache(bootstrapDir, true, forceOverride: true);
                        }
                        
                        AppDomain.CurrentDomain.AssemblyResolve += CurrentDomain_AssemblyResolve;
                        Console.WriteLine($"[Bootstrap] Assembly cache ready ({_assemblyPathCache.Count} entries)");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] Assembly cache failed: {ex.Message}");
                        return -75;
                    }
                    
                    // 加载目标游戏程序集（这样补丁才能找到要patch的类型）
                    try
                    {
                        Console.WriteLine($"[Bootstrap] Loading target assembly: {Path.GetFileName(targetGamePath)}");
                        targetAssembly = Assembly.LoadFrom(targetGamePath);
                        Console.WriteLine($"[Bootstrap] Target assembly loaded successfully");
                        
                        // 🔧 立即应用补丁（MonoGame.Framework 现在已加载）
                        try
                        {
                            FuncLoaderPatch.Apply();
                            MouseInputPatch.Apply(); // 修复触屏偏移问题
                        }
                        catch (Exception patchEx)
                        {
                            Console.WriteLine($"[Bootstrap] Patch warning: {patchEx.Message}");
                        }
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] ERROR: Cannot load target assembly: {ex.Message}");
                        return -76;
                    }
                    
                    // 检测游戏类型并决定需要哪些补丁
                    GameDetector.GameInfo gameInfo = null;
                    try
                    {
                        gameInfo = GameDetector.DetectGame(targetGamePath, targetAssembly);
                        GameDetector.PrintGameInfo(gameInfo);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] Game detection failed: {ex.Message}");
                        gameInfo = new GameDetector.GameInfo
                        {
                            RequiresFileCasingsPatch = true
                        };
                    }
                    
                    // GetEntryAssembly补丁
                    try
                    {
                        GetEntryAssembly.GetEntryAssemblyPatch();
                        Console.WriteLine("[Bootstrap] GetEntryAssembly patch applied");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] GetEntryAssembly patch failed: {ex.Message}");
                    }
                    
                    // LoggingHooks补丁
                    try
                    {
                        LoggingHooksPatch.LoggingHooksHarmonyPatch(targetGamePath);
                        Console.WriteLine("[Bootstrap] LoggingHooks patch applied");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] LoggingHooks patch failed: {ex.Message}");
                    }
                    
                    // TryFixFileCasings补丁
                    if (gameInfo != null && gameInfo.RequiresFileCasingsPatch)
                    {
                        try
                        {
                            TryFixFileCasings.TryFixFileCasingsPatch(targetGamePath);
                            Console.WriteLine("[Bootstrap] FileCasings patch applied");
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[Bootstrap] FileCasings patch failed: {ex.Message}");
                        }
                    }
                    
                    // Terraria全屏补丁
                    if (gameInfo != null && gameInfo.RequiresFullscreenPatch)
                    {
                        try
                        {
                            TerrariaFullscreenPatch.ApplyFullscreenPatch(targetGamePath);
                            Console.WriteLine("[Bootstrap] Fullscreen patch applied");
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[Bootstrap] Fullscreen patch failed: {ex.Message}");
                        }
                    }
                    
                    // 性能监控和FPS钩子
                    try
                    {
                        PerformanceMonitor.Start();
                        FnaPerformanceHook.ApplyPatch();
                        Console.WriteLine("[Bootstrap] Performance monitoring enabled");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Bootstrap] Performance monitoring failed: {ex.Message}");
                    }
                    
                    Console.WriteLine("[Bootstrap] ✅ Initialization completed successfully!");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ERROR: Initialization failed: {ex.Message}");
                    Console.WriteLine($"[Bootstrap] Stack trace: {ex.StackTrace}");
                    return -7;
                }
                
                // 步骤6: 通过反射直接调用游戏入口点（不使用native callback以避免Mono冲突）
                try
                {
                    Console.WriteLine("[Bootstrap] �� Launching game via reflection...");
                    Console.WriteLine($"[Bootstrap] Target assembly: {targetAssembly.FullName}");
                    
                    // 获取入口点
                    MethodInfo entryPoint = targetAssembly.EntryPoint;
                    if (entryPoint == null)
                    {
                        Console.WriteLine("[Bootstrap] ERROR: No entry point found in target assembly");
                        return -81;
                    }
                    
                    Console.WriteLine($"[Bootstrap] Entry point: {entryPoint.DeclaringType.FullName}.{entryPoint.Name}");
                    
                    // 准备参数（空字符串数组）
                    object[] args = new object[] { new string[0] };
                    
                    // 调用入口点
                    Console.WriteLine("[Bootstrap] �� Invoking game entry point...");
                    object result = entryPoint.Invoke(null, args);
                    
                    // 返回结果
                    int exitCode = result is int code ? code : 0;
                    Console.WriteLine($"[Bootstrap] ✅ Game execution finished with exit code: {exitCode}");
                    return exitCode;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] ERROR: Game execution failed: {ex.Message}");
                    Console.WriteLine($"[Bootstrap] Stack trace: {ex.StackTrace}");
                    if (ex.InnerException != null)
                    {
                        Console.WriteLine($"[Bootstrap] Inner exception: {ex.InnerException.Message}");
                        Console.WriteLine($"[Bootstrap] Inner stack trace: {ex.InnerException.StackTrace}");
                    }
                    return -8;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Bootstrap] FATAL ERROR: {ex.Message}");
                Console.WriteLine($"[Bootstrap] Stack trace: {ex.StackTrace}");
                return -1;
            }
        }

        private static void InitializeGameEnvironment(string targetGamePath)
        {
            // 验证文件存在
            if (!File.Exists(targetGamePath))
            {
                throw new FileNotFoundException($"Target game assembly not found: {targetGamePath}");
            }

            string directoryName = Path.GetDirectoryName(targetGamePath);
          
            Console.WriteLine($"[Bootstrap] Working directory: {directoryName}");
            Directory.SetCurrentDirectory(directoryName);


            // 设置SavePath
            string savePath = directoryName + "_Saves";
            Console.WriteLine($"[Bootstrap] Save path: {savePath}");

            AssemblyLoadContext.Default.Resolving += ResolveManagedAssemblies;

            Thread.CurrentThread.Name = "Entry Thread";

            Environment.SetEnvironmentVariable("FNA_WORKAROUND_WINDOW_RESIZABLE", "1");


            // 读取配置文件
            if (File.Exists("cli-argsConfig.txt"))

            {
                var configArgs = File.ReadAllLines("cli-argsConfig.txt").SelectMany(a => a.Split(" ", 2)).ToArray();
                Console.WriteLine($"[Bootstrap] Loaded {configArgs.Length} args from cli-argsConfig.txt");
            }

            if (File.Exists("env-argsConfig.txt"))

            {
                foreach (var environmentVar in File.ReadAllLines("env-argsConfig.txt").Select(text => text.Split("=")).Where(envVar => envVar.Length == 2))

                {
                    Environment.SetEnvironmentVariable(environmentVar[0], environmentVar[1]);

                    Console.WriteLine($"[Bootstrap] Set env: {environmentVar[0]}={environmentVar[1]}");
                }
            }

            _assemblyPathCache.Clear();

            AddAssembliesToCache(directoryName);

            string librariesPath = Path.Combine(directoryName, "Libraries");
            if (Directory.Exists(librariesPath))
            {
                AddAssembliesToCache(librariesPath, true);
            }


            Console.WriteLine($"[Bootstrap] Built assembly path cache with {_assemblyPathCache.Count} entries");

            AppDomain.CurrentDomain.AssemblyResolve += CurrentDomain_AssemblyResolve;


            // 应用Harmony补丁
            Console.WriteLine($"[Bootstrap] Applying Harmony patches...");
            GetEntryAssembly.GetEntryAssemblyPatch();
            LoggingHooksPatch.LoggingHooksHarmonyPatch(targetGamePath);
            TryFixFileCasings.TryFixFileCasingsPatch(targetGamePath);
            // 全屏补丁已移至SDL/FNA底层

            Console.WriteLine($"[Bootstrap] Initialization complete");
        }

        public static void Main(string[] args)

        {
            // 从args[0]获取目标游戏程序集路径
            if (args.Length == 0)
            {
                Console.WriteLine("[Bootstrap] ERROR: No target game assembly specified!");
                Console.WriteLine("[Bootstrap] Usage: Bootstrap.dll <GameAssemblyPath>");
                Environment.Exit(-1);
                return;
            }

            string targetGamePath = args[0];
            Console.WriteLine($"[Bootstrap] Target game assembly: {targetGamePath}");

            if (!File.Exists(targetGamePath))
            {
                Console.WriteLine($"[Bootstrap] ERROR: Target game assembly not found: {targetGamePath}");
                Environment.Exit(-1);
                return;
            }

            string directoryName = Path.GetDirectoryName(targetGamePath);
            Console.WriteLine($"[Bootstrap] Working directory: {directoryName}");
            Directory.SetCurrentDirectory(directoryName);

            // 快速检测游戏类型
            GameDetector.GameInfo gameInfo = null;
            Assembly gameAssembly = null;
            
            try
            {
                // 先加载程序集以便检测
                AssemblyLoadContext.Default.Resolving += ResolveManagedAssemblies;
                
                // 🔧 注册 UnmanagedDll 解析器（必须在加载任何程序集之前！）
                AssemblyLoadContext.Default.ResolvingUnmanagedDll += SdlAndroidPatch.ResolveUnmanagedDll;
                
                Thread.CurrentThread.Name = "Entry Thread";
                
                _assemblyPathCache.Clear();
                AddAssembliesToCache(directoryName);
                
                string librariesPath = Path.Combine(directoryName, "Libraries");
                if (Directory.Exists(librariesPath))
                {
                    AddAssembliesToCache(librariesPath, true);
                }
                
                string bootstrapDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
                if (!string.IsNullOrEmpty(bootstrapDir) && Directory.Exists(bootstrapDir))
                {
                    AddAssembliesToCache(bootstrapDir, true, forceOverride: true);
                }
                
                AppDomain.CurrentDomain.AssemblyResolve += CurrentDomain_AssemblyResolve;
                
                gameAssembly = Assembly.LoadFrom(targetGamePath);
                
                // 🔧 立即应用补丁（MonoGame.Framework 现在已加载）
                try
                {
                    FuncLoaderPatch.Apply();
                    MouseInputPatch.Apply(); // 修复触屏偏移问题
                }
                catch (Exception patchEx)
                {
                    Console.WriteLine($"[Bootstrap] Patch warning: {patchEx.Message}");
                }
                
                gameInfo = GameDetector.DetectGame(targetGamePath, gameAssembly);
                GameDetector.PrintGameInfo(gameInfo);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Bootstrap] Detection failed: {ex.Message}");
                gameInfo = new GameDetector.GameInfo();
            }

            // 准备启动参数
            string[] gameArgs = null;
            
            // 🎯 tModLoader特有设置
            if (gameInfo.Type == GameDetector.GameType.Terraria || 
                gameInfo.Type == GameDetector.GameType.TerrariatModLoader)
            {
                // 设置SavePath（tModLoader特有）
                string savePath = directoryName + "_Saves";
                Console.WriteLine($"[Bootstrap] Save path: {savePath}");
                
                // tModLoader启动参数
                gameArgs = new string[] {
                    "-tmlsavedirectory",
                    savePath
                };

                // 应用tModLoader特有补丁
                try
                {
            GetEntryAssembly.GetEntryAssemblyPatch();
                    LoggingHooksPatch.LoggingHooksHarmonyPatch(targetGamePath);
                    
                    if (gameInfo.RequiresFileCasingsPatch)
                    {
                        TryFixFileCasings.TryFixFileCasingsPatch(targetGamePath);
                    }
                    
                    Console.WriteLine("[Bootstrap] tModLoader patches applied");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[Bootstrap] Patch failed: {ex.Message}");
                }
            }
            else
            {
                // 其他游戏（如SMAPI）：无启动参数，无特殊补丁
                gameArgs = new string[0];
                Console.WriteLine("[Bootstrap] Generic game mode (no special patches)");
            }

            // 读取配置文件
            Environment.SetEnvironmentVariable("FNA_WORKAROUND_WINDOW_RESIZABLE", "1");
            
            // 🔧 Android OpenGL 配置：使用 gl4es 转换层
            Environment.SetEnvironmentVariable("FNA3D_OPENGL_DRIVER", "gl4es");
            Environment.SetEnvironmentVariable("LIBGL_ES", "2");
            
            // 🔧 Android 文件系统重定向
            string currentDir = Directory.GetCurrentDirectory();
            Environment.SetEnvironmentVariable("HOME", currentDir);
            Environment.SetEnvironmentVariable("XDG_CONFIG_HOME", Path.Combine(currentDir, ".config"));
            Environment.SetEnvironmentVariable("XDG_DATA_HOME", Path.Combine(currentDir, ".local", "share"));
            Environment.SetEnvironmentVariable("XDG_CACHE_HOME", Path.Combine(currentDir, ".cache"));
            Environment.SetEnvironmentVariable("TMPDIR", Path.Combine(currentDir, "tmp"));
            
            if (File.Exists("cli-argsConfig.txt"))
            {
                var configArgs = File.ReadAllLines("cli-argsConfig.txt").SelectMany(a => a.Split(" ", 2)).ToArray();
                Console.WriteLine($"[Bootstrap] Loaded config args");
            }

            if (File.Exists("env-argsConfig.txt"))
            {
                foreach (var environmentVar in File.ReadAllLines("env-argsConfig.txt").Select(text => text.Split("=")).Where(envVar => envVar.Length == 2))
                {
                    Environment.SetEnvironmentVariable(environmentVar[0], environmentVar[1]);
                }
            }

            // 启动游戏
            try
            {
                MethodInfo entryPoint = gameAssembly.EntryPoint;
                
                if (entryPoint == null)
                {
                    Console.WriteLine("[Bootstrap] ERROR: No entry point!");
                    Environment.Exit(-1);
                    return;
                }

                Console.WriteLine($"[Bootstrap] Launching: {entryPoint.DeclaringType?.FullName}.{entryPoint.Name}");
                entryPoint.Invoke(null, new object[] { gameArgs });
                
                Console.WriteLine("[Bootstrap] Game exited normally");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Bootstrap] FATAL: {ex.Message}");
                Environment.Exit(-1);
            }
        }

        public static Assembly CurrentDomain_AssemblyResolve(object sender, ResolveEventArgs args)
        {
            try
            {
                // 检查已解析缓存
                if (_resolvedAssemblies.TryGetValue(args.Name, out var cached))
                    return cached;

                var requestedName = new AssemblyName(args.Name);
                string simpleName = requestedName.Name;
                Console.WriteLine($"Resolving assembly: {simpleName}");

                // 从缓存中获取程序集路径
                if (_assemblyPathCache.TryGetValue(simpleName, out string assemblyPath))
                {
                    var assembly = LoadAssemblyWithDependencies(assemblyPath);
                    _resolvedAssemblies[args.Name] = assembly;
                    return assembly;
                }

                Console.WriteLine($"Assembly not found in cache: {simpleName}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Assembly resolve failed for {args.Name}: {ex}");
            }

            return null;
        }
        private static Assembly LoadAssemblyWithDependencies(string path)
        {
            Console.WriteLine($"Loading assembly: {path}");
            var assembly = Assembly.LoadFrom(path);

            // 预加载所有依赖项
            foreach (var refAsm in assembly.GetReferencedAssemblies())
            {
                if (_resolvedAssemblies.ContainsKey(refAsm.FullName))
                    continue;

                try
                {
                    // 触发解析
                    Assembly.Load(refAsm);
                }
                catch
                {
                    Console.WriteLine($"Warning: Could not preload dependency {refAsm.Name}");
                }
            }

            return assembly;
        }



    

        /// <summary>
        /// 判断游戏目录中的DLL是否应该跳过
        /// 跳过与CoreCLR运行时冲突的DLL
        /// </summary>
        private static bool ShouldSkipGameDll(string fileName)
        {
            string lower = fileName.ToLowerInvariant();
            
            // 1. CoreCLR核心运行时DLL（由运行时目录提供）
            if (lower == "system.private.corelib" ||
                lower == "coreclr" ||
                lower == "hostfxr" ||
                lower == "hostpolicy" ||
                lower == "mscorlib" ||
                lower == "netstandard")
            {
                return true;
            }
            
            // 2. 原生Windows库（不是托管程序集）
            if (lower == "soft_oal" ||
                lower == "galaxy64" ||
                lower == "galaxy" ||
                lower == "openal32" ||
                lower == "d3dcompiler_47" ||
                lower == "libskiasharp")
            {
                return true;
            }
            
            // 3. Windows API兼容层（不是托管程序集）
            if (lower.StartsWith("api-ms-win-") || lower == "ucrtbase")
            {
                return true;
            }
            
            return false;
        }

        private static void AddAssembliesToCache(string path, bool recursive = false, bool forceOverride = false, bool applyFilter = true)
        {
            var searchOption = recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;
            try
            {
                foreach (var dllPath in Directory.GetFiles(path, "*.dll", searchOption))
                {
                    string fileNameWithoutExtension = Path.GetFileNameWithoutExtension(dllPath);

                    // 根据applyFilter决定是否过滤（游戏目录过滤，运行时目录不过滤）
                    if (applyFilter && ShouldSkipGameDll(fileNameWithoutExtension))
                    {
                        continue;
                    }
                    
                    // 如果forceOverride为true，强制覆盖已存在的条目（用于Bootstrap目录）
                    if (forceOverride)
                    {
                        _assemblyPathCache[fileNameWithoutExtension] = dllPath;
                    }
                    else
                    {
                    _assemblyPathCache.TryAdd(fileNameWithoutExtension, dllPath);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error building assembly cache from {path}: {ex}");
            }
        }

        private static Assembly ResolveManagedAssemblies(AssemblyLoadContext ctx, AssemblyName name)
    {
        if (name.Name is null)
            return null;

        try
        {
            // 🔧 动态查找 AssemblyManager 类型（tModLoader特有）
            // 只在 tModLoader 游戏中才存在，其他游戏中不会加载
            Type assemblyManagerType = null;
            
            // 尝试从已加载的程序集中查找 AssemblyManager
            foreach (var loadedAssembly in AppDomain.CurrentDomain.GetAssemblies())
            {
                try
                {
                    assemblyManagerType = loadedAssembly.GetType("Terraria.ModLoader.Core.AssemblyManager");
                    if (assemblyManagerType != null)
                        break;
                }
                catch { }
            }
            
            // 如果找不到 AssemblyManager（非 tModLoader 游戏），直接返回
            if (assemblyManagerType == null)
            {
                // 回退到简单的程序集缓存查找
                if (_assemblyPathCache.TryGetValue(name.Name, out string assemblyPath))
                {
                    if (File.Exists(assemblyPath))
                    {
                        return Assembly.LoadFrom(assemblyPath);
                    }
                }
                return null;
            }

            // 以下是 tModLoader 特有的解析逻辑
            FieldInfo loadedModContextsField = assemblyManagerType.GetField("loadedModContexts",
                BindingFlags.NonPublic | BindingFlags.Static);

            if (loadedModContextsField == null)
            {
                return null;
            }

            // 使用非泛型方式处理字典
            var dict = loadedModContextsField.GetValue(null) as IDictionary;
            if (dict == null)
            {
                return null;
            }

            // 获取 ModLoadContext 类型
            Type modLoadContextType = assemblyManagerType.GetNestedType("ModLoadContext",
                BindingFlags.NonPublic);

            if (modLoadContextType == null)
            {
                return null;
            }

            // 获取 assemblies 字段
            FieldInfo assembliesField = modLoadContextType.GetField("assemblies",
                BindingFlags.Public | BindingFlags.Instance);

            if (assembliesField == null)
            {
                return null;
            }

            // 遍历所有加载的 ModLoadContext
            foreach (var key in dict.Keys)
            {
                object modLoadContext = dict[key];
                var assemblies = assembliesField.GetValue(modLoadContext) as Dictionary<string, Assembly>;

                if (assemblies != null && assemblies.TryGetValue(name.Name, out Assembly asm))
                {
                    return asm;
                }
            }

            return null;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"ResolveManagedAssemblies error: {ex.Message}");
            
            // 异常时回退到简单的程序集缓存查找
            if (_assemblyPathCache.TryGetValue(name.Name, out string assemblyPath))
            {
                if (File.Exists(assemblyPath))
                {
                    try
                    {
                        return Assembly.LoadFrom(assemblyPath);
                    }
                    catch { }
                }
            }
            return null;
        }
    }

      
    }
