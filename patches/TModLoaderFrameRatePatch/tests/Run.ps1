param([string]$TmlRoot = 'D:/Steam/steamapps/common/tModLoader')

$ErrorActionPreference = 'Stop'
$patchRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
dotnet build (Join-Path $patchRoot 'TModLoaderFrameRatePatch.csproj') -c Release -v:minimal
if ($LASTEXITCODE -ne 0) { throw 'Production patch build failed' }
dotnet build (Join-Path $PSScriptRoot 'RuntimeHarness/RuntimeHarness.csproj') -c Release "-p:TmlRoot=$TmlRoot" -v:minimal
if ($LASTEXITCODE -ne 0) { throw 'Runtime harness build failed' }

$harnessRoot = Join-Path $PSScriptRoot 'RuntimeHarness/bin/Release/net8.0'
dotnet (Join-Path $harnessRoot 'RuntimeHarness.dll')
if ($LASTEXITCODE -ne 0) { throw 'Runtime harness failed' }

$savedHooks = $env:DOTNET_STARTUP_HOOKS
$savedMonoPath = $env:MONOMOD_PATH
try {
    $env:DOTNET_STARTUP_HOOKS = Join-Path $patchRoot 'bin/Release/net8.0/TModLoaderFrameRatePatch.dll'
    $env:MONOMOD_PATH = $harnessRoot
    dotnet (Join-Path $harnessRoot 'RuntimeHarness.dll') --startup-smoke
    if ($LASTEXITCODE -ne 0) { throw 'Production startup-hook test failed' }
}
finally {
    $env:DOTNET_STARTUP_HOOKS = $savedHooks
    $env:MONOMOD_PATH = $savedMonoPath
}
