param([string]$Configuration = 'Release')

$ErrorActionPreference = 'Stop'
dotnet build (Join-Path $PSScriptRoot 'TModLoaderFrameRatePatch.csproj') -c $Configuration -v:minimal
if ($LASTEXITCODE -ne 0) { throw 'TModLoaderFrameRatePatch build failed' }

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$archive = Join-Path $repoRoot 'app/src/main/assets/patches/com.app.ralaunch.tmodloader.framerate.zip'
$assembly = Join-Path $PSScriptRoot "bin/$Configuration/net8.0/TModLoaderFrameRatePatch.dll"
Compress-Archive -LiteralPath (Join-Path $PSScriptRoot 'patch.json'), $assembly -DestinationPath $archive -Force
Write-Output "Packaged: $archive"
