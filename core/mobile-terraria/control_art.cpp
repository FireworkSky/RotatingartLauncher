#include "control_art.h"

#include <array>
#include <cctype>
#include <list>

namespace terraria {
namespace {
constexpr std::string_view uiAtlas = "content/packedimages/ui";

template<size_t N>
std::array<int, N> coordinates(Reader &reader)
{
    std::array<int, N> result;
    for (auto &value : result) value = reader.read<int32_t>();
    return result;
}

Image crop(Image source, const std::array<int, 4> &box)
{
    const auto [x, y, width, height] = box;
    if (width == 0 && height == 0) return source;
    require(x >= 0 && y >= 0 && width <= source.width - x && height <= source.height - y,
        "Invalid control sprite crop");
    Image result(width, height);
    for (int row = 0; row < height; ++row)
        std::copy_n(source.rgba.data() + source.offset(x, y + row), width * 4,
            result.rgba.data() + result.offset(0, row));
    return result;
}

int frameSample(int point, int target, int source)
{
    const int edge = std::min(source, target) / 2 - 1;
    if (point < edge) return point;
    if (point >= target - edge) return point + source - target;
    return edge + (point - edge) * (source - 2 * edge) / (target - 2 * edge);
}

Image frame(Image source, int width, int height)
{
    if (width == 0 && height == 0) return source;
    require(width >= 2 && height >= 2, "Invalid control frame size");
    Image result(width, height);
    for (int y = 0; y < height; ++y) {
        const int sy = frameSample(y, height, source.height);
        for (int x = 0; x < width; ++x)
            std::copy_n(source.rgba.data() + source.offset(frameSample(x, width, source.width), sy),
                4, result.rgba.data() + result.offset(x, y));
    }
    return result;
}

void composite(Image &canvas, const Image &layer, int left, int top)
{
    for (int y = std::max(0, -top); y < std::min(layer.height, canvas.height - top); ++y) {
        for (int x = std::max(0, -left); x < std::min(layer.width, canvas.width - left); ++x) {
            const auto *source = layer.rgba.data() + layer.offset(x, y);
            auto *target = canvas.rgba.data() + canvas.offset(left + x, top + y);
            if (source[3] == 0) continue;
            const int back = target[3] * (255 - source[3]);
            const int alpha = 255 * source[3] + back;
            for (int c = 0; c < 3; ++c)
                target[c] = (source[c] * source[3] * 255 + target[c] * back + alpha / 2) / alpha;
            target[3] = (alpha + 127) / 255;
        }
    }
}

Image outline(const Image &source)
{
    Image result(source.width + 4, source.height + 4);
    for (int y = 0; y < source.height; ++y) {
        for (int x = 0; x < source.width; ++x) {
            const auto alpha = source.rgba[source.offset(x, y) + 3];
            for (int dy = 1; dy <= 3; ++dy) {
                for (int dx = 1; dx <= 3; ++dx) {
                    auto *pixel = result.rgba.data() + result.offset(x + dx, y + dy);
                    if (alpha <= pixel[3]) continue;
                    pixel[0] = 255;
                    pixel[1] = 220;
                    pixel[2] = 50;
                    pixel[3] = alpha;
                }
            }
        }
    }
    composite(result, source, 2, 2);
    return result;
}

void recolor(Image &image, Reader &reader)
{
    const auto border = reader.take(4);
    const auto fill = reader.take(4);
    const auto *center = image.rgba.data() + image.offset(image.width / 2, image.height / 2);
    const std::array<uint8_t, 3> base{center[0], center[1], center[2]};
    for (size_t i = 0; i < image.rgba.size(); i += 4) {
        auto *pixel = image.rgba.data() + i;
        if (pixel[3] == 0) continue;
        const auto color = std::equal(base.begin(), base.end(), pixel) ? fill : border;
        std::copy_n(color.data(), 4, pixel);
    }
}

void iris(Image &image)
{
    // Keep the iris mask white so the character creator can tint it.
    const auto *sample = image.rgba.data() + image.offset(image.width / 2, image.height / 2 - 5);
    const std::array<uint8_t, 3> color{sample[0], sample[1], sample[2]};
    for (int y = 0; y < image.height; ++y) {
        for (int x = 0; x < image.width; ++x) {
            auto *pixel = image.rgba.data() + image.offset(x, y);
            if (x >= image.width / 2 - 5 && x < image.width / 2 + 5 &&
                y >= image.height / 2 - 5 && y < image.height / 2 + 5 &&
                std::equal(color.begin(), color.end(), pixel))
                std::fill_n(pixel, 3, 255);
            else pixel[3] = 0;
        }
    }
}

void writeRawImage(const fs::path &path, Image image)
{
    Bytes header;
    put(header, 1);
    put(header, image.width);
    put(header, image.height);
    for (size_t i = 0; i < image.rgba.size(); i += 4)
        for (int c = 0; c < 3; ++c) image.rgba[i + c] = image.rgba[i + c] * image.rgba[i + 3] / 255;
    fs::create_directories(path.parent_path());
    auto file = outputFile(path);
    write(file, header);
    write(file, image.rgba);
    file.close();
}
}

int extractControlArt(const UnityResources &unity, const Regions &regions, const fs::path &recipes,
    const fs::path &output)
{
    MappedFile file(recipes);
    Reader reader{file.bytes()};
    require(reader.read<uint32_t>() == 1, "Unsupported control sprite recipe format");
    const int count = reader.read<uint32_t>();
    require(count > 0 && count <= 1024, "Invalid control sprite count");
    std::unordered_map<uint32_t, Image> sprites;
    std::list<std::pair<std::string, Image>> atlases;
    const auto sprite = [&](uint32_t hash) -> const Image & {
        if (const auto cached = sprites.find(hash); cached != sprites.end()) return cached->second;
        const auto found = regions.find(hash);
        require(found != regions.end(), "Missing mobile UI sprite");
        const Region *chosen = nullptr;
        for (const auto &region : found->second) {
            if (!region.sheet.starts_with(uiAtlas) || region.sheet.size() == uiAtlas.size() ||
                !std::isdigit(static_cast<unsigned char>(region.sheet[uiAtlas.size()]))) continue;
            require(!chosen, "Ambiguous mobile UI sprite");
            chosen = &region;
        }
        require(chosen, "Missing high-resolution mobile UI sprite");
        auto atlas = std::ranges::find_if(atlases, [&](const auto &item) { return item.first == chosen->sheet; });
        if (atlas == atlases.end()) {
            if (atlases.size() == 3) atlases.pop_back();
            atlases.emplace_front(chosen->sheet, loadAtlas(unity, chosen->sheet));
        } else atlases.splice(atlases.begin(), atlases, atlas);
        return sprites.emplace(hash, unpackSprite(atlases.front().second, *chosen)).first->second;
    };

    for (int i = 0; i < count; ++i) {
        const auto name = reader.string();
        const auto slash = name.find('/');
        require(slash != std::string::npos && name.find('/', slash + 1) == std::string::npos &&
            name.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/_-") == std::string::npos &&
            slash + 1 < name.size() && slash > 0, "Invalid control sprite path");
        try {
            const auto [width, height] = coordinates<2>(reader);
            const auto flags = reader.read<uint8_t>();
            const auto layers = reader.read<uint8_t>();
            require(layers > 0 && layers <= 16, "Invalid control sprite layers");
            std::unique_ptr<Image> image;
            for (int l = 0; l < layers; ++l) {
                const auto hash = reader.read<uint32_t>();
                const auto box = coordinates<4>(reader);
                const auto [frameWidth, frameHeight] = coordinates<2>(reader);
                const auto [x, y] = coordinates<2>(reader);
                auto layer = frame(crop(sprite(hash), box), frameWidth, frameHeight);
                if (layers == 1 && !width && !height) image = std::make_unique<Image>(std::move(layer));
                else {
                    if (!image) image = std::make_unique<Image>(width ? width : layer.width, height ? height : layer.height);
                    composite(*image, layer, x == -1 ? (image->width - layer.width) / 2 : x,
                        y == -1 ? (image->height - layer.height) / 2 : y);
                }
            }
            if (flags & 1) {
                for (int y = 0; y < image->height / 2; ++y)
                    std::swap_ranges(image->rgba.data() + image->offset(0, y),
                        image->rgba.data() + image->offset(0, y + 1),
                        image->rgba.data() + image->offset(0, image->height - 1 - y));
            }
            if (flags & 2) recolor(*image, reader);
            if (flags & 4) *image = outline(*image);
            if (flags & 8) iris(*image);
            writeRawImage(output / (name + ".rawimg"), std::move(*image));
        } catch (const std::exception &failure) {
            throw std::runtime_error(name + ": " + failure.what());
        }
    }
    require(reader.pos == reader.bytes.size(), "Trailing control sprite recipe data");
    return count;
}
}
