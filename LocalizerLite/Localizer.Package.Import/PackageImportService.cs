using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using Localizer.Attributes;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Ninject;
using Ninject.Parameters;
using Ninject.Syntax;

namespace Localizer.Package.Import
{
public class PackageImportService : IPackageImportService
{
	private readonly List<IPackageGroup> packageGroups = new List<IPackageGroup>();

	private Dictionary<Type, FileImporter> _importers;

	public PackageImportService()
	{
		_importers = new Dictionary<Type, FileImporter>();
	}

	public void RegisterImporter<T>(Type importerType) where T : IFile
	{
		if ((object)importerType == null)
		{
			throw new ArgumentNullException("importerType");
		}
		((IBindingInSyntax<object>)(object)((BindingRoot)Localizer.Kernel).Bind(new Type[2]
		{
			typeof(FileImporter),
			importerType
		}).To(importerType)).InSingletonScope();
		if (!(ResolutionExtensions.Get((IResolutionRoot)(object)Localizer.Kernel, importerType, new IParameter[0]) is FileImporter fileImporter))
		{
			Utils.LogError("Importer binding failed.");
			return;
		}
		if (_importers.ContainsKey(typeof(T)))
		{
			_importers[typeof(T)] = fileImporter;
		}
		else
		{
			_importers.Add(typeof(T), fileImporter);
		}
		Utils.LogInfo("Importer: [" + fileImporter.GetType().FullName + "] registered for file type: [" + typeof(T).FullName + "].");
	}

	public void UnregisterImporter<T>() where T : IFile
	{
		if (_importers.ContainsKey(typeof(T)))
		{
			_importers.Remove(typeof(T));
			Utils.LogInfo("Unregistered importer of type [" + typeof(T).FullName + "]");
		}
	}

	public void Queue(IPackage package)
	{
		if (packageGroups.All((IPackageGroup pg) => pg.Mod != package.Mod))
		{
			packageGroups.Add(new PackageGroup
			{
				Mod = package.Mod,
				Packages = new List<IPackage> { package }
			});
		}
		else
		{
			packageGroups.FirstOrDefault((IPackageGroup pg) => pg.Mod == package.Mod)?.Packages.Add(package);
		}
		Utils.LogDebug("Queued [" + package.Name + "]");
	}

	public void Import(bool preferEarly = true)
	{
		Utils.LogInfo("Begin importing");
		foreach (IPackageGroup group in packageGroups)
		{
			Utils.LogInfo("Importing Mod: [" + group.Mod.Name + "]");
			Utils.SafeWrap(() =>
			{
				foreach (IFile f in Merge(group).Files)
				{
					Utils.SafeWrap(() =>
					{
						FileImporter importer = GetImporter(f);
						if (importer != null)
						{
							Import(f, group, importer, preferEarly);
						}
					});
				}
			});
		}
	}

	private void Import(IFile f, IPackageGroup group, FileImporter importer, bool preferEarly)
	{
		OperationTiming operationTiming = importer.GetType().GetCustomAttribute<OperationTimingAttribute>()?.Timing ?? OperationTiming.Any;
		Utils.LogDebug($"Trying to Import [{f.GetType()}] using [{importer.GetType()}]" + $"\n    ImporterTiming: [{operationTiming}]" + $"\n    State: [{Localizer.State}]" + $"\n    PreferEarly: [{preferEarly}]");
		if (Localizer.CanDoOperationNow(importer.GetType()) && (!preferEarly || (int)operationTiming <= (int)Localizer.State || operationTiming != OperationTiming.Any || Localizer.State == OperationTiming.BeforeModCtor))
		{
			importer.Import(f, group.Mod, group.Packages.ToList()[0].Language);
			Utils.LogDebug("Import Complete");
		}
	}

	public void Reset()
	{
		Utils.LogInfo("Resetting importers...");
		foreach (FileImporter value in _importers.Values)
		{
			value.Reset();
		}
		packageGroups.Clear();
		Utils.LogInfo("Reset completed.");
	}

	public void Clear()
	{
		Utils.LogInfo("Clearing import queue...");
		packageGroups.Clear();
		Utils.LogInfo("Cleared.");
	}

	private FileImporter GetImporter(IFile file)
	{
		if (_importers.TryGetValue(file.GetType(), out var value))
		{
			return value;
		}
		Utils.LogWarn($"No registered importer for file type: [{file.GetType()}]");
		return null;
	}

	internal global::Localizer.DataModel.Default.Package Merge(IPackageGroup group)
	{
		global::Localizer.DataModel.Default.Package result = new global::Localizer.DataModel.Default.Package
		{
			Mod = group.Mod,
			ModName = group.Mod.Name,
			Files = new List<IFile>(),
			FileList = new List<string>()
		};
		foreach (IPackage package in group.Packages)
		{
			Utils.SafeWrap(() =>
			{
				foreach (IFile file in package.Files)
				{
					if (result.Files.All((IFile f) => f.GetType() != file.GetType()))
					{
						result.Files.Add(file);
					}
					else
					{
						IFile file2 = package.Files.FirstOrDefault((IFile f) => f.GetType() == file.GetType());
						FileImporter importer = GetImporter(file);
						if (importer != null)
						{
							IFile item = importer.Merge(file2, file);
							result.Files.Remove(file2);
							result.Files.Add(item);
						}
					}
				}
			});
		}
		return result;
	}

	public void Dispose()
	{
		foreach (FileImporter importer in _importers.Values)
		{
			Utils.SafeWrap(() =>
			{
				importer.Dispose();
			});
		}
		_importers = null;
	}
}
}
