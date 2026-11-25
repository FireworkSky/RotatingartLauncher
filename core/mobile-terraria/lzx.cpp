#include "lzx.h"

#include <array>
#include <lz4.h>
#include <queue>

namespace terraria {
namespace {
// FNA reads independent bit frames with a 64 KiB LZX window and shared trees.
constexpr int frameSize = 32768, mainSymbols = 512, lengthSymbols = 249;

class BitWriter {
public:
    void bits(uint32_t value, int count) {
        pending = (pending << count) | value;
        used += count;
        if (used >= 16) {
            used -= 16;
            put(data, pending >> used, 2);
            pending &= (1u << used) - 1;
        }
    }

    Bytes finish() {
        if (used) bits(0, 16 - used);
        // The FNA decoder looks ahead by up to one word when reading a symbol.
        put(data, 0, 2);
        return std::move(data);
    }

private:
    Bytes data;
    uint32_t pending = 0;
    int used = 0;
};

template<size_t N>
struct Huffman {
    std::array<uint8_t, N> lengths{};
    std::array<uint16_t, N> codes{};

    void assignCodes() {
        std::array<unsigned, 17> counts{}, next{};
        for (int length : lengths) if (length) ++counts[length];
        for (size_t i = 1; i < next.size(); ++i) next[i] = (next[i - 1] + counts[i - 1]) << 1;
        for (size_t symbol = 0; symbol < N; ++symbol)
            if (lengths[symbol]) codes[symbol] = next[lengths[symbol]]++;
    }

