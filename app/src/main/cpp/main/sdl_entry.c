

#include <jni.h>
#include <android/log.h>
#include "jni_bridge.h"
#include "app_logger.h"
#include "osmesa/osm_renderer.h"


#define LOG_TAG "GameLauncher"
#define LOGI(...) app_logger_log(LOG_LEVEL_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) app_logger_log(LOG_LEVEL_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) app_logger_log(LOG_LEVEL_ERROR, LOG_TAG, __VA_ARGS__)



JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;

    return Bridge_JNI_OnLoad(vm);
}

/**
 * @brief JNI_OnUnload 回调

 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)reserved;
    Bridge_JNI_OnUnload(vm);
}

/**
 * @brief SDL 主函数入口点
 */
__attribute__((visibility("default"))) int SDL_main(int argc, char* argv[]) {
//    (void)argc;
//    (void)argv;
//
//    LOGI("SDL_main started (using netcorehost API)");
//    int result = netcorehost_launch();
//
//    LOGI(".NET execution finished with result: %d", result);
//
//    const char* error_message = netcorehost_get_last_error();
//
//    netcorehost_cleanup();
//    Bridge_NotifyGameExitWithMessage(result, error_message);
//
//    return result;

    // Obsolete function
    LOGE(LOG_TAG, "SDL_main is obsolete. Use GameActivity.Main instead.");
    return -1;
}


