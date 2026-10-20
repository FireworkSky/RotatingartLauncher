using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using SteamKit2;
using SteamKit2.Internal;
using SteamKit2.Discovery;

namespace RAL.SteamBridge;

/// <summary>SteamKit2 会话：匿名登入 Steam3，用 IPublishedFileService 查工坊。</summary>
public static class Sk2 {
    public const uint TML_APPID = 1281930;

    static SteamClient _client;
    static CallbackManager _mgr;
    static SteamUser _user;
    static SteamUnifiedMessages _unified;
    static PublishedFile _pf;
    static readonly object _lock = new();

    public static volatile bool LoggedOn;
    public static string Status = "未启动";
    public static int QueryCount, DetailCount;

    public static void Start() {
        lock (_lock) {
            if (_client != null) return;
            Console.WriteLine("[sk2] 初始化（匿名登入 Steam3）...");
            // 关键：强制 IPv4。Termux/Android 下 .NET 默认双栈 socket 解析到 IPv6 会连不上 CM
            //（DepotDownloader 里那段注释就是这个坑）
            var cfg = SteamConfiguration.Create(b => b
                .WithHttpClientFactory(static _ => MakeIpv4HttpClient())
                .WithProtocolTypes(ProtocolTypes.Tcp)
                .WithServerListProvider(new StaticCmList())
                .WithMachineInfoProvider(new AndroidMachineInfo())
                .WithDirectoryFetch(false));   // 不查 Web API（国内经常被墙），只用自带列表
            _client = new SteamClient(cfg);
            _mgr = new CallbackManager(_client);
            _user = _client.GetHandler<SteamUser>();
            _unified = _client.GetHandler<SteamUnifiedMessages>();
            _pf = _unified.CreateService<PublishedFile>();

            _mgr.Subscribe<SteamClient.ConnectedCallback>(cb => {
                Console.WriteLine("[sk2] 已连上 CM，发起匿名登入");
                Status = "连接成功，登入中";

                _user.LogOnAnonymous();
            });
            _mgr.Subscribe<SteamUser.LoggedOnCallback>(cb => {
                LoggedOn = cb.Result == EResult.OK;
                Status = cb.Result.ToString();
                Console.WriteLine($"[sk2] 登入结果: {cb.Result}  (LoggedOn={LoggedOn})");
            });
            _mgr.Subscribe<SteamClient.DisconnectedCallback>(_ => {
                LoggedOn = false; Status = "已断开";
                Console.WriteLine("[sk2] 连接断开，3 秒后重连");
                Task.Run(async () => { await Task.Delay(3000); try { if (!LoggedOn) _client.Connect(); } catch { } });
            });

            try { _client.Connect(); } catch (Exception e) { Console.WriteLine("[sk2] Connect 抛异常: " + e.Message); }
            var t = new Thread(() => {
                while (true) {
                    try { _mgr.RunWaitCallbacks(TimeSpan.FromSeconds(1)); }
                    catch (Exception e) { Console.WriteLine("[sk2] pump 异常: " + e.Message); Thread.Sleep(1000); }
                }
            }) { IsBackground = true, Name = "sk2-pump" };
            t.Start();
        }
    }

    /// <summary>
    /// 安卓上没有 /etc/machine-id、/sys/class/net 也读不到，SteamKit2 默认实现会连环抛异常
    /// （tML 会把它当"静默捕获异常"刷屏）。这里给一份能被读取的实现，机器码用固定盐 + Android ID 派生。
    /// </summary>
    sealed class AndroidMachineInfo : IMachineInfoProvider {
        static byte[] Guid16() {
            var seed = "RAL-SteamBridge-tML-1281930";
            using var sha = System.Security.Cryptography.SHA256.Create();
            var full = sha.ComputeHash(System.Text.Encoding.UTF8.GetBytes(seed));
            var b = new byte[16];
            Array.Copy(full, b, 16);
            return b;
        }
        public byte[] GetMachineGuid() => Guid16();
        public byte[] GetMacAddress() => Guid16();     // 同一份稳定值即可
        public byte[] GetDiskId() => Guid16();
    }

