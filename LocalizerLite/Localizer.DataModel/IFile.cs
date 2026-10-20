using System.Collections.Generic;

namespace Localizer.DataModel
{
public interface IFile
{
	List<string> GetKeys();

	IEntry GetValue(string key);
}
}
