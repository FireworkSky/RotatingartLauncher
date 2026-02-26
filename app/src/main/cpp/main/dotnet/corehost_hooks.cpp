#include <android/log.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdarg>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <unistd.h>
#include <time.h>
#include <atomic>
#include "And64InlineHook/And64InlineHook.hpp"
#include <jni.h>

#define LOG_TAG "COREHOST_TRACE"

// 原始函数指针
static int (*original_vfprintf)(FILE* stream, const char* format, va_list ap) = nullptr;
static int (*original_fputc)(int c, FILE* stream) = nullptr;
static int (*original_pthread_condattr_setclock)(pthread_condattr_t* attr, clockid_t clock_id) = nullptr;
static int (*original_pthread_attr_setstacksize)(pthread_attr_t* attr, size_t stack_size) = nullptr;
static int (*original_pthread_create)(
    pthread_t* thread,
    const pthread_attr_t* attr,
    void* (*start_routine)(void*),
    void* arg
) = nullptr;

static std::atomic_bool g_compat_hooks_installed = false;

// 用于累积 trace 输出的缓冲区
static __thread char trace_buffer[4096] = {0};
static __thread size_t trace_buffer_len = 0;

static bool is_xiaomi_compat_enabled() {
    const char* enabled = getenv("RAL_CORECLR_XIAOMI_COMPAT");
    return enabled != nullptr && strcmp(enabled, "1") == 0;
}

// Hook后的vfprintf函数
static int hooked_vfprintf(FILE* stream, const char* format, va_list ap) {
    // 先调用原始函数
    va_list ap_copy;
    va_copy(ap_copy, ap);
    int result = original_vfprintf(stream, format, ap);

    // 将输出也发送到 logcat
    if (stream && format) {
        char buffer[2048];
        int len = vsnprintf(buffer, sizeof(buffer) - 1, format, ap_copy);
        if (len > 0 && len < (int)sizeof(buffer)) {
            buffer[len] = '\0';

            // 累积到 trace_buffer
            if (trace_buffer_len + len < sizeof(trace_buffer) - 1) {
                memcpy(trace_buffer + trace_buffer_len, buffer, len);
                trace_buffer_len += len;
            }
        }
    }
    va_end(ap_copy);

    return result;
}

// Hook后的fputc函数
static int hooked_fputc(int c, FILE* stream) {
    // 先调用原始函数
    int result = original_fputc(c, stream);

    // 如果是换行符,输出累积的内容到 logcat
    if (c == '\n' && trace_buffer_len > 0) {
        trace_buffer[trace_buffer_len] = '\0';
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", trace_buffer);
        trace_buffer_len = 0;
    } else if (c != '\n' && trace_buffer_len < sizeof(trace_buffer) - 1) {
        // 累积字符
        trace_buffer[trace_buffer_len++] = (char)c;
    }

    return result;
}

static int hooked_pthread_condattr_setclock(pthread_condattr_t* attr, clockid_t clock_id) {
    if (original_pthread_condattr_setclock == nullptr) {
        return EINVAL;
    }

    int rc = original_pthread_condattr_setclock(attr, clock_id);
    if (rc == 0 || !is_xiaomi_compat_enabled()) {
        return rc;
    }

    if (clock_id == CLOCK_MONOTONIC) {
        int fallback = original_pthread_condattr_setclock(attr, CLOCK_REALTIME);
        if (fallback == 0) {
            __android_log_print(
                ANDROID_LOG_WARN,
                LOG_TAG,
                "Compat: pthread_condattr_setclock(CLOCK_MONOTONIC) failed rc=%d, fallback to CLOCK_REALTIME",
                rc
            );
            return 0;
        }
    }

    return rc;
}

static int hooked_pthread_attr_setstacksize(pthread_attr_t* attr, size_t stack_size) {
    if (original_pthread_attr_setstacksize == nullptr) {
        return EINVAL;
    }

    int rc = original_pthread_attr_setstacksize(attr, stack_size);
    if (rc == 0 || !is_xiaomi_compat_enabled()) {
        return rc;
    }

    constexpr size_t kCompatStackSize = 1024 * 1024; // 1 MiB
    if (stack_size != kCompatStackSize) {
        int fallback = original_pthread_attr_setstacksize(attr, kCompatStackSize);
        if (fallback == 0) {
            __android_log_print(
                ANDROID_LOG_WARN,
                LOG_TAG,
                "Compat: pthread_attr_setstacksize(%zu) failed rc=%d, fallback to %zu",
                stack_size,
                rc,
                kCompatStackSize
            );
            return 0;
        }
    }

    return rc;
}

