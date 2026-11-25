#pragma once

#include "converter.h"

namespace terraria {
// Terraria's PC streaming wave bank uses 70 bytes per channel per block.
constexpr int adpcmBytesPerChannel = 70;
constexpr int adpcmFramesPerBlock = (adpcmBytesPerChannel - 6) * 2;
Bytes encodeMsAdpcm(View pcm, int channels);
}
