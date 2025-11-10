# Build hostfxr and hostpolicy for Android from .NET 10 RC 2
# 为 Android 编译 .NET 10 RC 2 的 hostfxr 和 hostpolicy

param(
    [string]$AndroidNdkPath = "D:\Android\ndk\27.2.12479018",
    [string]$DotnetSourcePath = "D:\runtime-10.0.0-rc.2",
    [string]$OutputPath = "D:\Rotating-art-Launcher\app\src\main\jnilibs\arm64-v8a"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building .NET 10 hostfxr for Android" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查路径是否存在
Write-Host "1. Checking paths..." -ForegroundColor Yellow

if (-not (Test-Path $AndroidNdkPath)) {
    Write-Host "❌ Android NDK not found at: $AndroidNdkPath" -ForegroundColor Red
    Write-Host "   Please install Android NDK 27.x" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ Android NDK found: $AndroidNdkPath" -ForegroundColor Green

if (-not (Test-Path $DotnetSourcePath)) {
    Write-Host "❌ .NET source not found at: $DotnetSourcePath" -ForegroundColor Red
    Write-Host "   Please wait for git clone to complete or run:" -ForegroundColor Red
    Write-Host "   git clone --depth 1 --branch v10.0.0-rc.2.25502.107 https://github.com/dotnet/runtime.git $DotnetSourcePath" -ForegroundColor Yellow
    exit 1
}
Write-Host "   ✓ .NET source found: $DotnetSourcePath" -ForegroundColor Green

# 2. 检查必要工具
Write-Host ""
Write-Host "2. Checking required tools..." -ForegroundColor Yellow

$cmakeVersion = & cmake --version 2>&1 | Select-String "version" | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ CMake not found!" -ForegroundColor Red
    Write-Host "   Please install CMake: https://cmake.org/download/" -ForegroundColor Yellow
    exit 1
}
Write-Host "   ✓ CMake found: $($cmakeVersion.Trim())" -ForegroundColor Green

$ninjaVersion = & ninja --version 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Ninja not found!" -ForegroundColor Red
    Write-Host "   Please install Ninja: https://ninja-build.org/" -ForegroundColor Yellow
    Write-Host "   Or install via chocolatey: choco install ninja" -ForegroundColor Yellow
    exit 1
}
Write-Host "   ✓ Ninja found: version $($ninjaVersion.Trim())" -ForegroundColor Green

# 3. 设置环境变量
Write-Host ""
Write-Host "3. Setting environment variables..." -ForegroundColor Yellow
$env:ANDROID_NDK_ROOT = $AndroidNdkPath
$env:ANDROID_NDK_HOME = $AndroidNdkPath
$env:ANDROID_NDK = $AndroidNdkPath
Write-Host "   ✓ ANDROID_NDK_ROOT = $AndroidNdkPath" -ForegroundColor Green

# 4. 进入 corehost 目录
Write-Host ""
Write-Host "4. Navigating to corehost directory..." -ForegroundColor Yellow
$CorehostPath = Join-Path $DotnetSourcePath "src\native\corehost"
if (-not (Test-Path $CorehostPath)) {
    Write-Host "❌ corehost directory not found at: $CorehostPath" -ForegroundColor Red
    exit 1
}
Set-Location $CorehostPath
Write-Host "   ✓ Current directory: $CorehostPath" -ForegroundColor Green

# 5. 创建构建目录
Write-Host ""
Write-Host "5. Creating build directory..." -ForegroundColor Yellow
$BuildPath = Join-Path $CorehostPath "build-android-arm64"
if (Test-Path $BuildPath) {
    Write-Host "   Cleaning old build directory..." -ForegroundColor Yellow
    Remove-Item -Path $BuildPath -Recurse -Force
}
New-Item -ItemType Directory -Path $BuildPath | Out-Null
Set-Location $BuildPath
Write-Host "   ✓ Build directory created: $BuildPath" -ForegroundColor Green

# 6. 运行 CMake 配置
Write-Host ""
Write-Host "6. Running CMake configuration..." -ForegroundColor Yellow
Write-Host "   This may take a few minutes..." -ForegroundColor Gray
Write-Host ""

