# 支持从 Steam 下载的游戏（首批 5 个）

数据来自 `AppInfoProbe`（匿名 PICS 查询，2026-10 快照）。depot 清单运行时现查，这里只记录结构。

| App | 名称 | 默认分支 | 启动 exe | 内容 depot（Windows） | 备注 |
|---|---|---|---|---|---|
| 1281930 | tModLoader | `1.3-legacy` | `tModLoader.exe` | 1281932 (win) / 1281931 (公共) | `public` 分支是 1.4/net8；`1.3-legacy` 才是 mono/net40 + FNA。另有 `preview-v2026.*`、`1.4.3-legacy` 分支。免费 app，匿名即可取 token |
| 224760 | FEZ | `public` | `FEZ.exe` | 224761 (win) | 还有 `sdl3`、`beta` 分支；XNA4/.NET4 |
| 105600 | Terraria | `public` | `Terraria.exe` | 105601 (win) | 旧分支 `v1.3.0.8`/`v1.2.4.1`/`v1.1.2`/`v1.0.6.1`；Steam 版 exe 有 Steamworks 校验 |
| 413150 | Stardew Valley | `public` | `Stardew Valley.exe` | 413151 (win) | 分支 `legacy_1.5.6`(net461)、`legacy_1.6.8`、`previous_version`、`compatibility` |
| 504230 | Celeste | `public` | `Celeste.exe` | 504231 (win) | 分支 `beta-1.3.3.0`、`opengl`、`sdl3`、若干 `outdated-*` |

所有 app 下都会出现的共享 depot（**必须跳过**，是 Steamworks 运行时/redist，不是游戏内容）：
`228980 228983 228988 228990 229002 229004 229005 229012 229033`（Steamworks Common Redistributables，app 228980）。

## 下载机制

`SteamContentCli --app <appid|名称> [--branch <branch>] --game-root <dir>`：

1. `SteamSession.VerifyOwnershipAsync(appId)`：PICS access token 判拥有（免费 app 匿名也有 token，付费 app 需要账号持有 license）。
2. `SteamAppDownloader.DownloadAsync(session, spec, dir)`：按分支取 depot 与 manifest gid → 逐 depot 取 depot key → `GetManifestRequestCode` → 从 CDN 拉 manifest → 并发拉 chunk（LZMA 解压 + AES-128-CBC 解密 + SHA1 校验）→ 落盘到游戏目录，保留相对路径。
3. 完成标记 `.steam-content-<appid>.complete`。

## 各游戏 DRM / 可运行性

| App | Steam DRM | 我们的处理 |
|---|---|---|
| tModLoader | 无（1.3 是 mono/net40 + FNA；1.4 是 .NET8 启动器） | 直接可用；1.3 走 `1.3-legacy` 分支 |
| FEZ | 有 `steam_api.dll`（成就/云），但不校验 | 可用；缺 steam_api 时由 shim 兜底 |
| Terraria | **Steamworks 强校验**（`SteamAPI.Init()` 失败即退出） | 内容照下，exe 用完 GOG DRM-free 版（不替换用户已有 exe） |
| 星露谷 | 有 Steamworks.NET，1.6 起 .NET6 | 需 .NET 运行时；不校验时可直接跑 |
| Celeste | 有 Steamworks，不强制 | 可直接跑 |

需要"只有 Steam 版 exe"时的兜底：提供托管版 `Steamworks.NET.dll`（或 `steam_api64.dll` 的 dllmap）shim，
`Init()` 仅在账号 license 校验通过时返回 true，成就/云存档落到本地 —— 不碰 exe 本身。
