/*
 * glibc-bridge - Memory Locking Wrappers
 * 
 * Handles mlock/munlock/mlockall/munlockall and membarrier for Android.
 * Android's seccomp may block mlock or RLIMIT_MEMLOCK may be 0.
 * These wrappers provide fallback behavior for CoreCLR GC initialization.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <android/log.h>

#define LOG_TAG "GLIBC_BRIDGE_MLOCK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/* ============================================================================
 * membarrier syscall support
 * 
 * CoreCLR's GC uses membarrier for FlushProcessWriteBuffers.
 * If membarrier works, mlock is not needed.
 * ============================================================================ */

#ifndef __NR_membarrier
#if defined(__aarch64__)
#define __NR_membarrier 283
#elif defined(__arm__)
#define __NR_membarrier 389
#else
#error "Unsupported architecture for __NR_membarrier (only ARM64/ARM32 supported)"
#endif
#endif

/* membarrier commands */
#define MEMBARRIER_CMD_QUERY                                 0
#define MEMBARRIER_CMD_GLOBAL                                (1 << 0)
#define MEMBARRIER_CMD_GLOBAL_EXPEDITED                      (1 << 1)
#define MEMBARRIER_CMD_REGISTER_GLOBAL_EXPEDITED             (1 << 2)
#define MEMBARRIER_CMD_PRIVATE_EXPEDITED                     (1 << 3)
#define MEMBARRIER_CMD_REGISTER_PRIVATE_EXPEDITED            (1 << 4)
#define MEMBARRIER_CMD_PRIVATE_EXPEDITED_SYNC_CORE           (1 << 5)
#define MEMBARRIER_CMD_REGISTER_PRIVATE_EXPEDITED_SYNC_CORE  (1 << 6)

static int g_membarrier_available = -1;  /* -1 = unknown, 0 = no, 1 = yes */

static int check_membarrier_available(void) {
    if (g_membarrier_available >= 0) {
        return g_membarrier_available;
    }
    
    /* Query membarrier support */
    int ret = syscall(__NR_membarrier, MEMBARRIER_CMD_QUERY, 0);
    if (ret < 0) {
        LOGW("membarrier QUERY failed: %s", strerror(errno));
        g_membarrier_available = 0;
        return 0;
    }
    
    /* Check if PRIVATE_EXPEDITED is supported */
    if (ret & MEMBARRIER_CMD_PRIVATE_EXPEDITED) {
        /* Register for private expedited */
        int reg_ret = syscall(__NR_membarrier, MEMBARRIER_CMD_REGISTER_PRIVATE_EXPEDITED, 0);
        if (reg_ret == 0) {
            LOGI("membarrier PRIVATE_EXPEDITED registered successfully");
            g_membarrier_available = 1;
            return 1;
        }
        LOGW("membarrier REGISTER_PRIVATE_EXPEDITED failed: %s", strerror(errno));
    }
    
    g_membarrier_available = 0;
    return 0;
}

int membarrier_wrapper(int cmd, unsigned int flags, int cpu_id) {
    (void)cpu_id;  /* cpu_id is for newer kernels, ignore for now */
    return syscall(__NR_membarrier, cmd, flags);
}

/* ============================================================================
 * mlock wrapper
 * 
 * On Android, mlock may fail due to:
 * 1. seccomp filter blocking the syscall
 * 2. RLIMIT_MEMLOCK being 0
 * 3. Insufficient permissions
 * 
 * CoreCLR only needs mlock for FlushProcessWriteBuffers fallback.
 * If membarrier is available, mlock failure is not critical.
 * ============================================================================ */

