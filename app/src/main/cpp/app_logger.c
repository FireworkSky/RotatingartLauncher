/*
 * RALaunch Native Logger Implementation
 */

#include "app_logger.h"
#include <stdarg.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/stat.h>
#include <errno.h>

// Configuration
#define MAX_LOG_LINE 2048
#define MAX_PATH 512
#define LOG_FILE_PREFIX "ralaunch_native_"
#define LOG_RETENTION_DAYS 7

// Global state
static FILE* g_log_file = NULL;
static pthread_mutex_t g_log_mutex = PTHREAD_MUTEX_INITIALIZER;
static char g_log_dir[MAX_PATH] = {0};
static int g_initialized = 0;

// Level names
static const char* level_names[] = {
    "E", // ERROR
    "W", // WARN
    "I", // INFO
    "D"  // DEBUG
};

// Android log priorities
static const android_LogPriority android_priorities[] = {
    ANDROID_LOG_ERROR,
    ANDROID_LOG_WARN,
    ANDROID_LOG_INFO,
    ANDROID_LOG_DEBUG
};

// Get current date string for log file name
static void get_date_string(char* buf, size_t size) {
    time_t now = time(NULL);
    struct tm* tm_info = localtime(&now);
    strftime(buf, size, "%Y-%m-%d", tm_info);
}

// Get current timestamp string for log entries
static void get_timestamp_string(char* buf, size_t size) {
    time_t now = time(NULL);
    struct tm* tm_info = localtime(&now);
    strftime(buf, size, "%Y-%m-%d %H:%M:%S", tm_info);

    // Add milliseconds
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    int ms = ts.tv_nsec / 1000000;

    size_t len = strlen(buf);
    snprintf(buf + len, size - len, ".%03d", ms);
}

// Remove emojis and special characters
static void strip_emojis(char* text) {
    if (!text) return;

    // Simple ASCII cleanup - remove non-printable characters except newlines
    char* src = text;
    char* dst = text;

    while (*src) {
        if ((*src >= 32 && *src <= 126) || *src == '\n' || *src == '\t') {
            *dst++ = *src;
        }
        src++;
    }
    *dst = '\0';
}

// Rotate old log files
static void rotate_old_logs(void) {
    // Not implemented yet - would need dirent.h
    // For now, just rely on manual cleanup
}

// Open log file for current date
static int open_log_file(void) {
    if (!g_initialized || g_log_dir[0] == '\0') {
        return 0;
    }

    // Close existing file if open
    if (g_log_file) {
        fclose(g_log_file);
        g_log_file = NULL;
    }

    // Build log file path
    char date_str[32];
    char log_path[MAX_PATH];

    get_date_string(date_str, sizeof(date_str));
    snprintf(log_path, sizeof(log_path), "%s/%s%s.log",
             g_log_dir, LOG_FILE_PREFIX, date_str);

    // Open file in append mode
    g_log_file = fopen(log_path, "a");
    if (!g_log_file) {
        __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/Logger",
                          "Failed to open log file: %s (errno=%d)", log_path, errno);
        return 0;
    }

    // Make file unbuffered for immediate writes
    setvbuf(g_log_file, NULL, _IONBF, 0);

    return 1;
}

// Initialize logger
void app_logger_init(const char* log_dir) {
    pthread_mutex_lock(&g_log_mutex);

    if (g_initialized) {
        pthread_mutex_unlock(&g_log_mutex);
        return;
    }

    if (!log_dir || strlen(log_dir) == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/Logger",
                          "Invalid log directory");
        pthread_mutex_unlock(&g_log_mutex);
        return;
    }

    // Create log directory if it doesn't exist
    mkdir(log_dir, 0755);

    // Store log directory
    strncpy(g_log_dir, log_dir, sizeof(g_log_dir) - 1);
    g_log_dir[sizeof(g_log_dir) - 1] = '\0';

    // Open log file
    if (!open_log_file()) {
        __android_log_print(ANDROID_LOG_WARN, APP_TAG "/Logger",
                          "File logging disabled (failed to open file)");
    }

    g_initialized = 1;

    pthread_mutex_unlock(&g_log_mutex);

    // Log initialization
    app_logger_log(LOG_LEVEL_INFO, "Logger", "Native logger initialized: %s", log_dir);
}

// Close logger
void app_logger_close(void) {
    pthread_mutex_lock(&g_log_mutex);

    if (!g_initialized) {
        pthread_mutex_unlock(&g_log_mutex);
        return;
    }

    if (g_log_file) {
        fflush(g_log_file);
        fclose(g_log_file);
        g_log_file = NULL;
    }

    g_initialized = 0;
    g_log_dir[0] = '\0';

    pthread_mutex_unlock(&g_log_mutex);
}

// Main log function
void app_logger_log(LogLevel level, const char* tag, const char* fmt, ...) {
    if (!tag || !fmt) return;

    // Format message
    char message[MAX_LOG_LINE];
    va_list args;
    va_start(args, fmt);
    vsnprintf(message, sizeof(message), fmt, args);
    va_end(args);

    // Strip emojis
    strip_emojis(message);

    // Always log to logcat (use tag directly without prefix)
    __android_log_print(android_priorities[level], tag, "%s", message);

    // Log to file if initialized
    pthread_mutex_lock(&g_log_mutex);

    if (g_initialized && g_log_file) {
        char timestamp[64];
        get_timestamp_string(timestamp, sizeof(timestamp));

        fprintf(g_log_file, "[%s] %s/%s: %s\n",
                timestamp, level_names[level], tag, message);
        fflush(g_log_file);
    }

    pthread_mutex_unlock(&g_log_mutex);
}
