using System;
using System.Linq;
using System.Reflection;
using Mono.Cecil;
using Mono.Cecil.Cil;
using MonoMod.Cil;
using Terraria;

namespace MobileServerPatch;

internal static class HostVisualPatches
{
	internal static void Install(Action<MethodInfo, ILContext.Manipulator> install)
	{
		// Each offset identifies an audited read in the supported tModLoader assembly.
		// Project the value on the evaluation stack; never change the shared field.
		InstallMethod(install, "DoUpdate",
			(0x0920, 0), (0x0936, 0), // Respect menu/loading gates on the graphical host.
			(0x094a, 0), // Let rendering interpolate the local rain opacity.
			(0x0963, 1), // Broadcast changes to the host's player through the server serializer.
			(0x0a90, 0), (0x0ab5, 0), (0x0ba5, 0));
		InstallMethod(install, "DoUpdateInWorld",
			(0x00a3, 0), (0x0540, 0), (0x058b, 0));
		InstallMethod(install, "DoDraw",
			(0x0a95, 0), (0x0b56, 0), (0x0c15, 0),
			(0x0cc1, 0), (0x0e99, 0), (0x1260, 0));
		InstallMethod(install, "DoDraw_UpdateCameraPosition", (0x0029, 0));
		InstallMethod(install, "DrawStarsInBackground", (0x0000, 0));
		InstallMethod(install, "UpdateAtmosphereTransparencyToSkyColor", (0x0082, 0));
		InstallMethod(install, "DoUpdate_AutoSave", (0x0092, 0));
		InstallMethod(install, "DoUpdate_HandleChat", (0x01db, 0));
		InstallMethod(install, "DrawInventory", (0x09e2, 1), (0x0b30, 1), (0x10a6, 1));
		InstallMethod(install, "SubmitSignText", (0x0037, 1));
		InstallMethod(install, "GUIChatDrawInner", (0x1933, 0));
		InstallMethod(install, "get_ShouldPVPDraw", (0x0000, 1));
	}

	private static void InstallMethod(Action<MethodInfo, ILContext.Manipulator> install,
		string methodName, params (int Offset, int Role)[] projections)
	{
		MethodInfo method = typeof(Main).GetMethod(methodName,
			BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static | BindingFlags.Instance)
			?? throw new MissingMethodException(typeof(Main).FullName, methodName);
		install(method, il => ProjectReads(il, projections));
	}

	private static void ProjectReads(ILContext il, (int Offset, int Role)[] projections)
	{
		// Capture every original instruction before inserting delegates so earlier
		// insertions cannot change the locations used by subsequent projections.
		Instruction[] originalReads = projections.Select(projection => {
			Instruction instruction = il.Body.Instructions.SingleOrDefault(candidate => candidate.Offset == projection.Offset);
			if (instruction == null || instruction.OpCode != OpCodes.Ldsfld ||
				instruction.Operand is not FieldReference field ||
				field.DeclaringType.FullName != typeof(Main).FullName ||
				field.Name != nameof(Main.netMode) || field.FieldType.FullName != "System.Int32") {
				throw new InvalidOperationException(
					$"Unsupported tModLoader IL: {il.Method.FullName} at IL_{projection.Offset:x4} must read Main.netMode.");
			}

			return instruction;
		}).ToArray();

		var cursor = new ILCursor(il);
		for (int index = 0; index < projections.Length; index++) {
			int desiredRole = projections[index].Role;
			cursor.Goto(originalReads[index], MoveType.After);
			cursor.EmitDelegate<Func<int, int>>(value => HostSession.IsHosting ? desiredRole : value);
		}
	}
}
