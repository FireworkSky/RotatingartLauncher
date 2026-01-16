/*
 * glibc-bridge - 动态链接函数包装
 * 
 * 包含 dlopen, dlclose, dlsym, dladdr 等动态链接相关函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <link.h>
#include <errno.h>
#include <unistd.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* 外部声明 - 定义在 elf_loader.c */
extern void* glibc_bridge_dlopen_glibc_lib(const char* path);

/* ============================================================================
 * dlopen 包装
 * 
 * 处理 glibc 和 bionic 之间的 RTLD_* 标志差异
 * 以及 native 库重定向（SDL2, GL4ES 等）
 * ============================================================================ */

/* glibc RTLD_* 标志值 */
#define GLIBC_RTLD_LAZY     0x00001
#define GLIBC_RTLD_NOW      0x00002
#define GLIBC_RTLD_GLOBAL   0x00100
#define GLIBC_RTLD_LOCAL    0x00000
#define GLIBC_RTLD_NODELETE 0x01000
#define GLIBC_RTLD_NOLOAD   0x00004
#define GLIBC_RTLD_DEEPBIND 0x00008

/* Native library redirect table */
static const struct { 
    const char* prefix; 
    const char* native_lib; 
} g_native_lib_map[] = {
    {"libSDL2-2.0.so",  "libSDL2.so"},
    {"libSDL2.so",      "libSDL2.so"},
    {"libGL.so.1",      "libGL_gl4es.so"},
    {"libGL.so",        "libGL_gl4es.so"},
    {"libGLU.so.1",     "libGL_gl4es.so"},
    {"libGLU.so",       "libGL_gl4es.so"},
    {"libEGL.so.1",     "libEGL_gl4es.so"},
    {"libEGL.so",       "libEGL_gl4es.so"},
    {"libopenal.so",    "libopenal32.so"},
    {"libopenal.so.1",  "libopenal32.so"},
    {NULL, NULL}
};

/* ICU library redirects */
static const struct {
    const char* prefix;
    const char* android;
} g_icu_map[] = {
    {"libicuuc.so",     "/apex/com.android.i18n/lib64/libicuuc.so"},
    {"libicui18n.so",   "/apex/com.android.i18n/lib64/libicui18n.so"},
    {"libicudata.so",   "/apex/com.android.i18n/lib64/libicuuc.so"},
    {NULL, NULL}
};

void* dlopen_wrapper(const char* filename, int flags) {
    LOG_DEBUG("dlopen_wrapper: filename='%s', flags=0x%x", 
              filename ? filename : "(null)", flags);
    
    /* 转换 glibc 标志到 bionic 标志 */
    int bionic_flags = 0;
    
    if (flags & GLIBC_RTLD_LAZY) {
        bionic_flags |= RTLD_LAZY;
    }
    if (flags & GLIBC_RTLD_NOW) {
        bionic_flags |= RTLD_NOW;
    }
    if (flags & GLIBC_RTLD_GLOBAL) {
        bionic_flags |= RTLD_GLOBAL;
    }
    if (flags & GLIBC_RTLD_LOCAL) {
        bionic_flags |= RTLD_LOCAL;
    }
    if (flags & GLIBC_RTLD_NODELETE) {
        bionic_flags |= RTLD_NODELETE;
    }
    if (flags & GLIBC_RTLD_NOLOAD) {
        bionic_flags |= RTLD_NOLOAD;
    }
    /* RTLD_DEEPBIND 在 bionic 上不支持，忽略 */
    
    void* handle = dlopen(filename, bionic_flags);
    
    LOG_DEBUG("dlopen_wrapper: 返回 handle=%p", handle);
    return handle;
}

/* ============================================================================
 * dlclose 包装
 * ============================================================================ */

int dlclose_wrapper(void* handle) {
    LOG_DEBUG("dlclose_wrapper: handle=%p", handle);
    return dlclose(handle);
}

/* ============================================================================
 * dlsym 包装
 * ============================================================================ */

void* dlsym_wrapper(void* handle, const char* symbol) {
    LOG_DEBUG("dlsym_wrapper: handle=%p, symbol='%s'", handle, symbol);
    
    void* result = dlsym(handle, symbol);
    
    LOG_DEBUG("dlsym_wrapper: 返回 %p", result);
    return result;
}

/* ============================================================================
 * dlvsym 包装 (带版本)
 * bionic 不支持，回退到 dlsym
 * ============================================================================ */

void* dlvsym_wrapper(void* handle, const char* symbol, const char* version) {
    LOG_DEBUG("dlvsym_wrapper: handle=%p, symbol='%s', version='%s'",
              handle, symbol, version ? version : "(null)");
    
    /* bionic 不支持符号版本，忽略版本参数 */
    return dlsym(handle, symbol);
}

/* ============================================================================
 * dlerror 包装
 * ============================================================================ */

char* dlerror_wrapper(void) {
    return dlerror();
}

/* ============================================================================
 * dladdr 包装
 * ============================================================================ */

