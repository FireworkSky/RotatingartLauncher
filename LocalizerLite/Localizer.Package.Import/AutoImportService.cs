using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Localizer.Package.Load;
using Ninject;
using Terraria;

namespace Localizer.Package.Import
{

/// <summary>
/// 精简版自动导入：启动后（所有 mod 都加载完）扫描
/// <c>Main.SavePath/Localizer/*.locpack</c>（与 Mods 同级），
/// 把包导入对应 mod。没有 GUI、没有网络、没有打包。
/// </summary>
public sealed class AutoImportService : Disposable
{
	private readonly IPackageManageService _packageManage;

	private readonly PackedPackageLoad<global::Localizer.DataModel.Default.Package> _packedPackageLoad;

	private readonly IPackageImportService _packageImport;

	private readonly IFileLoadService _fileLoad;

	private bool _imported;

	public bool Imported => _imported;

	[Inject]
	public AutoImportService(IPackageManageService packageManage, PackedPackageLoad<global::Localizer.DataModel.Default.Package> packedPackageLoad, IPackageImportService packageImport, IFileLoadService fileLoad)
	{
		_packageManage = packageManage ?? throw new ArgumentNullException("packageManage");
		_packedPackageLoad = packedPackageLoad ?? throw new ArgumentNullException("packedPackageLoad");
		_packageImport = packageImport ?? throw new ArgumentNullException("packageImport");
		_fileLoad = fileLoad ?? throw new ArgumentNullException("fileLoad");
		Hooks.PostSetupContent += OnPostSetupContent;
	}

	private void OnPostSetupContent()
	{
		if (_imported)
		{
			return;
		}
		_imported = true;
		try
		{
			if (Localizer.Config.AutoImport)
			{
				Utils.LogInfo("Auto import begin (all mods loaded).");
				LoadPackages();
				Import();
			}
		}
		catch (Exception o)
		{
			Utils.LogError(o);
		}
	}

	private void LoadPackages()
	{
		_packageManage.PackageGroups = new List<IPackageGroup>();
		Directory.CreateDirectory(Localizer.PackageDirPath);
		string[] files = Directory.GetFiles(Localizer.PackageDirPath, "*.locpack");
		Utils.LogInfo($"Scanning packages in [{Localizer.PackageDirPath}], found {files.Length}");
		foreach (string file in files.OrderBy(f => f))
		{
			try
			{
				IPackage package = _packedPackageLoad.Load(file, _fileLoad);
				if (package != null)
				{
					_packageManage.AddPackage(package);
					Utils.LogInfo($"Loaded package: [{package.Name}] for mod [{package.ModName}]");
				}
			}
			catch (Exception e)
			{
				Utils.LogError($"[{Path.GetFileName(file)}] load failed: {e.Message}");
			}
		}
		_packageManage.LoadState();
	}

	private void Import(IMod mod = null)
	{
		try
		{
			_packageImport.Clear();
			if (mod == null)
			{
				foreach (IPackageGroup pg in _packageManage.PackageGroups)
				{
					QueuePackageGroup(pg);
				}
			}
			else
			{
				QueuePackageGroup(_packageManage.PackageGroups.FirstOrDefault(pg => pg.Mod.Name == mod.Name));
			}
			_packageImport.Import(preferEarly: false);
			Localizer.RefreshLanguages();
			Utils.LogInfo("Auto import end.");
		}
		catch (Exception e)
		{
			Utils.LogError(e);
		}
	}

	private void QueuePackageGroup(IPackageGroup packageGroup)
	{
		if (packageGroup == null || !packageGroup.Mod.Enabled)
		{
			return;
		}
		foreach (IPackage package in packageGroup.Packages)
		{
			if (package.Enabled)
			{
				_packageImport.Queue(package);
			}
		}
	}

	protected override void DisposeUnmanaged()
	{
		Hooks.PostSetupContent -= OnPostSetupContent;
	}
}
}
