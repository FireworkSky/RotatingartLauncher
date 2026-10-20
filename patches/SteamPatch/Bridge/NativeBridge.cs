using System;
using System.Collections.Generic;
using System.Reflection;

namespace RAL.SteamBridge;

/// <summary>
/// 所有 steam_api 原生调用的**唯一入口**。
/// Steamworks.NET 里 981 个 [DllImport] 的 body 会被 Cecil 改写成调用这里。
/// 约定：args 里 byref 参数已按“值”装箱；返回 object[]，
///   [0] = 返回值（null 表示“用默认值”），[i+1] = 第 i 个参数写回值（按参数顺序，仅 byref 需要）。
/// </summary>
public static class NativeBridge {
    public const uint TML_APPID = 1281930;
    public const ulong FAKE_STEAMID = 76561198000000000UL;

    static readonly IntPtr FAKE_INTERFACE = (IntPtr)0x5EA00000;

    public static int CallCount;
    public static readonly List<string> Seen = new List<string>();

    public static void Bind(Assembly swAssembly) {
        StructKit.Sw = swAssembly;
        Console.WriteLine($"[bridge] 已绑定 Steamworks.NET: {swAssembly.GetName().Name} {swAssembly.GetName().Version}");
    }

    static readonly HashSet<string> _reported = new HashSet<string>();

    public static object[] Invoke(string name, string retType, object[] args) {
        try {
            return InvokeInner(name, retType, args);
        } catch (Exception e) {
            // 我们的 bug 绝不能崩掉游戏：记一次日志，然后交回默认值
            if (_reported.Add(name))
                Console.WriteLine($"[bridge][错误] {name}: {e.GetType().Name}: {e.Message}\n{e.StackTrace}");
            return null;
        }
    }

    static object[] InvokeInner(string name, string retType, object[] args) {
        CallCount++;
        if (Seen.Count < 400 && !Seen.Contains(name)) Seen.Add(name);

        if (name.StartsWith("SteamAPI_ManualDispatch_")) {
            switch (name) {
                case "SteamAPI_ManualDispatch_RunFrame":        return Dispatch.RunFrame(args);
                case "SteamAPI_ManualDispatch_GetNextCallback": return Dispatch.GetNextCallback(args);
                case "SteamAPI_ManualDispatch_GetAPICallResult":return Dispatch.GetAPICallResult(args);
                case "SteamAPI_ManualDispatch_FreeLastCallback":return Dispatch.FreeLastCallback(args);
            }
        }
        if (name.StartsWith("ISteamUGC_")) return UgcStore.Handle(name, retType, args);

        switch (name) {
            // ---- 初始化 ----
            case "SteamInternal_SteamAPI_Init":              // 客户端路径
                Console.WriteLine("[bridge] SteamInternal_SteamAPI_Init -> OK");
                return new object[] { 0 };                    // k_ESteamAPIInitResult_OK
            case "SteamInternal_GameServer_Init_V2":         // GOG 路径
                Console.WriteLine("[bridge] SteamInternal_GameServer_Init_V2 -> OK");
                return new object[] { 0 };
            case "SteamAPI_Shutdown":
                Console.WriteLine("[bridge] SteamAPI_Shutdown");
                return null;
            case "SteamAPI_RunCallbacks":
                return null;                                  // 每帧调用，走快路径

            // ---- 上下文/接口指针：一律给个非零假指针 ----
            case "SteamInternal_CreateInterface":
            case "SteamInternal_FindOrCreateUserInterface":
            case "SteamInternal_FindOrCreateGameServerInterface":
                return new object[] { FAKE_INTERFACE };

            // ---- 句柄 ----
            case "SteamAPI_GetHSteamPipe":
            case "SteamAPI_GetHSteamUser":
                return new object[] { 1 };          // 客户端 pipe
            case "SteamGameServer_GetHSteamPipe":
            case "SteamGameServer_GetHSteamUser":
                return new object[] { 2 };          // GameServer pipe（必须与客户端不同）

            // ---- 常用值 ----
            case "ISteamUtils_GetAppID":
                return new object[] { TML_APPID };
            case "ISteamUtils_IsSteamRunningOnSteamDeck":
                return new object[] { false };
            case "ISteamUser_GetSteamID":
                return new object[] { FAKE_STEAMID };
            case "ISteamApps_BIsSubscribed":
            case "ISteamApps_BIsSubscribedApp":
            case "ISteamApps_BIsAppInstalled":
                return new object[] { true };
            case "ISteamApps_GetAppBuildId":
                return new object[] { 0 };
            case "ISteamRemoteStorage_IsCloudEnabledForAccount":
            case "ISteamRemoteStorage_IsCloudEnabledForApp":
                return new object[] { false };
            case "SteamAPI_IsSteamRunning":
                return new object[] { true };

            // ---- GameServer ----
            case "ISteamGameServer_LogOnAnonymous":
                Console.WriteLine("[bridge] ISteamGameServer_LogOnAnonymous -> 匿名会话");
                return null;
            case "ISteamGameServer_SetProduct":
            case "ISteamGameServer_SetGameDescription":
            case "ISteamGameServer_SetModDir":
            case "ISteamGameServer_SetDedicatedServer":
                return null;
        }

        // 兜底：名字像“接口获取”的都给非零指针，其余交给 IL 填默认值
        if (name.Contains("GetISteam") || name.Contains("CreateInterface") || name.StartsWith("SteamAPI_Steam"))
            return new object[] { FAKE_INTERFACE };

        return null;
    }

    // ---- ISteamUGC_* ----
    static object[] Ugc(string name, string retType, object[] args) {
        switch (name) {
            case "ISteamUGC_CreateQueryAllUGCRequestPage":
            case "ISteamUGC_CreateQueryAllUGCRequestCursor":
                Console.WriteLine($"[bridge] {name} -> handle");
                return new object[] { 0x1001UL };            // UGCQueryHandle_t
            case "ISteamUGC_SendQueryUGCRequest":
                Console.WriteLine("[bridge] ISteamUGC_SendQueryUGCRequest -> SteamAPICall_t");
                return new object[] { 0x2002UL };
            case "ISteamUGC_ReleaseQueryUGCRequest":
                return new object[] { true };
            case "ISteamUGC_GetItemState":
                return new object[] { 0u };                   // 未安装
            case "ISteamUGC_DownloadItem":
                Console.WriteLine("[bridge] ISteamUGC_DownloadItem -> 由 SteamKit2 接手（待接）");
                return new object[] { true };
            case "ISteamUGC_GetItemInstallInfo":
                return new object[] { false };
            case "ISteamUGC_GetQueryUGCResult":
                return new object[] { true };
            case "ISteamUGC_GetWorkshopEULAStatus":
                return new object[] { 0x3003UL };             // SteamAPICall_t
        }
        return null;
    }
}
