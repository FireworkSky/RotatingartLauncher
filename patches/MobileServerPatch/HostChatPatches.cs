using System;
using System.Reflection;
using Microsoft.Xna.Framework;
using Terraria;
using Terraria.Chat;
using Terraria.GameContent.NetModules;
using Terraria.Localization;
using Terraria.Net;

namespace MobileServerPatch;

internal static class HostChatPatches
{
	private delegate void MessageOrig(byte author, NetworkText text, Color color, int player);
	private delegate void MessageHook(MessageOrig orig, byte author, NetworkText text, Color color, int player);
	private delegate void DisplayOrig(NetworkText text, Color color, int player);
	private delegate void DisplayHook(DisplayOrig orig, NetworkText text, Color color, int player);
	internal static void Install(Action<MethodInfo, Delegate> install)
	{
		install(HostSession.Method(typeof(ChatHelper), "BroadcastChatMessageAs", typeof(byte), typeof(NetworkText), typeof(Color), typeof(int)), (MessageHook)Broadcast);
		install(HostSession.Method(typeof(ChatHelper), "SendChatMessageToClientAs", typeof(byte), typeof(NetworkText), typeof(Color), typeof(int)), (MessageHook)Send);
		install(HostSession.Method(typeof(ChatHelper), "DisplayMessageOnClient", typeof(NetworkText), typeof(Color), typeof(int)), (DisplayHook)Display);
	}
	private static void Broadcast(MessageOrig orig, byte author, NetworkText text, Color color, int excluded)
	{
		if (!HostSession.IsHosting) { orig(author, text, color, excluded); return; }
		NetManager.Instance.Broadcast(NetTextModule.SerializeServerMessage(text, color, author),
			i => i != Main.myPlayer && ChatHelper.OnlySendToPlayersWhoAreLoggedIn(i), excluded);
		if (excluded != Main.myPlayer)
			ChatHelper.DisplayMessage(text, color, author);
	}
	private static void Send(MessageOrig orig, byte author, NetworkText text, Color color, int player)
	{
		if (!HostSession.IsHosting) { orig(author, text, color, player); return; }
		Deliver(author, text, color, player);
	}
	private static void Display(DisplayOrig orig, NetworkText text, Color color, int player)
	{
		if (!HostSession.IsHosting) { orig(text, color, player); return; }
		Deliver(byte.MaxValue, text, color, player);
	}
	private static void Deliver(byte author, NetworkText text, Color color, int player)
	{
		if (player == Main.myPlayer)
			ChatHelper.DisplayMessage(text, color, author);
		else
			NetManager.Instance.SendToClient(NetTextModule.SerializeServerMessage(text, color, author), player);
	}
}
