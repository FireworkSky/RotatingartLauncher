/**
 * @file framework_utils.cpp
 * @brief 框架工具实现（保留以兼容旧代码，但不再使用）
 */

#include "framework_utils.h"
#include <android/log.h>

#define LOG_TAG "FrameworkUtils"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" int framework_pick_version(
    const char* dotnet_root,
    int preferred_major_version,
    char* out_version,
    size_t out_size) {
    LOGW("framework_pick_version is deprecated and not used");
    return -1;
}

extern "C" int framework_generate_runtimeconfig(
    const char* app_dir,
    const char* assembly_name,
    const char* framework_version) {
    LOGW("framework_generate_runtimeconfig is deprecated and not used");
    return -1;
}





