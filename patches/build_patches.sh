#!/bin/bash

echo "========================================"
echo "Building Patch System"
echo "========================================"

cd "$(dirname "$0")"

echo ""
echo "[1/3] Building ExamplePatch..."
cd ExamplePatch
dotnet build -c Release
if [ $? -ne 0 ]; then
    echo "ERROR: ExamplePatch build failed"
    exit 1
fi
cd ..

echo ""
echo "[2/3] Copying files to assets..."
ASSETS_DIR="../app/src/main/assets/patches"

mkdir -p "$ASSETS_DIR"

cp ExamplePatch/bin/Release/net8.0/ExamplePatch.dll "$ASSETS_DIR/"
cp ExamplePatch/patch.json "$ASSETS_DIR/"

echo ""
echo "[3/3] Build complete!"
echo "========================================"
echo "Output: $ASSETS_DIR"
echo ""
echo "ExamplePatch.dll       [Patch Assembly]"
echo "patch.json             [Metadata]"
echo "========================================"
