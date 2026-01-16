/*
 * glibc-bridge - Basic libc Wrapper Functions
 * 
 * Wrappers for fundamental libc functions that differ between glibc and bionic.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdint.h>
#include <unistd.h>
#include <pthread.h>
#include <errno.h>
#include <search.h>
#include <sys/sysinfo.h>

#include "../include/glibc_bridge_wrappers.h"
#include "../include/glibc_bridge_private.h"
#include "../dynlink/glibc_bridge_log.h"
#include "wrapper_common.h"  /* Common wrapper utilities */
#include "proot_bypass.h"    /* PRoot-style Android bypasses */

/* Additional includes for wrappers */
#include <time.h>
#include <ctype.h>
#include <signal.h>
#include <sys/resource.h>
#include <limits.h>

#ifdef __ANDROID__
#include <android/log.h>
#endif

/* External reference to glibc root path */
extern char g_glibc_root[512];

/* glibc sysconf constants that may differ from bionic
 * These values are from glibc's bits/confname.h */
#define GLIBC_SC_PAGESIZE          30
#define GLIBC_SC_PAGE_SIZE         GLIBC_SC_PAGESIZE
#define GLIBC_SC_NPROCESSORS_CONF  83
#define GLIBC_SC_NPROCESSORS_ONLN  84
#define GLIBC_SC_PHYS_PAGES        85
#define GLIBC_SC_AVPHYS_PAGES      86
#define GLIBC_SC_CLK_TCK           2
#define GLIBC_SC_OPEN_MAX          4
#define GLIBC_SC_NGROUPS_MAX       3
#define GLIBC_SC_ARG_MAX           0
#define GLIBC_SC_CHILD_MAX         1

/* ============================================================================
 * Global Program Name Variables (glibc compatibility)
 * ============================================================================ */

char* __progname = NULL;
char* __progname_full = NULL;
char* program_invocation_name = NULL;
char* program_invocation_short_name = NULL;

/* ============================================================================
 * App Files Directory
 * ============================================================================ */

const char* g_app_files_dir = NULL;
static char g_app_base_dir[512] = {0};  /* Base directory containing glibc-root (set once) */

void glibc_bridge_set_app_files_dir(const char* dir) {
    g_app_files_dir = dir;
    
    /* Set app base dir only once - this is where glibc-root lives */
    if (g_app_base_dir[0] == '\0' && dir && dir[0]) {
        /* Extract the base /data/user/0/<package>/files directory */
        const char* files_marker = strstr(dir, "/files");
        if (files_marker) {
            /* Find the end of /files (could be /files/ or /files) */
            const char* end = files_marker + 6;  /* strlen("/files") = 6 */
            if (*end == '/' || *end == '\0') {
                /* Copy up to and including /files */
                size_t len = end - dir;
                if (len < sizeof(g_app_base_dir)) {
                    memcpy(g_app_base_dir, dir, len);
                    g_app_base_dir[len] = '\0';
#ifdef __ANDROID__
                    __android_log_print(ANDROID_LOG_INFO, "glibc-bridge", 
                        "App base dir set to: %s (from working dir: %s)", 
                        g_app_base_dir, dir);
#endif
                }
            }
        }
        
        /* Fallback: use dir as-is if no /files marker found */
        if (g_app_base_dir[0] == '\0') {
            strncpy(g_app_base_dir, dir, sizeof(g_app_base_dir) - 1);
        }
    }
}

const char* glibc_bridge_get_app_base_dir(void) {
    return g_app_base_dir[0] ? g_app_base_dir : g_app_files_dir;
}

/* ============================================================================
 * vsnprintf wrapper - Fix for .NET hostpolicy MTE pointer truncation bug
 * 
 * Problem: .NET hostpolicy formats pointers with buffer size 18 bytes:
 *   pal::char_t buffer[STRING_LENGTH("0xffffffffffffffff")];  // = 18
 *   pal::snwprintf(buffer, ARRAY_SIZE(buffer), "0x%zx", (size_t)ptr);
 * 
 * On Android ARM64 with MTE, pointers like 0xb4000076b9e9d7d0 need 19 chars
 * (18 hex chars + null), causing truncation to "0xb4000076b9e9d7d".
 * 
 * Solution: Detect pointer formatting to small buffers and fix the output.
 * ============================================================================ */

/**
 * Check if format string contains pointer-like format specifiers
 */
static int format_has_pointer_spec(const char* fmt) {
    if (!fmt) return 0;
    while (*fmt) {
        if (*fmt == '%') {
            fmt++;
            /* Skip flags */
            while (*fmt == '-' || *fmt == '+' || *fmt == ' ' || 
                   *fmt == '#' || *fmt == '0') fmt++;
            /* Skip width */
            while (*fmt >= '0' && *fmt <= '9') fmt++;
            /* Skip precision */
            if (*fmt == '.') {
                fmt++;
                while (*fmt >= '0' && *fmt <= '9') fmt++;
            }
            /* Check length modifier and specifier */
            if (*fmt == 'z' || *fmt == 'l') {
                fmt++;
                if (*fmt == 'l') fmt++;  /* ll */
                if (*fmt == 'x' || *fmt == 'X') return 1;
            } else if (*fmt == 'p') {
                return 1;
            }
        }
        if (*fmt) fmt++;
    }
    return 0;
}

/* Global storage for last formatted MTE pointer (for use by strtoull) */
/* Note: NOT static so strtoull_wrapper can access via extern */
__thread unsigned long long g_last_mte_pointer = 0;
__thread char g_last_mte_string[64] = {0};  /* Increased buffer size for safety */

/**
 * vsnprintf wrapper with MTE pointer truncation fix
 */
int vsnprintf_wrapper(char* str, size_t size, const char* format, va_list ap) {
    /* Log ALL vsnprintf calls for debugging */
#ifdef __ANDROID__
    if (format && size > 0 && size <= 32) {
        __android_log_print(ANDROID_LOG_WARN, "glibc-bridge",
            "[vsnprintf] CALL: size=%zu fmt='%.40s'", size, format);
    }
#endif
    
    if (!str || size == 0 || !format) {
        return vsnprintf(str, size, format, ap);
    }
    
    /* Check if this might be formatting a pointer to a small buffer */
    int is_ptr_fmt = format_has_pointer_spec(format);
    
    /* Debug: log potential pointer formatting */
    if (is_ptr_fmt && size >= 15 && size <= 30) {
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
            "[vsnprintf] !!! PTR FMT DETECTED: size=%zu fmt='%.40s'",
            size, format);
#endif
    }
    
    /* If buffer is small (15-30 bytes) and formatting pointer */
    if (is_ptr_fmt && size >= 15 && size <= 30) {
        /* Use a larger temporary buffer */
        char temp[64];
        va_list ap_copy;
        va_copy(ap_copy, ap);
        int needed = vsnprintf(temp, sizeof(temp), format, ap_copy);
        va_end(ap_copy);
        
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
            "[vsnprintf] FORMAT RESULT: needed=%d temp='%s'", needed, temp);
#endif
        
        /* If output would be truncated and looks like MTE pointer */
        if (needed >= (int)size && needed <= 30) {
            /* Check if it looks like "0x" followed by hex digits */
            if (temp[0] == '0' && (temp[1] == 'x' || temp[1] == 'X')) {
                /* Parse the full pointer value and save it */
                unsigned long long full_ptr = strtoull(temp, NULL, 16);
                if (full_ptr > 0x7000000000000000ULL) {
                    g_last_mte_pointer = full_ptr;
                    /* Safe copy with explicit null termination */
                    size_t copy_len = strlen(temp);
                    if (copy_len >= sizeof(g_last_mte_string)) {
                        copy_len = sizeof(g_last_mte_string) - 1;
                    }
                    memcpy(g_last_mte_string, temp, copy_len);
                    g_last_mte_string[copy_len] = '\0';
                    
#ifdef __ANDROID__
                    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
                        "[vsnprintf] !!! SAVING MTE PTR: buf=%zu needed=%d val=%s (0x%llx)",
                        size, needed, temp, full_ptr);
#endif
                }
                /* Still truncate as normal - strtoull will use saved value */
                /* Safe copy: copy size-1 bytes max, then null terminate */
                size_t copy_bytes = size - 1;
                if (copy_bytes > (size_t)needed) {
                    copy_bytes = (size_t)needed;
                }
                if (copy_bytes > sizeof(temp) - 1) {
                    copy_bytes = sizeof(temp) - 1;  /* Never copy more than temp buffer */
                }
                memcpy(str, temp, copy_bytes);
                str[copy_bytes] = '\0';
                return needed;
            }
        }
    }
    
    /* Normal path */
    return vsnprintf(str, size, format, ap);
}

/**
 * snprintf wrapper - uses vsnprintf_wrapper
 */
int snprintf_wrapper(char* str, size_t size, const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    int ret = vsnprintf_wrapper(str, size, format, ap);
    va_end(ap);
    return ret;
}

/* ============================================================================
 * C2x/C23 _Float64 Functions
 * These are used by newer glibc applications
 * On most platforms, _Float64 == double
 * ============================================================================ */

/**
 * strtof64 - Convert string to _Float64 (equivalent to strtod)
 * C2x standard function
 */
double strtof64_wrapper(const char *nptr, char **endptr) {
    return strtod(nptr, endptr);
}

/**
 * strfromf64 - Convert _Float64 to string
 * C2x standard function
 * format must be one of: %a, %A, %e, %E, %f, %F, %g, %G
 */
int strfromf64_wrapper(char *str, size_t n, const char *format, double fp) {
    return snprintf(str, n, format, fp);
}

/**
 * strtoull wrapper - fix truncated MTE pointers
 * 
 * When hostpolicy formats an MTE pointer like 0xb4000076b9e9d7d0 to a
 * small buffer, it gets truncated to "0xb4000076b9e9d7d" (15 hex digits).
 * This causes strtoull to return 0x0b4000076b9e9d7d instead of the real address.
 * 
 * We detect this pattern and shift left 4 bits to recover the correct address.
 * We try to determine the last digit by checking pointer alignment:
 * - For 16-byte aligned: last nibble is 0
 * - For 8-byte aligned (but not 16): last nibble is 8
 */

#include <sys/mman.h>
#include <unistd.h>
#include <signal.h>
#include <setjmp.h>

/* Test if an address is readable using mincore */
static int test_address_readable(unsigned long long addr) {
    /* Strip MTE tag from pointer (ARM64 MTE uses upper bits for tag) */
    /* MTE pointers look like 0xb4XXXXX... where b4 is the tag */
    unsigned long long real_addr = addr & 0x00FFFFFFFFFFFFFFULL;
    
    /* Quick validation: check if addr looks like a valid user pointer */
    if (real_addr < 0x1000 || real_addr > 0x0000FFFFFFFFFFFFULL) {
        return 0;
    }
    
    /* Use mincore to test if the page is mapped */
    unsigned long page_size = sysconf(_SC_PAGESIZE);
    unsigned long page_addr = (real_addr / page_size) * page_size;
    unsigned char vec[1];
    
    /* mincore returns 0 if the page is mapped, -1 with ENOMEM if not */
    if (mincore((void*)page_addr, page_size, vec) == 0) {
        return 1;
    }
    return 0;
}



unsigned long long strtoull_wrapper(const char* nptr, char** endptr, int base) {

        return strtoull(nptr, endptr, base);

    

}



/**
 * __isoc23_strtoull wrapper
 */
unsigned long long isoc23_strtoull_wrapper(const char* nptr, char** endptr, int base) {
    return strtoull_wrapper(nptr, endptr, base);
}

/* ============================================================================
 * Basic Wrappers
 * ============================================================================ */

char* secure_getenv_wrapper(const char* name) {
    /* In non-setuid context, same as getenv */
    return getenv(name);
}

int __register_atfork_wrapper(void (*prepare)(void), void (*parent)(void), 
                               void (*child)(void), void* dso_handle) {
    /* Use bionic's pthread_atfork */
    (void)dso_handle;
    return pthread_atfork(prepare, parent, child);
}

void error_wrapper(int status, int errnum, const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    
    /* Print program name */
    if (__progname) {
        fprintf(stderr, "%s: ", __progname);
    }
    
    /* Print message */
    vfprintf(stderr, format, ap);
    
    /* Print error string if errnum != 0 */
    if (errnum != 0) {
        fprintf(stderr, ": %s", strerror(errnum));
    }
    fprintf(stderr, "\n");
    
    va_end(ap);
    
    if (status != 0) {
        exit(status);
    }
}

/* ============================================================================
 * sysconf Wrapper (handle glibc/bionic constant differences)
 * ============================================================================ */

long sysconf_wrapper(int name) {
    /* Translate glibc constants to bionic equivalents.
     * glibc and bionic have different numbering for sysconf constants.
     * We need to map them appropriately. */
    long result;
    const char* name_str = "unknown";
    
    switch (name) {
        case GLIBC_SC_PAGESIZE:  /* Also GLIBC_SC_PAGE_SIZE, same value */
            name_str = "_SC_PAGESIZE";
            result = sysconf(_SC_PAGESIZE);
            break;
        case GLIBC_SC_NPROCESSORS_CONF:
            name_str = "_SC_NPROCESSORS_CONF";
            result = sysconf(_SC_NPROCESSORS_CONF);
            break;
        case GLIBC_SC_NPROCESSORS_ONLN:
            name_str = "_SC_NPROCESSORS_ONLN";
            result = sysconf(_SC_NPROCESSORS_ONLN);
            break;
        case GLIBC_SC_PHYS_PAGES:
            name_str = "_SC_PHYS_PAGES";
            result = sysconf(_SC_PHYS_PAGES);
            break;
        case GLIBC_SC_AVPHYS_PAGES:
            name_str = "_SC_AVPHYS_PAGES";
            result = sysconf(_SC_AVPHYS_PAGES);
            break;
        case GLIBC_SC_CLK_TCK:
            name_str = "_SC_CLK_TCK";
            result = sysconf(_SC_CLK_TCK);
            break;
        case GLIBC_SC_OPEN_MAX:
            name_str = "_SC_OPEN_MAX";
            result = sysconf(_SC_OPEN_MAX);
            break;
        case GLIBC_SC_NGROUPS_MAX:
            name_str = "_SC_NGROUPS_MAX";
            result = sysconf(_SC_NGROUPS_MAX);
            break;
        case GLIBC_SC_ARG_MAX:
            name_str = "_SC_ARG_MAX";
            result = sysconf(_SC_ARG_MAX);
            break;
        case GLIBC_SC_CHILD_MAX:
            name_str = "_SC_CHILD_MAX";
            result = sysconf(_SC_CHILD_MAX);
            break;
        default:
            /* For other values, pass directly - hope they match */
            result = sysconf(name);
            break;
    }
    
    LOG_DEBUG("sysconf(%d=%s) = %ld", name, name_str, result);
    return result;
}

/* ============================================================================
 * getsid Wrapper
 * ============================================================================ */
#include "../glibc_bridge_tls.h"  /* For SYNC_ERRNO() */
#include <sys/socket.h>

/* Error hook - wrapper name tracking is now in wrapper_common.h */

pid_t getsid_wrapper(pid_t pid) {
    LOG_DEBUG("getsid_wrapper: pid=%d", pid);
    return proot_getsid(pid);
}

/* ============================================================================
 * socket Wrapper (sync errno for Android permission errors)
 * ============================================================================ */

/* External: fake root socket fallback */
extern int glibc_bridge_socket_with_fallback(int domain, int type, int protocol);
extern int g_fake_root_enabled;

int socket_wrapper(int domain, int type, int protocol) {
    WRAPPER_BEGIN("socket");
    int ret;
    
    if (g_fake_root_enabled) {
        ret = glibc_bridge_socket_with_fallback(domain, type, protocol);
    } else {
        ret = socket(domain, type, protocol);
    }
    
    WRAPPER_RETURN(ret);
}

/* ============================================================================
 * Signal Wrappers (fix for Android forked process)
 * ============================================================================ */
#include <signal.h>

/* External: fake root signal handling */
extern void* glibc_bridge_signal(int signum, void* handler);
extern int glibc_bridge_raise(int sig);

/* Callback invoker for ARM64 glibc mode */
static inline int64_t glibc_bridge_invoke_callback(uint64_t callback_addr, 
                                            uint64_t arg0, uint64_t arg1, 
                                            uint64_t arg2, uint64_t arg3) {
    /* ARM64 glibc mode - call directly */
    typedef int64_t (*callback_func_t)(uint64_t, uint64_t, uint64_t, uint64_t);
    callback_func_t func = (callback_func_t)callback_addr;
    return func(arg0, arg1, arg2, arg3);
}

/* Simplified versions for common callback signatures */
static inline void glibc_bridge_invoke_callback_void(uint64_t callback_addr, uint64_t arg0) {
    typedef void (*callback_func_t)(uint64_t);
    callback_func_t func = (callback_func_t)callback_addr;
    func(arg0);
}

