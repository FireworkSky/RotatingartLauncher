/*
 * glibc-bridge 公共 API
 * 
 * 在 Android 上运行 Linux ARM64 glibc 可执行文件的兼容层
 * 
 * 版权所有 (c) 2024
 * MIT 许可证
 */

#ifndef GLIBC_BRIDGE_API_H
#define GLIBC_BRIDGE_API_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * 版本信息
 * ============================================================================ */

#define GLIBC_BRIDGE_VERSION_MAJOR     1
#define GLIBC_BRIDGE_VERSION_MINOR     0
#define GLIBC_BRIDGE_VERSION_PATCH     0
#define GLIBC_BRIDGE_VERSION_STRING    "1.0.0"

/* ============================================================================
 * 错误码
 * ============================================================================ */

typedef enum {
    GLIBC_BRIDGE_OK                    = 0,      /* 成功 */
    GLIBC_BRIDGE_ERROR_INVALID_ARG     = -1,     /* 无效参数 */
    GLIBC_BRIDGE_ERROR_FILE_NOT_FOUND  = -2,     /* 文件不存在 */
    GLIBC_BRIDGE_ERROR_INVALID_ELF     = -3,     /* 无效 ELF 文件 */
    GLIBC_BRIDGE_ERROR_LOAD_FAILED     = -4,     /* 加载失败 */
    GLIBC_BRIDGE_ERROR_EXEC_FAILED     = -5,     /* 执行失败 */
    GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY   = -6,     /* 内存不足 */
    GLIBC_BRIDGE_ERROR_NOT_SUPPORTED   = -7,     /* 不支持 */
    GLIBC_BRIDGE_ERROR_FORK_FAILED     = -8,     /* fork 失败 */
    GLIBC_BRIDGE_ERROR_SIGNAL          = -100,   /* 信号终止（加信号号获取具体信号） */
} glibc_bridge_error_t;

/* ============================================================================
 * 日志级别
 * ============================================================================ */

typedef enum {
    GLIBC_BRIDGE_LOG_NONE      = 0,  /* 无日志 */
    GLIBC_BRIDGE_LOG_ERROR     = 1,  /* 仅错误 */
    GLIBC_BRIDGE_LOG_WARN      = 2,  /* 警告及以上 */
    GLIBC_BRIDGE_LOG_INFO      = 3,  /* 信息及以上 */
    GLIBC_BRIDGE_LOG_DEBUG     = 4,  /* 调试及以上 */
} glibc_bridge_log_level_t;

/* ============================================================================
 * 配置结构
 * ============================================================================ */

typedef struct glibc_bridge_config_s {
    glibc_bridge_log_level_t   log_level;        /* 日志详细程度 */
    size_t              stack_size;              /* 栈大小（默认：8MB）*/
    int                 redirect_output;         /* 重定向 stdout/stderr 到 logcat */
    int                 use_tls;                 /* 设置 glibc 兼容的 TLS */
    const char*         lib_path;                /* 动态链接库搜索路径 */
    int                 direct_execution;        /* 直接执行（不 fork，JNI 必须）*/
} glibc_bridge_config_t;

/* 默认配置 */
#define GLIBC_BRIDGE_CONFIG_DEFAULT { \
    .log_level = GLIBC_BRIDGE_LOG_INFO, \
    .stack_size = 32 * 1024 * 1024, \
    .redirect_output = 1, \
    .use_tls = 1, \
    .lib_path = NULL, \
    .direct_execution = 1 \
}

/* ============================================================================
 * 句柄类型
 * ============================================================================ */

typedef struct glibc_bridge_s*         glibc_bridge_t;     /* 运行时句柄 */
typedef struct glibc_bridge_elf_s*     glibc_bridge_elf_t; /* ELF 句柄 */

/* ============================================================================
 * ELF 信息
 * ============================================================================ */

typedef struct glibc_bridge_elf_info_s {
    const char*     path;           /* 文件路径 */
    uint8_t         is_64bit;       /* 1=64位, 0=32位 */
    uint8_t         is_arm64;       /* 1=ARM64/AArch64 */
    uint8_t         is_static;      /* 1=静态链接 */
    uint8_t         is_pie;         /* 1=位置无关 */
    uintptr_t       entry_point;    /* 入口点地址 */
    uintptr_t       load_addr;      /* 加载地址 */
    size_t          memory_size;    /* 内存占用 */
} glibc_bridge_elf_info_t;

/* ============================================================================
 * 执行结果
 * ============================================================================ */

typedef struct glibc_bridge_result_s {
    int             exit_code;      /* 正常退出时的退出码 */
    int             signal;         /* 被信号终止时的信号号 */
    int             exited;         /* 1=正常退出, 0=被信号终止 */
    char*           stdout_buf;     /* 捕获的 stdout（如果 redirect_output）*/
    size_t          stdout_len;     /* stdout 长度 */
    char*           stderr_buf;     /* 捕获的 stderr */
    size_t          stderr_len;     /* stderr 长度 */
} glibc_bridge_result_t;

/* ============================================================================
 * 核心 API 函数
 * ============================================================================ */

/* 获取版本字符串 */
const char* glibc_bridge_version(void);

/* 初始化运行时 */
glibc_bridge_t glibc_bridge_init(const glibc_bridge_config_t* config);

/* 清理运行时 */
void glibc_bridge_cleanup(glibc_bridge_t bridge);

/* 设置日志级别 */
void glibc_bridge_set_log_level(glibc_bridge_t bridge, glibc_bridge_log_level_t level);

/* 设置库搜索路径 */
void glibc_bridge_set_lib_path(glibc_bridge_t bridge, const char* lib_path);

/* ============================================================================
 * ELF 加载 API
 * ============================================================================ */

/* 加载 ELF 可执行文件 */
glibc_bridge_elf_t glibc_bridge_load(glibc_bridge_t bridge, const char* path);

/* 获取 ELF 信息 */
glibc_bridge_error_t glibc_bridge_elf_info(glibc_bridge_elf_t elf, glibc_bridge_elf_info_t* info);

/* 卸载 ELF */
void glibc_bridge_unload(glibc_bridge_elf_t elf);

/* ============================================================================
 * 执行 API
 * ============================================================================ */

/* 运行已加载的 ELF */
int glibc_bridge_run(glibc_bridge_t bridge, glibc_bridge_elf_t elf, 
              int argc, char** argv, char** envp,
              glibc_bridge_result_t* result);

/* 加载并运行 ELF（便捷函数）*/
int glibc_bridge_exec(glibc_bridge_t bridge, const char* path,
               int argc, char** argv, char** envp,
               glibc_bridge_result_t* result);

/* 释放结果缓冲区 */
void glibc_bridge_result_free(glibc_bridge_result_t* result);

/* ============================================================================
 * 主入口点 - 自动初始化
 * ============================================================================ */

/* 执行 glibc 程序 - 主入口点（自动初始化）*/
int glibc_bridge_execute(const char* path, int argc, char** argv, char** envp, const char* rootfs_path);

/* ============================================================================
 * 工具函数
 * ============================================================================ */

/* 检查文件是否为有效 ELF */
int glibc_bridge_is_valid_elf(const char* path);

/* 获取错误信息 */
const char* glibc_bridge_strerror(glibc_bridge_error_t error);

#ifdef __cplusplus
}
#endif

#endif /* GLIBC_BRIDGE_API_H */
