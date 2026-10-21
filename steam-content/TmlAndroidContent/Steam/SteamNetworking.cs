using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using SteamKit2;
using SteamKit2.Discovery;

namespace TmlAndroidContent.Steam;

/// <summary>
/// SteamKit2 在 Android/受限网络下的连接配置。
///
/// 默认配置会在登录前先请求 api.steampowered.com 拿 CM 列表（ISteamDirectory），
/// 国内直连基本失败；同时 Android 上读不到 /etc/machine-id，SteamKit2 默认
/// IMachineInfoProvider 会连环抛异常。这里统一改成：
///   - 内置 CM 列表（TCP 27017，实测可达），禁用目录 WebAPI
///   - 只用 TCP 协议（WebSocket 443 端点常被中间设备断掉）
///   - IPv4-only 的 HTTP 客户端（部分 Android 双栈解析到 IPv6 会连不上）
///   - 机器信息用固定盐派生，保证可读且稳定
/// </summary>
public static class SteamNetworking
{
    // 来自 GetCMList 的返回，且实测 TCP 可达
    private static readonly string[] CmSeeds =
    {
        "155.133.226.75:27017", "155.133.225.21:27017", "155.133.225.20:27017",
        "162.254.195.71:27017", "162.254.195.66:27017", "162.254.194.52:27017",
        "205.196.6.148:27017", "205.196.6.134:27017",
        "103.28.54.169:27017", "103.10.124.125:27017", "45.121.184.22:27017",
    };

    /// <summary>给 SteamConfiguration.Builder 套上 Android/受限网络下的默认值</summary>
    public static ISteamConfigurationBuilder Apply(ISteamConfigurationBuilder builder, Uri? proxy = null)
    {
        builder
            .WithProtocolTypes(ProtocolTypes.Tcp)
            .WithServerListProvider(new StaticCmList())
            .WithMachineInfoProvider(new AndroidMachineInfo())
            .WithDirectoryFetch(false)          // 不查 Web API（国内经常被墙），只用内置列表
            .WithConnectionTimeout(TimeSpan.FromSeconds(30));

        if (proxy is not null)
        {
            builder.WithHttpClientFactory(_ => MakeHttpClient(proxy));
        }
        else
        {
            builder.WithHttpClientFactory(static _ => MakeHttpClient(null));
        }

        return builder;
    }

    public static SteamConfiguration Create(Uri? proxy = null)
        => SteamConfiguration.Create(builder => Apply(builder, proxy));

    private static HttpClient MakeHttpClient(Uri? proxy)
    {
        var handler = new SocketsHttpHandler
        {
            ConnectCallback = async (context, cancellationToken) =>
            {
                var socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp)
                {
                    NoDelay = true,
                };
                try
                {
                    await socket.ConnectAsync(context.DnsEndPoint.Host, context.DnsEndPoint.Port, cancellationToken)
                        .ConfigureAwait(false);
                    return new NetworkStream(socket, ownsSocket: true);
                }
                catch
                {
                    socket.Dispose();
                    throw;
                }
            },
        };

        if (proxy is not null)
        {
            handler.Proxy = new WebProxy(proxy);
            handler.UseProxy = true;
        }

        // 有些设备时钟不准，.NET 会因证书 NotTimeValid 拒掉整条链（curl 却能过）。
        // 只放宽"时间无效"这一种错误，链到受信根、域名匹配仍然强制。
        handler.SslOptions.RemoteCertificateValidationCallback = (_, _, chain, errors) =>
        {
            if (errors == System.Net.Security.SslPolicyErrors.None)
                return true;
            var onlyTime = errors == System.Net.Security.SslPolicyErrors.RemoteCertificateChainErrors && chain is not null;
            if (onlyTime)
            {
                foreach (var status in chain!.ChainStatus)
                {
                    if ((status.Status & ~System.Security.Cryptography.X509Certificates.X509ChainStatusFlags.NotTimeValid) != 0)
                    {
                        onlyTime = false;
                        break;
                    }
                }
            }
            return onlyTime;
        };

        var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(20) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("RAL-SteamContent/1.0");
        return client;
    }

    private sealed class StaticCmList : IServerListProvider
    {
        public DateTime LastServerListRefresh => DateTime.UtcNow;

        public Task<IEnumerable<ServerRecord>> FetchServerListAsync()
        {
            var list = new List<ServerRecord>();
            // 随机轮转：CM 首次连接偶发失败，换个起点能显著提高一次成功率
            foreach (var seed in CmSeeds.OrderBy(_ => Guid.NewGuid()))
            {
                if (ServerRecord.TryCreateSocketServer(seed, out var record))
                    list.Add(record);
            }
            return Task.FromResult<IEnumerable<ServerRecord>>(list);
        }

        public Task UpdateServerListAsync(IEnumerable<ServerRecord> endpoints) => Task.CompletedTask;
    }

    private sealed class AndroidMachineInfo : IMachineInfoProvider
    {
        private static readonly byte[] MachineId = Derive();

        private static byte[] Derive()
        {
            using var sha = System.Security.Cryptography.SHA256.Create();
            var full = sha.ComputeHash(System.Text.Encoding.UTF8.GetBytes("RAL-SteamContent-tML"));
            var id = new byte[16];
            Array.Copy(full, id, 16);
            return id;
        }

        public byte[] GetMachineGuid() => MachineId;
        public byte[] GetMacAddress() => MachineId;
        public byte[] GetDiskId() => MachineId;
    }
}
