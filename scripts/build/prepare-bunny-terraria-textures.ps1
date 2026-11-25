[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ReportPath,
    [Parameter(Mandatory)] [string]$TerrariaContent,
    [Parameter(Mandatory)] [string]$TmlContent,
    [Parameter(Mandatory)] [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$report = Get-Content -LiteralPath $ReportPath -Raw | ConvertFrom-Json
$textures = @($report.missing | Where-Object { $_.path -clike 'Images/*' } | ForEach-Object { $_.path } | Sort-Object -Unique)
if ($textures.Count -eq 0) { throw 'The conversion report has no missing textures.' }

$roots = @((Resolve-Path -LiteralPath $TmlContent).Path, (Resolve-Path -LiteralPath $TerrariaContent).Path)
$sources = [ordered]@{}
foreach ($path in $textures) {
    if ($path -cnotmatch '^Images/(?:[\w. -]+/)*[\w. -]+\.xnb$' -or $path -match '(?:^|/)\.{1,2}(?:/|$)') {
        throw "Invalid texture path: $path"
    }
    foreach ($root in $roots) {
        $source = Join-Path $root $path
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            $sources[$path] = $source
            break
        }
    }
    if (-not $sources.Contains($path)) { throw "Texture is absent from both installations: $path" }
}

$output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
if (Test-Path -LiteralPath $output) { throw "Output already exists: $output" }
$staged = "$output.$([Guid]::NewGuid()).tmp"
try {
    $zip = [IO.Compression.ZipFile]::Open($staged, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $sources.GetEnumerator()) {
            $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zip, $entry.Value, $entry.Key, [IO.Compression.CompressionLevel]::Optimal
            )
        }
    } finally {
        $zip.Dispose()
    }
    [IO.File]::Move($staged, $output)
} finally {
    if ([IO.File]::Exists($staged)) { [IO.File]::Delete($staged) }
}
Write-Output "Packed $($sources.Count) missing textures into $output"
