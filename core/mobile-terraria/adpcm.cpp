#include "adpcm.h"

#include <array>
#include <cstdlib>
#include <limits>

namespace terraria {
namespace {
// Microsoft ADPCM coefficients and adaptation table used by the XACT decoder.
constexpr std::array<int, 7> coeff1{256, 512, 0, 192, 240, 460, 392};
constexpr std::array<int, 7> coeff2{0, -256, 0, 64, 0, -208, -232};
constexpr std::array<int, 16> adapt{230, 230, 230, 230, 307, 409, 512, 614,
    768, 614, 512, 409, 307, 230, 230, 230};

int prediction(int predictor, int previous, int older)
{
    return (previous * coeff1[predictor] + older * coeff2[predictor]) / 256;
}

struct Channel {
    std::array<int, adpcmFramesPerBlock> samples;
    int predictor = 0, delta = 16, previous = 0, older = 0;

    void start() {
        constexpr int probe = 16;
        int best = std::numeric_limits<int>::max();
        for (int candidate = 0; candidate < int(coeff1.size()); ++candidate) {
            int error = 0;
            for (int i = 2; i < probe; ++i)
                error += std::abs(samples[i] - prediction(candidate, samples[i - 1], samples[i - 2]));
            if (error < best) {
                best = error;
                predictor = candidate;
            }
        }
        delta = std::clamp(best / (2 * (probe - 2)), 16, 32767);
        older = samples[0];
        previous = samples[1];
    }

    int encode(int index) {
        const int predicted = prediction(predictor, previous, older);
        const int residual = samples[index] - predicted;
        const int code = std::clamp((residual + (residual < 0 ? -delta / 2 : delta / 2)) / delta, -8, 7);
        older = previous;
        previous = std::clamp(predicted + code * delta, -32768, 32767);
        const int nibble = code & 15;
        // FAudio stores the adapted delta as a signed 16-bit value.
        delta = std::max(16, int(std::bit_cast<int16_t>(uint16_t(adapt[nibble] * delta / 256))));
        return nibble;
    }
};
}

Bytes encodeMsAdpcm(View pcm, int channels)
{
    require((channels == 1 || channels == 2) && !pcm.empty() && pcm.size() % (2 * channels) == 0,
        "Invalid PCM data for ADPCM");
    const size_t frames = pcm.size() / (2 * channels);
    const size_t blocks = (frames + adpcmFramesPerBlock - 1) / adpcmFramesPerBlock;
    Bytes output;
    output.reserve(blocks * adpcmBytesPerChannel * channels);
    for (size_t start = 0; start < frames; start += adpcmFramesPerBlock) {
        std::array<Channel, 2> state;
        for (int position = 0; position < adpcmFramesPerBlock; ++position) {
            const size_t frame = std::min(start + position, frames - 1);
            for (int channel = 0; channel < channels; ++channel) {
                const size_t index = 2 * (frame * channels + channel);
                const auto sample = uint16_t(pcm[index] | (pcm[index + 1] << 8));
                state[channel].samples[position] = std::bit_cast<int16_t>(sample);
            }
        }
        auto active = std::span(state).first(channels);
        for (auto &channel : active) {
            channel.start();
            output.push_back(channel.predictor);
        }
        for (const auto &channel : active) put(output, channel.delta, 2);
        for (const auto &channel : active) put(output, channel.previous, 2);
        for (const auto &channel : active) put(output, channel.older, 2);
        if (channels == 2) {
            for (int i = 2; i < adpcmFramesPerBlock; ++i)
                output.push_back((state[0].encode(i) << 4) | state[1].encode(i));
        } else {
            for (int i = 2; i < adpcmFramesPerBlock; i += 2) {
                const int high = state[0].encode(i);
                output.push_back((high << 4) | state[0].encode(i + 1));
            }
        }
    }
    return output;
}
}
