using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Localizer.DataModel;
using Localizer.DataModel.Default;

namespace Localizer.Package
{
public class PackageManageService : IPackageManageService
{
	private readonly string stateSavePath = Localizer.SavePath + "PackageStates.json";

	private List<PackageGroupState> oldPackageGroupStates = new List<PackageGroupState>();

	public ICollection<IPackageGroup> PackageGroups { get; set; }

	public void AddPackage(IPackage package)
	{
		if (package == null)
		{
			return;
		}
		if (PackageGroups.All((IPackageGroup pg) => pg.Mod.Name != package.Mod.Name))
		{
			PackageGroups.Add(new PackageGroup
			{
				Mod = package.Mod,
				Packages = new List<IPackage> { package }
			});
			return;
		}
		PackageGroups.FirstOrDefault((IPackageGroup pg) => pg.Mod.Name == package.Mod.Name && pg.Packages.All((IPackage p) => p.Name != package.Name))?.Packages.Add(package);
	}

	public void RemovePackage(IPackage package)
	{
		throw new NotImplementedException();
	}

	public void LoadState()
	{
		if (!System.IO.File.Exists(stateSavePath))
		{
			return;
		}
		oldPackageGroupStates = Utils.ReadFileAndDeserializeJson<List<PackageGroupState>>(stateSavePath) ?? new List<PackageGroupState>();
		foreach (PackageGroupState state in oldPackageGroupStates)
		{
			IPackageGroup packageGroup = PackageGroups.FirstOrDefault((IPackageGroup pg) => pg.Mod.Name == state.ModName);
			if (packageGroup == null)
			{
				continue;
			}
			foreach (IPackage package in packageGroup.Packages)
			{
				if (state.Packages.Contains(package.Name))
				{
					package.Enabled = (bool)state.Packages[package.Name];
				}
			}
		}
	}

	public void SaveState()
	{
		foreach (IPackageGroup pg in PackageGroups)
		{
			PackageGroupState packageGroupState = oldPackageGroupStates.FirstOrDefault((PackageGroupState s) => s.ModName == pg.Mod.Name);
			if (packageGroupState != null)
			{
				oldPackageGroupStates.Remove(packageGroupState);
			}
			oldPackageGroupStates.Add(new PackageGroupState(pg));
		}
		Utils.SerializeJsonAndCreateFile(oldPackageGroupStates, stateSavePath);
	}

	public void Dispose()
	{
	}
}
}
