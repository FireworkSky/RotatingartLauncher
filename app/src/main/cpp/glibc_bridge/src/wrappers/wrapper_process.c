/*
 * glibc-bridge - 进程生命周期管理包装
 * 
 * 包含 __libc_start_main, atexit, __cxa_atexit, exit, abort 等进程管理函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <signal.h>
#include <unistd.h>
#include <errno.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

#ifdef __ANDROID__
#include <android/log.h>
#endif

/* ============================================================================
 * 全局程序名变量 (glibc 兼容)
 * ============================================================================ */

char* __progname = NULL;
char* __progname_full = NULL;
char* program_invocation_name = NULL;
char* program_invocation_short_name = NULL;

/* ============================================================================
 * 应用文件目录
 * ============================================================================ */

const char* g_app_files_dir = NULL;
static char g_app_base_dir[512] = {0};

void glibc_bridge_set_app_files_dir(const char* dir) {
    g_app_files_dir = dir;
    
    if (g_app_base_dir[0] == '\0' && dir && dir[0]) {
        const char* files_marker = strstr(dir, "/files");
        if (files_marker) {
            const char* end = files_marker + 6;
            if (*end == '/' || *end == '\0') {
                size_t len = end - dir;
                if (len < sizeof(g_app_base_dir)) {
                    memcpy(g_app_base_dir, dir, len);
                    g_app_base_dir[len] = '\0';
#ifdef __ANDROID__
                    __android_log_print(ANDROID_LOG_INFO, "glibc-bridge", 
                        "应用基础目录设置为: %s (来自工作目录: %s)", 
                        g_app_base_dir, dir);
#endif
                }
            }
        }
        
        if (g_app_base_dir[0] == '\0') {
            strncpy(g_app_base_dir, dir, sizeof(g_app_base_dir) - 1);
        }
    }
}

const char* glibc_bridge_get_app_base_dir(void) {
    return g_app_base_dir[0] ? g_app_base_dir : g_app_files_dir;
}

/* ============================================================================
 * error 函数 - glibc 特有的错误报告函数
 * ============================================================================ */

void error_wrapper(int status, int errnum, const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    
    fprintf(stderr, "%s: ", program_invocation_short_name ? 
            program_invocation_short_name : "program");
    vfprintf(stderr, format, ap);
    
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
 * __register_atfork 包装
 * ============================================================================ */

int __register_atfork_wrapper(void (*prepare)(void), void (*parent)(void), 
                               void (*child)(void), void* __dso_handle) {
    (void)__dso_handle;
    return pthread_atfork(prepare, parent, child);
}

/* ============================================================================
 * __libc_start_main - glibc 程序入口点
 * 
 * 这是 glibc 程序的主入口，由 _start 调用。
 * 我们需要设置环境然后调用 main。
 * ============================================================================ */

typedef int (*main_fn_t)(int, char**, char**);

/* 用于保存/恢复 atexit 处理程序 */
#define MAX_ATEXIT_HANDLERS 64
static void (*g_atexit_handlers[MAX_ATEXIT_HANDLERS])(void);
static int g_atexit_count = 0;

/* __cxa_atexit 处理程序 */
typedef struct {
    void (*func)(void*);
    void* arg;
    void* dso_handle;
} cxa_atexit_entry_t;

#define MAX_CXA_ATEXIT_HANDLERS 256
static cxa_atexit_entry_t g_cxa_atexit_handlers[MAX_CXA_ATEXIT_HANDLERS];
static int g_cxa_atexit_count = 0;

int __libc_start_main_wrapper(
    int (*main)(int, char**, char**),
    int argc,
    char** argv,
    int (*init)(int, char**, char**),
    void (*fini)(void),
    void (*rtld_fini)(void),
    void* stack_end
) {
    (void)stack_end;
    (void)init;
    (void)fini;
    (void)rtld_fini;
    
    LOG_DEBUG("__libc_start_main_wrapper: main=%p, argc=%d", (void*)main, argc);
    
    /* 设置程序名变量 */
    if (argc > 0 && argv && argv[0]) {
        __progname_full = argv[0];
        program_invocation_name = argv[0];
        
        char* slash = strrchr(argv[0], '/');
        __progname = slash ? slash + 1 : argv[0];
        program_invocation_short_name = __progname;
    }
    
    /* 调用初始化函数 */
    if (init) {
        LOG_DEBUG("调用 init 函数: %p", (void*)init);
        extern char** environ;
        init(argc, argv, environ);
    }
    
    /* 调用 main */
    extern char** environ;
    LOG_DEBUG("调用 main: argc=%d", argc);
    int result = main(argc, argv, environ);
    LOG_DEBUG("main 返回: %d", result);
    
    /* 调用退出处理程序 */
    exit_wrapper(result);
    
    /* 不应到达这里 */
    return result;
}

/* ============================================================================
 * atexit - 注册退出处理程序
 * ============================================================================ */

int atexit_wrapper(void (*function)(void)) {
    LOG_DEBUG("atexit_wrapper: 注册处理程序 %p", (void*)function);
    
    if (g_atexit_count >= MAX_ATEXIT_HANDLERS) {
        LOG_DEBUG("atexit_wrapper: 处理程序数组已满");
        return -1;
    }
    
    g_atexit_handlers[g_atexit_count++] = function;
    return 0;
}

/* ============================================================================
 * __cxa_atexit - C++ 退出处理程序
 * ============================================================================ */

int __cxa_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle) {
    LOG_DEBUG("__cxa_atexit_wrapper: func=%p, arg=%p, dso=%p", 
              (void*)func, arg, dso_handle);
    
    if (g_cxa_atexit_count >= MAX_CXA_ATEXIT_HANDLERS) {
        return -1;
    }
    
    g_cxa_atexit_handlers[g_cxa_atexit_count].func = func;
    g_cxa_atexit_handlers[g_cxa_atexit_count].arg = arg;
    g_cxa_atexit_handlers[g_cxa_atexit_count].dso_handle = dso_handle;
    g_cxa_atexit_count++;
    
    return 0;
}