static inline int glibc_bridge_invoke_callback_int2(uint64_t callback_addr, 
                                              uint64_t arg0, uint64_t arg1) {
    typedef int (*callback_func_t)(const void*, const void*);
    callback_func_t func = (callback_func_t)callback_addr;
    return func((const void*)arg0, (const void*)arg1);
}

/* ============================================================================
 * Signal Handler Wrappers
 * 
 * For ARM64 glibc programs, signal handlers can be called directly.
 * No conversion needed as struct sigaction and sigset_t layouts match bionic.
 * ============================================================================ */

void* signal_wrapper(int signum, void* handler) {
    WRAPPER_BEGIN("signal");
    
    if (g_fake_root_enabled) {
        void* ret = glibc_bridge_signal(signum, handler);
        CLEAR_WRAPPER();
        return ret;
    }
    
    void* ret = signal(signum, handler);
    /* For pointer-returning functions like signal, sync errno based on NULL check
     * CRITICAL: Do NOT cast pointer to long - on x64 Windows, long is 32-bit and will truncate 64-bit pointers */
    if (ret == NULL || ret == SIG_ERR) {
        SYNC_ERRNO();  /* Failed - sync errno with logging */
    } else {
        SYNC_ERRNO_SILENT();  /* Success - sync errno silently */
    }
    CLEAR_WRAPPER();
    return ret;
}

int raise_wrapper(int sig) {
    WRAPPER_BEGIN("raise");
    
    if (g_fake_root_enabled) {
        int ret = glibc_bridge_raise(sig);
        CLEAR_WRAPPER();
        return ret;
    }
    
    int ret = raise(sig);
    WRAPPER_RETURN(ret);
}

/* ============================================================================
 * Assert Wrapper
 * ============================================================================ */

void assert_fail_wrapper(const char* assertion, const char* file, 
                         unsigned int line, const char* function) {
    char buf[512];
    snprintf(buf, sizeof(buf), "[ASSERT] %s:%u: %s: Assertion `%s' failed.\n", 
             file ? file : "?", line, function ? function : "?", 
             assertion ? assertion : "?");
    write(STDERR_FILENO, buf, strlen(buf));
    abort();
}

/* ============================================================================
 * pthread Wrapper
 * ============================================================================ */

/* Structure to pass thread info to native thread */
typedef struct {
    void* (*start_routine)(void*);  /* Thread start function */
    void* arg;                       /* Argument to pass */
} thread_info_t;

/* Native thread function that invokes the thread start routine.
 * Supports native ARM64 glibc programs. */
static void* pthread_native_start(void* arg) {
    thread_info_t* info = (thread_info_t*)arg;
    void* (*start_routine)(void*) = info->start_routine;
    void* thread_arg = info->arg;
    free(info);  /* We own this memory */
    
    /* Initialize glibc TLS compatibility layer for this new thread */
    glibc_bridge_init_glibc_tls();
    
    void* result;
    
    /* Use glibc_bridge_invoke_callback for callback invocation */
    LOG_DEBUG("pthread_native_start: invoking routine %p with arg %p",
              (void*)start_routine, thread_arg);
    int64_t ret = glibc_bridge_invoke_callback((uint64_t)start_routine, 
                                         (uint64_t)thread_arg, 0, 0, 0);
    result = (void*)(intptr_t)ret;
    
    LOG_DEBUG("pthread_native_start: thread returned %p", result);
    return result;
}

int pthread_create_wrapper(pthread_t* thread, const pthread_attr_t* attr,
                          void* (*start_routine)(void*), void* arg) {
    SET_WRAPPER("pthread_create");
    
    LOG_DEBUG("pthread_create_wrapper: start_routine=%p, arg=%p",
              (void*)start_routine, arg);
    
    /* Allocate structure to pass info to the native thread function */
    thread_info_t* info = (thread_info_t*)malloc(sizeof(thread_info_t));
    if (!info) {
        CLEAR_WRAPPER();
        return ENOMEM;
    }
    
    info->start_routine = start_routine;
    info->arg = arg;
    
    /* Call callback function */
    /* Create thread with our native wrapper that initializes TLS */
    int ret = pthread_create(thread, attr, pthread_native_start, info);
    if (ret != 0) {
        free(info);  /* Cleanup on failure */
    }
    
    CLEAR_WRAPPER();
    return ret;
}

int pthread_key_create_wrapper(pthread_key_t* key, void (*destructor)(void*)) {
    /* Note: destructor callback is rarely called and often NULL */
    /* For now, pass through - may need wrapper if destructor callbacks are used */
    return pthread_key_create(key, destructor);
}

/* ============================================================================
 * Dynamic Linker Stub - _dl_find_object implementation
 * Optimized for fast exception unwinding (no logging in hot path)
 * ============================================================================ */

/* glibc 2.35+ _dl_find_object structure - used for fast exception handling lookup */
struct dl_find_object {
    unsigned long long int dlfo_flags;
    void *dlfo_map_start;           /* Start of mapped region */
    void *dlfo_map_end;             /* End of mapped region */
    void *dlfo_link_map;            /* struct link_map pointer (can be NULL) */
    void *dlfo_eh_frame;            /* PT_GNU_EH_FRAME pointer (critical for unwinding!) */
};

/* Forward declaration - implemented in glibc_bridge_sharedlib.c */
extern int glibc_bridge_find_eh_frame(void* addr, void** map_start, void** map_end, void** eh_frame);

int dl_find_object_wrapper(void* addr, void* result) {
    struct dl_find_object* obj = (struct dl_find_object*)result;
    void *map_start, *map_end, *eh_frame;
    
    if (glibc_bridge_find_eh_frame(addr, &map_start, &map_end, &eh_frame)) {
        if (obj) {
            obj->dlfo_flags = 0;
            obj->dlfo_map_start = map_start;
            obj->dlfo_map_end = map_end;
            obj->dlfo_link_map = NULL;
            obj->dlfo_eh_frame = eh_frame;
        }
        return 0;
    }
    return -1;
}

/* ============================================================================
 * Memory/String Functions (BSD compatibility)
 * ============================================================================ */

int bcmp_wrapper(const void* s1, const void* s2, size_t n) {
    return memcmp(s1, s2, n);
}

void bcopy_wrapper(const void* src, void* dest, size_t n) {
    memmove(dest, src, n);
}

void bzero_wrapper(void* s, size_t n) {
    memset(s, 0, n);
}

void explicit_bzero_wrapper(void* s, size_t n) {
    /* Use volatile to prevent compiler from optimizing away the memset */
    volatile unsigned char* p = (volatile unsigned char*)s;
    while (n--) {
        *p++ = 0;
    }
}

/* ============================================================================
 * getdelim/getline Wrappers
 * ============================================================================ */

ssize_t getdelim_wrapper(char** lineptr, size_t* n, int delim, FILE* stream) {
    /* Convert glibc FILE* to bionic FILE* before calling bionic getdelim */
    FILE* bionic_fp = glibc_bridge_get_bionic_fp(stream);
    if (!bionic_fp) {
        errno = EBADF;
        return -1;
    }
    return getdelim(lineptr, n, delim, bionic_fp);
}

ssize_t getline_wrapper(char** lineptr, size_t* n, FILE* stream) {
    /* Convert glibc FILE* to bionic FILE* before calling bionic getline */
    FILE* bionic_fp = glibc_bridge_get_bionic_fp(stream);
    if (!bionic_fp) {
        errno = EBADF;
        return -1;
    }
    return getline(lineptr, n, bionic_fp);
}

/* ============================================================================
 * __fsetlocking Wrapper
 * 
 * This function controls the locking behavior of stdio streams.
 * glibc FILE* structures are incompatible with bionic, so we need to:
 * - Return FSETLOCKING_INTERNAL for glibc FILE* (pretend we handle locking)
 * - Only call real __fsetlocking for bionic FILE*
 * ============================================================================ */

/* Locking types (from stdio_ext.h) */
#ifndef FSETLOCKING_INTERNAL
#define FSETLOCKING_INTERNAL 0
#define FSETLOCKING_BYCALLER 1
#define FSETLOCKING_QUERY    2
#endif

/* glibc FILE magic number */
#define GLIBC_IO_MAGIC      0xFBAD0000
#define GLIBC_IO_MAGIC_MASK 0xFFFF0000

/* Check if a FILE* looks like a glibc FILE* by checking magic in _flags */
static int is_glibc_file_ptr(void* fp) {
    if (!fp || (uintptr_t)fp < 0x1000) return 0;
    
    /* glibc FILE has _flags as first member with magic 0xFBAD0000 */
    int flags = *(int*)fp;
    return (flags & GLIBC_IO_MAGIC_MASK) == GLIBC_IO_MAGIC;
}

int __fsetlocking_wrapper(FILE* fp, int type) {
    if (!fp) return FSETLOCKING_INTERNAL;
    
    /* Check if this is a glibc FILE* - can't pass to bionic */
    if (is_glibc_file_ptr(fp)) {
        /* glibc FILE* - return internal locking mode */
        return FSETLOCKING_INTERNAL;
    }
    
    /* Try to get mapped bionic FILE* */
    FILE* bionic_fp = glibc_bridge_get_bionic_fp(fp);
    if (!bionic_fp || is_glibc_file_ptr(bionic_fp)) {
        /* No valid bionic FILE* found */
        return FSETLOCKING_INTERNAL;
    }
    
    /* This is a bionic FILE* - call the real function */
    extern int __fsetlocking(FILE*, int);
    return __fsetlocking(bionic_fp, type);
}

/* ============================================================================
 * popen/pclose Wrappers
 * 
 * On Android, commands like lscpu/uname may not exist. We need to handle
 * these gracefully by returning NULL (command not found) rather than crashing.
 * ============================================================================ */

FILE* popen_wrapper(const char* command, const char* type) {
    /* Try to execute the command, but handle gracefully if it fails */
    FILE* f = popen(command, type);
    /* Even if popen succeeds, the command might fail */
    return f;
}

int pclose_wrapper(FILE* stream) {
    if (!stream) return -1;
    return pclose(stream);
}

/* ============================================================================
 * C99 scanf Family Wrappers
 * 
 * glibc uses __isoc99_* prefixed names for C99-compliant scanf functions.
 * These simply forward to the standard functions in bionic.
 * ============================================================================ */

/* Special sscanf wrapper that takes up to 4 extra args
 * ARM64 passes: X0=str, X1=format, X2-X5=args (max 4 extra args via regs)
 * We receive them as fixed args and pass directly to sscanf.
 * Varargs handling: str, format, arg2, arg3, arg4, arg5
 */
int __isoc99_sscanf_wrapper(const char* str, const char* format,
                            uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3) {
    /* Pass args directly to sscanf. Since sscanf is variadic, 
     * we can pass more args than needed - unused ones are ignored. */
    return sscanf(str, format, 
                  (void*)a0, (void*)a1, (void*)a2, (void*)a3);
}

/* scanf wrapper - format is first arg, followed by up to 5 varargs in regs */
int __isoc99_scanf_wrapper(const char* format,
                           uint64_t a0, uint64_t a1, uint64_t a2, 
                           uint64_t a3, uint64_t a4) {
    return scanf(format, (void*)a0, (void*)a1, (void*)a2, (void*)a3, (void*)a4);
}

/* fscanf wrapper - stream, format, followed by up to 4 varargs in regs
 * IMPORTANT: Must convert glibc FILE* to bionic FILE* before calling bionic fscanf */
int __isoc99_fscanf_wrapper(FILE* stream, const char* format,
                            uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3) {
    FILE* bionic_fp = glibc_bridge_get_bionic_fp(stream);
    if (!bionic_fp) {
        errno = EBADF;
        return EOF;
    }
    return fscanf(bionic_fp, format, (void*)a0, (void*)a1, (void*)a2, (void*)a3);
}

int __isoc99_vsscanf_wrapper(const char* str, const char* format, va_list ap) {
    return vsscanf(str, format, ap);
}

int __isoc99_vscanf_wrapper(const char* format, va_list ap) {
    return vscanf(format, ap);
}

/* vfscanf wrapper - must convert glibc FILE* to bionic FILE* */
int __isoc99_vfscanf_wrapper(FILE* stream, const char* format, va_list ap) {
    FILE* bionic_fp = glibc_bridge_get_bionic_fp(stream);
    if (!bionic_fp) {
        errno = EBADF;
        return EOF;
    }
    return vfscanf(bionic_fp, format, ap);
}

/* ============================================================================
 * __libc_start_main Wrapper
 * 
 * This is the CRITICAL function that bridges glibc's _start to bionic.
 * glibc's _start calls this to initialize the C runtime and call main().
 * ============================================================================ */

int __libc_start_main_wrapper(
    int (*main_func)(int, char**, char**),
    int argc,
    char** argv,
    int (*init)(int, char**, char**),
    void (*fini)(void),
    void (*rtld_fini)(void),
    void* stack_end)
{
    char buf[256];
    (void)rtld_fini;
    (void)stack_end;
    
    snprintf(buf, sizeof(buf), "[WRAPPER] __libc_start_main called: main=%p argc=%d\n", 
             (void*)main_func, argc);
    write(STDERR_FILENO, buf, strlen(buf));
    
    /* Setup program name variables */
    if (argc > 0 && argv && argv[0]) {
        __progname_full = argv[0];
        program_invocation_name = argv[0];
        
        /* Find short name (after last '/') */
        char* p = strrchr(argv[0], '/');
        __progname = p ? p + 1 : argv[0];
        program_invocation_short_name = __progname;
        
        snprintf(buf, sizeof(buf), "[WRAPPER] __progname: %s\n", __progname);
        glibc_bridge_dl_child_log(buf);
    }
    
    /* Compute envp from argv - it's right after argv on the stack
     * This ensures the auxval vector is accessible after envp for programs
     * that read it directly from memory */
    char** envp = argv + argc + 1;
    
    /* Initialize stdio FILE wrapper system before anything else */
    glibc_bridge_stdio_init();
    glibc_bridge_dl_child_log("[WRAPPER] stdio initialized\n");
    
    /* Setup DOTNET_ROOT from argv[0] (the dotnet executable path) */
    if (argv && argv[0]) {
        char dotnet_root[512];
        strncpy(dotnet_root, argv[0], sizeof(dotnet_root) - 1);
        dotnet_root[sizeof(dotnet_root) - 1] = '\0';
        
        /* Remove the executable name to get the directory */
        char* last_slash = strrchr(dotnet_root, '/');
        if (last_slash) {
            *last_slash = '\0';
            setenv("DOTNET_ROOT", dotnet_root, 0);  /* Don't overwrite if already set */
            
            snprintf(buf, sizeof(buf), "[WRAPPER] DOTNET_ROOT=%s\n", dotnet_root);
            glibc_bridge_dl_child_log(buf);
        }
    }
    
    /* Enable CoreCLR detailed trace logging */
    setenv("COREHOST_TRACE", "1", 1);
    setenv("COREHOST_TRACE_VERBOSITY", "4", 1);  /* 1-4, 4 is most verbose */
    
    /* Set trace file path if app files dir is available */
    if (g_app_files_dir && g_app_files_dir[0]) {
        char tracefile_path[1024];
        snprintf(tracefile_path, sizeof(tracefile_path), "%s/coreclr_trace.log", g_app_files_dir);
        setenv("COREHOST_TRACEFILE", tracefile_path, 1);
        snprintf(buf, sizeof(buf), "[WRAPPER] COREHOST_TRACE enabled (verbosity=4, file=%s)\n", tracefile_path);
        glibc_bridge_dl_child_log(buf);
    } else {
        glibc_bridge_dl_child_log("[WRAPPER] COREHOST_TRACE enabled (verbosity=4, output=stderr)\n");
    }
    
    /* Enable full globalization support for .NET
     * Note: ICU libraries must be available for locale support */
    /* setenv("DOTNET_SYSTEM_GLOBALIZATION_INVARIANT", "1", 1); */
    glibc_bridge_dl_child_log("[WRAPPER] Full globalization mode enabled\n");
    
    /* Setup LD_LIBRARY_PATH for glibc libs */
    if (g_glibc_root[0]) {
        char ld_path[1024];
        snprintf(ld_path, sizeof(ld_path), "%s/lib:%s/lib/aarch64-linux-gnu:%s/usr/lib",
                 g_glibc_root, g_glibc_root, g_glibc_root);
        setenv("LD_LIBRARY_PATH", ld_path, 0);
    }
    
    /* Call init function if provided */
    if (init) {
        snprintf(buf, sizeof(buf), "[WRAPPER] Calling init function: %p\n", (void*)init);
        glibc_bridge_dl_child_log(buf);
        init(argc, argv, envp);
    }
    
    /* Disable buffering on stdout/stderr */
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    
    /* Set working directory to the directory of the program being executed
     * This is critical for .NET applications that load assemblies relative to CWD
     * argv[1] is typically the .dll/.exe being run */
    if (argc >= 2 && argv && argv[1]) {
        char work_dir[512];
        strncpy(work_dir, argv[1], sizeof(work_dir) - 1);
        work_dir[sizeof(work_dir) - 1] = '\0';
        
        /* Remove the file name to get the directory */
        char* last_slash = strrchr(work_dir, '/');
        if (last_slash) {
            *last_slash = '\0';
            if (chdir(work_dir) == 0) {
                snprintf(buf, sizeof(buf), "[WRAPPER] chdir to: %s (from argv[1])\n", work_dir);
                glibc_bridge_dl_child_log(buf);
            } else {
                snprintf(buf, sizeof(buf), "[WRAPPER] chdir FAILED: %s errno=%d\n", work_dir, errno);
                glibc_bridge_dl_child_log(buf);
            }
        }
    }
    
    glibc_bridge_dl_child_log("[WRAPPER] Calling main()...\n");
    
    /* Call main! */
    int result = main_func(argc, argv, envp);
    
    /* Flush any remaining output */
    fflush(stdout);
    fflush(stderr);
    
    snprintf(buf, sizeof(buf), "[WRAPPER] main() returned: %d\n", result);
    glibc_bridge_dl_child_log(buf);
    
    /* Call fini function if provided (cleanup) */
    if (fini) {
        glibc_bridge_dl_child_log("[WRAPPER] Calling fini function...\n");
        fini();
    }
    
    /* Use _exit() to avoid calling atexit handlers with compatibility issues */
    _exit(result);
    
    return result;
}

