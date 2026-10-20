using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class PrefixEntry : IEntry
{
	[ModTranslationProp("DisplayName")]
	public BaseEntry Name { get; set; }

	public IEntry Clone()
	{
		return new PrefixEntry
		{
			Name = (Name.Clone() as BaseEntry)
		};
	}
}
}
