# Apply custom SDL patches after submodule init/update
# Usage: .\apply_sdl_patches.ps1
#
# This script should be run after:
#   git submodule update --init app/src/main/cpp/SDL

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SdlDir = Join-Path $ScriptDir "app\src\main\cpp\SDL"
$PatchesDir = Join-Path $ScriptDir "app\src\main\cpp\SDL_patches"

if (-not (Test-Path $SdlDir)) {
    Write-Error "SDL submodule not found at $SdlDir`nRun: git submodule update --init app/src/main/cpp/SDL"
    exit 1
}

# Checkout the correct tag
Write-Host "Checking out SDL release-2.30.1..."
Push-Location $SdlDir
git checkout release-2.30.1

# Apply patches
Write-Host "Applying custom SDL patches..."
$patches = Get-ChildItem -Path $PatchesDir -Filter "*.patch" -ErrorAction SilentlyContinue
foreach ($patch in $patches) {
    Write-Host "  Applying: $($patch.Name)"
    git apply $patch.FullName
}
Pop-Location

Write-Host "SDL patches applied successfully!"
