// RAL: tModLoader 1.3 (mono + FNA) 包装启动器
//
// 安装时 game_info.json 的 gameExePathRelative 指向本 exe，由它负责：
//   1. 切换工作目录到游戏目录（tModLoader 1.3 依赖相对路径找 Content/）
//   2. 注册程序集解析（同目录 dll 兜底，兼容 FNA 版本号不一致）
//   3. 调用 tModLoader.exe 的入口点，把参数原样传过去
//   4. 未处理异常落到 launcher-crash.log，方便在 RAL 日志里定位
//
// mono 不支持 DOTNET_STARTUP_HOOKS（那是 CoreCLR 的机制），所以 1.3 的兼容修复
// 只能放在这个包装进程里做；需要的 detour 用随包分发的 MonoMod 19（Android 可用）。
using System;
using System.IO;
using System.Reflection;
using System.Text;

namespace TML13Launcher
{
    internal static class Program
    {
        private static string s_gameDir = ".";

        /// <summary>tModLoader.exe 本身（其嵌入资源里带着 ReLogic/Newtonsoft/Cecil 等依赖）</summary>
        private static Assembly s_tmlAssembly;

        private static void Log(string message)
        {
            try
            {
                File.AppendAllText(Path.Combine(s_gameDir, "launcher.log"),
                    DateTime.Now.ToString("HH:mm:ss.fff") + " " + message + Environment.NewLine);
            }
            catch { /* 日志失败不影响启动 */ }
        }

        [STAThread]
        private static int Main(string[] args)
        {
            s_gameDir = AppDomain.CurrentDomain.BaseDirectory;
            if (string.IsNullOrEmpty(s_gameDir)) s_gameDir = Directory.GetCurrentDirectory();
            s_gameDir = s_gameDir.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);

            try { Directory.SetCurrentDirectory(s_gameDir); } catch (Exception e) { Log("chdir failed: " + e.Message); }

            Log("=== TML13Launcher start, dir=" + s_gameDir + ", args=" + string.Join(" ", args));

            AppDomain.CurrentDomain.AssemblyResolve += ResolveFromGameDir;
            AppDomain.CurrentDomain.UnhandledException += (s, e) =>
            {
                var ex = e.ExceptionObject as Exception;
                Log("UNHANDLED: " + (ex != null ? ex.ToString() : e.ExceptionObject.ToString()));
            };

            var tmlPath = Path.Combine(s_gameDir, "tModLoader.exe");
            if (!File.Exists(tmlPath))
            {
                Log("tModLoader.exe not found: " + tmlPath);
                Console.Error.WriteLine("tModLoader.exe not found: " + tmlPath);
                return 2;
            }

            Assembly asm;
            try
            {
                asm = Assembly.LoadFrom(tmlPath);
                s_tmlAssembly = asm;
            }
            catch (Exception e)
            {
                Log("LoadFrom(tModLoader.exe) failed: " + e);
                Console.Error.WriteLine(e.ToString());
                return 3;
            }

            try
            {
                ApplyAndroidFixes(asm);
            }
            catch (Exception e)
            {
                Log("ApplyAndroidFixes failed: " + e);
            }

            var entry = asm.EntryPoint;
            if (entry == null)
            {
                Log("tModLoader.exe has no entry point");
                return 4;
            }

            Log("entry point: " + entry.DeclaringType + "." + entry.Name +
                " params=" + entry.GetParameters().Length);

            try
            {
                object result = entry.GetParameters().Length == 0
                    ? entry.Invoke(null, null)
                    : entry.Invoke(null, new object[] { args });
                var code = result is int ? (int)result : 0;
                Log("tModLoader exited with " + code);
                return code;
            }
            catch (TargetInvocationException tie)
            {
                var inner = tie.InnerException ?? tie;
                Log("tModLoader threw: " + inner);
                Console.Error.WriteLine(inner.ToString());
                return 5;
            }
            catch (Exception e)
            {
                Log("launch failed: " + e);
                Console.Error.WriteLine(e.ToString());
                return 6;
            }
        }

