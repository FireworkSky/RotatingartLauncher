#include "unity.h"

#include <fcntl.h>
#include <lz4.h>
#include <sys/mman.h>
#include <unistd.h>
#include <algorithm>
#include <limits>

namespace terraria {
MappedFile::MappedFile(const fs::path &path)
{
    const auto size = fs::file_size(path);
    require(size > 0 && size <= (1ull << 31), "Unsupported Unity file size");
    int fd = open(path.c_str(), O_RDONLY);
    require(fd >= 0, "Unable to open Unity data");
    void *mapped = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    require(mapped != MAP_FAILED, "Unable to map Unity data");
    data = {static_cast<const uint8_t *>(mapped), static_cast<size_t>(size)};
}

MappedFile::~MappedFile()
{
    munmap(const_cast<uint8_t *>(data.data()), data.size());
}

namespace {
struct Block { uint32_t size, packed; uint16_t flags; };
struct Entry { uint64_t offset, size; std::string name; };

View decompress(View packed, uint32_t size, uint32_t flags, Bytes &buffer)
{
    const unsigned compression = flags & 63;
    if (compression == 0) {
        require(packed.size() == size, "Invalid uncompressed Unity block");
        return packed;
    }
    require((compression == 2 || compression == 3) && size <= 64 * 1024 * 1024 &&
        packed.size() <= std::numeric_limits<int>::max(), "Unsupported Unity block compression");
    buffer.resize(size);
    const int decoded = LZ4_decompress_safe(reinterpret_cast<const char *>(packed.data()),
        reinterpret_cast<char *>(buffer.data()), static_cast<int>(packed.size()), static_cast<int>(size));
    require(decoded >= 0 && static_cast<uint32_t>(decoded) == size, "Invalid LZ4 Unity block");
    return buffer;
}

fs::path unpack(const fs::path &workspace, const Progress &progress)
{
    MappedFile packed(workspace / "data.unity3d");
    Reader header{packed.bytes(), 0, true};
    require(header.string() == "UnityFS", "Unsupported Unity archive");
    const auto version = header.read<uint32_t>();
    require(version == 7 || version == 8, "Unsupported UnityFS version");
    header.string();
    header.string();
    require(header.read<uint64_t>() == packed.bytes().size(), "Invalid UnityFS archive size");
    const auto packedSize = header.read<uint32_t>();
    const auto size = header.read<uint32_t>();
    const auto flags = header.read<uint32_t>();
    require((flags & ~0x2ffu) == 0 && (flags & 0x40u), "Unsupported UnityFS flags");
    header.align(16);
    const auto info = flags & 0x80
        ? slice(packed.bytes(), packed.bytes().size() - packedSize, packedSize)
        : header.take(packedSize);
    Bytes tableBytes;
    Reader table{decompress(info, size, flags, tableBytes), 0, true};
    table.take(16);
    const auto count = table.read<uint32_t>();
    require(count <= 100000, "Too many Unity blocks");
    std::vector<Block> blocks;
    uint64_t total = 0;
    for (uint32_t i = 0; i < count; ++i) {
        auto &block = blocks.emplace_back(Block{table.read<uint32_t>(), table.read<uint32_t>(), table.read<uint16_t>()});
        total += block.size;
    }
    require(total > 0 && total <= (1ull << 30), "Unsupported expanded UnityFS size");
    const auto path = workspace / "unity-unpacked";
    auto output = outputFile(path);
    if (flags & 0x200) header.align(16);
    Bytes blockBytes;
    for (size_t i = 0; i < blocks.size(); ++i) {
        if (i % 64 == 0) progress("extract", i, blocks.size());
        const auto &block = blocks[i];
        write(output, decompress(header.take(block.packed), block.size, block.flags, blockBytes));
    }
    output.close();

    const auto entries = table.read<uint32_t>();
    require(entries <= 10000, "Too many Unity archive files");
    Bytes index;
    put(index, entries);
    for (uint32_t i = 0; i < entries; ++i) {
        put(index, table.read<uint64_t>(), 8);
        put(index, table.read<uint64_t>(), 8);
        table.take(4);
        append(index, table.string());
        index.push_back(0);
    }
    save(workspace / "unity-index", index);
    return path;
}

struct SerializedFile {
    std::unordered_map<uint64_t, UnityAsset> objects;
    std::vector<std::string> externals;