static int hooked_pthread_create(
    pthread_t* thread,
    const pthread_attr_t* attr,
    void* (*start_routine)(void*),
    void* arg
) {
    if (original_pthread_create == nullptr) {
        return EAGAIN;
    }

    int rc = original_pthread_create(thread, attr, start_routine, arg);
    if (rc == 0 || !is_xiaomi_compat_enabled()) {
        return rc;
    }

    // EAGAIN on a few MIUI builds is transient during burst startup.
    if (rc == EAGAIN) {
        usleep(2000);
        int retry = original_pthread_create(thread, attr, start_routine, arg);
        if (retry == 0) {
            __android_log_print(
                ANDROID_LOG_WARN,
                LOG_TAG,
                "Compat: pthread_create EAGAIN recovered by one retry"
            );
            return 0;
        }
    }

    return rc;
}

extern "C" void init_corehost_compat_hooks() {
    if (g_compat_hooks_installed.exchange(true)) {
        return;
    }

    void* libc = dlopen("libc.so", RTLD_NOW);
    if (!libc) {
        g_compat_hooks_installed.store(false);
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Compat: failed to open libc.so: %s", dlerror());
        return;
    }

    void* pthread_condattr_setclock_addr = dlsym(libc, "pthread_condattr_setclock");
    if (pthread_condattr_setclock_addr) {
        A64HookFunction(
            pthread_condattr_setclock_addr,
            (void*)hooked_pthread_condattr_setclock,
            (void**)&original_pthread_condattr_setclock
        );
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Compat: hooked pthread_condattr_setclock");
    } else {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Compat: symbol pthread_condattr_setclock not found");
    }

    void* pthread_attr_setstacksize_addr = dlsym(libc, "pthread_attr_setstacksize");
    if (pthread_attr_setstacksize_addr) {
        A64HookFunction(
            pthread_attr_setstacksize_addr,
            (void*)hooked_pthread_attr_setstacksize,
            (void**)&original_pthread_attr_setstacksize
        );
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Compat: hooked pthread_attr_setstacksize");
    } else {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Compat: symbol pthread_attr_setstacksize not found");
    }

    void* pthread_create_addr = dlsym(libc, "pthread_create");
    if (pthread_create_addr) {
        A64HookFunction(
            pthread_create_addr,
            (void*)hooked_pthread_create,
            (void**)&original_pthread_create
        );
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Compat: hooked pthread_create");
    } else {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Compat: symbol pthread_create not found");
    }

    dlclose(libc);
}

// 初始化trace重定向
extern "C" void init_corehost_trace_hooks() {
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (!libc) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to open libc.so: %s", dlerror());
        return;
    }

    // Hook vfprintf
    void* vfprintf_addr = dlsym(libc, "vfprintf");
    if (vfprintf_addr) {
        A64HookFunction(vfprintf_addr, (void*)hooked_vfprintf, (void**)&original_vfprintf);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Hooked vfprintf at: %p", vfprintf_addr);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to find vfprintf: %s", dlerror());
    }

    // Hook fputc
    void* fputc_addr = dlsym(libc, "fputc");
    if (fputc_addr) {
        A64HookFunction(fputc_addr, (void*)hooked_fputc, (void**)&original_fputc);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Hooked fputc at: %p", fputc_addr);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to find fputc: %s", dlerror());
    }

    dlclose(libc);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "COREHOST_TRACE redirect initialization complete");
}

// JNI接口函数
extern "C"
JNIEXPORT void JNICALL
Java_com_app_ralaunch_core_platform_runtime_dotnet_CoreHostHooks_nativeInitCoreHostTraceHooks(JNIEnv *env,
                                                                        jobject thiz) {
    init_corehost_trace_hooks();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_app_ralaunch_core_platform_runtime_dotnet_CoreHostHooks_nativeInitCoreHostCompatHooks(JNIEnv *env,
                                                                         jobject thiz) {
    init_corehost_compat_hooks();
}
