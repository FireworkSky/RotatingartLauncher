#include "app_log.h"

#include <android/log.h>
#include <jni.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

#include "../jni_entry.h"

#define APP_LOG_CLASS_NAME "com/app/ralaunch/core/logging/AppLog"
#define APP_LOG_METHOD_SIGNATURE "(Ljava/lang/String;Ljava/lang/String;)I"
#define APP_LOG_FORMAT_BUFFER_SIZE 1024

static jclass g_app_log_class = NULL;
static jmethodID g_app_log_v = NULL;
static jmethodID g_app_log_d = NULL;
static jmethodID g_app_log_i = NULL;
static jmethodID g_app_log_w = NULL;
static jmethodID g_app_log_e = NULL;

static int app_log_android_priority(const char *method_name) {
    switch (method_name[0]) {
        case 'v':
            return ANDROID_LOG_VERBOSE;
        case 'd':
            return ANDROID_LOG_DEBUG;
        case 'i':
            return ANDROID_LOG_INFO;
        case 'w':
            return ANDROID_LOG_WARN;
        case 'e':
            return ANDROID_LOG_ERROR;
        default:
            return ANDROID_LOG_INFO;
    }
}

static int app_log_fallback(const char *method_name, const char *tag, const char *message) {
    const char *safe_tag = tag != NULL ? tag : "";
    const char *safe_message = message != NULL ? message : "";
    return __android_log_print(app_log_android_priority(method_name), safe_tag, "%s", safe_message);
}

static int app_log_cache_methods(JNIEnv *env, jclass app_log_class) {
    g_app_log_v = (*env)->GetStaticMethodID(env, app_log_class, "v", APP_LOG_METHOD_SIGNATURE);
    g_app_log_d = (*env)->GetStaticMethodID(env, app_log_class, "d", APP_LOG_METHOD_SIGNATURE);
    g_app_log_i = (*env)->GetStaticMethodID(env, app_log_class, "i", APP_LOG_METHOD_SIGNATURE);
    g_app_log_w = (*env)->GetStaticMethodID(env, app_log_class, "w", APP_LOG_METHOD_SIGNATURE);
    g_app_log_e = (*env)->GetStaticMethodID(env, app_log_class, "e", APP_LOG_METHOD_SIGNATURE);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }

    return g_app_log_v != NULL &&
           g_app_log_d != NULL &&
           g_app_log_i != NULL &&
           g_app_log_w != NULL &&
           g_app_log_e != NULL;
}

