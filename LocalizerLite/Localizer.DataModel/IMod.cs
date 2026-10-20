using System;
using System.Reflection;
using Terraria.ModLoader.Core;

namespace Localizer.DataModel
{
public interface IMod
{
	string Name { get; }

	Assembly Code { get; }

	string DisplayName { get; }

	Version Version { get; }

	TmodFile File { get; }

	bool Enabled { get; }
}
}
