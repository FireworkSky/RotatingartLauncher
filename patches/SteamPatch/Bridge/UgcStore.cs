using System;
using System.Collections.Generic;
using System.Collections.Concurrent;
using System.IO;
using System.Runtime.InteropServices;

namespace RAL.SteamBridge;

public sealed class WsItem {
    public ulong PubId;
    public string Title = "";
    public string Description = "";
    public string Metadata = "";      // tML 靠这个解析 name/version/modloaderversion
    public string PreviewUrl = "";
    public string FileName = "";
    public uint TimeUpdated;
    public uint TimeCreated;
    public uint VotesUp, VotesDown;
    public uint Subs, Favs, Views;
    public uint NumChildren;
    public ulong HContentFile;
    public long FileSize;
    public bool Banned;
    public ulong Owner;
    public readonly List<string> Tags = new();
    public readonly List<(string k, string v)> KvTags = new();
    public readonly List<ulong> Children = new();
}

/// <summary>工坊数据 + 查询句柄 + 安装状态。数据源下一步换成 SteamKit2。</summary>
public static class UgcStore {
    public const uint TML_APPID = 1281930;

    static readonly ConcurrentDictionary<ulong, WsItem> Items = new();
    static readonly ConcurrentDictionary<ulong, List<WsItem>> QueryResults = new();
    static readonly ConcurrentDictionary<ulong, string> QuerySearch = new();
    static readonly ConcurrentDictionary<ulong, uint> QueryPage = new();
    static readonly ConcurrentDictionary<ulong, string> QueryCursor = new();
    static readonly ConcurrentDictionary<ulong, int> Downloaded = new();   // pubId -> 0..100
    public static readonly ConcurrentDictionary<ulong, long> KnownSizes = new();   // 查询时记下 file_size，给进度条当分母
    static ulong _nextQuery = 0x1000;
    // 查询种类：browse（按条件翻页/游标） / details（按 publish ID 精确查）
    static readonly ConcurrentDictionary<ulong, bool> QueryIsDetails = new();      // true = 按 ID 查
    static readonly ConcurrentDictionary<ulong, List<ulong>> QueryIds = new();

    public static void Seed() {
        Items.Clear();
        Add(new WsItem {
            PubId = 2824688266, Title = "Calamity Mod Music", Owner = 76561198000000002UL,
            Description = "Calamity 的音乐包", TimeUpdated = 1760000000u,
            VotesUp = 12345, VotesDown = 67, Subs = 987654, Favs = 45678, FileName = "CalamityModMusic.tmod",
            Metadata = "name=CalamityModMusic\nauthor=Fabsol\nmodside=Both\nmodloaderversion=tModLoader v2025.4\nversion=2.0.2.1\nmodreferences=CalamityMod\nversionsummary=2025.4:2.0.2.1"
        });
        Add(new WsItem {
            PubId = 2824688800, Title = "Calamity Mod", Owner = 76561198000000003UL,
            Description = "泰拉瑞亚最大的内容 mod", TimeUpdated = 1760500000u,
            VotesUp = 54321, VotesDown = 210, Subs = 3210000, Favs = 210000, FileName = "CalamityMod.tmod",
            Metadata = "name=CalamityMod\nauthor=Fabsol\nmodside=Both\nmodloaderversion=tModLoader v2025.4\nversion=2.1.2.4\nmodreferences=\nversionsummary=2025.4:2.1.2.4"
        });
        Console.WriteLine($"[ugc] 假数据仓库就绪：{Items.Count} 个物品");
    }
    static void Add(WsItem it) {
        it.KvTags.Add(("name", it.Metadata.Split('\n')[0].Split('=')[1]));
        it.KvTags.Add(("modside", "Both"));
        Items[it.PubId] = it;
    }

