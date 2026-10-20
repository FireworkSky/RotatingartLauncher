namespace TmlAndroidBootstrap.Runtime;

public enum BootstrapStage
{
    Authentication,
    Ownership,
    Manifest,
    Download,
    Validation,
    Runtime,
    Game,
    ModCompilation
}

public sealed class BootstrapStageException : Exception
{
    public BootstrapStageException(BootstrapStage stage, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Stage = stage;
    }

    public BootstrapStage Stage { get; }
}
