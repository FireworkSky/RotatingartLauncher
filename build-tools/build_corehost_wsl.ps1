# Build .NET 10 RC 2 corehost in WSL for Android
# 在 WSL 中为 Android 编译 .NET 10 RC 2 corehost

param(
    [string]$DotnetSourcePath = "D:\runtime-10.0.0-rc.2",
    [string]$AndroidNdkVersion = "27.0.12077973",
    [string]$OutputPath = "D:\Rotating-art-Launcher\app\src\main\jniLibs\arm64-v8a"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building .NET 10 corehost in WSL" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查 WSL
Write-Host "1. Checking WSL..." -ForegroundColor Yellow
$wslList = wsl --list --quiet
if ($LASTEXITCODE -ne 0 -or $wslList.Count -eq 0) {
    Write-Host "❌ WSL not installed!" -ForegroundColor Red
    Write-Host "   Please install WSL: wsl --install" -ForegroundColor Yellow
    exit 1
}
Write-Host "   ✓ WSL is installed" -ForegroundColor Green

# 2. 启动 WSL 并检查 Ubuntu
Write-Host ""
Write-Host "2. Starting WSL..." -ForegroundColor Yellow
wsl --distribution Ubuntu-20.04 echo "WSL is ready"
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to start Ubuntu-20.04" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ Ubuntu 20.04 is ready" -ForegroundColor Green

# 3. 使用 WSL 中已有的源码目录
Write-Host ""
Write-Host "3. Locating source code in WSL..." -ForegroundColor Yellow
$wslRuntimePath = "/runtime-10.0.0-rc.2"

# 检查源码是否存在
$checkSource = wsl --distribution Ubuntu-20.04 bash -c "test -d '$wslRuntimePath' && echo 'exists' || echo 'not found'"
if ($checkSource -notmatch "exists") {
    Write-Host "❌ Source code not found at $wslRuntimePath" -ForegroundColor Red
    Write-Host "   Please ensure source is at \\wsl.localhost\Ubuntu-20.04\runtime-10.0.0-rc.2" -ForegroundColor Yellow
    exit 1
}
Write-Host "   ✓ Source code found: $wslRuntimePath" -ForegroundColor Green

# 5. 检查 WSL 中的 Android NDK
Write-Host ""
Write-Host "5. Checking Android NDK in WSL..." -ForegroundColor Yellow

# 创建临时脚本文件
$tempScript = "/tmp/check_ndk_$((Get-Random)).sh"
$ndkCheckScript = @'
#!/bin/bash
set -e

NDK_BASE_DIR=/home/Android/ndk

echo "Checking for NDK at $NDK_BASE_DIR..."
if [ ! -d "$NDK_BASE_DIR" ]; then
    echo "Error: NDK directory not found at $NDK_BASE_DIR"
    exit 1
fi

# 查找所有 NDK 版本
echo "Available NDK versions:"
ls -1 $NDK_BASE_DIR

# 查找 NDK 27.x (最新版)
NDK_27=$(ls -1 $NDK_BASE_DIR | grep '^27\.' | sort -V | tail -1)
if [ -z "$NDK_27" ]; then
    # 如果没有 27.x，尝试使用 26.x
    NDK_27=$(ls -1 $NDK_BASE_DIR | grep '^26\.' | sort -V | tail -1)
fi

if [ -z "$NDK_27" ]; then
    echo "Error: No suitable NDK found (need 26.x or 27.x)"
    exit 1
fi

NDK_PATH="$NDK_BASE_DIR/$NDK_27"
echo "Using NDK: $NDK_PATH"
echo "$NDK_PATH"
'@

# 写入脚本到 WSL
$ndkCheckScript | wsl --distribution Ubuntu-20.04 bash -c "cat > $tempScript && chmod +x $tempScript"

# 执行脚本
$ndkPath = wsl --distribution Ubuntu-20.04 bash -c "$tempScript"
wsl --distribution Ubuntu-20.04 bash -c "rm -f $tempScript"

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Failed to find NDK" -ForegroundColor Red
    Write-Host $ndkPath -ForegroundColor Red
    exit 1
}

# 获取最后一行（NDK 路径）
$wslNdkPath = ($ndkPath -split "`n" | Where-Object { $_ -match "^/home/Android/ndk" })[-1].Trim()
Write-Host "   ✓ Android NDK found: $wslNdkPath" -ForegroundColor Green

# 6. 安装构建依赖
Write-Host ""
Write-Host "6. Installing build dependencies..." -ForegroundColor Yellow

$installDepsScript = @"
#!/bin/bash
set -e

echo "Updating package lists..."
sudo apt-get update -qq

echo "Installing dependencies..."
sudo apt-get install -y -qq \
    build-essential \
    cmake \
    clang \
    llvm \
    python3 \
    python3-pip \
    libicu-dev \
    liblttng-ust-dev \
    libssl-dev \
    libkrb5-dev \
    zlib1g-dev \
    ninja-build \
    wget \
    unzip

echo "✓ All dependencies installed"
"@

$installDepsScript | wsl --distribution Ubuntu-20.04 bash
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠  Some dependencies may not be installed, continuing..." -ForegroundColor Yellow
}
Write-Host "   ✓ Build dependencies ready" -ForegroundColor Green

# 7. 生成版本文件
Write-Host ""
Write-Host "7. Generating version files..." -ForegroundColor Yellow

$genVersionScript = @"
#!/bin/bash
set -e

cd $wslRuntimePath

# 创建 artifacts 目录
mkdir -p artifacts/obj

