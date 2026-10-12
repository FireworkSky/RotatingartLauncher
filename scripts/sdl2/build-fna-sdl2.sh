#!/data/data/com.termux/files/usr/bin/bash
# 从仓库内 vendored 源码编译「SDL2 版」FNA3D / FAudio（RAL 的 SDL2-Wayland 路径专用）
#
# 为什么必须自己编：
#   - 包内默认的 libFNA3D.so / libFAudio.so 链接 SDL3，在 SDL2 流程里会走
#     SDL_GL_DestroyContext / SDL_GPU（SDL2 无此 API），必然失败；
#   - 2.1.1 的旧二进制是 SDL2 版，但用 -femulated-tls 编译，引用 Android libc 不导出的
#     __emutls_get_address，加载期即失败。
# vendored 源码本身带 BUILD_SDL3=OFF 分支（SDL2 API + OpenGL 驱动），因此直接从源码编。
set -euo pipefail

export NDK=${NDK:-/data/data/com.termux/files/usr/opt/android-ndk}
export API=${API:-28}
export WORK=${WORK:-/data/data/com.termux/files/home/sdl2wayland}
export PREFIX=${PREFIX:-$WORK/prefix}
export RAL=${RAL:-/data/data/com.termux/files/home/RotatingartLauncher}
export LIBS=${LIBS:-$RAL/app/src/main/jniLibs/arm64-v8a}
export BUILDROOT=${BUILDROOT:-/data/data/com.termux/files/home/fna-sdl2-build}
CMAKE=${CMAKE:-cmake}
JOBS=$(nproc)

[ -f "$PREFIX/lib/libSDL2.so" ] || { echo "!! 缺少 $PREFIX/lib/libSDL2.so（先跑 build.sh）"; exit 1; }

common_args=(
  -G Ninja
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
  -DANDROID_ABI=arm64-v8a
  -DANDROID_PLATFORM=android-$API
  -DCMAKE_BUILD_TYPE=Release
  -DBUILD_SDL3=OFF
  -DSDL2_INCLUDE_DIRS="$PREFIX/include/SDL2"
  -DSDL2_LIBRARIES="$PREFIX/lib/libSDL2.so"
  -DCMAKE_PLATFORM_NO_VERSIONED_SONAME=ON
  -DCMAKE_SKIP_RPATH=ON
  -DCMAKE_SKIP_BUILD_RPATH=ON
  -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON
)

# 让 bionic 能按「文件名」解析依赖：包内 SDL2 的文件名是 libSDL2.so
# （SONAME 保留 Linux 惯例 libSDL2-2.0.so.0，供游戏自带库命中）
fixup() {
  local lib=$1 soname=$2
  patchelf --set-soname "$soname" "$lib"
  for old in libSDL2-2.0.so.0 libSDL2-2.0.so; do
    if patchelf --print-needed "$lib" | grep -qx "$old"; then
      patchelf --replace-needed "$old" libSDL2.so "$lib"
    fi
  done
  patchelf --remove-rpath "$lib" 2>/dev/null || true
  echo "  SONAME=$(patchelf --print-soname "$lib")  NEEDED=$(patchelf --print-needed "$lib" | tr '\n' ' ')"
}

echo "=== [1/2] FNA3D (SDL2) ==="
"$CMAKE" -S "$RAL/core/libs/FNA3D" -B "$BUILDROOT/fna3d" "${common_args[@]}" -DBUILD_NATIVE_VULKAN=OFF
"$CMAKE" --build "$BUILDROOT/fna3d" --parallel "$JOBS"
cp -f "$BUILDROOT/fna3d/libFNA3D.so" "$LIBS/libFNA3D_sdl2.so"
fixup "$LIBS/libFNA3D_sdl2.so" libFNA3D.so

echo "=== [2/2] FAudio (SDL2) ==="
"$CMAKE" -S "$RAL/core/libs/FAudio" -B "$BUILDROOT/faudio" "${common_args[@]}"
"$CMAKE" --build "$BUILDROOT/faudio" --parallel "$JOBS"
cp -f "$BUILDROOT/faudio/libFAudio.so" "$LIBS/libFAudio_sdl2.so"
fixup "$LIBS/libFAudio_sdl2.so" libFAudio.so

chmod 644 "$LIBS/libFNA3D_sdl2.so" "$LIBS/libFAudio_sdl2.so"
echo "=== 完成 ==="
ls -l "$LIBS/libFNA3D_sdl2.so" "$LIBS/libFAudio_sdl2.so"
