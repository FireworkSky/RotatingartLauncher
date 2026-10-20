using System;
using System.Reflection;
using Terraria;
using Terraria.ModLoader;
using Terraria.ModLoader.Core;

namespace Localizer.DataModel.Default
{
public class LoadedModWrapper : IMod
{
	internal readonly WeakReference<object> wrapped;

	private string name;

	private string displayName;

	private Version version;

	public string Name => name ?? "";

	public Assembly Code { get; }

	public string DisplayName => displayName ?? "";

	public Version Version => version ?? new Version();

	public TmodFile File { get; }

	public bool Enabled => (bool)typeof(ModLoader).Invoke("IsEnabled", Name);

	public LoadedModWrapper(object mod)
	{
		wrapped = new WeakReference<object>(mod);
		name = mod.ValueOf<string>("Name");
		Code = ((name == "ModLoader") ? Assembly.GetAssembly(typeof(Main)) : mod.ValueOf<Assembly>("assembly"));
		object obj = mod.ValueOf("properties");
		displayName = obj.ValueOf<string>("displayName");
		version = obj.ValueOf<Version>("version");
		File = mod.ValueOf<TmodFile>("modFile");
	}
}
}
