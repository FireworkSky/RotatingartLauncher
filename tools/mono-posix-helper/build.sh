#!/data/data/com.termux/files/usr/bin/bash
# 交叉编译精简版 libMonoPosixHelper.so（仅 zlib 封装）
#
# mono 的 System.IO.Compression.DeflateStream/GZipStream 通过
# MonoPosixHelper 的 CreateZStream/ReadZStream/WriteZStream/CloseZStream/Flush
# 调 zlib；Android 上 RAL 的 mono 运行时不带这个库，tModLoader 1.3 的
# CalamityMod 读 .csc 结构图时会 DllNotFoundException 并被自动禁用。
#
# Termux 自带的 libMonoPosixHelper.so 依赖 libgssapi_krb5.so.2 / libz.so.1
# （桌面 Linux 命名），在 Android 上加载不了，所以这里用 NDK 重新编一个，
# NEEDED 只有系统 libz.so。
set -euo pipefail
cd "$(dirname "$0")"

NDK=${NDK:-/data/data/com.termux/files/usr/opt/android-ndk}
TC=$NDK/toolchains/llvm/prebuilt/linux-aarch64
OUT=../../app/src/main/jniLibs/arm64-v8a/libMonoPosixHelper.so

$TC/bin/aarch64-linux-android28-clang -shared -fPIC -O2 \
  -Wl,-soname,libMonoPosixHelper.so \
  -o "$OUT" zlib-helper-android.c -lz

echo "built: $(cd "$(dirname "$OUT")" && pwd)/libMonoPosixHelper.so"
$TC/bin/llvm-readelf -h "$OUT" | grep -E 'Class|Machine'
