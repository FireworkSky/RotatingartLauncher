using System;
using System.IO;
using System.Linq;
using Mono.Cecil;
using Mono.Cecil.Cil;
using MethodAttributes = Mono.Cecil.MethodAttributes;

namespace ShimTool;

/// <summary>
/// 把 Steamworks.NET 里所有 [DllImport] 方法的 body 改写成 call RAL.SteamBridge.NativeBridge.Invoke(name, retType, args)。
/// 结构体/枚举/回调机制一律不动 —— 只用他们自己的那份，避免任何类型对齐问题。
/// </summary>
public static class Redirector {
    public const string BridgeAsm  = "RAL.SteamBridge";
    public const string BridgeNs   = "RAL.SteamBridge";
    public const string BridgeType = "NativeBridge";

    static TypeReference mod_ObjectType;

    public static int PatchAll(ModuleDefinition mod) {
        mod_ObjectType = mod.ImportReference(typeof(object));
        var objRef  = mod.ImportReference(typeof(object));
        var strRef  = mod.ImportReference(typeof(string));
        var objArr  = new ArrayType(objRef);

        var asmRef = mod.AssemblyReferences.FirstOrDefault(a => a.Name == BridgeAsm);
        if (asmRef == null) { asmRef = new AssemblyNameReference(BridgeAsm, new Version(1, 0, 0, 0)); mod.AssemblyReferences.Add(asmRef); }
        var bridgeType = new TypeReference(BridgeNs, BridgeType, mod, asmRef);
        var invoke = new MethodReference("Invoke", objArr, bridgeType) { HasThis = false };
        invoke.Parameters.Add(new ParameterDefinition("name",    ParameterAttributes.None, strRef));
        invoke.Parameters.Add(new ParameterDefinition("retType", ParameterAttributes.None, strRef));
        invoke.Parameters.Add(new ParameterDefinition("args",    ParameterAttributes.None, objArr));

        int n = 0;
        foreach (var type in AllTypes(mod)) {
            foreach (var m in type.Methods.Where(x => x.IsPInvokeImpl).ToList()) {
                Rewrite(m, objRef, strRef, objArr, invoke);
                n++;
            }
        }
        return n;
    }

    static System.Collections.Generic.IEnumerable<TypeDefinition> AllTypes(ModuleDefinition mod) {
        foreach (var t in mod.Types) {
            yield return t;
            foreach (var nt in Nested(t)) yield return nt;
        }
    }
    static System.Collections.Generic.IEnumerable<TypeDefinition> Nested(TypeDefinition t) {
        foreach (var n in t.NestedTypes) { yield return n; foreach (var d in Nested(n)) yield return d; }
    }

