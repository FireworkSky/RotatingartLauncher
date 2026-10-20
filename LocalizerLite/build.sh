#!/data/data/com.termux/files/usr/bin/bash
# 编译精简版 Localizer（tModLoader 1.3 的汉化 mod，去掉了 GUI/ModBrowser/网络/导出）
#
# 依赖：
#   - mcs（mono-mcs）
#   - 游戏目录里 tModLoader 1.3 的 Terraria.exe（本仓库里按 Terraria.dll 引用）、FNA.dll
#   - libs/ 里的 MonoMod/Cecil/Newtonsoft/Ninject/log4net/ReLogic（与游戏内嵌版本一致）
#
# 用法：RAL_GAME_DIR=<游戏目录> ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

GAME_DIR=${RAL_GAME_DIR:-/storage/emulated/0/Android/data/com.app.ralaunch/files/games/Terraria_b9ab7274/GoG\ Games/Terraria}
LIBS=libs
OUT=out
S=/data/data/com.termux/files/usr/lib/mono/4.5

mkdir -p "$OUT"
for f in Terraria.dll FNA.dll; do
  if [ ! -f "$OUT/$f" ]; then
    src="$GAME_DIR/${f/Terraria.dll/tModLoader.exe}"
    cp "$src" "$OUT/$f"
  fi
done

mcs -nologo -target:library -langversion:latest -unsafe -out:"$OUT/Localizer.dll" \
  -r:"$LIBS/Mono.Cecil.dll" -r:"$LIBS/MonoMod.Utils.dll" -r:"$LIBS/MonoMod.RuntimeDetour.dll" \
  -r:"$LIBS/Newtonsoft.Json.dll" -r:"$LIBS/Ninject.dll" -r:"$LIBS/log4net.dll" -r:"$LIBS/ReLogic.dll" \
  -r:"$S/System.IO.Compression.dll" -r:"$S/System.Core.dll" -r:"$S/System.Xml.dll" -r:"$S/System.dll" \
  -r:"$OUT/Terraria.dll" -r:"$OUT/FNA.dll" \
  $(find . -name '*.cs' | sort)

python3 pack.py "$OUT/Localizer.tmod"
echo "产物：$(pwd)/$OUT/Localizer.tmod"
