using System.Collections.Generic;
using System.Linq;
using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
[OperationTiming(OperationTiming.Any)]
public class LdstrFile : IFile
{
	public Dictionary<string, LdstrEntry> LdstrEntries { get; set; } = new Dictionary<string, LdstrEntry>();

	public List<string> GetKeys()
	{
		return LdstrEntries.Keys.ToList();
	}

	public IEntry GetValue(string key)
	{
		return LdstrEntries[key];
	}
}
}
