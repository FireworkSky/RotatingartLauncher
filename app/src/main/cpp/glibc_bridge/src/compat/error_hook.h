/*
 * glibc-bridge - 自动错误钩子机制
 * 
 * 通过钩住 errno 访问来自动捕获 bionic 调用错误
 * 无需手动为每个包装函数添加错误捕获代码
 */

#ifndef GLIBC_BRIDGE_ERROR_HOOK_H
#define GLIBC_BRIDGE_ERROR_HOOK_H

#include <errno.h>
#include <string.h>

/* ============================================================================
 * Global Error Tracking State
 * ============================================================================ */

/* Current wrapper context - set by WRAPPER_ENTER, cleared by WRAPPER_EXIT */
extern __thread const char* g_current_wrapper_name;
extern __thread int g_wrapper_depth;

/* Error hook configuration */
extern int g_error_hook_enabled;

/* Initialize error hook (call once at startup) */
void glibc_bridge_error_hook_init(void);

/* ============================================================================
 * Wrapper Context Macros - Use at start/end of wrappers
 * ============================================================================ */

/* 
 * WRAPPER_ENTER(name) - Call at the beginning of a wrapper
 * This sets the context so errors can be attributed to this wrapper
 */
#define WRAPPER_ENTER(name) \
    do { \
        g_wrapper_depth++; \
        if (g_wrapper_depth == 1) { \
            g_current_wrapper_name = name; \
        } \
    } while(0)

/*
 * WRAPPER_EXIT() - Call before returning from wrapper
 * This clears the context and logs any error that occurred
 */
#define WRAPPER_EXIT() \
    do { \
        g_wrapper_depth--; \
        if (g_wrapper_depth == 0) { \
            g_current_wrapper_name = NULL; \
        } \
    } while(0)

/* ============================================================================
 * Automatic Error Logging (called from errno sync)
 * ============================================================================ */

/* 
 * Called automatically when errno is synced from bionic to glibc.
 * Logs error if errno is non-zero and we're inside a wrapper context.
 */
void glibc_bridge_log_bionic_error(int bionic_errno);

/* ============================================================================
 * SYNC_ERRNO_AND_CHECK - Sync errno and auto-log errors
 * ============================================================================ */

/* 
 * Use this instead of raw SYNC_ERRNO() in wrappers for automatic error logging.
 * It syncs errno AND logs any error that occurred.
 */
#define SYNC_ERRNO_AND_CHECK() \
    do { \
        int _saved_errno = errno; \
        extern void glibc_bridge_sync_errno_from_bionic(void); \
        glibc_bridge_sync_errno_from_bionic(); \
        if (_saved_errno != 0) { \
            glibc_bridge_log_bionic_error(_saved_errno); \
        } \
    } while(0)

/* ============================================================================
 * Simplified Wrapper Macros - For quick wrapper creation
 * ============================================================================ */

/*
 * WRAP_SIMPLE(ret_type, name, call) - Simple wrapper that auto-handles errno
 * Example: WRAP_SIMPLE(int, open, open(path, flags))
 */
#define WRAP_SIMPLE(ret_type, name, call) \
    do { \
        WRAPPER_ENTER(#name); \
        ret_type _result = (call); \
        SYNC_ERRNO_AND_CHECK(); \
        WRAPPER_EXIT(); \
        return _result; \
    } while(0)

/*
 * WRAP_SIMPLE_VOID(name, call) - For void-returning functions
 */
#define WRAP_SIMPLE_VOID(name, call) \
    do { \
        WRAPPER_ENTER(#name); \
        (call); \
        SYNC_ERRNO_AND_CHECK(); \
        WRAPPER_EXIT(); \
    } while(0)

/* ============================================================================
 * Enhanced Error Info Logging
 * ============================================================================ */

/* Log error with additional context */
void glibc_bridge_log_error_with_info(const char* func, int ret, const char* fmt, ...);

/* Macro version for convenience */
#define LOG_ERROR_INFO(func, ret, ...) \
    glibc_bridge_log_error_with_info(func, ret, __VA_ARGS__)

#endif /* GLIBC_BRIDGE_ERROR_HOOK_H */
