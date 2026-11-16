using System;
using System.Linq;
using System.Reflection;
using Mono.Cecil;
using Mono.Cecil.Cil;
using MonoMod.Cil;
using Terraria;
using Terraria.GameContent.NetModules;
using System.IO;

namespace MobileServerPatch;

internal static class HostNetworkPatches
{
	internal static void Install(Action<MethodInfo, ILContext.Manipulator> install)
	{
		// These methods' dedicated flag reads select server protocol handling,
		// not presentation. Keep the actual global dedServ false for all UI.
		install(HostSession.Method(typeof(NetMessage), "CheckBytes", typeof(int)), il => EnableServerReads(il, 2));
		install(HostSession.Method(typeof(MessageBuffer), "GetData", typeof(int), typeof(int), typeof(int).MakeByRefType()),
			il => EnableServerReads(il, 1));
		install(HostSession.Method(typeof(NetTextModule), "Deserialize", typeof(BinaryReader), typeof(int)),
			il => EnableServerReads(il, 1));
		install(HostSession.Method(typeof(NetTeleportPylonModule), "Deserialize", typeof(BinaryReader), typeof(int)),
			il => EnableServerReads(il, 2));
		foreach (Type type in new[] { typeof(NetCreativeUnlocksModule), typeof(NetBestiaryModule), typeof(NetAmbienceModule) })
			install(HostSession.Method(type, "Deserialize", typeof(BinaryReader), typeof(int)),
				il => EnableServerReads(il, 1, remoteOnly: true));
		install(HostSession.Method(typeof(Main), "UpdateServer"), SkipLocalRemoteMaintenance);
	}
	private static void SkipLocalRemoteMaintenance(ILContext il)
	{
		var reads = il.Body.Instructions.Where(i => i.OpCode == OpCodes.Ldfld && i.Operand is FieldReference f &&
			f.DeclaringType.FullName == typeof(RemoteClient).FullName && f.Name == nameof(RemoteClient.IsActive)).ToArray();
		if (reads.Length != 2)
			throw new InvalidOperationException("Unsupported UpdateServer remote maintenance guards.");
		var cursor = new ILCursor(il);
		foreach (var read in reads) {
			cursor.Goto(read, MoveType.Before);
			cursor.Emit(OpCodes.Dup);
			cursor.Goto(read, MoveType.After);
			cursor.EmitDelegate<Func<RemoteClient, bool, bool>>((client, active) =>
				HostSession.IsHosting && client.Id == Main.myPlayer ? false : active);
		}
	}
	private static void EnableServerReads(ILContext il, int expected, bool remoteOnly = false)
	{
		var reads = il.Body.Instructions.Where(i => i.OpCode == OpCodes.Ldsfld &&
			i.Operand is FieldReference f && f.DeclaringType.FullName == typeof(Main).FullName && f.Name == nameof(Main.dedServ)).ToArray();
		if (reads.Length != expected)
			throw new InvalidOperationException($"Unsupported server protocol IL in {il.Method.FullName}.");
		var cursor = new ILCursor(il);
		foreach (var read in reads) {
			cursor.Goto(read, MoveType.After);
			if (remoteOnly) {
				cursor.Emit(OpCodes.Ldarg_2);
				cursor.EmitDelegate<Func<bool, int, bool>>((original, sender) => original || (HostSession.IsHosting && sender != Main.myPlayer));
			}
			else
				cursor.EmitDelegate<Func<bool, bool>>(original => original || HostSession.IsHosting);
		}
	}
}