/* ============================================================================
 * String functions - glibc internal names
 * ============================================================================ */

/* __strdup is glibc's internal name for strdup */
char* strdup_wrapper(const char* s) {
    return strdup(s);
}

/* ============================================================================
 * Exit and atexit handling
 * 
 * glibc programs may call exit() which triggers atexit handlers.
 * We need to handle this carefully to avoid calling corrupted handlers.
 * ============================================================================ */

/* Store atexit handlers registered by the glibc program */
#define MAX_ATEXIT_HANDLERS 64
static void (*g_atexit_handlers[MAX_ATEXIT_HANDLERS])(void);
static int g_atexit_count = 0;
static pthread_mutex_t g_atexit_mutex = PTHREAD_MUTEX_INITIALIZER;

int atexit_wrapper(void (*function)(void)) {
    pthread_mutex_lock(&g_atexit_mutex);
    if (g_atexit_count < MAX_ATEXIT_HANDLERS) {
        g_atexit_handlers[g_atexit_count++] = function;
        pthread_mutex_unlock(&g_atexit_mutex);
        return 0;
    }
    pthread_mutex_unlock(&g_atexit_mutex);
    return -1;
}

/* __cxa_atexit is used by C++ for destructor registration.
 * 
 * The dso_handle is a pointer to a unique object in the DSO (usually __dso_handle).
 * When the DSO is unloaded, __cxa_finalize(dso_handle) is called to run the
 * destructors registered by that DSO.
 * 
 * On Android, bionic's __cxa_atexit validates the dso_handle against its list
 * of loaded libraries. Since our glibc libraries are not in bionic's list,
 * we get "Couldn't find soinfo by dso_handle" error.
 * 
 * Solution: Pass NULL as dso_handle to make it a global atexit handler.
 * This means the destructor won't be called when the DSO is unloaded, but
 * it will be called at process exit.
 */

/* Storage for cxa_atexit handlers with arguments */
#define MAX_CXA_ATEXIT_HANDLERS 256
typedef struct {
    void (*func)(void*);
    void* arg;
    void* dso_handle;
} cxa_atexit_entry_t;

static cxa_atexit_entry_t g_cxa_atexit_handlers[MAX_CXA_ATEXIT_HANDLERS];
static int g_cxa_atexit_count = 0;
static pthread_mutex_t g_cxa_atexit_mutex = PTHREAD_MUTEX_INITIALIZER;

int __cxa_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle) {
    LOG_DEBUG("__cxa_atexit_wrapper: func=%p, arg=%p, dso_handle=%p", func, arg, dso_handle);
    
    pthread_mutex_lock(&g_cxa_atexit_mutex);
    if (g_cxa_atexit_count < MAX_CXA_ATEXIT_HANDLERS) {
        g_cxa_atexit_handlers[g_cxa_atexit_count].func = func;
        g_cxa_atexit_handlers[g_cxa_atexit_count].arg = arg;
        g_cxa_atexit_handlers[g_cxa_atexit_count].dso_handle = dso_handle;
        g_cxa_atexit_count++;
        pthread_mutex_unlock(&g_cxa_atexit_mutex);
        LOG_DEBUG("__cxa_atexit_wrapper: registered handler %d", g_cxa_atexit_count - 1);
        return 0;
    }
    pthread_mutex_unlock(&g_cxa_atexit_mutex);
    LOG_DEBUG("__cxa_atexit_wrapper: too many handlers");
    return -1;
}

/* __cxa_thread_atexit is used for thread-local destructor registration.
 * This is called by C++ code directly.
 */
int __cxa_thread_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle) {
    LOG_DEBUG("__cxa_thread_atexit_wrapper: func=%p, arg=%p, dso_handle=%p", func, arg, dso_handle);
    /* For now, treat as regular cxa_atexit - thread-local destructors will be
     * called at process exit instead of thread exit. This is not ideal but
     * prevents the crash. */
    return __cxa_atexit_wrapper(func, arg, dso_handle);
}

/* __cxa_thread_atexit_impl is the actual implementation called by libstdc++.
 * libstdc++.so.6 exports __cxa_thread_atexit which internally calls
 * __cxa_thread_atexit_impl. We MUST intercept this to prevent bionic's
 * implementation from being called, which validates dso_handle against
 * bionic's soinfo list and crashes with:
 * "increment_dso_handle_reference_counter: Couldn't find soinfo by dso_handle"
 */
int __cxa_thread_atexit_impl_wrapper(void (*func)(void*), void* arg, void* dso_handle) {
    LOG_DEBUG("__cxa_thread_atexit_impl_wrapper: func=%p, arg=%p, dso_handle=%p", func, arg, dso_handle);
    /* Same handling as __cxa_thread_atexit_wrapper */
    return __cxa_atexit_wrapper(func, arg, dso_handle);
}

/* __cxa_finalize is called when a DSO is unloaded */
void __cxa_finalize_wrapper(void* dso_handle) {
    LOG_DEBUG("__cxa_finalize_wrapper: dso_handle=%p", dso_handle);
    
    pthread_mutex_lock(&g_cxa_atexit_mutex);
    for (int i = g_cxa_atexit_count - 1; i >= 0; i--) {
        if (dso_handle == NULL || g_cxa_atexit_handlers[i].dso_handle == dso_handle) {
            void (*func)(void*) = g_cxa_atexit_handlers[i].func;
            void* arg = g_cxa_atexit_handlers[i].arg;
            
            /* Mark as called */
            g_cxa_atexit_handlers[i].func = NULL;
            
            if (func) {
                pthread_mutex_unlock(&g_cxa_atexit_mutex);
                LOG_DEBUG("__cxa_finalize_wrapper: calling handler %d", i);
                func(arg);
                pthread_mutex_lock(&g_cxa_atexit_mutex);
            }
        }
    }
    
    /* If dso_handle is NULL, clear all handlers */
    if (dso_handle == NULL) {
        g_cxa_atexit_count = 0;
    }
    pthread_mutex_unlock(&g_cxa_atexit_mutex);
}

/* Run all registered atexit handlers (in reverse order) */
static void run_atexit_handlers(void) {
    pthread_mutex_lock(&g_atexit_mutex);
    for (int i = g_atexit_count - 1; i >= 0; i--) {
        if (g_atexit_handlers[i]) {
            pthread_mutex_unlock(&g_atexit_mutex);
            g_atexit_handlers[i]();
            pthread_mutex_lock(&g_atexit_mutex);
        }
    }
    g_atexit_count = 0;
    pthread_mutex_unlock(&g_atexit_mutex);
}

void exit_wrapper(int status) {
    char buf[128];
    snprintf(buf, sizeof(buf), "[WRAPPER] exit(%d) called\n", status);
    write(STDERR_FILENO, buf, strlen(buf));
    
    /* Flush stdio */
    fflush(stdout);
    fflush(stderr);
    
    /* Run our managed atexit handlers */
    run_atexit_handlers();
    
    /* Use _exit to avoid bionic's atexit handlers */
    _exit(status);
}

/* ============================================================================
 * qsort/bsearch wrappers (handle callback functions)
 * ============================================================================ */

/* TLS storage for comparator callback address */
static __thread uint64_t g_qsort_compar_addr = 0;

/* Native comparator that invokes callback */
static int qsort_native_compar(const void* a, const void* b) {
    if (!g_qsort_compar_addr) {
        LOG_WARN("qsort_native_compar: no callback address!");
        return 0;
    }
    
    /* Invoke callback with the two pointers */
    int result = glibc_bridge_invoke_callback_int2(g_qsort_compar_addr, 
                                            (uint64_t)a, (uint64_t)b);
    LOG_DEBUG("qsort_compar(%p, %p) = %d", a, b, result);
    return result;
}

void qsort_wrapper(void* base, size_t nmemb, size_t size, 
                   int (*compar)(const void*, const void*)) {
    /* Store the comparator address */
    g_qsort_compar_addr = (uint64_t)compar;
    
    LOG_DEBUG("qsort_wrapper: base=%p, nmemb=%zu, size=%zu, compar=0x%lx",
              base, nmemb, size, (unsigned long)g_qsort_compar_addr);
    
    /* Call native qsort with our trampoline comparator */
    qsort(base, nmemb, size, qsort_native_compar);
    
    /* Clear the callback address */
    g_qsort_compar_addr = 0;
}

/* TLS storage for bsearch comparator */
static __thread uint64_t g_bsearch_compar_addr = 0;

/* Native comparator that invokes callback */
static int bsearch_native_compar(const void* key, const void* elem) {
    if (!g_bsearch_compar_addr) return 0;
    
    return glibc_bridge_invoke_callback_int2(g_bsearch_compar_addr,
                                      (uint64_t)key, (uint64_t)elem);
}

void* bsearch_wrapper(const void* key, const void* base, size_t nmemb,
                      size_t size, int (*compar)(const void*, const void*)) {
    g_bsearch_compar_addr = (uint64_t)compar;
    
    LOG_DEBUG("bsearch_wrapper: key=%p, base=%p, nmemb=%zu, size=%zu, compar=0x%lx",
              key, base, nmemb, size, (unsigned long)g_bsearch_compar_addr);
    
    void* result = bsearch(key, base, nmemb, size, bsearch_native_compar);
    
    g_bsearch_compar_addr = 0;
    return result;
}

/* TLS storage for lfind/lsearch comparator */
static __thread uint64_t g_lfind_compar_addr = 0;

/* Native comparator that invokes callback */
static int lfind_native_compar(const void* a, const void* b) {
    if (!g_lfind_compar_addr) return 0;
    
    return glibc_bridge_invoke_callback_int2(g_lfind_compar_addr,
                                      (uint64_t)a, (uint64_t)b);
}

void* lfind_wrapper(const void* key, const void* base, size_t* nmemb,
                    size_t size, int (*compar)(const void*, const void*)) {
    SET_WRAPPER("lfind");
    g_lfind_compar_addr = (uint64_t)compar;
    
    LOG_DEBUG("lfind_wrapper: key=%p, base=%p, nmemb=%zu, size=%zu, compar=0x%lx",
              key, base, nmemb ? *nmemb : 0, size, (unsigned long)g_lfind_compar_addr);
    
    void* result = lfind(key, base, nmemb, size, lfind_native_compar);
    
    g_lfind_compar_addr = 0;
    CLEAR_WRAPPER();
    return result;
}

void* lsearch_wrapper(const void* key, void* base, size_t* nmemb,
                      size_t size, int (*compar)(const void*, const void*)) {
    SET_WRAPPER("lsearch");
    g_lfind_compar_addr = (uint64_t)compar;
    
    LOG_DEBUG("lsearch_wrapper: key=%p, base=%p, nmemb=%zu, size=%zu, compar=0x%lx",
              key, base, nmemb ? *nmemb : 0, size, (unsigned long)g_lfind_compar_addr);
    
    void* result = lsearch(key, base, nmemb, size, lfind_native_compar);
    
    g_lfind_compar_addr = 0;
    CLEAR_WRAPPER();
    return result;
}

/* ============================================================================
 * Binary tree (tsearch/tfind/tdelete/twalk/tdestroy) wrappers
 * ============================================================================ */

/* TLS storage for binary tree comparator */
static __thread uint64_t g_tsearch_compar_addr = 0;

/* Native comparator that invokes callback */
static int tsearch_native_compar(const void* a, const void* b) {
    if (!g_tsearch_compar_addr) return 0;
    
    return glibc_bridge_invoke_callback_int2(g_tsearch_compar_addr,
                                      (uint64_t)a, (uint64_t)b);
}

void* tsearch_wrapper(const void* key, void** rootp,
                      int (*compar)(const void*, const void*)) {
    SET_WRAPPER("tsearch");
    g_tsearch_compar_addr = (uint64_t)compar;
    
    LOG_DEBUG("tsearch_wrapper: key=%p, rootp=%p, compar=0x%lx",
              key, rootp, (unsigned long)g_tsearch_compar_addr);
    
    void* result = tsearch(key, rootp, tsearch_native_compar);
    
    g_tsearch_compar_addr = 0;
    CLEAR_WRAPPER();
    return result;
}

void* tfind_wrapper(const void* key, void* const* rootp,
                    int (*compar)(const void*, const void*)) {
    SET_WRAPPER("tfind");
    g_tsearch_compar_addr = (uint64_t)compar;
    
    LOG_DEBUG("tfind_wrapper: key=%p, rootp=%p, compar=0x%lx",
              key, rootp, (unsigned long)g_tsearch_compar_addr);
    
    void* result = tfind(key, rootp, tsearch_native_compar);
    
    g_tsearch_compar_addr = 0;
    CLEAR_WRAPPER();
    return result;
}

void* tdelete_wrapper(const void* key, void** rootp,
                      int (*compar)(const void*, const void*)) {
    SET_WRAPPER("tdelete");
    g_tsearch_compar_addr = (uint64_t)compar;
    
    LOG_DEBUG("tdelete_wrapper: key=%p, rootp=%p, compar=0x%lx",
              key, rootp, (unsigned long)g_tsearch_compar_addr);
    
    void* result = tdelete(key, rootp, tsearch_native_compar);
    
    g_tsearch_compar_addr = 0;
    CLEAR_WRAPPER();
    return result;
}

/* TLS storage for twalk action callback */
static __thread uint64_t g_twalk_action_addr = 0;

/* Native action callback that invokes callback */
static void twalk_native_action(const void* nodep, VISIT which, int depth) {
    if (!g_twalk_action_addr) return;
    
    glibc_bridge_invoke_callback(g_twalk_action_addr,
                          (uint64_t)nodep, (uint64_t)which, (uint64_t)depth, 0);
}

void twalk_wrapper(const void* root, 
                   void (*action)(const void* nodep, VISIT which, int depth)) {
    SET_WRAPPER("twalk");
    g_twalk_action_addr = (uint64_t)action;
    
    LOG_DEBUG("twalk_wrapper: root=%p, action=0x%lx",
              root, (unsigned long)g_twalk_action_addr);
    
    twalk(root, twalk_native_action);
    
    g_twalk_action_addr = 0;
    CLEAR_WRAPPER();
}

/* TLS storage for tdestroy free callback */
static __thread uint64_t g_tdestroy_free_addr = 0;

/* Native free callback that invokes callback */
static void tdestroy_native_free(void* nodep) {
    if (!g_tdestroy_free_addr) return;
    
    glibc_bridge_invoke_callback_void(g_tdestroy_free_addr, (uint64_t)nodep);
}

void tdestroy_wrapper(void* root, void (*free_node)(void* nodep)) {
    SET_WRAPPER("tdestroy");
    g_tdestroy_free_addr = (uint64_t)free_node;
    
    LOG_DEBUG("tdestroy_wrapper: root=%p, free_node=0x%lx",
              root, (unsigned long)g_tdestroy_free_addr);
    
    tdestroy(root, tdestroy_native_free);
    
    g_tdestroy_free_addr = 0;
    CLEAR_WRAPPER();
}

/* ============================================================================
 * h_errno location wrapper (for network errors)
 * ============================================================================ */

static int g_h_errno = 0;

int* __h_errno_location_wrapper(void) {
    return &g_h_errno;
}

/* ============================================================================
 * Memory allocation wrappers
 * ============================================================================ */

