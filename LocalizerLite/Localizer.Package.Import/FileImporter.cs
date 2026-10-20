using System.Globalization;
using Localizer.DataModel;

namespace Localizer.Package.Import
{
public abstract class FileImporter : Disposable
{
	public abstract void Import(IFile file, IMod mod, CultureInfo culture);

	public abstract IFile Merge(IFile main, IFile addition);

	public virtual void Reset()
	{
	}
}
}
