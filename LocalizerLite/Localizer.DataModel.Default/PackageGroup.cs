using System.Collections.Generic;

namespace Localizer.DataModel.Default
{
public class PackageGroup : IPackageGroup
{
	public IMod Mod { get; set; }

	public ICollection<IPackage> Packages { get; set; }
}
}
