using MonoMod.RuntimeDetour;
using System.Reflection;
using System.Runtime.Loader;
using TmlAndroidBootstrap.Android;

namespace TmlAndroidBootstrap.Runtime;

public sealed class TmlLoaderEntrypoint
{
    private static Hook? _loggingHooksBypass;
    private int _started;

    public int Launch(
        string assemblyPath,
        string[] arguments,
        string workingDirectory,
        IAndroidBridge androidBridge)
    {
        if (Interlocked.Exchange(ref _started, 1) != 0)
            throw new InvalidOperationException("tModLoader can only be launched once per CoreCLR process.");

        if (!File.Exists(assemblyPath))
            throw new BootstrapStageException(BootstrapStage.Validation, $"tModLoader assembly was not found: {assemblyPath}");

        Directory.CreateDirectory(workingDirectory);
        Directory.SetCurrentDirectory(workingDirectory);
        Environment.SetEnvironmentVariable("DOTNET_ROOT", workingDirectory);
        Environment.SetEnvironmentVariable("XDG_DATA_HOME", workingDirectory);
        Environment.SetEnvironmentVariable("XDG_CONFIG_HOME", workingDirectory);

        try
        {
            var assemblyDirectory = Path.GetDirectoryName(Path.GetFullPath(assemblyPath))!;
            var monoModPath = Path.Combine(assemblyDirectory, "MonoMod.Android");
            Environment.SetEnvironmentVariable("MONOMOD_PATH", monoModPath);
            RegisterDependencyResolver(assemblyDirectory);
            Assembly assembly = AssemblyLoadContext.Default.LoadFromAssemblyPath(Path.GetFullPath(assemblyPath));
            ApplyAndroidCompatibilityPatch(assembly);
            using var platformServices = AndroidPlatformServiceProxy.TryConfigure(assembly, androidBridge);
            LogPlatformConfiguration(androidBridge, platformServices is not null);
            MethodInfo? entryPoint = assembly.EntryPoint;
            if (entryPoint is null)
                throw new BootstrapStageException(BootstrapStage.Runtime, "tModLoader assembly has no entry point.");

            object? result = entryPoint.GetParameters().Length == 0
                ? entryPoint.Invoke(null, null)
                : entryPoint.Invoke(null, new object?[] { arguments });
            return result is int exitCode ? exitCode : 0;
        }
        catch (TargetInvocationException exception) when (exception.InnerException is not null)
        {
            throw new BootstrapStageException(BootstrapStage.Game, "tModLoader entry point failed.", exception.InnerException);
        }
    }

    private static void LogPlatformConfiguration(IAndroidBridge bridge, bool servicesConfigured)
    {
        var platformType = Type.GetType("ReLogic.OS.Platform, ReLogic", throwOnError: false);
        var isAndroid = platformType?.GetProperty("IsAndroid", BindingFlags.Public | BindingFlags.Static)
            ?.GetValue(null) as bool? ?? false;
        Console.WriteLine(
            $"[TmlAndroidBootstrap] PlatformType={(isAndroid ? "Android" : "Other")}; " +
            $"files={bridge.FilesDirectory}; cache={bridge.CacheDirectory}; " +
            $"platformServicesConfigured={servicesConfigured}");
    }

    private static void ApplyAndroidCompatibilityPatch(Assembly assembly)
    {
        if (!IsAndroidRuntime())
            return;

        var loggingHooks = assembly.GetType("Terraria.ModLoader.Engine.LoggingHooks");
        var initialize = loggingHooks?.GetMethod(
            "Init",
            BindingFlags.Static | BindingFlags.NonPublic);
        if (initialize is null)
            throw new BootstrapStageException(BootstrapStage.Runtime, "tModLoader LoggingHooks.Init was not found.");

        _loggingHooksBypass = new Hook(initialize, new Action<Action>(SkipUnsupportedAndroidLoggingHooks));
        Console.WriteLine("[TmlAndroidBootstrap] Applied Android LoggingHooks MonoMod hook.");
    }

    private static void SkipUnsupportedAndroidLoggingHooks(Action original)
    {
    }