/* ============================================================================
 * __cxa_thread_atexit - 线程退出处理程序
 * ============================================================================ */

int __cxa_thread_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle) {
    LOG_DEBUG("__cxa_thread_atexit_wrapper: func=%p", (void*)func);
    /* 转发到普通 __cxa_atexit，因为 Android 上线程退出处理较复杂 */
    return __cxa_atexit_wrapper(func, arg, dso_handle);
}

int __cxa_thread_atexit_impl_wrapper(void (*func)(void*), void* arg, void* dso_handle) {
    return __cxa_thread_atexit_wrapper(func, arg, dso_handle);
}

/* ============================================================================
 * __cxa_finalize - 调用 DSO 的退出处理程序
 * ============================================================================ */

void __cxa_finalize_wrapper(void* dso_handle) {
    LOG_DEBUG("__cxa_finalize_wrapper: dso=%p", dso_handle);
    
    /* 以 LIFO 顺序调用匹配的处理程序 */
    for (int i = g_cxa_atexit_count - 1; i >= 0; i--) {
        if (dso_handle == NULL || 
            g_cxa_atexit_handlers[i].dso_handle == dso_handle) {
            if (g_cxa_atexit_handlers[i].func) {
                g_cxa_atexit_handlers[i].func(g_cxa_atexit_handlers[i].arg);
                g_cxa_atexit_handlers[i].func = NULL;
            }
        }
    }
}

/* ============================================================================
 * exit - 退出程序
 * ============================================================================ */

void exit_wrapper(int status) {
    LOG_DEBUG("exit_wrapper: status=%d", status);
    
    /* 以 LIFO 顺序调用 __cxa_atexit 处理程序 */
    for (int i = g_cxa_atexit_count - 1; i >= 0; i--) {
        if (g_cxa_atexit_handlers[i].func) {
            LOG_DEBUG("调用 __cxa_atexit 处理程序 %d: %p", 
                     i, (void*)g_cxa_atexit_handlers[i].func);
            g_cxa_atexit_handlers[i].func(g_cxa_atexit_handlers[i].arg);
        }
    }
    
    /* 以 LIFO 顺序调用 atexit 处理程序 */
    for (int i = g_atexit_count - 1; i >= 0; i--) {
        if (g_atexit_handlers[i]) {
            LOG_DEBUG("调用 atexit 处理程序 %d: %p", i, (void*)g_atexit_handlers[i]);
            g_atexit_handlers[i]();
        }
    }
    
    /* 刷新 stdio */
    fflush(stdout);
    fflush(stderr);
    
    /* 调用真正的 exit */
    exit(status);
}

/* ============================================================================
 * abort - 异常终止程序
 * ============================================================================ */

void abort_wrapper(void) __attribute__((noreturn));
void abort_wrapper(void) {
    LOG_DEBUG("abort_wrapper: 终止程序");
    
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge", 
                        "程序调用 abort()");
