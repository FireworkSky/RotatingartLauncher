using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;

namespace TmlAndroidContent.Steam;

public sealed record SteamCredentials(
    string AccountName,
    string RefreshToken,
    string? GuardData);

public interface ISteamCredentialStore
{
    ValueTask<SteamCredentials?> LoadAsync(CancellationToken cancellationToken = default);

    ValueTask SaveAsync(SteamCredentials credentials, CancellationToken cancellationToken = default);

    ValueTask ClearAsync(CancellationToken cancellationToken = default);
}

public interface ISteamGuardCodeProvider
{
    Task<string> GetCodeAsync(bool previousCodeWasIncorrect, CancellationToken cancellationToken);
}

/// <summary>
/// Stores only the refresh token and GuardData through Android Keystore-backed
/// AES-GCM storage. The Steam password never reaches this type.
/// </summary>
public sealed class AndroidKeystoreSteamCredentialStore : ISteamCredentialStore
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public ValueTask<SteamCredentials?> LoadAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var blob = NativeMethods.Load();
        if (blob == IntPtr.Zero)
            return ValueTask.FromResult<SteamCredentials?>(null);

        var json = Marshal.PtrToStringUTF8(blob);
        if (string.IsNullOrWhiteSpace(json))
            return ValueTask.FromResult<SteamCredentials?>(null);

        try
        {
            var payload = Encoding.UTF8.GetBytes(json);
            return ValueTask.FromResult(JsonSerializer.Deserialize<SteamCredentials>(payload, JsonOptions));
        }
        catch (JsonException)
        {
            return ValueTask.FromResult<SteamCredentials?>(null);
        }
    }

    public ValueTask SaveAsync(
        SteamCredentials credentials,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(credentials);
        cancellationToken.ThrowIfCancellationRequested();
        var json = JsonSerializer.Serialize(credentials, JsonOptions);
        if (NativeMethods.Save(json) != 0)
            throw new InvalidOperationException("Android Keystore could not save Steam credentials.");
        return ValueTask.CompletedTask;
    }

    public ValueTask ClearAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (NativeMethods.Clear() != 0)
            throw new InvalidOperationException("Android Keystore could not clear Steam credentials.");
        return ValueTask.CompletedTask;
    }

    private static class NativeMethods
    {
        [DllImport("main", EntryPoint = "fna_android_bridge_load_steam_credentials")]
        public static extern IntPtr Load();

        [DllImport("main", EntryPoint = "fna_android_bridge_save_steam_credentials")]
        public static extern int Save([MarshalAs(UnmanagedType.LPUTF8Str)] string json);

        [DllImport("main", EntryPoint = "fna_android_bridge_clear_steam_credentials")]
        public static extern int Clear();
    }
}