        private static Assembly ResolveFromGameDir(object sender, ResolveEventArgs e)
        {
            try
            {
                var name = new AssemblyName(e.Name).Name;
                var candidate = Path.Combine(s_gameDir, name + ".dll");
                if (File.Exists(candidate))
                {
                    Log("AssemblyResolve: " + e.Name + " -> " + candidate);
                    return Assembly.LoadFrom(candidate);
                }

                // tModLoader 1.3 把 ReLogic / MonoMod / Steamworks.NET / Mono.Cecil 等
                // 直接嵌在 tModLoader.exe 的 manifest resource 里（运行时由它自己的
                // ResourceAssemblyLoader 解出来）。我们在 EntryPoint 之前就反射这些类型，
                // 那时它的加载器还没注册，所以这里按同样的规则从嵌入资源取。
                if (s_tmlAssembly != null)
                {
                    foreach (var res in s_tmlAssembly.GetManifestResourceNames())
                    {
                        if (!res.EndsWith(".dll", StringComparison.OrdinalIgnoreCase)) continue;
                        if (!string.Equals(Path.GetFileNameWithoutExtension(res), name, StringComparison.OrdinalIgnoreCase)) continue;
                        using (var stream = s_tmlAssembly.GetManifestResourceStream(res))
                        {
                            if (stream == null) continue;
                            var buffer = new byte[stream.Length];
                            int read = 0;
                            while (read < buffer.Length)
                            {
                                int n = stream.Read(buffer, read, buffer.Length - read);
                                if (n <= 0) break;
                                read += n;
                            }
                            Log("AssemblyResolve(embedded): " + e.Name + " -> " + res);
                            return Assembly.Load(buffer);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Log("AssemblyResolve failed for " + e.Name + ": " + ex.Message);
            }
            return null;
        }

        private static bool s_lateFixesApplied;

        /// <summary>
        /// 延后执行的补丁（第一次 Game.Tick 时调用）
        ///
        /// MdbManager 的静态构造会拉 Logging 的静态构造，而 Logging 在 tML 初始化早期
        /// 依赖尚不存在的路径（Path.Combine(null, ...) 直接抛异常）。所以不能在
        /// EntryPoint 之前碰它，等主循环跑起来再挂。
        /// </summary>
        internal static void ApplyLateFixes(Assembly tmlAsm)
        {
            if (s_lateFixesApplied) return;
            s_lateFixesApplied = true;
            try
            {
                // tModLoader 1.3 的 MonoModHooks 会拦截「未申请原生调转权限」的 detour。
                // 它自带的 RequestNativeAccess() 用 StackTrace.GetFrame(1) 认定申请方，
                // 反射调用时拿到的是 mscorlib，会授权错对象；这里直接把我们的程序集塞进
                // 它私有的 NativeDetouringGranted 集合。
                var monoModHooks = tmlAsm.GetType("Terraria.ModLoader.MonoModHooks");
                var grantedField = monoModHooks?.GetField("NativeDetouringGranted",
                    BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
                var granted = grantedField?.GetValue(null);
                if (granted != null)
                {
                    granted.GetType().GetMethod("Add")
                        ?.Invoke(granted, new object[] { Assembly.GetExecutingAssembly() });
                    Log("native detouring granted to launcher assembly");
                }

                DisableMdbSymbolization(tmlAsm);
            }
            catch (Exception e)
            {
                Log("late fixes failed: " + e);
            }
        }

        /// <summary>
        /// 关掉 tML 的 mdb 符号化
        ///
        /// tML 1.3 的 MdbManager.Symbolize 会用 Mono.Cecil.Mdb 把栈帧映射到 .mdb 行号；
        /// 在 mono/Android 上 Cecil 版本对不上时会抛 FieldAccessException，
        /// 异常在 Exception.ToString 里再次抛出，log4net 于是只打印
        /// "Exception rendering object type [System.DllNotFoundException]"，
        /// 真正的 DllNotFoundException 信息（缺哪个 so）就丢了。
        /// 打个 no-op 补丁让原始异常信息能正常显示。
        /// </summary>
        private static void DisableMdbSymbolization(Assembly tmlAsm)
        {
            var mdb = tmlAsm.GetType("Terraria.ModLoader.Core.MdbManager");
            var symbolize = mdb?.GetMethod("Symbolize", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            if (symbolize == null)
            {
                Log("MdbManager.Symbolize not found, skip");
                return;
            }
            new MonoMod.RuntimeDetour.Hook(symbolize, new Action<Action<System.Diagnostics.StackFrame[]>, System.Diagnostics.StackFrame[]>((orig, frames) => { }));
            Log("MdbManager.Symbolize hooked (no-op)");
        }

        /// <summary>
        /// Android/mono 环境下的兼容修复
        /// </summary>
        private static void ApplyAndroidFixes(Assembly tmlAsm)
        {
            // mono 下 Environment.SpecialFolder 的个人目录映射依赖 HOME；
            // GameLauncher 已设置 HOME，这里只做兜底。
            if (string.IsNullOrEmpty(Environment.GetEnvironmentVariable("HOME")))
                Environment.SetEnvironmentVariable("HOME", s_gameDir);

            // 每一项单独兜底：某一个补丁失败不能连累 GlCompat（图像兼容层必须先装上）
            Try("GlCompat.Install", () => GlCompat.Install(tmlAsm));
            Try("GLCallLocker", () => PatchGLCallLocker(tmlAsm));
            Try("InstallVerifier", () => PatchInstallVerifier(tmlAsm));
        }

        private static void Try(string name, Action action)
        {
            try
            {
                action();
            }
            catch (Exception e)
            {
                Log(name + " failed: " + e);
            }
        }

        /// <summary>
        /// 拦掉 tML 的 `Interface.MessageBoxShow`。
        ///
        /// 默认**不启用**：消息框内部是 System.Windows.Forms，Android 上没有 X11，
        /// 真弹出来就会 `DllNotFoundException: libX11.so.6` 然后静默退出。但用 MonoMod 去
        /// hook 这个方法会把 `Terraria.Main` 的静态构造提前拉起来（Main..cctor 里的
        /// `Path.Combine(path1, ...)` 此时 path1 还是 null）→ `TypeInitializationException`，
        /// 反而把后面的 GlCompat 一起带崩。
        ///
        /// 正确做法是从根上不触发它：tML 1.3 只在找不到素材目录时才弹框
        /// （`Main.PostSocialInitialize` 找 `../Terraria/Content`），所以要求安装目录就叫
        /// `Terraria`（见 SteamInstaller.deterministicInstallDir）。
        /// </summary>
        private static void PatchMessageBox(Assembly tmlAsm)
        {
            var ui = tmlAsm.GetType("Terraria.ModLoader.UI.Interface");
            var show = ui?.GetMethod("MessageBoxShow",
                BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic,
                null,
                new[] { typeof(string), typeof(string) },
                null);
            if (show == null)
            {
                Log("Interface.MessageBoxShow not found, skip patch");
                return;
            }

            new MonoMod.RuntimeDetour.Hook(show,
                new Action<Action<string, string>, string, string>((orig, text, caption) =>
                {
                    Log("MessageBoxShow suppressed: [" + caption + "] " + text);
                }));
            Log("Interface.MessageBoxShow hooked (suppressed, logged)");
        }

        /// <summary>
        /// tModLoader 1.3 启动时会用 InstallVerifier 校验「正版安装」：
        ///   - 有 lib/libsteam_api.so → 走 Steam 校验
        ///   - 否则走 GOG 校验：要求当前目录 / ../Terraria 下存在 Terraria.exe，
        ///     并且该 exe 的 MD5 必须等于 1.3.5.3 的 GOG 构建（ed920c5c...）
        /// Android 上我们拿不到那个特定 exe（用户用的可能是 1.4.5 的 GOG .sh，
        /// 或者 tModLoader 1.3 自带的 Content），校验不过会直接
        /// Interface.MessageBoxShow + Environment.Exit(1)，进程静默退出。
        ///
        /// 这里把 InstallCheck 替换成「视为 GOG 安装、直接通过」。
        /// </summary>
        private static void PatchInstallVerifier(Assembly tmlAsm)
        {
            var t = tmlAsm.GetType("Terraria.ModLoader.Engine.InstallVerifier");
            if (t == null)
            {
                Log("InstallVerifier type not found, skip patch");
                return;
            }
            var check = t.GetMethod("InstallCheck", BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
            if (check == null)
            {
                Log("InstallVerifier.InstallCheck not found, skip patch");
                return;
            }
            var isGoG = t.GetField("IsGoG", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
            var isValid = t.GetField("isValid", BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
            new MonoMod.RuntimeDetour.Hook(check, new Func<Func<bool>, bool>(orig =>
            {
                if (isGoG != null) isGoG.SetValue(null, true);
                if (isValid != null) isValid.SetValue(null, (bool?)true);
                Log("InstallVerifier.InstallCheck patched: treated as valid GOG install");
                return true;
            }));
            Log("InstallVerifier.InstallCheck hooked");
        }

        /// <summary>
        /// tModLoader 1.3 的 GLCallLocker.Init 会去 hook FNA 内部的
        /// Microsoft.Xna.Framework.Graphics.OpenGLDevice.ForceToMainThread —— 那个类型是
        /// 旧版（FNA 17.x，直接调 GL）才有的；新版 FNA 走 FNA3D，没有 OpenGLDevice，
        /// GetMethod 返回 null，随后 MonoMod 的 Hook 直接 NullReferenceException，
        /// 游戏在 EngineInit 阶段就崩掉。
        ///
        /// 新版 FNA3D 内部自己做序列化，不再需要这层 GL 调用转发，所以这里把 Init 换成
        /// 只记录主线程 ID 的空实现。
        /// </summary>
        private static void PatchGLCallLocker(Assembly tmlAsm)
        {
            var lockerType = tmlAsm.GetType("Terraria.ModLoader.Engine.GLCallLocker");
            if (lockerType == null)
            {
                Log("GLCallLocker type not found, skip patch");
                return;
            }
            var init = lockerType.GetMethod("Init", BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
            if (init == null)
            {
                Log("GLCallLocker.Init not found, skip patch");
                return;
            }
            var mainThreadField = lockerType.GetField("mainThreadId", BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
            new MonoMod.RuntimeDetour.Hook(init, new Action<Action>(orig =>
            {
                if (mainThreadField != null)
                {
                    mainThreadField.SetValue(null, System.Threading.Thread.CurrentThread.ManagedThreadId);
                }
                Log("GLCallLocker.Init patched: skipped FNA OpenGLDevice hook");
            }));
            Log("GLCallLocker.Init hooked");
        }
    }
}
