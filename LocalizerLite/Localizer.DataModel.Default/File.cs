using System.Collections.Generic;
using Newtonsoft.Json;

namespace Localizer.DataModel.Default
{
public abstract class File : IFile
{
	[JsonIgnore]
	public IPackage Owner { get; set; }

	public abstract List<string> GetKeys();

	public abstract IEntry GetValue(string key);

	public abstract void AddEntry(string key, IEntry entry);
}
}
