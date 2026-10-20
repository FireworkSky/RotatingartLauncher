# SteamPatch（tModLoader Steam 创意工坊支持）

用一个**匿名 Steam3 会话**接入真实的 Steam 创意工坊：游戏里 Mod Browser 的搜索 /
浏览 / 下载全部继续走 tModLoader 自己的代码路径，不需要 Steam 客户端，也不需要
`steam_api` 原生库。

## 原理

```
tModLoader (CoreCLR)
  └─ DOTNET_STARTUP_HOOKS = SteamPatch.dll        ← RAL 的补丁系统挂载
       ├─ SteamFilePatcher: 用 Cecil 把游戏目录里的 Steamworks.NET.dll 改写成
       │    981 个 native 调用 → RAL.SteamBridge 的托管实现（其它逻辑不动）
       ├─ Redirector: 解析 Steamworks.NET 时优先指向改写后的那份
       ├─ TmlGates: 可选，Harmony 观测 SteamedWraps.Initialize、清掉 tML 的
       │    「工坊搜索假成功 / 异常下载检测」两个旧门（可用 RAL_STEAM_KEEP_LEGACY_GATES=1 保留）
       └─ 内嵌资源：RAL.SteamBridge / SteamKit2 / protobuf-net / QRCoder /
            System.IO.Hashing / ZstdSharp / Mono.Cecil / 0Harmony / DepotDownloader
```

`RAL.SteamBridge`：

| 文件 | 作用 |
|---|---|
| `NativeBridge.cs` | Steam 原生 API 的托管替身（SteamAPI_*、SteamInternal_*、GetHSteamUser 等） |
| `UgcStore.cs` | ISteamUGC：CreateQueryAllUGCRequest / SetSearchText / SendQueryUGCRequest / GetQueryUGCResult / metadata / kvtags |
| `Sk2.cs` | SteamKit2 匿名登入 Steam3；内置 CM 列表（绕开被墙的 Web API） |
| `Dispatch.cs` | 把 Steam 回调（SteamUGCQueryCompleted_t 等）按原生语义入队、由 RunCallbacks 派发 |
| `StructKit.cs` | 原生结构体打包/解包（280B 的 SteamUGCQueryCompleted_t 等） |
| `Downloader.cs` | 进程内调用 DepotDownloader 下载工坊物品，落盘到 `steamapps/workshop` 与 `Mods/` |

下载之所以走进程内：Android 上 `Process.Start` 会被 SELinux 拒绝。

## 构建

```
DEPOT_DIR=<DepotDownloader bin/Release/net8.0> \
CECIL_DLL=<Mono.Cecil.dll> \
HARMONY_DLL=<0Harmony.dll> \
./build.sh
```

会得到 `build/SteamPatch.dll`，并打包成
`app/src/main/assets/patches/com.app.ralaunch.tmodloader.steam.zip`
（`patch.json` 里有 id / entryPoint / targetGames，RAL 启动游戏时按
`DOTNET_STARTUP_HOOKS` 加载）。

## 测试

`tools/ShimTool` 是开发期工具：把任意 Steamworks.NET.dll 按同样的规则改写成
走 bridge 的版本，用于离线验证（原始 POC 里还用 TmlSim/SimLib 做了
`SteamAPI.Init → UGC 查询 → 回调派发 → 下载` 的端到端模拟）。

## 已知限制

- 只覆盖 tModLoader 用到的 Steam 接口（UGC/UT 回调/Apps 少量）；其余 native 调用
  返回安全默认值。
- 匿名会话没有 Steam 账号，订阅/收藏等写操作不可用，只能查询与下载。
- `TmlGates` 依赖 Harmony；缺 Harmony 时该步骤会失败但不影响 Steam 功能。
