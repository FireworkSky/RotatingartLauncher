using System;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;

namespace RAL.SteamBridge;

public sealed class PendingCall {
    public ulong Handle;
    public string ResultStruct;   // 例如 SteamUGCQueryCompleted_t
    public byte[] Payload;        // 已按该结构体布局序列化
    public bool IsBroadcastCallback;   // true = 直接回调（Callback<T>），false = API 调用结果（CallResult<T>）
    public int TargetPipe;             // 0=任意, 1=客户端, 2=GameServer
    public int Skips;                  // 因 pipe 不匹配被跳过几次（兜底用）
}

/// <summary>
/// 喂给 Steamworks.NET 自己的 CallbackDispatcher。
/// 它由 4 个原生驱动：ManualDispatch_{RunFrame,GetNextCallback,GetAPICallResult,FreeLastCallback}
/// —— 我们只要把队列喂进去，Steamworks.NET 就会把结果派发给 tML 的 CallResult&lt;T&gt;。
/// </summary>
public static class Dispatch {
    static readonly Queue<PendingCall> Q = new();
    static PendingCall _current;
    static IntPtr _completedPtr;   // SteamAPICallCompleted_t（每次 GetNextCallback 分配）
    static ulong _nextHandle = 0xA000;

    public static int Queued => Q.Count;

    public static ulong NewHandle() { lock (Q) return ++_nextHandle; }

    public static void QueueWith(ulong handle, string resultStruct, Action<object> fill) {
        var obj = StructKit.New(resultStruct);
        StructKit.EnsureArrays(obj);
        fill?.Invoke(obj);
        var pc = new PendingCall { Handle = handle, ResultStruct = resultStruct, Payload = StructKit.ToBytes(obj) };
        lock (Q) Q.Enqueue(pc);
        Console.WriteLine($"[dispatch] 入队 {resultStruct} handle=0x{handle:X} ({pc.Payload.Length}B)");
    }

    /// <summary>投递“广播回调”（对应 Steamworks.NET 的 Callback&lt;T&gt;），例如 DownloadItemResult_t</summary>
    public static void QueueCallback(string structName, Action<object> fill) {
        var obj = StructKit.New(structName);
        StructKit.EnsureArrays(obj);
        fill?.Invoke(obj);
        var pc = new PendingCall { Handle = 0, ResultStruct = structName, Payload = StructKit.ToBytes(obj), IsBroadcastCallback = true, TargetPipe = GuessTargetPipe() };
        lock (Q) Q.Enqueue(pc);
        Console.WriteLine($"[dispatch] 入队回调 {structName} ({pc.Payload.Length}B)");
    }

    public static ulong Queue(string resultStruct, Action<object> fill) {
        var obj = StructKit.New(resultStruct);
        StructKit.EnsureArrays(obj);
        fill?.Invoke(obj);
        var pc = new PendingCall { Handle = ++_nextHandle, ResultStruct = resultStruct, Payload = StructKit.ToBytes(obj) };
        lock (Q) Q.Enqueue(pc);
        Console.WriteLine($"[dispatch] 入队 {resultStruct} handle=0x{pc.Handle:X} ({pc.Payload.Length}B)");
        return pc.Handle;
    }

    // ---- 被重定向后的原生实现的这 4 个 ----
    public static object[] RunFrame(object[] args) => null;

    /// <summary>
    /// 广播回调该投给哪个 dispatcher？看 tML 自己走的是哪条路：
    /// SteamedWraps.SteamClient == true → 客户端(Callback.Create)，否则 GameServer(Callback.CreateGameServer)。
    /// </summary>
    static int GuessTargetPipe() {
        try {
            var t = Type.GetType("Terraria.Social.Steam.SteamedWraps, tModLoader", false);
            var p = t?.GetProperty("SteamClient", BindingFlags.Public | BindingFlags.Static);
            if (p != null) return (bool)p.GetValue(null) ? 1 : 2;
        } catch { }
        return 2;   // 拿不到就按 GameServer 走（GoG 路径）
    }

