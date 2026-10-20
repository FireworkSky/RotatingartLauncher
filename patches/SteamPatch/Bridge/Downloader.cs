using System;
using System.Collections.Generic;
using DepotDownloader;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace RAL.SteamBridge;

/// <summary>
/// 工坊物品下载：拉起 DepotDownloader 子进程（匿名），
/// 下载完把内容搬到 <install>/steamapps/workshop/content/1281930/&lt;id&gt;/，
/// 并把匹配当前 tML 版本的那份 .tmod 镜像进 Mods/，最后维护 acf + 派发 DownloadItemResult_t。
/// </summary>
public static class Downloader {
    public const uint TML_APPID = 1281930;

    public sealed class Job {
        public ulong PubId;
        public bool Started, Done, Failed;
        public long Total = 1, OnDisk;
        public string Stage = "排队";
        public string TmpDir;
        public string ItemDir;
        public int ExitCode = -1;
        public string LastError = "";
    }

    static readonly Dictionary<ulong, Job> _jobs = new();
    static readonly object _lock = new();

    public static string InstallDir => Directory.GetCurrentDirectory();
    public static string WorkshopRoot {
        get {
            var custom = LaunchParam("-steamworkshopfolder");
            if (!string.IsNullOrEmpty(custom) && Directory.Exists(custom)) return custom;
            return Path.Combine(InstallDir, "steamapps", "workshop");
        }
    }
    public static string ContentDir => Path.Combine(WorkshopRoot, "content", TML_APPID.ToString());
    public static string ItemPath(ulong id) => Path.Combine(ContentDir, id.ToString());
    public static string ModsDir => Path.Combine(InstallDir, "Mods");
    public static string AcfPath => Path.Combine(WorkshopRoot, $"appworkshop_{TML_APPID}.acf");

    static string LaunchParam(string name) {
        try {
            var args = Environment.GetCommandLineArgs();
            for (int i = 0; i < args.Length - 1; i++)
                if (string.Equals(args[i], name, StringComparison.OrdinalIgnoreCase)) return args[i + 1];
        } catch { }
        return null;
    }

    public static bool IsInstalled(ulong id) {
        lock (_lock) if (_jobs.TryGetValue(id, out var j) && j.Done) return true;
        var p = ItemPath(id);
        return Directory.Exists(p) && Directory.EnumerateFileSystemEntries(p).Any();
    }

    public static Job Get(ulong id) { lock (_lock) return _jobs.TryGetValue(id, out var j) ? j : null; }

    /// <summary>tML 的 DownloadItem() 调这里；立即返回 true，后台拉子进程下载</summary>
    public static bool Start(ulong pubId, long knownFileSize) {
        lock (_lock) {
            if (_jobs.TryGetValue(pubId, out var old) && !old.Done && !old.Failed) return true;   // 已在下载
            var job = new Job {
                PubId = pubId,
                Total = knownFileSize > 0 ? knownFileSize : 1,
                TmpDir = Path.Combine(WorkshopRoot, ".ral_dl", pubId.ToString()),
                ItemDir = ItemPath(pubId),
                Stage = "准备中",
            };
            _jobs[pubId] = job;
            Task.Run(() => Run(job, knownFileSize));
            return true;
        }
    }

