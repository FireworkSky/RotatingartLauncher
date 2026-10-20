using System.Collections.Generic;
using System.Linq;
using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class BasicBuffFile : IFile
{
	[ModTranslationOwnerField("buffs")]
	public Dictionary<string, BuffEntry> Buffs { get; set; } = new Dictionary<string, BuffEntry>();

	public List<string> GetKeys()
	{
		return Buffs.Keys.ToList();
	}

	public IEntry GetValue(string key)
	{
		return Buffs[key];
	}
}
}
