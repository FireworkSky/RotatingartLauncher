using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using Microsoft.Xna.Framework;
using MonoMod.Cil;
using MonoMod.RuntimeDetour;
using Terraria;
using Terraria.ID;
using Terraria.IO;
using Terraria.ModLoader;
using Terraria.Net.Sockets;

namespace MobileServerPatch;

internal static class HostSession
{
	private enum SessionState { Idle, Starting, Playing, Stopping }
	private delegate void VoidOrig();
	private delegate void VoidHook(VoidOrig orig);
	private delegate void SectionOrig(int playerIndex, Vector2 position, int fluff);
	private delegate void SectionHook(SectionOrig orig, int playerIndex, Vector2 position, int fluff);
	private static readonly List<IDisposable> hooks = new();
	private static readonly Guid SupportedAssembly = new("7b8a9f2a-b51f-4957-bcbc-cf6609b474c8");
	private static readonly FieldInfo NetworkMods = typeof(ModNet).GetField("netMods", BindingFlags.NonPublic | BindingFlags.Static);
	private static readonly FieldInfo ModNetworkID = typeof(Mod).GetField("netID", BindingFlags.NonPublic | BindingFlags.Instance);
	private static Action<RemoteClient, string> terminate;
	private static SessionState state;
	private static HostTransport transport;
	private static Snapshot snapshot;
	private static int hostSlot;
	private static bool broadcasting;
	private static int syncFrame;
	private static Vector2 lastPosition;
	private static Vector2 lastVelocity;
	internal static string UnavailableReason { get; private set; }
	internal static string LastError { get; private set; }
	internal static bool IsHosting => state is SessionState.Starting or SessionState.Playing;
	internal static bool IsAvailable => UnavailableReason == null;
	internal static string ConnectionHint()
	{
		string address;
		try {
			address = string.Join(" / ", Dns.GetHostAddresses(Dns.GetHostName())
				.Where(ip => ip.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip)).Select(ip => ip.ToString()));
		}
		catch (SocketException) { address = string.Empty; }
		return $"房间已创建。加入地址：{(address.Length == 0 ? "本机局域网 IP" : address)}，端口 {Netplay.ListenPort}。";
	}

	internal static void Load()
	{
		if (hooks.Count > 0) return;
		UnavailableReason = null;
		if (Main.dedServ)
			return;
		try {
			if (typeof(Main).Module.ModuleVersionId != SupportedAssembly)
				throw new NotSupportedException("此 tModLoader 版本未经适配。需要 Terraria 1.4.4.9 / tModLoader 2026.09.3.1。");
			terminate = (Action<RemoteClient, string>)Method(typeof(RemoteClient), "SetPendingTermination", typeof(string))
				.CreateDelegate(typeof(Action<RemoteClient, string>));
			HostVisualPatches.Install(AddILHook);
			HostPlayerPatches.Install(AddILHook);
			HostNetworkPatches.Install(AddILHook);
			HostChatPatches.Install(AddHook);
			HostNetModulePatches.Install(AddHook, AddILHook);
			HostInteractionPatches.Install(AddHook);
			HostMenuPatches.Install(AddHook);
			HostCommandPatch.Install(AddHook);
			AddHook(Method(typeof(Netplay), "UpdateInMainThread"), (VoidHook)UpdateNetwork);
			AddHook(Method(typeof(RemoteClient), "CheckSection", typeof(int), typeof(Vector2), typeof(int)), (SectionHook)CheckSection);
			Patcher.Log($"Integrated hosting ready; tML MVID={typeof(Main).Module.ModuleVersionId}, hooks={hooks.Count}.");
		}
		catch (Exception ex) {
			DisposeHooks();
			UnavailableReason = ex.Message;
			Patcher.Log("Integrated hosting disabled; hook installation was rolled back.", ex);
			HostMenuPatches.InstallEntry(AddHook);
			HostCommandPatch.Install(AddHook);
		}
	}

	internal static MethodInfo Method(Type type, string name, params Type[] arguments) =>
		type.GetMethod(name, BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static | BindingFlags.Instance,
			null, arguments, null) ?? throw new MissingMethodException(type.FullName, name);
	private static void AddHook(MethodInfo target, Delegate hook) => hooks.Add(new Hook(target, hook));
	private static void AddILHook(MethodInfo target, ILContext.Manipulator patch) => hooks.Add(new ILHook(target, patch));
	private static void DisposeHooks()
	{
		foreach (IDisposable hook in hooks.AsEnumerable().Reverse())
			hook.Dispose();
		hooks.Clear();
	}

	internal static void Unload()
	{
		try { Shutdown(save: true); }
		finally {
			DisposeHooks();
			HostMenuPatches.Reset();
		}
	}

	internal static bool StartFromCurrentWorld(string password = "", int port = 7777)
	{
		LastError = null;
		if (!IsAvailable || state != SessionState.Idle || Main.dedServ || Main.gameMenu ||
			Main.netMode != NetmodeID.SinglePlayer || Main.ActiveWorldFileData == null || Main.ActivePlayerFileData == null) {
			LastError = UnavailableReason ?? "请先进入单人世界，或从多人游戏的创建房间入口启动。";
			return false;
		}
		if (port < 1 || port > ushort.MaxValue || Main.myPlayer < 0 || Main.myPlayer >= 255) {
			LastError = "端口或本地玩家编号无效。";
			return false;
		}

		snapshot = new Snapshot();
		hostSlot = Main.myPlayer;
		state = SessionState.Starting;
		try {
			// Bind before changing any world/player state; no half-started room on a busy port.
			transport = new HostTransport(port);
			Netplay.ListenPort = port;
			Netplay.ServerPassword = password ?? string.Empty;
			Netplay.ServerIP = IPAddress.Any;
			Netplay.UseUPNP = false;
			Netplay.Disconnect = false;
			Netplay.IsListening = true;
			Netplay.HasClients = false;
			NetMessage.buffer = new MessageBuffer[257];
			for (int i = 0; i < NetMessage.buffer.Length; i++)
				NetMessage.buffer[i] = new MessageBuffer { whoAmI = i };
			Netplay.Clients = new RemoteClient[256];
			for (int i = 0; i < Netplay.Clients.Length; i++) {
				// Id must precede Reset; otherwise every Reset replaces player zero.
				RemoteClient client = Netplay.Clients[i] = new RemoteClient { Id = i, ReadBuffer = new byte[ushort.MaxValue] };
				if (i != hostSlot)
					client.Reset();
				else {
					client.Socket = new LocalHostSocket(transport.CompleteSend);
					client.State = 10;
					client.IsActive = true; // Include the local host in join/name identity checks.
					client.Name = Main.LocalPlayer.name;
					client.IsAnnouncementCompleted = true;
					FillHostSections();
				}
			}
			Main.LocalPlayer.active = true;
			Array.Clear(Main.countsAsHostForGameplay);
			Main.countsAsHostForGameplay[hostSlot] = true;
			Main.clientPlayer = Main.LocalPlayer.clientClone();
			syncFrame = 0;
			lastPosition = Main.LocalPlayer.position;
			lastVelocity = Main.LocalPlayer.velocity;
			Main.netMode = NetmodeID.Server;
			Method(typeof(ModNet), "AssignNetIDs").Invoke(null, null);
			broadcasting = true;
			Netplay.StartBroadCasting();
			state = SessionState.Playing;
			Patcher.Log($"Integrated room opened on TCP {port}; host player {hostSlot}; one shared world.");
			return true;
		}
		catch (Exception ex) {
			LastError = $"创建房间失败：{ex.GetBaseException().Message}";
			Patcher.Log("Integrated host startup failed.", ex);
			Shutdown(save: false);
			return false;
		}
	}

	internal static void Shutdown(bool save)
	{
		if (state is SessionState.Idle or SessionState.Stopping)
			return;
		state = SessionState.Stopping;
		Netplay.Disconnect = true;
		void Cleanup(Action action) {
			try { action(); }
			catch (Exception ex) { Patcher.Log("Error while closing room resource.", ex); }
		}
		try {
			Cleanup(() => transport?.Dispose());
			if (broadcasting) {
				Cleanup(Netplay.StopBroadCasting);
				broadcasting = false;
			}
			if (Netplay.Clients != snapshot.Clients) {
				for (int i = 0; i < Netplay.Clients.Length; i++) {
					RemoteClient client = Netplay.Clients[i];
					if (client == null || i == hostSlot)
						continue;
					Cleanup(() => client.Socket?.Close());
					if (Main.player[i].active) {
						Main.player[i].active = false;
						int disconnected = i;
						Cleanup(() => Player.Hooks.PlayerDisconnect(disconnected));
					}
				}
			}
			Cleanup(() => transport?.DrainCompletions());
		}
		finally {
			transport = null;
			snapshot.Restore();
			snapshot = null;
			state = SessionState.Idle;
		}
		Patcher.Log($"Room closed; singleplayer state restored; save={save}.");
		if (save) {
			Player.SavePlayer(Main.ActivePlayerFileData);
			WorldFile.SaveWorld();
		}
	}

	private static void UpdateNetwork(VoidOrig orig)
	{
		if (!IsHosting) { orig(); return; }
		if (Main.gameMenu)
			return;
		try {
			transport.DrainCompletions();
			while (transport.TryAccept(out ISocket socket)) {
				int slot = -1;
				for (int i = 0; i < Math.Min(255, Main.maxNetPlayers); i++)
					if (i != hostSlot && !Netplay.Clients[i].IsConnected() && !Netplay.Clients[i].IsActive && !Netplay.Clients[i].PendingTermination) { slot = i; break; }
				if (slot < 0) { socket.Close(); continue; }
				Netplay.Clients[slot].Reset();
				Netplay.Clients[slot].Socket = socket;
			}
			int connected = 0;
			for (int i = 0; i < 256; i++) {
				if (i == hostSlot)
					continue;
				RemoteClient client = Netplay.Clients[i];
				if (client.PendingTermination) {
					// There is no parser worker to wait for; this game thread owns
					// both parsing and cleanup, including failed reads with no bytes.
					client.PendingTerminationApproved = true;
					{
						client.State = 0;
						NetMessage.SyncDisconnectedPlayer(i);
						client.Reset();
						Main.countsAsHostForGameplay[i] = false;
					}
				}
				else if (client.IsConnected()) { client.Update(); connected++; }
				else if (client.IsActive) {
					terminate(client, "Connection lost");
					client.PendingTerminationApproved = true;
				}
				if (NetMessage.buffer[i].checkBytes)
					NetMessage.CheckBytes(i);
			}
			Netplay.HasClients = connected > 0;
			syncFrame++;
			Player host = Main.LocalPlayer;
			if (syncFrame % 120 == 0 || (syncFrame % 3 == 0 && (host.position != lastPosition || host.velocity != lastVelocity))) {
				NetMessage.SendData(MessageID.PlayerControls, -1, hostSlot, null, hostSlot);
				lastPosition = host.position;
				lastVelocity = host.velocity;
			}
			// Server Update leaves reservations of active players alone. Run the
			// client owner reassessment only for the integrated local player.
			for (int i = 0; i < Main.maxItems; i++)
				if (Main.item[i].active && Main.item[i].playerIndexTheItemIsReservedFor == hostSlot)
					Main.item[i].FindOwner(i);
		}
		catch (Exception ex) {
			Patcher.Log("Room networking failed; returning to singleplayer.", ex);
			Shutdown(save: false);
			Main.NewText($"房间已关闭：{ex.GetBaseException().Message}", Color.OrangeRed);
		}
	}

	private static void FillHostSections()
	{
		bool[,] sections = Netplay.Clients[hostSlot].TileSections;
		for (int x = 0; x < sections.GetLength(0); x++)
			for (int y = 0; y < sections.GetLength(1); y++)
				sections[x, y] = true;
	}
	private static void CheckSection(SectionOrig orig, int playerIndex, Vector2 position, int fluff)
	{
		if (IsHosting && playerIndex == hostSlot) { FillHostSections(); return; }
		orig(playerIndex, position, fluff);
	}

	private sealed class Snapshot
	{
		internal readonly RemoteClient[] Clients = Netplay.Clients;
		private readonly MessageBuffer[] buffers = NetMessage.buffer;
		private readonly Player[] players = (Player[])Main.player.Clone();
		private readonly Player clientPlayer = Main.clientPlayer;
		private readonly int localPlayer = Main.myPlayer;
		private readonly int mode = Main.netMode;
		private readonly bool dedicated = Main.dedServ;
		private readonly bool disconnect = Netplay.Disconnect;
		private readonly bool listening = Netplay.IsListening;
		private readonly bool hasClients = Netplay.HasClients;
		private readonly bool upnp = Netplay.UseUPNP;
		private readonly int port = Netplay.ListenPort;
		private readonly string password = Netplay.ServerPassword;
		private readonly IPAddress ip = Netplay.ServerIP;
		private readonly bool[] hostFlags = (bool[])Main.countsAsHostForGameplay.Clone();
		private readonly object netMods = NetworkMods.GetValue(null);
		private readonly (Mod Mod, object ID)[] modIDs = ModLoader.Mods.Select(mod => (mod, ModNetworkID.GetValue(mod))).ToArray();
		internal void Restore()
		{
			Netplay.Clients = Clients;
			NetMessage.buffer = buffers;
			Array.Copy(players, Main.player, players.Length);
			Main.clientPlayer = clientPlayer;
			Main.myPlayer = localPlayer;
			Main.netMode = mode;
			Main.dedServ = dedicated;
			Netplay.Disconnect = disconnect;
			Netplay.IsListening = listening;
			Netplay.HasClients = hasClients;
			Netplay.UseUPNP = upnp;
			Netplay.ListenPort = port;
			Netplay.ServerPassword = password;
			Netplay.ServerIP = ip;
			Array.Copy(hostFlags, Main.countsAsHostForGameplay, hostFlags.Length);
			NetworkMods.SetValue(null, netMods);
			foreach (var (mod, id) in modIDs)
				ModNetworkID.SetValue(mod, id);
		}
	}
}
