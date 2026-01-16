/*
 * glibc-bridge 内部私有头文件
 * 
 * 不在公共 API 中暴露的内部结构和函数
 */

#ifndef GLIBC_BRIDGE_PRIVATE_H
#define GLIBC_BRIDGE_PRIVATE_H

#include "api.h"
#include <elf.h>
#include <sys/types.h>

/* 包含 TLS 兼容层 */
#include "../compat/tls.h"

/* ============================================================================
 * 内部结构
 * ============================================================================ */

/* ELF 头部包装 */
typedef struct elfheader_s {
    char*           path;           /* 文件路径 */
    Elf64_Ehdr      ehdr;           /* ELF 头部 */
    Elf64_Phdr*     phdr;           /* 程序头表 */
    int             phnum;          /* 程序头数量 */
    
    void*           image;          /* 加载后的镜像基址 */
    uintptr_t       delta;          /* 加载地址偏移（ASLR）*/
    size_t          memsz;          /* 总内存大小 */
    uintptr_t       entrypoint;     /* 入口点地址 */
    
    /* TLS 信息 */
    size_t          tlssize;        /* TLS 段大小 */
    size_t          tlsalign;       /* TLS 对齐 */
    void*           tlsdata;        /* TLS 初始数据 */
    
    /* 动态链接 */
    char*           interp;         /* 解释器路径（ld-linux）*/
    
    /* 标志 */
    uint8_t         is_pie;         /* 位置无关可执行文件 */
    uint8_t         is_static;      /* 静态链接 */
} elfheader_t;

/* glibc TLS 结构 */
typedef struct glibc_tls_s {
    void*           tls_block;      /* 分配的 TLS 块 */
    size_t          tls_size;       /* 总大小 */
    void*           tcb;            /* TCB 指针（tpidr_el0）*/
} glibc_tls_t;

/* glibc-bridge 运行时上下文 */
struct glibc_bridge_s {
    glibc_bridge_config_t  config;  /* 配置 */
    
    /* 已加载的 ELF */
    elfheader_t**   elfs;           /* ELF 数组 */
    int             elf_count;      /* 已加载数量 */
    int             elf_capacity;   /* 数组容量 */
    
    /* 执行状态 */
    void*           stack;          /* 分配的栈 */
    size_t          stack_size;     /* 栈大小 */
    glibc_tls_t*    tls;            /* TLS 状态 */
    
    /* 输出捕获 */
    char*           stdout_buf;     /* 捕获的 stdout */
    size_t          stdout_len;
    size_t          stdout_cap;
    char*           stderr_buf;     /* 捕获的 stderr */
    size_t          stderr_len;
    size_t          stderr_cap;
};

/* ELF 句柄 */
struct glibc_bridge_elf_s {
    glibc_bridge_t  bta;            /* 父运行时 */
    elfheader_t*    elf;            /* ELF 头部 */
    int             loaded;         /* 是否已加载到内存 */
};

/* ============================================================================
 * 内部函数 - ELF 加载
 * ============================================================================ */

/* 解析 ELF 头部 */
elfheader_t* elf_parse_header(const char* path);

/* 加载 ELF 到内存 */
int elf_load_memory(elfheader_t* elf);

/* 重定位 ELF */
int elf_relocate(elfheader_t* elf);

/* 使用包装函数重定位动态 ELF */
int glibc_bridge_relocate_dynamic(elfheader_t* elf);

/* 设置符号上下文 */
void glibc_bridge_set_symbol_context(elfheader_t* elf, Elf64_Sym* symtab, 
                               const char* strtab, size_t symcount);

/* 解析符号（返回包装函数、bionic 或内部函数）*/
void* glibc_bridge_resolve_symbol(const char* name);

/* 释放 ELF 头部 */
void elf_free(elfheader_t* elf);

/* ============================================================================
 * 内部函数 - 执行
 * ============================================================================ */

/* 设置程序栈（Linux ABI）*/
uintptr_t setup_stack(void* stack_base, size_t stack_size,
                      int argc, char** argv, char** envp,
                      elfheader_t* elf);

/* 设置 glibc 兼容的 TLS */
glibc_tls_t* setup_glibc_tls(elfheader_t* elf);

