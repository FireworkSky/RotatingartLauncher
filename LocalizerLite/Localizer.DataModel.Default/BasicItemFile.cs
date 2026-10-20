using System.Collections.Generic;
using System.Linq;
using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class BasicItemFile : IFile
{
	[ModTranslationOwnerField("items")]
	public Dictionary<string, ItemEntry> Items { get; set; } = new Dictionary<string, ItemEntry>();

	public List<string> GetKeys()
	{
		return Items.Keys.ToList();
	}

	public IEntry GetValue(string key)
	{
		return Items[key];
	}
}
}
