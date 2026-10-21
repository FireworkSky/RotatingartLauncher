using System.Collections.Concurrent;
using System.Text.Json;
using System.Net;
using SteamKit2;
using SteamKit2.CDN;

namespace TmlAndroidContent.Steam;

public sealed record SteamDownloadProgress(
    long TotalBytes,
    long DownloadedBytes,
    int TotalFiles,
    int CompletedFiles,
    string? CurrentFile)
{
    public double Percentage => TotalBytes == 0 ? 100 : DownloadedBytes * 100d / TotalBytes;
}

public sealed class SteamAppDownloader
{
    /// <summary>下载完成标记文件名（按 app 区分，避免多游戏互相覆盖）。</summary>
    public static string CompletionMarker(uint appId) => $".steam-content-{appId}.complete";

    private const int MaxRetries = 5;
    // 并发度：文件级 8 + 每个文件内 4 个 chunk 并行（之前是文件级 4、chunk 串行，太慢）
    private const int MaxConcurrentFiles = 8;
    private const int MaxConcurrentChunksPerFile = 4;
    private static readonly TimeSpan ContentDirectoryTimeout = TimeSpan.FromSeconds(20);

    private readonly Action<SteamDownloadProgress>? _progress;
    private readonly Action<string>? _status;
    /// <summary>
    /// beta 分支密码缓存（app+branch → 32 字节分支密码）。
    /// CheckAppBetaPassword 会被 Steam 限流（RateLimitExceeded），所以拿到一次就记下来，
    /// 重试下载 / 重复安装都不再打这个接口。
    /// </summary>
    private static readonly ConcurrentDictionary<(uint AppId, string Branch), byte[]> BetaPasswords = new();

    /// <summary>
    /// beta 分支密码落盘缓存（app+branch → 密码字节，base64）。
    /// 每个账号对同一个分支只需要成功校验一次，之后就一直用这份密码换私有 depot 段，
    /// 不再请求 CheckAppBetaPassword（那个接口会 RateLimitExceeded）。
    /// </summary>
    private static string? _betaCacheFile;
    private static readonly object BetaCacheLock = new();

