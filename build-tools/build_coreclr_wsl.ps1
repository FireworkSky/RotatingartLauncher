# 在 WSL 中编译 libcoreclr.so（仅编译 coreclr 组件）

Write-Host "🔨 开始编译 libcoreclr.so..." -ForegroundColor Cyan

# WSL 路径
$WSL_RUNTIME_PATH = "/mnt/d/runtime-10.0.0-rc.2"
$WSL_NDK_PATH = "/home/Android/ndk"
$OUTPUT_DIR = "D:\coreclr_build_output"

# 创建输出目录
if (!(Test-Path $OUTPUT_DIR)) {
    New-Item -ItemType Directory -Path $OUTPUT_DIR | Out-Null
}

Write-Host "📝 步骤 1: 添加调试日志..." -ForegroundColor Yellow
wsl bash -c "chmod +x /mnt/d/Rotating-art-Launcher/build-tools/add_coreclr_logs.sh && /mnt/d/Rotating-art-Launcher/build-tools/add_coreclr_logs.sh"

Write-Host "🔧 步骤 2: 编译 libcoreclr.so..." -ForegroundColor Yellow

# 创建编译脚本
$buildScript = @'
#!/bin/bash
set -e

RUNTIME_PATH="/mnt/d/runtime-10.0.0-rc.2"
NDK_PATH="/home/Android/ndk/android-ndk-r27d"
OUTPUT_PATH="/mnt/d/coreclr_build_output"

cd "$RUNTIME_PATH"

echo "📦 清理之前的构建..."
rm -rf artifacts/bin/coreclr/linux.arm64.Release
rm -rf artifacts/obj/coreclr/linux.arm64.Release

echo "🔨 配置环境变量..."

# 设置环境变量
export __BuildArch=arm64
export __BuildOS=linux  
export __HostArch=x64
export __TargetOS=linux
export __TargetArch=arm64
export ANDROID_NDK_ROOT="$NDK_PATH"
export TOOLCHAIN_DIR="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN_DIR/bin:$PATH"
export CC="$TOOLCHAIN_DIR/bin/aarch64-linux-android21-clang"
export CXX="$TOOLCHAIN_DIR/bin/aarch64-linux-android21-clang++"
export AR="$TOOLCHAIN_DIR/bin/llvm-ar"
export AS="$TOOLCHAIN_DIR/bin/llvm-as"
export RANLIB="$TOOLCHAIN_DIR/bin/llvm-ranlib"
export LD="$TOOLCHAIN_DIR/bin/ld.lld"
export STRIP="$TOOLCHAIN_DIR/bin/llvm-strip"
export NM="$TOOLCHAIN_DIR/bin/llvm-nm"
export OBJDUMP="$TOOLCHAIN_DIR/bin/llvm-objdump"

echo "🔨 创建构建目录..."
mkdir -p artifacts/obj/coreclr/linux.arm64.Release
cd artifacts/obj/coreclr/linux.arm64.Release

echo "🔧 运行 CMake..."

cmake "$RUNTIME_PATH/src/coreclr" \
    -DCMAKE_SYSTEM_NAME=Linux \
    -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER="$CC" \
    -DCMAKE_CXX_COMPILER="$CXX" \
    -DCMAKE_AR="$AR" \
    -DCMAKE_RANLIB="$RANLIB" \
    -DCMAKE_SYSROOT="$TOOLCHAIN_DIR/sysroot" \
    -DCMAKE_C_FLAGS="-fPIC -DPAL_STDCPP_COMPAT -DHOST_ANDROID=1 -D__ANDROID__=1 -target aarch64-linux-android21" \
    -DCMAKE_CXX_FLAGS="-fPIC -DPAL_STDCPP_COMPAT -DHOST_ANDROID=1 -D__ANDROID__=1 -target aarch64-linux-android21" \
    -DCLR_CMAKE_TARGET_ARCH=arm64 \
    -DCLR_CMAKE_TARGET_OS=linux \
    -DCLR_CMAKE_HOST_ARCH=x64 \
    -DCLR_CMAKE_BUILD_ARCH=arm64 \
    -DCLR_CMAKE_TARGET_LINUX=1 \
    -DCLR_CMAKE_TARGET_ANDROID=1 \
    -DCLR_CMAKE_TARGET_UNIX=1 \
    -DFEATURE_DISTRO_AGNOSTIC_SSL=1 \
    -DFEATURE_GDBJIT=0 \
    -DFEATURE_PERFTRACING=0 \
    -DCMAKE_INSTALL_PREFIX="$RUNTIME_PATH/artifacts/bin/coreclr/linux.arm64.Release"

echo "🔨 开始编译 libcoreclr.so..."
make -j$(nproc)

# 复制编译结果
echo "📦 复制编译结果..."
cd "$RUNTIME_PATH"
mkdir -p "$OUTPUT_PATH"

# 查找编译结果
CORECLR_SO=$(find artifacts/obj/coreclr/linux.arm64.Release -name "libcoreclr.so" 2>/dev/null | head -1)
if [ -n "$CORECLR_SO" ]; then
    cp -v "$CORECLR_SO" "$OUTPUT_PATH/"
    echo "✅ 找到并复制了 libcoreclr.so"
else
    echo "❌ 未找到 libcoreclr.so，尝试搜索..."
    find artifacts -name "libcoreclr.so" 2>/dev/null || true
fi

echo "✅ libcoreclr.so 编译完成！"
ls -lh "$OUTPUT_PATH/libcoreclr.so"
'@

# 将脚本写入 WSL 临时文件（修复行结束符）
$tempScript = "/tmp/build_coreclr_$(Get-Random).sh"
$buildScript -replace "`r`n", "`n" | wsl bash -c "cat > $tempScript && chmod +x $tempScript"

# 执行编译脚本
wsl bash $tempScript

Write-Host ""
Write-Host "✅ libcoreclr.so 编译完成！" -ForegroundColor Green
Write-Host "输出目录: $OUTPUT_DIR" -ForegroundColor Cyan

# 检查文件
if (Test-Path "$OUTPUT_DIR\libcoreclr.so") {
    $fileSize = (Get-Item "$OUTPUT_DIR\libcoreclr.so").Length / 1MB
    Write-Host "文件大小: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Green
    
    # 询问是否推送到设备
    Write-Host ""
    $push = Read-Host "是否推送到设备? (y/n)"
    if ($push -eq 'y') {
        Write-Host "📱 推送到设备..." -ForegroundColor Yellow
        
        # 停止应用
        adb shell am force-stop com.app.ralaunch
        
        # 推送文件
        adb push "$OUTPUT_DIR\libcoreclr.so" "/data/data/com.app.ralaunch/files/dotnet-arm64/shared/Microsoft.NETCore.App/10.0.0-rc.2.25502.107/libcoreclr.so"
        
        Write-Host "✅ 推送完成！" -ForegroundColor Green
        Write-Host ""
        Write-Host "🚀 现在可以启动应用并查看日志：" -ForegroundColor Cyan
        Write-Host "   adb logcat | Select-String 'CORECLR'" -ForegroundColor White
    }
} else {
    Write-Host "❌ 编译失败，未找到 libcoreclr.so" -ForegroundColor Red
}

