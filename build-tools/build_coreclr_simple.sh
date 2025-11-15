#!/bin/bash
# 简化的构建脚本 - 直接在 WSL 中运行

set -e

RUNTIME_PATH="/mnt/d/runtime-10.0.0-rc.2"
NDK_PATH="/home/Android/ndk/android-ndk-r27d"
OUTPUT_PATH="/mnt/d/coreclr_build_output"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔨 构建 libcoreclr.so for Android (Debug, x64)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

cd "$RUNTIME_PATH"

# 设置环境变量
export ANDROID_NDK_ROOT="$NDK_PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/Android/sdk}"

echo "📋 环境变量："
echo "  ANDROID_NDK_ROOT=$ANDROID_NDK_ROOT"
echo "  ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo ""

# 检查 NDK 是否存在
if [ ! -d "$NDK_PATH" ]; then
    echo "❌ 错误：未找到 Android NDK at $NDK_PATH"
    exit 1
fi

echo "✅ NDK 路径正确"
echo ""

# 添加日志（如果需要）
if [ -f "/mnt/d/Rotating-art-Launcher/build-tools/add_coreclr_logs.sh" ]; then
    echo "📝 添加调试日志..."
    chmod +x /mnt/d/Rotating-art-Launcher/build-tools/add_coreclr_logs.sh
    /mnt/d/Rotating-art-Launcher/build-tools/add_coreclr_logs.sh
    echo "✅ 日志添加完成"
    echo ""
fi

echo "🔨 开始构建..."
echo "⚠️  注意：这可能需要 20-40 分钟"
echo ""

# 使用官方构建脚本
./build.sh clr.runtime+clr.alljits+clr.corelib+clr.nativecorelib+clr.tools+clr.packages+libs \
    -os android \
    -arch x64 \
    -c Debug \
    -keepnativesymbols true

echo ""
echo "📦 查找编译结果..."

# 查找编译结果
CORECLR_SO=$(find artifacts/bin/coreclr/android.x64.Debug -name "libcoreclr.so" 2>/dev/null | head -1)

if [ -n "$CORECLR_SO" ]; then
    echo "✅ 找到 libcoreclr.so: $CORECLR_SO"
    mkdir -p "$OUTPUT_PATH"
    cp -v "$CORECLR_SO" "$OUTPUT_PATH/"
    echo "✅ 复制完成！"
    ls -lh "$OUTPUT_PATH/libcoreclr.so"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 构建成功完成！"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo "❌ 未找到 libcoreclr.so"
    echo ""
    echo "🔍 搜索所有可能的位置..."
    find artifacts -name "libcoreclr.so" 2>/dev/null | head -5 || true
    echo ""
    echo "❌ 构建可能失败，请检查上面的错误信息"
    exit 1
fi