    static void Rewrite(MethodDefinition m, TypeReference objRef, TypeReference strRef, ArrayType objArr, MethodReference invoke) {
        m.IsPInvokeImpl = false;
        m.PInvokeInfo = null;
        m.Attributes = (MethodAttributes)((int)m.Attributes & ~0x2000);
        for (int i = m.CustomAttributes.Count - 1; i >= 0; i--)
            if (m.CustomAttributes[i].AttributeType.Name == "DllImportAttribute") m.CustomAttributes.RemoveAt(i);

        var body = new MethodBody(m) { InitLocals = true, MaxStackSize = 16 };
        m.Body = body;
        var il = body.GetILProcessor();

        var arrVar = new VariableDefinition(objArr); body.Variables.Add(arrVar);
        var resVar = new VariableDefinition(objArr); body.Variables.Add(resVar);
        bool retVoid = m.ReturnType.MetadataType == MetadataType.Void;
        VariableDefinition defVar = null;
        if (!retVoid && m.ReturnType.IsValueType) { defVar = new VariableDefinition(m.ReturnType); body.Variables.Add(defVar); }

        // arr = new object[argc]
        il.Emit(OpCodes.Ldc_I4, m.Parameters.Count);
        il.Emit(OpCodes.Newarr, objRef);
        il.Emit(OpCodes.Stloc, arrVar);

        for (int i = 0; i < m.Parameters.Count; i++) {
            var p = m.Parameters[i];
            il.Emit(OpCodes.Ldloc, arrVar);
            il.Emit(OpCodes.Ldc_I4, i);
            il.Emit(OpCodes.Ldarg, p);
            var pt = p.ParameterType;
            if (pt is ByReferenceType brt) {
                var et = brt.ElementType;
                il.Emit(et.IsValueType ? OpCodes.Ldobj : OpCodes.Ldind_Ref, et);
                pt = et;
            }
            if (pt.IsValueType) il.Emit(OpCodes.Box, pt);
            il.Emit(OpCodes.Stelem_Ref);
        }

        // res = NativeBridge.Invoke(name, retType, arr)
        il.Emit(OpCodes.Ldstr, m.Name);
        il.Emit(OpCodes.Ldstr, m.ReturnType.FullName);
        il.Emit(OpCodes.Ldloc, arrVar);
        il.Emit(OpCodes.Call, invoke);
        il.Emit(OpCodes.Stloc, resVar);

        // 写回 byref 参数（带保护：res==null / 长度不够 / 该项为 null 时不动原值）
        for (int i = 0; i < m.Parameters.Count; i++) {
            var p = m.Parameters[i];
            if (!(p.ParameterType is ByReferenceType brt)) continue;
            var et = brt.ElementType;
            // 统一用 object 临时变量承载，避免值类型/引用类型两套逻辑
            var tmpObj = new VariableDefinition(mod_ObjectType);
            body.Variables.Add(tmpObj);

            var skip = Instruction.Create(OpCodes.Nop);
            il.Emit(OpCodes.Ldloc, resVar); il.Emit(OpCodes.Brfalse, skip);
            il.Emit(OpCodes.Ldloc, resVar); il.Emit(OpCodes.Ldlen); il.Emit(OpCodes.Conv_I4);
            il.Emit(OpCodes.Ldc_I4, i + 1); il.Emit(OpCodes.Ble, skip);
            il.Emit(OpCodes.Ldloc, resVar); il.Emit(OpCodes.Ldc_I4, i + 1); il.Emit(OpCodes.Ldelem_Ref);
            il.Emit(OpCodes.Stloc, tmpObj);
            il.Emit(OpCodes.Ldloc, tmpObj); il.Emit(OpCodes.Brfalse, skip);
            il.Emit(OpCodes.Ldarg, p);
            il.Emit(OpCodes.Ldloc, tmpObj);
            il.Emit(OpCodes.Unbox_Any, et);
            il.Emit(et.IsValueType ? OpCodes.Stobj : OpCodes.Stind_Ref, et);
            il.Append(skip);
        }

        if (retVoid) { il.Emit(OpCodes.Ret); return; }

        // 有返回值：res[0] 优先，null 则落默认值
        var lDef  = Instruction.Create(OpCodes.Nop);
        var lDone = Instruction.Create(OpCodes.Ret);
        il.Emit(OpCodes.Ldloc, resVar);
        il.Emit(OpCodes.Brfalse, lDef);
        il.Emit(OpCodes.Ldloc, resVar);
        il.Emit(OpCodes.Ldlen);
        il.Emit(OpCodes.Conv_I4);
        il.Emit(OpCodes.Brfalse, lDef);
        il.Emit(OpCodes.Ldloc, resVar);
        il.Emit(OpCodes.Ldc_I4_0);
        il.Emit(OpCodes.Ldelem_Ref);
        il.Emit(m.ReturnType.IsValueType ? OpCodes.Unbox_Any : OpCodes.Castclass, m.ReturnType);
        il.Emit(OpCodes.Br, lDone);
        il.Append(lDef);
        if (m.ReturnType.IsValueType) {
            il.Emit(OpCodes.Ldloca, defVar);
            il.Emit(OpCodes.Initobj, m.ReturnType);
            il.Emit(OpCodes.Ldloc, defVar);
        } else {
            il.Emit(OpCodes.Ldnull);
        }
        il.Append(lDone);
    }
}
