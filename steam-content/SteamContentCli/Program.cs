// RAL Steam 内容下载宿主
//
// 用法（stdout 每行一个 JSON 事件；RAL 里还会通过 --events 写同样的行到文件）：
//   SteamContentCli --list
//   SteamContentCli --workshop-list [--query <文本>] [--page <n>] [--count <n>]
//   SteamContentCli --workshop-download <publishedFileId> --workshop-out <dir> [--mods-dir <dir>]
//   SteamContentCli --game-root <游戏目录> [--app <appid|名称>] [--branch <分支>] [--platform linux|windows]
//                   [--credentials <文件>] [--account <账号> --password <密码>] [--guard <验证码>]
//                   [--proxy <url>] [--verify-only]
// 通用：--events <文件>（事件另写一份到文件，供宿主进程内运行时读取）
//       --guard-file <文件>（从中读 Steam Guard 验证码，用于无 stdin 的宿主）
//       --anonymous（跳过账号凭据，匿名会话；工坊查询/下载可用）
//
// 事件：{"type":"status","message":...}
//       {"type":"progress","percent":..,"downloaded":..,"total":..,"files":..,"current":..}
//       {"type":"workshop","items":[...]}
//       {"type":"done"} / {"type":"error","message":...} / {"type":"need-guard","message":...}
using System.Text.Json;
using SteamKit2;
using TmlAndroidContent.Steam;

var options = ParseArgs(args);

if (options.ServeDir is { } serveDir)
{
    return await ServeAsync(serveDir).ConfigureAwait(false);
}

Emitter.Attach(options.EventsPath);

if (options.ListApps)
{
    foreach (var app in SteamAppCatalog.All)
        Emit("app", null, app: app);
    return 0;
}

return await ExecuteAsync(options).ConfigureAwait(false);

