using System.Collections.Generic;

namespace Localizer.DataModel.Default
{
public class LdstrEntry : IEntry
{
	public List<BaseEntry> Instructions { get; set; }

	public IEntry Clone()
	{
		LdstrEntry ldstrEntry = new LdstrEntry
		{
			Instructions = new List<BaseEntry>()
		};
		foreach (BaseEntry instruction in Instructions)
		{
			ldstrEntry.Instructions.Add(instruction.Clone() as BaseEntry);
		}
		return ldstrEntry;
	}
}
}