    private static void RegisterDependencyResolver(string assemblyDirectory)
    {
        var searchDirectories = new List<string>();
        var monoModPath = Environment.GetEnvironmentVariable("MONOMOD_PATH");
        if (!string.IsNullOrWhiteSpace(monoModPath))
        {
            searchDirectories.AddRange(monoModPath.Split(
                Path.PathSeparator,
                StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries));
        }
        searchDirectories.Add(assemblyDirectory);

        var libraryDirectory = Path.Combine(assemblyDirectory, "Libraries");
        var assemblyIndex = new Lazy<IReadOnlyDictionary<string, string[]>>(
            () => BuildAssemblyIndex(libraryDirectory),
            LazyThreadSafetyMode.ExecutionAndPublication);

        Assembly? ResolveAssembly(AssemblyName requestedAssembly)
        {
            var assemblyFile = requestedAssembly.Name + ".dll";
            var candidates = new List<string>();
            foreach (var directory in searchDirectories)
            {
                var candidate = Path.Combine(directory, assemblyFile);
                if (File.Exists(candidate))
                    candidates.Add(candidate);
            }

            if (assemblyIndex.Value.TryGetValue(assemblyFile, out var indexedCandidates))
                candidates.AddRange(indexedCandidates);

            var resolvedPath = candidates.FirstOrDefault(candidate =>
                MatchesAssembly(candidate, requestedAssembly, requireExactVersion: true));
            resolvedPath ??= candidates.FirstOrDefault(candidate =>
                MatchesAssembly(candidate, requestedAssembly, requireExactVersion: false));
            if (resolvedPath is null)
                return null;

            Console.WriteLine(
                $"[TmlAndroidBootstrap] Resolved {requestedAssembly.FullName} from {resolvedPath}");
            return AssemblyLoadContext.Default.LoadFromAssemblyPath(resolvedPath);
        }

        AssemblyLoadContext.Default.Resolving += (_, requestedAssembly) =>
            ResolveAssembly(requestedAssembly);
        AppDomain.CurrentDomain.AssemblyResolve += (_, args) =>
            ResolveAssembly(new AssemblyName(args.Name));
    }

    private static IReadOnlyDictionary<string, string[]> BuildAssemblyIndex(string libraryDirectory)
    {
        if (!Directory.Exists(libraryDirectory))
            return new Dictionary<string, string[]>(StringComparer.OrdinalIgnoreCase);

        return Directory.EnumerateFiles(libraryDirectory, "*.dll", SearchOption.AllDirectories)
            .Where(path => !IsReferenceAssembly(path))
            .Select(path => (Name: Path.GetFileName(path)!, Path: path))
            .GroupBy(file => file.Name, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(
                group => group.Key,
                group => group
                    .Select(file => file.Path)
                    .OrderBy(GetAssemblyCandidatePriority)
                    .ToArray(),
                StringComparer.OrdinalIgnoreCase);
    }

    private static int GetAssemblyCandidatePriority(string path)
    {
        if (IsAndroidRuntime() &&
            path.Contains(
                $"{Path.DirectorySeparatorChar}runtimes{Path.DirectorySeparatorChar}linux{Path.DirectorySeparatorChar}",
                StringComparison.OrdinalIgnoreCase))
            return 0;

        if (path.Contains(
                $"{Path.DirectorySeparatorChar}runtimes{Path.DirectorySeparatorChar}",
                StringComparison.OrdinalIgnoreCase))
            return 1;

        return 2;
    }

    private static bool IsAndroidRuntime()
    {
        return OperatingSystem.IsAndroid() ||
            !string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("ANDROID_ROOT"));
    }

    private static bool IsReferenceAssembly(string path)
    {
        var probeContext = new AssemblyLoadContext(
            $"TmlAndroidBootstrap.ReferenceProbe.{Guid.NewGuid():N}",
            isCollectible: true);
        try
        {
            probeContext.LoadFromAssemblyPath(path);
            return false;
        }
        catch (BadImageFormatException exception)
        {
            return exception.Message.Contains(
                "Reference assemblies cannot be loaded",
                StringComparison.OrdinalIgnoreCase);
        }
        catch (Exception exception) when (exception is IOException or FileLoadException)
        {
            return false;
        }
        finally
        {
            probeContext.Unload();
        }
    }

    private static bool MatchesAssembly(
        string path,
        AssemblyName requestedAssembly,
        bool requireExactVersion)
    {
        try
        {
            var candidate = AssemblyName.GetAssemblyName(path);
            if (!string.Equals(candidate.Name, requestedAssembly.Name, StringComparison.OrdinalIgnoreCase))
                return false;

            if (requireExactVersion && requestedAssembly.Version is not null &&
                requestedAssembly.Version != new Version(0, 0, 0, 0) &&
                candidate.Version != requestedAssembly.Version)
                return false;

            var requestedToken = requestedAssembly.GetPublicKeyToken();
            var candidateToken = candidate.GetPublicKeyToken();
            return requestedToken is null || requestedToken.Length == 0 ||
                candidateToken is not null && requestedToken.SequenceEqual(candidateToken);
        }
        catch (Exception exception) when (exception is IOException or BadImageFormatException)
        {
            return false;
        }
    }
}
