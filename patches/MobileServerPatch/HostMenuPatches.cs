using System;
using System.Reflection;
using Microsoft.Xna.Framework;
using Terraria;
using Terraria.ID;

namespace MobileServerPatch;

internal static class HostMenuPatches
{
	private delegate void MainOrig(Main self);
	private delegate void MainHook(MainOrig orig, Main self);
	private delegate void VoidOrig();
	private delegate void VoidHook(VoidOrig orig);
	private delegate void SaveOrig(Action callback);
	private delegate void SaveHook(SaveOrig orig, Action callback);
	private delegate void CallbackOrig(object context);
	private delegate void CallbackHook(CallbackOrig orig, object context);
	private delegate void ExitOrig(Main self, object sender, EventArgs args);
	private delegate void ExitHook(ExitOrig orig, Main self, object sender, EventArgs args);
	private static string pendingPassword;
	private static int pendingPort;
	private static bool launchingRoom;

	internal static void Install(Action<MethodInfo, Delegate> install)
	{
		InstallEntry(install);
		install(HostSession.Method(typeof(WorldGen), "FinishPlayWorld"), (VoidHook)FinishLoading);
		install(HostSession.Method(typeof(WorldGen), "playWorld"), (VoidHook)PlayWorld);
		install(HostSession.Method(typeof(WorldGen), "SaveAndQuit", typeof(Action)), (SaveHook)SaveAndQuit);
		install(HostSession.Method(typeof(WorldGen), "SaveAndQuitCallBack", typeof(object)), (CallbackHook)SaveCallback);
		install(HostSession.Method(typeof(WorldGen), "JustQuit"), (VoidHook)JustQuit);
		install(HostSession.Method(typeof(Main), "QuitGame"), (MainHook)QuitGame);
		install(HostSession.Method(typeof(Main), "Main_Exiting", typeof(object), typeof(EventArgs)), (ExitHook)Exiting);
	}

	// Retain this guard even if the version check disables the rest of the feature:
	// selecting Host & Play must never silently launch the original child server.
	internal static void InstallEntry(Action<MethodInfo, Delegate> install) =>
		install(HostSession.Method(typeof(Main), "OnSubmitServerPassword"), (MainHook)CreateRoom);

	private static void CreateRoom(MainOrig orig, Main self)
	{
		if (!HostSession.IsAvailable) {
			Utils.ShowFancyErrorMessage(HostSession.UnavailableReason, 0);
			return;
		}
		if (HostSession.IsHosting)
			return;
		pendingPassword = Netplay.ServerPassword ?? string.Empty;
		pendingPort = Netplay.ListenPort;
		Main.netMode = NetmodeID.SinglePlayer;
		Main.menuMode = 10;
		launchingRoom = true;
		try { WorldGen.playWorld(); }
		finally { launchingRoom = false; }
	}
	private static void PlayWorld(VoidOrig orig)
	{
		if (!launchingRoom)
			Reset(); // A later singleplayer selection cancels a previously failed load.
		orig();
	}

	private static void FinishLoading(VoidOrig orig)
	{
		orig();
		if (pendingPassword == null)
			return;
		string password = pendingPassword;
		pendingPassword = null;
		if (HostSession.StartFromCurrentWorld(password, pendingPort))
			Main.NewText(HostSession.ConnectionHint(), Color.LightGreen);
		else
			Main.NewText(HostSession.LastError, Color.OrangeRed);
	}

	private static void SaveAndQuit(SaveOrig orig, Action callback)
	{
		if (HostSession.IsHosting) {
			Main.gameMenu = true; // Freeze simulation before the asynchronous save starts.
			HostSession.Shutdown(save: false);
		}
		Reset();
		orig(callback); // The original now captures mode 0 and saves the shared world.
	}

	private static void SaveCallback(CallbackOrig orig, object context)
	{
		if (HostSession.IsHosting) {
			// Covers mods that invoke the callback directly from a worker.
			Main.QueueMainThreadAction(() => {
				Main.gameMenu = true;
				HostSession.Shutdown(save: false);
				orig(context);
			});
			return;
		}
		orig(context);
	}

	private static void JustQuit(VoidOrig orig)
	{
		if (HostSession.IsHosting)
			HostSession.Shutdown(save: true);
		Reset();
		orig();
	}
	private static void QuitGame(MainOrig orig, Main self)
	{
		if (HostSession.IsHosting)
			HostSession.Shutdown(save: true);
		Reset();
		orig(self);
	}
	private static void Exiting(ExitOrig orig, Main self, object sender, EventArgs args)
	{
		if (HostSession.IsHosting)
			HostSession.Shutdown(save: true);
		Reset();
		orig(self, sender, args);
	}
	internal static void Reset() => pendingPassword = null;
}
