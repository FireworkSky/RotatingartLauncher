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
    string? WindowsLaunchTarget,
    string Notes)
{
    /// <summary>tModLoader 的两个分支语义差别很大：1.3-legacy = mono/net40（我们支持的），public = 1.4/net8。</summary>
    public bool IsLegacyBranch => Branch != "public";
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
    public const uint TModLoaderAppId = 1281930;

    public static IReadOnlyList<SteamAppSpec> All { get; } = new[]
    {
        new SteamAppSpec(TModLoaderAppId, "tModLoader", "1.3-legacy", "tModLoader.exe",
            "1.3-legacy 分支是 mono/net40 + FNA 的老版本（我们运行时支持）；public 分支是 1.4/net8"),
        new SteamAppSpec(224760, "FEZ", "public", "FEZ.exe",
            "XNA 4.0 / .NET 4.0，FNA 可跑；无 Steam DRM 依赖"),
        new SteamAppSpec(TerrariaAppId, "Terraria", "public", "Terraria.exe",
            "Steam 版 exe 带 Steamworks 校验，安装时保留 DRM-free exe（GOG）即可不碰它"),
        new SteamAppSpec(413150, "Stardew Valley", "public", "Stardew Valley.exe",
            "1.6 起是 .NET 6 + MonoGame，需要 .NET 运行时；1.5.6 分支是 net461"),
        new SteamAppSpec(504230, "Celeste", "public", "Celeste.exe",
            "XNA 4.0 / .NET 4.0，FNA 可跑"),
    };

    public static SteamAppSpec? Find(uint appId) => All.FirstOrDefault(a => a.AppId == appId);

    public static SteamAppSpec? Find(string name) =>
        All.FirstOrDefault(a => string.Equals(a.Name, name, StringComparison.OrdinalIgnoreCase));

    /// <summary>
    /// 从 PICS 的 depots 段里挑出该分支、Windows 平台、非共享运行时的 depot 及 manifest gid。
    /// 分支不存在时回退到 public。
    /// </summary>
    public static List<(uint DepotId, ulong ManifestId)> SelectDepots(KeyValue depotSection, string branch)
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
            if (!string.IsNullOrWhiteSpace(osList) &&
                !osList.Contains("windows", StringComparison.OrdinalIgnoreCase))
                continue;

            var gid = depot["manifests"][branch]["gid"].Value;
            if (!ulong.TryParse(gid, out var manifestId))
                gid = depot["manifests"]["public"]["gid"].Value;
            if (ulong.TryParse(gid, out manifestId))
                result.Add((depotId, manifestId));
        }

        return result;
    }
}
