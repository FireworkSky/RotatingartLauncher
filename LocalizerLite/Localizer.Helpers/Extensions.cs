using System;
using System.Globalization;
using System.Linq;
using System.Reflection;
using Localizer.Attributes;
using Localizer.DataModel.Default;
using Terraria.ModLoader;

namespace Localizer.Helpers
{
public static class Extensions
{
	public static void Import(this ModTranslation modTranslation, BaseEntry entry, CultureInfo culture)
	{
		if (entry != null)
		{
			if (entry.Origin != modTranslation.GetDefault() && modTranslation.GetDefault() != modTranslation.Key && !string.IsNullOrWhiteSpace(modTranslation.GetDefault()) && !string.IsNullOrWhiteSpace(entry.Origin))
			{
				Utils.LogWarn("Mismatch origin text when importing \"" + modTranslation.Key + "\"" + Environment.NewLine + "Origin in mod: " + modTranslation.GetDefault() + Environment.NewLine + "Origin in package: " + entry.Origin + Environment.NewLine + "Translation in package: " + entry.Translation);
			}
			else if (modTranslation.GetDefault() != null && !string.IsNullOrEmpty(entry.Translation) && entry.Translation != modTranslation.Key)
			{
				modTranslation.AddTranslation(Localizer.CultureInfoToGameCulture(culture), entry.Translation);
			}
		}
	}

	public static string GetTranslation(this ModTranslation modTranslation, CultureInfo culture)
	{
		string translation = modTranslation.GetTranslation(Localizer.CultureInfoToGameCulture(culture));
		if (!string.IsNullOrWhiteSpace(translation))
		{
			return translation;
		}
		return "";
	}

	public static string DefaultOrEmpty(this ModTranslation modTranslation)
	{
		string text = modTranslation.GetDefault();
		if (!string.IsNullOrWhiteSpace(text) && !(text == modTranslation.Key))
		{
			return text;
		}
		return "";
	}

	public static PropertyInfo[] ModTranslationOwnerField(this Type type)
	{
		return (from p in type.GetProperties()
			where p.GetCustomAttribute<ModTranslationOwnerFieldAttribute>() != null
			select p).ToArray();
	}

	public static string ModTranslationOwnerFieldName(this PropertyInfo prop)
	{
		return prop.GetCustomAttribute<ModTranslationOwnerFieldAttribute>()?.FieldName;
	}

	public static PropertyInfo[] ModTranslationProp(this Type type)
	{
		return (from p in type.GetProperties()
			where p.GetCustomAttribute<ModTranslationPropAttribute>() != null
			select p).ToArray();
	}
}
}
