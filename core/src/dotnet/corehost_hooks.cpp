#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdarg>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <sched.h>
#include <unistd.h>
#include <time.h>
#include <atomic>
#include <jni.h>

#include "dobby.h"
#include "logger.hpp"

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
static int (*original_sched_getaffinity)(pid_t pid, size_t cpusetsize, cpu_set_t* mask) = nullptr;
static int (*original_sched_setaffinity)(pid_t pid, size_t cpusetsize, const cpu_set_t* mask) = nullptr;

static std::atomic_bool g_compat_hooks_installed = false;

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
        LOGI("{}", trace_buffer);
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
    if (rc == 0) {
        return rc;
    }

    if (clock_id == CLOCK_MONOTONIC) {
        int fallback = original_pthread_condattr_setclock(attr, CLOCK_REALTIME);
        if (fallback == 0) {
            LOGW(
                    "Compat: pthread_condattr_setclock(CLOCK_MONOTONIC) failed rc={}, fallback to CLOCK_REALTIME",
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
    if (rc == 0) {
        return rc;
    }

    constexpr size_t kCompatStackSize = 1024 * 1024; // 1 MiB
    if (stack_size != kCompatStackSize) {
        int fallback = original_pthread_attr_setstacksize(attr, kCompatStackSize);
        if (fallback == 0) {
            LOGW(
                    "Compat: pthread_attr_setstacksize({}) failed rc={}, fallback to {}",
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
    if (rc == 0) {
        return rc;
    }

    // EAGAIN on a few MIUI builds is transient during burst startup.
    if (rc == EAGAIN) {
        usleep(2000);
        int retry = original_pthread_create(thread, attr, start_routine, arg);
        if (retry == 0) {
            LOGW(
                "Compat: pthread_create EAGAIN recovered by one retry"
            );
            return 0;
        }
    }

    return rc;
}

static int hooked_sched_getaffinity(pid_t pid, size_t cpusetsize, cpu_set_t* mask) {
    if (original_sched_getaffinity == nullptr) {
        errno = ENOSYS;
        return -1;
    }

    int rc = original_sched_getaffinity(pid, cpusetsize, mask);
    if (rc == 0) {
        return rc;
    }

    const int saved_errno = errno;
    if ((pid == 0 || pid == getpid()) && mask != nullptr && cpusetsize > 0) {
        CPU_ZERO(mask);
        long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
        if (cpu_count <= 0) {
            cpu_count = 1;
        }
        const size_t max_cpus = cpusetsize * 8;
        const size_t usable_cpus = static_cast<size_t>(cpu_count) < max_cpus
            ? static_cast<size_t>(cpu_count)
            : max_cpus;
        for (size_t i = 0; i < usable_cpus; ++i) {
            CPU_SET(i, mask);
        }
        LOGW(
                "Compat: sched_getaffinity(pid={}) failed errno={}, fallback to synthetic cpu mask ({} cpus)",
            static_cast<int>(pid),
            saved_errno,
            usable_cpus
        );
        errno = 0;
        return 0;
    }

    errno = saved_errno;
    return rc;
}

static int hooked_sched_setaffinity(pid_t pid, size_t cpusetsize, const cpu_set_t* mask) {
    if (original_sched_setaffinity == nullptr) {
        errno = ENOSYS;
        return -1;
    }

    int rc = original_sched_setaffinity(pid, cpusetsize, mask);
    if (rc == 0) {
        return rc;
    }

    const int saved_errno = errno;
    if (pid == 0 || pid == getpid()) {
        if (saved_errno == EPERM || saved_errno == EACCES || saved_errno == EINVAL || saved_errno == ENOSYS) {
            LOGW(

                    "Compat: sched_setaffinity(pid={}) failed errno={}, treating as non-fatal",
                static_cast<int>(pid),
                saved_errno
            );
            errno = 0;
            return 0;
        }
    }

    errno = saved_errno;
    return rc;
}

extern "C" void init_corehost_compat_hooks() {
    if (g_compat_hooks_installed.exchange(true)) {
        return;
    }

    void* libc = dlopen("libc.so", RTLD_NOW);
    if (!libc) {
        g_compat_hooks_installed.store(false);
        LOGE("Compat: failed to open libc.so: {}", dlerror());
        return;
    }

    void* pthread_condattr_setclock_addr = dlsym(libc, "pthread_condattr_setclock");
    if (pthread_condattr_setclock_addr) {
        DobbyHook(
            pthread_condattr_setclock_addr,
            (void*)hooked_pthread_condattr_setclock,
            (void**)&original_pthread_condattr_setclock
        );
        LOGI("Compat: hooked pthread_condattr_setclock");
    } else {
        LOGW("Compat: symbol pthread_condattr_setclock not found");
    }

    void* pthread_attr_setstacksize_addr = dlsym(libc, "pthread_attr_setstacksize");
    if (pthread_attr_setstacksize_addr) {
        DobbyHook(
            pthread_attr_setstacksize_addr,
            (void*)hooked_pthread_attr_setstacksize,
            (void**)&original_pthread_attr_setstacksize
        );
        LOGI("Compat: hooked pthread_attr_setstacksize");
    } else {
        LOGW("Compat: symbol pthread_attr_setstacksize not found");
    }

    void* pthread_create_addr = dlsym(libc, "pthread_create");
    if (pthread_create_addr) {
        DobbyHook(
            pthread_create_addr,
            (void*)hooked_pthread_create,
            (void**)&original_pthread_create
        );
        LOGI("Compat: hooked pthread_create");
    } else {
        LOGW("Compat: symbol pthread_create not found");
    }

    void* sched_getaffinity_addr = dlsym(libc, "sched_getaffinity");
    if (sched_getaffinity_addr) {
        DobbyHook(
            sched_getaffinity_addr,
            (void*)hooked_sched_getaffinity,
            (void**)&original_sched_getaffinity
        );
        LOGI("Compat: hooked sched_getaffinity");
    } else {
        LOGW("Compat: symbol sched_getaffinity not found");
    }

    void* sched_setaffinity_addr = dlsym(libc, "sched_setaffinity");
    if (sched_setaffinity_addr) {
        DobbyHook(
            sched_setaffinity_addr,
            (void*)hooked_sched_setaffinity,
            (void**)&original_sched_setaffinity
        );
        LOGI("Compat: hooked sched_setaffinity");
    } else {
        LOGW("Compat: symbol sched_setaffinity not found");
    }

    dlclose(libc);
}

// 初始化trace重定向
extern "C" void init_corehost_trace_hooks() {
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (!libc) {
        LOGE("Failed to open libc.so: {}", dlerror());
        return;
    }

    // Hook vfprintf
    void* vfprintf_addr = dlsym(libc, "vfprintf");
    if (vfprintf_addr) {
        DobbyHook(vfprintf_addr, (void *) hooked_vfprintf, (void **) &original_vfprintf);
        LOGI("Hooked vfprintf at: {}", vfprintf_addr);
    } else {
        LOGE("Failed to find vfprintf: {}", dlerror());
    }

    // Hook fputc
    void* fputc_addr = dlsym(libc, "fputc");
    if (fputc_addr) {
        DobbyHook(fputc_addr, (void *) hooked_fputc, (void **) &original_fputc);
        LOGI("Hooked fputc at: {}", fputc_addr);
    } else {
        LOGE("Failed to find fputc: {}", dlerror());
    }

    dlclose(libc);
    LOGI("COREHOST_TRACE redirect initialization complete");
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
