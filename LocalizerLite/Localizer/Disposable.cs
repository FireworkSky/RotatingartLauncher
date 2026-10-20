using System;

namespace Localizer
{
public abstract class Disposable : IDisposable
{
	protected bool disposed;

	public void Dispose()
	{
		Dispose(disposeManaged: true);
		GC.SuppressFinalize(this);
	}

	~Disposable()
	{
		Dispose(disposeManaged: false);
	}

	protected virtual void Dispose(bool disposeManaged)
	{
		if (!disposed)
		{
			if (disposeManaged)
			{
				DisposeManaged();
			}
			DisposeUnmanaged();
			disposed = true;
		}
	}

	protected virtual void DisposeManaged()
	{
	}

	protected virtual void DisposeUnmanaged()
	{
	}
}
}
