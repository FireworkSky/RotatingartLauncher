using System;

namespace Localizer
{
public abstract class LocalizerPlugin : Disposable
{
	public abstract string Name { get; }

	public abstract string Author { get; }

	public virtual Version Version => new Version();

	public abstract string Description { get; }

	public abstract void Initialize(LocalizerKernel kernel);
}
}
