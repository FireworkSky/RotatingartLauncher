using System.Collections.Specialized;
using Newtonsoft.Json;

namespace Localizer.DataModel.Default
{
[JsonObject(/*Could not decode attribute arguments.*/)]
public class PackageGroupState
{
	public string ModName { get; set; }

	public OrderedDictionary Packages { get; set; }

	public PackageGroupState()
	{
	}

	public PackageGroupState(IPackageGroup pg)
	{
		ModName = pg.Mod.Name;
		Packages = new OrderedDictionary();
		foreach (IPackage package in pg.Packages)
		{
			Packages.Add(package.Name, package.Enabled);
		}
	}
}
}
