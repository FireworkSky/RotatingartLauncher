/*
 * glibc-bridge 动态链接器 - 日志系统实现
 * 
 * 环境变量控制的动态链接器日志
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "log.h"

/* ============================================================================
 * Static State
 * ============================================================================ */

static int g_log_level = -1;  /* -1 = not initialized */

/* ============================================================================
 * Implementation
 * ============================================================================ */

int glibc_bridge_dl_get_log_level(void) {
    if (g_log_level < 0) {
        const char* env = getenv("GLIBC_BRIDGE_LOG_LEVEL");
        if (env) {
            g_log_level = atoi(env);
            if (g_log_level < 0) g_log_level = 0;
            if (g_log_level > 5) g_log_level = 5;
        } else {
            g_log_level = GLIBC_BRIDGE_DL_LOG_INFO;  /* Default */
        }
    }
    return g_log_level;
}

/* Force set log level (bypasses cache) */
void glibc_bridge_dl_set_log_level(int level) {
    if (level < 0) level = 0;
    if (level > 5) level = 5;
    g_log_level = level;
}

void glibc_bridge_dl_log(int level, const char* msg) {
    if (glibc_bridge_dl_get_log_level() < level) {
        return;
    }
    
    const char* prefix = "";
    switch (level) {
        case GLIBC_BRIDGE_DL_LOG_ERROR: prefix = "[ERROR] "; break;
        case GLIBC_BRIDGE_DL_LOG_WARN:  prefix = "[WARN] ";  break;
        case GLIBC_BRIDGE_DL_LOG_DEBUG: prefix = "[DEBUG] "; break;
        /* INFO and TRACE have no prefix */
    }
    
    if (prefix[0]) {
        write(STDERR_FILENO, prefix, strlen(prefix));
    }
    write(STDERR_FILENO, msg, strlen(msg));
}

void glibc_bridge_dl_log_error(const char* msg) {
    glibc_bridge_dl_log(GLIBC_BRIDGE_DL_LOG_ERROR, msg);
}

void glibc_bridge_dl_log_warn(const char* msg) {
    glibc_bridge_dl_log(GLIBC_BRIDGE_DL_LOG_WARN, msg);
}

void glibc_bridge_dl_log_info(const char* msg) {
    glibc_bridge_dl_log(GLIBC_BRIDGE_DL_LOG_INFO, msg);
}

void glibc_bridge_dl_log_debug(const char* msg) {
    glibc_bridge_dl_log(GLIBC_BRIDGE_DL_LOG_DEBUG, msg);
}

void glibc_bridge_dl_log_trace(const char* msg) {
    glibc_bridge_dl_log(GLIBC_BRIDGE_DL_LOG_TRACE, msg);
}

void glibc_bridge_dl_child_log(const char *msg) {
    if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_INFO) {
        write(STDERR_FILENO, msg, strlen(msg));
    }
}



