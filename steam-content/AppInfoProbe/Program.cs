using SteamKit2;
using SteamKit2.CDN;
using SteamNetworking = TmlAndroidContent.Steam.SteamNetworking;

// 用法：
//   AppInfoProbe                      → 打印 5 个 app 的 depot / 分支 / 启动项
//   AppInfoProbe --files <appId> <branch> <depotId> → 打印该 depot 分支里的文件清单
// 匿名登录，只能对免费 app（tModLoader）成功取 depot key / manifest。

uint[] apps = { 1281930, 224760, 105600, 413150, 504230 };
var dumpMode = args.Length >= 1 && args[0] == "--dump";
var filesMode = args.Length >= 4 && args[0] == "--files";
uint filesAppId = filesMode ? uint.Parse(args[1]) : 0;
var filesBranch = filesMode ? args[2] : "";
uint filesDepotId = filesMode ? uint.Parse(args[3]) : 0;

var client = new SteamClient(SteamNetworking.Create());
var cb = new CallbackManager(client);
var user = client.GetHandler<SteamUser>()!;
var steamApps = client.GetHandler<SteamApps>()!;
var steamContent = client.GetHandler<SteamContent>()!;

var connected = new TaskCompletionSource();
var loggedOn = new TaskCompletionSource<SteamUser.LoggedOnCallback>();
cb.Subscribe<SteamClient.ConnectedCallback>(_ => { connected.TrySetResult(); user.LogOnAnonymous(); });
cb.Subscribe<SteamUser.LoggedOnCallback>(c => loggedOn.TrySetResult(c));

new Thread(() => { while (true) { try { cb.RunWaitCallbacks(TimeSpan.FromMilliseconds(200)); } catch { } } }) { IsBackground = true }.Start();

client.Connect();
await connected.Task.WaitAsync(TimeSpan.FromSeconds(30));
var logon = await loggedOn.Task.WaitAsync(TimeSpan.FromSeconds(40));
Console.Error.WriteLine($"[probe] logon={logon.Result}");

uint[] wantedIds = filesMode ? new[] { filesAppId } : apps;
var resultSet = await steamApps.PICSGetProductInfo(
    wantedIds.Select(id => new SteamApps.PICSRequest(id)).ToList(),
    new List<SteamApps.PICSRequest>());
var appsKV = resultSet.Results.FirstOrDefault()?.Apps;
if (appsKV is null) return 2;

if (dumpMode)
{
    foreach (var (appId, info) in appsKV)
    {
        var path = $"/data/data/com.termux/files/home/pics-{appId}.txt";
        info.KeyValues.SaveToFile(path, false);
        Console.WriteLine($"dumped {appId} -> {path}");
    }
    return 0;
}

if (filesMode)
{
    if (!appsKV.TryGetValue(filesAppId, out var info)) return 2;
    var depotKV = info.KeyValues["depots"];
    var manifestGidText = depotKV[filesDepotId.ToString()]["manifests"][filesBranch]["gid"].Value;
    if (!ulong.TryParse(manifestGidText, out var manifestGid))
    {
        Console.Error.WriteLine($"[files] no manifest for depot {filesDepotId} branch {filesBranch}");
        return 6;
    }

    var keyResp = await steamApps.GetDepotDecryptionKey(filesDepotId, filesAppId);
    Console.Error.WriteLine($"[files] depotKey={keyResp.Result}");
    if (keyResp.Result != EResult.OK) return 5;

    var requestCode = await steamContent.GetManifestRequestCode(filesDepotId, filesAppId, manifestGid, filesBranch);
    Console.Error.WriteLine($"[files] manifest={manifestGid} requestCode={requestCode != 0}");

    var servers = (await ContentServerDirectoryService.LoadAsync(client.Configuration))
        .Where(s => s.Type is "CDN" or "SteamCache")
        .OrderBy(s => s.WeightedLoad).ToList();
    var manifest = await new Client(client).DownloadManifestAsync(
        filesDepotId, manifestGid, requestCode, servers[0], keyResp.DepotKey);
    Console.WriteLine($"[files] {manifest.Files?.Count ?? 0} files");
    foreach (var f in manifest.Files ?? [])
        Console.WriteLine($"  {(f.Flags.HasFlag(EDepotFileFlag.Directory) ? "D" : "F")} {f.TotalSize,12} {f.FileName}");
    return 0;
}

foreach (var appId in apps)
{
    if (!appsKV.TryGetValue(appId, out var app)) { Console.WriteLine($"== {appId}: MISSING"); continue; }
    var kv = app.KeyValues;
    Console.WriteLine($"== {appId} {kv["common"]["name"].AsString()}");
    foreach (var l in kv["config"]["launch"].Children)
        Console.WriteLine($"  launch exe={l["executable"].AsString()} os={l["config"]["oslist"].AsString()}");
    foreach (var d in kv["depots"].Children)
    {
        if (!uint.TryParse(d.Name, out var depotId)) continue;
        var oslist = d["config"]["oslist"].AsString();
        var manifests = d["manifests"];
        var branches = manifests == KeyValue.Invalid
            ? ""
            : string.Join(" ", manifests.Children.Select(m => $"{m.Name}={m["gid"].AsString()}"));
        Console.WriteLine($"  depot {depotId} os='{oslist}' {branches}");
    }
}
return 0;