# CMake 参数（参考 .NET 官方 Android 构建）
# 使用正斜杠避免转义问题
$AndroidNdkPathUnix = $AndroidNdkPath.Replace("\", "/")
$NinjaPath = (Get-Command ninja -ErrorAction SilentlyContinue).Source
if (-not $NinjaPath) {
    $NinjaPath = "D:/Android/cmake/3.22.1/bin/ninja.exe".Replace("\", "/")
}

$CMakeArgs = @(
    ".."
    "-DCMAKE_SYSTEM_NAME=Android"
    "-DCMAKE_SYSTEM_VERSION=21"  # Android API 21 (最低版本)
    "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a"
    "-DCMAKE_ANDROID_NDK=$AndroidNdkPathUnix"
    "-DCMAKE_ANDROID_STL_TYPE=c++_shared"
    "-DCMAKE_BUILD_TYPE=Release"
    "-DCMAKE_MAKE_PROGRAM=$NinjaPath"
    "-DCLI_CMAKE_PLATFORM_ARCH_ARM64=1"
    "-DCLI_CMAKE_PLATFORM_ANDROID=1"
    "-DCLI_CMAKE_HOST_POLICY_VER=10.0.0"
    "-DCLI_CMAKE_HOST_FXR_VER=10.0.0"
    "-DCLI_CMAKE_PKG_RID=android-arm64"
    "-DCLI_CMAKE_FALLBACK_OS=linux"
    "-DCLI_CMAKE_COMMIT_HASH=25502.107"
    "-DCLI_CMAKE_RESOURCE_DIR=$DotnetSourcePath/artifacts"
    "-G"
    "Ninja"
)

Write-Host "CMake arguments:" -ForegroundColor Gray
$CMakeArgs | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
Write-Host ""

try {
    & cmake $CMakeArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ CMake configuration failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
    Write-Host "   ✓ CMake configuration successful" -ForegroundColor Green
} catch {
    Write-Host "❌ CMake failed to run: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 7. 编译 hostfxr
Write-Host ""
Write-Host "7. Building hostfxr..." -ForegroundColor Yellow
Write-Host "   This may take several minutes..." -ForegroundColor Gray
Write-Host ""

try {
    & ninja hostfxr
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ hostfxr build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
    Write-Host "   ✓ hostfxr build successful" -ForegroundColor Green
} catch {
    Write-Host "❌ Ninja failed to run: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 8. 编译 hostpolicy（也需要这个）
Write-Host ""
Write-Host "8. Building hostpolicy..." -ForegroundColor Yellow
Write-Host "   This may take a few minutes..." -ForegroundColor Gray
Write-Host ""

try {
    & ninja hostpolicy
    if ($LASTEXITCODE -ne 0) {
        Write-Host "⚠  hostpolicy build failed (may be optional)" -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "   ✓ hostpolicy build successful" -ForegroundColor Green
    }
} catch {
    Write-Host "⚠  hostpolicy build skipped: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 9. 查找生成的 SO 文件
Write-Host ""
Write-Host "9. Locating built libraries..." -ForegroundColor Yellow

$BuiltFiles = @()

# 查找 libhostfxr.so
$HostfxrSo = Get-ChildItem -Path $BuildPath -Recurse -Filter "libhostfxr.so" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($HostfxrSo) {
    Write-Host "   ✓ Found libhostfxr.so: $($HostfxrSo.FullName)" -ForegroundColor Green
    $BuiltFiles += $HostfxrSo
} else {
    Write-Host "   ⚠ libhostfxr.so not found!" -ForegroundColor Yellow
}

# 查找 libhostpolicy.so
$HostpolicySo = Get-ChildItem -Path $BuildPath -Recurse -Filter "libhostpolicy.so" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($HostpolicySo) {
    Write-Host "   ✓ Found libhostpolicy.so: $($HostpolicySo.FullName)" -ForegroundColor Green
    $BuiltFiles += $HostpolicySo
} else {
    Write-Host "   ⚠ libhostpolicy.so not found!" -ForegroundColor Yellow
}

if ($BuiltFiles.Count -eq 0) {
    Write-Host ""
    Write-Host "❌ No libraries were built!" -ForegroundColor Red
    Write-Host "   Build may have failed silently" -ForegroundColor Red
    exit 1
}

# 10. 复制到目标位置
Write-Host ""
Write-Host "10. Copying libraries to project..." -ForegroundColor Yellow

# 确保输出目录存在
if (-not (Test-Path $OutputPath)) {
    New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
}

foreach ($file in $BuiltFiles) {
    # 复制到 jnilibs
    $destPath = Join-Path $OutputPath $file.Name
    Copy-Item -Path $file.FullName -Destination $destPath -Force
    Write-Host "   ✓ Copied $($file.Name) to: $destPath" -ForegroundColor Green
    
    # 显示文件大小
    $sizeKB = [math]::Round($file.Length / 1KB, 2)
    Write-Host "     Size: $sizeKB KB" -ForegroundColor Gray
}

# 11. 完成
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ Build completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Built libraries:" -ForegroundColor Yellow
foreach ($file in $BuiltFiles) {
    Write-Host "  • $($file.Name)" -ForegroundColor White
}
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Push libraries to device:" -ForegroundColor White
foreach ($file in $BuiltFiles) {
    $destPath = Join-Path $OutputPath $file.Name
    Write-Host "   adb push `"$destPath`" /sdcard/$($file.Name)" -ForegroundColor Gray
    Write-Host "   adb shell `"run-as com.app.ralaunch cp /sdcard/$($file.Name) /data/user/0/com.app.ralaunch/files/dotnet-arm64/shared/Microsoft.NETCore.App/10.0.0-rc.2.25502.107/$($file.Name)`"" -ForegroundColor Gray
}
Write-Host ""
Write-Host "2. Or rebuild and deploy the Android app" -ForegroundColor White
Write-Host ""