    void emit(BitWriter &writer, int symbol) const {
        writer.bits(codes[symbol], lengths[symbol]);
    }
};

template<size_t N>
Huffman<N> buildTree(const std::array<int, N> &frequency)
{
    Huffman<N> tree;
    std::array<int, 2 * N - 1> parent;
    parent.fill(-1);
    using Entry = std::pair<int, int>;
    std::priority_queue<Entry, std::vector<Entry>, std::greater<Entry>> queue;
    for (size_t i = 0; i < N; ++i) if (frequency[i]) queue.emplace(frequency[i], i);
    if (queue.empty()) return tree;
    if (queue.size() == 1) queue.emplace(1, queue.top().second == 0 ? 1 : 0);
    int next = N;
    while (queue.size() > 1) {
        const auto [leftWeight, left] = queue.top();
        queue.pop();
        const auto [rightWeight, right] = queue.top();
        queue.pop();
        parent[left] = parent[right] = next;
        queue.emplace(leftWeight + rightWeight, next++);
    }
    int depth = 0;
    for (size_t i = 0; i < N; ++i) {
        for (int node = i; parent[node] != -1; node = parent[node]) ++tree.lengths[i];
        depth = std::max(depth, int(tree.lengths[i]));
    }
    if (depth > 16) {
        // A complete balanced tree keeps all codes within the LZX limit.
        const int upper = std::bit_width(N - 1);
        const size_t shortCodes = (1u << upper) - N;
        for (size_t i = 0; i < N; ++i) tree.lengths[i] = i < shortCodes ? upper - 1 : upper;
    }
    tree.assignCodes();
    return tree;
}

void writeLengths(BitWriter &writer, View current, View previous)
{
    static const auto pretree = [] {
        Huffman<20> result;
        for (int i = 0; i < 20; ++i) result.lengths[i] = i < 12 ? 4 : 5;
        result.assignCodes();
        return result;
    }();
    for (auto length : pretree.lengths) writer.bits(length, 4);
    size_t index = 0;
    while (index < current.size()) {
        size_t zeros = 0;
        while (zeros < 51 && index + zeros < current.size() && current[index + zeros] == 0) ++zeros;
        if (zeros >= 20) {
            pretree.emit(writer, 18);
            writer.bits(zeros - 20, 5);
            index += zeros;
        } else if (zeros >= 4) {
            pretree.emit(writer, 17);
            writer.bits(zeros - 4, 4);
            index += zeros;
        } else {
            pretree.emit(writer, (17 + previous[index] - current[index]) % 17);
            ++index;
        }
    }
}

struct Token {
    int symbol;
    int lengthCode = -1;
    int extraOffset = 0;
    int offsetBits = 0;
};

void addMatch(std::vector<Token> &tokens, int distance, int length, std::array<int, 3> &recent)
{
    int slot = 0, extraOffset = 0, offsetBits = 0;
    if (auto found = std::ranges::find(recent, distance); found != recent.end()) {
        slot = found - recent.begin();
        std::swap(*found, recent.front());
    } else {
        const unsigned position = distance + 2;
        if (position <= 3) slot = 3;
        else {
            offsetBits = std::bit_width(position) - 2;
            slot = (offsetBits + 1) * 2 + ((position >> offsetBits) & 1);
            extraOffset = position & ((1 << offsetBits) - 1);
        }
        recent = {distance, recent[0], recent[1]};
    }
    const int primary = std::min(7, length - 2);
    tokens.push_back({256 + 8 * slot + primary, length >= 9 ? length - 9 : -1, extraOffset, offsetBits});
}

std::vector<Token> tokenize(View frame, std::array<int, 3> &recent)
{
    // LZ4 and LZX both use backward references. Reuse the existing LZ4 match
    // finder and serialize its sequences as LZX literals and matches.
    Bytes packed(LZ4_compressBound(frame.size()));
    const int size = LZ4_compress_default(reinterpret_cast<const char *>(frame.data()),
        reinterpret_cast<char *>(packed.data()), frame.size(), packed.size());
    require(size > 0, "Could not find XNB compression matches");
    Reader sequences{View(packed).first(size)};
    const auto length = [&](int value) {
        if (value != 15) return value;
        int extension;
        do {
            extension = sequences.read<uint8_t>();
            value += extension;
        } while (extension == 255);
        return value;
    };
    std::vector<Token> tokens;
    tokens.reserve(frame.size());
    while (sequences.pos < sequences.bytes.size()) {
        const auto descriptor = sequences.read<uint8_t>();
        for (auto literal : sequences.take(length(descriptor >> 4))) tokens.push_back({literal});
        if (sequences.pos == sequences.bytes.size()) break;
        const int distance = sequences.read<uint16_t>();
        int remaining = length(descriptor & 15) + 4;
        while (remaining) {
            // LZX spans at most 257 bytes and requires at least two.
            const int count = remaining == 258 ? 256 : std::min(remaining, 257);
            addMatch(tokens, distance, count, recent);
            remaining -= count;
        }
    }
    return tokens;
}
}

Bytes encodeXnbLzx(View input)
{
    Bytes encoded;
    Huffman<mainSymbols> previousMain;
    Huffman<lengthSymbols> previousLengths;
    std::array<int, 3> recent{1, 1, 1};
    for (size_t offset = 0; offset < input.size(); offset += frameSize) {
        const auto frame = input.subspan(offset, std::min<size_t>(frameSize, input.size() - offset));
        auto tokens = tokenize(frame, recent);
        std::array<int, mainSymbols> mainFrequency{};
        std::array<int, lengthSymbols> lengthFrequency{};
        for (const auto &token : tokens) {
            ++mainFrequency[token.symbol];
            if (token.lengthCode >= 0) ++lengthFrequency[token.lengthCode];
        }
        const auto main = buildTree(mainFrequency);
        const auto lengths = buildTree(lengthFrequency);
        BitWriter writer;
        if (!offset) writer.bits(0, 1); // No Intel executable transform.
        writer.bits(1, 3); // LZX verbatim block.
        writer.bits(frame.size(), 24);
        writeLengths(writer, View(main.lengths).first(256), View(previousMain.lengths).first(256));
        writeLengths(writer, View(main.lengths).subspan(256), View(previousMain.lengths).subspan(256));
        writeLengths(writer, lengths.lengths, previousLengths.lengths);
        for (const auto &token : tokens) {
            main.emit(writer, token.symbol);
            if (token.lengthCode >= 0) lengths.emit(writer, token.lengthCode);
            writer.bits(token.extraOffset, token.offsetBits);
        }
        auto block = writer.finish();
        require(block.size() < 0xff00, "LZX block exceeds the XNB frame limit");
        if (frame.size() != frameSize) {
            encoded.push_back(0xff);
            encoded.push_back(frame.size() >> 8);
            encoded.push_back(frame.size());
        }
        encoded.push_back(block.size() >> 8);
        encoded.push_back(block.size());
        append(encoded, block);
        previousMain = main;
        previousLengths = lengths;
    }
    return encoded;
}
}