    static void Run(Job job, long knownFileSize) {
        try {
            Directory.CreateDirectory(ContentDir);
            if (Directory.Exists(job.TmpDir)) Directory.Delete(job.TmpDir, true);
            Directory.CreateDirectory(job.TmpDir);

            job.Started = true;
            job.Stage = "连接 Steam";
            // 进程内下载：app 里 Process.Start 会被 SELinux 拒绝
            //   （实测：Permission denied on /data/user/0/<pkg>/files/runtimes/dotnet/...）
            Console.WriteLine($"[dl] 进程内下载 pubfile={job.PubId} -> {job.TmpDir}");

            var task = System.Threading.Tasks.Task.Run(async () => {
                try {
                    EnsureDownloaderInit(job.TmpDir);
                    ContentDownloader.Config.InstallDirectory = job.TmpDir;
                    ContentDownloader.Config.MaxDownloads = 8;
                    ContentDownloader.Config.VerifyAll = false;
                    ContentDownloader.Config.CellID = -1;
                    ContentDownloader.Config.DownloadManifestOnly = false;
                    Console.WriteLine("[dl] 匿名登入（DepotDownloader 自带会话）...");
                    if (!ContentDownloader.InitializeSteam3(null, null))
                        throw new Exception("匿名登入失败（InitializeSteam3 返回 false）");
                    Console.WriteLine("[dl] 登入 OK，开始 DownloadPubfileAsync...");
                    try {
                        await ContentDownloader.DownloadPubfileAsync(TML_APPID, job.PubId).ConfigureAwait(false);
                        Console.WriteLine("[dl] DownloadPubfileAsync 返回");
                    } finally {
                        try { ContentDownloader.ShutdownSteam3(); } catch { }
                    }
                } catch (Exception ex) {
                    Console.WriteLine("[dl][task] 异常: " + ex.GetType().Name + ": " + ex.Message);
                    Console.WriteLine("[dl][task] 栈: " + ex.StackTrace);
                    if (ex.InnerException != null) Console.WriteLine("[dl][task] 内层: " + ex.InnerException);
                    throw;
                }
            });

            while (!task.IsCompleted) {
                job.OnDisk = DirSize(job.TmpDir);
                if (knownFileSize <= 0 && job.OnDisk > job.Total) job.Total = job.OnDisk + 1;
                Thread.Sleep(300);
            }
            task.GetAwaiter().GetResult();          // 下载异常在这里抛出
            job.OnDisk = DirSize(job.TmpDir);
            job.Total = knownFileSize > 0 ? knownFileSize : Math.Max(job.OnDisk, 1);
            job.ExitCode = 0;

            job.Stage = "安装中";
            Install(job);
            job.Done = true; job.Stage = "已安装";
            Console.WriteLine($"[dl] 完成: {job.ItemDir}");
            Dispatch.QueueCallback("DownloadItemResult_t", o => {
                StructKit.SetAuto(o, "m_nPublishedFileId", job.PubId);
                StructKit.SetAuto(o, "m_unAppID", TML_APPID);
                StructKit.SetAuto(o, "m_eResult", 1);
            });
        } catch (Exception e) {
            job.Failed = true; job.LastError = e.Message;
            job.Stage = "下载失败";
            Console.WriteLine($"[dl] 失败: {e.GetType().Name}: {e.Message}");
            Console.WriteLine("[dl] 栈: " + e.StackTrace);
            if (e.InnerException != null) Console.WriteLine("[dl] 内层: " + e.InnerException.GetType().Name + ": " + e.InnerException.Message + "\n" + e.InnerException.StackTrace);
            Dispatch.QueueCallback("DownloadItemResult_t", o => {
                StructKit.SetAuto(o, "m_nPublishedFileId", job.PubId);
                StructKit.SetAuto(o, "m_unAppID", TML_APPID);
                StructKit.SetAuto(o, "m_eResult", 2);
            });
        }
    }

    static void OnLine(Job job, string line) {
        if (line.Contains("Logging anonymously")) job.Stage = "匿名登入";
        else if (line.Contains("Downloading depot")) job.Stage = "下载中";
        else if (line.Contains("Total downloaded")) job.Stage = "下载完成";
        Console.WriteLine("[dl] " + line);
    }

    static bool _ddInited;

    /// <summary>
    /// DepotDownloader 的 Program.Main 里有几处一次性初始化，进程内调用必须自己补：
    ///   · AccountSettingsStore.LoadFromFile —— 不调用会在 CDNClientPool 里 NRE；且**只能调用一次**（重复调用抛 "Config already loaded"）
    ///   · Ansi.Init / DebugLog
    /// </summary>
    static void EnsureDownloaderInit(string workDir) {
        if (_ddInited) return;
        lock (typeof(Downloader)) {
            if (_ddInited) return;
            Console.WriteLine("[dl] 配置 ContentDownloader（首次）...");
            try { DepotDownloader.Ansi.Init(); } catch (Exception e) { Console.WriteLine("[dl] Ansi.Init 忽略: " + e.Message); }
            try { SteamKit2.DebugLog.Enabled = false; } catch { }
            try { DepotDownloader.AccountSettingsStore.LoadFromFile(Path.Combine(workDir, "account.config")); }
            catch (Exception e) { Console.WriteLine("[dl] LoadFromFile 忽略: " + e.Message); }
            _ddInited = true;
        }
    }

