#!/data/data/com.termux/files/usr/bin/bash
# 构建 SteamPatch（tModLoader 1.4+ 的 Steam 创意工坊支持补丁）
#
# 产物：
#   build/SteamPatch.dll                                 入口程序集（内嵌 bridge + 依赖）
#   ../assets/patches/com.app.ralaunch.tmodloader.steam.zip   与 patch.json 一起打包的补丁 zip
#
# 依赖：
#   - dotnet SDK 8+
#   - DepotDownloader 的 Release 输出（DepotDownloader.dll 及其依赖），用于进程内下载：
#       git clone https://github.com/SteamRE/DepotDownloader && dotnet build -c Release
#   - Mono.Cecil.dll / 0Harmony.dll（分别来自任何 RAL 依赖目录 / SteamPatch 原构建）
set -euo pipefail
cd "$(dirname "$0")"

ROOT=$(pwd)
BUILD=$ROOT/build
EMBED=$BUILD/embed
ASSETS=$ROOT/../../app/src/main/assets/patches
DEPOT_DIR=${DEPOT_DIR:-$HOME/steam-poc/dd/DepotDownloader/bin/Release/net8.0}
CECIL_DLL=${CECIL_DLL:-$HOME/locrefs/Mono.Cecil.dll}
HARMONY_DLL=${HARMONY_DLL:-$HOME/steam-poc/0Harmony.dll}

mkdir -p "$EMBED" "$BUILD"

echo "== 1/3 构建 RAL.SteamBridge"
dotnet build Bridge/RAL.SteamBridge.csproj -c Release -o "$BUILD/bridge" \
  -p:DepotDownloaderDll="$DEPOT_DIR/DepotDownloader.dll"

echo "== 2/3 收集内嵌资源"
cp "$BUILD/bridge/RAL.SteamBridge.dll" "$EMBED/"
cp "$DEPOT_DIR"/{DepotDownloader.dll,SteamKit2.dll,protobuf-net.dll,protobuf-net.Core.dll,QRCoder.dll,System.IO.Hashing.dll,ZstdSharp.dll} "$EMBED/"
[ -f "$DEPOT_DIR/DepotDownloader.deps.json" ] && cp "$DEPOT_DIR/DepotDownloader.deps.json" "$EMBED/"
[ -f "$DEPOT_DIR/DepotDownloader.runtimeconfig.json" ] && cp "$DEPOT_DIR/DepotDownloader.runtimeconfig.json" "$EMBED/"
cp "$CECIL_DLL" "$EMBED/Mono.Cecil.dll"
cp "$HARMONY_DLL" "$EMBED/0Harmony.dll"

echo "== 3/3 构建 SteamPatch 并打包"
dotnet build SteamPatch/SteamPatch.csproj -c Release -o "$BUILD" -p:EmbedPath="$EMBED"

rm -f "$BUILD/com.app.ralaunch.tmodloader.steam.zip"
cp patch.json "$BUILD/patch.json"
( cd "$BUILD" && zip -q com.app.ralaunch.tmodloader.steam.zip SteamPatch.dll patch.json )

mkdir -p "$ASSETS"
cp "$BUILD/com.app.ralaunch.tmodloader.steam.zip" "$ASSETS/"
echo "产物：$BUILD/SteamPatch.dll"
echo "     $ASSETS/com.app.ralaunch.tmodloader.steam.zip"
