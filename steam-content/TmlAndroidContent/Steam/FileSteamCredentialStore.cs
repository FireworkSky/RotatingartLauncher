using System.Text.Json;

namespace TmlAndroidContent.Steam;

/// <summary>
/// 非 Android（CLI / RAL 进程）环境下的凭据存储：只存 refresh token + GuardData，
/// 密码不落盘，文件权限 600。
/// </summary>
public sealed class FileSteamCredentialStore : ISteamCredentialStore
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly string _path;

    public FileSteamCredentialStore(string path)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(path);
        _path = Path.GetFullPath(path);
    }

    public async ValueTask<SteamCredentials?> LoadAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_path))
            return null;
        try
        {
            var json = await File.ReadAllTextAsync(_path, cancellationToken).ConfigureAwait(false);
            return JsonSerializer.Deserialize<SteamCredentials>(json, JsonOptions);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    public async ValueTask SaveAsync(SteamCredentials credentials, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(credentials);
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        var json = JsonSerializer.Serialize(credentials, JsonOptions);
        await File.WriteAllTextAsync(_path, json, cancellationToken).ConfigureAwait(false);
        try
        {
            File.SetUnixFileMode(_path, UnixFileMode.UserRead | UnixFileMode.UserWrite);
        }
        catch (Exception)
        {
            // 非 Unix 文件系统忽略
        }
    }

    public ValueTask ClearAsync(CancellationToken cancellationToken = default)
    {
        if (File.Exists(_path))
            File.Delete(_path);
        return ValueTask.CompletedTask;
    }
}
