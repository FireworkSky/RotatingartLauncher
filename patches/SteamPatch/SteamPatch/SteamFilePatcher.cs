using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using Mono.Cecil;

namespace SteamPatch;

public static class SteamFilePatcher
{
	public const string BackupSuffix = ".ral-orig";

	public const string BridgeMarker = "RAL.SteamBridge";

	public static void Apply(string patchDir)
	{
		string text = FindTmlRoot(AppContext.BaseDirectory);
		Console.WriteLine("[SteamPatch] 游戏根目录: " + text);
		List<string> list = new List<string>();
		string path = Path.Combine(text, "Libraries");
		if (Directory.Exists(path))
		{
			list.AddRange(Directory.EnumerateFiles(path, "Steamworks.NET.dll", SearchOption.AllDirectories));
		}
		string text2 = Path.Combine(text, "Steamworks.NET.dll");
		if (File.Exists(text2))
		{
			list.Add(text2);
		}
		if (list.Count == 0)
		{
			Console.WriteLine("[SteamPatch] ⚠\ufe0f 没找到 Steamworks.NET.dll，Steam 支持不会生效");
			return;
		}
		list = (from p in list
			orderby p.Replace('\\', '/').Contains("/runtimes/linux/") descending, p.Replace('\\', '/').Contains("/runtimes/") descending
			select p).ToList();
		foreach (string item in list)
		{
			try
			{
				PatchOne(item);
			}
			catch (Exception ex)
			{
				Console.WriteLine($"[SteamPatch] 改写失败 {item}: {ex.GetType().Name}: {ex.Message}");
			}
		}
	}

	private static string FindTmlRoot(string baseDir)
	{
		if (File.Exists(Path.Combine(baseDir, "tModLoader.dll")))
		{
			return baseDir;
		}
		DirectoryInfo directoryInfo = new DirectoryInfo(baseDir);
		int num = 0;
		while (num < 4 && directoryInfo != null)
		{
			if (File.Exists(Path.Combine(directoryInfo.FullName, "tModLoader.dll")))
			{
				return directoryInfo.FullName;
			}
			num++;
			directoryInfo = directoryInfo.Parent;
		}
		return baseDir;
	}

	private static void PatchOne(string path)
	{
		byte[] array = File.ReadAllBytes(path);
		if (Encoding.UTF8.GetString(array).Contains("RAL.SteamBridge"))
		{
			Console.WriteLine("[SteamPatch] 已改写，跳过: " + Short(path));
			return;
		}
		string text = path + ".ral-orig";
		if (!File.Exists(text))
		{
			File.Copy(path, text, overwrite: false);
			Console.WriteLine("[SteamPatch] 已备份: " + Short(text));
		}
		using MemoryStream memoryStream = new MemoryStream(array);
		AssemblyDefinition val = AssemblyDefinition.ReadAssembly((Stream)memoryStream);
		int value = Redirector.PatchAll(val.MainModule);
		string text2 = path + ".ral-tmp";
		val.Write(text2);
		File.Move(text2, path, overwrite: true);
		Console.WriteLine($"[SteamPatch] ✅ 已改写 {Short(path)}：{value} 个原生调用 -> RAL.SteamBridge ({new FileInfo(path).Length} 字节)");
	}

	private static string Short(string p)
	{
		int num = p.Replace('\\', '/').LastIndexOf("/Libraries/", StringComparison.Ordinal);
		if (num < 0)
		{
			return p;
		}
		return ".../" + p.Replace('\\', '/').Substring(num + 1);
	}

	public static void Restore()
	{
		string path = Path.Combine(FindTmlRoot(AppContext.BaseDirectory), "Libraries");
		if (!Directory.Exists(path))
		{
			return;
		}
		foreach (string item in Directory.EnumerateFiles(path, "*.ral-orig", SearchOption.AllDirectories))
		{
			string text = item.Substring(0, item.Length - ".ral-orig".Length);
			File.Copy(item, text, overwrite: true);
			Console.WriteLine("[SteamPatch] 已还原 " + Short(text));
		}
	}
}