#endif
    
    /* 先 flush */
    fflush(stdout);
    fflush(stderr);
    
    /* 发送 SIGABRT */
    raise(SIGABRT);
    
    /* 如果信号被阻塞，直接 _exit */
    _exit(134);
}

/* ============================================================================
 * sysconf 包装 - 系统配置值
 * ============================================================================ */

/* glibc sysconf 常量（可能与 bionic 不同）*/
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

#include <sys/sysinfo.h>

long sysconf_wrapper(int name) {
    LOG_DEBUG("sysconf_wrapper: name=%d", name);
    
    /* 将 glibc 常量映射到 bionic 常量 */
    switch (name) {
        case GLIBC_SC_PAGESIZE:
            /* GLIBC_SC_PAGE_SIZE 与 GLIBC_SC_PAGESIZE 相同 */
            return sysconf(_SC_PAGESIZE);
        case GLIBC_SC_NPROCESSORS_CONF:
            return sysconf(_SC_NPROCESSORS_CONF);
        case GLIBC_SC_NPROCESSORS_ONLN:
            return sysconf(_SC_NPROCESSORS_ONLN);
        case GLIBC_SC_PHYS_PAGES:
            return sysconf(_SC_PHYS_PAGES);
        case GLIBC_SC_AVPHYS_PAGES:
            return sysconf(_SC_AVPHYS_PAGES);
        case GLIBC_SC_CLK_TCK:
            return sysconf(_SC_CLK_TCK);
        case GLIBC_SC_OPEN_MAX:
            return sysconf(_SC_OPEN_MAX);
        case GLIBC_SC_NGROUPS_MAX:
            return sysconf(_SC_NGROUPS_MAX);
        case GLIBC_SC_ARG_MAX:
            return sysconf(_SC_ARG_MAX);
        case GLIBC_SC_CHILD_MAX:
            return sysconf(_SC_CHILD_MAX);
        default:
            /* 尝试直接传递 */
            return sysconf(name);
    }
}

/* ============================================================================
 * confstr - 获取配置字符串值
 * bionic 不支持，需要模拟
 * ============================================================================ */

#define CS_PATH             0
#define CS_GNU_LIBC_VERSION 2
#define CS_GNU_LIBPTHREAD_VERSION 3

size_t confstr_wrapper(int name, char* buf, size_t len) {
    const char* value = NULL;
    
    LOG_DEBUG("confstr_wrapper: name=%d", name);
    
    switch (name) {
        case CS_PATH:
            value = "/system/bin:/system/xbin";
            break;
        case CS_GNU_LIBC_VERSION:
            value = "glibc 2.31";
            break;
        case CS_GNU_LIBPTHREAD_VERSION:
            value = "NPTL 2.31";
            break;
        default:
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
 * getdtablesize - 获取文件描述符表大小
 * ============================================================================ */

#include <sys/resource.h>

int getdtablesize_wrapper(void) {
    struct rlimit rl;
    if (getrlimit(RLIMIT_NOFILE, &rl) == 0) {
        return (int)rl.rlim_cur;
    }
    return 1024;  /* 默认值 */
}

/* ============================================================================
 * getsid - 获取会话 ID
 * ============================================================================ */

pid_t getsid_wrapper(pid_t pid) {
    LOG_DEBUG("getsid_wrapper: pid=%d", pid);
    pid_t result = getsid(pid);
    if (result < 0 && errno == EPERM) {
        /* Android 上可能没有权限，返回当前进程 ID */
        return getpid();
    }
    return result;
}

/* ============================================================================
 * assert_fail - 断言失败处理
 * ============================================================================ */

void assert_fail_wrapper(const char* assertion, const char* file, 
                          unsigned int line, const char* function) {
    fprintf(stderr, "%s:%u: %s: 断言 `%s' 失败.\n",
            file, line, function ? function : "?", assertion);
    
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_FATAL, "glibc-bridge",
                        "%s:%u: %s: 断言 `%s' 失败.",
                        file, line, function ? function : "?", assertion);
#endif
    
    abort_wrapper();
}

/* ============================================================================
 * environ - 全局环境变量指针
 * ============================================================================ */

extern char **environ;

void* glibc_bridge_get_environ_addr(void) {
    return &environ;
}

/* ============================================================================
 * PAL_RegisterModule - .NET CoreCLR PAL 存根
 * ============================================================================ */

int PAL_RegisterModule_wrapper(const char* name) {
    LOG_DEBUG("PAL_RegisterModule_wrapper: name=%s", name ? name : "(null)");
    return 1;  /* 返回成功 */
}