#include <malloc.h>

void* valloc_wrapper(size_t size) {
    /* valloc allocates page-aligned memory */
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;
    return memalign(page_size, size);
}

void* pvalloc_wrapper(size_t size) {
    /* pvalloc rounds up size to page boundary */
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;
    size_t aligned_size = (size + page_size - 1) & ~(page_size - 1);
    if (aligned_size == 0) aligned_size = page_size;
    return memalign(page_size, aligned_size);
}

/* ============================================================================
 * String functions
 * ============================================================================ */

/* strverscmp - compare version strings */
int strverscmp_wrapper(const char* s1, const char* s2) {
    /* Simple implementation of strverscmp */
    const unsigned char *p1 = (const unsigned char*)s1;
    const unsigned char *p2 = (const unsigned char*)s2;
    
    while (*p1 && *p2) {
        /* If both are digits, compare numerically */
        if ((*p1 >= '0' && *p1 <= '9') && (*p2 >= '0' && *p2 <= '9')) {
            /* Skip leading zeros */
            while (*p1 == '0') p1++;
            while (*p2 == '0') p2++;
            
            /* Count digits */
            const unsigned char *d1 = p1, *d2 = p2;
            while (*d1 >= '0' && *d1 <= '9') d1++;
            while (*d2 >= '0' && *d2 <= '9') d2++;
            
            size_t len1 = d1 - p1;
            size_t len2 = d2 - p2;
            
            /* Longer number is greater */
            if (len1 != len2) return len1 < len2 ? -1 : 1;
            
            /* Same length, compare digit by digit */
            while (p1 < d1) {
                if (*p1 != *p2) return *p1 < *p2 ? -1 : 1;
                p1++; p2++;
            }
        } else {
            if (*p1 != *p2) return *p1 < *p2 ? -1 : 1;
            p1++; p2++;
        }
    }
    
    if (*p1) return 1;
    if (*p2) return -1;
    return 0;
}

/* __xpg_basename - XPG version of basename (modifies string) */
char* __xpg_basename_wrapper(char* path) {
    if (path == NULL || *path == '\0') {
        return (char*)".";
    }
    
    /* Remove trailing slashes */
    char* end = path + strlen(path) - 1;
    while (end > path && *end == '/') {
        *end-- = '\0';
    }
    
    /* Find last component */
    char* base = strrchr(path, '/');
    return base ? base + 1 : path;
}

/* ============================================================================
 * wordexp wrappers (stub implementation)
 * Android doesn't have wordexp, so we provide a minimal stub
 * ============================================================================ */

/* wordexp_t structure compatible with glibc */
typedef struct {
    size_t we_wordc;    /* Count of words matched */
    char** we_wordv;    /* List of expanded words */
    size_t we_offs;     /* Slots to reserve in we_wordv */
} wordexp_stub_t;

/* wordexp flags */
#define WRDE_DOOFFS   (1 << 0)
#define WRDE_APPEND   (1 << 1)
#define WRDE_NOCMD    (1 << 2)
#define WRDE_REUSE    (1 << 3)
#define WRDE_SHOWERR  (1 << 4)
#define WRDE_UNDEF    (1 << 5)

/* wordexp error codes */
#define WRDE_NOSPACE  1
#define WRDE_BADCHAR  2
#define WRDE_BADVAL   3
#define WRDE_CMDSUB   4
#define WRDE_SYNTAX   5

/* Helper: expand a single $VAR or ${VAR} */
static char* expand_env_var(const char* str) {
    if (!str || str[0] != '$') return strdup(str);
    
    const char* varname;
    size_t varlen;
    
    if (str[1] == '{') {
        /* ${VAR} format */
        varname = str + 2;
        const char* end = strchr(varname, '}');
        if (!end) return strdup(str);  /* Invalid, return as-is */
        varlen = end - varname;
    } else {
        /* $VAR format */
        varname = str + 1;
        varlen = strlen(varname);
        /* Variable name ends at non-alnum/underscore */
        for (size_t i = 0; i < varlen; i++) {
            char c = varname[i];
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                  (c >= '0' && c <= '9') || c == '_')) {
                varlen = i;
                break;
            }
        }
    }
    
    if (varlen == 0) return strdup(str);
    
    /* Extract variable name */
    char* name = (char*)malloc(varlen + 1);
    if (!name) return strdup(str);
    memcpy(name, varname, varlen);
    name[varlen] = '\0';
    
    /* Get value */
    const char* value = getenv(name);
    free(name);
    
    if (value) {
        return strdup(value);
    }
    return strdup("");  /* Empty if not found */
}

int wordexp_wrapper(const char* words, void* pwordexp, int flags) {
    wordexp_stub_t* we = (wordexp_stub_t*)pwordexp;
    (void)flags;
    
    if (!words || !we) {
        return WRDE_BADVAL;
    }
    
    /* Simple implementation with environment variable expansion */
    char* expanded;
    
    if (words[0] == '$') {
        /* Environment variable - expand it */
        expanded = expand_env_var(words);
    } else {
        /* Just copy the string */
        expanded = strdup(words);
    }
    
    if (!expanded) {
        return WRDE_NOSPACE;
    }
    
    we->we_wordc = 1;
    we->we_wordv = (char**)malloc(2 * sizeof(char*));
    if (!we->we_wordv) {
        free(expanded);
        return WRDE_NOSPACE;
    }
    
    we->we_wordv[0] = expanded;
    we->we_wordv[1] = NULL;
    we->we_offs = 0;
    
    return 0;
}

void wordfree_wrapper(void* pwordexp) {
    wordexp_stub_t* we = (wordexp_stub_t*)pwordexp;
    if (!we) return;
    
    if (we->we_wordv) {
        for (size_t i = 0; i < we->we_wordc; i++) {
            free(we->we_wordv[i]);
        }
        free(we->we_wordv);
        we->we_wordv = NULL;
    }
    we->we_wordc = 0;
}

/* ============================================================================
 * Weak symbol stubs (TM clone table, gmon)
 * These are usually NULL or no-ops
 * ============================================================================ */

void _ITM_deregisterTMCloneTable_stub(void) {
    /* No-op stub */
}

void _ITM_registerTMCloneTable_stub(void) {
    /* No-op stub */
}

void __gmon_start___stub(void) {
    /* No-op stub */
}

void _Jv_RegisterClasses_stub(void* classes) {
    /* No-op stub - GCJ/Java interop, not used in modern systems */
    (void)classes;
}

/* ============================================================================
 * LTTng (Linux Trace Toolkit) stubs
 * 
 * .NET CoreCLR uses LTTng for tracing on Linux. Since we don't have LTTng
 * libraries on Android, we provide stub implementations.
 * ============================================================================ */
int lttng_probe_register_stub(void* probe) {
    /* No-op stub - return success */
    (void)probe;
    return 0;
}

void lttng_probe_unregister_stub(void* probe) {
    /* No-op stub */
    (void)probe;
}

/* ============================================================================
 * dlsym wrapper for RTLD_DEFAULT/RTLD_NEXT compatibility
 * ============================================================================ */
#include <dlfcn.h>
#include <stdio.h>

/* glibc RTLD_DEFAULT/RTLD_NEXT might differ from bionic */
#ifndef GLIBC_RTLD_DEFAULT
#define GLIBC_RTLD_DEFAULT ((void*)0)
#endif
#ifndef GLIBC_RTLD_NEXT  
#define GLIBC_RTLD_NEXT ((void*)-1)
#endif

/* External: lookup symbol in glibc-bridge's wrapper symbol table */
extern void* glibc_bridge_lookup_symbol(const char* name);

/* External: load glibc shared library */
extern void* glibc_bridge_dlopen_glibc_lib(const char* path);
extern void* glibc_bridge_resolve_from_shared_libs(const char* name);

/* ICU library redirect table */
static const struct { const char* prefix; const char* android; } g_icu_map[] = {
    {"libicuuc.so",   "/apex/com.android.i18n/lib64/libicuuc.so"},
    {"libicui18n.so", "/apex/com.android.i18n/lib64/libicui18n.so"},
    {"libicudata.so", "/apex/com.android.i18n/lib64/libicuuc.so"},
    {NULL, NULL}
};

/* Native library redirect table - redirect x86_64 library requests to ARM64 native libs
 * These libraries are provided by RotatingartLauncher and loaded by the app process
 * 
 * NOTE: SDL2 IS in this list because Box64's wrappedsdl2_init() calls dlopen() to load
 * the native library. Box64's wrapper mechanism works at the FUNCTION level (wrapping
 * SDL_Init, audio callbacks, etc.), not at the dlopen level. So glibc_bridge still needs
 * to redirect the library name to the Android native library.
 */
static const struct { const char* prefix; const char* native_lib; } g_native_lib_map[] = {
    /* SDL2 - redirect to RotatingartLauncher's native SDL2
     * Box64's wrappedsdl2.c wraps the SDL functions for proper callback bridging */
    {"libSDL2-2.0.so",  "libSDL2.so"},
    {"libSDL2.so",      "libSDL2.so"},
    
    /* OpenGL - use gl4es to translate GL to GLES */
    {"libGL.so.1",      "libGL_gl4es.so"},
    {"libGL.so",        "libGL_gl4es.so"},
    {"libGLU.so.1",     "libGL_gl4es.so"},  /* GLU calls redirect to gl4es */
    {"libGLU.so",       "libGL_gl4es.so"},
    
    /* EGL */
    {"libEGL.so.1",     "libEGL_gl4es.so"},
    {"libEGL.so",       "libEGL_gl4es.so"},
    
    /* Audio - FAudio */
    {"libopenal.so",    "libopenal32.so"},
    {"libOpenAL.so",    "libopenal32.so"},
    
    /* NOTE: libstdc++.so.6 should NOT be redirected here!
     * It's a glibc C++ library that needs to be loaded from rootfs,
     * not from Android's libc++_shared.so (ABI incompatible).
     * Box64 should find it via BOX64_LD_LIBRARY_PATH. */
    
    {NULL, NULL}
};

/* Simple dlopen wrapper that can load glibc .so files */
void* dlopen_wrapper(const char* filename, int flags) {
    SET_WRAPPER("dlopen");

    /* ALWAYS log dlopen calls to stderr for debugging */
    {
        char buf[256];
        snprintf(buf, sizeof(buf), "[DLOPEN_WRAPPER] dlopen('%s', 0x%x) CALLED\n",
                 filename ? filename : "(null)", flags);
        write(STDERR_FILENO, buf, strlen(buf));
    }

#ifdef __ANDROID__
    if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLOPEN",
            "dlopen('%s', 0x%x)", filename ? filename : "(null)", flags);
    }
#endif
    
    if (!filename) {
        /* dlopen(NULL) - return handle to main program */
        void* result = dlopen(NULL, flags);
        CLEAR_WRAPPER();
        return result;
    }
    
    /* Check ICU redirect first */
    const char* base = strrchr(filename, '/');
    base = base ? base + 1 : filename;
    for (int i = 0; g_icu_map[i].prefix; i++) {
        if (strncmp(base, g_icu_map[i].prefix, strlen(g_icu_map[i].prefix)) == 0) {
            void* h = dlopen(g_icu_map[i].android, flags | RTLD_GLOBAL);
#ifdef __ANDROID__
            if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
                __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_ICU", 
                    "ICU redirect: %s -> %s = %p", filename, g_icu_map[i].android, h);
            }
#endif
            if (h) { CLEAR_WRAPPER(); return h; }
        }
    }
    
    /* Check native library redirect (SDL2, GL, etc.) 
     * These are ARM64 libraries loaded by RotatingartLauncher that Box64 can use via wrapped calls */
    for (int i = 0; g_native_lib_map[i].prefix; i++) {
        if (strncmp(base, g_native_lib_map[i].prefix, strlen(g_native_lib_map[i].prefix)) == 0) {
            void* h = dlopen(g_native_lib_map[i].native_lib, flags | RTLD_GLOBAL);
            {
                char buf[256];
                snprintf(buf, sizeof(buf), "[DLOPEN] Native redirect: %s -> %s = %p\n", 
                         filename, g_native_lib_map[i].native_lib, h);
                write(STDERR_FILENO, buf, strlen(buf));
            }
#ifdef __ANDROID__
            __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_NATIVE", 
                "Native redirect: %s -> %s = %p", filename, g_native_lib_map[i].native_lib, h);
#endif
            if (h) {
                /* Special handling for SDL2: Initialize JNI and pre-cache environment variables
                 * This prevents StackOverflowError when SDL makes JNI calls from deep Box64 call stack.
                 * Box64 adds significant stack depth, and by the time SDL_getenv() is called
                 * (during SDL_InitSubSystem), the stack may be near the 9MB limit.
                 * Pre-calling these functions caches the results so JNI isn't needed later. */
                if (strstr(g_native_lib_map[i].prefix, "SDL2") != NULL) {
                    /* 1. Setup JNI thread first */
                    typedef int (*SDL_JNI_SetupThread_t)(void);
                    SDL_JNI_SetupThread_t setup_fn = (SDL_JNI_SetupThread_t)dlsym(h, "Android_JNI_SetupThread");
                    if (setup_fn) {
                        int ret = setup_fn();
                        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_SDL2", 
                            "SDL2 JNI thread setup called: result=%d", ret);
                    }
                    
                    /* 2. Pre-cache manifest environment variables - this is the key fix!
                     * SDL_getenv() calls Android_JNI_GetManifestEnvironmentVariables() which
                     * does a JNI call. When called from deep Box64 stack, this causes stack overflow.
                     * Calling it here (before Box64 runs) caches the result. */
                    typedef int (*SDL_JNI_GetManifestEnv_t)(void);
                    SDL_JNI_GetManifestEnv_t getenv_fn = (SDL_JNI_GetManifestEnv_t)dlsym(h, "Android_JNI_GetManifestEnvironmentVariables");
                    if (getenv_fn) {
                        int ret = getenv_fn();
                        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_SDL2", 
                            "SDL2 manifest env vars pre-cached: result=%d", ret);
                    } else {
                        __android_log_print(ANDROID_LOG_WARN, "GLIBC_BRIDGE_SDL2", 
                            "Android_JNI_GetManifestEnvironmentVariables not found in SDL2");
                    }
                    
                    /* 3. Also call SDL_SetMainReady to bypass SDL_main requirement */
                    typedef void (*SDL_SetMainReady_t)(void);
                    SDL_SetMainReady_t mainready_fn = (SDL_SetMainReady_t)dlsym(h, "SDL_SetMainReady");
                    if (mainready_fn) {
                        mainready_fn();
                        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_SDL2", 
                            "SDL_SetMainReady called");
                    }
                }
                CLEAR_WRAPPER(); 
                return h; 
            }
            /* If native lib not found, continue to try other methods */
        }
    }
    
    /* Try to load as glibc library first if it's a .so file */
    if (strstr(filename, ".so")) {
        /* Build full path if needed */
        char full_path[512];
        const char* path_to_load = filename;
        
#ifdef __ANDROID__
        /* Debug: print g_glibc_root and search attempts */
        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_DLOPEN", 
            "Searching for '%s', g_glibc_root='%s'", filename, g_glibc_root);
#endif
        
        if (filename[0] != '/') {
            /* Relative path - search in glibc root subdirectories */
            if (g_glibc_root[0]) {
                /* Search in multiple library directories (multiarch support) */
                static const char* lib_subdirs[] = {
                    "usr/lib/aarch64-linux-gnu",  /* Debian multiarch - primary for ARM64 native libs */
                    "lib/aarch64-linux-gnu",      /* Debian multiarch /lib */
                    "lib",                        /* Standard /lib */
                    "usr/lib",                    /* Standard /usr/lib */
                    NULL
                };
                int found = 0;
                for (const char** subdir = lib_subdirs; *subdir; subdir++) {
                    snprintf(full_path, sizeof(full_path), "%s/%s/%s", g_glibc_root, *subdir, filename);
                    int acc_result = access(full_path, R_OK);
#ifdef __ANDROID__
                    __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLOPEN", 
                        "Trying path: %s -> access=%d", full_path, acc_result);
#endif
                    if (acc_result == 0) {
                        path_to_load = full_path;
                        found = 1;
#ifdef __ANDROID__
                        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_DLOPEN", 
                            "Found lib in %s: %s", *subdir, full_path);
#endif
                        break;
                    }
                }
#ifdef __ANDROID__
                if (!found) {
                    __android_log_print(ANDROID_LOG_WARN, "GLIBC_BRIDGE_DLOPEN", 
                        "Library %s not found in any rootfs subdirectory", filename);
                }
