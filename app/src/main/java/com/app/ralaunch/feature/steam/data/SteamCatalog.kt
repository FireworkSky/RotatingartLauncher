package com.app.ralaunch.feature.steam.data

/**
 * 支持从 Steam 下载的游戏清单，与 steam-content/TmlAndroidContent/Steam/SteamAppCatalog.cs 保持一致。
 * depot 清单运行时由 SteamContentCli 从 PICS 现查，这里只描述分支与启动入口。
 */
/** 可选分支；secretCode 非空表示要输入秘密代码才显示 */
data class SteamBranch(
    val id: String,
    val label: String,
    val secretCode: String? = null,
)

data class SteamGame(
    val appId: Int,
    val name: String,
    val branch: String,
    val platform: String,
    /** 下载完成后 game_info.json 里的启动入口 */
    val launchTarget: String,
    /** 需要覆盖到已安装 Terraria 目录（tModLoader 1.3 就是这种） */
    val requiresTerraria: Boolean = false,
    /** 可选分支（空表示只有默认分支）；下载时选择的分支会覆盖同 gameId 的旧安装 */
    val branches: List<SteamBranch> = emptyList(),
    val notes: String = "",
)

object SteamCatalog {
    const val TERRARIA_APP_ID = 105600
    const val TMODLOADER_APP_ID = 1281930

    val all: List<SteamGame> = listOf(
        SteamGame(
            appId = TMODLOADER_APP_ID,
            name = "tModLoader",
            branch = "1.3-legacy",
            platform = "linux",
            launchTarget = "TML13Launcher.exe",
            requiresTerraria = true,
            notes = "1.3：tModLoader.exe 与 Terraria.exe 同级（与 GOG 安装同一布局，mono + 包装启动器）；" +
                "1.4：装到与 Terraria 同级的 tModLoader 目录（tModLoader.dll + .NET 10）",
            branches = listOf(
                SteamBranch("public", "1.4 正式版"),
                SteamBranch("1.3-legacy", "1.3 经典版"),
                SteamBranch("1.4.3-legacy", "1.4.3 旧版"),
                SteamBranch("preview-v2026.09", "1.4.5 测试版", secretCode = "iamacontributor"),
            ),
        ),
        SteamGame(
            appId = 224760,
            name = "FEZ",
            branch = "public",
            platform = "linux",
            launchTarget = "FEZ.exe",
            notes = "XNA 4.0 / .NET 4.0",
        ),
        SteamGame(
            appId = TERRARIA_APP_ID,
            name = "Terraria",
            branch = "public",
            platform = "linux",
            launchTarget = "Terraria.exe",
            notes = "Linux depot；exe 任意版本都能被 tModLoader 包装器拉起",
        ),
        SteamGame(
            appId = 413150,
            name = "Stardew Valley",
            branch = "public",
            platform = "linux",
            launchTarget = "Stardew Valley.exe",
            notes = "1.6 起 .NET6 + MonoGame",
        ),
        SteamGame(
            appId = 504230,
            name = "Celeste",
            branch = "public",
            platform = "linux",
            launchTarget = "Celeste.exe",
            notes = "XNA 4.0 / .NET 4.0",
        ),
    )

    fun find(appId: Int): SteamGame? = all.firstOrNull { it.appId == appId }
}
