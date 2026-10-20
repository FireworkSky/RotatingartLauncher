using System;
using Localizer.DataModel;

namespace Localizer.Package.Import
{
public interface IPackageImportService
{
	void RegisterImporter<T>(Type importerType) where T : IFile;

	void UnregisterImporter<T>() where T : IFile;

	void Queue(IPackage package);

	void Import(bool preferEarly);

	void Reset();

	void Clear();
}
}
