#pragma once

#include "xnb.h"

namespace terraria {
int extractControlArt(const UnityResources &unity, const Regions &regions, const fs::path &recipes,
    const fs::path &output);
}
