using System.Collections.Generic;
using System.Globalization;
using System.Reflection;
using Localizer.Attributes;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Localizer.Helpers;
using Terraria.ModLoader;

namespace Localizer.Package.Import
{
[OperationTiming(OperationTiming.PostContentLoad)]
public class CustomModTranslationImporter : FileImporter
{
	public override void Import(IFile file, IMod mod, CultureInfo culture)
	{
		ImportInternal(file as CustomModTranslationFile, mod, culture);
	}

	private void ImportInternal(CustomModTranslationFile file, IMod mod, CultureInfo culture)
	{
		Dictionary<string, BaseEntry> translations = file.Translations;
		IDictionary<string, ModTranslation> dictionary = Utils.GetModByName(mod.Name).ValueOf<IDictionary<string, ModTranslation>>("translations");
		if (dictionary == null)
		{
			return;
		}
		foreach (KeyValuePair<string, BaseEntry> item in translations)
		{
			if (dictionary.ContainsKey(item.Key))
			{
				ModTranslation val = dictionary[item.Key];
				val?.Import(item.Value, culture);
				dictionary[item.Key] = val;
			}
		}
	}

	public override IFile Merge(IFile main, IFile addition)
	{
		return MergeInternal(main as CustomModTranslationFile, addition as CustomModTranslationFile);
	}

	internal CustomModTranslationFile MergeInternal(CustomModTranslationFile main, CustomModTranslationFile addition)
	{
		CustomModTranslationFile customModTranslationFile = new CustomModTranslationFile
		{
			Translations = new Dictionary<string, BaseEntry>()
		};
		foreach (KeyValuePair<string, BaseEntry> translation in main.Translations)
		{
			customModTranslationFile.Translations.Add(translation.Key, translation.Value.Clone() as BaseEntry);
		}
		foreach (KeyValuePair<string, BaseEntry> translation2 in addition.Translations)
		{
			if (customModTranslationFile.Translations.ContainsKey(translation2.Key))
			{
				customModTranslationFile.Translations[translation2.Key] = Merge(main.Translations[translation2.Key], translation2.Value);
			}
			else
			{
				customModTranslationFile.Translations.Add(translation2.Key, translation2.Value.Clone() as BaseEntry);
			}
		}
		return customModTranslationFile;
	}

	public BaseEntry Merge(BaseEntry main, BaseEntry addition)
	{
		BaseEntry baseEntry = main.Clone() as BaseEntry;
		if (string.IsNullOrWhiteSpace(main.Translation))
		{
			baseEntry.Translation = addition.Translation;
		}
		return baseEntry;
	}
}
}
