using System;
using System.Linq;
using System.Reflection;
using HarmonyLib;

namespace SteamPatch;

public static class TmlGates
{
	private static Harmony _harmony;

	private static bool _done;

	public static void Schedule()
	{
		Assembly assembly = AppDomain.CurrentDomain.GetAssemblies().FirstOrDefault((Assembly a) => a.GetName().Name == "tModLoader");
		if (assembly != null)
		{
			Apply(assembly);
			return;
		}
		AppDomain.CurrentDomain.AssemblyLoad += OnLoad;
		Console.WriteLine("[SteamPatch] 等待 tModLoader 程序集加载...");
	}

	private static void OnLoad(object sender, AssemblyLoadEventArgs args)
	{
		if (!(args.LoadedAssembly.GetName().Name != "tModLoader") && !_done)
		{
			AppDomain.CurrentDomain.AssemblyLoad -= OnLoad;
			Apply(args.LoadedAssembly);
		}
	}

	private static void Apply(Assembly tml)
	{
		//IL_000b: Unknown result type (might be due to invalid IL or missing references)
		//IL_0015: Expected Obj, but got Unknown
		//IL_005a: Unknown result type (might be due to invalid IL or missing references)
		//IL_0066: Expected Obj, but got Unknown
		_done = true;
		try
		{
			_harmony = new Harmony("com.ralaunch.steampatch");
			MethodInfo methodInfo = tml.GetType("Terraria.Social.Steam.SteamedWraps")?.GetMethod("Initialize", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
			if (methodInfo != null)
			{
				_harmony.Patch((MethodBase)methodInfo, (HarmonyMethod)null, new HarmonyMethod(typeof(TmlGates).GetMethod("AfterSteamInitialize", BindingFlags.Static | BindingFlags.NonPublic)), (HarmonyMethod)null, (HarmonyMethod)null);
				Console.WriteLine("[SteamPatch] 已挂 SteamedWraps.Initialize 观测点");
			}
			else
			{
				Console.WriteLine("[SteamPatch] 未找到 SteamedWraps.Initialize");
			}
			if (Environment.GetEnvironmentVariable("RAL_STEAM_KEEP_LEGACY_GATES") != "1")
			{
				UnpatchLegacyGate(tml, "Terraria.Social.Steam.WorkshopHelper+QueryHelper+AQueryInstance", "TryGetModDownloadItem", "工坊搜索假成功");
				UnpatchLegacyGate(tml, "Terraria.ModLoader.Core.ModOrganizer", "DetectAbnormalSteamWorkshopDownloads", "异常下载检测拦截");
			}
		}
		catch (Exception ex)
		{
			Console.WriteLine("[SteamPatch] TmlGates 失败: " + ex.GetType().Name + ": " + ex.Message);
		}
	}

	private static void UnpatchLegacyGate(Assembly tml, string typeName, string methodName, string what)
	{
		try
		{
			MethodInfo methodInfo = tml.GetType(typeName)?.GetMethod(methodName, BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
			if (methodInfo == null)
			{
				Console.WriteLine("[SteamPatch] 旧门未找到（跳过）: " + typeName + "." + methodName);
				return;
			}
			if (Harmony.GetPatchInfo((MethodBase)methodInfo) == null)
			{
				Console.WriteLine("[SteamPatch] " + what + ": 该方法上没有补丁，跳过");
				return;
			}
			_harmony.Unpatch((MethodBase)methodInfo, (HarmonyPatchType)0, "com.ralaunch.tmlpatch");
			Console.WriteLine($"[SteamPatch] 已撤掉旧门（仅此方法）: {what} -> {typeName}.{methodName}");
		}
		catch (Exception ex)
		{
			Console.WriteLine($"[SteamPatch] 撤旧门失败（忽略）: {what}: {ex.GetType().Name}: {ex.Message}");
		}
	}

	private static void AfterSteamInitialize()
	{
		try
		{
			Type type = Type.GetType("Terraria.Social.Steam.SteamedWraps, tModLoader", throwOnError: false);
			if (!(type == null))
			{
				object value = type.GetProperty("SteamClient", BindingFlags.Static | BindingFlags.Public)?.GetValue(null);
				object value2 = type.GetProperty("SteamAvailable", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic)?.GetValue(null);
				object value3 = Type.GetType("Terraria.Social.SocialAPI, tModLoader", throwOnError: false)?.GetProperty("Mode", BindingFlags.Static | BindingFlags.Public)?.GetValue(null);
				Console.WriteLine($"[SteamPatch] tML Steam 状态: SteamClient={value} SteamAvailable={value2} SocialMode={value3}");
			}
		}
		catch (Exception ex)
		{
			Console.WriteLine("[SteamPatch] 观测点异常: " + ex.Message);
		}
	}
}
