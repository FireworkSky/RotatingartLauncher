#!/data/data/com.termux/files/usr/bin/bash
# 构建 Steam 内容下载宿主，并拷进 APK assets/steam/（RAL 用自带的 .NET 运行时执行）
set -euo pipefail
cd "$(dirname "$0")"

ASSETS=../app/src/main/assets/steam
mkdir -p "$ASSETS"

dotnet build SteamContentCli/SteamContentCli.csproj -c Release

OUT=SteamContentCli/bin/Release/net10.0
rm -rf "$ASSETS"
mkdir -p "$ASSETS"
cp "$OUT"/SteamContentCli.dll "$OUT"/SteamContentCli.runtimeconfig.json "$OUT"/SteamContentCli.deps.json "$ASSETS/"
# 依赖（SteamKit2 及其传递依赖）一起带上
for f in "$OUT"/*.dll; do
  case "$(basename "$f")" in
    SteamContentCli.dll) ;;
    *) cp "$f" "$ASSETS/" ;;
  esac
done
echo "产出：$(cd "$ASSETS" && pwd)（$(ls "$ASSETS" | wc -l) 个文件）"
