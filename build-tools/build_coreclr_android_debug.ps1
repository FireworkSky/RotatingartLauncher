# 使用官方方法编译 libcoreclr.so for Android (Debug, x64)

Write-Host "🔨 开始编译 libcoreclr.so for Android (Debug, x64)..." -ForegroundColor Cyan

# WSL 路径
$WSL_RUNTIME_PATH = "/mnt/d/runtime-10.0.0-rc.2"
$WSL_NDK_PATH = "/home/Android/ndk/android-ndk-r27d"
$OUTPUT_DIR = "D:\coreclr_build_output"

# 创建输出目录
if (!(Test-Path $OUTPUT_DIR)) {
    New-Item -ItemType Directory -Path $OUTPUT_DIR | Out-Null
}

Write-Host "📝 步骤 1: 添加调试日志..." -ForegroundColor Yellow
wsl bash -c "chmod +x /mnt/d/Rotating-art-Launcher/build-tools/add_coreclr_logs.sh && /mnt/d/Rotating-art-Launcher/build-tools/add_coreclr_logs.sh"

Write-Host "🔧 步骤 2: 使用官方构建脚本编译..." -ForegroundColor Yellow

# 创建编译脚本（使用官方方法）
$buildScript = @'
#!/bin/bash
set -e

RUNTIME_PATH="/mnt/d/runtime-10.0.0-rc.2"
NDK_PATH="/home/Android/ndk/android-ndk-r27d"
OUTPUT_PATH="/mnt/d/coreclr_build_output"

cd "$RUNTIME_PATH"

# 设置环境变量（根据官方文档）
export ANDROID_NDK_ROOT="$NDK_PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/Android/sdk}"

echo "📋 环境变量："
echo "  ANDROID_NDK_ROOT=$ANDROID_NDK_ROOT"
echo "  ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"

echo "🔨 使用官方构建脚本编译 CoreCLR for Android (Debug, x64)..."

# 根据官方文档：./build.sh clr.runtime+clr.alljits+clr.corelib+clr.nativecorelib+clr.tools+clr.packages+libs -os android -arch <x64|arm64> -c <Debug|Release>
# 用户要求：Debug 配置，x64 架构
./build.sh clr.runtime+clr.alljits+clr.corelib+clr.nativecorelib+clr.tools+clr.packages+libs \
    -os android \
    -arch x64 \
    -c Debug \
    -keepnativesymbols true

echo "📦 查找编译结果..."

# 根据官方文档，输出在 artifacts/bin/coreclr/android.x64.Debug
CORECLR_SO=$(find artifacts/bin/coreclr/android.x64.Debug -name "libcoreclr.so" 2>/dev/null | head -1)

if [ -n "$CORECLR_SO" ]; then
    echo "✅ 找到 libcoreclr.so: $CORECLR_SO"
    mkdir -p "$OUTPUT_PATH"
    cp -v "$CORECLR_SO" "$OUTPUT_PATH/"
    echo "✅ 复制完成！"
    ls -lh "$OUTPUT_PATH/libcoreclr.so"
else
    echo "❌ 未找到 libcoreclr.so，尝试搜索所有位置..."
    find artifacts -name "libcoreclr.so" 2>/dev/null | head -5 || true
    echo ""
    echo "💡 提示：检查构建日志 artifacts/log/"
fi

echo "✅ 构建完成！"
'@

# 将脚本写入 WSL 临时文件（修复行结束符）
$tempScript = "/tmp/build_coreclr_android_$(Get-Random).sh"
$buildScript -replace "`r`n", "`n" | wsl bash -c "cat > $tempScript && chmod +x $tempScript"

# 执行编译脚本
Write-Host ""
Write-Host "⚠️  注意：这可能需要很长时间（10-30分钟）..." -ForegroundColor Yellow
Write-Host ""

wsl bash $tempScript

Write-Host ""
Write-Host "✅ 构建完成！" -ForegroundColor Green
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
        
        # 推送文件（注意：x64 版本应该推送到 x64 目录）
        $targetPath = "/data/data/com.app.ralaunch/files/dotnet-x64/shared/Microsoft.NETCore.App/10.0.0-rc.2.25502.107/libcoreclr.so"
        Write-Host "目标路径: $targetPath" -ForegroundColor Cyan
        
        # 确保目录存在
        adb shell "mkdir -p $(dirname $targetPath)"
        
        # 推送文件
        adb push "$OUTPUT_DIR\libcoreclr.so" $targetPath
        
        Write-Host "✅ 推送完成！" -ForegroundColor Green
        Write-Host ""
        Write-Host "🚀 现在可以启动应用并查看日志：" -ForegroundColor Cyan
        Write-Host "   adb logcat | Select-String 'CORECLR'" -ForegroundColor White
    }
} else {
    Write-Host "❌ 编译失败，未找到 libcoreclr.so" -ForegroundColor Red
    Write-Host "💡 提示：检查 WSL 中的构建日志" -ForegroundColor Yellow
}

