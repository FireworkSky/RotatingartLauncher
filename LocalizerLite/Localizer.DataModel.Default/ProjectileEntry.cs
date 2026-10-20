using Localizer.Attributes;

namespace Localizer.DataModel.Default
{
public class ProjectileEntry : IEntry
{
	[ModTranslationProp("DisplayName")]
	public BaseEntry Name { get; set; }

	public IEntry Clone()
	{
		return new ProjectileEntry
		{
			Name = (Name.Clone() as BaseEntry)
		};
	}
}
}
