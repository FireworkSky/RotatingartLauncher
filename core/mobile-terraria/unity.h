#pragma once

#include "converter.h"
#include <map>
#include <memory>
#include <unordered_map>

namespace terraria {
class MappedFile {
public:
    explicit MappedFile(const fs::path &path);
    ~MappedFile();
    MappedFile(const MappedFile &) = delete;
    MappedFile &operator=(const MappedFile &) = delete;
    View bytes() const { return data; }

private:
    View data;
};

struct Image {
    int width, height;
    Bytes rgba;
    Image(int width, int height);
    size_t offset(int x, int y) const { return (size_t(y) * width + x) * 4; }
};

struct UnityAsset {
    enum Type { Texture = 28, Text = 49, Audio = 83, Manager = 147 };
    int type;
    View data;
};

class UnityResources {
public:
    UnityResources(const fs::path &workspace, const Progress &progress, bool includeAudio = true);
    Image texture(const UnityAsset &asset) const;
    View text(const UnityAsset &asset) const;
    View audio(const UnityAsset &asset) const;
    const UnityAsset &get(const std::string &name) const {
        auto asset = assets.find(name);
        if (asset == assets.end()) throw std::runtime_error("Missing Unity asset: " + name);
        return asset->second;
    }
    std::map<std::string, UnityAsset> assets;

private:
    MappedFile unpacked;
    std::unique_ptr<MappedFile> streamedAudio;
    std::map<std::string, View> files;
};
}
