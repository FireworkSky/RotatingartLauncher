using System;

namespace Localizer.Attributes
{
public class ModTranslationPropAttribute : Attribute
{
	public string PropName { get; }

	public ModTranslationPropAttribute(string propName)
	{
		PropName = propName;
	}
}
}
