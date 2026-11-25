#pragma once

#include <bit>
#include <algorithm>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <functional>
#include <span>
#include <stdexcept>
#include <string>
#include <string_view>
#include <type_traits>
#include <vector>

namespace terraria {
namespace fs = std::filesystem;
using Bytes = std::vector<uint8_t>;
using View = std::span<const uint8_t>;
using Progress = std::function<void(const char *, int, int)>;

inline void require(bool condition, const char *message)
{
    if (!condition) throw std::runtime_error(message);
}

inline View slice(View bytes, uint64_t offset, uint64_t size)
{
    require(offset <= bytes.size() && size <= bytes.size() - offset, "Truncated resource data");
    return bytes.subspan(offset, size);
}

struct Reader {
    View bytes;
    size_t pos = 0;
    bool big = false;

    View take(size_t count) {
        auto data = slice(bytes, pos, count);
        pos += count;
        return data;
    }
    template<class T> T read() {
        auto data = take(sizeof(T));
        uint64_t value = 0;
        for (size_t i = 0; i < sizeof(T); ++i)
            value |= uint64_t(data[big ? sizeof(T) - 1 - i : i]) << (i * 8);
        return std::bit_cast<T>(static_cast<std::make_unsigned_t<T>>(value));
    }
    void align(size_t boundary = 4) { take((boundary - pos % boundary) % boundary); }
    std::string string() {
        require(pos < bytes.size(), "Truncated resource string");
        auto end = bytes.subspan(pos);
        auto length = std::ranges::find(end, uint8_t{0}) - end.begin();
        auto data = take(length);
        take(1);
        return {reinterpret_cast<const char *>(data.data()), data.size()};
    }
    std::string alignedString() {
        auto data = take(read<uint32_t>());
        align();
        return {reinterpret_cast<const char *>(data.data()), data.size()};
    }
};

inline void put(Bytes &out, uint64_t value, int count = 4)
{
    for (int i = 0; i < count; ++i) out.push_back(static_cast<uint8_t>(value >> (i * 8)));
}

inline void append(Bytes &out, View bytes)
{
    out.insert(out.end(), bytes.begin(), bytes.end());
}

inline void append(Bytes &out, std::string_view text)
{
    out.insert(out.end(), text.begin(), text.end());
}

inline std::ofstream outputFile(const fs::path &path)
{
    std::ofstream stream;
    stream.exceptions(std::ios::badbit | std::ios::failbit);
    stream.open(path, std::ios::binary);
    return stream;
}

inline void write(std::ostream &stream, View bytes)
{
    stream.write(reinterpret_cast<const char *>(bytes.data()), bytes.size());
}

inline void save(const fs::path &path, View data)
{
    fs::create_directories(path.parent_path());
    auto stream = outputFile(path);
    write(stream, data);
    stream.close();
}

std::string convert(const fs::path &workspace, const fs::path &output, const Progress &progress);
}
