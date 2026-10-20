namespace TmlAndroidHost;

internal sealed class LauncherOperationControl
{
    private readonly object _gate = new();
    private TaskCompletionSource<bool> _resumed = CompletedSignal();
    private bool _paused;

    public void Pause()
    {
        lock (_gate)
        {
            if (_paused)
                return;

            _paused = true;
            _resumed = new TaskCompletionSource<bool>(
                TaskCreationOptions.RunContinuationsAsynchronously);
        }
    }

    public void Resume()
    {
        lock (_gate)
        {
            if (!_paused)
                return;

            _paused = false;
            _resumed.TrySetResult(true);
        }
    }

    public async ValueTask WaitIfPausedAsync(CancellationToken cancellationToken)
    {
        while (true)
        {
            Task waitTask;
            lock (_gate)
            {
                if (!_paused)
                    return;

                waitTask = _resumed.Task;
            }

            await waitTask.WaitAsync(cancellationToken).ConfigureAwait(false);
        }
    }

    private static TaskCompletionSource<bool> CompletedSignal()
    {
        var signal = new TaskCompletionSource<bool>(
            TaskCreationOptions.RunContinuationsAsynchronously);
        signal.TrySetResult(true);
        return signal;
    }
}
