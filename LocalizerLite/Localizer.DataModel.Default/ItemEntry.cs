using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class ItemEntry : IEntry
{
	[ModTranslationProp("DisplayName")]
	public BaseEntry Name { get; set; }

	[ModTranslationProp("Tooltip")]
	public BaseEntry Tooltip { get; set; }

	public IEntry Clone()
	{
		return new ItemEntry
		{
			Name = (Name.Clone() as BaseEntry),
			Tooltip = (Tooltip.Clone() as BaseEntry)
		};
	}
}
}