    explicit SerializedFile(View data) {
        Reader header{data, 0, true};
        header.take(8);
        require(header.read<uint32_t>() == 22, "Unsupported Unity serialized file version");
        header.take(4);
        require(header.read<uint8_t>() == 0, "Unsupported big-endian Unity assets");
        header.take(3);
        const auto metadataLength = header.read<uint32_t>();
        require(header.read<uint64_t>() == data.size(), "Invalid serialized Unity file size");
        const auto dataOffset = header.read<uint64_t>();
        header.take(8);
        Reader reader{slice(data, 0, header.pos + metadataLength), header.pos};
        require(reader.string().starts_with("2021.3."), "Unsupported Unity object layout");
        reader.take(4);
        require(reader.read<uint8_t>() == 0, "Unsupported serialized Unity type tree");
        const auto typeCount = reader.read<uint32_t>();
        require(typeCount <= 20000, "Too many serialized Unity types");
        std::vector<int> types;
        for (uint32_t i = 0; i < typeCount; ++i) {
            const auto type = reader.read<int32_t>();
            types.push_back(type);
            reader.take(type == 114 ? 35 : 19);
        }
        const auto objectsCount = reader.read<uint32_t>();
        require(objectsCount <= 100000, "Too many serialized Unity objects");
        for (uint32_t i = 0; i < objectsCount; ++i) {
            reader.align();
            const auto id = reader.read<uint64_t>();
            const auto offset = reader.read<uint64_t>();
            const auto length = reader.read<uint32_t>();
            const auto type = reader.read<uint32_t>();
            require(type < types.size() && dataOffset <= data.size() && offset <= data.size() - dataOffset,
                "Invalid serialized Unity object");
            objects.emplace(id, UnityAsset{types[type], slice(data, dataOffset + offset, length)});
        }
        const auto scripts = reader.read<uint32_t>();
        require(scripts <= 100000, "Too many serialized Unity script references");
        for (uint32_t i = 0; i < scripts; ++i) {
            reader.take(4);
            reader.align();
            reader.take(8);
        }
        const auto references = reader.read<uint32_t>();
        require(references <= 10000, "Too many serialized Unity external references");
        for (uint32_t i = 0; i < references; ++i) {
            reader.string();
            reader.take(20);
            externals.push_back(reader.string());
        }
    }
};

std::string basename(const std::string &path)
{
    return path.substr(path.find_last_of("/\\") + 1);
}
}

UnityResources::UnityResources(const fs::path &workspace, const Progress &progress, bool includeAudio)
    : unpacked(unpack(workspace, progress))
{
    if (includeAudio) streamedAudio = std::make_unique<MappedFile>(workspace / "resources.resource");
    MappedFile index(workspace / "unity-index");
    Reader entries{index.bytes()};
    const auto count = entries.read<uint32_t>();
    for (uint32_t i = 0; i < count; ++i) {
        const auto offset = entries.read<uint64_t>();
        const auto size = entries.read<uint64_t>();
        files.emplace(basename(entries.string()), slice(unpacked.bytes(), offset, size));
    }
    SerializedFile managerFile(files.at("globalgamemanagers"));
    SerializedFile resources(files.at("resources.assets"));
    const auto manager = std::ranges::find_if(managerFile.objects,
        [](const auto &item) { return item.second.type == UnityAsset::Manager; });
    require(manager != managerFile.objects.end(), "Unity resource manager is missing");
    Reader container{manager->second.data};
    const auto size = container.read<uint32_t>();
    require(size <= 100000, "Too many Unity resource paths");
    for (uint32_t i = 0; i < size; ++i) {
        auto name = container.alignedString();
        const auto file = container.read<uint32_t>();
        const auto object = container.read<uint64_t>();
        if (!name.starts_with("content/") || !object) continue;
        require(file > 0 && file <= managerFile.externals.size() &&
            basename(managerFile.externals[file - 1]) == "resources.assets",
            "Unsupported external Unity resource container");
        assets.insert_or_assign(std::move(name), resources.objects.at(object));
    }
}

Image::Image(int w, int h) : width(w), height(h)
{
    require(width > 0 && height > 0 && uint64_t(width) * height <= 8192ull * 8192,
        "Unsupported texture dimensions");
    rgba.resize(size_t(width) * height * 4);
}

Image UnityResources::texture(const UnityAsset &asset) const
{
    require(asset.type == UnityAsset::Texture, "Expected a Unity Texture2D");
    Reader reader{asset.data};
    reader.alignedString();
    // Forced fallback format and fallback flags precede the texture dimensions.
    reader.take(6);
    reader.align();
    const int width = reader.read<int32_t>();
    const int height = reader.read<int32_t>();
    reader.take(8);
    const auto format = reader.read<uint32_t>();
    // Skip mipmap settings, texture sampler state, lightmap format and color space.
    reader.take(52);
    reader.take(reader.read<uint32_t>());
    reader.align();
    auto pixels = reader.take(reader.read<uint32_t>());
    if (pixels.empty()) {
        const auto offset = reader.read<uint64_t>();
        const auto size = reader.read<uint32_t>();
        pixels = slice(files.at(basename(reader.alignedString())), offset, size);
    }
    const int stride = format == 1 ? 1 : format == 3 ? 3 : format == 4 ? 4 : format == 13 ? 2 : 0;
    require(stride != 0, "Unsupported Unity texture format");
    Image result(width, height);
    require(pixels.size() >= size_t(width) * height * stride, "Truncated Unity texture pixels");
    for (int y = 0; y < height; ++y) {
        auto src = pixels.data() + size_t(height - 1 - y) * width * stride;
        auto dst = result.rgba.data() + result.offset(0, y);
        for (int x = 0; x < width; ++x, src += stride, dst += 4) {
            if (format == 1) dst[3] = src[0];
            else if (format == 13) {
                dst[0] = (src[0] & 15) * 17;
                dst[1] = (src[0] >> 4) * 17;
                dst[2] = (src[1] & 15) * 17;
                dst[3] = (src[1] >> 4) * 17;
            } else {
                std::copy_n(src, stride, dst);
                if (stride == 3) dst[3] = 255;
            }
        }
    }
    return result;
}

View UnityResources::text(const UnityAsset &asset) const
{
    require(asset.type == UnityAsset::Text, "Expected a Unity TextAsset");
    Reader reader{asset.data};
    reader.alignedString();
    return reader.take(reader.read<uint32_t>());
}

View UnityResources::audio(const UnityAsset &asset) const
{
    require(asset.type == UnityAsset::Audio, "Expected a Unity AudioClip");
    Reader reader{asset.data};
    reader.alignedString();
    // Fixed AudioClip settings precede the external stream reference.
    reader.take(32);
    require(basename(reader.alignedString()) == "resources.resource", "Unsupported audio resource stream");
    const auto offset = reader.read<uint64_t>();
    const auto size = reader.read<uint64_t>();
    require(bool(streamedAudio), "Unity audio stream is unavailable");
    auto bytes = slice(streamedAudio->bytes(), offset, size);
    require(bytes.size() >= 4 && std::string_view(reinterpret_cast<const char *>(bytes.data()), 4) == "FSB5",
        "Unsupported Unity audio format");
    return bytes;
}
}