    public static object[] GetNextCallback(object[] args) {
        IntPtr pMsg = (IntPtr)args[1];
        int pipe = StructKit.AsInt(args[0]);
        lock (Q) {
            if (_current != null) return new object[] { false };
            if (Q.Count == 0) return new object[] { false };
            var head = Q.Peek();
            // 广播回调：只交给目标 dispatcher；连续被跳过太多就放行（兜底，避免卡住）
            if (head.IsBroadcastCallback && head.TargetPipe != 0 && head.TargetPipe != pipe) {
                if (++head.Skips < 60) return new object[] { false };
                Console.WriteLine($"[dispatch] 兜底放行 {head.ResultStruct}（目标 pipe={head.TargetPipe} 实际={pipe}）");
            }
            _current = head;
        }
        if (_current.IsBroadcastCallback) {
            // 直接回调：CallbackMsg_t 直接指向载荷，CallbackDispatcher 会按 m_iCallback 找 Callback<T>
            var payloadPtr = Marshal.AllocHGlobal(_current.Payload.Length);
            Marshal.Copy(_current.Payload, 0, payloadPtr, _current.Payload.Length);
            _completedPtr = payloadPtr;      // 复用同一个待释放指针
            var m = Activator.CreateInstance(StructKit.T("CallbackMsg_t"));
            StructKit.SetAuto(m, "m_hSteamUser", 1);
            StructKit.SetAuto(m, "m_iCallback", StructKit.CallbackId(_current.ResultStruct));
            StructKit.SetAuto(m, "m_pubParam", payloadPtr);
            StructKit.SetAuto(m, "m_cubParam", _current.Payload.Length);
            Marshal.StructureToPtr(m, pMsg, false);
            Console.WriteLine($"[dispatch] 投递回调 {_current.ResultStruct} (id={StructKit.CallbackId(_current.ResultStruct)})");
            return new object[] { true };
        }

        var completedType = StructKit.T("SteamAPICallCompleted_t");
        _completedPtr = Marshal.AllocHGlobal(Marshal.SizeOf(completedType));
        var ca = Activator.CreateInstance(completedType);
        StructKit.SetAuto(ca, "m_hAsyncCall", _current.Handle);
        StructKit.SetAuto(ca, "m_iCallback", StructKit.CallbackId(_current.ResultStruct));
        StructKit.SetAuto(ca, "m_cubParam", (uint)_current.Payload.Length);
        Marshal.StructureToPtr(ca, _completedPtr, false);

        var msgType = StructKit.T("CallbackMsg_t");
        var msg = Activator.CreateInstance(msgType);
        StructKit.SetAuto(msg, "m_hSteamUser", 1);
        StructKit.SetAuto(msg, "m_iCallback", StructKit.CallbackId("SteamAPICallCompleted_t"));
        StructKit.SetAuto(msg, "m_pubParam", _completedPtr);
        StructKit.SetAuto(msg, "m_cubParam", Marshal.SizeOf(completedType));
        Marshal.StructureToPtr(msg, pMsg, false);
        return new object[] { true };
    }

    public static object[] GetAPICallResult(object[] args) {
        ulong hCall = StructKit.AsULong(args[1]);
        IntPtr pOut = (IntPtr)args[2];
        int cap = StructKit.AsInt(args[3]);
        lock (Q) {
            if (_current == null || _current.Handle != hCall) return new object[] { false, false };
            int n = Math.Min(cap, _current.Payload.Length);
            Marshal.Copy(_current.Payload, 0, pOut, n);
            Console.WriteLine($"[dispatch] 交付 {_current.ResultStruct} -> 回调 ({n}B)");
            return new object[] { true, false };
        }
    }

    public static object[] FreeLastCallback(object[] args) {
        lock (Q) {
            if (_current != null) Q.Dequeue();
            _current = null;
        }
        if (_completedPtr != IntPtr.Zero) { Marshal.FreeHGlobal(_completedPtr); _completedPtr = IntPtr.Zero; }
        return null;
    }
}