    /// <summary>跨设备搬运：TMPDIR 常在应用内部存储，工坊目录在外部存储，Directory.Move 会抛 Cross-device link</summary>
    static void MoveOrCopy(string src, string dest) {
        if (Directory.Exists(src)) {
            try { Directory.Move(src, dest); return; }
            catch (Exception e) { Console.WriteLine($"[dl] 目录移动失败({e.GetType().Name})，改为复制: {Path.GetFileName(src)}"); }
            CopyDir(src, dest);
            try { Directory.Delete(src, true); } catch { }
        } else {
            try { File.Move(src, dest, true); return; }
            catch (Exception e) { Console.WriteLine($"[dl] 文件移动失败({e.GetType().Name})，改为复制: {Path.GetFileName(src)}"); }
            File.Copy(src, dest, true);
            try { File.Delete(src); } catch { }
        }
    }

    static void CopyDir(string src, string dest) {
        Directory.CreateDirectory(dest);
        foreach (var d in Directory.GetDirectories(src)) CopyDir(d, Path.Combine(dest, Path.GetFileName(d)));
        foreach (var f in Directory.GetFiles(src)) File.Copy(f, Path.Combine(dest, Path.GetFileName(f)), true);
    }

    /// <summary>把临时目录的内容搬进工坊目录、写 acf、镜像 .tmod 到 Mods/</summary>
    static void Install(Job job) {
        if (Directory.Exists(job.ItemDir)) Directory.Delete(job.ItemDir, true);
        Directory.CreateDirectory(job.ItemDir);
        foreach (var f in Directory.EnumerateFileSystemEntries(job.TmpDir)) {
            var dest = Path.Combine(job.ItemDir, Path.GetFileName(f));
            MoveOrCopy(f, dest);
        }
        try {
            var dd = Path.Combine(job.ItemDir, ".DepotDownloader");
            if (Directory.Exists(dd)) Directory.Delete(dd, true);
        } catch (Exception e) { Console.WriteLine("[dl] 清理 .DepotDownloader 忽略: " + e.Message); }
        WriteAcf(job);
        MirrorToMods(job);
    }

    /// <summary>挑出匹配当前 tML 版本的那份 .tmod 复制进 Mods/（tML 也认工坊目录，这是双保险）</summary>
    static void MirrorToMods(Job job) {
        try {
            var tmods = Directory.GetFiles(job.ItemDir, "*.tmod", SearchOption.AllDirectories);
            if (tmods.Length == 0) { Console.WriteLine("[dl] 物品里没有 .tmod（可能是资源包）"); return; }
            var pick = PickForCurrentTml(tmods);
            Directory.CreateDirectory(ModsDir);
            var dest = Path.Combine(ModsDir, Path.GetFileName(pick));
            File.Copy(pick, dest, true);
            Console.WriteLine($"[dl] 已镜像到 Mods/: {Path.GetFileName(dest)}  (源: {pick.Substring(job.ItemDir.Length).TrimStart('/')})");
        } catch (Exception e) { Console.WriteLine("[dl] 镜像失败: " + e.Message); }
    }

    /// <summary>版本目录形如 2025.4 / 2026.8，取 <= 当前 tML 版本的最新那份</summary>
    static string PickForCurrentTml(string[] tmods) {
        var cur = CurrentTmlVersion();
        var withVer = tmods.Select(f => {
            var m = System.Text.RegularExpressions.Regex.Match(f, @"[/|\\](\d{4}[.]\d{1,2})[/|\\]");
            return (file: f, ver: m.Success ? m.Groups[1].Value : null);
        }).ToList();
        var candidates = withVer.Where(x => x.ver != null).ToList();
        if (candidates.Count == 0) return withVer[0].file;                 // 老格式：.tmod 直接放物品根目录
        if (cur != null) {
            var ok = candidates.Where(x => CompareVer(x.ver, cur) <= 0).OrderByDescending(x => CompareVer(x.ver, cur)).ToList();
            if (ok.Count > 0) return ok[0].file;
        }
        return candidates.OrderByDescending(x => x.ver).First().file;
    }

