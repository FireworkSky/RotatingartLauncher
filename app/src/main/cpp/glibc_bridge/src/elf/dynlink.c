/*
 * glibc-bridge 动态链接器 - 主入口点
 * 
 * 这是协调以下内容的主动态链接器模块：
 *   - 符号解析（通过 resolver.c）
 *   - 重定位处理（通过 reloc.c）
 *   - 包装函数注册（通过 wrapper_*.c 模块）
 * 
 * 我们不加载真正的 glibc 库，而是拦截符号查找
 * 并将其重定向到 bionic 或我们的包装实现
 * 这类似于 libhybris 的做法
 * 
 * 模块结构：
 * ================
 * 
 * elf/dynlink.c（本文件）
 *   └── 入口点和初始化
 * 
 * elf/
 *   ├── log.c           - 带环境变量控制的日志系统
 *   ├── symbol_table.c  - 符号包装表定义
 *   ├── resolver.c      - 符号解析逻辑
 *   └── reloc.c         - ELF 重定位处理
 * 
 * wrappers/
 *   ├── wrapper_libc.c       - 基本 libc 包装
 *   ├── wrapper_stat.c       - stat/fstat 包装
 *   ├── wrapper_locale.c     - 本地化 _l 后缀函数
 *   ├── wrapper_fortify.c    - FORTIFY _chk 后缀函数
 *   ├── wrapper_gettext.c    - 国际化桩函数
 *   └── wrapper_cxx.c        - C++ runtime wrappers
 * 
 * glibc_bridge_stdio.c              - FILE structure conversion (existing)
 * glibc_bridge_tls.c                - TLS and ctype wrappers (existing)
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "../include/private.h"
#include "../include/wrappers.h"
#include "log.h"

/* ============================================================================
 * Module Initialization
 * 
 * Called automatically when the dynamic linker module is first used.
 * Performs one-time setup of the wrapper system.
 * ============================================================================ */

static int g_dynlink_initialized = 0;

static void __attribute__((constructor)) glibc_bridge_dynlink_init(void) {
    if (g_dynlink_initialized) return;
    g_dynlink_initialized = 1;
    
    /* Log initialization */
    if (glibc_bridge_dl_get_log_level() >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        const char* msg = "[DYNLINK] glibc-bridge Dynamic Linker initialized\n";
        write(STDERR_FILENO, msg, strlen(msg));
    }
}

/* ============================================================================
 * Public API Re-exports
 * 
 * These functions are the main interface used by the glibc-bridge runtime.
 * They delegate to the appropriate submodules.
 * ============================================================================ */

/*
 * The following functions are implemented in their respective modules:
 * 
 * glibc_bridge_resolve_symbol()       - dynlink/glibc_bridge_resolver.c
 * glibc_bridge_relocate_dynamic()     - dynlink/glibc_bridge_reloc.c
 * glibc_bridge_set_symbol_context()   - dynlink/glibc_bridge_resolver.c
 * glibc_bridge_get_symbol_table()     - dynlink/glibc_bridge_symbol_table.c
 * 
 * All wrapper functions are in wrappers/*.c
 * 
 * See include/glibc_bridge_wrappers.h for the complete API.
 */

/* ============================================================================
 * Version Information
 * ============================================================================ */

const char* glibc_bridge_dynlink_version(void) {
    return "glibc-bridge Dynamic Linker v1.0.0 (Modular)";
}

/* ============================================================================
 * Debug/Diagnostic Functions
 * ============================================================================ */

/**
 * Print summary of loaded wrapper counts
 */
void glibc_bridge_dynlink_print_stats(void) {
    if (glibc_bridge_dl_get_log_level() < GLIBC_BRIDGE_DL_LOG_INFO) return;
    
    const symbol_wrapper_t* table = glibc_bridge_get_symbol_table();
    int total = 0;
    int with_wrapper = 0;
    int passthrough = 0;
    
    for (const symbol_wrapper_t* w = table; w->name; w++) {
        total++;
        if (w->wrapper) {
            with_wrapper++;
        } else {
            passthrough++;
        }
    }
    
    char buf[256];
    snprintf(buf, sizeof(buf), 
             "[DYNLINK] Symbol table: %d total, %d wrappers, %d pass-through\n",
             total, with_wrapper, passthrough);
        write(STDERR_FILENO, buf, strlen(buf));
    }
    
/**
 * Dump all registered symbols (for debugging)
 */
void glibc_bridge_dynlink_dump_symbols(void) {
    if (glibc_bridge_dl_get_log_level() < GLIBC_BRIDGE_DL_LOG_DEBUG) return;
    
    const symbol_wrapper_t* table = glibc_bridge_get_symbol_table();
    
    write(STDERR_FILENO, "[DYNLINK] Registered symbols:\n", 30);
    
    for (const symbol_wrapper_t* w = table; w->name; w++) {
        char buf[128];
        snprintf(buf, sizeof(buf), "  %s -> %s\n", 
                 w->name, 
                 w->wrapper ? "wrapper" : "bionic");
        write(STDERR_FILENO, buf, strlen(buf));
    }
}
