using System.Collections.Generic;
using System.Linq;

namespace Localizer.DataModel.Default
{
public class CustomModTranslationFile : IFile
{
	public Dictionary<string, BaseEntry> Translations { get; set; } = new Dictionary<string, BaseEntry>();

	public List<string> GetKeys()
	{
		return Translations.Keys.ToList();
	}

	public IEntry GetValue(string key)
	{
		return Translations[key];
	}
}
}