    /// <summary>自带 CM 列表：不依赖被墙的 api.steampowered.com，直接连 CM 的 27017</summary>
    sealed class StaticCmList : IServerListProvider {
        // 这些来自 GetCMList 的返回，且实测 TCP 可达
        static readonly string[] Seeds = {
            "155.133.226.75:27017", "155.133.225.21:27017", "155.133.225.20:27017",
            "162.254.195.71:27017", "162.254.195.66:27017", "162.254.194.52:27017",
            "205.196.6.148:27017", "205.196.6.134:27017",
            "103.28.54.169:27017", "103.10.124.125:27017", "45.121.184.22:27017",
        };
        public DateTime LastServerListRefresh => DateTime.UtcNow;
        public Task<IEnumerable<ServerRecord>> FetchServerListAsync() {
            var list = new List<ServerRecord>();
            // 随机轮转：CM 首次连接偶发失败，换个起点能显著提高一次成功率
            var order = Seeds.OrderBy(_ => Guid.NewGuid()).ToArray();
            foreach (var s in order)
                if (ServerRecord.TryCreateSocketServer(s, out var r)) list.Add(r);
            Console.WriteLine($"[sk2] 使用内置 CM 列表: {list.Count} 个（不走 Web API）");
            return Task.FromResult<IEnumerable<ServerRecord>>(list);
        }
        public Task UpdateServerListAsync(IEnumerable<ServerRecord> endpoints) => Task.CompletedTask;
    }

    static System.Net.Http.HttpClient MakeIpv4HttpClient() {
        var handler = new System.Net.Http.SocketsHttpHandler {
            ConnectCallback = async (ctx, ct) => {
                var socket = new System.Net.Sockets.Socket(System.Net.Sockets.AddressFamily.InterNetwork,
                    System.Net.Sockets.SocketType.Stream, System.Net.Sockets.ProtocolType.Tcp) { NoDelay = true };
                try {
                    await socket.ConnectAsync(ctx.DnsEndPoint.Host, ctx.DnsEndPoint.Port, ct).ConfigureAwait(false);
                    return new System.Net.Sockets.NetworkStream(socket, ownsSocket: true);
                } catch { socket.Dispose(); throw; }
            }
        };
        // 有些设备/环境时钟不准，.NET 会因 NotTimeValid 拒掉整条链（curl 却能过）。
        // 这里只放宽“时间无效”这一种错误，链到受信根、域名匹配仍然强制。
        handler.SslOptions.RemoteCertificateValidationCallback = (sender, cert, chain, errors) => {
            if (errors == System.Net.Security.SslPolicyErrors.None) return true;
            bool onlyTime = errors == System.Net.Security.SslPolicyErrors.RemoteCertificateChainErrors && chain != null;
            if (onlyTime)
                foreach (var st in chain.ChainStatus)
                    if ((st.Status & ~System.Security.Cryptography.X509Certificates.X509ChainStatusFlags.NotTimeValid) != 0) { onlyTime = false; break; }
            if (onlyTime) { Console.WriteLine("[sk2] 备注: 放松了证书时间校验（本机时钟与证书有效期不符）"); return true; }
            Console.WriteLine($"[sk2] TLS 校验失败: {errors}" + (chain == null ? "" : " / " + string.Join(",", chain.ChainStatus.Select(x => x.Status.ToString()))));
            return false;
        };
        var c = new System.Net.Http.HttpClient(handler);
        c.DefaultRequestHeaders.UserAgent.ParseAdd("RAL-SteamBridge/1.0");
        return c;
    }

    public static bool WaitLoggedOn(int timeoutMs) {
        Start();
        var t0 = Environment.TickCount;
        while (!LoggedOn && Environment.TickCount - t0 < timeoutMs) Thread.Sleep(50);
        return LoggedOn;
    }

    /// <summary>QueryFiles —— 等价于原生 ISteamUGC_CreateQueryAllUGCRequest + SendQueryUGCRequest</summary>
    public sealed class QueryResult {
        public List<WsItem> Items = new();
        public uint Total;
        public string NextCursor = "*";
    }

    static readonly System.Threading.SemaphoreSlim _qGate = new(1, 1);

