using System.Collections.Generic;
using Localizer.DataModel;

namespace Localizer.Package
{
public interface IPackageManageService
{
	ICollection<IPackageGroup> PackageGroups { get; set; }

	void AddPackage(IPackage package);

	void RemovePackage(IPackage package);

	void LoadState();

	void SaveState();
}
}
