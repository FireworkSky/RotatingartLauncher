using System;
using System.Collections.Generic;
using System.Linq;
using Mono.Cecil;
using Mono.Cecil.Cil;
using Mono.Collections.Generic;

namespace SteamPatch;

public static class Redirector
{
	public const string BridgeAsm = "RAL.SteamBridge";

	public const string BridgeNs = "RAL.SteamBridge";

	public const string BridgeType = "NativeBridge";

	private static TypeReference mod_ObjectType;

	public static int PatchAll(ModuleDefinition mod)
	{
		//IL_0038: Unknown result type (might be due to invalid IL or missing references)
		//IL_003e: Expected Obj, but got Unknown
		//IL_0098: Unknown result type (might be due to invalid IL or missing references)
		//IL_009f: Expected Obj, but got Unknown
		//IL_00a7: Unknown result type (might be due to invalid IL or missing references)
		//IL_00ac: Unknown result type (might be due to invalid IL or missing references)
		//IL_00b5: Expected Obj, but got Unknown
		//IL_00c3: Unknown result type (might be due to invalid IL or missing references)
		//IL_00cd: Expected Obj, but got Unknown
		//IL_00db: Unknown result type (might be due to invalid IL or missing references)
		//IL_00e5: Expected Obj, but got Unknown
		//IL_00f3: Unknown result type (might be due to invalid IL or missing references)
		//IL_00fd: Expected Obj, but got Unknown
		//IL_007a: Unknown result type (might be due to invalid IL or missing references)
		//IL_0080: Expected Obj, but got Unknown
		mod_ObjectType = mod.ImportReference(typeof(object));
		TypeReference val = mod.ImportReference(typeof(object));
		TypeReference val2 = mod.ImportReference(typeof(string));
		ArrayType val3 = new ArrayType(val);
		AssemblyNameReference val4 = ((IEnumerable<AssemblyNameReference>)mod.AssemblyReferences).FirstOrDefault((AssemblyNameReference a) => a.Name == "RAL.SteamBridge");
		if (val4 == null)
		{
			val4 = new AssemblyNameReference("RAL.SteamBridge", new Version(1, 0, 0, 0));
			mod.AssemblyReferences.Add(val4);
		}
		TypeReference val5 = new TypeReference("RAL.SteamBridge", "NativeBridge", mod, (IMetadataScope)(object)val4);
		MethodReference val6 = new MethodReference("Invoke", (TypeReference)(object)val3, val5)
		{
			HasThis = false
		};
		val6.Parameters.Add(new ParameterDefinition("name", (ParameterAttributes)0, val2));
		val6.Parameters.Add(new ParameterDefinition("retType", (ParameterAttributes)0, val2));
		val6.Parameters.Add(new ParameterDefinition("args", (ParameterAttributes)0, (TypeReference)(object)val3));
		int num = 0;
		foreach (TypeDefinition item in AllTypes(mod))
		{
			foreach (MethodDefinition item2 in ((IEnumerable<MethodDefinition>)item.Methods).Where((MethodDefinition x) => x.IsPInvokeImpl).ToList())
			{
				Rewrite(item2, val, val2, val3, val6);
				num++;
			}
		}
		return num;
	}

	private static IEnumerable<TypeDefinition> AllTypes(ModuleDefinition mod)
	{
		foreach (TypeDefinition t in mod.Types)
		{
			yield return t;
			foreach (TypeDefinition item in Nested(t))
			{
				yield return item;
			}
		}
	}

	private static IEnumerable<TypeDefinition> Nested(TypeDefinition t)
	{
		foreach (TypeDefinition n in t.NestedTypes)
		{
			yield return n;
			foreach (TypeDefinition item in Nested(n))
			{
				yield return item;
			}
		}
	}

