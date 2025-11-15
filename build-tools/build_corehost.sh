#!/bin/bash
set -e

# .NET 10 RC 2 corehost build script for Android
# Usage: ./build_corehost.sh <ndk_path>

RUNTIME_PATH="/runtime-10.0.0-rc.2"
NDK_PATH="$1"

if [ -z "$NDK_PATH" ]; then
    echo "Error: NDK path not provided"
    echo "Usage: $0 <ndk_path>"
    exit 1
fi

echo "=========================================="
echo "Building .NET 10 corehost for Android"
echo "=========================================="
echo ""
echo "Runtime: $RUNTIME_PATH"
echo "NDK: $NDK_PATH"
echo ""

# 1. Install dependencies
echo "1. Installing dependencies..."
sudo apt-get update -qq
sudo apt-get install -y -qq \
    build-essential \
    cmake \
    clang \
    ninja-build \
    python3 \
    libssl-dev \
    zlib1g-dev
echo "✓ Dependencies installed"
echo ""

# 2. Generate version file
echo "2. Generating version file..."
cd $RUNTIME_PATH
mkdir -p artifacts/obj
cat > artifacts/obj/_version.c << 'EOF'
#define QUOTE(s) #s
#define EXPAND_AND_QUOTE(s) QUOTE(s)

char sccsid[] = "@(#)Version 10.0.0";
EOF
echo "✓ Version file generated"
echo ""

# 3. Configure CMake
echo "3. Configuring CMake..."
cd $RUNTIME_PATH/src/native/corehost

export ANDROID_NDK_ROOT="$NDK_PATH"
export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"

rm -rf build-android-arm64
mkdir -p build-android-arm64
cd build-android-arm64

cmake .. \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=21 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK="$ANDROID_NDK_ROOT" \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS="-fPIC -DPAL_STDCPP_COMPAT" \
    -DCMAKE_CXX_FLAGS="-fPIC -DPAL_STDCPP_COMPAT" \
    -DCLI_CMAKE_PLATFORM_ARCH_ARM64=1 \
    -DCLI_CMAKE_PLATFORM_ANDROID=1 \
    -DCLI_CMAKE_HOST_POLICY_VER=10.0.0 \
    -DCLI_CMAKE_HOST_FXR_VER=10.0.0 \
    -DCLI_CMAKE_PKG_RID=android-arm64 \
    -DCLI_CMAKE_FALLBACK_OS=linux \
    -DCLI_CMAKE_COMMIT_HASH=25502.107 \
    -DCLI_CMAKE_RESOURCE_DIR="$RUNTIME_PATH/artifacts" \
    -DCLI_CMAKE_CORECLR_ARTIFACTS="$RUNTIME_PATH/artifacts" \
    -DCLI_CMAKE_NATIVE_VER=10.0.0 \
    -DSKIP_VERSIONING=1 \
    -DCLR_CMAKE_TARGET_LINUX=1 \
    -DCLR_CMAKE_TARGET_ANDROID=1 \
    -DCLR_CMAKE_TARGET_OS=android \
    -G Ninja

echo "✓ CMake configured"
echo ""

# 4. Build hostfxr
echo "4. Building hostfxr..."
ninja hostfxr
echo "✓ hostfxr built"
echo ""

# 5. Build hostpolicy  
echo "5. Building hostpolicy..."
ninja hostpolicy
echo "✓ hostpolicy built"
echo ""

# 6. List built libraries
echo "=========================================="
echo "✓ Build completed successfully!"
echo "=========================================="
echo ""
echo "Built libraries:"
find . -name "libhostfxr.so" -o -name "libhostpolicy.so" | while read file; do
    size=$(du -h "$file" | cut -f1)
    echo "  $(basename "$file"): $size"
done
echo ""