int mlock_wrapper(const void* addr, size_t len) {
    int ret = mlock(addr, len);
    
    if (ret == 0) {
        LOGD("mlock(%p, %zu) succeeded", addr, len);
        return 0;
    }
    
    int saved_errno = errno;
    LOGW("mlock(%p, %zu) failed: %s (errno=%d)", addr, len, strerror(saved_errno), saved_errno);
    
    /* Check if membarrier is available as alternative */
    if (check_membarrier_available()) {
        LOGI("mlock failed but membarrier is available - returning success for CoreCLR compatibility");
        /* Return success so CoreCLR's GC can initialize */
        /* The GC will use membarrier for FlushProcessWriteBuffers anyway */
        return 0;
    }
    
    /* Check RLIMIT_MEMLOCK */
    struct rlimit rlim;
    if (getrlimit(RLIMIT_MEMLOCK, &rlim) == 0) {
        LOGW("RLIMIT_MEMLOCK: soft=%lu, hard=%lu", 
             (unsigned long)rlim.rlim_cur, (unsigned long)rlim.rlim_max);
        
        /* If limit is 0, try to increase it */
        if (rlim.rlim_cur == 0 && rlim.rlim_max > 0) {
            rlim.rlim_cur = rlim.rlim_max;
            if (setrlimit(RLIMIT_MEMLOCK, &rlim) == 0) {
                LOGI("Increased RLIMIT_MEMLOCK to %lu, retrying mlock", (unsigned long)rlim.rlim_cur);
                ret = mlock(addr, len);
                if (ret == 0) {
                    return 0;
                }
            }
        }
    }
    
    /* For CoreCLR compatibility, return success anyway */
    /* The worst case is that FlushProcessWriteBuffers won't work optimally */
    /* but the GC should still function */
    LOGW("mlock failed but returning success for CoreCLR GC compatibility");
    LOGW("FlushProcessWriteBuffers may not work correctly");
    return 0;
}

int munlock_wrapper(const void* addr, size_t len) {
    int ret = munlock(addr, len);
    if (ret != 0) {
        /* munlock failure is not critical, log and continue */
        LOGD("munlock(%p, %zu) failed: %s", addr, len, strerror(errno));
        return 0;  /* Return success anyway */
    }
    return 0;
}

int mlockall_wrapper(int flags) {
    int ret = mlockall(flags);
    if (ret != 0) {
        LOGW("mlockall(%d) failed: %s", flags, strerror(errno));
        /* Check if membarrier is available */
        if (check_membarrier_available()) {
            return 0;  /* Return success for CoreCLR compatibility */
        }
        /* Return success anyway for best effort */
        return 0;
    }
    return 0;
}

int munlockall_wrapper(void) {
    int ret = munlockall();
    if (ret != 0) {
        LOGD("munlockall() failed: %s", strerror(errno));
        return 0;  /* Return success anyway */
    }
    return 0;
}

/* ============================================================================
 * madvise wrapper
 * 
 * Some madvise flags may not be available on older Android versions
 * ============================================================================ */

int madvise_wrapper(void* addr, size_t length, int advice) {
    int ret = madvise(addr, length, advice);
    if (ret != 0) {
        int saved_errno = errno;
        /* MADV_FREE and MADV_DONTNEED are important for GC */
        /* Log but don't fail for non-critical advice values */
        LOGD("madvise(%p, %zu, %d) failed: %s", addr, length, advice, strerror(saved_errno));
        
        /* Return the actual error for critical operations */
        errno = saved_errno;
    }
    return ret;
}

/* ============================================================================
 * sched_getaffinity wrapper
 * 
 * CoreCLR GC uses sched_getaffinity to get CPU affinity.
 * On Android, this should work but we add logging for debugging.
 * ============================================================================ */

#include <sched.h>

int sched_getaffinity_wrapper(pid_t pid, size_t cpusetsize, cpu_set_t* mask) {
    LOGD("sched_getaffinity(pid=%d, size=%zu, mask=%p)", pid, cpusetsize, mask);
    
    int ret = sched_getaffinity(pid, cpusetsize, mask);
    
    if (ret == 0) {
        LOGD("sched_getaffinity succeeded: %d CPUs in set", CPU_COUNT(mask));
    } else {
        int saved_errno = errno;
        LOGW("sched_getaffinity failed: %s (errno=%d)", strerror(saved_errno), saved_errno);
        
        /* On some Android devices, sched_getaffinity may fail for pid != 0 */
        /* Try with pid = 0 (current thread) */
        if (pid != 0) {
            LOGD("Retrying sched_getaffinity with pid=0");
            ret = sched_getaffinity(0, cpusetsize, mask);
            if (ret == 0) {
                LOGD("sched_getaffinity(0) succeeded: %d CPUs in set", CPU_COUNT(mask));
                return 0;
            }
        }
        
        /* If still failing, create a default mask with all available CPUs */
        long nprocs = sysconf(_SC_NPROCESSORS_ONLN);
        if (nprocs > 0) {
            LOGD("Creating default CPU mask with %ld processors", nprocs);
            CPU_ZERO(mask);
            for (long i = 0; i < nprocs && i < CPU_SETSIZE; i++) {
                CPU_SET(i, mask);
            }
            return 0;
        }
        
        errno = saved_errno;
    }
    
    return ret;
}