#endif
            } else {
#ifdef __ANDROID__
                __android_log_print(ANDROID_LOG_WARN, "GLIBC_BRIDGE_DLOPEN", 
                    "g_glibc_root is empty, cannot search for %s", filename);
#endif
            }
        } else if (g_glibc_root[0]) {
            /* Absolute path - translate paths like /tmp, /usr, /lib to glibc_root */
            /* Skip paths that should remain real Android paths */
            int should_translate = 1;
            if (strncmp(filename, "/data", 5) == 0 ||
                strncmp(filename, "/system", 7) == 0 ||
                strncmp(filename, "/vendor", 7) == 0 ||
                strncmp(filename, "/apex", 5) == 0 ||
                strncmp(filename, g_glibc_root, strlen(g_glibc_root)) == 0) {
                should_translate = 0;
            }
            
            if (should_translate) {
                snprintf(full_path, sizeof(full_path), "%s%s", g_glibc_root, filename);
                if (access(full_path, R_OK) == 0) {
                    path_to_load = full_path;
#ifdef __ANDROID__
                    if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
                        __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLOPEN", 
                            "Path translated: %s -> %s", filename, full_path);
                    }
#endif
                }
            }
        }
        
        /* Try to load as glibc library with full dlopen support */
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_DLOPEN", 
            "Calling glibc_bridge_dlopen_glibc_lib('%s')", path_to_load);
#endif
        void* handle = glibc_bridge_dlopen_glibc_lib(path_to_load);
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_DLOPEN", 
            "glibc_bridge_dlopen_glibc_lib returned: %p", handle);
#endif
        if (handle) {
            /* Log the returned handle to stderr for debugging */
            {
                char buf[256];
                snprintf(buf, sizeof(buf), "[DLOPEN] Returning glibc handle: %p for '%s'\n",
                         handle, path_to_load);
                write(STDERR_FILENO, buf, strlen(buf));
            }
#ifdef __ANDROID__
            __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_DLOPEN",
                "Successfully loaded as glibc lib: %s -> handle %p", path_to_load, handle);
#endif
            CLEAR_WRAPPER();
            return handle;
        } else {
#ifdef __ANDROID__
            __android_log_print(ANDROID_LOG_WARN, "GLIBC_BRIDGE_DLOPEN", 
                "glibc_bridge_dlopen_glibc_lib failed for '%s'", path_to_load);
#endif
        }
    }
    
    /* Fall back to bionic dlopen - also try translated path */
    const char* bionic_path = filename;
    char bionic_full_path[512];
    if (filename[0] == '/' && g_glibc_root[0] &&
        strncmp(filename, "/data", 5) != 0 &&
        strncmp(filename, "/system", 7) != 0 &&
        strncmp(filename, "/vendor", 7) != 0 &&
        strncmp(filename, "/apex", 5) != 0 &&
        strncmp(filename, g_glibc_root, strlen(g_glibc_root)) != 0) {
        snprintf(bionic_full_path, sizeof(bionic_full_path), "%s%s", g_glibc_root, filename);
        if (access(bionic_full_path, R_OK) == 0) {
            bionic_path = bionic_full_path;
#ifdef __ANDROID__
            if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
                __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLOPEN", 
                    "bionic path translated: %s -> %s", filename, bionic_full_path);
            }
#endif
        }
    }
    void* result = dlopen(bionic_path, flags);
    
#ifdef __ANDROID__
    if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLOPEN", 
            "bionic dlopen('%s') -> %p", filename, result);
    }
#endif
    
    /* For pointer-returning functions like dlopen, sync errno based on NULL check
     * CRITICAL: Do NOT cast pointer to long - on x64 Windows, long is 32-bit and will truncate 64-bit pointers */
    if (result == NULL) {
        SYNC_ERRNO();  /* Failed - sync errno with logging */
    } else {
        SYNC_ERRNO_SILENT();  /* Success - sync errno silently */
    }
    CLEAR_WRAPPER();
    return result;
}

/* ============================================================================
 * Exported functions for Box64 integration
 * These allow Box64's wrapped libraries to use glibc_bridge's dlopen/dlsym
 * for proper library redirection (SDL2, GL4ES, etc.)
 * ============================================================================ */

__attribute__((visibility("default")))
void* glibc_bridge_dlopen_for_box64(const char* filename, int flags) {
    return dlopen_wrapper(filename, flags);
}

__attribute__((visibility("default")))
void* glibc_bridge_dlsym_for_box64(void* handle, const char* symbol) {
    return dlsym_wrapper(handle, symbol);
}

/* External: check if handle is a glibc shared lib */
extern int glibc_bridge_is_glibc_handle(void* handle);

/* External: dladdr lookup for our loaded libraries */
extern int glibc_bridge_dladdr_lookup(const void* addr, Dl_info* info);

/* dlclose wrapper - prevent bionic from trying to unload glibc-bridge-loaded libraries */
int dlclose_wrapper(void* handle) {
    SET_WRAPPER("dlclose");
    
    if (!handle) {
        CLEAR_WRAPPER();
        return 0;
    }
    
    /* Check if this is a glibc-bridge-loaded glibc library */
    if (glibc_bridge_is_glibc_handle(handle)) {
        /* Don't actually close - glibc-bridge manages these libraries.
         * Calling bionic's dlclose on these would crash because
         * bionic's linker doesn't know about them (they're not in soinfo list).
         * Just return success. */
#ifdef __ANDROID__
        if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
            __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLCLOSE",
                "dlclose(%p) - glibc-bridge library, skipping bionic dlclose", handle);
        }
#endif
        CLEAR_WRAPPER();
        return 0;
    }
    
    /* For bionic-loaded libraries, pass through to real dlclose */
    int result = dlclose(handle);
    
#ifdef __ANDROID__
    if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLCLOSE",
            "dlclose(%p) -> bionic dlclose returned %d", handle, result);
    }
#endif
    
    CLEAR_WRAPPER();
    return result;
}

/* dladdr wrapper - look up symbol info by address */
int dladdr_wrapper(const void* addr, Dl_info* info) {
    SET_WRAPPER("dladdr");
    
    if (!addr || !info) {
        CLEAR_WRAPPER();
        return 0;
    }
    
    /* First check our glibc-bridge-loaded glibc libraries */
    if (glibc_bridge_dladdr_lookup(addr, info)) {
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLADDR",
            "dladdr(%p) -> glibc-bridge lib: %s, base=%p, sym=%s@%p",
            addr, info->dli_fname ? info->dli_fname : "(null)",
            info->dli_fbase,
            info->dli_sname ? info->dli_sname : "(null)",
            info->dli_saddr);
#endif
        CLEAR_WRAPPER();
        return 1;
    }
    
    /* Fall back to bionic's dladdr for other libraries */
    int result = dladdr(addr, info);
    
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_DLADDR",
        "dladdr(%p) -> bionic: %s, base=%p, sym=%s@%p (ret=%d)",
        addr, info->dli_fname ? info->dli_fname : "(null)",
        info->dli_fbase,
        info->dli_sname ? info->dli_sname : "(null)",
        info->dli_saddr,
        result);
#endif
    
    CLEAR_WRAPPER();
    return result;
}

extern void* glibc_bridge_dlsym_from_handle(void* handle, const char* name);

/* Forward declaration for PAL_RegisterModule_wrapper */
int PAL_RegisterModule_wrapper(const char* name);

void* dlsym_wrapper(void* handle, const char* symbol) {
    SET_WRAPPER("dlsym");

    /* CRITICAL: Log to stderr for debugging in forked processes */
    {
        char buf[256];
        snprintf(buf, sizeof(buf), "[DLSYM] dlsym(handle=%p, symbol='%s')\n",
                 handle, symbol ? symbol : "(null)");
        write(STDERR_FILENO, buf, strlen(buf));
    }



    /* Check if handle is glibc's RTLD_DEFAULT or RTLD_NEXT or dlopen(NULL) result */
    int is_default = (handle == GLIBC_RTLD_DEFAULT || handle == RTLD_DEFAULT || handle == NULL);
    int is_next = (handle == GLIBC_RTLD_NEXT || handle == RTLD_NEXT);
    int is_glibc_handle = glibc_bridge_is_glibc_handle(handle);

    void* result = NULL;
    
    /* CRITICAL: Special handling for PAL_RegisterModule - always return our wrapper
     * if the symbol is requested, regardless of handle. This is needed because
     * .NET CoreCLR may look for this symbol in libcoreclr.so, but it's not exported there.
     * BOX64 may call dlsym directly, bypassing our wrapper, so we need to handle
     * this at the earliest possible point. */
    if (symbol && strcmp(symbol, "PAL_RegisterModule") == 0) {
        result = (void*)PAL_RegisterModule_wrapper;
        {
            char buf[256];
            snprintf(buf, sizeof(buf), "[DLSYM] PAL_RegisterModule special case -> wrapper %p\n", result);
            write(STDERR_FILENO, buf, strlen(buf));
        }
        CLEAR_WRAPPER();
        return result;
    }

    /* For glibc-loaded libraries, first search from that specific handle */
    if (is_glibc_handle) {
        result = glibc_bridge_dlsym_from_handle(handle, symbol);
        if (!result) result = glibc_bridge_resolve_from_shared_libs(symbol);
        if (!result) result = glibc_bridge_lookup_symbol(symbol);

        /* Log to stderr */
        {
            char buf[256];
            snprintf(buf, sizeof(buf), "[DLSYM] glibc handle %p -> result=%p\n", handle, result);
            write(STDERR_FILENO, buf, strlen(buf));
        }


        CLEAR_WRAPPER();
        return result;
    }
    
    /* For RTLD_DEFAULT/RTLD_NEXT or NULL handle:
     * First try glibc-bridge's wrapper symbol table to provide glibc-compatible implementations.
     * This is crucial for programs that use dlopen(NULL) + dlsym to get
     * glibc functions like __ctype_b_loc, __ctype_tolower_loc, etc. */
    if (is_default || is_next || handle == NULL || is_glibc_handle) {
        /* First: check glibc shared libs */
        if (!result) {
            result = glibc_bridge_resolve_from_shared_libs(symbol);
        }
        
        /* Then: check glibc-bridge's wrapper symbol table */
        if (!result) {
            result = glibc_bridge_lookup_symbol(symbol);
        }
        
        if (result) {
            CLEAR_WRAPPER();
            return result;
        }
        
        /* Try bionic's RTLD_DEFAULT as fallback */
        result = dlsym(RTLD_DEFAULT, symbol);
    } else {
        /* For specific handles: first check our wrappers, then the actual handle */
        result = glibc_bridge_resolve_from_shared_libs(symbol);
        if (!result) {
            result = glibc_bridge_lookup_symbol(symbol);
        }
        if (!result) {
            result = dlsym(handle, symbol);
        }
    }
    
    /* For pointer-returning functions like dlsym, sync errno based on NULL check
     * CRITICAL: Do NOT cast pointer to long - on x64 Windows, long is 32-bit and will truncate 64-bit pointers */
    if (result == NULL) {
        SYNC_ERRNO();  /* Failed - sync errno with logging */
    } else {
        SYNC_ERRNO_SILENT();  /* Success - sync errno silently */
    }
    
    CLEAR_WRAPPER();
    return result;
}

/* ============================================================================
 * Stack Protection Wrapper
 * ============================================================================ */

/**
 * __stack_chk_guard - stack canary value
 * 
 * This is a data symbol that glibc programs read to get the stack canary.
 * We expose bionic's __stack_chk_guard directly.
 */
extern uintptr_t __stack_chk_guard;

void* glibc_bridge_get_stack_chk_guard(void) {
    return &__stack_chk_guard;
}

/**
 * __stack_chk_fail wrapper - intercept stack canary failure
 * 
 * Print detailed info and abort to help debug.
 */
void __stack_chk_fail_wrapper(void) {
    /* Use stderr first as it's more likely to work even with corrupted stack */
    const char* msg = "\n!!! STACK CANARY CHECK FAILED (glibc-bridge) !!!\n";
    write(STDERR_FILENO, msg, strlen(msg));
    
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "=== STACK CANARY CHECK FAILED ===");
    
    /* Try to get return address for debugging */
    void* ret_addr = __builtin_return_address(0);
    void* frame_addr = __builtin_frame_address(0);
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "  Return addr: %p  Frame addr: %p", ret_addr, frame_addr);
    
    /* Print stack guard value */
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "  Stack guard: 0x%lx", (unsigned long)__stack_chk_guard);
    
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "=================================");
#endif
    
    /* Write to stderr for visibility in logcat [STDERR] */
    char buf[256];
    snprintf(buf, sizeof(buf), "STACK FAIL: ret=%p frame=%p guard=0x%lx\n",
        __builtin_return_address(0), __builtin_frame_address(0),
        (unsigned long)__stack_chk_guard);
    write(STDERR_FILENO, buf, strlen(buf));
    
    /* Call the real abort to generate proper crash info */
    abort();
}

/* ============================================================================
 * FORTIFY_SOURCE Wrappers - glibc security hardening functions
 * ============================================================================ */

/**
 * __explicit_bzero_chk - secure zero memory with buffer overflow check
 * 
 * Used by glibc when compiled with FORTIFY_SOURCE.
 * Clears memory and prevents compiler from optimizing it away.
 */
