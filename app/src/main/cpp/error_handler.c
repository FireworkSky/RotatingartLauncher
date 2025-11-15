/**
 * @file error_handler.c
 * @brief Error handling implementation
 * 
 * 参考 CoreHost 的错误处理机制，捕获并记录崩溃信息
 */

#include "error_handler.h"
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <ucontext.h>
#include <dlfcn.h>
#include <pthread.h>

#define LOG_TAG "ErrorHandler"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 全局状态
static JavaVM* g_jvm = NULL;
static JNIEnv* g_env = NULL;
static struct sigaction g_old_sigactions[32];
static int g_initialized = 0;

/**
 * @brief 获取信号名称
 */
static const char* get_signal_name(int sig) {
    switch (sig) {
        case SIGABRT: return "SIGABRT";
        case SIGBUS: return "SIGBUS";
        case SIGFPE: return "SIGFPE";
        case SIGILL: return "SIGILL";
        case SIGSEGV: return "SIGSEGV";
        case SIGTRAP: return "SIGTRAP";
        case SIGSYS: return "SIGSYS";
        default: return "UNKNOWN";
    }
}

/**
 * @brief 记录寄存器状态（ARM64）
 */
static void log_registers(ucontext_t* uc) {
    if (!uc) return;
    
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGE("Register State (ARM64):");
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
#ifdef __aarch64__
    mcontext_t* mc = &uc->uc_mcontext;
    
    // 通用寄存器
    for (int i = 0; i < 31; i++) {
        LOGE("  x%-2d: 0x%016llx", i, (unsigned long long)mc->regs[i]);
    }
    
    // 特殊寄存器
    LOGE("  sp:  0x%016llx (Stack Pointer)", (unsigned long long)mc->sp);
    LOGE("  pc:  0x%016llx (Program Counter)", (unsigned long long)mc->pc);
    LOGE("  pstate: 0x%016llx", (unsigned long long)mc->pstate);
    
    // 尝试解析 PC 地址
    Dl_info info;
    if (dladdr((void*)mc->pc, &info)) {
        LOGE("  PC in: %s", info.dli_fname ? info.dli_fname : "unknown");
        if (info.dli_sname) {
            LOGE("  Symbol: %s + 0x%llx", info.dli_sname, 
                 (unsigned long long)((char*)mc->pc - (char*)info.dli_saddr));
        }
    }
#endif
}

/**
 * @brief 记录栈回溯（简化版）
 */
static void log_backtrace(void* pc) {
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGE("Backtrace:");
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
    Dl_info info;
    if (dladdr(pc, &info)) {
        LOGE("  #0  pc %p  %s", pc, info.dli_fname ? info.dli_fname : "???");
        if (info.dli_sname) {
            LOGE("      %s + 0x%llx", info.dli_sname,
                 (unsigned long long)((char*)pc - (char*)info.dli_saddr));
        }
    } else {
        LOGE("  #0  pc %p  ???", pc);
    }
    
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGE("Note: Use 'adb logcat | ndk-stack -sym <path/to/symbols>' for full backtrace");
}

/**
 * @brief 记录 CoreCLR 运行时状态
 */
static void log_coreclr_state(void) {
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGE("CoreCLR Runtime State:");
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
    // 检查环境变量
    const char* gc_server = getenv("COMPlus_gcServer");
    const char* gc_concurrent = getenv("COMPlus_gcConcurrent");
    const char* threadpool_min = getenv("COMPlus_ThreadPool_ForceMinWorkerThreads");
    const char* threadpool_max = getenv("COMPlus_ThreadPool_ForceMaxWorkerThreads");
    
    LOGE("  GC Mode: Server=%s, Concurrent=%s", 
         gc_server ? gc_server : "default",
         gc_concurrent ? gc_concurrent : "default");
    LOGE("  ThreadPool: Min=%s, Max=%s",
         threadpool_min ? threadpool_min : "default",
         threadpool_max ? threadpool_max : "default");
    
    // 线程信息
    LOGE("  Thread ID: %d", gettid());
    LOGE("  Process ID: %d", getpid());
}