    public static object[] Handle(string name, string retType, object[] args) {
        switch (name) {
            case "ISteamUGC_CreateQueryAllUGCRequestPage":
            case "ISteamUGC_CreateQueryAllUGCRequestCursor": {
                var h = ++_nextQuery;
                QueryResults[h] = new List<WsItem>();         // 真数据由 SK2 异步填
                if (name.EndsWith("Page")) QueryPage[h] = StructKit.AsUInt(args[5]);
                else { QueryPage[h] = 1u; QueryCursor[h] = args[5] as string ?? "*"; }
                Console.WriteLine($"[ugc] {name} -> handle=0x{h:X} ({QueryResults[h].Count} 条)");
                return new object[] { h };
            }
            case "ISteamUGC_CreateQueryUGCDetailsRequest": {
                // tML 的 DirectQueryInstalledMDItems / 按 ID 查询走这里（之前没实现 -> 落到 handle 0，索引全错位）
                var h = ++_nextQuery;
                var arr = args[1] as Array;
                var ids = new List<ulong>();
                if (arr != null) for (int i = 0; i < arr.Length; i++) {
                    var v = arr.GetValue(i);
                    if (v != null) ids.Add(StructKit.AsULong(v));
                }
                QueryIsDetails[h] = true;
                QueryIds[h] = ids;
                QueryResults[h] = new List<WsItem>();
                QueryPage[h] = 1;
                Console.WriteLine($"[ugc] CreateQueryUGCDetailsRequest({ids.Count} 个 id) -> handle=0x{h:X}");
                return new object[] { h };
            }
            case "ISteamUGC_SetSearchText": {
                var h = StructKit.AsULong(args[1]); var s = StructKit.AsString(args[2]);
                QuerySearch[h] = s;
                if (QueryResults.TryGetValue(h, out var list))
                    QueryResults[h] = list.FindAll(i => s.Length == 0 || i.Title.Contains(s, StringComparison.OrdinalIgnoreCase));
                Console.WriteLine($"[ugc] SetSearchText('{s}') -> {QueryResults[h].Count} 条");
                return new object[] { true };
            }
            case "ISteamUGC_SetReturnMetadata":
            case "ISteamUGC_SetReturnKeyValueTags":
            case "ISteamUGC_SetReturnChildren":
            case "ISteamUGC_SetReturnPlaytimeStats":
            case "ISteamUGC_SetLanguage":
            case "ISteamUGC_SetRankedByTrendDays":
            case "ISteamUGC_SetAllowCachedResponse":
                return new object[] { true };

            case "ISteamUGC_SendQueryUGCRequest": {
                var h = StructKit.AsULong(args[1]);
                var call = Dispatch.NewHandle();     // 先返回句柄，结果稍后由后台 SK2 入队
                if (!QueryResults.ContainsKey(h)) {
                    // 未知句柄：交付一个失败结果，别让 tML 一直等
                    Console.WriteLine($"[ugc] SendQueryUGCRequest(未知 handle=0x{h:X}) -> 直接交付失败，避免挂死");
                    Dispatch.QueueWith(call, "SteamUGCQueryCompleted_t", o => {
                        StructKit.SetAuto(o, "m_handle", h);
                        StructKit.SetAuto(o, "m_eResult", 2);   // k_EResultFail
                        StructKit.SetAuto(o, "m_unNumResultsReturned", 0u);
                        StructKit.SetAuto(o, "m_unTotalMatchingResults", 0u);
                    });
                    return new object[] { call };
                }
                if (QueryIsDetails.TryGetValue(h, out var isDet) && isDet) {
                    var ids = QueryIds.TryGetValue(h, out var l2) ? l2 : new List<ulong>();
                    Console.WriteLine($"[ugc] SendQueryUGCRequest(按 ID, {ids.Count} 个) -> call=0x{call:X}");
                    System.Threading.Tasks.Task.Run(() => {
                        List<WsItem> items;
                        try { items = Sk2.Details(ids); }
                        catch (Exception ex) {
                            Console.WriteLine($"[ugc] 按 ID 查询失败，交付失败结果: {ex.GetType().Name}: {ex.Message}");
                            Dispatch.QueueWith(call, "SteamUGCQueryCompleted_t", o => {
                                StructKit.SetAuto(o, "m_handle", h);
                                StructKit.SetAuto(o, "m_eResult", 2);
                                StructKit.SetAuto(o, "m_unNumResultsReturned", 0u);
                                StructKit.SetAuto(o, "m_unTotalMatchingResults", 0u);
                                StructKit.EnsureArrays(o);
                            });
                            return;
                        }
                        QueryResults[h] = items;
                        foreach (var it in items) if (it.FileSize > 0) KnownSizes[it.PubId] = it.FileSize;
                        Dispatch.QueueWith(call, "SteamUGCQueryCompleted_t", o => {
                            StructKit.SetAuto(o, "m_handle", h);
                            StructKit.SetAuto(o, "m_eResult", 1);
                            StructKit.SetAuto(o, "m_unNumResultsReturned", (uint)items.Count);
                            StructKit.SetAuto(o, "m_unTotalMatchingResults", (uint)items.Count);
                            StructKit.EnsureArrays(o);
                        });
                    });
                    return new object[] { call };
                }
                var page = QueryPage.TryGetValue(h, out var pg) ? pg : 1u;
                var search = QuerySearch.TryGetValue(h, out var st) ? st : "";
                Console.WriteLine($"[ugc] SendQueryUGCRequest(handle=0x{h:X}, page={page}, search='{search}') -> call=0x{call:X}（后台查 Steam）");
                var cursor = QueryCursor.TryGetValue(h, out var cur0) ? cur0 : "*";
                System.Threading.Tasks.Task.Run(() => {
                    List<WsItem> items; uint total; string next;
                    try {
                        var qr = Sk2.Query(page, search, 50, 0, cursor);
                        items = qr.Items; total = qr.Total; next = qr.NextCursor;
                    } catch (Exception ex) {
                        // 关键：查询失败也必须交付回调，否则 tML 会一直等（"No response from steam workshop query"）
                        Console.WriteLine($"[ugc] 查询失败，交付失败结果: {ex.GetType().Name}: {ex.Message}");
                        Dispatch.QueueWith(call, "SteamUGCQueryCompleted_t", o => {
                            StructKit.SetAuto(o, "m_handle", h);
                            StructKit.SetAuto(o, "m_eResult", 2);   // k_EResultFail
                            StructKit.SetAuto(o, "m_unNumResultsReturned", 0u);
                            StructKit.SetAuto(o, "m_unTotalMatchingResults", 0u);
                            StructKit.EnsureArrays(o);
                        });
                        return;
                    }
                    if (string.IsNullOrEmpty(next) || next == cursor) next = "*";   // 防 tML 死循环翻页
                    QueryResults[h] = items;
                    QueryCursor[h] = next;
                    foreach (var it in items) if (it.FileSize > 0) KnownSizes[it.PubId] = it.FileSize;
                    Dispatch.QueueWith(call, "SteamUGCQueryCompleted_t", o => {
                        StructKit.SetAuto(o, "m_handle", h);
                        StructKit.SetAuto(o, "m_eResult", 1);
                        StructKit.SetAuto(o, "m_unNumResultsReturned", (uint)items.Count);
                        StructKit.SetAuto(o, "m_unTotalMatchingResults", total);
                        StructKit.SetAuto(o, "m_bCachedData", false);
                        StructKit.EnsureArrays(o);
                        var cur = (byte[])StructKit.Get(o, "m_rgchNextCursor_");
                        var nb = System.Text.Encoding.UTF8.GetBytes(next);
                        Array.Copy(nb, cur, Math.Min(nb.Length, cur.Length - 1));
                    });
                });
                return new object[] { call };
            }
            case "ISteamUGC_ReleaseQueryUGCRequest": {
                QueryResults.TryRemove(StructKit.AsULong(args[1]), out _); return new object[] { true };
            }

            case "ISteamUGC_GetQueryUGCResult": {
                var h = StructKit.AsULong(args[1]); var idx = StructKit.AsUInt(args[2]);
                var list = QueryResults.TryGetValue(h, out var l) ? l : new List<WsItem>();
                if (idx >= list.Count) return new object[] { false };
                var it = list[(int)idx];
                var d = args[3];                                  // byref 装箱副本，写完回写
                StructKit.SetAuto(d, "m_nPublishedFileId", it.PubId);
                StructKit.SetAuto(d, "m_eResult", 1);
                StructKit.SetAuto(d, "m_eFileType", 0);               // k_EWorkshopFileTypeCommunity
                StructKit.SetAuto(d, "m_nConsumerAppID", TML_APPID);
                StructKit.SetAuto(d, "m_nCreatorAppID", TML_APPID);
                StructKit.SetAuto(d, "m_ulSteamIDOwner", it.Owner);
                StructKit.SetAuto(d, "m_rtimeUpdated", it.TimeUpdated);
                StructKit.SetAuto(d, "m_unVotesUp", it.VotesUp);
                StructKit.SetAuto(d, "m_unVotesDown", it.VotesDown);
                StructKit.SetAuto(d, "m_bBanned", it.Banned);
                StructKit.SetAuto(d, "m_unNumChildren", (uint)it.Children.Count);
                StructKit.EnsureArrays(d);
                StructKit.SetUtf8(d, "m_rgchTitle_", it.Title);
                StructKit.SetUtf8(d, "m_rgchDescription_", it.Description);
                StructKit.SetUtf8(d, "m_rgchTags_", "New Content,Both");
                StructKit.SetUtf8(d, "m_pchFileName_", it.FileName);
                StructKit.SetUtf8(d, "m_rgchURL_", $"https://steamcommunity.com/sharedfiles/filedetails/?id={it.PubId}");
                return new object[] { true, null, null, null, d };   // res[4] -> 回写第 3 个参数(ref details)
            }
            case "ISteamUGC_GetQueryUGCMetadata": {
                var h = StructKit.AsULong(args[1]); var idx = StructKit.AsUInt(args[2]);
                var list = QueryResults.TryGetValue(h, out var l) ? l : new List<WsItem>();
                if (idx >= list.Count) return new object[] { false };
                StructKit.WriteUtf8ToPtr((IntPtr)args[3], StructKit.AsUInt(args[4]), list[(int)idx].Metadata);
                return new object[] { true };
            }
            case "ISteamUGC_GetQueryUGCPreviewURL": {
                var h = StructKit.AsULong(args[1]); var idx = StructKit.AsUInt(args[2]);
                var list = QueryResults.TryGetValue(h, out var l) ? l : new List<WsItem>();
                if (idx >= list.Count) return new object[] { false };
                StructKit.WriteUtf8ToPtr((IntPtr)args[3], StructKit.AsUInt(args[4]), list[(int)idx].PreviewUrl);
                return new object[] { true };
            }
            case "ISteamUGC_GetQueryUGCNumKeyValueTags": {
                var h = StructKit.AsULong(args[1]); var idx = StructKit.AsUInt(args[2]);
                var list = QueryResults.TryGetValue(h, out var l) ? l : new List<WsItem>();
                return new object[] { idx < list.Count ? (uint)list[(int)idx].KvTags.Count : 0u };
            }
            case "ISteamUGC_GetQueryUGCKeyValueTag": {
                var h = StructKit.AsULong(args[1]); var idx = StructKit.AsUInt(args[2]); var kv = StructKit.AsUInt(args[3]);
                var list = QueryResults.TryGetValue(h, out var l) ? l : new List<WsItem>();
                if (idx >= list.Count || kv >= list[(int)idx].KvTags.Count) return new object[] { false };
                var (k, v) = list[(int)idx].KvTags[(int)kv];
                StructKit.WriteUtf8ToPtr((IntPtr)args[4], StructKit.AsUInt(args[5]), k);
                StructKit.WriteUtf8ToPtr((IntPtr)args[6], StructKit.AsUInt(args[7]), v);
                return new object[] { true };
            }
            case "ISteamUGC_GetQueryUGCStatistic": {
                var h = StructKit.AsULong(args[1]); var idx = StructKit.AsUInt(args[2]);
                ulong val = 0;
                var list = QueryResults.TryGetValue(h, out var l) ? l : new List<WsItem>();
                if (idx < list.Count) {
                    var it = list[(int)idx];
                    val = StructKit.AsInt(args[3]) switch { 0 => it.Subs, 1 => it.Favs, 2 => it.Favs, 3 => it.Subs, _ => 0 };
                }
                return new object[] { true, null, null, null, null, val };   // res[5] -> 回写 ref ulong
            }
            case "ISteamUGC_GetQueryUGCChildren": {
                var h = StructKit.AsULong(args[1]); var idx = StructKit.AsUInt(args[2]);
                var list = QueryResults.TryGetValue(h, out var l) ? l : new List<WsItem>();
                if (idx >= list.Count) return new object[] { false };
                var children = list[(int)idx].Children;
                var buf = args[3] as Array;
                if (buf != null && children.Count > 0) buf.SetValue(StructKit.Make("PublishedFileId_t", "m_PublishedFileId", children[0]), 0);
                return new object[] { true };
            }

            case "ISteamUGC_GetItemState": {
                var id = StructKit.AsULong(args[1]);
                var job = Downloader.Get(id);
                if (job != null && !job.Done && !job.Failed) return new object[] { 4u | 8u };   // Downloading|NeedsUpdate
                return new object[] { Downloader.IsInstalled(id) ? (4u | 1u) : 0u };            // Installed|Subscribed
            }
            case "ISteamUGC_GetItemInstallInfo": {
                var id = StructKit.AsULong(args[1]);
                if (!Downloader.IsInstalled(id)) return new object[] { false };
                var dir = Downloader.ItemPath(id);
                var size = (ulong)Downloader.DirSize(dir);
                var ts = (uint)DateTimeOffset.UtcNow.ToUnixTimeSeconds();
                // 参数: (IntPtr, pfid, ref ulong sizeOnDisk, IntPtr folderBuf, uint cap, ref uint timestamp)
                StructKit.WriteUtf8ToPtr((IntPtr)args[3], StructKit.AsUInt(args[4]), dir);
                return new object[] { true, null, null, size, null, null, ts };   // res[3]->ref size(参数2) res[6]->ref ts(参数5)
            }
            case "ISteamUGC_GetItemDownloadInfo": {
                var id = StructKit.AsULong(args[1]);
                var job = Downloader.Get(id);
                if (job == null) return new object[] { false };
                return new object[] { true, null, null, (ulong)Math.Min(job.OnDisk, job.Total), (ulong)Math.Max(job.Total, 1) };   // res[3]->ref dl res[4]->ref tot
            }
            case "ISteamUGC_DownloadItem": {
                var id = StructKit.AsULong(args[1]);
                var known = KnownSizes.TryGetValue(id, out var sz) ? sz : 0L;
                Console.WriteLine($"[ugc] DownloadItem({id}) -> DepotDownloader 子进程（已知大小 {known}）");
                return new object[] { Downloader.Start(id, known) };
            }
            case "ISteamUGC_GetWorkshopEULAStatus":
                return new object[] { Dispatch.Queue("WorkshopEULAStatus_t", o => { StructKit.SetAuto(o, "m_eResult", 1); StructKit.Set(o, "m_bNeedsAction", false); }) };
            case "ISteamUGC_GetNumSubscribedItems": {
                var n = Directory.Exists(Downloader.ContentDir) ? Directory.GetDirectories(Downloader.ContentDir).Length : 0;
                return new object[] { (uint)n };
            }
            case "ISteamUGC_GetSubscribedItems": {
                var dirs = Directory.Exists(Downloader.ContentDir) ? Directory.GetDirectories(Downloader.ContentDir) : Array.Empty<string>();
                var arr = args[2] as Array;
                if (arr != null) for (int i = 0; i < Math.Min(arr.Length, dirs.Length); i++)
                    if (ulong.TryParse(Path.GetFileName(dirs[i]), out var id)) arr.SetValue(StructKit.Make("PublishedFileId_t", "m_PublishedFileId", id), i);
                return new object[] { (uint)dirs.Length };
            }
        }
        return null;
    }

}
