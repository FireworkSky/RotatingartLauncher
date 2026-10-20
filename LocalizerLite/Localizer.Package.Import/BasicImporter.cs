using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Reflection;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Localizer.Helpers;
using Terraria.ModLoader;

namespace Localizer.Package.Import
{
public class BasicImporter<T> : FileImporter where T : IFile
{
	public override void Import(IFile file, IMod mod, CultureInfo culture)
	{
		ImportInternal((T)file, mod, culture);
	}

	private void ImportInternal(T file, IMod mod, CultureInfo culture)
	{
		PropertyInfo[] array = typeof(T).ModTranslationOwnerField();
		foreach (PropertyInfo obj in array)
		{
			IDictionary field = NoroHelper.ValueOf<IDictionary>(name: obj.ModTranslationOwnerFieldName(), obj: Utils.GetModByName(mod.Name));
			Type entryType = obj.PropertyType.GenericTypeArguments.FirstOrDefault((Type g) => Enumerable.Contains(g.GetInterfaces(), typeof(IEntry)));
			IDictionary entries = (IDictionary)obj.GetValue(file);
			ApplyEntries(entries, field, entryType, culture);
		}
	}

	public override IFile Merge(IFile main, IFile addition)
	{
		return MergeInternal((T)main, (T)addition);
	}

	internal T MergeInternal(T main, T addition)
	{
		object obj = Activator.CreateInstance(typeof(T));
		PropertyInfo[] array = typeof(T).ModTranslationOwnerField();
		foreach (PropertyInfo propertyInfo in array)
		{
			IDictionary dictionary = (IDictionary)Activator.CreateInstance(propertyInfo.PropertyType);
			IDictionary dictionary2 = (IDictionary)propertyInfo.GetValue(main);
			IDictionary dictionary3 = (IDictionary)propertyInfo.GetValue(addition);
			foreach (string key3 in dictionary2.Keys)
			{
				dictionary.Add(key3, (dictionary2[key3] as IEntry)?.Clone());
			}
			foreach (string key4 in dictionary3.Keys)
			{
				if (dictionary.Contains(key4))
				{
					dictionary[key4] = Merge((IEntry)dictionary2[key4], (IEntry)dictionary3[key4]);
				}
				else
				{
					dictionary.Add(key4, dictionary3[key4]);
				}
			}
			propertyInfo.SetValue(obj, dictionary);
		}
		return (T)obj;
	}

	public IEntry Merge(IEntry main, IEntry addition)
	{
		if (main.GetType() != addition.GetType())
		{
			return main;
		}
		IEntry entry = Activator.CreateInstance(main.GetType()) as IEntry;
		PropertyInfo[] array = entry.GetType().ModTranslationProp();
		foreach (PropertyInfo propertyInfo in array)
		{
			string text = Utils.GetTranslationOfEntry(main, propertyInfo);
			if (string.IsNullOrEmpty(text))
			{
				text = Utils.GetTranslationOfEntry(addition, propertyInfo) ?? "";
			}
			BaseEntry baseEntry = Activator.CreateInstance(propertyInfo.PropertyType) as BaseEntry;
			baseEntry.Translation = text;
			propertyInfo.SetValue(entry, baseEntry);
		}
		return entry;
	}

	private void ApplyEntries(IDictionary entries, IDictionary field, Type entryType, CultureInfo culture)
	{
		Dictionary<string, PropertyInfo> dictionary = Utils.CreateEntryMappings(entryType);
		foreach (string key in entries.Keys)
		{
			if (!field.Contains(key))
			{
				continue;
			}
			foreach (KeyValuePair<string, PropertyInfo> item in dictionary)
			{
				object obj = field[key];
				object obj2 = obj?.GetType().GetProperty(item.Key)?.GetValue(obj);
				((ModTranslation)((obj2 is ModTranslation) ? obj2 : null))?.Import(item.Value.GetValue(entries[key]) as BaseEntry, culture);
			}
		}
	}
}
}