void app_log_init(JNIEnv *env) {
    if (env == NULL || g_app_log_class != NULL) {
        return;
    }

    jclass local_class = (*env)->FindClass(env, APP_LOG_CLASS_NAME);
    if (local_class == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return;
    }

    if (!app_log_cache_methods(env, local_class)) {
        app_log_shutdown(env);
        (*env)->DeleteLocalRef(env, local_class);
        return;
    }

    g_app_log_class = (jclass)(*env)->NewGlobalRef(env, local_class);
    (*env)->DeleteLocalRef(env, local_class);
    if (g_app_log_class == NULL && (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
    if (g_app_log_class == NULL) {
        app_log_shutdown(env);
    }
}

void app_log_shutdown(JNIEnv *env) {
    if (env != NULL && g_app_log_class != NULL) {
        (*env)->DeleteGlobalRef(env, g_app_log_class);
    }
    g_app_log_class = NULL;
    g_app_log_v = NULL;
    g_app_log_d = NULL;
    g_app_log_i = NULL;
    g_app_log_w = NULL;
    g_app_log_e = NULL;
}

static jmethodID app_log_method_id(const char *method_name) {
    switch (method_name[0]) {
        case 'v':
            return g_app_log_v;
        case 'd':
            return g_app_log_d;
        case 'i':
            return g_app_log_i;
        case 'w':
            return g_app_log_w;
        case 'e':
            return g_app_log_e;
        default:
            return NULL;
    }
}

static void app_log_try_lazy_init(JNIEnv *env) {
    if (g_app_log_class != NULL) {
        return;
    }

    jclass app_log_class = (*env)->FindClass(env, APP_LOG_CLASS_NAME);
    if (app_log_class == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return;
    }

    if (app_log_cache_methods(env, app_log_class)) {
        g_app_log_class = (jclass)(*env)->NewGlobalRef(env, app_log_class);
        if (g_app_log_class == NULL && (*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (g_app_log_class == NULL) {
            app_log_shutdown(env);
        }
    } else {
        app_log_shutdown(env);
    }
    (*env)->DeleteLocalRef(env, app_log_class);
}

static int app_log_call(const char *method_name, const char *tag, const char *message) {
    const char *safe_tag = tag != NULL ? tag : "";
    const char *safe_message = message != NULL ? message : "";

    JNIEnv *env = JniEntry_GetEnv();
    if (env == NULL) {
        return app_log_fallback(method_name, safe_tag, safe_message);
    }

    app_log_try_lazy_init(env);
    jmethodID log_method = app_log_method_id(method_name);
    if (g_app_log_class == NULL || log_method == NULL) {
        return app_log_fallback(method_name, safe_tag, safe_message);
    }

    jstring j_tag = (*env)->NewStringUTF(env, safe_tag);
    if (j_tag == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return app_log_fallback(method_name, safe_tag, safe_message);
    }

    jstring j_message = (*env)->NewStringUTF(env, safe_message);
    if (j_message == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        (*env)->DeleteLocalRef(env, j_tag);
        return app_log_fallback(method_name, safe_tag, safe_message);
    }

    jint result = (*env)->CallStaticIntMethod(env, g_app_log_class, log_method, j_tag, j_message);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        result = app_log_fallback(method_name, safe_tag, safe_message);
    }

    (*env)->DeleteLocalRef(env, j_message);
    (*env)->DeleteLocalRef(env, j_tag);
    return result;
}

static int app_log_vformat(const char *method_name, const char *tag, const char *format, va_list args) {
    if (format == NULL) {
        return app_log_call(method_name, tag, "");
    }

    char stack_buffer[APP_LOG_FORMAT_BUFFER_SIZE];
    va_list args_copy;
    va_copy(args_copy, args);
    int required = vsnprintf(stack_buffer, sizeof(stack_buffer), format, args_copy);
    va_end(args_copy);

    if (required < 0) {
        return app_log_call(method_name, tag, "Failed to format native log message");
    }

    if ((size_t)required < sizeof(stack_buffer)) {
        return app_log_call(method_name, tag, stack_buffer);
    }

    size_t buffer_size = (size_t)required + 1;
    char *heap_buffer = (char *)malloc(buffer_size);
    if (heap_buffer == NULL) {
        return app_log_call(method_name, tag, "Failed to allocate native log message buffer");
    }

    int result = vsnprintf(heap_buffer, buffer_size, format, args);
    if (result < 0) {
        free(heap_buffer);
        return app_log_call(method_name, tag, "Failed to format native log message");
    }

    result = app_log_call(method_name, tag, heap_buffer);
    free(heap_buffer);
    return result;
}

int app_log_v(const char *tag, const char *message) {
    return app_log_call("v", tag, message);
}

int app_log_d(const char *tag, const char *message) {
    return app_log_call("d", tag, message);
}

int app_log_i(const char *tag, const char *message) {
    return app_log_call("i", tag, message);
}

int app_log_w(const char *tag, const char *message) {
    return app_log_call("w", tag, message);
}

int app_log_e(const char *tag, const char *message) {
    return app_log_call("e", tag, message);
}

int app_log_vf(const char *tag, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int result = app_log_vformat("v", tag, format, args);
    va_end(args);
    return result;
}

int app_log_df(const char *tag, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int result = app_log_vformat("d", tag, format, args);
    va_end(args);
    return result;
}

int app_log_if(const char *tag, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int result = app_log_vformat("i", tag, format, args);
    va_end(args);
    return result;
}

int app_log_wf(const char *tag, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int result = app_log_vformat("w", tag, format, args);
    va_end(args);
    return result;
}

int app_log_ef(const char *tag, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int result = app_log_vformat("e", tag, format, args);
    va_end(args);
    return result;
}
