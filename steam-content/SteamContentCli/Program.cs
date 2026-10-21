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
            Emit("error", $"该 Steam 账号没有 {spec.Name}（App {spec.AppId}）");
            return 2;
        }

        if (options.VerifyOnly)
        {
            Emit("done", null, owned: true);
            return 0;
        }

        var downloader = new SteamAppDownloader(
            progress => Emit("progress", progress.CurrentFile, progress: progress),
            message => Emit("status", message));
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

void Emit(string type, string? message, SteamDownloadProgress? progress = null, bool? owned = null, SteamAppSpec? app = null)
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
            }
            catch (Exception ex)
            {
                try { File.WriteAllText(donePath, "4"); } catch { }
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