async Task<int> ExecuteAsync(CliOptions options)
{
// 注意：--serve 模式下事件文件是按请求 attach 的，这里只有显式传了 --events 才覆盖
if (!string.IsNullOrWhiteSpace(options.EventsPath))
    Emitter.Attach(options.EventsPath);
try
{
    if (options.WorkshopList)
    {
        using var session = new SteamSession();
        await session.ConnectAsync().ConfigureAwait(false);
        Emit("status", "已连接 Steam（匿名），查询创意工坊...");
        var items = await SteamWorkshopService.QueryAsync(
            session, SteamAppCatalog.TModLoaderAppId, options.Query, options.Page, options.Count)
            .ConfigureAwait(false);
        EmitWorkshop(items);
        Emit("done", null);
        return 0;
    }

    // --beta-code 单独使用时是「查隐藏分支」；带 --game-root 时是下载参数，走下面的下载流程
    if (!string.IsNullOrWhiteSpace(options.BetaCode) && !options.DumpDepots
        && string.IsNullOrWhiteSpace(options.GameRoot))
    {
        var appId = ResolveSpec(options.App)?.AppId
            ?? (uint.TryParse(options.App, out var betaApp) ? betaApp : SteamAppCatalog.TModLoaderAppId);
        var store = new FileSteamCredentialStore(
            options.CredentialsPath ?? throw new ArgumentException("--beta-code 需要 --credentials"));
        var credentials = await store.LoadAsync().ConfigureAwait(false)
            ?? throw new InvalidOperationException("没有可用的 Steam 凭据，请先登录");
        using var session = new SteamSession(credentials);
        await session.ConnectAsync().ConfigureAwait(false);
        Emit("status", $"用 beta 密码向 Steam 查询 app {appId} 的隐藏分支...");
        var callback = await session.Apps.CheckAppBetaPassword(appId, options.BetaCode)
            .ToTask().WaitAsync(TimeSpan.FromSeconds(30)).ConfigureAwait(false);
        Emit("status", $"beta 校验结果：{callback.Result}，分支数：{callback.BetaPasswords?.Count ?? 0}");
        foreach (var pair in callback.BetaPasswords ?? new Dictionary<string, byte[]>())
        {
            var payload = new Dictionary<string, object?>
            {
                ["name"] = pair.Key,
                ["passwordLength"] = pair.Value?.Length ?? 0,
                ["password"] = pair.Value is { Length: > 0 } bytes ? System.Text.Encoding.UTF8.GetString(bytes) : null,
            };
            Emit("branch", null, branchPayload: payload);
        }
        Emit("done", null);
        return 0;
    }

    if (options.DumpDepots)
    {
        var appId = ResolveSpec(options.App)?.AppId
            ?? (uint.TryParse(options.App, out var dumpApp) ? dumpApp : SteamAppCatalog.TModLoaderAppId);
        var dumpStore = new FileSteamCredentialStore(
            options.CredentialsPath ?? throw new ArgumentException("--dump-depots 需要 --credentials"));
        var dumpCreds = await dumpStore.LoadAsync().ConfigureAwait(false)
            ?? throw new InvalidOperationException("没有可用的 Steam 凭据，请先登录");
        using var dumpSession = new SteamSession(dumpCreds);
        await dumpSession.ConnectAsync().ConfigureAwait(false);
        if (!string.IsNullOrWhiteSpace(options.BetaCode))
        {
            var beta = await dumpSession.Apps.CheckAppBetaPassword(appId, options.BetaCode)
                .ToTask().WaitAsync(TimeSpan.FromSeconds(30)).ConfigureAwait(false);
            Emit("status", $"beta 结果分支: {string.Join(",", beta.BetaPasswords?.Keys ?? Enumerable.Empty<string>())}");
        }
        var tokenResp = await dumpSession.Apps.PICSGetAccessTokens(appId, null).ToTask().ConfigureAwait(false);
        var dumpToken = tokenResp.AppTokens.TryGetValue(appId, out var dt) ? dt : 0UL;
        var infoResp = await dumpSession.Apps.PICSGetProductInfo(
            new[] { new SteamApps.PICSRequest(appId, dumpToken) }, Array.Empty<SteamApps.PICSRequest>())
            .ToTask().ConfigureAwait(false);
        var dumpInfo = infoResp.Results?.SelectMany(r => r.Apps.Values).FirstOrDefault();
        if (dumpInfo is null) { Emit("error", "没有 appinfo"); return 4; }
        foreach (var depot in dumpInfo.KeyValues["depots"].Children)
        {
            var manifests = depot["manifests"];
            if (manifests == KeyValue.Invalid) continue;
            var names = string.Join(",", manifests.Children.Select(m => m.Name));
            Emit("status", $"depot {depot.Name}: manifests=[{names}]");
        }
        Emit("done", null);
        return 0;
    }

    if (options.ListBranches)
    {
        var appId = ResolveSpec(options.App)?.AppId
            ?? (uint.TryParse(options.App, out var parsedApp) ? parsedApp : SteamAppCatalog.TModLoaderAppId);
        // 匿名 PICS 看不到 beta 分支，有凭据就用账号会话
        SteamSession session;
        if (!string.IsNullOrWhiteSpace(options.CredentialsPath) && File.Exists(options.CredentialsPath))
        {
            var stored = await new FileSteamCredentialStore(options.CredentialsPath).LoadAsync().ConfigureAwait(false);
            session = stored is null ? new SteamSession() : new SteamSession(stored);
        }
        else
        {
            session = new SteamSession();
        }
        using var _ = session;
        await session.ConnectAsync().ConfigureAwait(false);
        Emit("status", $"已连接 Steam（{session.SteamId.ConvertToUInt64()}），查询 app {appId} 的分支...");
        var tokenResponse = await session.Apps.PICSGetAccessTokens(appId, null).ToTask().ConfigureAwait(false);
        var token = tokenResponse.AppTokens.TryGetValue(appId, out var t) ? t : 0UL;
        var infoResponse = await session.Apps.PICSGetProductInfo(
            new[] { new SteamApps.PICSRequest(appId, token) }, Array.Empty<SteamApps.PICSRequest>())
            .ToTask().ConfigureAwait(false);
        var info = infoResponse.Results?.SelectMany(r => r.Apps.Values).FirstOrDefault();
        if (info is null)
        {
            Emit("error", $"Steam 没有返回 app {appId} 的信息");
            return 4;
        }
        // 分支信息在 PICS 里挂在 depots 段下面（"branches" / "privatebranches" 这两个伪 depot），
        // 少数 app 会单独给顶层 branches 段，两边都试。
        var branchSection = info.KeyValues["branches"];
        if (branchSection == KeyValue.Invalid)
            branchSection = info.KeyValues["depots"]["branches"];
        if (branchSection == KeyValue.Invalid)
            branchSection = info.KeyValues["depots"]["privatebranches"];
        if (branchSection != KeyValue.Invalid)
        {
            foreach (var branch in branchSection.Children)
            {
                var password = branch["password"].Value;
                var payload = new Dictionary<string, object?>
                {
                    ["name"] = branch.Name,
                    ["description"] = branch["description"].Value ?? "",
                    ["buildId"] = branch["buildid"].Value ?? "",
                    ["passwordRequired"] = !string.IsNullOrEmpty(password)
                        || string.Equals(branch["pwdrequired"].Value, "1", StringComparison.Ordinal),
                    ["keys"] = string.Join(",", branch.Children.Select(c => c.Name)),
                };
                Emit("branch", null, branchPayload: payload);
            }
        }
        Emit("done", null);
        return 0;
    }

    if (options.IdentityOut is { } identityOut)
    {
        var store = new FileSteamCredentialStore(
            options.CredentialsPath
            ?? throw new ArgumentException("--identity 需要 --credentials"));
        var credentials = await store.LoadAsync().ConfigureAwait(false)
            ?? throw new InvalidOperationException("没有可用的 Steam 凭据，请先登录");
        using var session = new SteamSession(credentials);
        await session.ConnectAsync().ConfigureAwait(false);
        var identity = new Dictionary<string, object?>
        {
            ["steamid"] = session.SteamId.ConvertToUInt64(),
            ["persona"] = string.IsNullOrWhiteSpace(session.PersonaName)
                ? credentials.AccountName
                : session.PersonaName,
            ["language"] = "schinese",
            ["appid"] = options.App ?? SteamAppCatalog.TerrariaAppId.ToString(),
        };
        var path = identityOut;
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
        await File.WriteAllTextAsync(path, JsonSerializer.Serialize(identity)).ConfigureAwait(false);
        Emit("status", $"身份已写入 {path}");
        Emit("done", null);
        return 0;
    }

    if (options.WorkshopDownloadId is { } workshopId)
    {
        var targetRoot = options.WorkshopOutDir ?? options.GameRoot
            ?? throw new ArgumentException("缺少 --workshop-out");
        using var session = new SteamSession();
        await session.ConnectAsync().ConfigureAwait(false);
        var downloader = new SteamAppDownloader(
            progress => Emit("progress", progress.CurrentFile, progress: progress),
            message => Emit("status", message));
        Emit("status", $"查询创意工坊物品 {workshopId} 详情...");
        var details = await SteamWorkshopService.GetDetailsAsync(
            session, SteamAppCatalog.TModLoaderAppId, new[] { workshopId }).ConfigureAwait(false);
        var item = details.FirstOrDefault()
            ?? throw new InvalidOperationException($"找不到创意工坊物品 {workshopId}");
        if (item.ContentHandle == 0)
            throw new InvalidOperationException($"物品 {workshopId} 没有内容句柄（可能是合集/隐藏物品）");
        Emit("status", $"下载 {item.Title}（{item.FileSize / 1024.0 / 1024.0:F2} MB）...");
        var itemRoot = Path.Combine(targetRoot, workshopId.ToString());
        await downloader.DownloadWorkshopItemAsync(
            session, SteamAppCatalog.TModLoaderAppId, item.ContentHandle, itemRoot).ConfigureAwait(false);
        if (options.ModsDir is { } modsDir)
        {
            Directory.CreateDirectory(modsDir);
            foreach (var file in Directory.EnumerateFiles(itemRoot, "*.tmod", SearchOption.AllDirectories))
            {
                var destination = Path.Combine(modsDir, Path.GetFileName(file));
                File.Copy(file, destination, overwrite: true);
                Emit("status", $"已安装 {Path.GetFileName(destination)} -> {modsDir}");
            }
        }
        Emit("done", null);
        return 0;
    }

    return await RunGameContentAsync(options).ConfigureAwait(false);
}
catch (SteamAuthenticationException ex)
{
    Emit("error", $"Steam 登录失败：{ex.ResultName}");
    return 3;
}
catch (SteamKit2.Authentication.AuthenticationException ex)
{
    Emit("error", $"Steam 登录失败：{ex.Result}");
    return 3;
}
catch (Exception ex)
{
    Emit("error", ex.ToString());
    return 4;
}
}

