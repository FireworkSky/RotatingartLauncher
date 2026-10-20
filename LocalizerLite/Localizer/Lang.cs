using System.Collections.Generic;
using Terraria.Localization;
using Terraria.ModLoader;

namespace Localizer
{
public static class Lang
{
	private static List<string> _keys = new List<string>
	{
		"NewVersion", "PackageManage", "Reload", "ReloadDesc", "OpenFolder", "OpenFolderDesc", "NoPackageFound", "PackageEnabled", "PackageDisabled", "PackageDisplay",
		"RefreshOnline", "RefreshOnlineDesc", "PackageOnline", "Export", "ExportDesc", "ExportWithTranslation", "ExportWithTranslationDesc", "PackageUpdate", "PackageLoading", "OpenUI",
		"TranslatedBy"
	};

	private static Dictionary<string, string> _en = new Dictionary<string, string>
	{
		{
			_keys[0],
			"Found new version of Localizer: {0} \nPlease go update it."
		},
		{
			_keys[1],
			"Package Management"
		},
		{
			_keys[2],
			"Reload"
		},
		{
			_keys[3],
			"Reload all packages"
		},
		{
			_keys[4],
			"Open Localizer Folder"
		},
		{
			_keys[5],
			"Find downloaded and exported packages"
		},
		{
			_keys[6],
			"No localization package found for this Mod"
		},
		{
			_keys[7],
			"Enabled"
		},
		{
			_keys[8],
			"Disabled"
		},
		{
			_keys[9],
			"({0}) {1} v{2} by {3}"
		},
		{
			_keys[10],
			"Refresh"
		},
		{
			_keys[11],
			"Refresh online package list"
		},
		{
			_keys[12],
			"Online"
		},
		{
			_keys[13],
			"Export"
		},
		{
			_keys[14],
			"Export without current translation"
		},
		{
			_keys[15],
			"Export T"
		},
		{
			_keys[16],
			"Export with translation"
		},
		{
			_keys[17],
			"New Version"
		},
		{
			_keys[18],
			"Packages loading"
		},
		{
			_keys[19],
			"{0}, Click to open {1} config"
		},
		{
			_keys[20],
			"{0}, translated by {1}"
		}
	};

	private static Dictionary<string, string> _zh = new Dictionary<string, string>
	{
		{
			_keys[0],
			"发现汉化者Mod有新版本: {0} \n请前往更新。"
		},
		{
			_keys[1],
			"汉化包管理"
		},
		{
			_keys[2],
			"重新加载"
		},
		{
			_keys[3],
			"重新读取所有汉化包"
		},
		{
			_keys[4],
			"打开文件夹"
		},
		{
			_keys[5],
			"查找下载、导出的汉化包及配置文件"
		},
		{
			_keys[6],
			"你没有这个Mod的汉化包，\n请下载或自己制作"
		},
		{
			_keys[7],
			"已启用"
		},
		{
			_keys[8],
			"未启用"
		},
		{
			_keys[9],
			"({0}) {1} v{2} 作者:{3}"
		},
		{
			_keys[10],
			"刷新"
		},
		{
			_keys[11],
			"刷新在线汉化包列表"
		},
		{
			_keys[12],
			"未下载"
		},
		{
			_keys[13],
			"导出"
		},
		{
			_keys[14],
			"导出不包含当前翻译的汉化包"
		},
		{
			_keys[15],
			"导出翻译"
		},
		{
			_keys[16],
			"导出包含翻译的汉化包"
		},
		{
			_keys[17],
			"可更新"
		},
		{
			_keys[18],
			"正在加载汉化包，请稍候查看或刷新"
		},
		{
			_keys[19],
			"{0}，点击打开{1}设置界面"
		},
		{
			_keys[20],
			"{0}，汉化：{1}"
		}
	};

	public static void AddModTranslations(Mod mod)
	{
		foreach (string key in _keys)
		{
			ModTranslation val = mod.CreateTranslation(key);
			val.SetDefault(_en[key]);
			val.AddTranslation(GameCulture.Chinese, _zh[key]);
			mod.AddTranslation(val);
		}
	}

	public static string _(string key, params object[] args)
	{
		string textValue = Language.GetTextValue("Mods.Localizer." + key);
		if (textValue == "Mods.Localizer." + key)
		{
			textValue = Language.GetTextValue("Mods.!Localizer." + key);
		}
		return string.Format(textValue, args);
	}
}
}
