using System;
using System.Reflection;
using Terraria;
using Terraria.ID;
using Terraria.Localization;

namespace MobileServerPatch;

internal static class HostInteractionPatches
{
	private delegate void OpenOrig(Player self, int x, int y, int chest);
	private delegate void OpenHook(OpenOrig orig, Player self, int x, int y, int chest);
	private delegate void SendOrig(int type, int to, int ignore, NetworkText text, int number, float n2, float n3, float n4, int n5, int n6, int n7);
	private delegate void SendHook(SendOrig orig, int type, int to, int ignore, NetworkText text, int number, float n2, float n3, float n4, int n5, int n6, int n7);
	internal static void Install(Action<MethodInfo, Delegate> install)
	{
		install(HostSession.Method(typeof(Player), "OpenChest", typeof(int), typeof(int), typeof(int)), (OpenHook)OpenChest);
		install(HostSession.Method(typeof(NetMessage), "SendData", typeof(int), typeof(int), typeof(int), typeof(NetworkText), typeof(int),
			typeof(float), typeof(float), typeof(float), typeof(int), typeof(int), typeof(int)), (SendHook)SendData);
	}
	private static void OpenChest(OpenOrig orig, Player self, int x, int y, int chest)
	{
		if (HostSession.IsHosting && self.whoAmI == Main.myPlayer && chest >= 0) {
			int usingPlayer = Chest.UsingChest(chest);
			if (usingPlayer >= 0 && usingPlayer != self.whoAmI)
				return;
		}
		orig(self, x, y, chest);
		if (HostSession.IsHosting && self.whoAmI == Main.myPlayer)
			NetMessage.SendData(MessageID.SyncPlayerChestIndex, -1, Main.myPlayer, null, Main.myPlayer, self.chest);
	}
	private static void SendData(SendOrig orig, int type, int to, int ignore, NetworkText text, int number,
		float n2, float n3, float n4, int n5, int n6, int n7)
	{
		// TrySyncingMyPlayer ends inventory deltas with this client-to-server
		// acknowledgement. The shared host inventory already is authoritative;
		// guests have no handler for packet 138 and must only receive the deltas.
		if (HostSession.IsHosting && type == MessageID.ClientSyncedInventory)
			return;
		if (HostSession.IsHosting && type == MessageID.SyncPlayerChest && to == -1) {
			// Packet 33 is a client request/server UI response, not a broadcast.
			// Reuse the server's committed-name and chest-user packets instead.
			if (n2 != 0f) {
				int chest = number >= 0 ? number : Main.clientPlayer.chest;
				if (chest >= 0 && chest < Main.chest.Length && Main.chest[chest] is Chest container)
					orig(MessageID.ChestName, -1, Main.myPlayer, null, chest, container.x, container.y, 0, 0, 0, 0);
			}
			orig(MessageID.SyncPlayerChestIndex, -1, Main.myPlayer, null, Main.myPlayer, Main.LocalPlayer.chest, 0, 0, 0, 0, 0);
			return;
		}
		orig(type, to, ignore, text, number, n2, n3, n4, n5, n6, n7);
	}
}
