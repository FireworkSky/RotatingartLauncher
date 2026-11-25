#include "xnb.h"
#include "lzx.h"

#include <array>
#include <numeric>

namespace terraria {
namespace {
int ceilDivide(int value, int divisor)
{
    return (value + divisor - 1) / divisor;
}

void sevenBit(Bytes &out, size_t size)
{
    while (size >= 128) {
        out.push_back(static_cast<uint8_t>(size | 128));
        size >>= 7;
    }
    out.push_back(static_cast<uint8_t>(size));
}

void character(Bytes &out, uint16_t code)
{
    require(code < 0xd800 || code >= 0xe000, "Unsupported font character");
    if (code < 128) out.push_back(code);
    else if (code < 2048) {
        out.push_back(0xc0 | (code >> 6));
        out.push_back(0x80 | (code & 63));
    } else {
        out.push_back(0xe0 | (code >> 12));
        out.push_back(0x80 | ((code >> 6) & 63));
        out.push_back(0x80 | (code & 63));
    }
}
}

void writeXnb(const fs::path &path, std::span<const std::string_view> readers, View payload, bool compress)
{
    Bytes header;
    sevenBit(header, readers.size());
    for (const auto reader : readers) {
        sevenBit(header, reader.size());
        append(header, reader);
        put(header, 0);
    }
    header.insert(header.end(), {0, 1});
    const auto contentLength = header.size() + payload.size();
    Bytes compressed;
    if (compress) {
        append(header, payload);
        compressed = encodeXnbLzx(header);
    }
    Bytes prefix{'X', 'N', 'B', 'w', 5, static_cast<uint8_t>(compress ? 0x81 : 1)};
    put(prefix, compress ? 14 + compressed.size() : 10 + contentLength);
    if (compress) put(prefix, contentLength);
    fs::create_directories(path.parent_path());
    auto file = outputFile(path);
    write(file, prefix);
    if (compress) write(file, compressed);
    else {
        write(file, header);
        write(file, payload);
    }
    file.close();
}

Bytes texturePayload(Image image)
{
    Bytes payload;
    payload.reserve(20 + image.rgba.size());
    for (uint32_t field : {0u, uint32_t(image.width), uint32_t(image.height), 1u, uint32_t(image.rgba.size())})
        put(payload, field);
    for (size_t offset = 0; offset < image.rgba.size(); offset += 4)
        for (size_t channel = 0; channel < 3; ++channel)
            image.rgba[offset + channel] = image.rgba[offset + channel] * image.rgba[offset + 3] / 255;
    append(payload, image.rgba);
    return payload;
}

Regions readAtlasRegions(const UnityResources &unity)
{
    Regions regions;
    for (const auto &[path, asset] : unity.assets) {
        if (!path.starts_with("content/packedimages/") || asset.type != UnityAsset::Text) continue;
        Reader blob{unity.text(asset)};
        const auto version = blob.read<uint16_t>();
        const auto layout = blob.read<uint16_t>();
        const auto sheets = blob.read<uint16_t>();
        if (version != 0 || (layout != 2 && layout != 3)) continue;
        blob.take(sheets * (layout == 2 ? 4 : 6));
        const auto count = blob.read<uint32_t>();
        for (uint32_t i = 0; i < count; ++i) {
            if (blob.bytes.size() - blob.pos < 22) break;
            const auto sprite = blob.read<uint32_t>();
            const auto sheet = blob.read<uint16_t>();
            blob.take(2);
            const int width = blob.read<uint16_t>();
            const int height = blob.read<uint16_t>();
            const int x = blob.read<uint16_t>();
            const int y = blob.read<uint16_t>();
            const int divisor = blob.read<uint16_t>();
            const int frameWidth = blob.read<uint16_t>();
            const int frameHeight = blob.read<uint16_t>();
            if (sheet >= sheets || (divisor != 1 && divisor != 2 && divisor != 4) || !width || !height) break;
            Region region{path + std::to_string(sheet + 1), x, y, width, height, divisor, frameWidth, frameHeight, {}};
            if (frameWidth != 65535 || frameHeight != 65535) {
                if (!frameWidth || !frameHeight || blob.pos == blob.bytes.size()) break;
                const auto mode = blob.read<uint8_t>();
                if (mode > 1) break;
                const size_t frames = size_t(ceilDivide(width * divisor, frameWidth)) *
                    ceilDivide(height * divisor, frameHeight);
                if (frames > (blob.bytes.size() - blob.pos) / (mode == 1 ? 12 : 8)) break;
                region.frames.reserve(frames);
                for (size_t j = 0; j < frames; ++j) {
                    const auto dx = blob.read<int16_t>();
                    const auto dy = blob.read<int16_t>();
                    const auto sx = blob.read<uint16_t>();
                    const auto sy = blob.read<uint16_t>();
                    const int right = mode ? blob.read<uint16_t>() : sx + ceilDivide(frameWidth, divisor);
                    const int bottom = mode ? blob.read<uint16_t>() : sy + ceilDivide(frameHeight, divisor);
                    region.frames.push_back(Frame{dx, dy, sx, sy, right, bottom});
                }
            }
            regions[sprite].push_back(std::move(region));
        }
    }
    return regions;
}

Image loadAtlas(const UnityResources &unity, const std::string &path)
{
    auto image = unity.texture(unity.get(path));
    if (const auto it = unity.assets.find(path + "_p"); it != unity.assets.end()) {
        const auto palette = unity.texture(it->second);
        const int columns = ceilDivide(image.width, 256);
        const int rows = ceilDivide(image.height, 256);
        require(palette.width == 256 && (palette.height == 1 || palette.height == rows * columns),
            "Unsupported atlas palette");
        for (int y = 0; y < image.height; ++y) {
            for (int x = 0; x < image.width; ++x) {
                const int row = palette.height == 1 ? 0 : y / 256 * columns + columns - 1 - x / 256;
                auto *pixel = image.rgba.data() + image.offset(x, y);
                std::copy_n(palette.rgba.data() + palette.offset(pixel[3], row), 4, pixel);
            }
        }
    }
    if (const auto it = unity.assets.find(path + "_a"); it != unity.assets.end()) {
        const auto alpha = unity.texture(it->second);
        require(alpha.width == image.width && alpha.height == image.height, "Atlas alpha map size mismatch");
        for (size_t i = 3; i < image.rgba.size(); i += 4) image.rgba[i] = alpha.rgba[i];
    }
    return image;
}

Image unpackSprite(const Image &atlas, const Region &region)
{
    const int divisor = region.divisor;
    Image result(region.width * divisor, region.height * divisor);
    if (region.frames.empty()) {
        require(region.x + region.width <= atlas.width && region.y + region.height <= atlas.height,
            "Out-of-range atlas crop");
        for (int y = 0; y < result.height; ++y)
            for (int x = 0; x < result.width; ++x)
                std::copy_n(atlas.rgba.data() + atlas.offset(region.x + x / divisor, region.y + y / divisor),
                    4, result.rgba.data() + result.offset(x, y));
        return result;
    }
    const int columns = ceilDivide(result.width, region.frameWidth);
    for (size_t i = 0; i < region.frames.size(); ++i) {
        const auto &frame = region.frames[i];
        if (frame.dx == -100 && frame.dy == -100) continue;
        require(frame.x < frame.right && region.x + frame.right <= atlas.width &&
            frame.y < frame.bottom && region.y + frame.bottom <= atlas.height, "Out-of-range atlas frame");
        const int cellX = (i % columns) * region.frameWidth;
        const int cellY = (i / columns) * region.frameHeight;
        const int left = cellX - frame.dx, top = cellY - frame.dy;
        const int startX = std::max(left, cellX), startY = std::max(top, cellY);
        const int endX = std::min({left + (frame.right - frame.x) * divisor,
            cellX + region.frameWidth, result.width});
        const int endY = std::min({top + (frame.bottom - frame.y) * divisor,
            cellY + region.frameHeight, result.height});
        for (int y = startY; y < endY; ++y)
            for (int x = startX; x < endX; ++x)
                std::copy_n(atlas.rgba.data() + atlas.offset(region.x + frame.x + (x - left) / divisor,
                    region.y + frame.y + (y - top) / divisor), 4, result.rgba.data() + result.offset(x, y));
    }
    return result;
}

Bytes fontPayload(const UnityResources &unity, const std::string &path)
{
    constexpr size_t glyphSize = 47;
    Reader blob{unity.text(unity.get(path))};
    const int pages = blob.read<uint8_t>();
    const auto count = blob.read<uint32_t>();
    require(pages > 0 && blob.bytes.size() == 5 + uint64_t(count) * glyphSize + 11,
        "Unsupported font metadata");
    std::vector<std::vector<View>> glyphs(pages);
    for (uint32_t i = 0; i < count; ++i) {
        const auto glyph = blob.take(glyphSize);
        require(glyph.back() < pages, "Font glyph references a missing page");
        glyphs[glyph.back()].push_back(glyph);
    }
    Bytes output;
    const auto lineSpacing = blob.read<uint32_t>();
    put(output, blob.read<uint32_t>());
    put(output, lineSpacing);
    const bool hasDefault = blob.read<uint8_t>() != 0;
    const auto fallback = blob.read<uint16_t>();
    character(output, hasDefault ? fallback : 0);
    put(output, pages);
    for (int page = 0; page < pages; ++page) {
        auto alpha = unity.texture(unity.get(path + "_" + std::to_string(page + 1) + "_a"));
        for (const auto glyph : glyphs[page]) {
            Reader source{glyph};
            const int x = source.read<int32_t>(), y = source.read<int32_t>();
            const int width = source.read<int32_t>(), height = source.read<int32_t>();
            require(x >= 0 && y >= 0 && width >= 0 && height >= 0 &&
                x <= alpha.width && y <= alpha.height &&
                width <= alpha.width - x && height <= alpha.height - y, "Font glyph exceeds its atlas page");
        }
        for (size_t i = 0; i < alpha.rgba.size(); i += 4)
            std::fill_n(alpha.rgba.data() + i, 3, 255);
        output.push_back(2);
        append(output, texturePayload(std::move(alpha)));
        constexpr std::array readers{3, 3, 4, 5};
        for (int list = 0; list < 4; ++list) {
            output.push_back(readers[list]);
            put(output, glyphs[page].size());
            for (const auto glyph : glyphs[page]) {
                if (list < 2) append(output, glyph.subspan(list * 16, 16));
                else if (list == 2) {
                    Reader entry{glyph, 32};
                    character(output, entry.read<uint16_t>());
                } else append(output, glyph.subspan(34, 12));
            }
        }
    }
    return output;
}
}
