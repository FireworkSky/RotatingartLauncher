[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$OutputPath,
    [Parameter(Mandatory)] [string]$TargetTml,
    [Parameter(Mandatory)] [string]$TerrariaContent,
    [Parameter(Mandatory)] [string]$TmlContent,
    [Parameter(Mandatory)] [string]$ImageLayout,
    [Parameter(Mandatory)] [string]$TextureFallback
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if ($TargetTml -cnotmatch '^(?:20\d{2}|9999)\.\d{1,2}(?:\.\d{1,5}){0,2}$') {
    throw 'Provide the exact tModLoader version for the resource profile.'
}

$assets = @(
    foreach ($folder in @('Sounds', 'Fonts')) {
        $directory = (Resolve-Path -LiteralPath (Join-Path $TerrariaContent $folder)).Path
        $files = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter '*.xnb')
        if ($files.Count -eq 0) { throw "Desktop Terraria $folder inventory is empty." }
        foreach ($file in $files) {
            "$folder/" + $file.FullName.Substring($directory.Length + 1).Replace('\', '/')
        }
    }
)
$files = [ordered]@{
    'images.tsv' = $ImageLayout
    'texture-fallback.zip' = $TextureFallback
    'TerrariaMusic.xgs' = Join-Path $TerrariaContent 'TerrariaMusic.xgs'
    'Sound Bank.xsb' = Join-Path $TerrariaContent 'Sound Bank.xsb'
    'PixelShader.xnb' = Join-Path $TmlContent 'PixelShader.xnb'
    'ScreenShader.xnb' = Join-Path $TmlContent 'ScreenShader.xnb'
    'TileShader.xnb' = Join-Path $TmlContent 'TileShader.xnb'
}
$output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
if (Test-Path -LiteralPath $output) { throw "Output already exists: $output" }
$staged = "$output.$([Guid]::NewGuid()).tmp"
try {
    $zip = [IO.Compression.ZipFile]::Open($staged, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $json = [ordered]@{
            'profile.json' = ConvertTo-Json -InputObject @{ targetTml = $TargetTml } -Compress
            'assets.json' = ConvertTo-Json -InputObject @($assets | Sort-Object) -Compress
        }
        foreach ($entry in $json.GetEnumerator()) {
            $writer = [IO.StreamWriter]::new($zip.CreateEntry($entry.Key).Open(), [Text.UTF8Encoding]::new($false))
            try { $writer.Write($entry.Value) } finally { $writer.Dispose() }
        }
        foreach ($entry in $files.GetEnumerator()) {
            $source = (Resolve-Path -LiteralPath $entry.Value).Path
            $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $source, $entry.Key)
        }
    } finally {
        $zip.Dispose()
    }
    [IO.File]::Move($staged, $output)
} finally {
    if ([IO.File]::Exists($staged)) { [IO.File]::Delete($staged) }
}
Write-Output "Prepared Android converter profile at $output"
