/*
 * RALaunch Native Logger
 * Features:
 * - Logs to both file and logcat
 * - Thread-safe
 * - Automatic log rotation
 * - Simplified output (no emojis)
 */

#ifndef APP_LOGGER_H
#define APP_LOGGER_H

#include <android/log.h>
#include <stdio.h>
#include <pthread.h>
#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

// Log levels
typedef enum {
    LOG_LEVEL_ERROR = 0,
    LOG_LEVEL_WARN = 1,
    LOG_LEVEL_INFO = 2,
    LOG_LEVEL_DEBUG = 3
} LogLevel;

// Initialize native logger
// log_dir: directory path for log files (e.g., "/sdcard/Android/data/.../files/logs")
void app_logger_init(const char* log_dir);

// Close logger and flush buffers
void app_logger_close(void);

// Log functions
void app_logger_log(LogLevel level, const char* tag, const char* fmt, ...);

// Convenience macros
#define APP_TAG "RALaunch"

#define LOGE(tag, ...) app_logger_log(LOG_LEVEL_ERROR, tag, __VA_ARGS__)
#define LOGW(tag, ...) app_logger_log(LOG_LEVEL_WARN, tag, __VA_ARGS__)
#define LOGI(tag, ...) app_logger_log(LOG_LEVEL_INFO, tag, __VA_ARGS__)

// Debug logs are disabled in release builds
#ifdef NDEBUG
#define LOGD(tag, ...) ((void)0)
#else
#define LOGD(tag, ...) app_logger_log(LOG_LEVEL_DEBUG, tag, __VA_ARGS__)
#endif

// Helper macros
#define LOG_SUCCESS(tag, msg) LOGI(tag, "%s: OK", msg)
#define LOG_FAILURE(tag, msg) LOGE(tag, "%s: FAILED", msg)

#ifdef __cplusplus
}
#endif

#endif // APP_LOGGER_H
