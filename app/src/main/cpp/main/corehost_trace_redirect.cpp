#include <android/log.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdarg>
#include <dlfcn.h>
#include "And64InlineHook/And64InlineHook.hpp"

#define LOG_TAG "COREHOST_TRACE"

// 原始函数指针
static int (*original_vfprintf)(FILE* stream, const char* format, va_list ap) = nullptr;
static int (*original_fputc)(int c, FILE* stream) = nullptr;

// 用于累积 trace 输出的缓冲区
static __thread char trace_buffer[4096] = {0};
static __thread size_t trace_buffer_len = 0;

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

// 初始化trace重定向
extern "C" void init_corehost_trace_redirect() {
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