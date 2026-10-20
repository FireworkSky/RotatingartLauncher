using System;

namespace Localizer.Attributes
{
public class ModTranslationOwnerFieldAttribute : Attribute
{
	public string FieldName { get; }

	public ModTranslationOwnerFieldAttribute(string fieldName)
	{
		FieldName = fieldName;
	}
}
}
