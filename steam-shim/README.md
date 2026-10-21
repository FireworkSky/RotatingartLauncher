# Steam shim（把 Steamworks.NET 的原生层换成 SteamKit2 会话）

Terraria 的 Steam 版 exe 一启动就会调 `SteamAPI.Init()`，没有真正的 Steam 客户端就会退出。
这里把 **Steamworks.NET 2024.8.0** 的原生层（`steam_api64.dll` 的 P/Invoke）换成托管实现：

- 结构体 / 枚举 / `Callback<T>` / `CallResult<T>` / 各 `SteamAPI_*` 接口层：直接用 Steamworks.NET 源码
  （MIT，tag `2024.8.0`），保证 ABI、枚举值、回调 ID 与 Terraria 的引用完全一致；
- 原生函数：`gen/gen_native.py` 从 `autogen/NativeMethods.cs` 生成 `NativeMethods.g.cs`，
  把 964 个 `static extern` 改写成托管方法；其中 69 个接到我们自己的实现（`src/Shim.cs`），
  其余返回默认值。

当前已实现（够单机用）：

| 区域 | 内容 |
|---|---|
| 核心 | `SteamAPI_Init/Shutdown/RunCallbacks/RestartAppIfNecessary/IsSteamRunning`，ManualDispatch 回调队列 |
| 玩家 | `SteamUser.GetSteamID/BLoggedOn`、`SteamFriends.GetPersonaName`（读 `steam-shim/identity.json`） |
| 成就/统计 | `SteamUserStats` 读 `SetAchievement/GetAchievement/StoreStats/ResetAllStats/GetStat*/SetStat*`，本地落 `steam-shim/stats.txt` |
| 云存档 | `SteamRemoteStorage` 文件读写落到 `steam-shim/cloud/`（异步接口也实现了，回调走 ManualDispatch） |
| 其它 | `SteamUtils/GetAppID/语言`、`SteamApps.IsSubscribed/GetAppInstallDir`、Steam 大厅与 P2P 全部降级为默认值 |

## 构建

```bash
# 1) 取 Steamworks.NET 2024.8.0 源码（MIT）
git clone --depth 1 --branch 2024.8.0 https://github.com/rlabrecque/Steamworks.NET.git /tmp/sw
cp -r /tmp/sw/com.rlabrecque.steamworks.net/Runtime/{autogen,types,*.cs} build/

# 2) 生成托管版 NativeMethods（替换掉 autogen/NativeMethods.cs）
python3 gen/gen_native.py

# 3) 用 mono 的 mcs 编译成 net40 程序集，assembly 名/版本必须是 Steamworks.NET 2024.8.0.0
mcs -nologo -target:library -unsafe -langversion:latest -define:STEAMWORKS_LIN_OSX \
    -out:Steamworks.NET.dll $(find build -name '*.cs' ! -name NativeMethods.cs) \
    src/Shim.cs src/AssemblyInfo.cs build/gen/NativeMethods.g.cs
```

产物 `Steamworks.NET.dll`（约 400 KB）放进游戏目录，包装启动器用 `Assembly.LoadFrom` 预加载，
Terraria 引用 `Steamworks.NET, Version=2024.8.0.0` 时直接命中我们这份。

## 运行时数据

游戏目录下 `steam-shim/`：

- `identity.json`：`{"steamid":"7656...","persona":"...","language":"schinese","appid":"105600"}`
  （由启动器登录后的 SteamKit2 会话写出）
- `stats.txt`：已解锁成就
- `cloud/`：云存档文件镜像

## 还没做

- 启动器侧的 shim 服务（SteamKit2 会话 → identity/成就上传/云存档同步，本地 TCP）
- 包装启动器里的 `Assembly.LoadFrom("Steamworks.NET.dll")` + 写 `identity.json`
- 联机（Steam 大厅 / P2P / SteamNetworkingSockets）目前全部走默认值，Steam 联机不可用
