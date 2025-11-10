# Build libhostpolicy.so for Android from .NET 10 RC 2
# 为 Android 编译 .NET 10 RC 2 的 libhostpolicy.so

param(
    [string]$AndroidNdkPath = "D:\Android\ndk\27.2.12479018",
    [string]$DotnetSourcePath = "D:\runtime-10.0.0-rc.2",
    [string]$OutputPath = "D:\Rotating-art-Launcher\app\src\main\jnilibs\arm64-v8a"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building libhostpolicy.so for Android" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查路径是否存在
Write-Host "1. Checking paths..." -ForegroundColor Yellow

if (-not (Test-Path $AndroidNdkPath)) {
    Write-Host "❌ Android NDK not found at: $AndroidNdkPath" -ForegroundColor Red
    Write-Host "   Please set correct NDK path" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ Android NDK found" -ForegroundColor Green

if (-not (Test-Path $DotnetSourcePath)) {
    Write-Host "❌ .NET source not found at: $DotnetSourcePath" -ForegroundColor Red
    Write-Host "   Please wait for git clone to complete" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ .NET source found" -ForegroundColor Green

# 2. 设置环境变量
Write-Host ""
Write-Host "2. Setting environment variables..." -ForegroundColor Yellow
$env:ANDROID_NDK_ROOT = $AndroidNdkPath
$env:ANDROID_NDK_HOME = $AndroidNdkPath
Write-Host "   ✓ ANDROID_NDK_ROOT = $AndroidNdkPath" -ForegroundColor Green

# 3. 进入 corehost 目录
Write-Host ""
Write-Host "3. Navigating to corehost directory..." -ForegroundColor Yellow
$CorehostPath = Join-Path $DotnetSourcePath "src\native\corehost"
if (-not (Test-Path $CorehostPath)) {
    Write-Host "❌ corehost directory not found at: $CorehostPath" -ForegroundColor Red
    exit 1
}
Set-Location $CorehostPath
Write-Host "   ✓ Current directory: $CorehostPath" -ForegroundColor Green

# 4. 创建构建目录
Write-Host ""
Write-Host "4. Creating build directory..." -ForegroundColor Yellow
$BuildPath = Join-Path $CorehostPath "build-android-arm64"
if (Test-Path $BuildPath) {
    Write-Host "   Cleaning old build directory..." -ForegroundColor Yellow
    Remove-Item -Path $BuildPath -Recurse -Force
}
New-Item -ItemType Directory -Path $BuildPath | Out-Null
Set-Location $BuildPath
Write-Host "   ✓ Build directory created: $BuildPath" -ForegroundColor Green

# 5. 运行 CMake 配置
Write-Host ""
Write-Host "5. Running CMake configuration..." -ForegroundColor Yellow
Write-Host "   This may take a few minutes..." -ForegroundColor Gray

$CMakeArgs = @(
    ".."
    "-DCMAKE_SYSTEM_NAME=Android"
    "-DCMAKE_SYSTEM_VERSION=21"
    "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a"
    "-DCMAKE_ANDROID_NDK=$AndroidNdkPath"
    "-DCMAKE_ANDROID_STL_TYPE=c++_shared"
    "-DCMAKE_BUILD_TYPE=Release"
    "-DCLI_CMAKE_PLATFORM_ARCH_ARM64=1"
    "-DCORECLR_SET_RPATH=/data/local/tmp"
    "-G"
    "Ninja"
)

try {
    & cmake $CMakeArgs 2>&1 | Tee-Object -Variable cmakeOutput
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ CMake configuration failed!" -ForegroundColor Red
        Write-Host $cmakeOutput -ForegroundColor Red
        exit 1
    }
    Write-Host "   ✓ CMake configuration successful" -ForegroundColor Green
} catch {
    Write-Host "❌ CMake not found or failed to run" -ForegroundColor Red
    Write-Host "   Please install CMake: https://cmake.org/download/" -ForegroundColor Yellow
    exit 1
}

# 6. 编译
Write-Host ""
Write-Host "6. Building libhostpolicy.so..." -ForegroundColor Yellow
Write-Host "   This may take several minutes..." -ForegroundColor Gray

try {
    & ninja hostpolicy 2>&1 | Tee-Object -Variable ninjaOutput
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Build failed!" -ForegroundColor Red
        Write-Host $ninjaOutput -ForegroundColor Red
        exit 1
    }
    Write-Host "   ✓ Build successful" -ForegroundColor Green
} catch {
    Write-Host "❌ Ninja not found or failed to run" -ForegroundColor Red
    Write-Host "   Please install Ninja: https://ninja-build.org/" -ForegroundColor Yellow
    exit 1
}

# 7. 查找并复制生成的 SO
Write-Host ""
Write-Host "7. Locating libhostpolicy.so..." -ForegroundColor Yellow

$HostpolicySo = Get-ChildItem -Path $BuildPath -Recurse -Filter "libhostpolicy.so" -ErrorAction SilentlyContinue | Select-Object -First 1

if ($null -eq $HostpolicySo) {
    Write-Host "❌ libhostpolicy.so not found in build directory!" -ForegroundColor Red
    Write-Host "   Build may have failed silently" -ForegroundColor Red
    exit 1
}

Write-Host "   ✓ Found: $($HostpolicySo.FullName)" -ForegroundColor Green

# 8. 复制到目标位置
Write-Host ""
Write-Host "8. Copying to Android project..." -ForegroundColor Yellow

# 确保输出目录存在
if (-not (Test-Path $OutputPath)) {
    New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
}

# 复制到 jnilibs (用于编译时链接)
Copy-Item -Path $HostpolicySo.FullName -Destination $OutputPath -Force
Write-Host "   ✓ Copied to: $OutputPath" -ForegroundColor Green

# 也复制到 .NET 10 运行时目录 (用于运行时)
$DotnetRuntimePath = Join-Path $OutputPath "..\..\..\assets\dotnet-arm64\shared\Microsoft.NETCore.App\10.0.0-rc.2.25502.107"
if (Test-Path $DotnetRuntimePath) {
    Copy-Item -Path $HostpolicySo.FullName -Destination $DotnetRuntimePath -Force
    Write-Host "   ✓ Copied to: $DotnetRuntimePath" -ForegroundColor Green
} else {
    Write-Host "   ⚠ .NET 10 runtime directory not found: $DotnetRuntimePath" -ForegroundColor Yellow
    Write-Host "   You'll need to manually copy libhostpolicy.so to:" -ForegroundColor Yellow
    Write-Host "   /data/user/0/com.app.ralaunch/files/dotnet-arm64/shared/Microsoft.NETCore.App/10.0.0-rc.2.25502.107/" -ForegroundColor Yellow
}

# 9. 完成
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ Build completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Rebuild your Android project" -ForegroundColor White
Write-Host "2. Deploy the APK to device" -ForegroundColor White
Write-Host "3. Push libhostpolicy.so to device:" -ForegroundColor White
Write-Host "   adb push `"$($HostpolicySo.FullName)`" /sdcard/libhostpolicy.so" -ForegroundColor Gray
Write-Host "   adb shell `"run-as com.app.ralaunch cp /sdcard/libhostpolicy.so /data/user/0/com.app.ralaunch/files/dotnet-arm64/shared/Microsoft.NETCore.App/10.0.0-rc.2.25502.107/`"" -ForegroundColor Gray
Write-Host ""


