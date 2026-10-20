#!/data/data/com.termux/files/usr/bin/bash
# 编译 tModLoader 1.3 包装启动器（mono 可运行的 net40 程序集）
# 依赖：mcs（mono-mcs）+ libs/ 下的 MonoMod 19（取自 Android 可用的 tModLoader 1.3 移植版）
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p libs
for f in MonoMod.RuntimeDetour.dll MonoMod.Utils.dll Mono.Cecil.dll; do
  [ -f "libs/$f" ] || cp "../../app/src/main/assets/prebuilt/tml13/$f" libs/
done
mcs -nologo -optimize+ -target:exe \
  -r:libs/MonoMod.RuntimeDetour.dll -r:libs/MonoMod.Utils.dll -r:libs/Mono.Cecil.dll \
  -out:TML13Launcher.exe Program.cs GlCompat.cs
cp TML13Launcher.exe ../../app/src/main/assets/prebuilt/tml13/TML13Launcher.exe
echo "built: $(pwd)/TML13Launcher.exe"
