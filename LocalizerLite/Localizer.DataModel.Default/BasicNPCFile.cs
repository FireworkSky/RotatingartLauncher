using System.Collections.Generic;
using System.Linq;
using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class BasicNPCFile : IFile
{
	[ModTranslationOwnerField("npcs")]
	public Dictionary<string, NPCEntry> NPCs { get; set; } = new Dictionary<string, NPCEntry>();

	public List<string> GetKeys()
	{
		return NPCs.Keys.ToList();
	}

	public IEntry GetValue(string key)
	{
		return NPCs[key];
	}
}
}
