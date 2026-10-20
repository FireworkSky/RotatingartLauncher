using System.Collections.Concurrent;
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

public sealed class SteamTerrariaContentDownloader
{
    public const string CompletionMarker = ".tmodloader-content.complete";

    private const int MaxRetries = 5;
    private const int MaxConcurrentFiles = 4;
    private static readonly TimeSpan ContentDirectoryTimeout = TimeSpan.FromSeconds(20);

    private readonly Action<SteamDownloadProgress>? _progress;
    private readonly Action<string>? _status;
    private readonly ConcurrentDictionary<(uint DepotId, string Host), string> _cdnAuthTokens = new();

    public SteamTerrariaContentDownloader(
        Action<SteamDownloadProgress>? progress = null,
        Action<string>? status = null)
    {
        _progress = progress;
        _status = status;
    }

    public async Task DownloadAsync(
        SteamSession session,
        string targetDirectory,
        CancellationToken cancellationToken = default,
        Func<CancellationToken, ValueTask>? waitForResume = null)
    {
        ArgumentNullException.ThrowIfNull(session);
        ArgumentException.ThrowIfNullOrWhiteSpace(targetDirectory);
        if (!session.TerrariaOwnershipVerified)
            throw new InvalidOperationException("Terraria ownership must be verified before downloading.");

        _status?.Invoke("Requesting Terraria product information...");
        Directory.CreateDirectory(targetDirectory);
        File.Delete(Path.Combine(Path.GetFullPath(targetDirectory), CompletionMarker));
        var appInfo = await GetAppInfoAsync(session, SteamSession.TerrariaAppId, cancellationToken)
            .ConfigureAwait(false);
        var depots = ParsePublicDepots(appInfo.KeyValues["depots"]);
        if (depots.Count == 0)
            throw new InvalidOperationException("Steam returned no public Terraria depots.");

        var servers = (await LoadContentServersAsync(session, cancellationToken)
                .ConfigureAwait(false))
            .Where(server => server.Type is "SteamCache" or "CDN")
            .OrderBy(server => server.WeightedLoad)
            .ToList();
        if (servers.Count == 0)
            throw new InvalidOperationException("Steam returned no usable CDN servers.");

        _status?.Invoke($"Preparing {depots.Count} Terraria content depots...");
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

        var marker = Path.Combine(Path.GetFullPath(targetDirectory), CompletionMarker);
        var temporaryMarker = marker + ".tmp";
        await File.WriteAllTextAsync(
                temporaryMarker,
                DateTimeOffset.UtcNow.ToString("O"),
                cancellationToken)
            .ConfigureAwait(false);
        File.Move(temporaryMarker, marker, overwrite: true);
    }

    private static async Task<IReadOnlyCollection<Server>> LoadContentServersAsync(
        SteamSession session,
        CancellationToken cancellationToken)
    {
        return await ContentServerDirectoryService.LoadAsync(
                session.Configuration, cancellationToken)
            .WaitAsync(ContentDirectoryTimeout, cancellationToken)
            .ConfigureAwait(false);
    }

    private static string DescribeServer(Server server) =>
        $"{server.VHost ?? server.Host ?? "unknown"} ({server.Protocol})";

    private static async Task<SteamApps.PICSProductInfoCallback.PICSProductInfo> GetAppInfoAsync(
        SteamSession session,
        uint appId,
        CancellationToken cancellationToken)
    {
        var response = await session.Apps.PICSGetProductInfo(
            new[] { new SteamApps.PICSRequest(appId, session.AppAccessToken) },
            Enumerable.Empty<SteamApps.PICSRequest>())
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);

        foreach (var callback in response.Results ?? [])
        {
            if (callback.Apps.TryGetValue(appId, out var info))
                return info;
        }

        throw new InvalidOperationException($"Steam returned no product information for app {appId}.");
    }

    private static List<(uint DepotId, ulong ManifestId)> ParsePublicDepots(KeyValue depotSection)
    {
        var result = new List<(uint, ulong)>();
        foreach (var depot in depotSection.Children)
        {
            if (!uint.TryParse(depot.Name, out var depotId))
                continue;

            var config = depot["config"];
            var osList = config == KeyValue.Invalid ? null : config["oslist"].Value;
            if (!string.IsNullOrWhiteSpace(osList) &&
                !osList.Contains("windows", StringComparison.OrdinalIgnoreCase))
                continue;

            var manifest = depot["manifests"]["public"]["gid"].Value;
            if (ulong.TryParse(manifest, out var manifestId))
                result.Add((depotId, manifestId));
        }

        return result;
    }

    private async Task DownloadDepotAsync(
        SteamSession session,
        DownloadState output,
        uint depotId,
        ulong manifestId,
        Server server,
        CancellationToken cancellationToken,
        Func<CancellationToken, ValueTask>? waitForResume)
    {
        var keyResponse = await session.Apps.GetDepotDecryptionKey(depotId, SteamSession.TerrariaAppId)
            .ToTask().WaitAsync(cancellationToken).ConfigureAwait(false);
        if (keyResponse.Result != EResult.OK)
            throw new InvalidOperationException($"Could not get depot {depotId} key: {keyResponse.Result}");

        var requestCode = await session.Content.GetManifestRequestCode(
            depotId, SteamSession.TerrariaAppId, manifestId, "public")
            .WaitAsync(cancellationToken).ConfigureAwait(false);
        if (requestCode == 0)
            throw new InvalidOperationException($"Could not authorize depot {depotId} manifest.");

        using var client = new Client(session.Client);
        _status?.Invoke($"Reading depot {depotId} manifest...");
        var manifest = await DownloadManifestWithRetryAsync(
            client, depotId, manifestId, requestCode, server, keyResponse.DepotKey,
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
                    session, server, cancellationToken, waitForResume).ConfigureAwait(false);
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
                        session, depotId, server, cancellationToken)
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
            await using (var stream = new FileStream(temporary, FileMode.Create, FileAccess.Write,
                FileShare.None, 64 * 1024, FileOptions.Asynchronous | FileOptions.SequentialScan))
            {
                foreach (var chunk in file.Chunks.OrderBy(chunk => chunk.Offset))
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    if (waitForResume is not null)
                        await waitForResume(cancellationToken).ConfigureAwait(false);
                    var buffer = new byte[chunk.UncompressedLength];
                    var written = await DownloadChunkWithRetryAsync(
                            client, session, depotId, chunk, buffer, depotKey, server,
                            relativePath, cancellationToken)
                        .ConfigureAwait(false);

                    stream.Position = checked((long)chunk.Offset);
                    await stream.WriteAsync(buffer.AsMemory(0, written), cancellationToken)
                        .ConfigureAwait(false);
                    output.ReportBytes(written);
                }
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
                        session, depotId, server, cancellationToken)
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
                SteamSession.TerrariaAppId, depotId, host)
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
