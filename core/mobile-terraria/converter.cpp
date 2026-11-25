#include "control_art.h"
#include "adpcm.h"

#include <array>
#include <charconv>
#include <list>
#include <set>
#include <unordered_set>
#include <zlib.h>

extern "C" int tml_decode_fsb5(const char *input, const char *output, int *channels, int *rate);

namespace terraria {
namespace {
constexpr std::array<std::string_view, 1> textureReaders{"Microsoft.Xna.Framework.Content.Texture2DReader"};
constexpr std::array<std::string_view, 1> soundReaders{"Microsoft.Xna.Framework.Content.SoundEffectReader"};
constexpr std::array<std::string_view, 8> fontReaders{
    "ReLogic.Graphics.DynamicSpriteFontReader, ReLogic",
    "Microsoft.Xna.Framework.Content.Texture2DReader",
    "Microsoft.Xna.Framework.Content.ListReader`1[[Microsoft.Xna.Framework.Rectangle, FNA]]",
    "Microsoft.Xna.Framework.Content.ListReader`1[[System.Char, mscorlib]]",
    "Microsoft.Xna.Framework.Content.ListReader`1[[Microsoft.Xna.Framework.Vector3, FNA]]",
    "Microsoft.Xna.Framework.Content.RectangleReader",
    "Microsoft.Xna.Framework.Content.CharReader",
    "Microsoft.Xna.Framework.Content.Vector3Reader",
};
std::unordered_set<uint32_t> audioSetupIds;

bool setupId(View fsb, uint32_t &id)
{
    require(fsb.size() >= 68 && std::string_view(reinterpret_cast<const char *>(fsb.data()), 4) == "FSB5",
        "Invalid FSB5 audio header");
    Reader fileHeader{fsb, 0x18};
    if (fileHeader.read<uint32_t>() != 0x0f) return false;
    Reader reader{fsb, 60};
    (void)reader.read<uint64_t>();
    require(reader.bytes[60] & 1, "FSB5 Vorbis setup metadata is missing");
    for (;;) {
        const uint32_t header = reader.read<uint32_t>();
        const uint32_t type = header >> 25;
        const uint32_t size = (header >> 1) & 0xffffff;
        require(size <= reader.bytes.size() - reader.pos, "Invalid FSB5 audio metadata");
        if (type == 0x0b) {
            require(size >= 4, "Empty FSB5 Vorbis setup ID");
            id = reader.read<uint32_t>();
            return true;
        }
        reader.take(size);
        if (!(header & 1)) break;
    }
    throw std::runtime_error("FSB5 Vorbis setup ID is missing");
}

uint32_t key(std::string name)
{
    for (auto &c : name) if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
    return crc32(0, reinterpret_cast<const uint8_t *>(name.data()), name.size());
}

struct Required {
    std::string path;
    int width, height;
};

std::vector<Required> inventory(const fs::path &path)
{
    std::ifstream input(path, std::ios::binary);
    require(input.is_open(), "Resource inventory is missing");
    std::vector<Required> result;
    std::string name;
    int width, height;
    while (std::getline(input, name, '\t')) {
        require(bool(input >> width >> height) && input.get() == '\n', "Invalid resource inventory");
        result.push_back({name, width, height});
    }
    require(!result.empty(), "Empty resource inventory");
    return result;
}

struct Audio {
    int channels, rate;
    Bytes samples;
    uint32_t frames() const { return samples.size() / (channels * 2); }
};

Audio decode(const fs::path &workspace, View fsb)
{
    const auto input = workspace / "clip.fsb";
    const auto output = workspace / "clip.pcm";
    save(input, fsb);
    Audio result{};
    require(tml_decode_fsb5(input.c_str(), output.c_str(), &result.channels, &result.rate) == 0,
        "Could not decode FSB5 audio");
    MappedFile pcm(output);
    require(pcm.bytes().size() % (result.channels * 2) == 0, "Truncated PCM frames");
    result.samples.assign(pcm.bytes().begin(), pcm.bytes().end());
    return result;
}

Bytes soundPayload(const Audio &audio)
{
    Bytes output;
    const uint32_t alignment = audio.channels * 2;
    put(output, 18);
    put(output, 1, 2);
    put(output, audio.channels, 2);
    put(output, audio.rate);
    put(output, audio.rate * alignment);
    put(output, alignment, 2);
    put(output, 16, 2);
    put(output, 0, 2); // PCM WAVEFORMATEX has no extension.
    put(output, audio.samples.size());
    append(output, audio.samples);
    put(output, 0);
    put(output, 0);
    put(output, uint64_t(audio.frames()) * 1000 / audio.rate);
    return output;
}

std::vector<int> musicOrder(View soundBank)
{
    Reader bank{soundBank};
    require(bank.read<uint32_t>() == 0x4b424453 && bank.read<uint16_t>() == 46 &&
        bank.read<uint16_t>() == 43, "Unsupported XACT sound bank");
    bank.pos = 19;
    const auto simple = bank.read<uint16_t>();
    const auto complex = bank.read<uint16_t>();
    bank.pos = 27;
    require(simple > 0 && !complex && bank.read<uint8_t>() == 1 && bank.read<uint16_t>() == simple,
        "Expected a single XACT wave bank");
    bank.pos = 34;
    const auto cues = bank.read<uint32_t>();
    bank.pos += 20;
    const auto waveBank = bank.read<uint32_t>();
    bank.pos += 4;
    const auto names = bank.read<uint32_t>();
    const auto sounds = bank.read<uint32_t>();
    require(waveBank >= 138 && waveBank <= sounds && sounds <= cues && cues <= names &&
        uint64_t(cues) + 5 * simple <= names && uint64_t(names) + 6 * simple <= soundBank.size(),
        "Invalid XACT sound bank offsets");
    Reader waveName{slice(soundBank, waveBank, 64)};
    require(waveName.string() == "Wave Bank", "Unexpected XACT wave bank name");
    std::vector<int> order(simple);
    std::set<int> numbers;
    for (int i = 0; i < simple; ++i) {
        bank.pos = cues + 5 * i;
        require(bank.read<uint8_t>() == 4, "Unsupported XACT cue");
        const auto sound = bank.read<uint32_t>();
        bank.pos = names + 6 * i;
        const auto nameOffset = bank.read<uint32_t>();
        require(nameOffset < soundBank.size() && sound >= sounds && uint64_t(sound) + 12 <= cues,
            "Invalid XACT cue offsets");
        bank.pos = nameOffset;
        const auto name = bank.string();
        require(name.starts_with("Music_"), "Unexpected XACT cue name");
        int number = 0;
        auto parsed = std::from_chars(name.data() + 6, name.data() + name.size(), number);
        require(parsed.ec == std::errc{} && parsed.ptr == name.data() + name.size() && number > 0 &&
            numbers.insert(number).second, "Invalid XACT music number");
        bank.pos = sound;
        require(!(bank.read<uint8_t>() & 1), "Unsupported XACT sound type");
        bank.pos = sound + 9;
        const auto slot = bank.read<uint16_t>();
        require(bank.read<uint8_t>() == 0 && slot < order.size() && !order[slot], "Invalid XACT wave slot");
        order[slot] = number;
    }
    return order;
}

int music(const UnityResources &unity, const fs::path &workspace, const fs::path &content,
    const Progress &progress)
{
    constexpr uint32_t alignment = 2048, headerSize = 52, bankInfoSize = 96, entrySize = 24;
    const std::array<uint8_t, alignment> zeros{};
    const auto aligned = [](uint64_t size) { return (size + alignment - 1) / alignment * alignment; };
    MappedFile settings(workspace / "TerrariaMusic.xgs");
    Reader settingsReader{settings.bytes()};
    require(settings.bytes().size() >= 77 && settingsReader.read<uint32_t>() == 0x46534758 &&
        settingsReader.read<uint16_t>() == 46 && settingsReader.read<uint16_t>() == 42,
        "Unsupported XACT settings");
    MappedFile soundBank(workspace / "Sound Bank.xsb");
    const auto tracks = musicOrder(soundBank.bytes());
    const uint32_t metadataEnd = headerSize + bankInfoSize + tracks.size() * entrySize;
    const uint32_t dataStart = aligned(metadataEnd);
    auto bank = outputFile(content / "Wave Bank.xwb");
    bank.seekp(dataStart);
    Bytes entries;
    uint32_t offset = 0;
    for (size_t i = 0; i < tracks.size(); ++i) {
        progress("music", i, tracks.size());
        const auto source = unity.audio(unity.get("content/music/music_" + std::to_string(tracks[i])));
        uint32_t id = 0;
        if (setupId(source, id)) audioSetupIds.insert(id);
        const auto audio = decode(workspace, source);
        require(audio.rate > 0 && audio.rate < (1 << 18), "Unsupported XACT sample rate");
        const auto encoded = encodeMsAdpcm(audio.samples, audio.channels);
        const uint32_t frames = encoded.size() / (adpcmBytesPerChannel * audio.channels) * adpcmFramesPerBlock;
        require(frames < (1 << 28), "XACT ADPCM track exceeds the sample limit");
        const uint64_t length = aligned(encoded.size());
        require(dataStart + offset + length < (1ull << 32), "XACT wave bank size limit exceeded");
        constexpr uint32_t adpcmTag = 2;
        const uint32_t format = adpcmTag | uint32_t(audio.channels) << 2 | uint32_t(audio.rate) << 5 |
            uint32_t(adpcmBytesPerChannel - 22) << 23;
        for (uint32_t field : {frames << 4, format, offset,
            uint32_t(encoded.size()), 0u, frames}) put(entries, field);
        write(bank, encoded);
        write(bank, View(zeros).first(length - encoded.size()));
        offset += length;
    }
    Bytes metadata;
    append(metadata, "WBND");
    for (uint32_t field : {46u, 44u, headerSize, bankInfoSize, headerSize + bankInfoSize,
        uint32_t(entries.size()), metadataEnd, 0u, 0u, 0u, dataStart, offset}) put(metadata, field);
    put(metadata, 1);
    put(metadata, tracks.size());
    append(metadata, "Wave Bank");
    metadata.resize(headerSize + 8 + 64);
    for (uint32_t field : {entrySize, 64u, alignment, 0u, 0u, 0u}) put(metadata, field);
    append(metadata, entries);
    bank.seekp(0);
    write(bank, metadata);
    bank.close();
    save(content / "TerrariaMusic.xgs", settings.bytes());
    save(content / "Sound Bank.xsb", soundBank.bytes());
    return tracks.size();
}

bool validXnb(View data)
{
    if (data.size() < 10 || data[0] != 'X' || data[1] != 'N' || data[2] != 'B') return false;
    Reader header{data, 6};
    return header.read<uint32_t>() == data.size();
}

// Verified against the decoded 2026.09.3.1 profile textures. The checksums cover
// the complete premultiplied RGBA canvas, including transparent sprite padding.
struct CanvasAdjustment {
    std::string_view path;
    int sourceWidth, sourceHeight, targetWidth, targetHeight, x, y;
    uint32_t pixelCrc;
};

constexpr CanvasAdjustment canvasAdjustments[] = {
    {"Images/Glow_110.xnb", 50, 252, 64, 252, 12, 0, 0x657596e9},
    {"Images/Glow_95.xnb", 28, 28, 32, 32, 2, 2, 0x771181c3},
    {"Images/Tiles_552.xnb", 214, 36, 208, 36, 0, 0, 0x49d474e8},
};

Bytes adjustedTexture(const Required &item, const Image &image)
{
    const auto rule = std::ranges::find(canvasAdjustments, item.path, &CanvasAdjustment::path);
    require(rule != std::end(canvasAdjustments) && image.width == rule->sourceWidth &&
        image.height == rule->sourceHeight && item.width == rule->targetWidth &&
        item.height == rule->targetHeight, "Unexpected texture dimensions");
    Image canvas(item.width, item.height);
    const auto width = std::min(image.width, canvas.width - rule->x);
    const auto height = std::min(image.height, canvas.height - rule->y);
    for (int y = 0; y < height; ++y) {
        std::copy_n(image.rgba.data() + image.offset(0, y), size_t(width) * 4,
            canvas.rgba.data() + canvas.offset(rule->x, y + rule->y));
    }
    auto payload = texturePayload(std::move(canvas));
    const auto pixels = View(payload).subspan(20);
    require(crc32(0, pixels.data(), pixels.size()) == rule->pixelCrc,
        "Adjusted texture pixels differ from the target profile");
    return payload;
}

std::string quote(std::string_view value)
{
    std::string result = "\"";
    for (unsigned char c : value) {
        if (c == '"' || c == '\\') result += '\\';
        result += c < 32 ? '?' : static_cast<char>(c);
    }
    return result + '"';
}
}

std::string convert(const fs::path &workspace, const fs::path &output, const Progress &progress)
{
    const auto required = inventory(workspace / "inventory.tsv");
    const auto content = output / "Content";
    fs::create_directories(content);
    UnityResources unity(workspace, progress);
    auto regions = readAtlasRegions(unity);
    const auto controlRecipes = workspace / "control-recipes";
    int controlSprites = 0;
    if (fs::is_regular_file(controlRecipes))
        controlSprites = extractControlArt(unity, regions, controlRecipes, output / "mobile-controls");
    std::list<std::pair<std::string, Image>> sheets;
    const auto texture = [&](const Required &item, auto asset) {
        if (asset != unity.assets.end() && asset->second.type == UnityAsset::Texture)
            return unity.texture(asset->second);
        const fs::path relative = fs::path(item.path.substr(7)).replace_extension(".png");
        auto entries = regions.find(key(relative.generic_string()));
        if (entries == regions.end()) entries = regions.find(key(relative.filename().string()));
        require(entries != regions.end(), "No atlas entry");
        const Region *chosen = nullptr;
        for (const auto &region : entries->second) {
            if (entries->second.size() > 1 && (region.width * region.divisor != item.width ||
                region.height * region.divisor != item.height)) continue;
            require(!chosen, "Ambiguous atlas entry");
            chosen = &region;
        }
        require(chosen, "No matching atlas entry");
        auto cached = std::ranges::find_if(sheets, [&](const auto &sheet) { return sheet.first == chosen->sheet; });
        if (cached == sheets.end()) {
            if (sheets.size() == 4) sheets.pop_back();
            sheets.emplace_front(chosen->sheet, loadAtlas(unity, chosen->sheet));
        } else sheets.splice(sheets.begin(), sheets, cached);
        return unpackSprite(sheets.front().second, *chosen);
    };
    int images = 0, sounds = 0, fonts = 0;
    std::vector<std::string> fallbacks;
    for (size_t i = 0; i < required.size(); ++i) {
        progress("assets", i, required.size());
        const auto &item = required[i];
        auto mobile = "content/" + item.path.substr(0, item.path.size() - 4);
        for (auto &c : mobile) if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
        const auto asset = unity.assets.find(mobile);
        try {
            if (item.path.starts_with("Images/")) {
                Bytes payload;
                try {
                    auto image = texture(item, asset);
                    payload = image.width == item.width && image.height == item.height
                        ? texturePayload(std::move(image))
                        : adjustedTexture(item, image);
                } catch (const std::runtime_error &) {
                    const auto fallback = workspace / "fallback" / item.path;
                    if (!fs::is_regular_file(fallback)) throw;
                    MappedFile file(fallback);
                    require(validXnb(file.bytes()), "Invalid fallback texture");
                    save(content / item.path, file.bytes());
                    fallbacks.push_back(item.path);
                    continue;
                }
                writeXnb(content / item.path, textureReaders, payload);
                ++images;
            } else if (item.path.starts_with("Sounds/")) {
                writeXnb(content / item.path, soundReaders,
                    soundPayload(decode(workspace, unity.audio(unity.get(mobile)))), false);
                ++sounds;
            } else {
                writeXnb(content / item.path, fontReaders, fontPayload(unity, mobile));
                ++fonts;
            }
        } catch (const std::exception &failure) {
            throw std::runtime_error(item.path + ": " + failure.what());
        }
    }
    sheets.clear();
    for (const auto name : {"PixelShader.xnb", "ScreenShader.xnb", "TileShader.xnb"}) {
        MappedFile effect(workspace / name);
        require(validXnb(effect.bytes()), "Invalid compiled shader");
        save(content / name, effect.bytes());
    }
    const auto tracks = music(unity, workspace, content, progress);
    std::string report = "{\"complete\":true,\"missing\":[],\"required_files\":" +
        std::to_string(required.size() + 6) + ",\"converted\":{\"Images\":" + std::to_string(images) +
        ",\"Sounds\":" + std::to_string(sounds) + ",\"Fonts\":" + std::to_string(fonts) +
        "},\"music_tracks_from_apk\":" + std::to_string(tracks) +
        ",\"control_sprites\":" + std::to_string(controlSprites) + ",\"fallback_textures\":[";
    for (size_t i = 0; i < fallbacks.size(); ++i) {
        if (i) report += ',';
        report += quote(fallbacks[i]);
    }
    report += "],\"vorbis_setup_ids\":[";
    size_t setupIndex = 0;
    for (uint32_t id : audioSetupIds) {
        if (setupIndex++) report += ',';
        report += std::to_string(id);
    }
    report += "]}\n";
    save(output / "conversion-report.json", View(reinterpret_cast<const uint8_t *>(report.data()), report.size()));
    return report;
}
}
