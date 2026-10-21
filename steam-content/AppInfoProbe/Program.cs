using SteamKit2;
using TmlAndroidContent.Steam;
using SteamNetworking = TmlAndroidContent.Steam.SteamNetworking;
static void Log(string m) { try { File.AppendAllText(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "probe-trace.log"), $"{DateTime.Now:HH:mm:ss.fff} {m}\n"); } catch { } }
Log("start");

// 5 个目标 app
int[] apps = { 1281930, 224760, 105600, 413150, 504230 };

var cfg = SteamNetworking.Create();
var client = new SteamClient(cfg);
var cb = new CallbackManager(client);
var user = client.GetHandler<SteamUser>()!;
var steamApps = client.GetHandler<SteamApps>()!;

var loggedOn = new TaskCompletionSource<SteamUser.LoggedOnCallback>();
cb.Subscribe<SteamClient.ConnectedCallback>(_ => { Console.Error.WriteLine("[probe] connected, logging on"); user.LogOnAnonymous(); });
cb.Subscribe<SteamUser.LoggedOnCallback>(c => { Log($"loggedon {c.Result}"); Console.WriteLine($"[probe] LoggedOn result={c.Result} extended={c.ExtendedResult} msg={c.ExtendedResult}"); Console.Out.Flush(); loggedOn.TrySetResult(c); });
cb.Subscribe<SteamClient.DisconnectedCallback>(_ => Console.Error.WriteLine("[probe] disconnected"));

var pump = new Thread(() => { while (true) { try { cb.RunWaitCallbacks(TimeSpan.FromMilliseconds(200)); } catch (Exception e) { Log("pump EX " + e); } } }) { IsBackground = true };
pump.Start();

Log("connecting"); client.Connect(); Log("connect called");
SteamUser.LoggedOnCallback logon;
try { logon = await loggedOn.Task.WaitAsync(TimeSpan.FromSeconds(40)); }
catch (TimeoutException) { Console.WriteLine("[probe] logon TIMEOUT"); return 3; }
Console.Error.WriteLine($"[probe] logon result={logon.Result}");
if (logon.Result != EResult.OK) return 1;

var reqs = apps.Select(id => new SteamApps.PICSRequest((uint)id)).ToList();
var resultSet = await steamApps.PICSGetProductInfo(reqs, new List<SteamApps.PICSRequest>());
var result = resultSet.Results.FirstOrDefault();
if (result?.Apps == null) { Console.Error.WriteLine("[probe] no PICS result"); return 2; }

foreach (var appId in apps)
{
    if (!result.Apps.TryGetValue((uint)appId, out var app)) { Console.WriteLine($"== {appId}: MISSING"); continue; }
    var kv = app.KeyValues;
    Console.WriteLine($"== {appId} {kv["common"]["name"].AsString()} changenumber={app.ChangeNumber}");
    var launch = kv["config"]["launch"];
    if (launch != KeyValue.Invalid)
        foreach (var l in launch.Children)
            Console.WriteLine($"  launch[{l.Name}] exe={l["executable"].AsString()} oslist={l["config"]["oslist"].AsString()} type={l["type"].AsString()}");
    var depots = kv["depots"];
    if (depots != KeyValue.Invalid)
        foreach (var d in depots.Children)
        {
            if (!int.TryParse(d.Name, out var depotId)) continue;
            var cfg2 = d["config"];
            var oslist = cfg2["oslist"].AsString();
            var lang = cfg2["language"].AsString();
            var branch = d["manifests"]["public"]["gid"].AsString();
            Console.WriteLine($"  depot {depotId} {d["name"].AsString()} os={oslist} lang={lang} public={branch} dlcapp={d["dlcappid"].AsString()} shared={cfg2["sharedinstall"].AsString()}");
        }
    var branches = kv["depots"]["branches"];
    if (branches != KeyValue.Invalid)
        foreach (var b in branches.Children) Console.WriteLine($"  branch {b.Name} buildid={b["buildid"].AsString()} pwd={(b["pwdrequired"].AsString())}");
}
return 0;
