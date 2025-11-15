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
#include <jni.h>

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

// JVM for error dialog callbacks
static JavaVM* g_jvm = NULL;
static jclass g_error_handler_class = NULL;
static jmethodID g_show_error_method = NULL;

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

    char* src = text;
    char* dst = text;

    while (*src) {
        char c = *src;

        // Keep only basic printable ASCII and common whitespace
        if ((c >= 32 && c <= 126) || c == '\n' || c == '\r' || c == '\t') {
            *dst++ = c;
        }
        // Skip all other characters (emojis, special symbols, etc.)

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

// Initialize JVM for error dialogs
void app_logger_init_jvm(JavaVM* vm) {
    if (!vm) {
        __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/Logger",
                          "Cannot initialize JVM: vm is NULL");
        return;
    }

    g_jvm = vm;

    JNIEnv* env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/Logger",
                          "Failed to get JNI environment");
        g_jvm = NULL;
        return;
    }

    // Find ErrorHandler class
    jclass local_class = (*env)->FindClass(env, "com/app/ralib/error/ErrorHandler");
    if (!local_class) {
        __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/Logger",
                          "Failed to find ErrorHandler class");
        (*env)->ExceptionClear(env);
        g_jvm = NULL;
        return;
    }

    // Create global reference
    g_error_handler_class = (*env)->NewGlobalRef(env, local_class);
    (*env)->DeleteLocalRef(env, local_class);

    if (!g_error_handler_class) {
        __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/Logger",
                          "Failed to create global reference to ErrorHandler");
        g_jvm = NULL;
        return;
    }

    // Find showNativeError method
    g_show_error_method = (*env)->GetStaticMethodID(env, g_error_handler_class,
                                                     "showNativeError",
                                                     "(Ljava/lang/String;Ljava/lang/String;Z)V");
    if (!g_show_error_method) {
        __android_log_print(ANDROID_LOG_ERROR, APP_TAG "/Logger",
                          "Failed to find showNativeError method");
        (*env)->ExceptionClear(env);
        (*env)->DeleteGlobalRef(env, g_error_handler_class);
        g_error_handler_class = NULL;
        g_jvm = NULL;
        return;
    }

    __android_log_print(ANDROID_LOG_INFO, APP_TAG "/Logger",
                      "JVM initialized for error dialogs");
}

// Show error dialog from native code
void app_logger_show_error(const char* title, const char* message, int is_fatal) {
    if (!g_jvm || !g_error_handler_class || !g_show_error_method) {
        LOGE("Logger", "Cannot show error dialog: JVM not initialized");
        LOGE("ErrorDialog", "%s: %s", title, message);
        return;
    }

    // Log the error
    LOGE("ErrorDialog", "%s: %s (fatal=%d)", title, message, is_fatal);

    JNIEnv* env = NULL;
    int need_detach = 0;

    // Get JNI environment
    int get_env_result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    if (get_env_result == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("Logger", "Failed to attach current thread");
            return;
        }
        need_detach = 1;
    } else if (get_env_result != JNI_OK) {
        LOGE("Logger", "Failed to get JNI environment: %d", get_env_result);
        return;
    }

    // Create Java strings
    jstring j_title = (*env)->NewStringUTF(env, title ? title : "Error");
    jstring j_message = (*env)->NewStringUTF(env, message ? message : "Unknown error");
    jboolean j_is_fatal = (jboolean)(is_fatal ? JNI_TRUE : JNI_FALSE);

    if (!j_title || !j_message) {
        LOGE("Logger", "Failed to create Java strings");
        if (j_title) (*env)->DeleteLocalRef(env, j_title);
        if (j_message) (*env)->DeleteLocalRef(env, j_message);
        if (need_detach) (*g_jvm)->DetachCurrentThread(g_jvm);
        return;
    }

    // Call Java method
    (*env)->CallStaticVoidMethod(env, g_error_handler_class, g_show_error_method,
                                  j_title, j_message, j_is_fatal);

    // Check for exceptions
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Logger", "Exception occurred while showing error dialog");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    // Cleanup
    (*env)->DeleteLocalRef(env, j_title);
    (*env)->DeleteLocalRef(env, j_message);

    if (need_detach) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }
}
