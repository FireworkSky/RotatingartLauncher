using System;
using System.IO;
using System.Linq;
using System.Reflection;
using Mono.Cecil;
using Mono.Cecil.Cil;
using MonoMod.Cil;
using Terraria;
using Terraria.GameContent.Drawing;
using Terraria.GameContent.NetModules;
using Terraria.Net;
using Microsoft.Xna.Framework;

namespace MobileServerPatch;

internal static class HostNetModulePatches
{
	private delegate void PacketOrig(NetManager self, NetPacket packet);
	private delegate void PacketHook(PacketOrig orig, NetManager self, NetPacket packet);
	private delegate bool ParticleOrig(NetParticlesModule self, BinaryReader reader, int userId);
	private delegate bool ParticleHook(ParticleOrig orig, NetParticlesModule self, BinaryReader reader, int userId);
	private delegate bool PingOrig(NetPingModule self, BinaryReader reader, int userId);
	private delegate bool PingHook(PingOrig orig, NetPingModule self, BinaryReader reader, int userId);
	private delegate void TriggerOrig(Vector2 position);
	private delegate void TriggerHook(TriggerOrig orig, Vector2 position);

	internal static void Install(Action<MethodInfo, Delegate> install,
		Action<MethodInfo, ILContext.Manipulator> installIL)
	{
		install(HostSession.Method(typeof(NetManager), "SendToServerOrLoopback", typeof(NetPacket)), (PacketHook)SendLocalRequest);
		install(HostSession.Method(typeof(NetManager), "SendToServerAndSelf", typeof(NetPacket)), (PacketHook)SendLocalRequest);
		install(HostSession.Method(typeof(NetManager), "BroadcastOrLoopback", typeof(NetPacket)), (PacketHook)BroadcastWithLocalVisuals);
		install(HostSession.Method(typeof(NetParticlesModule), "Deserialize", typeof(BinaryReader), typeof(int)), (ParticleHook)ReceiveParticles);
		install(HostSession.Method(typeof(NetPingModule), "Deserialize", typeof(BinaryReader), typeof(int)), (PingHook)ReceivePing);
		install(HostSession.Method(typeof(Main), "TriggerPing", typeof(Vector2)), (TriggerHook)TriggerPing);
		installIL(HostSession.Method(typeof(ParticleOrchestrator), "SpawnParticlesDirect", typeof(ParticleOrchestraType), typeof(ParticleOrchestraSettings)), il => {
			var reads = il.Body.Instructions.Where(instruction => instruction.OpCode == OpCodes.Ldsfld &&
				instruction.Operand is FieldReference field && field.DeclaringType.FullName == typeof(Main).FullName &&
				field.Name == nameof(Main.netMode)).ToArray();
			if (reads.Length != 1)
				throw new InvalidOperationException("Unsupported SpawnParticlesDirect: expected one netMode guard.");
			var cursor = new ILCursor(il);
			cursor.Goto(reads[0], MoveType.After);
			cursor.EmitDelegate<Func<int, int>>(value => HostSession.IsHosting ? 0 : value);
		});
	}

	private static void SendLocalRequest(PacketOrig orig, NetManager self, NetPacket packet)
	{
		if (!HostSession.IsHosting) { orig(self, packet); return; }
		// The host owns the server state already. Dispatch once with its real player
		// identity so pylon and creative handlers retain their server validation.
		self.SendToSelf(packet);
	}

	private static bool ReceivePing(PingOrig orig, NetPingModule self, BinaryReader reader, int userId)
	{
		if (!HostSession.IsHosting)
			return orig(self, reader, userId);
		Vector2 position = new(reader.ReadSingle(), reader.ReadSingle());
		NetManager.Instance.Broadcast(NetPingModule.Serialize(position), userId);
		if (userId != Main.myPlayer)
			Main.Pings.Add(position);
		return true;
	}
	private static void TriggerPing(TriggerOrig orig, Vector2 position)
	{
		orig(position);
		if (HostSession.IsHosting)
			NetManager.Instance.Broadcast(NetPingModule.Serialize(position), Main.myPlayer);
	}

	private static void BroadcastWithLocalVisuals(PacketOrig orig, NetManager self, NetPacket packet)
	{
		if (!HostSession.IsHosting) { orig(self, packet); return; }
		long position = packet.Reader.BaseStream.Position;
		try {
			packet.Reader.BaseStream.Position = 5; // packet length, message ID, module ID
			if (packet.Id == self.GetId<NetParticlesModule>())
				SpawnParticles(packet.Reader);
			else if (packet.Id == self.GetId<NetAmbienceModule>())
				self.GetModule<NetAmbienceModule>().Deserialize(packet.Reader, Main.myPlayer);
		}
		finally { packet.Reader.BaseStream.Position = position; }
		// Other broadcasts (notably pylon additions) describe state that has
		// already changed in this shared world, so they must not be replayed.
		orig(self, packet);
	}

	private static bool ReceiveParticles(ParticleOrig orig, NetParticlesModule self, BinaryReader reader, int userId)
	{
		if (!HostSession.IsHosting)
			return orig(self, reader, userId);
		long position = reader.BaseStream.Position;
		ParticleOrchestraType type = (ParticleOrchestraType)reader.ReadByte();
		ParticleOrchestraSettings settings = default;
		settings.DeserializeFrom(reader);
		reader.BaseStream.Position = position;
		// Keep the original server relay, which excludes the invoking player.
		bool result = orig(self, reader, userId);
		if (result)
			ParticleOrchestrator.SpawnParticlesDirect(type, settings);
		return result;
	}

	private static void SpawnParticles(BinaryReader reader)
	{
		ParticleOrchestraType type = (ParticleOrchestraType)reader.ReadByte();
		ParticleOrchestraSettings settings = default;
		settings.DeserializeFrom(reader);
		ParticleOrchestrator.SpawnParticlesDirect(type, settings);
	}
}
