using System.Threading.Channels;
using TmlAndroidBootstrap.Android;

namespace TmlAndroidHost;

internal sealed class LauncherActionRouter
{
    private readonly IAndroidBridge _bridge;
    private readonly LauncherOperationControl _operation;
    private readonly CancellationTokenSource _flowCancellation;
    private readonly Channel<AndroidLauncherAction> _actions =
        Channel.CreateBounded<AndroidLauncherAction>(new BoundedChannelOptions(8)
        {
            FullMode = BoundedChannelFullMode.DropOldest,
            SingleReader = true,
            SingleWriter = true,
        });

    public LauncherActionRouter(
        IAndroidBridge bridge,
        LauncherOperationControl operation,
        CancellationTokenSource flowCancellation)
    {
        _bridge = bridge;
        _operation = operation;
        _flowCancellation = flowCancellation;
    }

    public async Task PumpAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                var action = await _bridge.WaitForLauncherActionAsync(cancellationToken)
                    .ConfigureAwait(false);
                switch (action)
                {
                    case AndroidLauncherAction.Pause:
                        _operation.Pause();
                        _bridge.SetStartupStatus(
                            "Paused",
                            "Content preparation is paused. Press Resume to continue.",
                            -1);
                        break;
                    case AndroidLauncherAction.Resume:
                        _operation.Resume();
                        break;
                    case AndroidLauncherAction.Cancel:
                        _flowCancellation.Cancel();
                        return;
                    default:
                        _actions.Writer.TryWrite(action);
                        break;
                }
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"Android launcher action pump failed: {exception}");
            _flowCancellation.Cancel();
        }
    }

    public ValueTask<AndroidLauncherAction> ReadAsync(CancellationToken cancellationToken) =>
        _actions.Reader.ReadAsync(cancellationToken);
}
