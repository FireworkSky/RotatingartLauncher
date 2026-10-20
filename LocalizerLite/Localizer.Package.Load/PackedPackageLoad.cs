using System.IO;
using System.IO.Compression;
using Localizer.DataModel;

namespace Localizer.Package.Load
{
public sealed class PackedPackageLoad<T> : IPackageLoadService<T> where T : IPackage
{
	private readonly string _packageMainFileName = "Package.json";

	public IPackage Load(string path, IFileLoadService fileLoadService)
	{
		Utils.LogDebug("Loading package from " + path);
		if (!File.Exists(path))
		{
			Utils.LogError("Package file doesn't exist!");
			return null;
		}
		using (FileStream stream = new FileStream(path, FileMode.Open))
		using (ZipArchive zipArchive = new ZipArchive(stream, ZipArchiveMode.Read))
		{
		IPackage package = Utils.ReadFileAndDeserializeJson<T>(zipArchive.GetEntry(_packageMainFileName)?.Open());
		if (package == null)
		{
			Utils.LogError("Package main file deserialization failed!");
			return null;
		}
		IMod wrappedMod = Localizer.GetWrappedMod(package.ModName);
		if (wrappedMod == null)
		{
			Utils.LogError("Package Mod " + package.ModName + " not loaded!");
			return null;
		}
		package.Mod = wrappedMod;
		foreach (string file2 in package.FileList)
		{
			Utils.LogDebug("Loading file [" + file2 + "]");
			Stream stream2 = zipArchive.GetEntry(file2 + ".json")?.Open();
			IFile file = fileLoadService.Load(stream2, file2);
			package.AddFile(file);
		}
		Utils.LogDebug("Package [" + package.Name + " loaded.]");
		return package;
		}
	}
	public void Dispose()
	{
	}
}
}