int sched_setaffinity_wrapper(pid_t pid, size_t cpusetsize, const cpu_set_t* mask) {
    LOGD("sched_setaffinity(pid=%d, size=%zu, mask=%p)", pid, cpusetsize, mask);
    
    int ret = sched_setaffinity(pid, cpusetsize, mask);
    
    if (ret != 0) {
        int saved_errno = errno;
        LOGW("sched_setaffinity failed: %s (errno=%d)", strerror(saved_errno), saved_errno);
        /* On Android, setting affinity may fail due to permissions */
        /* Return success anyway as it's not critical */
        LOGI("Returning success for sched_setaffinity despite failure");
        return 0;
    }
    
    return ret;
}

/* pthread_mutex_init_wrapper 定义在 wrapper_thread.c 中 */

/* ============================================================================
 * syscall wrapper
 * 
 * Intercept direct syscall() invocations to handle blocked syscalls.
 * CoreCLR may call syscall(__NR_membarrier, ...) or syscall(__NR_get_mempolicy, ...)
 * which are blocked by Android's seccomp.
 * ============================================================================ */

#include <stdarg.h>

/* get_mempolicy syscall number */
#ifndef __NR_get_mempolicy
#if defined(__aarch64__)
#define __NR_get_mempolicy 236
#elif defined(__arm__)
#define __NR_get_mempolicy 320
#endif
#endif

/* NUMA policy modes */
#define MPOL_DEFAULT    0
#define MPOL_PREFERRED  1
#define MPOL_BIND       2
#define MPOL_INTERLEAVE 3
#define MPOL_LOCAL      4

long syscall_wrapper(long number, ...) {
    va_list args;
    va_start(args, number);
    
    /* Handle membarrier */
    if (number == __NR_membarrier) {
        int cmd = va_arg(args, int);
        unsigned int flags = va_arg(args, unsigned int);
        va_end(args);
        
        LOGD("syscall(__NR_membarrier, cmd=%d, flags=%u)", cmd, flags);
        
        /* Try the actual syscall first */
        long ret = syscall(__NR_membarrier, cmd, flags);
        if (ret >= 0) {
            return ret;
        }
        
        /* If blocked by seccomp, return appropriate error */
        if (errno == ENOSYS) {
            LOGW("membarrier syscall not available, returning -ENOSYS");
            return -ENOSYS;
        }
        
        return ret;
    }
    
    /* Handle get_mempolicy (NUMA) */
    if (number == __NR_get_mempolicy) {
        int* mode = va_arg(args, int*);
        unsigned long* nodemask = va_arg(args, unsigned long*);
        unsigned long maxnode = va_arg(args, unsigned long);
        void* addr = va_arg(args, void*);
        unsigned long flags = va_arg(args, unsigned long);
        va_end(args);
        
        LOGD("syscall(__NR_get_mempolicy) - simulating default NUMA policy");
        
        /* Android doesn't support NUMA, simulate default policy */
        if (mode) *mode = MPOL_DEFAULT;
        if (nodemask && maxnode > 0) {
            /* Clear nodemask - only node 0 */
            size_t bytes = (maxnode + 7) / 8;
            memset(nodemask, 0, bytes);
        }
        
        return 0;  /* Success */
    }
    
    /* For other syscalls, pass through */
    /* Note: This is a simplified version - real implementation would need
     * to handle all argument types properly */
    long a1 = va_arg(args, long);
    long a2 = va_arg(args, long);
    long a3 = va_arg(args, long);
    long a4 = va_arg(args, long);
    long a5 = va_arg(args, long);
    long a6 = va_arg(args, long);
    va_end(args);
    
    return syscall(number, a1, a2, a3, a4, a5, a6);
}