int dladdr_wrapper(const void* addr, Dl_info* info) {
    LOG_DEBUG("dladdr_wrapper: addr=%p", addr);
    
    int result = dladdr(addr, info);
    
    if (result) {
        LOG_DEBUG("dladdr_wrapper: dli_fname='%s', dli_sname='%s'",
                  info->dli_fname ? info->dli_fname : "(null)",
                  info->dli_sname ? info->dli_sname : "(null)");
    }
    
    return result;
}

/* ============================================================================
 * dladdr1 包装 (glibc 扩展)
 * bionic 不支持，回退到 dladdr
 * ============================================================================ */

int dladdr1_wrapper(const void* addr, Dl_info* info, void** extra_info, int flags) {
    (void)extra_info;
    (void)flags;
    
    LOG_DEBUG("dladdr1_wrapper: addr=%p, flags=%d", addr, flags);
    
    /* bionic 不支持 dladdr1，使用 dladdr */
    return dladdr(addr, info);
}

/* dl_iterate_phdr_wrapper 定义在 elf/elf_loader.c 中（有完整的 ELF 枚举实现）*/

/* ============================================================================
 * _dl_find_object 包装 (glibc 2.35+)
 * bionic 不支持，返回未找到
 * ============================================================================ */

int dl_find_object_wrapper(void* addr, void* result) {
    (void)addr;
    (void)result;
    
    LOG_DEBUG("dl_find_object_wrapper: addr=%p (不支持)", addr);
    
    /* bionic 不支持此函数，返回 -1 表示未找到 */
    return -1;
}

/* ============================================================================
 * dlinfo 包装 (glibc 扩展)
 * bionic 支持有限，提供部分实现
 * ============================================================================ */

#ifndef RTLD_DI_LMID
#define RTLD_DI_LMID        1
#define RTLD_DI_LINKMAP     2
#define RTLD_DI_CONFIGADDR  3
#define RTLD_DI_SERINFO     4
#define RTLD_DI_SERINFOSIZE 5
#define RTLD_DI_ORIGIN      6
#define RTLD_DI_PROFILENAME 7
#define RTLD_DI_PROFILEOUT  8
#define RTLD_DI_TLS_MODID   9
#define RTLD_DI_TLS_DATA    10
#endif

/* bionic dlinfo 声明 (API 26+) */
extern int dlinfo(void* handle, int request, void* info) __attribute__((weak));

int dlinfo_wrapper(void* handle, int request, void* info) {
    LOG_DEBUG("dlinfo_wrapper: handle=%p, request=%d", handle, request);
    
    /* 检查 dlinfo 是否可用 */
    if (!dlinfo) {
        LOG_DEBUG("dlinfo_wrapper: dlinfo 不可用");
        errno = ENOSYS;
        return -1;
    }
    
    /* bionic 支持部分 dlinfo 请求 */
    switch (request) {
        case RTLD_DI_LINKMAP:
        case RTLD_DI_ORIGIN:
            return dlinfo(handle, request, info);
            
        default:
            /* 不支持的请求 */
            LOG_DEBUG("dlinfo_wrapper: 请求 %d 不支持", request);
            return -1;
    }
}

/* ============================================================================
 * dlmopen 包装 (glibc 扩展)
 * bionic 不支持命名空间，回退到 dlopen
 * ============================================================================ */

#ifndef LM_ID_BASE
#define LM_ID_BASE  0
#define LM_ID_NEWLM -1
#endif

void* dlmopen_wrapper(long lmid, const char* filename, int flags) {
    LOG_DEBUG("dlmopen_wrapper: lmid=%ld, filename='%s' (回退到 dlopen)", 
              lmid, filename ? filename : "(null)");
    
    (void)lmid;
    
    /* bionic 不支持链接器命名空间（在 dlmopen 意义上），回退到 dlopen */
    return dlopen_wrapper(filename, flags);
}

/* ============================================================================
 * Box64 专用接口
 * 
 * 这些函数被 Box64 用来加载和解析 glibc 库
 * 必须使用 glibc_bridge 的符号解析，而不是直接调用 bionic
 * ============================================================================ */

/* 外部声明 - 定义在 elf_loader.c */
extern int glibc_bridge_load_shared_lib(const char* name, const char* search_path);
extern void* glibc_bridge_dlsym_from_handle(void* handle, const char* name);
extern void* glibc_bridge_resolve_symbol(const char* name);

void* glibc_bridge_dlopen_for_box64(const char* filename, int flags) {
    (void)flags;
    /* 使用 glibc_bridge_dlopen_glibc_lib，它会处理 native library redirects 和 glibc 库加载 */
    return glibc_bridge_dlopen_glibc_lib(filename);
}

void* glibc_bridge_dlsym_for_box64(void* handle, const char* symbol) {

    
    /* 使用 glibc_bridge 的符号解析，正确处理 wrapper 函数 */
    void* result = glibc_bridge_dlsym_from_handle(handle, symbol);
    
    /* 如果 handle 解析失败，尝试直接从 wrapper 表查找 */
    if (!result && symbol) {
        result = glibc_bridge_resolve_symbol(symbol);
    }
    
    LOG_DEBUG("glibc_bridge_dlsym_for_box64: result=%p", result);
    return result;
}
