using SteamKit2;

namespace TmlAndroidContent.Steam;

/// <summary>
/// 一个可下载的 Steam app 的描述。只描述"下载哪个分支的哪些 depot、装完用哪个 exe 启动"，
/// 具体 depot 清单运行时从 PICS 现查（分支会更新，写死会失效）。
/// </summary>
public sealed record SteamAppSpec(
    uint AppId,
    string Name,
    string Branch,
    string Platform,
    string? LaunchTarget,
    string Notes)
{
    /// <summary>tModLoader 的两个分支语义差别很大：1.3-legacy = mono/net40（我们支持的），public = 1.4/net8。</summary>
    public bool IsLegacyBranch => Branch != "public";

    /// <summary>Linux depot 里的启动入口（多数是 apphost/脚本，实际托管程序集同名 .exe）。</summary>
    public string PlatformLabel => Platform switch
    {
        "linux" => "Linux",
        "macos" => "macOS",
        "windows" => "Windows",
        _ => Platform,
    };
}

public static class SteamAppCatalog
{
    /// <summary>
    /// Steamworks Common Redistributables（app 228980）的共享 depot。
    /// 它们在每个游戏下都会出现，内容是 vcredist/steamworks 运行时，装游戏时必须跳过。
    /// </summary>
    public static readonly IReadOnlySet<uint> SharedRedistDepots = new HashSet<uint>
    {
        228980, 228983, 228988, 228990, 229002, 229004, 229005, 229012, 229033,
    };

    public const uint TerrariaAppId = 105600;

    /// <summary>默认分支（无密码、所有账号可见）</summary>
    public const string DefaultBranch = "public";
    public const uint TModLoaderAppId = 1281930;

    /// <summary>
    /// 默认平台是 linux：Linux depot 里是托管程序集 + 内容，没有 Windows 那套 Steamworks exe 校验，
    /// 对我们（mono / FNA 运行时）更合适。
    /// </summary>
    public const string DefaultPlatform = "linux";

    public static IReadOnlyList<SteamAppSpec> All { get; } = new[]
    {
        new SteamAppSpec(TModLoaderAppId, "tModLoader", "1.3-legacy", DefaultPlatform, "tModLoader.exe",
            "1.3-legacy 是 mono/net40 + FNA（我们运行时支持）；public 是 1.4/net8。Linux depot 1281933"),
        new SteamAppSpec(224760, "FEZ", "public", DefaultPlatform, "FEZ.exe",
            "XNA 4.0 / .NET 4.0，FNA 可跑。Linux depot 224762，启动脚本 ./FEZ"),
        new SteamAppSpec(TerrariaAppId, "Terraria", "public", DefaultPlatform, "Terraria.exe",
            "Linux depot 105602（Terraria.bin.x86_64 + Content/ + 托管程序集）"),
        new SteamAppSpec(413150, "Stardew Valley", "public", DefaultPlatform, "Stardew Valley.exe",
            "1.6 起 .NET6 + MonoGame；Linux depot 413153 启动脚本 StardewValley"),
        new SteamAppSpec(504230, "Celeste", "public", DefaultPlatform, "Celeste.exe",
            "XNA 4.0 / .NET 4.0；Linux depot 504233 启动脚本 ./Celeste"),
    };

    public static SteamAppSpec? Find(uint appId) => All.FirstOrDefault(a => a.AppId == appId);

    public static SteamAppSpec? Find(string name) =>
        All.FirstOrDefault(a => string.Equals(a.Name, name, StringComparison.OrdinalIgnoreCase));

    /// <summary>
    /// 从 PICS 的 depots 段里挑出该分支、Windows 平台、非共享运行时的 depot 及 manifest gid。
    /// 分支不存在时回退到 public。
    /// </summary>
    /// <summary>
    /// 只按平台挑 depot id（不看分支 manifest）。私有 beta 的 depot 段往往缺 oslist，
    /// 用公开 appinfo 算出来的这套 id 去筛，避免把 Windows/macOS 的 depot 也下下来。
    /// </summary>
    public static HashSet<uint> SelectDepotIds(KeyValue depotSection, string platform)
    {
        var result = new HashSet<uint>();
        if (depotSection == KeyValue.Invalid) return result;
        foreach (var depot in depotSection.Children)
        {
            if (!uint.TryParse(depot.Name, out var depotId)) continue;
            if (SharedRedistDepots.Contains(depotId)) continue;
            var config = depot["config"];
            var osList = config == KeyValue.Invalid ? null : config["oslist"].Value;
            if (!string.IsNullOrWhiteSpace(osList) &&
                !osList.Contains(platform, StringComparison.OrdinalIgnoreCase))
                continue;
            result.Add(depotId);
        }
        return result;
    }

    public static List<(uint DepotId, ulong ManifestId)> SelectDepots(
        KeyValue depotSection,
        string branch,
        string platform)
    {
        var result = new List<(uint, ulong)>();
        if (depotSection == KeyValue.Invalid)
            return result;

        foreach (var depot in depotSection.Children)
        {
            if (!uint.TryParse(depot.Name, out var depotId))
                continue;
            if (SharedRedistDepots.Contains(depotId))
                continue;

            var config = depot["config"];
            var osList = config == KeyValue.Invalid ? null : config["oslist"].Value;
            // 有 oslist 时只取目标平台；没有 oslist 的平台无关 depot（共享内容）照收
            if (!string.IsNullOrWhiteSpace(osList) &&
                !osList.Contains(platform, StringComparison.OrdinalIgnoreCase))
                continue;

            var gid = depot["manifests"][branch]["gid"].Value;
            if (!ulong.TryParse(gid, out var manifestId))
            {
                // 只对 public 兜底。其它分支（尤其受密码保护的 beta）绝不能悄悄退回 public，
                // 否则会「下载成功但装的是正式版」，排查起来极难。
                if (!string.Equals(branch, DefaultBranch, StringComparison.OrdinalIgnoreCase))
                    continue;
                gid = depot["manifests"]["public"]["gid"].Value;
            }
            if (ulong.TryParse(gid, out manifestId))
                result.Add((depotId, manifestId));
        }

        return result;
    }
}
