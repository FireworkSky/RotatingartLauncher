using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.Loader;

namespace SteamPatch;

public static class EmbeddedAssemblies
{
	private static Assembly _owner;

	private static readonly HashSet<string> _resolved = new HashSet<string>();

	public static Assembly Owner => _owner ?? (_owner = typeof(EmbeddedAssemblies).Assembly);

	public static IEnumerable<string> Names => from n in Owner.GetManifestResourceNames()
		where n.EndsWith(".dll", StringComparison.OrdinalIgnoreCase)
		select n;

	public static Assembly Resolve(AssemblyLoadContext ctx, AssemblyName name)
	{
		Assembly assembly = Load(name.Name);
		if (assembly != null && _resolved.Add(name.Name))
		{
			Console.WriteLine("[SteamPatch] 从内嵌资源加载 " + name.Name);
		}
		return assembly;
	}

	public static Assembly Load(string simpleName)
	{
		Stream manifestResourceStream = Owner.GetManifestResourceStream(simpleName + ".dll");
		if (manifestResourceStream == null)
		{
			return null;
		}
		using (manifestResourceStream)
		{
			return AssemblyLoadContext.Default.LoadFromStream(manifestResourceStream);
		}
	}

	public static bool Has(string simpleName)
	{
		return Owner.GetManifestResourceStream(simpleName + ".dll") != null;
	}

	public static string Extract(string simpleName, string targetDir)
	{
		Directory.CreateDirectory(targetDir);
		string text = Path.Combine(targetDir, simpleName + ".dll");
		using (Stream stream = Owner.GetManifestResourceStream(simpleName + ".dll"))
		{
			if (stream == null)
			{
				Console.WriteLine("[SteamPatch] 内嵌资源里没有 " + simpleName + ".dll");
				return null;
			}
			using FileStream destination = File.Create(text);
			stream.CopyTo(destination);
		}
		string[] array = new string[2] { ".deps.json", ".runtimeconfig.json" };
		foreach (string text2 in array)
		{
			using Stream stream2 = Owner.GetManifestResourceStream(simpleName + text2);
			if (stream2 != null)
			{
				using FileStream destination2 = File.Create(Path.Combine(targetDir, simpleName + text2));
				stream2.CopyTo(destination2);
			}
		}
		return text;
	}

	public static string ExtractApp(string entryName, string targetDir, IEnumerable<string> alsoExtract)
	{
		Directory.CreateDirectory(targetDir);
		Extract(entryName, targetDir);
		foreach (string item in alsoExtract)
		{
			Extract(item, targetDir);
		}
		return Path.Combine(targetDir, entryName + ".dll");
	}
}
