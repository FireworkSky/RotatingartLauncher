using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class BuffEntry : IEntry
{
	[ModTranslationProp("DisplayName")]
	public BaseEntry Name { get; set; }

	[ModTranslationProp("Description")]
	public BaseEntry Description { get; set; }

	public IEntry Clone()
	{
		return new BuffEntry
		{
			Name = (Name.Clone() as BaseEntry),
			Description = (Description.Clone() as BaseEntry)
		};
	}
}
}