async Task<int> RunGameContentAsync(CliOptions options)
{
    var spec = ResolveSpec(options.App);
    if (spec is null)
    {
        Emit("error", $"未知的 app：{options.App}（可用 --list 查看）");
        return 1;
    }

    if (options.GameRoot is null)
    {
        Emit("error", "缺少 --game-root");
        return 1;
    }

    spec = spec with
    {
        Branch = options.Branch ?? spec.Branch,
        Platform = options.Platform ?? spec.Platform,
    };

    var credentialsPath = options.CredentialsPath
        ?? Path.Combine(options.GameRoot, "steam-content-credentials.json");
    var store = new FileSteamCredentialStore(credentialsPath);
    var guardProvider = new HostGuardProvider(options.GuardCode, options.GuardFile);
    try
    {
        var credentials = options.Anonymous ? null : await store.LoadAsync().ConfigureAwait(false);
        if (!options.Anonymous && options.Account is not null && options.Password is not null)
        {
            Emit("status", "登录 Steam...");
            using var login = new SteamLoginService(options.Proxy);
            var result = await login.LoginAsync(options.Account, options.Password, guardData: null, guardProvider)
                .ConfigureAwait(false);
            await store.SaveAsync(result.Credentials).ConfigureAwait(false);
            credentials = result.Credentials;
            Emit("status", "Steam 登录成功（已保存 refresh token）");
        }

        if (!options.Anonymous && credentials is null)
        {
            Emit("error", "没有可用的 Steam 凭据：请提供 --account/--password 或先登录一次");
            return 3;
        }

        using var session = credentials is null ? new SteamSession() : new SteamSession(credentials);
        Emit("status", "连接 Steam...");
        await session.ConnectAsync().ConfigureAwait(false);

        if (options.LoginOnly)
        {
            Emit("status", "登录成功，凭据已保存");
            Emit("done", null, owned: true);
            return 0;
        }

        if (session.IsAnonymous)
        {
            Emit("error", $"下载 {spec.Name} 需要登录 Steam 账号（匿名拿不到 license）");
            return 2;
        }

        Emit("status", $"验证 {spec.Name}（App {spec.AppId}）拥有状态...");
        if (!await session.VerifyOwnershipAsync(spec.AppId).ConfigureAwait(false))
        {
            Emit("error", $"该 Steam 账号没有 {spec.Name}（App {spec.AppId}）" +
                $"  [ownership={session.LastOwnershipResult}]");
            return 2;
        }

        if (options.VerifyOnly)
        {
            Emit("done", null, owned: true);
            return 0;
        }

        var downloader = new SteamAppDownloader(
            progress => Emit("progress", progress.CurrentFile, progress: progress),
            message => Emit("status", message),
            skipDepots: options.SkipDepots,
            branchPassword: options.BranchPassword,
            betaCode: options.BetaCode);
        Emit("status", $"开始下载 {spec.Name}（{spec.Branch}, {spec.PlatformLabel}）到 {options.GameRoot}");
        await downloader.DownloadAsync(session, spec, options.GameRoot).ConfigureAwait(false);
        Emit("done", null);
        return 0;
    }
    catch (SteamAuthenticationException ex)
    {
        try { await store.ClearAsync().ConfigureAwait(false); } catch (Exception) { }
        throw new SteamAuthenticationException(ex.Result);
    }
}

