using System;
using System.Reflection;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Localizer.Package;
using Localizer.Package.Import;
using Localizer.Package.Load;
using Ninject;
using Ninject.Modules;
using Ninject.Parameters;
using Ninject.Syntax;

namespace Localizer.Modules
{
public class DefaultPackageModule : NinjectModule
{
	public override void Load()
	{
		BindLoadService();
		BindManageService();
		BindImportService();
	}

	public override void Unload()
	{
		ResolutionExtensions.Get<AutoImportService>((IResolutionRoot)(object)((NinjectModule)this).Kernel, new IParameter[0]).Dispose();
		ResolutionExtensions.Get<PackageImportService>((IResolutionRoot)(object)((NinjectModule)this).Kernel, new IParameter[0]).Dispose();
	}

	private void BindLoadService()
	{
		((IBindingInSyntax<JsonFileLoad>)(object)((BindingRoot)this).Bind<IFileLoadService>().To<JsonFileLoad>()).InSingletonScope();
		((IBindingInSyntax<PackedPackageLoad<global::Localizer.DataModel.Default.Package>>)(object)((BindingRoot)this).Bind<IPackageLoadService<global::Localizer.DataModel.Default.Package>>().To<PackedPackageLoad<global::Localizer.DataModel.Default.Package>>()).InSingletonScope();
		((IBindingInSyntax<PackedPackageLoad<global::Localizer.DataModel.Default.Package>>)(object)((BindingRoot)this).Bind<PackedPackageLoad<global::Localizer.DataModel.Default.Package>>().ToSelf()).InSingletonScope();
	}

	private void BindManageService()
	{
		((BindingRoot)this).Bind<IPackageManageService>().To<PackageManageService>();
	}

	private void BindImportService()
	{
		((IBindingInSyntax<PackageImportService>)(object)((BindingRoot)this).Bind<IPackageImportService>().To<PackageImportService>()).InSingletonScope();
		IPackageImportService packageImportService = ResolutionExtensions.Get<IPackageImportService>((IResolutionRoot)(object)((NinjectModule)this).Kernel, new IParameter[0]);
		if (Localizer.Config.ImporBasictAfterSetupContent)
		{
			packageImportService.RegisterImporter<BasicItemFile>(typeof(BasicImporterPostContentLoad<BasicItemFile>));
			packageImportService.RegisterImporter<BasicNPCFile>(typeof(BasicImporterPostContentLoad<BasicNPCFile>));
			packageImportService.RegisterImporter<BasicBuffFile>(typeof(BasicImporterPostContentLoad<BasicBuffFile>));
			packageImportService.RegisterImporter<BasicProjectileFile>(typeof(BasicImporterPostContentLoad<BasicProjectileFile>));
			packageImportService.RegisterImporter<BasicPrefixFile>(typeof(BasicImporterPostContentLoad<BasicPrefixFile>));
		}
		else
		{
			packageImportService.RegisterImporter<BasicItemFile>(typeof(BasicImporterBeforeContentLoad<BasicItemFile>));
			packageImportService.RegisterImporter<BasicNPCFile>(typeof(BasicImporterBeforeContentLoad<BasicNPCFile>));
			packageImportService.RegisterImporter<BasicBuffFile>(typeof(BasicImporterBeforeContentLoad<BasicBuffFile>));
			packageImportService.RegisterImporter<BasicProjectileFile>(typeof(BasicImporterBeforeContentLoad<BasicProjectileFile>));
			packageImportService.RegisterImporter<BasicPrefixFile>(typeof(BasicImporterBeforeContentLoad<BasicPrefixFile>));
		}
		packageImportService.RegisterImporter<CustomModTranslationFile>(typeof(CustomModTranslationImporter));
		// 只保留 MonoMod 的 ldstr 导入器（走 tML 自带的 MonoMod ILHook，最稳）
		packageImportService.RegisterImporter<LdstrFile>(typeof(MonoModLdstrImporter));
		((IBindingInSyntax<AutoImportService>)(object)((BindingRoot)this).Bind<AutoImportService>().ToSelf()).InSingletonScope();
	}
}
}
