using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Runtime.Loader;
using SteamPatch;

internal class StartupHook
{
	public static void Initialize()
	{
		try
		{
			Console.SetOut(new StreamWriter(Console.OpenStandardOutput())
			{
				AutoFlush = true
			});
			if (Environment.GetEnvironmentVariable("RAL_STEAM_CHILD") == "1")
			{
				return;
			}
			AppDomain.CurrentDomain.UnhandledException += (object _, UnhandledExceptionEventArgs e) =>
			{
				Console.WriteLine("[SteamPatch] 未处理异常: " + e.ExceptionObject);
			};
			string patchDir = Path.GetDirectoryName(typeof(StartupHook).Assembly.Location) ?? ".";
			AssemblyLoadContext.Default.Resolving += EmbeddedAssemblies.Resolve;
			Console.WriteLine("========================================");
			Console.WriteLine("[SteamPatch] RAL Steam Workshop 支持（匿名 Steam3 + 真实下载）");
			Console.WriteLine("[SteamPatch] 补丁目录: " + patchDir);
			Console.WriteLine("========================================");
			Environment.SetEnvironmentVariable("RAL_STEAM_PATCH_DIR", patchDir);
			string text = Environment.GetEnvironmentVariable("DOTNET_HOST_PATH") ?? Path.Combine(Environment.GetEnvironmentVariable("DOTNET_ROOT") ?? "", "dotnet");
			if (File.Exists(text))
			{
				Environment.SetEnvironmentVariable("RAL_STEAM_DOTNET", text);
			}
			Console.WriteLine("[SteamPatch] 步骤1: 替换游戏目录里的 Steamworks.NET.dll");
			SteamFilePatcher.Apply(patchDir);
			Console.WriteLine("[SteamPatch] 步骤2: 注册解析器");
			AssemblyLoadContext.Default.Resolving += (AssemblyLoadContext ctx, AssemblyName name) =>
			{
				string text2 = Path.Combine(patchDir, name.Name + ".dll");
				if (File.Exists(text2))
				{
					Console.WriteLine("[SteamPatch] 解析程序集 " + name.Name + " -> 补丁目录");
					return ctx.LoadFromAssemblyPath(text2);
				}
				List<string> list = new List<string>();
				string environmentVariable = Environment.GetEnvironmentVariable("MONOMOD_PATH");
				if (!string.IsNullOrEmpty(environmentVariable))
				{
					list.Add(environmentVariable);
				}
				list.Add(AppContext.BaseDirectory);
				try
				{
					list.Add(Directory.GetCurrentDirectory());
				}
				catch
				{
				}
				foreach (string item in list)
				{
					if (!string.IsNullOrEmpty(item))
					{
						string text3 = Path.Combine(item, name.Name + ".dll");
						if (File.Exists(text3))
						{
							Console.WriteLine("[SteamPatch] 解析程序集 " + name.Name + " -> " + item);
							return ctx.LoadFromAssemblyPath(text3);
						}
					}
				}
				return (Assembly?)null;
			};
			Console.WriteLine("[SteamPatch] 步骤3: 挂 tML 侧的门（失败也不影响 Steam 功能）");
			try
			{
				ScheduleGates();
			}
			catch (Exception ex)
			{
				Console.WriteLine("[SteamPatch] 挂门失败（忽略）: " + ex.GetType().Name + ": " + ex.Message);
			}
			Console.WriteLine("[SteamPatch] 初始化完成");
			Console.WriteLine("========================================");
		}
		catch (Exception ex2)
		{
			Console.WriteLine("[SteamPatch] 初始化失败: " + ex2.GetType().Name + ": " + ex2.Message);
			Console.WriteLine(ex2.StackTrace);
		}
	}

	[MethodImpl(MethodImplOptions.NoInlining)]
	private static void ScheduleGates()
	{
		TmlGates.Schedule();
	}

	public static int Run(nint arg, int sizeBytes)
	{
		Initialize();
		return 0;
	}
}