/// <summary>解析逗号分隔的 depot id 列表（--skip-depot 1281931,1281932）</summary>
static void ParseDepotIds(string value, HashSet<uint> target)
{
    foreach (var part in value.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
    {
        if (uint.TryParse(part, out var depotId))
            target.Add(depotId);
        else
            Console.WriteLine($"{{\"type\":\"status\",\"message\":\"忽略无法解析的 depot id: {part}\"}}");
    }
}

static SteamAppSpec? ResolveSpec(string? app)
{
    if (string.IsNullOrWhiteSpace(app))
        return SteamAppCatalog.Find(SteamAppCatalog.TerrariaAppId);
    if (uint.TryParse(app, out var appId))
        return SteamAppCatalog.Find(appId);
    return SteamAppCatalog.Find(app);
}

void EmitWorkshop(IReadOnlyList<SteamWorkshopItem> items)
{
    var payload = new Dictionary<string, object?>
    {
        ["type"] = "workshop",
        ["items"] = items.Select(item => new Dictionary<string, object?>
        {
            ["publishedFileId"] = item.PublishedFileId,
            ["title"] = item.Title,
            ["description"] = item.Description.Length > 300 ? item.Description[..300] : item.Description,
            ["fileName"] = item.FileName,
            ["fileSize"] = item.FileSize,
            ["previewUrl"] = item.PreviewUrl,
            ["timeUpdated"] = item.TimeUpdated,
            ["subscriptions"] = item.Subscriptions,
            ["favorited"] = item.Favorited,
            ["votesUp"] = item.VotesUp,
            ["tags"] = item.Tags,
        }).ToList(),
    };
    Emitter.WriteLine(JsonSerializer.Serialize(payload));
}

void Emit(string type, string? message, SteamDownloadProgress? progress = null, bool? owned = null, SteamAppSpec? app = null, Dictionary<string, object?>? branchPayload = null)
{
    var payload = new Dictionary<string, object?>();
    if (type == "progress" && progress is not null)
    {
        payload["type"] = "progress";
        payload["percent"] = Math.Round(progress.Percentage, 2);
        payload["downloaded"] = progress.DownloadedBytes;
        payload["total"] = progress.TotalBytes;
        payload["files"] = $"{progress.CompletedFiles}/{progress.TotalFiles}";
        payload["current"] = progress.CurrentFile;
    }
    else if (type == "branch" && branchPayload is not null)
    {
        payload = branchPayload ?? new Dictionary<string, object?>();
        payload["type"] = "branch";
    }
    else if (type == "app" && app is not null)
    {
        payload["type"] = "app";
        payload["appId"] = app.AppId;
        payload["name"] = app.Name;
        payload["branch"] = app.Branch;
        payload["platform"] = app.Platform;
        payload["launchTarget"] = app.LaunchTarget;
        payload["notes"] = app.Notes;
    }
    else
    {
        payload["type"] = type;
        if (message is not null) payload["message"] = message;
        if (owned is not null) payload["owned"] = owned;
    }
    Emitter.WriteLine(JsonSerializer.Serialize(payload));
}

async Task<int> ServeAsync(string serveDir)
{
    Directory.CreateDirectory(serveDir);
    StartShimServer(serveDir);
    Emitter.Attach(Path.Combine(serveDir, "service.log.jsonl"));
    Emitter.WriteLine(JsonSerializer.Serialize(new Dictionary<string, object?> { ["type"] = "ready" }));
    var readyMarker = Path.Combine(serveDir, "service.ready");
    File.WriteAllText(readyMarker, Environment.ProcessId.ToString());
    Console.WriteLine($"[serve] 监听 {serveDir}");

    while (true)
    {
        string[] requests;
        try
        {
            requests = Directory.GetFiles(serveDir, "*.req");
        }
        catch (Exception)
        {
            Thread.Sleep(200);
            continue;
        }

        foreach (var requestFile in requests)
        {
            var id = Path.GetFileNameWithoutExtension(requestFile);
            var eventsPath = Path.Combine(serveDir, $"events-{id}.jsonl");
            var donePath = Path.Combine(serveDir, $"{id}.done");
            try
            {
                if (File.Exists(eventsPath)) File.Delete(eventsPath);
                var payload = JsonSerializer.Deserialize<JsonElement>(File.ReadAllText(requestFile));
                var requestArgs = payload.GetProperty("args").EnumerateArray()
                    .Select(item => item.GetString() ?? string.Empty).ToArray();
                Emitter.Attach(eventsPath);
                var requestOptions = ParseArgs(requestArgs);
                var code = await ExecuteAsync(requestOptions).ConfigureAwait(false);
                File.WriteAllText(donePath, code.ToString());
                if (code != 0)
                {
                    try { File.Copy(eventsPath, Path.Combine(serveDir, "last-events.jsonl"), overwrite: true); } catch { }
                }
            }
            catch (Exception ex)
            {
                try { File.WriteAllText(donePath, "4"); } catch { }
                try { File.WriteAllText(Path.Combine(serveDir, "last-error.txt"), ex.ToString()); } catch { }
                try { Emitter.WriteLine(JsonSerializer.Serialize(new Dictionary<string, object?> { ["type"] = "error", ["message"] = ex.ToString() })); } catch { }
            }
            finally
            {
                try { File.Delete(requestFile); } catch { }
            }
        }

        Thread.Sleep(150);
    }
}

// 游戏侧 Steamworks shim 通过本地 TCP 调这个端点（成就/云存档/身份）
void StartShimServer(string serveDir)
{
    var accountDir = Path.Combine(serveDir, "account");
    Directory.CreateDirectory(accountDir);
    var listener = new System.Net.Sockets.TcpListener(System.Net.IPAddress.Loopback, 0);
    listener.Start();
    var port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port;
    File.WriteAllText(Path.Combine(serveDir, "shim-port.txt"), port.ToString());
    Console.WriteLine($"[shim] 监听 127.0.0.1:{port}");

    var thread = new Thread(() =>
    {
        while (true)
        {
            try
            {
                var client = listener.AcceptTcpClient();
                var worker = new Thread(() => HandleShimClient(client, accountDir));
                worker.IsBackground = true;
                worker.Start();
            }
            catch (Exception ex)
            {
                Console.WriteLine("[shim] accept 失败: " + ex.Message);
            }
        }
    }) { IsBackground = true };
    thread.Start();
}

void HandleShimClient(System.Net.Sockets.TcpClient client, string accountDir)
{
    try
    {
        using (client)
        using (var stream = client.GetStream())
        using (var reader = new StreamReader(stream, System.Text.Encoding.UTF8))
        using (var writer = new StreamWriter(stream, System.Text.Encoding.UTF8) { AutoFlush = true })
        {
            string? line;
            while ((line = reader.ReadLine()) != null)
            {
                if (string.IsNullOrWhiteSpace(line)) continue;
                string response;
                try
                {
                    var request = JsonSerializer.Deserialize<JsonElement>(line);
                    response = ShimCommand(request, accountDir);
                }
                catch (Exception ex)
                {
                    response = JsonSerializer.Serialize(new Dictionary<string, object?> { ["ok"] = false, ["error"] = ex.Message });
                }
                writer.WriteLine(response);
            }
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine("[shim] 客户端异常: " + ex.Message);
    }
}

string ShimCommand(JsonElement request, string accountDir)
{
    var cmd = request.TryGetProperty("cmd", out var c) ? c.GetString() : null;
    switch (cmd)
    {
        case "identity":
        {
            var credPath = Path.Combine(accountDir, "identity.json");
            if (!File.Exists(credPath)) return JsonSerializer.Serialize(new Dictionary<string, object?> { ["ok"] = false, ["error"] = "no identity" });
            return File.ReadAllText(credPath);
        }
        case "achievement.set":
        {
            var name = request.TryGetProperty("name", out var n) ? n.GetString() : null;
            if (string.IsNullOrEmpty(name)) return "{\"ok\":false}";
            AppendAchievement(accountDir, name);
            return "{\"ok\":true}";
        }
        case "achievement.list":
        {
            var file = Path.Combine(accountDir, "achievements.txt");
            var names = File.Exists(file) ? File.ReadAllLines(file) : Array.Empty<string>();
            return JsonSerializer.Serialize(new Dictionary<string, object?> { ["ok"] = true, ["achievements"] = names });
        }
        case "cloud.put":
        {
            var file = request.TryGetProperty("file", out var f) ? f.GetString() : null;
            var data = request.TryGetProperty("data", out var d) ? d.GetString() : null;
            if (string.IsNullOrEmpty(file) || data is null) return "{\"ok\":false}";
            var path = Path.Combine(accountDir, "cloud", file.Replace('\\', '/'));
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            File.WriteAllBytes(path, Convert.FromBase64String(data));
            return "{\"ok\":true}";
        }
        case "cloud.get":
        {
            var file = request.TryGetProperty("file", out var f2) ? f2.GetString() : null;
            if (string.IsNullOrEmpty(file)) return "{\"ok\":false}";
            var path = Path.Combine(accountDir, "cloud", file.Replace('\\', '/'));
            if (!File.Exists(path)) return "{\"ok\":true,\"exists\":false}";
            var bytes = File.ReadAllBytes(path);
            return JsonSerializer.Serialize(new Dictionary<string, object?> {
                ["ok"] = true, ["exists"] = true, ["data"] = Convert.ToBase64String(bytes) });
        }
        case "cloud.list":
        {
            var root = Path.Combine(accountDir, "cloud");
            var files = Directory.Exists(root)
                ? Directory.GetFiles(root, "*", SearchOption.AllDirectories)
                    .Select(p => p.Substring(root.Length + 1).Replace('\\', '/')).ToArray()
                : Array.Empty<string>();
            return JsonSerializer.Serialize(new Dictionary<string, object?> { ["ok"] = true, ["files"] = files });
        }
        default:
            return JsonSerializer.Serialize(new Dictionary<string, object?> { ["ok"] = false, ["error"] = "unknown cmd " + cmd });
    }
}

void AppendAchievement(string accountDir, string name)
{
    var file = Path.Combine(accountDir, "achievements.txt");
    var existing = File.Exists(file) ? File.ReadAllLines(file) : Array.Empty<string>();
    if (existing.Contains(name)) return;
    File.AppendAllText(file, name + "\n");
}

static CliOptions ParseArgs(string[] args)
{
    var options = new CliOptions();
    var index = 0;
    while (index < args.Length)
    {
        var arg = args[index];
        string TakeValue()
        {
            if (index + 1 >= args.Length)
                throw new ArgumentException($"缺少参数值：{arg}");
            return args[++index];
        }

        switch (arg)
        {
            case "--game-root": options.GameRoot = TakeValue(); break;
            case "--app": options.App = TakeValue(); break;
            case "--branch": options.Branch = TakeValue(); break;
            case "--platform": options.Platform = TakeValue(); break;
            case "--skip-depot": ParseDepotIds(TakeValue(), options.SkipDepots); break;
            case "--list-branches": options.ListBranches = true; break;
            case "--branch-password": options.BranchPassword = TakeValue(); break;
            case "--beta-code": options.BetaCode = TakeValue(); break;
            case "--dump-depots": options.DumpDepots = true; break;
            case "--credentials": options.CredentialsPath = TakeValue(); break;
            case "--account": options.Account = TakeValue(); break;
            case "--password": options.Password = TakeValue(); break;
            case "--guard": options.GuardCode = TakeValue(); break;
            case "--guard-file": options.GuardFile = TakeValue(); break;
            case "--proxy": options.Proxy = new Uri(TakeValue()); break;
            case "--events": options.EventsPath = TakeValue(); break;
            case "--serve": options.ServeDir = TakeValue(); break;
            case "--verify-only": options.VerifyOnly = true; break;
            case "--anonymous": options.Anonymous = true; break;
            case "--login-only": options.LoginOnly = true; break;
            case "--identity": options.IdentityOut = TakeValue(); break;
            case "--list": options.ListApps = true; break;
            case "--workshop-list": options.WorkshopList = true; break;
            case "--workshop-download": options.WorkshopDownloadId = ulong.Parse(TakeValue()); break;
            case "--workshop-out": options.WorkshopOutDir = TakeValue(); break;
            case "--mods-dir": options.ModsDir = TakeValue(); break;
            case "--query": options.Query = TakeValue(); break;
            case "--page": options.Page = uint.Parse(TakeValue()); break;
            case "--count": options.Count = uint.Parse(TakeValue()); break;
            case "--help":
            case "-h":
                Console.WriteLine("SteamContentCli --game-root <dir> [--app <appid|name>] [--branch <b>] [--platform linux]");
                Console.WriteLine("               [--workshop-list [--query q]] [--workshop-download <id> --workshop-out <dir>]");
                Console.WriteLine("               [--events <file>] [--guard-file <file>] [--anonymous] [--list]");
                Environment.Exit(0);
                break;
        }
        index++;
    }
    return options;
}

internal sealed class CliOptions
{
    public string? App { get; set; }
    public string? Branch { get; set; }
    public string? Platform { get; set; }
    public bool ListApps { get; set; }
    public bool WorkshopList { get; set; }
    public ulong? WorkshopDownloadId { get; set; }
    public string? WorkshopOutDir { get; set; }
    public string? ModsDir { get; set; }
    public string? Query { get; set; }
    public uint Page { get; set; }
    public uint Count { get; set; } = 20;
    public string? GameRoot { get; set; }
    public string? CredentialsPath { get; set; }
    public string? Account { get; set; }
    public string? Password { get; set; }
    public string? GuardCode { get; set; }
    public string? GuardFile { get; set; }
    public string? EventsPath { get; set; }
    public string? ServeDir { get; set; }
    public Uri? Proxy { get; set; }
    public bool VerifyOnly { get; set; }
    public bool Anonymous { get; set; }
    public bool LoginOnly { get; set; }
    public string? IdentityOut { get; set; }

    /// <summary>不下载的 depot（tModLoader 的共享内容 depot 就属于这种）</summary>
    public HashSet<uint> SkipDepots { get; } = new();

    /// <summary>列出 app 的 Steam 分支（beta 分支的密码由 Steam 侧校验，必须原样传回去）</summary>
    public bool ListBranches { get; set; }

    /// <summary>受保护 beta 分支的密码（对应 PICS 里的 branches.<name>.password）</summary>
    public string? BranchPassword { get; set; }

    /// <summary>用 beta 密码向 Steam 换隐藏分支（IClientApps.CheckAppBetaPassword）</summary>
    public string? BetaCode { get; set; }

    /// <summary>调试：把 depots 段里所有 manifest 分支名打出来</summary>
    public bool DumpDepots { get; set; }
}

/// <summary>事件输出：stdout + 可选事件文件（宿主进程内运行、拿不到 stdout 时用）</summary>
internal static class Emitter
{
    private static readonly object Gate = new();
    private static string? _path;

    public static void Attach(string? path) => _path = path;

    public static void WriteLine(string line)
    {
        Console.WriteLine(line);
        Console.Out.Flush();
        if (_path is null) return;
        lock (Gate)
        {
            try { File.AppendAllText(_path, line + "\n"); } catch { }
        }
    }
}

/// <summary>Steam Guard 验证码：--guard 直接给，--guard-file 轮询宿主写入，否则读 stdin</summary>
internal sealed class HostGuardProvider(string? code, string? guardFile) : ISteamGuardCodeProvider
{
    public async Task<string> GetCodeAsync(bool previousCodeWasIncorrect, CancellationToken cancellationToken)
    {
        if (!string.IsNullOrWhiteSpace(code) && !previousCodeWasIncorrect)
            return code;

        if (!string.IsNullOrWhiteSpace(guardFile))
        {
            Emitter.WriteLine(JsonSerializer.Serialize(new Dictionary<string, object?>
            {
                ["type"] = "need-guard",
                ["message"] = previousCodeWasIncorrect ? "验证码错误，请重新输入 Steam Guard 代码" : "请输入 Steam Guard 代码",
            }));
            var deadline = DateTime.UtcNow.AddMinutes(10);
            while (DateTime.UtcNow < deadline)
            {
                cancellationToken.ThrowIfCancellationRequested();
                try
                {
                    if (File.Exists(guardFile))
                    {
                        var text = (await File.ReadAllTextAsync(guardFile, cancellationToken).ConfigureAwait(false)).Trim();
                        if (text.Length > 0)
                        {
                            File.Delete(guardFile);
                            return text;
                        }
                    }
                }
                catch (IOException) { }
                await Task.Delay(300, cancellationToken).ConfigureAwait(false);
            }
            throw new TimeoutException("等待 Steam Guard 验证码超时");
        }

        Emitter.WriteLine(JsonSerializer.Serialize(new Dictionary<string, object?>
        {
            ["type"] = "need-guard",
            ["message"] = previousCodeWasIncorrect ? "验证码错误，请重新输入 Steam Guard 代码" : "请输入 Steam Guard 代码",
        }));
        var line = Console.ReadLine();
        return (line ?? string.Empty).Trim();
    }
}
