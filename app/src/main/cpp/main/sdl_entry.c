#include "logging/app_log.h"


#define LOG_TAG "SDLEntry"

/**
 * @brief SDL 主函数入口点
 */
__attribute__((visibility("default"))) int SDL_main(int argc, char* argv[]) {
    // Obsolete function
    LOGE(LOG_TAG, "SDL_main is obsolete. Use GameActivity.Main instead.");
    return -1;
}
