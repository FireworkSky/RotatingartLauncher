#pragma once

#include "unity.h"
#include <memory>

namespace terraria {
struct Frame { int dx, dy, x, y, right, bottom; };
struct Region {
    std::string sheet;
    int x, y, width, height, divisor, frameWidth, frameHeight;
    std::vector<Frame> frames;
};

using Regions = std::unordered_map<uint32_t, std::vector<Region>>;
Regions readAtlasRegions(const UnityResources &unity);
Image loadAtlas(const UnityResources &unity, const std::string &path);
Image unpackSprite(const Image &atlas, const Region &region);
Bytes texturePayload(Image image);
Bytes fontPayload(const UnityResources &unity, const std::string &path);
void writeXnb(const fs::path &path, std::span<const std::string_view> readers, View payload, bool compress = true);
}