	private static void Rewrite(MethodDefinition m, TypeReference objRef, TypeReference strRef, ArrayType objArr, MethodReference invoke)
	{
		//IL_0010: Unknown result type (might be due to invalid IL or missing references)
		//IL_001a: Unknown result type (might be due to invalid IL or missing references)
		//IL_006e: Unknown result type (might be due to invalid IL or missing references)
		//IL_0073: Unknown result type (might be due to invalid IL or missing references)
		//IL_007a: Unknown result type (might be due to invalid IL or missing references)
		//IL_0083: Expected Obj, but got Unknown
		//IL_0092: Unknown result type (might be due to invalid IL or missing references)
		//IL_0098: Expected Obj, but got Unknown
		//IL_00a5: Unknown result type (might be due to invalid IL or missing references)
		//IL_00ab: Expected Obj, but got Unknown
		//IL_00bd: Unknown result type (might be due to invalid IL or missing references)
		//IL_00c3: Invalid comparison between Unknown and I4
		//IL_00f6: Unknown result type (might be due to invalid IL or missing references)
		//IL_010c: Unknown result type (might be due to invalid IL or missing references)
		//IL_0118: Unknown result type (might be due to invalid IL or missing references)
		//IL_00e1: Unknown result type (might be due to invalid IL or missing references)
		//IL_00e8: Expected Obj, but got Unknown
		//IL_013b: Unknown result type (might be due to invalid IL or missing references)
		//IL_0147: Unknown result type (might be due to invalid IL or missing references)
		//IL_0154: Unknown result type (might be due to invalid IL or missing references)
		//IL_01da: Unknown result type (might be due to invalid IL or missing references)
		//IL_01eb: Unknown result type (might be due to invalid IL or missing references)
		//IL_0201: Unknown result type (might be due to invalid IL or missing references)
		//IL_020d: Unknown result type (might be due to invalid IL or missing references)
		//IL_021a: Unknown result type (might be due to invalid IL or missing references)
		//IL_01b7: Unknown result type (might be due to invalid IL or missing references)
		//IL_01aa: Unknown result type (might be due to invalid IL or missing references)
		//IL_0190: Unknown result type (might be due to invalid IL or missing references)
		//IL_0189: Unknown result type (might be due to invalid IL or missing references)
		//IL_025f: Unknown result type (might be due to invalid IL or missing references)
		//IL_0266: Expected Obj, but got Unknown
		//IL_0273: Unknown result type (might be due to invalid IL or missing references)
		//IL_0280: Unknown result type (might be due to invalid IL or missing references)
		//IL_028c: Unknown result type (might be due to invalid IL or missing references)
		//IL_0299: Unknown result type (might be due to invalid IL or missing references)
		//IL_02a5: Unknown result type (might be due to invalid IL or missing references)
		//IL_02b0: Unknown result type (might be due to invalid IL or missing references)
		//IL_02bb: Unknown result type (might be due to invalid IL or missing references)
		//IL_02ca: Unknown result type (might be due to invalid IL or missing references)
		//IL_02d7: Unknown result type (might be due to invalid IL or missing references)
		//IL_02e3: Unknown result type (might be due to invalid IL or missing references)
		//IL_02f2: Unknown result type (might be due to invalid IL or missing references)
		//IL_02fd: Unknown result type (might be due to invalid IL or missing references)
		//IL_030a: Unknown result type (might be due to invalid IL or missing references)
		//IL_0317: Unknown result type (might be due to invalid IL or missing references)
		//IL_0324: Unknown result type (might be due to invalid IL or missing references)
		//IL_0331: Unknown result type (might be due to invalid IL or missing references)
		//IL_033e: Unknown result type (might be due to invalid IL or missing references)
		//IL_0397: Unknown result type (might be due to invalid IL or missing references)
		//IL_03a3: Unknown result type (might be due to invalid IL or missing references)
		//IL_03b0: Unknown result type (might be due to invalid IL or missing references)
		//IL_03bc: Unknown result type (might be due to invalid IL or missing references)
		//IL_03c9: Unknown result type (might be due to invalid IL or missing references)
		//IL_03d5: Unknown result type (might be due to invalid IL or missing references)
		//IL_03e0: Unknown result type (might be due to invalid IL or missing references)
		//IL_03eb: Unknown result type (might be due to invalid IL or missing references)
		//IL_03f8: Unknown result type (might be due to invalid IL or missing references)
		//IL_0404: Unknown result type (might be due to invalid IL or missing references)
		//IL_040f: Unknown result type (might be due to invalid IL or missing references)
		//IL_038c: Unknown result type (might be due to invalid IL or missing references)
		//IL_035b: Unknown result type (might be due to invalid IL or missing references)
		//IL_0354: Unknown result type (might be due to invalid IL or missing references)
		//IL_042e: Unknown result type (might be due to invalid IL or missing references)
		//IL_0427: Unknown result type (might be due to invalid IL or missing references)
		//IL_043f: Unknown result type (might be due to invalid IL or missing references)
		//IL_048e: Unknown result type (might be due to invalid IL or missing references)
		//IL_0461: Unknown result type (might be due to invalid IL or missing references)
		//IL_046e: Unknown result type (might be due to invalid IL or missing references)
		//IL_047f: Unknown result type (might be due to invalid IL or missing references)
		m.IsPInvokeImpl = false;
		m.PInvokeInfo = null;
		m.Attributes = (MethodAttributes)(((ushort)m.Attributes) & unchecked((ushort)0xDFFF));
		for (int num = m.CustomAttributes.Count - 1; num >= 0; num--)
		{
			if (((MemberReference)m.CustomAttributes[num].AttributeType).Name == "DllImportAttribute")
			{
				m.CustomAttributes.RemoveAt(num);
			}
		}
		MethodBody val = (m.Body = new MethodBody(m)
		{
			InitLocals = true,
			MaxStackSize = 16
		});
		ILProcessor iLProcessor = val.GetILProcessor();
		VariableDefinition val3 = new VariableDefinition((TypeReference)(object)objArr);
		val.Variables.Add(val3);
		VariableDefinition val4 = new VariableDefinition((TypeReference)(object)objArr);
		val.Variables.Add(val4);
		bool flag = (int)((MethodReference)m).ReturnType.MetadataType == 1;
		VariableDefinition val5 = null;
		if (!flag && ((MethodReference)m).ReturnType.IsValueType)
		{
			val5 = new VariableDefinition(((MethodReference)m).ReturnType);
			val.Variables.Add(val5);
		}
		iLProcessor.Emit(OpCodes.Ldc_I4, ((MethodReference)m).Parameters.Count);
		iLProcessor.Emit(OpCodes.Newarr, objRef);
		iLProcessor.Emit(OpCodes.Stloc, val3);
		for (int i = 0; i < ((MethodReference)m).Parameters.Count; i++)
		{
			ParameterDefinition val6 = ((MethodReference)m).Parameters[i];
			iLProcessor.Emit(OpCodes.Ldloc, val3);
			iLProcessor.Emit(OpCodes.Ldc_I4, i);
			iLProcessor.Emit(OpCodes.Ldarg, val6);
			TypeReference val7 = ((ParameterReference)val6).ParameterType;
			ByReferenceType val8 = (ByReferenceType)(object)((val7 is ByReferenceType) ? val7 : null);
			if (val8 != null)
			{
				TypeReference elementType = ((TypeSpecification)val8).ElementType;
				iLProcessor.Emit(elementType.IsValueType ? OpCodes.Ldobj : OpCodes.Ldind_Ref, elementType);
				val7 = elementType;
			}
			if (val7.IsValueType)
			{
				iLProcessor.Emit(OpCodes.Box, val7);
			}
			iLProcessor.Emit(OpCodes.Stelem_Ref);
		}
		iLProcessor.Emit(OpCodes.Ldstr, ((MemberReference)m).Name);
		iLProcessor.Emit(OpCodes.Ldstr, ((MemberReference)((MethodReference)m).ReturnType).FullName);
		iLProcessor.Emit(OpCodes.Ldloc, val3);
		iLProcessor.Emit(OpCodes.Call, invoke);
		iLProcessor.Emit(OpCodes.Stloc, val4);
		for (int j = 0; j < ((MethodReference)m).Parameters.Count; j++)
		{
			ParameterDefinition val9 = ((MethodReference)m).Parameters[j];
			TypeReference parameterType = ((ParameterReference)val9).ParameterType;
			ByReferenceType val10 = (ByReferenceType)(object)((parameterType is ByReferenceType) ? parameterType : null);
			if (val10 != null)
			{
				TypeReference elementType2 = ((TypeSpecification)val10).ElementType;
				VariableDefinition val11 = new VariableDefinition(mod_ObjectType);
				val.Variables.Add(val11);
				Instruction val12 = Instruction.Create(OpCodes.Nop);
				iLProcessor.Emit(OpCodes.Ldloc, val4);
				iLProcessor.Emit(OpCodes.Brfalse, val12);
				iLProcessor.Emit(OpCodes.Ldloc, val4);
				iLProcessor.Emit(OpCodes.Ldlen);
				iLProcessor.Emit(OpCodes.Conv_I4);
				iLProcessor.Emit(OpCodes.Ldc_I4, j + 1);
				iLProcessor.Emit(OpCodes.Ble, val12);
				iLProcessor.Emit(OpCodes.Ldloc, val4);
				iLProcessor.Emit(OpCodes.Ldc_I4, j + 1);
				iLProcessor.Emit(OpCodes.Ldelem_Ref);
				iLProcessor.Emit(OpCodes.Stloc, val11);
				iLProcessor.Emit(OpCodes.Ldloc, val11);
				iLProcessor.Emit(OpCodes.Brfalse, val12);
				iLProcessor.Emit(OpCodes.Ldarg, val9);
				iLProcessor.Emit(OpCodes.Ldloc, val11);
				iLProcessor.Emit(OpCodes.Unbox_Any, elementType2);
				iLProcessor.Emit(elementType2.IsValueType ? OpCodes.Stobj : OpCodes.Stind_Ref, elementType2);
				iLProcessor.Append(val12);
			}
		}
		if (flag)
		{
			iLProcessor.Emit(OpCodes.Ret);
			return;
		}
		Instruction val13 = Instruction.Create(OpCodes.Nop);
		Instruction val14 = Instruction.Create(OpCodes.Ret);
		iLProcessor.Emit(OpCodes.Ldloc, val4);
		iLProcessor.Emit(OpCodes.Brfalse, val13);
		iLProcessor.Emit(OpCodes.Ldloc, val4);
		iLProcessor.Emit(OpCodes.Ldlen);
		iLProcessor.Emit(OpCodes.Conv_I4);
		iLProcessor.Emit(OpCodes.Brfalse, val13);
		iLProcessor.Emit(OpCodes.Ldloc, val4);
		iLProcessor.Emit(OpCodes.Ldc_I4_0);
		iLProcessor.Emit(OpCodes.Ldelem_Ref);
		iLProcessor.Emit(((MethodReference)m).ReturnType.IsValueType ? OpCodes.Unbox_Any : OpCodes.Castclass, ((MethodReference)m).ReturnType);
		iLProcessor.Emit(OpCodes.Br, val14);
		iLProcessor.Append(val13);
		if (((MethodReference)m).ReturnType.IsValueType)
		{
			iLProcessor.Emit(OpCodes.Ldloca, val5);
			iLProcessor.Emit(OpCodes.Initobj, ((MethodReference)m).ReturnType);
			iLProcessor.Emit(OpCodes.Ldloc, val5);
		}
		else
		{
			iLProcessor.Emit(OpCodes.Ldnull);
		}
		iLProcessor.Append(val14);
	}
}