    public static QueryResult Query(uint page, string search, uint numPerPage, uint sortMode, string cursor = null) {
        if (!WaitLoggedOn(90000)) { Console.WriteLine("[sk2] 90s 未能登入，放弃: " + Status); return new QueryResult(); }
        var req = new CPublishedFile_QueryFiles_Request {
            query_type = 1,                    // 1 = ranked by publication date（原生 EUGCQuery 的对应物）
            page = page,
            numperpage = numPerPage == 0 ? 20 : numPerPage,
            creator_appid = TML_APPID,
            appid = TML_APPID,
            filetype = 0,
            return_vote_data = true,
            return_tags = true,
            return_kv_tags = true,
            return_previews = true,
            return_metadata = true,
            return_children = true,
            return_short_description = true,
        };
        if (!string.IsNullOrEmpty(search)) req.search_text = search;
        if (!string.IsNullOrEmpty(cursor) && cursor != "*") req.cursor = cursor;
        // 串行化：同一个 SK2 会话并发发多个统一消息容易互相超时
        _qGate.Wait();
        SteamKit2.SteamUnifiedMessages.ServiceMethodResponse<SteamKit2.Internal.CPublishedFile_QueryFiles_Response> r;
        try { r = _pf.QueryFiles(req).ToTask().GetAwaiter().GetResult(); }
        finally { _qGate.Release(); }
        if (r.Result != EResult.OK) { Console.WriteLine($"[sk2] QueryFiles 失败: {r.Result}"); return new QueryResult(); }
        var res = new QueryResult {
            Items = r.Body.publishedfiledetails.Select(Map).ToList(),
            Total = r.Body.total,
            NextCursor = string.IsNullOrEmpty(r.Body.next_cursor) ? "*" : r.Body.next_cursor,
        };
        QueryCount++;
        Console.WriteLine($"[sk2] QueryFiles('{search}') -> {res.Items.Count} 条 / 总 {res.Total} / next_cursor={res.NextCursor}");
        return res;
    }

    /// <summary>GetDetails —— 等价于原生 ISteamUGC_CreateQueryUGCDetailsRequest</summary>
    public static List<WsItem> Details(IEnumerable<ulong> ids) {
        if (!WaitLoggedOn(20000)) return new List<WsItem>();
        var req = new CPublishedFile_GetDetails_Request { appid = TML_APPID, includechildren = true, includekvtags = true, includemetadata = true, includevotes = true, short_description = true };
        foreach (var id in ids) req.publishedfileids.Add(id);
        var r = _pf.GetDetails(req).ToTask().GetAwaiter().GetResult();
        if (r.Result != EResult.OK) { Console.WriteLine($"[sk2] GetDetails 失败: {r.Result}"); return new List<WsItem>(); }
        DetailCount++;
        var items = r.Body.publishedfiledetails.Select(Map).ToList();
        Console.WriteLine($"[sk2] GetDetails({items.Count} 个 id) -> {items.Count} 条");
        return items;
    }

    static WsItem Map(PublishedFileDetails d) {
        var it = new WsItem {
            PubId = d.publishedfileid,
            Title = d.title ?? "",
            Description = !string.IsNullOrEmpty(d.short_description) ? d.short_description : (d.file_description ?? ""),
            Metadata = d.metadata ?? "",
            FileName = d.filename ?? "",
            PreviewUrl = !string.IsNullOrEmpty(d.preview_url) ? d.preview_url : (d.hcontent_preview != 0 ? $"https://steamuserimages-a.akamaihd.net/ugc/{d.hcontent_preview}/" : ""),
            TimeUpdated = d.time_updated,
            TimeCreated = d.time_created,
            Subs = d.subscriptions,
            Favs = d.favorited,
            Views = d.views,
            Banned = d.banned,
            Owner = d.creator,
            HContentFile = d.hcontent_file,
            FileSize = (long)d.file_size,
            NumChildren = d.num_children,
        };
        try { if (d.vote_data != null) { it.VotesUp = d.vote_data.votes_up; it.VotesDown = d.vote_data.votes_down; } } catch { }
        if (d.tags != null) foreach (var t in d.tags) it.Tags.Add(t.tag ?? "");
        if (d.kvtags != null) foreach (var kv in d.kvtags) it.KvTags.Add((kv.key ?? "", kv.value ?? ""));
        if (d.children != null) foreach (var c in d.children) it.Children.Add(c.publishedfileid);
        return it;
    }
}