    static int CompareVer(string a, string b) {
        var pa = a.Split('.'); var pb = b.Split('.');
        for (int i = 0; i < Math.Max(pa.Length, pb.Length); i++) {
            int va = i < pa.Length && int.TryParse(pa[i], out var x) ? x : 0;
            int vb = i < pb.Length && int.TryParse(pb[i], out var y) ? y : 0;
            if (va != vb) return va.CompareTo(vb);
        }
        return 0;
    }

    /// <summary>从 tML 自己的 BuildInfo.tMLVersion 拿当前版本（同一个进程里，反射即可）</summary>
    static string CurrentTmlVersion() {
        try {
            var t = Type.GetType("Terraria.ModLoader.BuildInfo, tModLoader", false);
            var v = t?.GetField("tMLVersion", System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static)?.GetValue(null)
                 ?? t?.GetProperty("tMLVersion", System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static)?.GetValue(null);
            if (v is Version ver) return $"{ver.Major}.{ver.Minor}";
        } catch { }
        return null;
    }

    static void WriteAcf(Job job) {
        try {
            Directory.CreateDirectory(WorkshopRoot);
            var installed = new List<ulong>();
            if (Directory.Exists(ContentDir))
                foreach (var d in Directory.EnumerateDirectories(ContentDir))
                    if (ulong.TryParse(Path.GetFileName(d), out var id)) installed.Add(id);
            if (!installed.Contains(job.PubId)) installed.Add(job.PubId);

            var sb = new System.Text.StringBuilder();
            sb.AppendLine("\"AppWorkshop\"");
            sb.AppendLine("{");
            sb.AppendLine($"\t\"appid\"\t\t\"{TML_APPID}\"");
            sb.AppendLine($"\t\"SizeOnDisk\"\t\t\"{DirSize(ContentDir)}\"");
            sb.AppendLine($"\t\"NeedsUpdate\"\t\t\"0\"");
            sb.AppendLine($"\t\"NeedsDownload\"\t\t\"0\"");
            sb.AppendLine($"\t\"TimeLastUpdated\"\t\t\"{(int)DateTimeOffset.UtcNow.ToUnixTimeSeconds()}\"");
            sb.AppendLine("\t\"WorkshopItemsInstalled\"");
            sb.AppendLine("\t{");
            foreach (var id in installed) {
                sb.AppendLine($"\t\t\"{id}\"");
                sb.AppendLine("\t\t{");
                sb.AppendLine($"\t\t\t\"size\"\t\t\"{DirSize(ItemPath(id))}\"");
                sb.AppendLine($"\t\t\t\"timeupdated\"\t\t\"{(int)DateTimeOffset.UtcNow.ToUnixTimeSeconds()}\"");
                sb.AppendLine($"\t\t\t\"timetouched\"\t\t\"{(int)DateTimeOffset.UtcNow.ToUnixTimeSeconds()}\"");
                sb.AppendLine("\t\t\t\"manifest\"\t\t\"0\"");
                sb.AppendLine("\t\t}");
            }
            sb.AppendLine("\t}");
            sb.AppendLine("}");
            File.WriteAllText(AcfPath, sb.ToString());
            Console.WriteLine($"[dl] 已写 {Path.GetFileName(AcfPath)}（{installed.Count} 个已安装）");
        } catch (Exception e) { Console.WriteLine("[dl] 写 acf 失败: " + e.Message); }
    }

    static void CleanTmp(Job job) {
        try { if (Directory.Exists(job.TmpDir)) Directory.Delete(job.TmpDir, true); } catch { }
    }

    public static long DirSize(string dir) {
        try {
            if (!Directory.Exists(dir)) return 0;
            return Directory.EnumerateFiles(dir, "*", SearchOption.AllDirectories).Sum(f => { try { return new FileInfo(f).Length; } catch { return 0L; } });
        } catch { return 0; }
    }
}
