// RAL Steam 内容下载宿主
//
// 用法（RAL 安装流程调用，stdout 每行一个 JSON 事件）：
//   dotnet SteamContentCli.dll --game-root <游戏目录> --credentials <凭据文件>
//        [--account <账号> --password <密码>] [--guard <验证码>] [--proxy <url>]
//        [--verify-only]
//
// 凭据文件只存 refresh token + GuardData，不存密码；首次登录用账号密码，
// 之后直接复用 refresh token。
//
// 事件：{"type":"status","message":...}
//       {"type":"progress","percent":..,"downloaded":..,"total":..,"files":..,"current":..}
//       {"type":"done"}
//       {"type":"error","message":...}
using System.Text.Json;
using TmlAndroidContent.Steam;

var options = ParseArgs(args);

if (options.ListApps)
{
    foreach (var app in SteamAppCatalog.All)
        Console.WriteLine(JsonSerializer.Serialize(new Dictionary<string, object?>
        {
            ["appId"] = app.AppId,
            ["name"] = app.Name,
            ["branch"] = app.Branch,
            ["launchTarget"] = app.WindowsLaunchTarget,
            ["notes"] = app.Notes,
        }));
    return 0;
}

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

// --branch 覆盖默认分支（例如 tModLoader 的 1.3-legacy / public）
var branch = options.Branch ?? spec.Branch;
spec = spec with { Branch = branch };

var credentialsPath = options.CredentialsPath
    ?? Path.Combine(options.GameRoot, "steam-content-credentials.json");
var store = new FileSteamCredentialStore(credentialsPath);
var guardProvider = new ConsoleGuardProvider(options.GuardCode);

try
{
    var credentials = await store.LoadAsync().ConfigureAwait(false);
    if (options.Account is not null && options.Password is not null)
    {
        credentials = await LoginAsync(options, store, guardProvider).ConfigureAwait(false);
    }

    if (credentials is null)
    {
        Emit("error", "没有可用的 Steam 凭据：请提供 --account/--password 或先登录一次");
        return 3;
    }

    using var session = new SteamSession(credentials);
    Emit("status", "连接 Steam...");
    await session.ConnectAsync().ConfigureAwait(false);

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

    Emit("status", $"开始下载 {spec.Name}（{spec.Branch}）到 {options.GameRoot}");
    await downloader.DownloadAsync(session, spec, options.GameRoot).ConfigureAwait(false);

    Emit("done", null);
    return 0;
}
catch (SteamAuthenticationException ex)
{
    try { await store.ClearAsync().ConfigureAwait(false); } catch (Exception) { }
    Emit("error", $"Steam 登录失败：{ex.ResultName}");
    return 3;
}
catch (Exception ex)
{
    Emit("error", ex.ToString());
    return 4;
}

static async Task<SteamCredentials> LoginAsync(CliOptions options, FileSteamCredentialStore store, ISteamGuardCodeProvider guard)
{
    Emit("status", "登录 Steam...");
    using var login = new SteamLoginService(options.Proxy);
    var result = await login.LoginAsync(options.Account!, options.Password!, guardData: null, guard).ConfigureAwait(false);
    await store.SaveAsync(result.Credentials).ConfigureAwait(false);
    Emit("status", "Steam 登录成功（已保存 refresh token）");
    return result.Credentials;
}

static void Emit(string type, string? message, SteamDownloadProgress? progress = null, bool? owned = null)
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
    else
    {
        payload["type"] = type;
        if (message is not null) payload["message"] = message;
        if (owned is not null) payload["owned"] = owned;
    }
    Console.WriteLine(JsonSerializer.Serialize(payload));
    Console.Out.Flush();
}

static SteamAppSpec? ResolveSpec(string? app)
{
    if (string.IsNullOrWhiteSpace(app))
        return SteamAppCatalog.Find(SteamAppCatalog.TerrariaAppId);
    if (uint.TryParse(app, out var appId))
        return SteamAppCatalog.Find(appId);
    return SteamAppCatalog.Find(app);
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
            case "--list": options.ListApps = true; break;
            case "--credentials": options.CredentialsPath = TakeValue(); break;
            case "--account": options.Account = TakeValue(); break;
            case "--password": options.Password = TakeValue(); break;
            case "--guard": options.GuardCode = TakeValue(); break;
            case "--proxy": options.Proxy = new Uri(TakeValue()); break;
            case "--verify-only": options.VerifyOnly = true; break;
            case "--help":
            case "-h":
                Console.WriteLine("SteamContentCli --game-root <dir> [--app <appid|name>] [--branch <branch>] [--credentials <file>]");
                Console.WriteLine("               [--account <user> --password <pass>] [--guard <code>] [--proxy <url>] [--verify-only] [--list]");
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
    public bool ListApps { get; set; }
    public string? GameRoot { get; set; }
    public string? CredentialsPath { get; set; }
    public string? Account { get; set; }
    public string? Password { get; set; }
    public string? GuardCode { get; set; }
    public Uri? Proxy { get; set; }
    public bool VerifyOnly { get; set; }
}

/// <summary>Steam Guard 验证码：优先 --guard，否则从 stdin 读一行</summary>
internal sealed class ConsoleGuardProvider(string? code) : ISteamGuardCodeProvider
{
    public Task<string> GetCodeAsync(bool previousCodeWasIncorrect, CancellationToken cancellationToken)
    {
        if (!string.IsNullOrWhiteSpace(code) && !previousCodeWasIncorrect)
            return Task.FromResult(code);
        Console.WriteLine(JsonSerializer.Serialize(new Dictionary<string, object?>
        {
            ["type"] = "need-guard",
            ["message"] = previousCodeWasIncorrect ? "验证码错误，请重新输入 Steam Guard 代码" : "请输入 Steam Guard 代码",
        }));
        Console.Out.Flush();
        var line = Console.ReadLine();
        return Task.FromResult((line ?? string.Empty).Trim());
    }
}