void __explicit_bzero_chk_wrapper(void* dest, size_t len, size_t destlen) {
    if (len > destlen) {
        /* Buffer overflow detected */
        const char* msg = "[FORTIFY] __explicit_bzero_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    /* Use volatile to prevent compiler optimization */
    volatile unsigned char* p = (volatile unsigned char*)dest;
    while (len--) {
        *p++ = 0;
    }
    /* Memory barrier to ensure the writes are not optimized away */
    __asm__ __volatile__("" ::: "memory");
}

/**
 * __mbstowcs_chk - multibyte to wide string with buffer overflow check
 * 
 * Used by glibc when compiled with FORTIFY_SOURCE.
 */
size_t __mbstowcs_chk_wrapper(wchar_t* dest, const char* src, size_t n, size_t destlen) {
    if (dest != NULL && n > destlen) {
        /* Buffer overflow detected */
        const char* msg = "[FORTIFY] __mbstowcs_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return mbstowcs(dest, src, n);
}

/**
 * __wcstombs_chk - wide to multibyte string with buffer overflow check
 */
size_t __wcstombs_chk_wrapper(char* dest, const wchar_t* src, size_t n, size_t destlen) {
    if (dest != NULL && n > destlen) {
        const char* msg = "[FORTIFY] __wcstombs_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return wcstombs(dest, src, n);
}

/**
 * __memcpy_chk - memcpy with buffer overflow check
 */
void* __memcpy_chk_wrapper(void* dest, const void* src, size_t n, size_t destlen) {
    if (n > destlen) {
        const char* msg = "[FORTIFY] __memcpy_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return memcpy(dest, src, n);
}

/**
 * __memmove_chk - memmove with buffer overflow check
 */
void* __memmove_chk_wrapper(void* dest, const void* src, size_t n, size_t destlen) {
    if (n > destlen) {
        const char* msg = "[FORTIFY] __memmove_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return memmove(dest, src, n);
}

/**
 * __memset_chk - memset with buffer overflow check
 */
void* __memset_chk_wrapper(void* dest, int c, size_t n, size_t destlen) {
    if (n > destlen) {
        const char* msg = "[FORTIFY] __memset_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return memset(dest, c, n);
}

/**
 * __strcpy_chk - strcpy with buffer overflow check
 */
char* __strcpy_chk_wrapper(char* dest, const char* src, size_t destlen) {
    size_t srclen = strlen(src) + 1;
    if (srclen > destlen) {
        const char* msg = "[FORTIFY] __strcpy_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return strcpy(dest, src);
}

/**
 * __strncpy_chk - strncpy with buffer overflow check
 */
char* __strncpy_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen) {
    if (n > destlen) {
        const char* msg = "[FORTIFY] __strncpy_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return strncpy(dest, src, n);
}

/**
 * __strcat_chk - strcat with buffer overflow check
 */
char* __strcat_chk_wrapper(char* dest, const char* src, size_t destlen) {
    size_t destused = strlen(dest);
    size_t srclen = strlen(src) + 1;
    if (destused + srclen > destlen) {
        const char* msg = "[FORTIFY] __strcat_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return strcat(dest, src);
}

/**
 * __strncat_chk - strncat with buffer overflow check
 */
char* __strncat_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen) {
    size_t destused = strlen(dest);
    if (destused + n + 1 > destlen) {
        const char* msg = "[FORTIFY] __strncat_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return strncat(dest, src, n);
}

/**
 * __readlinkat_chk - readlinkat with buffer overflow check
 */
ssize_t __readlinkat_chk_wrapper(int dirfd, const char* pathname, char* buf, size_t bufsiz, size_t buflen) {
    if (bufsiz > buflen) {
        const char* msg = "[FORTIFY] __readlinkat_chk: buffer overflow detected!\n";
        write(STDERR_FILENO, msg, strlen(msg));
        abort();
    }
    return readlinkat(dirfd, pathname, buf, bufsiz);
}

/**
 * __openat64_2 - openat64 fortify version (requires mode when O_CREAT used)
 */
int __openat64_2_wrapper(int dirfd, const char* pathname, int flags) {
    /* This version is called when O_CREAT is not in flags, so mode is not needed */
    return openat(dirfd, pathname, flags);
}

/* ============================================================================
 * glibc-specific Functions (not in bionic)
 * ============================================================================ */

/**
 * parse_printf_format - parse printf format string (glibc extension)
 * Returns the number of arguments required by the format string.
 * We provide a stub that returns 0 (not fully implemented).
 */
size_t parse_printf_format_wrapper(const char* fmt, size_t n, int* argtypes) {
    (void)fmt;
    (void)n;
    (void)argtypes;
    /* Not implemented - return 0 to indicate no arguments parsed */
    return 0;
}

/**
 * strerrorname_np - get error name string (glibc 2.32+)
 * Returns the name of the error (e.g., "ENOENT" for error 2)
 */
const char* strerrorname_np_wrapper(int errnum) {
    /* Common error names */
    switch (errnum) {
        case 0: return "0";
        case EPERM: return "EPERM";
        case ENOENT: return "ENOENT";
        case ESRCH: return "ESRCH";
        case EINTR: return "EINTR";
        case EIO: return "EIO";
        case ENXIO: return "ENXIO";
        case E2BIG: return "E2BIG";
        case ENOEXEC: return "ENOEXEC";
        case EBADF: return "EBADF";
        case ECHILD: return "ECHILD";
        case EAGAIN: return "EAGAIN";
        case ENOMEM: return "ENOMEM";
        case EACCES: return "EACCES";
        case EFAULT: return "EFAULT";
        case EBUSY: return "EBUSY";
        case EEXIST: return "EEXIST";
        case EXDEV: return "EXDEV";
        case ENODEV: return "ENODEV";
        case ENOTDIR: return "ENOTDIR";
        case EISDIR: return "EISDIR";
        case EINVAL: return "EINVAL";
        case ENFILE: return "ENFILE";
        case EMFILE: return "EMFILE";
        case ENOTTY: return "ENOTTY";
        case EFBIG: return "EFBIG";
        case ENOSPC: return "ENOSPC";
        case ESPIPE: return "ESPIPE";
        case EROFS: return "EROFS";
        case EMLINK: return "EMLINK";
        case EPIPE: return "EPIPE";
        case EDOM: return "EDOM";
        case ERANGE: return "ERANGE";
        case EDEADLK: return "EDEADLK";
        case ENAMETOOLONG: return "ENAMETOOLONG";
        case ENOLCK: return "ENOLCK";
        case ENOSYS: return "ENOSYS";
        case ENOTEMPTY: return "ENOTEMPTY";
        case ELOOP: return "ELOOP";
        case ENOTSOCK: return "ENOTSOCK";
        case ECONNREFUSED: return "ECONNREFUSED";
        case ETIMEDOUT: return "ETIMEDOUT";
        default: return NULL;
    }
}

/**
 * strerrordesc_np - get error description string (glibc 2.32+)
 * Returns a description of the error (e.g., "No such file or directory" for ENOENT)
 */
const char* strerrordesc_np_wrapper(int errnum) {
    /* Use strerror since Android bionic has it and it's thread-safe */
    return strerror(errnum);
}

/**
 * get_current_dir_name - get current working directory (glibc extension)
 * Returns malloc'd string containing the current directory
 */
char* get_current_dir_name_wrapper(void) {
    char* buf = (char*)malloc(PATH_MAX);
    if (!buf) return NULL;
    if (getcwd(buf, PATH_MAX) == NULL) {
        free(buf);
        return NULL;
    }
    return buf;
}

/**
 * getdtablesize - get file descriptor table size (obsolete but glibc has it)
 */
int getdtablesize_wrapper(void) {
    struct rlimit rl;
    if (getrlimit(RLIMIT_NOFILE, &rl) == 0) {
        return (int)rl.rlim_cur;
    }
    return 256; /* fallback */
}

/**
 * sigisemptyset - check if signal set is empty (glibc extension)
 */
int sigisemptyset_wrapper(const sigset_t* set) {
    sigset_t empty;
    sigemptyset(&empty);
    return memcmp(set, &empty, sizeof(sigset_t)) == 0 ? 1 : 0;
}

/**
 * Linux-specific syscall wrappers - these are newer syscalls
 * We provide stubs that return -ENOSYS
 */

/* open_tree - Linux 5.2+ syscall */
int open_tree_wrapper(int dirfd, const char* pathname, unsigned int flags) {
    (void)dirfd;
    (void)pathname;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

/* pidfd_open - Linux 5.3+ syscall */
int pidfd_open_wrapper(pid_t pid, unsigned int flags) {
    (void)pid;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

/* pidfd_send_signal - Linux 5.1+ syscall */
int pidfd_send_signal_wrapper(int pidfd, int sig, siginfo_t* info, unsigned int flags) {
    (void)pidfd;
    (void)sig;
    (void)info;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

/* name_to_handle_at - Linux 2.6.39+ syscall */
int name_to_handle_at_wrapper(int dirfd, const char* pathname, void* handle, 
                               int* mount_id, int flags) {
    (void)dirfd;
    (void)pathname;
    (void)handle;
    (void)mount_id;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

/* ============================================================================
 * abort wrapper - intercept abort() to debug who is calling it
 * ============================================================================ */
void abort_wrapper(void) __attribute__((noreturn));
void abort_wrapper(void) {
    /* Use stderr first as it's more likely to work even with corrupted stack */
    const char* msg = "\n!!! ABORT CALLED (glibc-bridge) !!!\n";
    write(STDERR_FILENO, msg, strlen(msg));
    
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "=== ABORT() CALLED ===");
    
    /* Try to get return address for debugging */
    void* ret_addr = __builtin_return_address(0);
    void* frame_addr = __builtin_frame_address(0);
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "  Caller: %p  Frame: %p", ret_addr, frame_addr);
    
    /* Try to get more context from backtrace */
    void* ret_addr1 = __builtin_return_address(1);
    void* ret_addr2 = __builtin_return_address(2);
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "  Backtrace: %p -> %p -> %p", ret_addr, ret_addr1, ret_addr2);
    
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "=========================");
#endif
    
    /* Write to stderr for visibility in logcat [STDERR] */
    char buf[256];
    int len = snprintf(buf, sizeof(buf), "ABORT: caller=%p frame=%p back=%p\n",
        __builtin_return_address(0), __builtin_frame_address(0),
        __builtin_return_address(1));
    write(STDERR_FILENO, buf, len);
    
    /* Call the real abort */
    abort();
}

/* ============================================================================
 * clock_gettime wrapper
 * struct timespec is compatible between glibc and bionic on 64-bit
 * ============================================================================ */
int clock_gettime_wrapper(clockid_t clk_id, struct timespec *tp) {
    /* Map glibc clock IDs to bionic clock IDs if needed */
    /* Most clock IDs are the same: CLOCK_REALTIME=0, CLOCK_MONOTONIC=1 */
    int ret = clock_gettime(clk_id, tp);
    return ret;
}

/* ============================================================================
 * nanosleep wrapper
 * ============================================================================ */
int nanosleep_wrapper(const struct timespec *req, struct timespec *rem) {
    return nanosleep(req, rem);
}

/* div/ldiv/lldiv use direct pass-through - struct layout matches */

/* ============================================================================
 * isgraph wrapper - bionic isgraph may behave slightly differently
 * ============================================================================ */
int isgraph_wrapper(int c) {
    /* isgraph returns non-zero for printable chars except space */
    return isgraph(c);
}

/* ============================================================================
 * select/pselect wrappers
 * 
 * These are mostly compatible, but pselect's sigmask needs conversion
 * sigset_t size matches bionic (8 bytes).
 * ============================================================================ */
#include <sys/select.h>

int select_wrapper(int nfds, fd_set *readfds, fd_set *writefds,
                   fd_set *exceptfds, struct timeval *timeout) {
    LOG_DEBUG("select_wrapper: nfds=%d", nfds);
    return proot_select(nfds, readfds, writefds, exceptfds, timeout);
}

int pselect_wrapper(int nfds, fd_set *readfds, fd_set *writefds,
                    fd_set *exceptfds, const struct timespec *timeout,
                    const sigset_t *sigmask) {
    WRAPPER_BEGIN("pselect");
    int ret = pselect(nfds, readfds, writefds, exceptfds, timeout, sigmask);
    WRAPPER_RETURN(ret);
}

/* __errno_location_wrapper is in glibc_bridge_tls.c */

/* ============================================================================
 * getaddrinfo wrapper - struct addrinfo is compatible
 * ============================================================================ */
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>

int getaddrinfo_wrapper(const char *node, const char *service,
                        const struct addrinfo *hints, struct addrinfo **res) {
    return getaddrinfo(node, service, hints, res);
}

/* ============================================================================
 * inet_pton wrapper
 * ============================================================================ */
#include <arpa/inet.h>

int inet_pton_wrapper(int af, const char *src, void *dst) {
    return inet_pton(af, src, dst);
}

/* ============================================================================
 * Wide character function wrappers
 * ============================================================================ */
#include <wchar.h>

wchar_t* wcschr_wrapper(const wchar_t *wcs, wchar_t wc) {
    return wcschr(wcs, wc);
}

wchar_t* wcsrchr_wrapper(const wchar_t *wcs, wchar_t wc) {
    return wcsrchr(wcs, wc);
}

wchar_t* wcspbrk_wrapper(const wchar_t *wcs, const wchar_t *accept) {
    return wcspbrk(wcs, accept);
}

wchar_t* wmemcpy_wrapper(wchar_t *dest, const wchar_t *src, size_t n) {
    return wmemcpy(dest, src, n);
}

wchar_t* wmemset_wrapper(wchar_t *wcs, wchar_t wc, size_t n) {
    return wmemset(wcs, wc, n);
}

double wcstod_wrapper(const wchar_t *nptr, wchar_t **endptr) {
    return wcstod(nptr, endptr);
}

/* ============================================================================
 * rawmemchr - glibc-specific function not in bionic
 * ============================================================================ */

void* rawmemchr_wrapper(const void* s, int c) {
    /* rawmemchr is like memchr but doesn't check bounds - assumes c exists */
    const unsigned char* p = (const unsigned char*)s;
    while (*p != (unsigned char)c) {
        p++;
    }
    return (void*)p;
}

/* ============================================================================
 * __xmknod - glibc internal mknod wrapper
 * ============================================================================ */

int __xmknod_wrapper(int ver, const char* path, mode_t mode, dev_t* dev) {
    (void)ver;  /* Version argument ignored */
    return mknod(path, mode, dev ? *dev : 0);
}

/* ============================================================================
 * crypt - Password encryption
 * 
 * Android's bionic doesn't have crypt() in the main libc.
 * We provide a simple DES-based implementation for compatibility.
 * ============================================================================ */

/* Simple crypt implementation - returns static buffer with encoded password */
static char g_crypt_result[128];

char* crypt_wrapper(const char* key, const char* salt) {
    SET_WRAPPER("crypt");
    
    if (!key || !salt) {
        errno = EINVAL;
        return NULL;
    }
    
    /* Simple implementation: just create a hash-like string
     * Real crypt() uses DES/MD5/SHA, but for testing we use a simple approach */
    
    /* Copy salt (first 2 chars) */
    g_crypt_result[0] = salt[0];
    g_crypt_result[1] = salt[1] ? salt[1] : salt[0];
    
    /* Create a simple hash of the key */
    unsigned long hash = 5381;
    const char* p = key;
    while (*p) {
        hash = ((hash << 5) + hash) + *p;
        p++;
    }
    
    /* Encode hash as printable characters */
    static const char* chars = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    for (int i = 2; i < 13; i++) {
        g_crypt_result[i] = chars[hash % 64];
        hash /= 64;
        if (hash == 0) hash = (unsigned long)(key[0]) * 31337;
    }
    g_crypt_result[13] = '\0';
    
    LOG_DEBUG("crypt_wrapper: key='%s', salt='%s' -> '%s'", key, salt, g_crypt_result);
    
    return g_crypt_result;
}

/* Thread-safe version using caller-provided buffer */
struct crypt_data {
    char output[128];
    char initialized;
};

char* crypt_r_wrapper(const char* key, const char* salt, struct crypt_data* data) {
    SET_WRAPPER("crypt_r");
    
    if (!key || !salt || !data) {
        errno = EINVAL;
        return NULL;
    }
    
    /* Copy salt (first 2 chars) */
    data->output[0] = salt[0];
    data->output[1] = salt[1] ? salt[1] : salt[0];
    
    /* Create a simple hash of the key */
    unsigned long hash = 5381;
    const char* p = key;
    while (*p) {
        hash = ((hash << 5) + hash) + *p;
        p++;
    }
    
    /* Encode hash as printable characters */
    static const char* chars = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    for (int i = 2; i < 13; i++) {
        data->output[i] = chars[hash % 64];
        hash /= 64;
        if (hash == 0) hash = (unsigned long)(key[0]) * 31337;
    }
    data->output[13] = '\0';
    data->initialized = 1;
    
    LOG_DEBUG("crypt_r_wrapper: key='%s', salt='%s' -> '%s'", key, salt, data->output);
    
    return data->output;
}

/* ============================================================================
 * POSIX Message Queue Functions (mqueue)
 * Android bionic doesn't support mqueue, so we provide stub implementations
 * ============================================================================ */
#include <fcntl.h>
#include <sys/stat.h>

/* Simple in-memory message queue implementation for compatibility */
#define MQ_MAX_QUEUES 16
#define MQ_MAX_MESSAGES 64
#define MQ_MAX_MSGSIZE 8192

typedef struct {
    char name[256];
    int in_use;
    long flags;
    long maxmsg;
    long msgsize;
    char messages[MQ_MAX_MESSAGES][MQ_MAX_MSGSIZE];
    size_t msg_sizes[MQ_MAX_MESSAGES];
    unsigned int msg_prios[MQ_MAX_MESSAGES];
    int msg_count;
    int head;
    int tail;
} mq_internal_t;

static mq_internal_t g_mqueues[MQ_MAX_QUEUES];
static int g_mq_initialized = 0;

static void mq_init_internal(void) {
    if (!g_mq_initialized) {
        memset(g_mqueues, 0, sizeof(g_mqueues));
        g_mq_initialized = 1;
    }
}

/* mq_open - open a message queue */
mqd_t mq_open_wrapper(const char* name, int oflag, ...) {
    mq_init_internal();
    
    mode_t mode = 0;
    struct mq_attr* attr = NULL;
    
    if (oflag & O_CREAT) {
        va_list ap;
        va_start(ap, oflag);
        mode = va_arg(ap, mode_t);
        attr = va_arg(ap, struct mq_attr*);
        va_end(ap);
    }
    
    LOG_DEBUG("mq_open_wrapper: name='%s', oflag=0x%x, mode=0%o", name, oflag, mode);
    
    /* Find existing or free slot */
    int found = -1;
    int free_slot = -1;
    for (int i = 0; i < MQ_MAX_QUEUES; i++) {
        if (g_mqueues[i].in_use && strcmp(g_mqueues[i].name, name) == 0) {
            found = i;
            break;
        }
        if (!g_mqueues[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((oflag & O_CREAT) && (oflag & O_EXCL)) {
            errno = EEXIST;
            return (mqd_t)-1;
        }
        return (mqd_t)found;
    }
    
    if (!(oflag & O_CREAT)) {
        errno = ENOENT;
        return (mqd_t)-1;
    }
    
    if (free_slot < 0) {
        errno = EMFILE;
        return (mqd_t)-1;
    }
    
    /* Create new queue */
    strncpy(g_mqueues[free_slot].name, name, sizeof(g_mqueues[free_slot].name) - 1);
    g_mqueues[free_slot].in_use = 1;
    g_mqueues[free_slot].flags = 0;
    g_mqueues[free_slot].maxmsg = attr ? attr->mq_maxmsg : MQ_MAX_MESSAGES;
    g_mqueues[free_slot].msgsize = attr ? attr->mq_msgsize : MQ_MAX_MSGSIZE;
    g_mqueues[free_slot].msg_count = 0;
    g_mqueues[free_slot].head = 0;
    g_mqueues[free_slot].tail = 0;
    
    return (mqd_t)free_slot;
}

/* mq_close - close a message queue */
int mq_close_wrapper(mqd_t mqdes) {
    LOG_DEBUG("mq_close_wrapper: mqdes=%d", (int)mqdes);
    /* Just validate - doesn't actually close in our simple implementation */
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    return 0;
}

/* mq_unlink - remove a message queue */
int mq_unlink_wrapper(const char* name) {
    LOG_DEBUG("mq_unlink_wrapper: name='%s'", name);
    mq_init_internal();
    
    for (int i = 0; i < MQ_MAX_QUEUES; i++) {
        if (g_mqueues[i].in_use && strcmp(g_mqueues[i].name, name) == 0) {
            g_mqueues[i].in_use = 0;
            return 0;
        }
    }
    errno = ENOENT;
    return -1;
}

/* mq_send - send a message to a message queue */
int mq_send_wrapper(mqd_t mqdes, const char* msg_ptr, size_t msg_len, unsigned int msg_prio) {
    LOG_DEBUG("mq_send_wrapper: mqdes=%d, msg_len=%zu, prio=%u", (int)mqdes, msg_len, msg_prio);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mq_internal_t* mq = &g_mqueues[mqdes];
    
    if ((size_t)msg_len > (size_t)mq->msgsize) {
        errno = EMSGSIZE;
        return -1;
    }
    
    if (mq->msg_count >= mq->maxmsg) {
        errno = EAGAIN;
        return -1;
    }
    
    int slot = mq->tail;
    memcpy(mq->messages[slot], msg_ptr, msg_len);
    mq->msg_sizes[slot] = msg_len;
    mq->msg_prios[slot] = msg_prio;
    mq->tail = (mq->tail + 1) % MQ_MAX_MESSAGES;
    mq->msg_count++;
    
    return 0;
}

/* mq_receive - receive a message from a message queue 
 * NOTE: POSIX requires msg_len >= mq_msgsize, but we relax this
 * to only check if the actual message fits in the buffer.
 * This is more practical for embedded/Android use cases.
 */
ssize_t mq_receive_wrapper(mqd_t mqdes, char* msg_ptr, size_t msg_len, unsigned int* msg_prio) {
    LOG_DEBUG("mq_receive_wrapper: mqdes=%d, msg_len=%zu", (int)mqdes, msg_len);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mq_internal_t* mq = &g_mqueues[mqdes];
    
    if (mq->msg_count == 0) {
        errno = EAGAIN;
        return -1;
    }
    
    int slot = mq->head;
    size_t size = mq->msg_sizes[slot];
    
    /* Check if the ACTUAL message (not mq_msgsize) fits in the buffer */
    if (msg_len < size) {
        LOG_DEBUG("mq_receive_wrapper: buffer too small (%zu < %zu)", msg_len, size);
        errno = EMSGSIZE;
        return -1;
    }
    
    memcpy(msg_ptr, mq->messages[slot], size);
    if (msg_prio) *msg_prio = mq->msg_prios[slot];
    mq->head = (mq->head + 1) % MQ_MAX_MESSAGES;
    mq->msg_count--;
    
    LOG_DEBUG("mq_receive_wrapper: received %zu bytes", size);
    return (ssize_t)size;
}

/* mq_getattr - get message queue attributes */
int mq_getattr_wrapper(mqd_t mqdes, struct mq_attr* attr) {
    LOG_DEBUG("mq_getattr_wrapper: mqdes=%d", (int)mqdes);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mq_internal_t* mq = &g_mqueues[mqdes];
    attr->mq_flags = mq->flags;
    attr->mq_maxmsg = mq->maxmsg;
    attr->mq_msgsize = mq->msgsize;
    attr->mq_curmsgs = mq->msg_count;
    
    return 0;
}

/* mq_setattr - set message queue attributes */
int mq_setattr_wrapper(mqd_t mqdes, const struct mq_attr* newattr, struct mq_attr* oldattr) {
    LOG_DEBUG("mq_setattr_wrapper: mqdes=%d", (int)mqdes);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mq_internal_t* mq = &g_mqueues[mqdes];
    
    if (oldattr) {
        oldattr->mq_flags = mq->flags;
        oldattr->mq_maxmsg = mq->maxmsg;
        oldattr->mq_msgsize = mq->msgsize;
        oldattr->mq_curmsgs = mq->msg_count;
    }
    
    if (newattr) {
        mq->flags = newattr->mq_flags;
    }
    
    return 0;
}

/* ============================================================================
 * POSIX AIO (Asynchronous I/O) Functions
 * Android bionic doesn't support aio, so we provide synchronous stub implementations
 * ============================================================================ */

/* aio_read - asynchronous read (synchronous stub) */
int aio_read_wrapper(struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    LOG_DEBUG("aio_read_wrapper: fd=%d, offset=%ld, nbytes=%zu",
              aiocbp->aio_fildes, (long)aiocbp->aio_offset, aiocbp->aio_nbytes);
    
    /* Perform synchronous read */
    ssize_t result = pread(aiocbp->aio_fildes, (void*)aiocbp->aio_buf,
                           aiocbp->aio_nbytes, aiocbp->aio_offset);
    
    if (result < 0) {
        aiocbp->__error_code = errno;
        aiocbp->__return_value = -1;
    } else {
        aiocbp->__error_code = 0;
        aiocbp->__return_value = result;
    }
    
    return 0;  /* Request submitted successfully */
}

/* aio_write - asynchronous write (synchronous stub) */
int aio_write_wrapper(struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    LOG_DEBUG("aio_write_wrapper: fd=%d, offset=%ld, nbytes=%zu",
              aiocbp->aio_fildes, (long)aiocbp->aio_offset, aiocbp->aio_nbytes);
    
    /* Perform synchronous write */
    ssize_t result = pwrite(aiocbp->aio_fildes, (const void*)aiocbp->aio_buf,
                            aiocbp->aio_nbytes, aiocbp->aio_offset);
    
    if (result < 0) {
        aiocbp->__error_code = errno;
        aiocbp->__return_value = -1;
    } else {
        aiocbp->__error_code = 0;
        aiocbp->__return_value = result;
    }
    
    return 0;  /* Request submitted successfully */
}

/* aio_error - get error status of async operation */
int aio_error_wrapper(const struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    LOG_DEBUG("aio_error_wrapper: error_code=%d", aiocbp->__error_code);
    
    /* In our synchronous implementation, operation is always complete */
    return aiocbp->__error_code;
}

/* aio_return - get return status of async operation */
ssize_t aio_return_wrapper(struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    LOG_DEBUG("aio_return_wrapper: return_value=%zd", aiocbp->__return_value);
    
    return aiocbp->__return_value;
}

/* aio_suspend - wait for async operations to complete */
int aio_suspend_wrapper(const struct aiocb* const list[], int nent, const struct timespec* timeout) {
    LOG_DEBUG("aio_suspend_wrapper: nent=%d", nent);
    
    (void)list;
    (void)timeout;
    
    /* In our synchronous implementation, all operations are already complete */
    return 0;
}

/* AIO constants */
#ifndef AIO_ALLDONE
#define AIO_ALLDONE     2
#define AIO_CANCELED    0
#define AIO_NOTCANCELED 1
#endif

/* aio_cancel - cancel async operation */
int aio_cancel_wrapper(int fd, struct aiocb* aiocbp) {
    LOG_DEBUG("aio_cancel_wrapper: fd=%d", fd);
    
    (void)fd;
    (void)aiocbp;
    
    /* Operations are already complete in our synchronous stub */
    return AIO_ALLDONE;
}

/* aio_fsync - sync file data */
int aio_fsync_wrapper(int op, struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    LOG_DEBUG("aio_fsync_wrapper: op=%d, fd=%d", op, aiocbp->aio_fildes);
    
    /* Perform synchronous fsync */
    int result;
    if (op == O_DSYNC) {
        result = fdatasync(aiocbp->aio_fildes);
    } else {
        result = fsync(aiocbp->aio_fildes);
    }
    
    if (result < 0) {
        aiocbp->__error_code = errno;
        aiocbp->__return_value = -1;
    } else {
        aiocbp->__error_code = 0;
        aiocbp->__return_value = 0;
    }
    
    return 0;
}

/* lio_listio - list directed I/O */
#ifndef LIO_READ
#define LIO_READ  0
#define LIO_WRITE 1
#define LIO_NOP   2
#endif

#ifndef LIO_WAIT
#define LIO_WAIT   0
#define LIO_NOWAIT 1
#endif

int lio_listio_wrapper(int mode, struct aiocb* const list[], int nent, struct sigevent* sig) {
    LOG_DEBUG("lio_listio_wrapper: mode=%d, nent=%d", mode, nent);
    
    (void)sig;
    
    for (int i = 0; i < nent; i++) {
        if (!list[i]) continue;
        
        switch (list[i]->aio_lio_opcode) {
            case LIO_READ:
                aio_read_wrapper(list[i]);
                break;
            case LIO_WRITE:
                aio_write_wrapper(list[i]);
                break;
            case LIO_NOP:
            default:
                break;
        }
    }
    
    return 0;
}

/* ============================================================================
 * System V IPC - Memory-based implementation
 * Android's seccomp blocks shmget/semget/msgget syscalls, so we provide
 * a userspace implementation using mmap and mutexes
 * ============================================================================ */
#include <pthread.h>
#include <sys/mman.h>

/* IPC key conversion */
#ifndef IPC_PRIVATE
#define IPC_PRIVATE 0
#endif
#ifndef IPC_CREAT
#define IPC_CREAT   01000
#define IPC_EXCL    02000
#define IPC_NOWAIT  04000
#define IPC_RMID    0
#define IPC_SET     1
#define IPC_STAT    2
#endif

/* ========== Shared Memory ========== */
#define SHM_MAX_SEGMENTS 64

typedef struct {
    key_t key;
    int in_use;
    size_t size;
    void* addr;
    int nattach;
} shm_segment_t;

static shm_segment_t g_shm_segments[SHM_MAX_SEGMENTS];
static pthread_mutex_t g_shm_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_shm_initialized = 0;

static void shm_init(void) {
    if (!g_shm_initialized) {
        memset(g_shm_segments, 0, sizeof(g_shm_segments));
        g_shm_initialized = 1;
    }
}

int shmget_wrapper(key_t key, size_t size, int shmflg) {
    pthread_mutex_lock(&g_shm_mutex);
    shm_init();
    
    LOG_DEBUG("shmget_wrapper: key=0x%x, size=%zu, flags=0x%x", key, size, shmflg);
    
    /* Find existing or free slot */
    int found = -1;
    int free_slot = -1;
    
    for (int i = 0; i < SHM_MAX_SEGMENTS; i++) {
        if (g_shm_segments[i].in_use && g_shm_segments[i].key == key && key != IPC_PRIVATE) {
            found = i;
            break;
        }
        if (!g_shm_segments[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((shmflg & IPC_CREAT) && (shmflg & IPC_EXCL)) {
            pthread_mutex_unlock(&g_shm_mutex);
            errno = EEXIST;
            return -1;
        }
        pthread_mutex_unlock(&g_shm_mutex);
        return found;
    }
    
    if (!(shmflg & IPC_CREAT)) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = ENOENT;
        return -1;
    }
    
    if (free_slot < 0) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = ENOSPC;
        return -1;
    }
    
    /* Allocate shared memory using mmap */
    void* addr = mmap(NULL, size, PROT_READ | PROT_WRITE,
                      MAP_SHARED | MAP_ANONYMOUS, -1, 0);
    if (addr == MAP_FAILED) {
        pthread_mutex_unlock(&g_shm_mutex);
        return -1;
    }
    
    g_shm_segments[free_slot].key = key;
    g_shm_segments[free_slot].in_use = 1;
    g_shm_segments[free_slot].size = size;
    g_shm_segments[free_slot].addr = addr;
    g_shm_segments[free_slot].nattach = 0;
    
    pthread_mutex_unlock(&g_shm_mutex);
    return free_slot;
}

void* shmat_wrapper(int shmid, const void* shmaddr, int shmflg) {
    (void)shmaddr;
    (void)shmflg;
    
    pthread_mutex_lock(&g_shm_mutex);
    
    LOG_DEBUG("shmat_wrapper: shmid=%d", shmid);
    
    if (shmid < 0 || shmid >= SHM_MAX_SEGMENTS || !g_shm_segments[shmid].in_use) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = EINVAL;
        return (void*)-1;
    }
    
    g_shm_segments[shmid].nattach++;
    void* addr = g_shm_segments[shmid].addr;
    
    pthread_mutex_unlock(&g_shm_mutex);
    return addr;
}

int shmdt_wrapper(const void* shmaddr) {
    pthread_mutex_lock(&g_shm_mutex);
    
    LOG_DEBUG("shmdt_wrapper: addr=%p", shmaddr);
    
    for (int i = 0; i < SHM_MAX_SEGMENTS; i++) {
        if (g_shm_segments[i].in_use && g_shm_segments[i].addr == shmaddr) {
            g_shm_segments[i].nattach--;
            pthread_mutex_unlock(&g_shm_mutex);
            return 0;
        }
    }
    
    pthread_mutex_unlock(&g_shm_mutex);
    errno = EINVAL;
    return -1;
}

int shmctl_wrapper(int shmid, int cmd, void* buf) {
    (void)buf;
    
    pthread_mutex_lock(&g_shm_mutex);
    
    LOG_DEBUG("shmctl_wrapper: shmid=%d, cmd=%d", shmid, cmd);
    
    if (shmid < 0 || shmid >= SHM_MAX_SEGMENTS || !g_shm_segments[shmid].in_use) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = EINVAL;
        return -1;
    }
    
    if (cmd == IPC_RMID) {
        munmap(g_shm_segments[shmid].addr, g_shm_segments[shmid].size);
        g_shm_segments[shmid].in_use = 0;
        g_shm_segments[shmid].addr = NULL;
    }
    
    pthread_mutex_unlock(&g_shm_mutex);
    return 0;
}

/* ========== Semaphores ========== */
#define SEM_MAX_SETS 64
#define SEM_MAX_PER_SET 64

typedef struct {
    key_t key;
    int in_use;
    int nsems;
    int values[SEM_MAX_PER_SET];
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} sem_set_t;

static sem_set_t g_sem_sets[SEM_MAX_SETS];
static pthread_mutex_t g_sem_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_sem_initialized = 0;

static void sem_init_internal(void) {
    if (!g_sem_initialized) {
        memset(g_sem_sets, 0, sizeof(g_sem_sets));
        for (int i = 0; i < SEM_MAX_SETS; i++) {
            pthread_mutex_init(&g_sem_sets[i].mutex, NULL);
            pthread_cond_init(&g_sem_sets[i].cond, NULL);
        }
        g_sem_initialized = 1;
    }
}

int semget_wrapper(key_t key, int nsems, int semflg) {
    pthread_mutex_lock(&g_sem_mutex);
    sem_init_internal();
    
    LOG_DEBUG("semget_wrapper: key=0x%x, nsems=%d, flags=0x%x", key, nsems, semflg);
    
    if (nsems < 0 || nsems > SEM_MAX_PER_SET) {
        pthread_mutex_unlock(&g_sem_mutex);
        errno = EINVAL;
        return -1;
    }
    
    int found = -1;
    int free_slot = -1;
    
    for (int i = 0; i < SEM_MAX_SETS; i++) {
        if (g_sem_sets[i].in_use && g_sem_sets[i].key == key && key != IPC_PRIVATE) {
            found = i;
            break;
        }
        if (!g_sem_sets[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((semflg & IPC_CREAT) && (semflg & IPC_EXCL)) {
            pthread_mutex_unlock(&g_sem_mutex);
            errno = EEXIST;
            return -1;
        }
        pthread_mutex_unlock(&g_sem_mutex);
        return found;
    }
    
    if (!(semflg & IPC_CREAT)) {
        pthread_mutex_unlock(&g_sem_mutex);
        errno = ENOENT;
        return -1;
    }
    
    if (free_slot < 0) {
        pthread_mutex_unlock(&g_sem_mutex);
        errno = ENOSPC;
        return -1;
    }
    
    g_sem_sets[free_slot].key = key;
    g_sem_sets[free_slot].in_use = 1;
    g_sem_sets[free_slot].nsems = nsems;
    memset(g_sem_sets[free_slot].values, 0, sizeof(g_sem_sets[free_slot].values));
    
    pthread_mutex_unlock(&g_sem_mutex);
    return free_slot;
}

/* sembuf structure */
struct sembuf_compat {
    unsigned short sem_num;
    short sem_op;
    short sem_flg;
};

int semop_wrapper(int semid, void* sops, size_t nsops) {
    struct sembuf_compat* ops = (struct sembuf_compat*)sops;
    
    LOG_DEBUG("semop_wrapper: semid=%d, nsops=%zu", semid, nsops);
    
    if (semid < 0 || semid >= SEM_MAX_SETS || !g_sem_sets[semid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    sem_set_t* set = &g_sem_sets[semid];
    pthread_mutex_lock(&set->mutex);
    
    for (size_t i = 0; i < nsops; i++) {
        if (ops[i].sem_num >= (unsigned)set->nsems) {
            pthread_mutex_unlock(&set->mutex);
            errno = EFBIG;
            return -1;
        }
        
        if (ops[i].sem_op > 0) {
            /* Increment semaphore */
            set->values[ops[i].sem_num] += ops[i].sem_op;
            pthread_cond_broadcast(&set->cond);
        } else if (ops[i].sem_op < 0) {
            /* Decrement semaphore (wait if necessary) */
            while (set->values[ops[i].sem_num] + ops[i].sem_op < 0) {
                if (ops[i].sem_flg & IPC_NOWAIT) {
                    pthread_mutex_unlock(&set->mutex);
                    errno = EAGAIN;
                    return -1;
                }
                pthread_cond_wait(&set->cond, &set->mutex);
            }
            set->values[ops[i].sem_num] += ops[i].sem_op;
        }
        /* sem_op == 0: wait for zero (simplified - just continue) */
    }
    
    pthread_mutex_unlock(&set->mutex);
    return 0;
}

/* semctl commands */
#ifndef GETVAL
#define GETVAL  12
#define SETVAL  16
#define GETALL  13
#define SETALL  17
#endif

int semctl_wrapper(int semid, int semnum, int cmd, ...) {
    LOG_DEBUG("semctl_wrapper: semid=%d, semnum=%d, cmd=%d", semid, semnum, cmd);
    
    if (semid < 0 || semid >= SEM_MAX_SETS || !g_sem_sets[semid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    sem_set_t* set = &g_sem_sets[semid];
    
    if (cmd == IPC_RMID) {
        pthread_mutex_lock(&g_sem_mutex);
        set->in_use = 0;
        pthread_mutex_unlock(&g_sem_mutex);
        return 0;
    }
    
    if (cmd == SETVAL) {
        va_list ap;
        va_start(ap, cmd);
        int val = va_arg(ap, int);
        va_end(ap);
        
        if (semnum >= set->nsems) {
            errno = EINVAL;
            return -1;
        }
        
        pthread_mutex_lock(&set->mutex);
        set->values[semnum] = val;
        pthread_cond_broadcast(&set->cond);
        pthread_mutex_unlock(&set->mutex);
        return 0;
    }
    
    if (cmd == GETVAL) {
        if (semnum >= set->nsems) {
            errno = EINVAL;
            return -1;
        }
        return set->values[semnum];
    }
    
    return 0;
}

/* ========== System V Message Queues ========== */
#define MSGQ_MAX_QUEUES 64
#define MSGQ_MAX_MESSAGES 128
#define MSGQ_MAX_SIZE 8192

typedef struct {
    long mtype;
    char mtext[MSGQ_MAX_SIZE];
    size_t msize;
} msg_entry_t;

typedef struct {
    key_t key;
    int in_use;
    msg_entry_t messages[MSGQ_MAX_MESSAGES];
    int msg_count;
    int head;
    int tail;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} msgq_t;

static msgq_t g_msgqs[MSGQ_MAX_QUEUES];
static pthread_mutex_t g_msgq_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_msgq_initialized = 0;

static void msgq_init(void) {
    if (!g_msgq_initialized) {
        memset(g_msgqs, 0, sizeof(g_msgqs));
        for (int i = 0; i < MSGQ_MAX_QUEUES; i++) {
            pthread_mutex_init(&g_msgqs[i].mutex, NULL);
            pthread_cond_init(&g_msgqs[i].cond, NULL);
        }
        g_msgq_initialized = 1;
    }
}

int msgget_wrapper(key_t key, int msgflg) {
    pthread_mutex_lock(&g_msgq_mutex);
    msgq_init();
    
    LOG_DEBUG("msgget_wrapper: key=0x%x, flags=0x%x", key, msgflg);
    
    int found = -1;
    int free_slot = -1;
    
    for (int i = 0; i < MSGQ_MAX_QUEUES; i++) {
        if (g_msgqs[i].in_use && g_msgqs[i].key == key && key != IPC_PRIVATE) {
            found = i;
            break;
        }
        if (!g_msgqs[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((msgflg & IPC_CREAT) && (msgflg & IPC_EXCL)) {
            pthread_mutex_unlock(&g_msgq_mutex);
            errno = EEXIST;
            return -1;
        }
        pthread_mutex_unlock(&g_msgq_mutex);
        return found;
    }
    
    if (!(msgflg & IPC_CREAT)) {
        pthread_mutex_unlock(&g_msgq_mutex);
        errno = ENOENT;
        return -1;
    }
    
    if (free_slot < 0) {
        pthread_mutex_unlock(&g_msgq_mutex);
        errno = ENOSPC;
        return -1;
    }
    
    g_msgqs[free_slot].key = key;
    g_msgqs[free_slot].in_use = 1;
    g_msgqs[free_slot].msg_count = 0;
    g_msgqs[free_slot].head = 0;
    g_msgqs[free_slot].tail = 0;
    
    pthread_mutex_unlock(&g_msgq_mutex);
    return free_slot;
}

int msgsnd_wrapper(int msqid, const void* msgp, size_t msgsz, int msgflg) {
    LOG_DEBUG("msgsnd_wrapper: msqid=%d, msgsz=%zu", msqid, msgsz);
    
    if (msqid < 0 || msqid >= MSGQ_MAX_QUEUES || !g_msgqs[msqid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    if (msgsz > MSGQ_MAX_SIZE) {
        errno = EINVAL;
        return -1;
    }
    
    msgq_t* q = &g_msgqs[msqid];
    pthread_mutex_lock(&q->mutex);
    
    while (q->msg_count >= MSGQ_MAX_MESSAGES) {
        if (msgflg & IPC_NOWAIT) {
            pthread_mutex_unlock(&q->mutex);
            errno = EAGAIN;
            return -1;
        }
        pthread_cond_wait(&q->cond, &q->mutex);
    }
    
    const long* mtype = (const long*)msgp;
    const char* mtext = (const char*)msgp + sizeof(long);
    
    int slot = q->tail;
    q->messages[slot].mtype = *mtype;
    memcpy(q->messages[slot].mtext, mtext, msgsz);
    q->messages[slot].msize = msgsz;
    q->tail = (q->tail + 1) % MSGQ_MAX_MESSAGES;
    q->msg_count++;
    
    pthread_cond_broadcast(&q->cond);
    pthread_mutex_unlock(&q->mutex);
    return 0;
}

ssize_t msgrcv_wrapper(int msqid, void* msgp, size_t msgsz, long msgtyp, int msgflg) {
    LOG_DEBUG("msgrcv_wrapper: msqid=%d, msgsz=%zu, msgtyp=%ld", msqid, msgsz, msgtyp);
    
    if (msqid < 0 || msqid >= MSGQ_MAX_QUEUES || !g_msgqs[msqid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    msgq_t* q = &g_msgqs[msqid];
    pthread_mutex_lock(&q->mutex);
    
    while (1) {
        /* Find matching message */
        for (int i = 0; i < q->msg_count; i++) {
            int idx = (q->head + i) % MSGQ_MAX_MESSAGES;
            int match = 0;
            
            if (msgtyp == 0) {
                match = 1;  /* Any message */
            } else if (msgtyp > 0) {
                match = (q->messages[idx].mtype == msgtyp);
            } else {
                match = (q->messages[idx].mtype <= -msgtyp);
            }
            
            if (match) {
                long* mtype_out = (long*)msgp;
                char* mtext_out = (char*)msgp + sizeof(long);
                
                *mtype_out = q->messages[idx].mtype;
                size_t copy_size = (q->messages[idx].msize < msgsz) ? q->messages[idx].msize : msgsz;
                memcpy(mtext_out, q->messages[idx].mtext, copy_size);
                
                /* Remove message (shift remaining) */
                for (int j = i; j < q->msg_count - 1; j++) {
                    int from = (q->head + j + 1) % MSGQ_MAX_MESSAGES;
                    int to = (q->head + j) % MSGQ_MAX_MESSAGES;
                    q->messages[to] = q->messages[from];
                }
                q->msg_count--;
                if (q->msg_count > 0) {
                    q->tail = (q->head + q->msg_count) % MSGQ_MAX_MESSAGES;
                } else {
                    q->head = q->tail = 0;
                }
                
                pthread_cond_broadcast(&q->cond);
                pthread_mutex_unlock(&q->mutex);
                return (ssize_t)copy_size;
            }
        }
        
        if (msgflg & IPC_NOWAIT) {
            pthread_mutex_unlock(&q->mutex);
            errno = ENOMSG;
            return -1;
        }
        
        pthread_cond_wait(&q->cond, &q->mutex);
    }
}

int msgctl_wrapper(int msqid, int cmd, void* buf) {
    (void)buf;
    
    LOG_DEBUG("msgctl_wrapper: msqid=%d, cmd=%d", msqid, cmd);
    
    if (msqid < 0 || msqid >= MSGQ_MAX_QUEUES || !g_msgqs[msqid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    if (cmd == IPC_RMID) {
        pthread_mutex_lock(&g_msgq_mutex);
        g_msgqs[msqid].in_use = 0;
        pthread_mutex_unlock(&g_msgq_mutex);
    }
    
    return 0;
}

/* ============================================================================
 * File Creation Functions
 * ============================================================================ */

/* mkfifo - create a FIFO (named pipe) 
 * Uses proot bypass module for Android compatibility
 */
int mkfifo_wrapper(const char* pathname, mode_t mode) {
    LOG_DEBUG("mkfifo_wrapper: pathname='%s', mode=0%o", pathname, mode);
    return proot_mkfifo(pathname, mode);
}

/* mknod - create a special or ordinary file 
 * Uses proot bypass module for Android compatibility
 */
int mknod_wrapper(const char* pathname, mode_t mode, dev_t dev) {
    LOG_DEBUG("mknod_wrapper: pathname='%s', mode=0%o, dev=%lu", pathname, mode, (unsigned long)dev);
    return proot_mknod(pathname, mode, dev);
}

/* mknodat - create a special or ordinary file relative to a directory */
int mknodat_wrapper(int dirfd, const char* pathname, mode_t mode, dev_t dev) {
    LOG_DEBUG("mknodat_wrapper: dirfd=%d, pathname='%s', mode=0%o, dev=%lu",
              dirfd, pathname, mode, (unsigned long)dev);
    
    int result = mknodat(dirfd, pathname, mode, dev);
    
    if (result < 0) {
        LOG_DEBUG("mknodat_wrapper: failed, errno=%d (%s)", errno, strerror(errno));
    }
    
    return result;
}

/* ============================================================================
 * Signal Handling Functions
 * 
 * For ARM64 glibc programs, struct sigaction and sigset_t layouts match bionic.
 * No conversion needed.
 * ============================================================================ */
#include <signal.h>

int sigprocmask_wrapper(int how, const sigset_t* set, sigset_t* oldset) {
    WRAPPER_BEGIN("sigprocmask");
    int ret = sigprocmask(how, set, oldset);
    WRAPPER_RETURN(ret);
}

/* External flag from glibc_bridge_runner.c - indicates crash handler is installed */
extern int g_glibc_bridge_crash_handler_installed;

int sigaction_wrapper(int signum, const struct sigaction* act, struct sigaction* oldact) {
    WRAPPER_BEGIN("sigaction");
    
    /* Protect glibc-bridge crash handlers from being overwritten */
    if (g_glibc_bridge_crash_handler_installed && act != NULL) {
        if (signum == SIGSEGV || signum == SIGBUS || signum == SIGFPE || 
            signum == SIGILL || signum == SIGABRT) {
            LOG_DEBUG("sigaction_wrapper: BLOCKING attempt to override crash handler for signal %d", signum);
            /* Return success but don't actually change the handler */
            if (oldact) {
                /* Return a dummy old action */
                memset(oldact, 0, sizeof(struct sigaction));
            }
            CLEAR_WRAPPER();
            return 0;
        }
    }
    
    int ret = sigaction(signum, act, oldact);
    WRAPPER_RETURN(ret);
}

int sigemptyset_wrapper(sigset_t* set) {
    WRAPPER_BEGIN("sigemptyset");
    int ret = sigemptyset(set);
    WRAPPER_RETURN(ret);
}

int sigfillset_wrapper(sigset_t* set) {
    WRAPPER_BEGIN("sigfillset");
    int ret = sigfillset(set);
    WRAPPER_RETURN(ret);
}

int sigaddset_wrapper(sigset_t* set, int signum) {
    WRAPPER_BEGIN("sigaddset");
    int ret = sigaddset(set, signum);
    WRAPPER_RETURN(ret);
}

int sigdelset_wrapper(sigset_t* set, int signum) {
    WRAPPER_BEGIN("sigdelset");
    int ret = sigdelset(set, signum);
    WRAPPER_RETURN(ret);
}

int sigismember_wrapper(const sigset_t* set, int signum) {
    WRAPPER_BEGIN("sigismember");
    int ret = sigismember(set, signum);
    WRAPPER_RETURN(ret);
}

int kill_wrapper(pid_t pid, int sig) {
    LOG_DEBUG("kill_wrapper: pid=%d, sig=%d", pid, sig);
    return kill(pid, sig);
}

/* Note: raise_wrapper is defined earlier in this file */

/* ============================================================================
 * confstr - Get configuration string values (not in bionic, emulated)
 * ============================================================================ */

/* glibc confstr name constants */
#define CS_PATH             0
#define CS_GNU_LIBC_VERSION 2
#define CS_GNU_LIBPTHREAD_VERSION 3

/* ============================================================================
 * environ - Global environment variable pointer
 * For data symbols, we return the address of the variable itself.
 * ============================================================================ */
extern char **environ;

/* Returns address of environ variable (for GOT filling) */
void* glibc_bridge_get_environ_addr(void) {
    return &environ;
}

/* ============================================================================
 * PAL_RegisterModule - Stub for .NET CoreCLR PAL (Platform Abstraction Layer)
 * CoreCLR looks for this symbol to register loaded modules. Provide a stub
 * that always succeeds.
 * ============================================================================ */

int PAL_RegisterModule_wrapper(const char* name) {
    LOG_DEBUG("PAL_RegisterModule_wrapper: name=%s", name ? name : "(null)");
    /* Return TRUE (1) to indicate success */
    return 1;
}

/* ============================================================================
 * confstr - Get configuration string values (not in bionic, emulated)
 * ============================================================================ */

size_t confstr_wrapper(int name, char* buf, size_t len) {
    const char* value = NULL;
    
    LOG_DEBUG("confstr_wrapper: name=%d, buf=%p, len=%zu", name, buf, len);
    
    switch (name) {
        case CS_PATH:
            /* Return standard PATH */
            value = "/system/bin:/system/xbin";
            break;
        case CS_GNU_LIBC_VERSION:
            /* Emulate glibc version for compatibility */
            value = "glibc 2.31";
            break;
        case CS_GNU_LIBPTHREAD_VERSION:
            /* Emulate pthread version */
            value = "NPTL 2.31";
            break;
        default:
            /* Unknown confstr name */
            errno = EINVAL;
            return 0;
    }
    
    size_t required = strlen(value) + 1;
    
    if (buf && len > 0) {
        size_t copy_len = (len < required) ? len - 1 : required - 1;
        memcpy(buf, value, copy_len);
        buf[copy_len] = '\0';
    }
    
    return required;
}

/* ============================================================================
 * iconv Wrappers - Use proot bypass for Android compatibility
 * ============================================================================ */

void* iconv_open_wrapper(const char* tocode, const char* fromcode) {
    LOG_DEBUG("iconv_open_wrapper: %s -> %s", fromcode, tocode);
    return proot_iconv_open(tocode, fromcode);
}

size_t iconv_wrapper(void* cd, char** inbuf, size_t* inbytesleft,
                     char** outbuf, size_t* outbytesleft) {
    return proot_iconv(cd, inbuf, inbytesleft, outbuf, outbytesleft);
}

int iconv_close_wrapper(void* cd) {
    return proot_iconv_close(cd);
}

/* ============================================================================
 * Socket Option Wrappers - Use proot bypass for Android compatibility
 * ============================================================================ */

int setsockopt_wrapper(int sockfd, int level, int optname,
                       const void *optval, socklen_t optlen) {
    LOG_DEBUG("setsockopt_wrapper: fd=%d level=%d optname=%d", sockfd, level, optname);
    return proot_setsockopt(sockfd, level, optname, optval, optlen);
}

int getsockopt_wrapper(int sockfd, int level, int optname,
                       void *optval, socklen_t *optlen) {
    LOG_DEBUG("getsockopt_wrapper: fd=%d level=%d optname=%d", sockfd, level, optname);
    return proot_getsockopt(sockfd, level, optname, optval, optlen);
}

/* ============================================================================
 * getopt Wrapper - Use proot bypass for consistent state handling
 * ============================================================================ */

int getopt_wrapper(int argc, char* const argv[], const char* optstring) {
    LOG_DEBUG("getopt_wrapper: argc=%d optstring=%s", argc, optstring);
    return proot_getopt(argc, argv, optstring);
}
