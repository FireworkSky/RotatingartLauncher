using System;
using System.Reflection;
using Terraria;
using Terraria.ModLoader;
using Terraria.ModLoader.Core;

namespace Localizer.DataModel.Default
{
public class ModWrapper : IMod
{
	private readonly WeakReference<Mod> wrapped;

	public Mod Mod
	{
		get
		{
			Mod target = null;
			wrapped?.TryGetTarget(out target);
			return target;
		}
	}

	public string Name
	{
		get
		{
			Mod mod = Mod;
			return ((mod != null) ? mod.Name : null) ?? "";
		}
	}

	public Assembly Code
	{
		get
		{
			if (Name == "ModLoader")
			{
				return Assembly.GetAssembly(typeof(Main));
			}
			Mod mod = Mod;
			if (mod == null)
			{
				return null;
			}
			return mod.Code;
		}
	}

	public string DisplayName => Mod.DisplayName ?? "";

	public Version Version
	{
		get
		{
			Mod mod = Mod;
			if (mod == null)
			{
				return null;
			}
			return mod.Version;
		}
	}

	public TmodFile File => Mod?.ValueOf<TmodFile>("File");

	public bool Enabled => (bool)typeof(ModLoader).Invoke("IsEnabled", Name);

	public ModWrapper(Mod mod)
	{
		if (mod == null)
		{
			throw new ArgumentNullException("mod");
		}
		wrapped = new WeakReference<Mod>(mod);
	}
}
}