# 生成 _version.c 文件
cat > artifacts/obj/_version.c << 'EOF'
#define QUOTE(s) #s
#define EXPAND_AND_QUOTE(s) QUOTE(s)

char  sccsid[] = "@(#)Version " EXPAND_AND_QUOTE(VERSION_FILE_VS_VERSION) " @Commit: " EXPAND_AND_QUOTE(VERSION_COMMIT_HASH);
EOF

echo "✓ Version file generated"
"@

$genVersionScript | wsl --distribution Ubuntu-20.04 bash
Write-Host "   ✓ Version files generated" -ForegroundColor Green

# 8. 编译 hostfxr 和 hostpolicy
Write-Host ""
Write-Host "8. Building hostfxr and hostpolicy..." -ForegroundColor Yellow
Write-Host "   This will take 10-20 minutes..." -ForegroundColor Gray
Write-Host ""

$buildScript = @"
#!/bin/bash
set -e

cd $wslRuntimePath/src/native/corehost

export ANDROID_NDK_ROOT=$wslNdkPath
export PATH=\$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:\$PATH

echo "Using NDK: \$ANDROID_NDK_ROOT"

# 清理旧构建
rm -rf build-android-arm64
mkdir -p build-android-arm64
cd build-android-arm64

# 配置 CMake
echo "Configuring CMake..."
cmake .. \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=21 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=\$ANDROID_NDK_ROOT \
    -DCMAKE_ANDROID_STL_TYPE=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    -DCLI_CMAKE_PKG_RID=android-arm64 \
    -DCLI_CMAKE_FALLBACK_OS=linux \
    -DCLI_CMAKE_COMMIT_HASH=25502.107 \
    -GNinja

# 编译 hostfxr
echo ""
echo "Building hostfxr..."
ninja hostfxr

# 编译 hostpolicy
echo ""
echo "Building hostpolicy..."
ninja hostpolicy

echo ""
echo "✓ Build complete!"

# 查找生成的文件
echo ""
echo "Built libraries:"
find . -name "libhostfxr.so" -o -name "libhostpolicy.so" | while read file; do
    echo "  \$(basename \$file): \$(du -h \$file | cut -f1)"
done
"@

$buildScript | wsl --distribution Ubuntu-20.04 bash 2>&1 | ForEach-Object {
    if ($_ -match "error" -or $_ -match "Error") {
        Write-Host $_ -ForegroundColor Red
    } elseif ($_ -match "warning" -or $_ -match "Warning") {
        Write-Host $_ -ForegroundColor Yellow
    } elseif ($_ -match "Building|Configuring|✓") {
        Write-Host $_ -ForegroundColor Cyan
    } else {
        Write-Host $_ -ForegroundColor Gray
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "   ✓ Build successful!" -ForegroundColor Green

# 9. 复制编译产物到 Windows
Write-Host ""
Write-Host "9. Copying libraries to Windows..." -ForegroundColor Yellow

# 查找并复制 libhostfxr.so
$findHostfxr = "find $wslRuntimePath/src/native/corehost/build-android-arm64 -name 'libhostfxr.so' | head -1"
$hostfxrPath = wsl --distribution Ubuntu-20.04 bash -c $findHostfxr

if ($hostfxrPath) {
    $windowsHostfxrPath = wsl wslpath -w $hostfxrPath
    if (-not (Test-Path $OutputPath)) {
        New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
    }
    Copy-Item -Path $windowsHostfxrPath -Destination "$OutputPath\libhostfxr.so" -Force
    $size = [math]::Round((Get-Item "$OutputPath\libhostfxr.so").Length / 1KB, 2)
    Write-Host "   ✓ Copied libhostfxr.so ($size KB)" -ForegroundColor Green
}

# 查找并复制 libhostpolicy.so
$findHostpolicy = "find $wslRuntimePath/src/native/corehost/build-android-arm64 -name 'libhostpolicy.so' | head -1"
$hostpolicyPath = wsl --distribution Ubuntu-20.04 bash -c $findHostpolicy

if ($hostpolicyPath) {
    $windowsHostpolicyPath = wsl wslpath -w $hostpolicyPath
    Copy-Item -Path $windowsHostpolicyPath -Destination "$OutputPath\libhostpolicy.so" -Force
    $size = [math]::Round((Get-Item "$OutputPath\libhostpolicy.so").Length / 1KB, 2)
    Write-Host "   ✓ Copied libhostpolicy.so ($size KB)" -ForegroundColor Green
}

# 10. 完成
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ Build completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Built libraries saved to:" -ForegroundColor Yellow
Write-Host "  $OutputPath\" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Rebuild Android project" -ForegroundColor White
Write-Host "2. Deploy to device" -ForegroundColor White
Write-Host "3. Or manually push to device:" -ForegroundColor White
Write-Host "   adb push `"$OutputPath\libhostpolicy.so`" /sdcard/" -ForegroundColor Gray
Write-Host "   adb shell `"run-as com.app.ralaunch cp /sdcard/libhostpolicy.so /data/user/0/com.app.ralaunch/files/dotnet-arm64/shared/Microsoft.NETCore.App/10.0.0-rc.2.25502.107/`"" -ForegroundColor Gray
Write-Host ""

# 可选：清理构建目录
$cleanup = Read-Host "Clean up build directory? (y/N)"
if ($cleanup -eq 'y' -or $cleanup -eq 'Y') {
    wsl --distribution Ubuntu-20.04 bash -c "rm -rf $wslRuntimePath/src/native/corehost/build-android-arm64"
    Write-Host "✓ Build directory cleaned" -ForegroundColor Green
}

