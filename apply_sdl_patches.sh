#!/bin/bash
# Apply custom SDL patches after submodule init/update
# Usage: ./apply_sdl_patches.sh
#
# This script should be run after:
#   git submodule update --init app/src/main/cpp/SDL

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDL_DIR="$SCRIPT_DIR/app/src/main/cpp/SDL"
PATCHES_DIR="$SCRIPT_DIR/app/src/main/cpp/SDL_patches"

if [ ! -d "$SDL_DIR" ]; then
    echo "Error: SDL submodule not found at $SDL_DIR"
    echo "Run: git submodule update --init app/src/main/cpp/SDL"
    exit 1
fi

# Checkout the correct tag
echo "Checking out SDL release-2.30.1..."
cd "$SDL_DIR"
git checkout release-2.30.1

# Apply patches
echo "Applying custom SDL patches..."
for patch in "$PATCHES_DIR"/*.patch; do
    if [ -f "$patch" ]; then
        echo "  Applying: $(basename "$patch")"
        git apply "$patch"
    fi
done

echo "SDL patches applied successfully!"
