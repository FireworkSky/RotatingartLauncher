using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Reflection;
using Localizer.Attributes;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Localizer.Package.Import;
using Microsoft.Xna.Framework;
using Ninject;
using Ninject.Parameters;
using Ninject.Syntax;
using Terraria;
using Terraria.Localization;
using Terraria.ModLoader;
using Terraria.ModLoader.Core;
using log4net;

namespace Localizer
{
/// <summary>
/// 精简版 Localizer：去掉 GUI / ModBrowser / 网络 / 导出打包，只保留
/// 「读 .locpack 汉化包 → 导入」这一条链路。
///
/// 汉化包放在 <c>Main.SavePath/Localizer/</c>（与 Mods 同级），启动时自动扫描导入。
/// </summary>
public sealed class Localizer : Mod
{
	public static string SavePath;

	public static string PackageDirPath;

	public static string ConfigPath;

	internal static LoadedModWrapper LoadedLocalizer;

	private static Dictionary<int, GameCulture> _gameCultures;

	private static bool _initiated;

	public static Localizer Instance { get; private set; }

	public static ILog Log { get; private set; }

	public static TmodFile TmodFile { get; private set; }

	public static Configuration Config { get; set; }

	public static OperationTiming State { get; internal set; }

	internal static LocalizerKernel Kernel { get; private set; }

	public Localizer()
	{
		Instance = this;
		LoadedLocalizer = new LoadedModWrapper("Terraria.ModLoader.Core.AssemblyManager".Type().ValueOf("loadedMods").Invoke("get_Item", "!Localizer"));
		this.SetField("<File>k__BackingField", LoadedLocalizer.File);
		this.SetField("<Code>k__BackingField", LoadedLocalizer.Code);
		Log = LogManager.GetLogger("Localizer");
		State = OperationTiming.BeforeModCtor;
		TmodFile = Instance.ValueOf<TmodFile>("File");
		Init();
		_initiated = true;
	}

	private static void Init()
	{
		_gameCultures = typeof(GameCulture).ValueOf<Dictionary<int, GameCulture>>("_legacyCultures");
		// 汉化包目录：与 Mods 同级的 Localizer 文件夹（Main.SavePath 就是 Mods 的父目录）
		SavePath = Path.Combine(Main.SavePath, "Localizer");
		PackageDirPath = SavePath;
		ConfigPath = Path.Combine(SavePath, "Config.json");
		Utils.EnsureDir(SavePath);
		LoadConfig();
		Lang.AddModTranslations((Mod)(object)Instance);
		Kernel = new LocalizerKernel();
		Kernel.Init();
		// 触发 AutoImportService 构造（构造里会扫描汉化包并挂 hook）
		Kernel.Get<AutoImportService>();
	}

	public override void Load()
	{
		if (!_initiated)
		{
			throw new Exception("Localizer not initialized.");
		}
		State = OperationTiming.BeforeModLoad;
		Hooks.InvokeBeforeLoad();
	}

	public override void PostSetupContent()
	{
		State = OperationTiming.BeforeContentLoad;
		Hooks.InvokeBeforeSetupContent();
	}

	public override void PostAddRecipes()
	{
		State = OperationTiming.PostContentLoad;
		Hooks.InvokePostSetupContent();
	}

	public override void UpdateUI(GameTime gameTime)
	{
		Hooks.InvokeOnGameUpdate(gameTime);
	}

	public override void Unload()
	{
		try
		{
			SaveConfig();
			LoadedLocalizer.File.SetField("<name>k__BackingField", "!Localizer");
			LoadedLocalizer.SetField("name", "!Localizer");
			Kernel.Dispose();
			Kernel = null;
			_gameCultures = null;
			Config = null;
			Instance = null;
		}
		catch (Exception ex)
		{
			Log?.Error((object)ex);
		}
		finally
		{
			_initiated = false;
			Log = null;
		}
	}

	public static void LoadConfig()
	{
		Log.Info((object)"Loading config");
		if (System.IO.File.Exists(ConfigPath))
		{
			Config = Utils.ReadFileAndDeserializeJson<Configuration>(ConfigPath) ?? new Configuration();
		}
		else
		{
			Log.Info((object)"No config file, creating...");
			Config = new Configuration();
		}
		Utils.SerializeJsonAndCreateFile(Config, ConfigPath);
		Log.Info((object)"Config loaded");
	}

	public static void SaveConfig()
	{
		Utils.SerializeJsonAndCreateFile(Config, ConfigPath);
	}

	public static GameCulture AddGameCulture(CultureInfo culture)
	{
		if (GameCulture.FromName(culture.Name) == null)
		{
			return new GameCulture(culture.Name, _gameCultures.Count);
		}
		return null;
	}

	public static GameCulture CultureInfoToGameCulture(CultureInfo culture)
	{
		return GameCulture.FromName(culture.Name) ?? AddGameCulture(culture);
	}

	/// <summary>导入后刷新一次语言系统（原版是 RefreshLanguageService，去掉 Harmony 后直接调 tML）</summary>
	public static void RefreshLanguages()
	{
		ModContent.RefreshModLanguage(LanguageManager.Instance.ActiveCulture);
	}

	public static IMod GetWrappedMod(string name)
	{
		if ((int)State < (int)OperationTiming.BeforeModCtor)
		{
			object o = "Terraria.ModLoader.Core.AssemblyManager".Type().ValueOf("loadedMods");
			if (!(bool)o.Invoke("ContainsKey", name))
			{
				return null;
			}
			return new LoadedModWrapper(o.Invoke("get_Item", name));
		}
		Mod modByName = Utils.GetModByName(name);
		if (modByName == null)
		{
			return null;
		}
		return new ModWrapper(modByName);
	}

	public static bool CanDoOperationNow(Type t)
	{
		OperationTimingAttribute customAttribute = t.GetCustomAttribute<OperationTimingAttribute>();
		if (customAttribute != null)
		{
			return CanDoOperationNow(customAttribute.Timing);
		}
		return true;
	}

	public static bool CanDoOperationNow(OperationTiming t)
	{
		return (t & State) != 0;
	}
}
}
