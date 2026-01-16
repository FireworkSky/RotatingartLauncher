/*
 * glibc-bridge - 自动错误钩子实现
 */

#include "error_hook.h"
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

/* ============================================================================
 * Thread-local State for Wrapper Context
 * ============================================================================ */

/* g_current_wrapper_name is defined in wrapper_common.c */
__thread int g_wrapper_depth = 0;

/* Error hook enabled - controlled by GLIBC_BRIDGE_ERROR_HOOK env var */
int g_error_hook_enabled = 1;

/* ============================================================================
 * Initialization
 * ============================================================================ */

void glibc_bridge_error_hook_init(void) {
    const char* env = getenv("GLIBC_BRIDGE_ERROR_HOOK");
    if (env) {
        g_error_hook_enabled = atoi(env);
    } else {
        /* Enable by default if GLIBC_BRIDGE_LOG_LEVEL >= 4 (DEBUG) */
        const char* log_level = getenv("GLIBC_BRIDGE_LOG_LEVEL");
        if (log_level && atoi(log_level) >= 4) {
            g_error_hook_enabled = 1;
        } else {
            /* Default: enabled for error tracking */
            g_error_hook_enabled = 1;
        }
    }
}

/* ============================================================================
 * Automatic Error Logging
 * ============================================================================ */

/* ANSI color codes */
#define COLOR_RED     "\x1b[31m"
#define COLOR_YELLOW  "\x1b[33m"
#define COLOR_RESET   "\x1b[0m"

/* Common expected errors that tests intentionally trigger */
static int is_expected_error(int err, const char* func) {
    /* ENOENT (2) - often tested with non-existent paths */
    if (err == ENOENT) return 1;
    /* EPERM (1) - Android permission restrictions (socket, etc) */
    if (err == EPERM) return 1;
    /* EACCES (13) - Permission denied, common on Android */
    if (err == EACCES) return 1;
    return 0;
}

void glibc_bridge_log_bionic_error(int bionic_errno) {
    if (!g_error_hook_enabled) return;
    if (bionic_errno == 0) return;
    
    const char* wrapper_name = g_current_wrapper_name;
    if (!wrapper_name) {
        return;  /* Not in wrapper context, skip logging */
    }
    
    /* Skip expected/common errors unless in verbose mode */
    const char* log_level = getenv("GLIBC_BRIDGE_LOG_LEVEL");
    int verbose = (log_level && atoi(log_level) >= 5);  /* TRACE level */
    
    if (!verbose && is_expected_error(bionic_errno, wrapper_name)) {
        return;  /* Skip expected errors in non-verbose mode */
    }
    
    /* Format error message */
    char buf[512];
    int len = snprintf(buf, sizeof(buf), 
        COLOR_YELLOW "[BIONIC] " COLOR_RESET
        "%s() errno=%d (%s)\n",
        wrapper_name, bionic_errno, strerror(bionic_errno));
    
    if (len > 0) {
        write(STDERR_FILENO, buf, len);
    }
}

void glibc_bridge_log_error_with_info(const char* func, int ret, const char* fmt, ...) {
    if (!g_error_hook_enabled) return;
    
    char info_buf[256];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(info_buf, sizeof(info_buf), fmt, ap);
    va_end(ap);
    
    char buf[512];
    int len = snprintf(buf, sizeof(buf),
        COLOR_RED "[BIONIC_ERR] " COLOR_RESET
        "%s() -> %d, errno=%d (%s) | %s\n",
        func, ret, errno, strerror(errno), info_buf);
    
    if (len > 0) {
        write(STDERR_FILENO, buf, len);
    }
}
