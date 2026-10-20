using System.Collections.Generic;

namespace TmlAndroidBootstrap.Android;

public sealed class AndroidEventQueue
{
    private readonly object _gate = new();
    private readonly Queue<AndroidInputEvent> _events;
    private readonly SemaphoreSlim _available = new(0);
    private readonly int _capacity;
    private long _dropped;

    public AndroidEventQueue(int capacity = 512)
    {
        if (capacity < 1)
            throw new ArgumentOutOfRangeException(nameof(capacity));

        _capacity = capacity;
        _events = new Queue<AndroidInputEvent>(capacity);
    }

    public int Count
    {
        get
        {
            lock (_gate)
                return _events.Count;
        }
    }

    public long DroppedCount => Interlocked.Read(ref _dropped);

    public void Enqueue(AndroidInputEvent value)
    {
        lock (_gate)
        {
            if (_events.Count == _capacity)
            {
                _events.Dequeue();
                _available.Wait(0);
                Interlocked.Increment(ref _dropped);
            }

            _events.Enqueue(value);
            _available.Release();
        }
    }

    public bool TryDequeue(out AndroidInputEvent value)
    {
        if (!_available.Wait(0))
        {
            value = default;
            return false;
        }

        lock (_gate)
        {
            if (_events.Count == 0)
            {
                value = default;
                return false;
            }

            value = _events.Dequeue();
            return true;
        }
    }

    public async ValueTask<AndroidInputEvent> DequeueAsync(CancellationToken cancellationToken = default)
    {
        await _available.WaitAsync(cancellationToken).ConfigureAwait(false);
        lock (_gate)
        {
            if (_events.Count == 0)
                throw new InvalidOperationException("Android event queue signal was out of sync.");

            return _events.Dequeue();
        }
    }
}
