# steam-content（Steam 账号验证 + Terraria 内容下载）

来源：作者提供的 `android.7z`（TmlAndroidHost / TmlAndroidBootstrap / TmlAndroidContent）。
这里把与 RAL 真正相关的部分抽出来做成可直接调用的模块，并保留上游源码供对照。

## 它解决什么

Terraria 的 `Content/` 有版权，用户必须**自己拥有**才能用。这个模块让 RAL：

1. 用玩家的 Steam 账号登录（账号密码 + Steam Guard，之后只留 refresh token）；
2. 验证该账号拥有 **Terraria（App 105600）**；
3. 从 **Steam CDN** 下载公开 depot（`Content/` 等）到游戏目录，带并发/重试/进度/完成标记。

tModLoader 本体（App 1281930，`tModLoader.dll`）不在这里，走官方安装包/创意工坊那条路。

## 目录

| 目录 | 说明 |
|---|---|
| `TmlAndroidContent/` | SteamKit2 封装：`SteamLoginService`（登录 + Guard）、`SteamSession`（refresh token 登入、`PICSGetAccessTokens` 验拥有）、`SteamTerrariaContentDownloader`（CDN 多线程下载、`.tmodloader-content.complete` 标记）、`FileSteamCredentialStore`（非 Android 环境凭据落盘，权限 600） |
| `SteamContentCli/` | RAL 调用的宿主程序：参数 `--game-root/--account/--password/--guard/--proxy/--credentials/--verify-only`，stdout 每行输出 JSON 事件（`status` / `progress` / `need-guard` / `done` / `error`），退出码 0=成功 2=不拥有 3=登录失败 4=下载失败 |
| `upstream/` | 作者原始的 `TmlAndroidBootstrap` + `TmlAndroidHost` 源码（**仅作参考，不参与构建**）。它们通过 JNI 桥接做启动状态/提示框/输入事件/分辨率，这些在 RAL 里对应 Kotlin 侧已有能力，不需要整套搬 |

## 与 RAL 的对接方式

```
RAL(Kotlin) ── 安装/启动流程检测到缺少 Content ──▶ 调 SteamContentCli（用 RAL 自带的 .NET 10 运行时）
                                                  │ stdout: {"type":"progress",...} 行
                                                  ▼
                                          解析 JSON 更新进度条；need-guard 弹输入框
```

- 凭据文件默认放 `<game-root>/steam-content-credentials.json`（只含 refresh token + GuardData）
- 需要 UI 时：账号/密码/Guard 输入 → 通过参数或 stdin 传给 CLI
- `--verify-only` 用于只校验拥有、不下载

## 构建

```
./build.sh
```

产物会拷到 `app/src/main/assets/steam/`（`SteamContentCli.dll` + 依赖 + runtimeconfig），随 APK 分发；
运行时用 RAL 的 .NET 10 运行时（`runtimes/dotnet/...`）执行。

## 注意

- Steam 登录走 SteamKit2（`ProtocolTypes.All`，TCP/WebSocket 都试），部分网络环境需要 `--proxy`
- 匿名会话不能下 Terraria（需要拥有该 App），所以这里用的是真实账号 + refresh token
- 合规：仅下载该账号已拥有的内容
