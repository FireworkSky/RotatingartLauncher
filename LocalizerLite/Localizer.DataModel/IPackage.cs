using System;
using System.Collections.Generic;
using System.Globalization;

namespace Localizer.DataModel
{
public interface IPackage
{
	string Name { get; set; }

	string Author { get; set; }

	string ModName { get; set; }

	string LocalizedModName { get; set; }

	string Description { get; set; }

	Version Version { get; set; }

	Version ModVersion { get; set; }

	CultureInfo Language { get; set; }

	ICollection<string> FileList { get; set; }

	ICollection<IFile> Files { get; set; }

	int Count { get; }

	bool Enabled { get; set; }

	IMod Mod { get; set; }

	void AddFile(IFile file);

	void RemoveFile(IFile file);
}
}