    public static void UseBetaCacheFile(string? path)
    {
        if (string.IsNullOrWhiteSpace(path)) return;
        _betaCacheFile = path;
        try
        {
            if (!File.Exists(path)) return;
            var loaded = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(path));
            if (loaded is null) return;
            foreach (var (key, value) in loaded)
            {
                var parts = key.Split('|');
                if (parts.Length != 2 || !uint.TryParse(parts[0], out var appId)) continue;
                BetaPasswords[(appId, parts[1])] = Convert.FromBase64String(value);
            }
        }
        catch
        {
            // 缓存坏了就当没有
        }
    }

    /// <summary>把成功校验拿到的分支密码记下来（每个账号每个分支只成功一次，之后长期复用）</summary>
    public static void RememberBetaPassword(uint appId, string branch, byte[] password) =>
        PersistBetaPassword(appId, branch, password);

    /// <summary>已缓存的某个 app 的 beta 分支名（有缓存就不用再问 Steam）</summary>
    public static IReadOnlyCollection<string> CachedBranches(uint appId) =>
        BetaPasswords.Keys.Where(k => k.AppId == appId).Select(k => k.Branch).ToArray();

    private static void PersistBetaPassword(uint appId, string branch, byte[] password)
    {
        BetaPasswords[(appId, branch)] = password;
        var path = _betaCacheFile;
        if (string.IsNullOrWhiteSpace(path)) return;
        lock (BetaCacheLock)
        {
            try
            {
                var map = BetaPasswords.ToDictionary(
                    kv => $"{kv.Key.AppId}|{kv.Key.Branch}",
                    kv => Convert.ToBase64String(kv.Value));
                Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
                File.WriteAllText(path, JsonSerializer.Serialize(map));
            }
            catch
            {
                // 写不进去不影响本次下载
            }
        }
    }

    private readonly IReadOnlyCollection<uint> _skipDepots;
    private readonly string? _branchPassword;
    private readonly string? _betaCode;
    private readonly ConcurrentDictionary<(uint DepotId, string Host), string> _cdnAuthTokens = new();

    /// <param name="skipDepots">
    /// 明确不下载的 depot。目前只有 tModLoader 用到：它的共享内容 depot（1281931）是原版素材，
    /// RAL 安装时保留游戏自己那份 Content，不需要再下一遍。
    /// </param>
    public SteamAppDownloader(
        Action<SteamDownloadProgress>? progress = null,
        Action<string>? status = null,
        IReadOnlyCollection<uint>? skipDepots = null,
        string? branchPassword = null,
        string? betaCode = null)
    {
        _progress = progress;
        _status = status;
        _skipDepots = skipDepots ?? Array.Empty<uint>();
        _branchPassword = branchPassword;
        _betaCode = betaCode;
    }

    public async Task DownloadAsync(
        SteamSession session,
        SteamAppSpec spec,
        string targetDirectory,
        CancellationToken cancellationToken = default,
        Func<CancellationToken, ValueTask>? waitForResume = null)
    {
        ArgumentNullException.ThrowIfNull(session);
        ArgumentNullException.ThrowIfNull(spec);
        ArgumentException.ThrowIfNullOrWhiteSpace(targetDirectory);
        if (!session.IsOwnershipVerified(spec.AppId))
            throw new InvalidOperationException(
                $"Steam ownership of {spec.Name} (app {spec.AppId}) must be verified before downloading.");

        var marker = Path.Combine(Path.GetFullPath(targetDirectory), CompletionMarker(spec.AppId));
        _status?.Invoke($"Requesting {spec.Name} product information...");
        Directory.CreateDirectory(targetDirectory);
        File.Delete(marker);
        var appInfo = await GetAppInfoAsync(session, spec.AppId, cancellationToken)
            .ConfigureAwait(false);
        // 受密码保护的 beta 分支（tModLoader 的 1.4.5-dev 这类）在公开 appinfo 里没有 manifest gid，
        // 必须先拿 beta 密码向 Steam 换「私有 beta 的 depot 段」（CheckAppBetaPassword → PICSGetPrivateBeta）。
        var depotSection = appInfo.KeyValues["depots"];
        if (!string.IsNullOrWhiteSpace(_betaCode) && !string.Equals(spec.Branch, SteamAppCatalog.DefaultBranch, StringComparison.OrdinalIgnoreCase))
        {
            var privateSection = await TryGetPrivateBetaDepotSectionAsync(session, spec, cancellationToken).ConfigureAwait(false);
            if (privateSection is not null && privateSection != KeyValue.Invalid)
            {
                // 私有段常常缺 oslist：用公开 appinfo 里该平台的 depot id 白名单筛一遍
                var platformDepots = SteamAppCatalog.SelectDepotIds(appInfo.KeyValues["depots"], spec.Platform);
                var filtered = SteamAppCatalog.SelectDepots(privateSection, spec.Branch, spec.Platform)
                    .Where(d => platformDepots.Count == 0 || platformDepots.Contains(d.DepotId))
                    .ToList();
                if (filtered.Count == 0)
                {
                    var ids = string.Join(",", privateBetaIds(privateSection));
                    throw new InvalidOperationException(
                        $"分支 {spec.Branch} 的私有 beta 数据里没有该平台的 depot（私有段 depot=[{ids}]，" +
                        $"平台白名单=[{string.Join(",", platformDepots)}]）");
                }
                if (filtered.Count > 0)
                {
                    var section = new KeyValue("depots");
                    foreach (var (depotId, manifestId) in filtered)
                    {
                        var depotNode = new KeyValue(depotId.ToString());
                        var manifests = new KeyValue("manifests");
                        var branchNode = new KeyValue(spec.Branch);
                        branchNode["gid"].Value = manifestId.ToString();
                        manifests.Children.Add(branchNode);
                        depotNode.Children.Add(manifests);
                        section.Children.Add(depotNode);
                    }
                    depotSection = section;
                    _status?.Invoke($"使用私有 beta 分支数据：{spec.Branch}（{filtered.Count} 个平台 depot）");
                }
            }
        }
        var depots = SteamAppCatalog.SelectDepots(depotSection, spec.Branch, spec.Platform);
        if (_skipDepots.Count > 0)
        {
            var skipped = depots.Where(depot => _skipDepots.Contains(depot.DepotId)).ToList();
            if (skipped.Count > 0)
            {
                depots = depots.Where(depot => !_skipDepots.Contains(depot.DepotId)).ToList();
                _status?.Invoke(
                    $"Skipping depot(s) {string.Join(", ", skipped.Select(s => s.DepotId))} " +
                    $"(keep the game's own files)");
            }
        }
        if (depots.Count == 0)
            throw new InvalidOperationException(
                $"Steam returned no usable {spec.Branch} depots for {spec.Name}.");

        var servers = (await LoadContentServersAsync(session, cancellationToken)
                .ConfigureAwait(false))
            .Where(server => server.Type is "SteamCache" or "CDN")
            .OrderBy(server => server.WeightedLoad)
            .ToList();
        if (servers.Count == 0)
            throw new InvalidOperationException("Steam returned no usable CDN servers.");

        _status?.Invoke($"Preparing {depots.Count} {spec.Name} depots ({spec.Branch}, {spec.PlatformLabel})...");
        var output = new DownloadState(targetDirectory, _progress);
        foreach (var (depotId, manifestId) in depots)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (waitForResume is not null)
                await waitForResume(cancellationToken).ConfigureAwait(false);

            var failures = new List<(Server Server, Exception Error)>();
            var downloaded = false;
            foreach (var server in servers)
            {
                cancellationToken.ThrowIfCancellationRequested();
                _status?.Invoke($"Trying Steam CDN {DescribeServer(server)}...");
                try
                {
                    await DownloadDepotAsync(
                            session,
                            spec,
                            output,
                            depotId,
                            manifestId,
                            server,
                            cancellationToken,
                            waitForResume)
                        .ConfigureAwait(false);
                    downloaded = true;
                    break;
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
                {
                    throw;
                }
                catch (Exception exception)
                {
                    failures.Add((server, exception));
                    _status?.Invoke(
                        $"Steam CDN {DescribeServer(server)} failed; switching to another official CDN...");
                }
            }

            if (!downloaded)
            {
                var summary = string.Join(
                    "; ",
                    failures.Select(f => $"{DescribeServer(f.Server)}: {f.Error.Message}"));
                throw new IOException(
                    $"All official Steam CDNs failed for depot {depotId}. {summary}",
                    failures.LastOrDefault().Error);
            }
        }

        var temporaryMarker = marker + ".tmp";
        await File.WriteAllTextAsync(
                temporaryMarker,
                DateTimeOffset.UtcNow.ToString("O"),
                cancellationToken)
            .ConfigureAwait(false);
        File.Move(temporaryMarker, marker, overwrite: true);
    }

    /// <summary>
    /// 创意工坊物品下载：workshopdepot 就是 appid 本身，manifest id 是 publishedFileId
    /// （DepotDownloader 的 isUgc 分支就是这么干的）。匿名会话可用。
    /// </summary>
    public async Task DownloadWorkshopItemAsync(
        SteamSession session,
        uint appId,
        ulong publishedFileId,
        string targetDirectory,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(session);
        ArgumentException.ThrowIfNullOrWhiteSpace(targetDirectory);
        Directory.CreateDirectory(targetDirectory);

        var spec = new SteamAppSpec(
            appId, $"workshop:{publishedFileId}", "public", SteamAppCatalog.DefaultPlatform, null, "创意工坊物品");

        var servers = (await LoadContentServersAsync(session, cancellationToken).ConfigureAwait(false))
            .Where(server => server.Type is "SteamCache" or "CDN")
            .OrderBy(server => server.WeightedLoad)
            .ToList();
        if (servers.Count == 0)
            throw new InvalidOperationException("Steam returned no usable CDN servers.");

        var output = new DownloadState(targetDirectory, _progress);
        var failures = new List<(Server Server, Exception Error)>();
        foreach (var server in servers)
        {
            cancellationToken.ThrowIfCancellationRequested();
            _status?.Invoke($"Trying Steam CDN {DescribeServer(server)}...");
            try
            {
                await DownloadDepotAsync(
                        session, spec, output, appId, publishedFileId, server, cancellationToken, null)
                    .ConfigureAwait(false);
                return;
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception exception)
            {
                failures.Add((server, exception));
            }
        }

        throw new IOException(
            $"All Steam CDNs failed for workshop item {publishedFileId}. " +
            string.Join("; ", failures.Select(f => $"{DescribeServer(f.Server)}: {f.Error.Message}")),
            failures.LastOrDefault().Error);
    }

    private static async Task<IReadOnlyCollection<Server>> LoadContentServersAsync(
        SteamSession session,
        CancellationToken cancellationToken)
    {
        // CM 侧取 CDN 列表（不走 api.steampowered.com，国内可直连）；
        // 拿不到再退回公开的 ContentServerDirectoryService。
        try
        {
            var servers = await session.Content.GetServersForSteamPipe()
                .WaitAsync(ContentDirectoryTimeout, cancellationToken)
                .ConfigureAwait(false);
            if (servers is { Count: > 0 })
                return servers;
        }
        catch (Exception) when (!cancellationToken.IsCancellationRequested)
        {
            // 走下面兜底
        }

        return await ContentServerDirectoryService.LoadAsync(
                session.Configuration, cancellationToken)
            .WaitAsync(ContentDirectoryTimeout, cancellationToken)
            .ConfigureAwait(false);
    }

    private static string DescribeServer(Server server) =>
        $"{server.VHost ?? server.Host ?? "unknown"} ({server.Protocol})";

    private static IEnumerable<string> privateBetaIds(KeyValue section) =>
        section == KeyValue.Invalid ? Enumerable.Empty<string>() : section.Children.Select(c => c.Name);

    /// <summary>
    /// 受密码保护的 beta 分支：先用 beta 密码向 Steam 校验（CheckAppBetaPassword），
    /// 再用拿到的分支密码换「私有 beta depot 段」（PICSGetPrivateBeta）。
    /// 公开 appinfo 里没有这类分支的 manifest gid，所以必须走这一条。
    /// </summary>
    private async Task<KeyValue?> TryGetPrivateBetaDepotSectionAsync(
        SteamSession session,
        SteamAppSpec spec,
        CancellationToken cancellationToken)
    {
        try
        {
            if (BetaPasswords.TryGetValue((spec.AppId, spec.Branch), out var cachedPassword))
            {
                _status?.Invoke($"使用缓存的 beta 分支密码：{spec.Branch}");
                var cachedToken = session.AccessTokenFor(spec.AppId);
                var cachedBeta = await session.Apps.PICSGetPrivateBeta(
                        spec.AppId, cachedToken, spec.Branch, cachedPassword)
                    .ToTask().WaitAsync(TimeSpan.FromSeconds(30), cancellationToken).ConfigureAwait(false);
                return cachedBeta.DepotSection;
            }

            var beta = await session.Apps.CheckAppBetaPassword(spec.AppId, _betaCode!)
                .ToTask().WaitAsync(TimeSpan.FromSeconds(30), cancellationToken).ConfigureAwait(false);
            var names = beta.BetaPasswords is null
                ? string.Empty
                : string.Join(",", beta.BetaPasswords.Keys);
            _status?.Invoke($"beta 密码校验结果：{beta.Result}，可见分支=[{names}]");
            if (beta.BetaPasswords is null || !beta.BetaPasswords.TryGetValue(spec.Branch, out var branchPassword))
            {
                var hint = beta.Result == EResult.RateLimitExceeded
                    ? "Steam 限流（RateLimitExceeded），等 1~2 分钟再试"
                    : "密码不对，或该账号无权访问这个分支";
                _status?.Invoke($"beta 校验失败：{beta.Result}（Steam 给的分支：[{names}]）— {hint}");
                return null;
            }
            PersistBetaPassword(spec.AppId, spec.Branch, branchPassword);

            var accessToken = session.AccessTokenFor(spec.AppId);
            var privateBeta = await session.Apps.PICSGetPrivateBeta(
                    spec.AppId, accessToken, spec.Branch, branchPassword)
                .ToTask().WaitAsync(TimeSpan.FromSeconds(30), cancellationToken).ConfigureAwait(false);
            var depotNames = privateBeta.DepotSection == KeyValue.Invalid
                ? "(无)"
                : string.Join(",", privateBeta.DepotSection.Children.Select(c => c.Name));
            _status?.Invoke($"已取到私有 beta 数据：{spec.Branch}，depots=[{depotNames}]");
            return privateBeta.DepotSection;
        }
        catch (Exception ex)
        {
            _status?.Invoke($"取私有 beta 数据失败：{ex.Message}");
            return null;
        }
    }

    private static async Task<SteamApps.PICSProductInfoCallback.PICSProductInfo> GetAppInfoAsync(
        SteamSession session,
        uint appId,
        CancellationToken cancellationToken)
    {
        var response = await session.Apps.PICSGetProductInfo(
            new[] { new SteamApps.PICSRequest(appId, session.AccessTokenFor(appId)) },
            Enumerable.Empty<SteamApps.PICSRequest>())
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);

        foreach (var callback in response.Results ?? [])
        {
            if (callback.Apps.TryGetValue(appId, out var info))
                return info;
        }

        throw new InvalidOperationException($"Steam returned no product information for app {appId}.");
    }

    private async Task DownloadDepotAsync(
        SteamSession session,
        SteamAppSpec spec,
        DownloadState output,
        uint depotId,
        ulong manifestId,
        Server server,
        CancellationToken cancellationToken,
        Func<CancellationToken, ValueTask>? waitForResume)
    {
        var keyResponse = await session.Apps.GetDepotDecryptionKey(depotId, spec.AppId)
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);
        if (keyResponse.Result != EResult.OK)
            throw new InvalidOperationException($"Could not get depot {depotId} key: {keyResponse.Result}");

        var requestCode = await session.Content.GetManifestRequestCode(
            depotId, spec.AppId, manifestId, spec.Branch, _branchPassword)
            .WaitAsync(cancellationToken).ConfigureAwait(false);
        if (requestCode == 0)
            throw new InvalidOperationException($"Could not authorize depot {depotId} manifest.");

        using var client = new Client(session.Client);
        _status?.Invoke($"Reading depot {depotId} manifest...");
        var manifest = await DownloadManifestWithRetryAsync(
            client, spec, depotId, manifestId, requestCode, server, keyResponse.DepotKey,
            session, cancellationToken).ConfigureAwait(false);

        var files = (manifest.Files ?? []).Where(file =>
            !file.Flags.HasFlag(EDepotFileFlag.Directory)).ToArray();
        output.Begin(files);

        using var limiter = new SemaphoreSlim(MaxConcurrentFiles);
        var tasks = files.Select(async file =>
        {
            await limiter.WaitAsync(cancellationToken).ConfigureAwait(false);
            try
            {
                await DownloadFileAsync(client, output, file, depotId, keyResponse.DepotKey,
                    session, spec, server, cancellationToken, waitForResume).ConfigureAwait(false);
            }
            finally
            {
                limiter.Release();
            }
        });
        await Task.WhenAll(tasks).ConfigureAwait(false);
    }

    private async Task<DepotManifest> DownloadManifestWithRetryAsync(
        Client client,
        SteamAppSpec spec,
        uint depotId,
        ulong manifestId,
        ulong requestCode,
        Server server,
        byte[] depotKey,
        SteamSession session,
        CancellationToken cancellationToken)
    {
        Exception? lastError = null;
        string? cdnAuthToken = null;
        for (var attempt = 0; attempt < MaxRetries; attempt++)
        {
            try
            {
                return await client.DownloadManifestAsync(
                    depotId, manifestId, requestCode, server, depotKey,
                    cdnAuthToken: cdnAuthToken).ConfigureAwait(false);
            }
            catch (SteamKitWebRequestException exception)
                when (exception.StatusCode == HttpStatusCode.Forbidden && cdnAuthToken is null)
            {
                lastError = exception;
                cdnAuthToken = await GetCdnAuthTokenAsync(
                        session, spec, depotId, server, cancellationToken)
                    .ConfigureAwait(false);
                if (cdnAuthToken is null && attempt == MaxRetries - 1)
                    throw;
            }
            catch (Exception exception) when (attempt < MaxRetries - 1)
            {
                lastError = exception;
                await Task.Delay(TimeSpan.FromMilliseconds(250 * (attempt + 1)), cancellationToken)
                    .ConfigureAwait(false);
            }
        }

        throw new InvalidOperationException("Steam manifest download failed.", lastError);
    }

    private async Task DownloadFileAsync(
        Client client,
        DownloadState output,
        DepotManifest.FileData file,
        uint depotId,
        byte[] depotKey,
        SteamSession session,
        SteamAppSpec spec,
        Server server,
        CancellationToken cancellationToken,
        Func<CancellationToken, ValueTask>? waitForResume)
    {
        var relativePath = file.FileName.Replace('\\', '/');
        var destination = output.ResolvePath(relativePath);
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        output.ReportFile(relativePath);

        var temporary = destination + ".downloading";
        try
        {
            var handle = File.OpenHandle(
                temporary, FileMode.Create, FileAccess.Write, FileShare.None,
                FileOptions.Asynchronous);
            try
            {
                using var chunkLimiter = new SemaphoreSlim(MaxConcurrentChunksPerFile);
                var tasks = file.Chunks.OrderBy(chunk => chunk.Offset).Select(async chunk =>
                {
                    await chunkLimiter.WaitAsync(cancellationToken).ConfigureAwait(false);
                    try
                    {
                        cancellationToken.ThrowIfCancellationRequested();
                        if (waitForResume is not null)
                            await waitForResume(cancellationToken).ConfigureAwait(false);
                        var buffer = new byte[chunk.UncompressedLength];
                        var written = await DownloadChunkWithRetryAsync(
                                client, session, spec, depotId, chunk, buffer, depotKey, server,
                                relativePath, cancellationToken)
                            .ConfigureAwait(false);

                        await RandomAccess.WriteAsync(
                                handle, buffer.AsMemory(0, written), checked((long)chunk.Offset),
                                cancellationToken)
                            .ConfigureAwait(false);
                        output.ReportBytes(written);
                    }
                    finally
                    {
                        chunkLimiter.Release();
                    }
                });
                await Task.WhenAll(tasks).ConfigureAwait(false);
                RandomAccess.SetLength(handle, checked((long)file.TotalSize));
            }
            finally
            {
                handle.Dispose();
            }

            if (!VerifyFile(temporary, file))
                throw new InvalidDataException($"SHA-1 mismatch after downloading {relativePath}.");

            File.Move(temporary, destination, overwrite: true);
            output.ReportCompleted();
        }
        finally
        {
            if (File.Exists(temporary))
                File.Delete(temporary);
        }
    }

    private async Task<int> DownloadChunkWithRetryAsync(
        Client client,
        SteamSession session,
        SteamAppSpec spec,
        uint depotId,
        DepotManifest.ChunkData chunk,
        byte[] buffer,
        byte[] depotKey,
        Server server,
        string relativePath,
        CancellationToken cancellationToken)
    {
        Exception? lastError = null;
        string? cdnAuthToken = null;
        for (var attempt = 0; attempt < MaxRetries; attempt++)
        {
            try
            {
                var written = await client.DownloadDepotChunkAsync(
                        depotId, chunk, server, buffer, depotKey,
                        cdnAuthToken: cdnAuthToken)
                    .ConfigureAwait(false);
                if (!VerifyChunk(buffer, written, chunk))
                    throw new InvalidDataException($"SHA-1 mismatch in {relativePath}.");
                return written;
            }
            catch (SteamKitWebRequestException exception)
                when (exception.StatusCode == HttpStatusCode.Forbidden && cdnAuthToken is null)
            {
                lastError = exception;
                cdnAuthToken = await GetCdnAuthTokenAsync(
                        session, spec, depotId, server, cancellationToken)
                    .ConfigureAwait(false);
                if (cdnAuthToken is not null)
                {
                    try
                    {
                        var written = await client.DownloadDepotChunkAsync(
                                depotId, chunk, server, buffer, depotKey,
                                cdnAuthToken: cdnAuthToken)
                            .ConfigureAwait(false);
                        if (!VerifyChunk(buffer, written, chunk))
                            throw new InvalidDataException($"SHA-1 mismatch in {relativePath}.");
                        return written;
                    }
                    catch (Exception retryException)
                    {
                        lastError = retryException;
                    }
                }
            }
            catch (Exception exception)
            {
                lastError = exception;
            }

            if (attempt < MaxRetries - 1)
            {
                await Task.Delay(
                        TimeSpan.FromMilliseconds(250 * (attempt + 1)), cancellationToken)
                    .ConfigureAwait(false);
            }
        }

        throw new IOException($"Steam CDN chunk download failed for {relativePath}.", lastError);
    }

    private static bool VerifyChunk(byte[] buffer, int length, DepotManifest.ChunkData chunk)
    {
        return chunk.ChunkID is null || chunk.ChunkID.Length == 0 ||
            System.Security.Cryptography.SHA1.HashData(buffer.AsSpan(0, length))
                .AsSpan().SequenceEqual(chunk.ChunkID);
    }

    private async Task<string?> GetCdnAuthTokenAsync(
        SteamSession session,
        SteamAppSpec spec,
        uint depotId,
        Server server,
        CancellationToken cancellationToken)
    {
        var host = server.Host ?? server.VHost;
        if (string.IsNullOrWhiteSpace(host))
            return null;

        if (_cdnAuthTokens.TryGetValue((depotId, host), out var cachedToken))
            return cachedToken;

        var response = await session.Content.GetCDNAuthToken(
                spec.AppId, depotId, host)
            .WaitAsync(cancellationToken)
            .ConfigureAwait(false);
        if (response.Result != EResult.OK || string.IsNullOrWhiteSpace(response.Token))
            return null;

        _status?.Invoke($"Steam CDN authorized: {host}");
        return _cdnAuthTokens.GetOrAdd((depotId, host), response.Token);
    }

    private static bool VerifyFile(string path, DepotManifest.FileData file)
    {
        var info = new FileInfo(path);
        if (info.Length != checked((long)file.TotalSize))
            return false;
        using var stream = File.OpenRead(path);
        return System.Security.Cryptography.SHA1.HashData(stream).AsSpan().SequenceEqual(file.FileHash);
    }

    private sealed class DownloadState
    {
        private readonly string _root;
        private readonly Action<SteamDownloadProgress>? _progress;
        private long _totalBytes;
        private long _downloadedBytes;
        private int _totalFiles;
        private int _completedFiles;
        private string? _currentFile;

        public DownloadState(string root, Action<SteamDownloadProgress>? progress)
        {
            _root = Path.GetFullPath(root);
            _progress = progress;
        }

        public void Begin(IReadOnlyCollection<DepotManifest.FileData> files)
        {
            Interlocked.Exchange(ref _totalBytes, files.Sum(file => (long)file.TotalSize));
            Interlocked.Exchange(ref _downloadedBytes, 0);
            Interlocked.Exchange(ref _totalFiles, files.Count);
            Interlocked.Exchange(ref _completedFiles, 0);
            _currentFile = null;
            Report();
        }

        public string ResolvePath(string relativePath)
        {
            var full = Path.GetFullPath(Path.Combine(_root, relativePath));
            var prefix = _root.TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
            if (!full.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException($"Steam depot path escapes content root: {relativePath}");

            EnsureNoReparsePoints(Path.GetDirectoryName(full)!);
            return full;
        }

        private void EnsureNoReparsePoints(string directory)
        {
            var current = _root;
            if (HasReparsePoint(current))
                throw new InvalidDataException("Steam content root cannot be a symbolic link.");

            var relative = Path.GetRelativePath(_root, directory);
            foreach (var segment in relative.Split(
                         Path.DirectorySeparatorChar,
                         StringSplitOptions.RemoveEmptyEntries))
            {
                current = Path.Combine(current, segment);
                if (Directory.Exists(current) && HasReparsePoint(current))
                    throw new InvalidDataException($"Steam content path contains a symbolic link: {segment}");
            }
        }

        private static bool HasReparsePoint(string path) =>
            (File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0;

        public void ReportFile(string file)
        {
            _currentFile = file;
            Report();
        }

        public void ReportBytes(int count)
        {
            Interlocked.Add(ref _downloadedBytes, count);
            Report();
        }

        public void ReportCompleted()
        {
            Interlocked.Increment(ref _completedFiles);
            Report();
        }

        private void Report() => _progress?.Invoke(new SteamDownloadProgress(
            Interlocked.Read(ref _totalBytes),
            Interlocked.Read(ref _downloadedBytes),
            Volatile.Read(ref _totalFiles),
            Volatile.Read(ref _completedFiles),
            _currentFile));
    }
}
