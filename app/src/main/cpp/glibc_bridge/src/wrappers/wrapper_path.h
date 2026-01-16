/*
 * glibc-bridge - Path Translation Utilities
 * 
 * Path translation between glibc rootfs and Android filesystem.
 * Also provides common macros for wrapper implementations.
 */

#ifndef WRAPPER_PATH_H
#define WRAPPER_PATH_H

#include "../include/wrappers.h"
#include "../compat/tls.h"  /* SYNC_ERRNO() */

/* ============================================================================
 * Wrapper Name Tracking (for error logging)
 * ============================================================================ */

/* Thread-local wrapper name for error tracking */
extern __thread const char* g_current_wrapper_name;

/* Set current wrapper name */
#define SET_WRAPPER(name) g_current_wrapper_name = name

/* Clear current wrapper name */
#define CLEAR_WRAPPER() g_current_wrapper_name = NULL

/* ============================================================================
 * Wrapper Function Macros
 * ============================================================================ */

/* WRAPPER_BEGIN - Start of wrapper function */
#define WRAPPER_BEGIN(name) \
    SET_WRAPPER(name)

/* WRAPPER_END - End of wrapper function */
#define WRAPPER_END() \
    CLEAR_WRAPPER()

/* WRAPPER_RETURN - Return with error sync and cleanup */
#define WRAPPER_RETURN(ret) do { \
    SYNC_ERRNO_IF_FAIL((long)(ret)); \
    CLEAR_WRAPPER(); \
    return (ret); \
} while(0)

/* WRAPPER_CALL - Call function with error sync */
#define WRAPPER_CALL(name, call) do { \
    SET_WRAPPER(name); \
    typeof(call) _ret = (call); \
    SYNC_ERRNO_IF_FAIL((long)_ret); \
    CLEAR_WRAPPER(); \
    return _ret; \
} while(0)

/* WRAPPER_CALL_VOID - Call void function with error sync */
#define WRAPPER_CALL_VOID(name, call) do { \
    SET_WRAPPER(name); \
    (call); \
    CLEAR_WRAPPER(); \
} while(0)

/* ============================================================================
 * Path Translation
 * ============================================================================ */

/* External: get glibc rootfs path */
extern const char* glibc_bridge_get_glibc_root(void);

/* Translate path to fake glibc rootfs */
const char* wrapper_translate_path(const char* path);

/* Reverse translate path (from glibc rootfs to original) */
const char* wrapper_reverse_translate_path(const char* path, char* out_buf, size_t buf_size);

/* Check if path should be translated */
int wrapper_should_translate_path(const char* path);

/* ============================================================================
 * Error Reporting Helpers
 * ============================================================================ */

/* Report error and abort (for exception wrappers) */
void wrapper_error_abort(const char* prefix, const char* message);

/* ============================================================================
 * Helper Functions for Common Patterns
 * ============================================================================ */

/* Call function with path translation */
#define WRAPPER_CALL_WITH_PATH(name, func, path_arg, ...) do { \
    SET_WRAPPER(name); \
    const char* _translated = wrapper_translate_path(path_arg); \
    typeof(func(_translated, ##__VA_ARGS__)) _ret = func(_translated, ##__VA_ARGS__); \
    SYNC_ERRNO_IF_FAIL((long)_ret); \
    CLEAR_WRAPPER(); \
    return _ret; \
} while(0)

/* Call function with optional path translation (for dirfd-based functions) */
#define WRAPPER_CALL_WITH_OPT_PATH(name, func, dirfd, path_arg, ...) do { \
    SET_WRAPPER(name); \
    const char* _path = (dirfd == AT_FDCWD && path_arg && path_arg[0] == '/') \
                        ? wrapper_translate_path(path_arg) : path_arg; \
    typeof(func(dirfd, _path, ##__VA_ARGS__)) _ret = func(dirfd, _path, ##__VA_ARGS__); \
    SYNC_ERRNO_IF_FAIL((long)_ret); \
    CLEAR_WRAPPER(); \
    return _ret; \
} while(0)

#endif /* WRAPPER_PATH_H */