/* 释放 TLS */
void free_glibc_tls(glibc_tls_t* tls);

/* 设置 TLS 寄存器（tpidr_el0）*/
void set_tls_register(void* tcb);

/* 跳转到入口点（不返回）*/
__attribute__((noreturn))
void jump_to_entry(uintptr_t entry, uintptr_t sp);

/* 在 fork 的进程中运行 ELF */
int run_elf_forked(glibc_bridge_t bta, elfheader_t* elf,
                   int argc, char** argv, char** envp,
                   glibc_bridge_result_t* result);

/* 直接在当前进程运行 ELF（JNI 兼容）*/
int run_elf_direct(glibc_bridge_t bta, elfheader_t* elf,
                   int argc, char** argv, char** envp,
                   glibc_bridge_result_t* result);

/* 直接执行模式的退出处理器 */
void glibc_bridge_exit_handler(int code);
int glibc_bridge_exit_handler_active(void);

/* ============================================================================
 * 内部函数 - 内存
 * ============================================================================ */

/* 分配可执行内存 */
void* alloc_exec_memory(size_t size, uintptr_t hint);

/* 释放内存 */
void free_memory(void* ptr, size_t size);

/* 分配栈 */
void* alloc_stack(size_t size);

/* 释放栈 */
void free_stack(void* stack, size_t size);

/* ============================================================================
 * 内部函数 - stdio 兼容层
 * ============================================================================ */

/* 获取 bionic FILE 指针 */
FILE* glibc_bridge_get_bionic_fp(void* glibc_fp);

/* 初始化 stdio 兼容层 */
void glibc_bridge_stdio_init(void);

/* ============================================================================
 * 内部函数 - 日志
 * ============================================================================ */

/* 内部日志级别 */
#define GLIBC_BRIDGE_LOG_LVL_ERROR   1
#define GLIBC_BRIDGE_LOG_LVL_WARN    2
#define GLIBC_BRIDGE_LOG_LVL_INFO    3
#define GLIBC_BRIDGE_LOG_LVL_DEBUG   4

/* 当前日志级别 */
extern int g_glibc_bridge_log_level;

/* 日志宏 */
#ifdef __ANDROID__
#include <android/log.h>
#define GLIBC_BRIDGE_LOG_TAG "glibc-bridge"
#define GLIBC_BRIDGE_LOG(level, fmt, ...) do { \
    if (level <= g_glibc_bridge_log_level) { \
        int prio = ANDROID_LOG_INFO; \
        if (level == GLIBC_BRIDGE_LOG_LVL_ERROR) prio = ANDROID_LOG_ERROR; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_WARN) prio = ANDROID_LOG_WARN; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_DEBUG) prio = ANDROID_LOG_DEBUG; \
        __android_log_print(prio, GLIBC_BRIDGE_LOG_TAG, fmt, ##__VA_ARGS__); \
    } \
} while(0)
#else
#include <stdio.h>
#define GLIBC_BRIDGE_LOG(level, fmt, ...) do { \
    if (level <= g_glibc_bridge_log_level) { \
        const char* lvl = "INFO"; \
        if (level == GLIBC_BRIDGE_LOG_LVL_ERROR) lvl = "ERROR"; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_WARN) lvl = "WARN"; \
        else if (level == GLIBC_BRIDGE_LOG_LVL_DEBUG) lvl = "DEBUG"; \
        fprintf(stderr, "[glibc-bridge/%s] " fmt "\n", lvl, ##__VA_ARGS__); \
    } \
} while(0)
#endif

#define LOG_ERROR(fmt, ...) GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_ERROR, fmt, ##__VA_ARGS__)
#define LOG_WARN(fmt, ...)  GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_WARN, fmt, ##__VA_ARGS__)
#define LOG_INFO(fmt, ...)  GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_INFO, fmt, ##__VA_ARGS__)
#define LOG_DEBUG(fmt, ...) GLIBC_BRIDGE_LOG(GLIBC_BRIDGE_LOG_LVL_DEBUG, fmt, ##__VA_ARGS__)

#endif /* GLIBC_BRIDGE_PRIVATE_H */
