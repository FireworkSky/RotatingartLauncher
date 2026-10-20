using System.IO;
using Localizer.DataModel;

namespace Localizer.Package.Load
{
public interface IFileLoadService
{
	IFile Load(Stream stream, string typeName);
}
}
