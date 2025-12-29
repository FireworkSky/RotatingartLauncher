/*
 * Unified logging system for RALaunch native code
 * Features:
 * - Simplified output (no emojis/graphics)
 * - Disabled debug logs in release builds
 * - Consistent format
 */

#ifndef APP_LOG_H
#define APP_LOG_H

#include <android/log.h>

// App name for all logs
#define APP_TAG "RALaunch"

// Debug logs are disabled in release builds
#ifdef NDEBUG
#define ENABLE_DEBUG_LOGS 0
#else
#define ENABLE_DEBUG_LOGS 1
#endif

// Logging macros with simplified output
#define LOGE(tag, ...) __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/" tag, __VA_ARGS__)
#define LOGW(tag, ...) __android_log_print(ANDROID_LOG_WARN, APP_TAG "/" tag, __VA_ARGS__)
#define LOGI(tag, ...) __android_log_print(ANDROID_LOG_INFO, APP_TAG "/" tag, __VA_ARGS__)

#if ENABLE_DEBUG_LOGS
#define LOGD(tag, ...) __android_log_print(ANDROID_LOG_DEBUG, APP_TAG "/" tag, __VA_ARGS__)
#else
#define LOGD(tag, ...) ((void)0)  // No-op in release builds
#endif

#define LOG_TAG(tag_name) static const char* LOG_TAG_NAME = tag_name



#endif
