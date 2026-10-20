using System;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Collections.Generic;

namespace RAL.SteamBridge;

/// <summary>按 Steamworks.NET 自己的结构体定义构造/填充，绝不猜布局。</summary>
public static class StructKit {
    static Assembly _sw;
    /// <summary>懒解析 Steamworks.NET —— 不依赖预载：我们是在它的代码里被调用的</summary>
    public static Assembly Sw {
        get {
            if (_sw != null) return _sw;
            foreach (var a in System.Runtime.Loader.AssemblyLoadContext.Default.Assemblies)
                if (a.GetName().Name == "Steamworks.NET") return _sw = a;
            var t = Type.GetType("Steamworks.CallbackDispatcher, Steamworks.NET", false);
            if (t != null) return _sw = t.Assembly;
            return _sw = Assembly.Load("Steamworks.NET");
        }
        set => _sw = value;
    }

    public static Type T(string name) {
        var t = Sw?.GetType("Steamworks." + name, false);
        if (t == null) throw new InvalidOperationException($"Steamworks.{name} 不存在（Sw 未绑定？）");
        return t;
    }
    public static object New(string name) => Activator.CreateInstance(T(name));
    public static int Size(Type t) => Marshal.SizeOf(t);
    public static int CallbackId(string structName) =>
        Convert.ToInt32(T(structName).GetField("k_iCallback", BindingFlags.Public | BindingFlags.Static).GetRawConstantValue());

    static FieldInfo F(object o, string f) =>
        o.GetType().GetField(f, BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic)
        ?? throw new InvalidOperationException($"{o.GetType().Name}.{f} 不存在");

    public static void Set(object o, string f, object v) => F(o, f).SetValue(o, v);
    public static object Get(object o, string f) => F(o, f).GetValue(o);

    /// <summary>把 byte[] 字段按 MarshalAs(SizeConst) 分配好（工厂里给的是 null）</summary>
    public static void EnsureArrays(object o) {
        foreach (var f in o.GetType().GetFields(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic)) {
            if (f.FieldType != typeof(byte[])) continue;
            if (f.GetValue(o) != null) continue;
            var ma = f.GetCustomAttribute<MarshalAsAttribute>();
            f.SetValue(o, new byte[ma?.SizeConst ?? 256]);
        }
    }

    /// <summary>写 UTF8 字符串到 byte[] 字段（不超容量）</summary>
    public static void SetUtf8(object o, string fieldName, string s) {
        var arr = (byte[])Get(o, fieldName);
        if (arr == null) { EnsureArrays(o); arr = (byte[])Get(o, fieldName); }
        var bytes = Encoding.UTF8.GetBytes(s ?? "");
        int n = Math.Min(bytes.Length, arr.Length - 1);
        Array.Copy(bytes, arr, n);
        arr[n] = 0;
    }


    /// <summary>自动处理枚举与单字段包装结构体（PublishedFileId_t / AppId_t / UGCQueryHandle_t / SteamAPICall_t ...）</summary>
    public static void SetAuto(object o, string field, object value) {
        var f = F(o, field);
        var ft = f.FieldType;
        if (ft.IsEnum) { f.SetValue(o, Enum.ToObject(ft, value)); return; }
        if (ft.IsValueType && !ft.IsPrimitive && value != null && !ft.IsInstanceOfType(value)) {
            var inner = ft.GetFields(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
            if (inner.Length == 1) {
                var boxed = Activator.CreateInstance(ft);
                var iv = value;
                if (inner[0].FieldType.IsPrimitive) iv = Convert.ChangeType(value, inner[0].FieldType);
                inner[0].SetValue(boxed, iv);
                f.SetValue(o, boxed);
                return;
            }
        }
        if (ft.IsPrimitive) f.SetValue(o, Convert.ChangeType(value, ft));
        else f.SetValue(o, value);
    }

    /// <summary>把参数取成字符串：可能是 string，也可能是 InteropHelp.UTF8StringHandle / IntPtr</summary>
    public static string AsString(object v) {
        if (v == null) return "";
        if (v is string s) return s;
        if (v is IntPtr ip) return PtrToUtf8(ip);
        var t = v.GetType();
        var m = t.GetMethod("DangerousGetHandle", Type.EmptyTypes);
        if (m != null) return PtrToUtf8((IntPtr)m.Invoke(v, null));
        return v.ToString();
    }
    public static string PtrToUtf8(IntPtr p) {
        if (p == IntPtr.Zero) return "";
        var bytes = new List<byte>();
        for (int i = 0; i < 8192; i++) { var b = Marshal.ReadByte(p, i); if (b == 0) break; bytes.Add(b); }
        return System.Text.Encoding.UTF8.GetString(bytes.ToArray());
    }

    /// <summary>把“装箱的标量或单字段包装结构体”取成 ulong/uint/int（byref 参数是装箱副本，绝不能 Convert.ToUInt64）</summary>
    public static ulong AsULong(object v) {
        if (v == null) return 0;
        if (v is ulong u) return u;
        if (v.GetType().IsPrimitive) return Convert.ToUInt64(v);
        var fs = v.GetType().GetFields(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
        if (fs.Length == 1) return Convert.ToUInt64(fs[0].GetValue(v));
        return 0;
    }
    public static uint AsUInt(object v) => (uint)AsULong(v);
    public static int AsInt(object v) {
        if (v == null) return 0;
        if (v.GetType().IsEnum) return Convert.ToInt32(v);
        if (v.GetType().IsPrimitive) return Convert.ToInt32(v);
        var fs = v.GetType().GetFields(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
        return fs.Length == 1 ? Convert.ToInt32(fs[0].GetValue(v)) : 0;
    }

    /// <summary>构造单字段包装结构体实例</summary>
    public static object Make(string structName, string field, object value) {
        var t = T(structName);
        var o = Activator.CreateInstance(t);
        var f = t.GetField(field, BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
        f.SetValue(o, f.FieldType.IsPrimitive ? Convert.ChangeType(value, f.FieldType) : value);
        return o;
    }

    public static byte[] ToBytes(object structObj) {
        int size = Marshal.SizeOf(structObj.GetType());
        var buf = Marshal.AllocHGlobal(size);
        try { Marshal.StructureToPtr(structObj, buf, false); var b = new byte[size]; Marshal.Copy(buf, b, 0, size); return b; }
        finally { Marshal.FreeHGlobal(buf); }
    }
    public static void WriteUtf8ToPtr(IntPtr ptr, uint cap, string s) {
        if (ptr == IntPtr.Zero || cap == 0) return;
        var bytes = Encoding.UTF8.GetBytes(s ?? "");
        int n = Math.Min(bytes.Length, (int)cap - 1);
        Marshal.Copy(bytes, 0, ptr, n);
        Marshal.WriteByte(ptr, n, 0);
    }
}
