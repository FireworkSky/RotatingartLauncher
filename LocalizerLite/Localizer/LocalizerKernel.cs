using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using Localizer.Modules;
using Ninject;
using Ninject.Infrastructure.Disposal;
using Ninject.Modules;
using Terraria.ModLoader;

namespace Localizer
{
public sealed class LocalizerKernel : StandardKernel
{
	private static readonly string ExternalPluginDirPath = "./Localizer/Plugins/";

	public List<LocalizerPlugin> Plugins { get; private set; }

	public Dictionary<string, bool> PluginEnableStatus { get; private set; }

	public LocalizerKernel()
		: base(new INinjectModule[0])
	{
		AppDomain.CurrentDomain.AssemblyResolve += AssemblyResolveHandler;
		Plugins = new List<LocalizerPlugin>();
		PluginEnableStatus = new Dictionary<string, bool>();
	}

	public void Init()
	{
		((KernelBase)this).Load((IEnumerable<INinjectModule>)(object)new NinjectModule[1]
		{
			new DefaultPackageModule()
		});
		LoadPlugins();
	}

	public override void Dispose(bool disposing)
	{
		if (!((DisposableObject)this).IsDisposed)
		{
			AppDomain.CurrentDomain.AssemblyResolve -= AssemblyResolveHandler;
			if (disposing)
			{
				UnloadAllPlugins();
				Plugins = null;
			}
		}
		((KernelBase)this).Dispose(disposing);
	}

	internal void UnloadAllPlugins()
	{
		foreach (LocalizerPlugin plugin in Plugins)
		{
			plugin.Dispose();
		}
		Plugins.Clear();
	}

	internal void LoadPlugins()
	{
		Utils.EnsureDir(ExternalPluginDirPath);
		FileInfo[] files = new DirectoryInfo(ExternalPluginDirPath).GetFiles("*.dll");
		for (int i = 0; i < files.Length; i++)
		{
			Assembly asm = Assembly.Load(File.ReadAllBytes(files[i].FullName));
			LoadPlugin(asm);
		}
	}

	internal void LoadPlugin(Assembly asm)
	{
		try
		{
			Type[] exportedTypes = asm.GetExportedTypes();
			foreach (Type type in exportedTypes)
			{
				if (type.IsSubclassOf(typeof(LocalizerPlugin)) && type.IsPublic && !type.IsAbstract)
				{
					LocalizerPlugin localizerPlugin = (LocalizerPlugin)Activator.CreateInstance(type);
					Plugins.Add(localizerPlugin);
					localizerPlugin.Initialize(this);
					Utils.LogInfo("Plugin [" + localizerPlugin.Name + "] loaded.");
				}
			}
		}
		catch (Exception arg)
		{
			Utils.LogError($"Error occured when loading Plugin [{asm.GetName()}]. \nError: {arg}");
		}
	}

	internal void UnloadPlugin(string pluginName)
	{
		if (string.IsNullOrWhiteSpace(pluginName))
		{
			throw new ArgumentException("pluginName");
		}
		LocalizerPlugin plugin = Plugins.SingleOrDefault((LocalizerPlugin p) => p.Name == pluginName) ?? throw new Exception("Cannot find plugin [" + pluginName + "]");
		UnloadPlugin(plugin);
	}

	internal void UnloadPlugin(LocalizerPlugin plugin)
	{
		if (plugin == null)
		{
			Utils.LogWarn("Plugin cannot be unloaded because it's null");
			return;
		}
		if (Plugins.Contains(plugin))
		{
			Utils.LogWarn("Plugin [" + plugin.Name + "] doesn't exist");
			return;
		}
		plugin.Dispose();
		Plugins.Remove(plugin);
		Utils.LogInfo("Plugin [" + plugin.Name + "] successfully unloaded.");
	}

	private static Assembly AssemblyResolveHandler(object sender, ResolveEventArgs args)
	{
		try
		{
			AssemblyName asmName = new AssemblyName(args.Name);
			if (asmName.Name == "Localizer")
			{
				return ((Mod)Localizer.Instance).Code;
			}
			AssemblyName assemblyName = Assembly.GetExecutingAssembly().GetReferencedAssemblies().FirstOrDefault((AssemblyName a) => a.Name.StartsWith("!Localizer_" + asmName.Name + "_"));
			if (assemblyName != null)
			{
				return Assembly.Load(assemblyName);
			}
			byte[] externalPluginFileBytes = GetExternalPluginFileBytes(asmName.Name + ".dll");
			if (externalPluginFileBytes != null && externalPluginFileBytes.Length != 0)
			{
				return Assembly.Load(externalPluginFileBytes);
			}
			return null;
		}
		catch (Exception)
		{
			return null;
		}
	}

	private static byte[] GetExternalPluginFileBytes(string fileName)
	{
		string path = Path.Combine(ExternalPluginDirPath, fileName);
		if (!File.Exists(path))
		{
			return null;
		}
		return File.ReadAllBytes(path);
	}
}
}
