using System;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.Loader;
using System.Runtime.InteropServices;
using Mono.Cecil;
using RAL.SteamBridge;
using ShimTool;

class Program {
    static int Main(string[] argv) {
        if (argv.Length > 1 && argv[1] == "probe") {
            var asmv = AssemblyLoadContext.Default.LoadFromStream(new MemoryStream(File.ReadAllBytes(argv[0])));
            return ProbeStructs(asmv);
        }
        var src = argv.Length > 0 ? argv[0] : "/data/data/com.termux/files/home/steam-poc/Steamworks.NET.linux.dll";
        var outPath = "/data/data/com.termux/files/home/steam-poc/Steamworks.NET.patched.dll";

        Console.WriteLine("=== 1) Cecil 改写 ===");
        var asm = AssemblyDefinition.ReadAssembly(src);
        int n = Redirector.PatchAll(asm.MainModule);
        asm.Write(outPath);
        Console.WriteLine($"  已改写 {n} 个 P/Invoke -> RAL.SteamBridge.NativeBridge.Invoke");
        Console.WriteLine($"  输出 {outPath} ({new FileInfo(outPath).Length} 字节)");

        Console.WriteLine("=== 2) 加载改写后的 Steamworks.NET ===");
        var bytes = File.ReadAllBytes(outPath);
        var loaded = AssemblyLoadContext.Default.LoadFromStream(new MemoryStream(bytes));
        Console.WriteLine($"  已加载: {loaded.FullName}");

        var tApi = loaded.GetType("Steamworks.SteamAPI");
        var tGs  = loaded.GetType("Steamworks.SteamGameServer");
        var tUgc = loaded.GetType("Steamworks.SteamGameServerUGC");
        var tUtils = loaded.GetType("Steamworks.SteamUtils");

        Console.WriteLine("=== 3) SteamAPI.Init() ===");
        try {
            var r = tApi.GetMethod("Init").Invoke(null, null);
            Console.WriteLine($"  SteamAPI.Init() = {r}");
        } catch (Exception e) { Console.WriteLine("  抛: " + Unwrap(e).Message); }

        Console.WriteLine("=== 4) SteamAPI.InitEx(out msg) ===");
        try {
            var args = new object[] { null };
            var r = tApi.GetMethod("InitEx").Invoke(null, args);
            Console.WriteLine($"  InitEx = {r}, msg = '{args[0]}'");
        } catch (Exception e) { Console.WriteLine("  抛: " + Unwrap(e).Message); }

        Console.WriteLine("=== 5) SteamUtils.GetAppID() ===");
        try { Console.WriteLine("  GetAppID() = " + tUtils.GetMethod("GetAppID").Invoke(null, null)); }
        catch (Exception e) { Console.WriteLine("  抛: " + Unwrap(e).Message); }

        Console.WriteLine("=== 6) SteamGameServer.LogOnAnonymous() ===");
        try { tGs.GetMethod("LogOnAnonymous").Invoke(null, null); Console.WriteLine("  调用成功"); }
        catch (Exception e) { Console.WriteLine("  抛: " + Unwrap(e).Message); }

        Console.WriteLine("=== 7) SteamGameServerUGC.DownloadItem(pubid, true) ===");
        try {
            var pfidType = loaded.GetType("Steamworks.PublishedFileId_t");
            var pfid = Activator.CreateInstance(pfidType, new object[] { 2824688266UL });
            var r = tUgc.GetMethod("DownloadItem", new[] { pfidType, typeof(bool) }).Invoke(null, new[] { pfid, (object)true });
            Console.WriteLine("  DownloadItem = " + r);
        } catch (Exception e) { Console.WriteLine("  抛: " + Unwrap(e).Message); }

        Console.WriteLine("=== 8) SteamAPI.RunCallbacks() ===");
        try { tApi.GetMethod("RunCallbacks").Invoke(null, null); Console.WriteLine("  调用成功"); }
        catch (Exception e) { Console.WriteLine("  抛: " + Unwrap(e).Message); }

        Console.WriteLine($"=== 桥统计: 调用 {NativeBridge.CallCount} 次，见过的原生函数 {NativeBridge.Seen.Count} 个 ===");
        foreach (var s in NativeBridge.Seen.Take(12)) Console.WriteLine("   " + s);
        return 0;
    }


    static int ProbeStructs(Assembly loaded) {
        Console.WriteLine("=== 结构体可marshal性探测 ===");
        foreach (var n in new[] { "Steamworks.SteamUGCQueryCompleted_t", "Steamworks.SteamAPICallCompleted_t", "Steamworks.CallbackMsg_t", "Steamworks.SteamUGCDetails_t" }) {
            var t = loaded.GetType(n);
            if (t == null) { Console.WriteLine($"  {n}: 没找到"); continue; }
            string size = "?";
            try { size = Marshal.SizeOf(t).ToString(); } catch (Exception e) { size = "SizeOf 抛 " + e.GetType().Name + ": " + e.Message; }
            string s2p = "未试";
            try {
                var inst = Activator.CreateInstance(t);
                var buf = Marshal.AllocHGlobal(4096);
                Marshal.StructureToPtr(inst, buf, false);
                s2p = "OK";
                Marshal.FreeHGlobal(buf);
            } catch (Exception e) { s2p = "抛 " + e.GetType().Name + ": " + e.Message; }
            Console.WriteLine($"  {t.Name,-28} SizeOf={size,-10} StructureToPtr={s2p}");
            if (n.EndsWith("SteamUGCDetails_t") || n.EndsWith("SteamUGCQueryCompleted_t")) {
                var ctors = string.Join(" | ", t.GetConstructors().Select(c => "(" + string.Join(",", c.GetParameters().Select(pp => pp.ParameterType.Name)) + ")"));
                Console.WriteLine($"      构造函数: {(ctors.Length == 0 ? "<无>" : ctors)}");
                var inst2 = Activator.CreateInstance(t);
                foreach (var f in t.GetFields(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic)) {
                    var v = f.GetValue(inst2);
                    if (v is Array a2) Console.WriteLine($"      字段 {f.Name}: byte[{a2.Length}]  (null={v == null})");
                    else if (v == null) Console.WriteLine($"      字段 {f.Name} ({f.FieldType.Name}): null");
                }
            }
        }
        return 0;
    }

    static Exception Unwrap(Exception e) => e is TargetInvocationException t && t.InnerException != null ? t.InnerException : e;
}
