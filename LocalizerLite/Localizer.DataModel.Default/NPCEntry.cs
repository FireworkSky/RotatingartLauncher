using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class NPCEntry : IEntry
{
	[ModTranslationProp("DisplayName")]
	public BaseEntry Name { get; set; }

	public IEntry Clone()
	{
		return new NPCEntry
		{
			Name = (Name.Clone() as BaseEntry)
		};
	}
}
}
