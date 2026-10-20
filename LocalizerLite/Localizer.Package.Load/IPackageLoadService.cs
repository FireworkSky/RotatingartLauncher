using Localizer.DataModel;

namespace Localizer.Package.Load
{
public interface IPackageLoadService<T> where T : IPackage
{
	IPackage Load(string path, IFileLoadService fileLoadService);
}
}
