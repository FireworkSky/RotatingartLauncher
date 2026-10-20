#!/data/data/com.termux/files/usr/bin/bash
# 编译 tModLoader 1.3 包装启动器（mono 可运行的 net40 程序集）
#
# 这个目录和 patches/ 一样：只放源码给别人看/复现，Gradle 构建不会编译它。
# 产物 TML13Launcher.exe 由安装器从 app/src/main/assets/prebuilt/tml13/ 拷进游戏目录。
#
# 依赖：mcs（mono-mcs）+ libs/ 下的 MonoMod 19 / Cecil（LFS 管理，取自
# Android 可用的 tModLoader 1.3 移植版；不要换成 RAL assets 里的 MonoMod 25，
# 那份是 net8 的，mono 4.5 profile 加载不了）。
set -euo pipefail
cd "$(dirname "$0")"

for f in MonoMod.RuntimeDetour.dll MonoMod.Utils.dll Mono.Cecil.dll; do
  [ -f "libs/$f" ] || { echo "缺少 libs/$f"; exit 1; }
done

mcs -nologo -optimize+ -target:exe \
  -r:libs/MonoMod.RuntimeDetour.dll -r:libs/MonoMod.Utils.dll -r:libs/Mono.Cecil.dll \
  -out:TML13Launcher.exe Program.cs GlCompat.cs

cp TML13Launcher.exe ../../app/src/main/assets/prebuilt/tml13/TML13Launcher.exe
echo "built: $(pwd)/TML13Launcher.exe"