/**
 * @brief Signal handler
 */
static void signal_handler(int sig, siginfo_t* info, void* context) {
    // 防止递归崩溃
    static volatile sig_atomic_t in_handler = 0;
    if (in_handler) {
        LOGE("Recursive crash detected in signal handler!");
        _exit(1);
    }
    in_handler = 1;
    
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGE("FATAL CRASH DETECTED");
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGE("Signal: %d (%s)", sig, get_signal_name(sig));
    LOGE("Code: %d", info->si_code);
    LOGE("Address: %p", info->si_addr);
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
    // 特殊处理 SIGABRT（pthread mutex 错误）
    if (sig == SIGABRT) {
        LOGE("SIGABRT Details:");
        LOGE("  Common causes:");
        LOGE("  - pthread_mutex_lock on destroyed mutex");
        LOGE("  - Assertion failure");
        LOGE("  - abort() called");
        LOGE("  - Fatal GC error");
        LOGE("");
        LOGE("Possible CoreCLR issues:");
        LOGE("  - Concurrent GC thread race condition");
        LOGE("  - ThreadPool shutdown issue");
        LOGE("  - Native/managed code interaction problem");
    }
    
    // 记录运行时状态
    log_coreclr_state();
    
    // 记录寄存器
    if (context) {
        log_registers((ucontext_t*)context);
    }
    
    // 记录栈回溯
    void* pc = NULL;
#ifdef __aarch64__
    if (context) {
        pc = (void*)((ucontext_t*)context)->uc_mcontext.pc;
    }
#endif
    if (pc) {
        log_backtrace(pc);
    }
    
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGE("Workarounds to try:");
    LOGE("  1. Set COMPlus_gcConcurrent=0 (disable concurrent GC)");
    LOGE("  2. Set COMPlus_gcServer=0 (use workstation GC)");
    LOGE("  3. Reduce ThreadPool threads");
    LOGE("  4. Check for native code memory corruption");
    LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
    // 调用原始的 signal handler（让系统生成 tombstone）
    if (sig >= 0 && sig < 32 && g_old_sigactions[sig].sa_sigaction) {
        g_old_sigactions[sig].sa_sigaction(sig, info, context);
    }
    
    // 如果原始 handler 没有终止进程，我们手动终止
    _exit(1);
}

void error_handler_init(void) {
    if (g_initialized) {
        LOGW("Error handler already initialized");
        return;
    }
    
    LOGI("Initializing error handler...");
    
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = signal_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    
    // 注册关键信号
    int signals[] = {SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGSEGV, SIGTRAP, SIGSYS};
    int num_signals = sizeof(signals) / sizeof(signals[0]);
    
    for (int i = 0; i < num_signals; i++) {
        int sig = signals[i];
        if (sigaction(sig, &sa, &g_old_sigactions[sig]) != 0) {
            LOGW("Failed to register handler for signal %d (%s)", sig, get_signal_name(sig));
        } else {
            LOGI("Registered handler for %s", get_signal_name(sig));
        }
    }

    g_initialized = 1;
    LOGI("Error handler initialized successfully");
}

void error_handler_set_jni_env(JNIEnv* env, JavaVM* vm) {
    g_env = env;
    g_jvm = vm;
    LOGI("JNI environment set for error reporting");
}

void error_handler_log_crash(int sig, siginfo_t* info, void* context) {
    signal_handler(sig, info, context);
}

void error_handler_cleanup(void) {
    if (!g_initialized) return;
    
    LOGI("Cleaning up error handler...");
    
    // 恢复原始的 signal handlers
    int signals[] = {SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGSEGV, SIGTRAP, SIGSYS};
    int num_signals = sizeof(signals) / sizeof(signals[0]);
    
    for (int i = 0; i < num_signals; i++) {
        int sig = signals[i];
        sigaction(sig, &g_old_sigactions[sig], NULL);
    }
    
    g_initialized = 0;
    LOGI("Error handler cleaned up");
}

