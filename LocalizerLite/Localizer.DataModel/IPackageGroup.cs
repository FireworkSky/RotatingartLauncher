using System.Collections.Generic;

namespace Localizer.DataModel
{
public interface IPackageGroup
{
	IMod Mod { get; set; }

	ICollection<IPackage> Packages { get; set; }
}
}
