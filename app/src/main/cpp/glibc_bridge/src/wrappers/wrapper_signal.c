/*
 * glibc-bridge - 信号处理函数包装
 * 
 * 包含 sigprocmask, sigaction, sigemptyset 等信号相关函数
 * 
 * 注意: ARM64 glibc 程序的 struct sigaction 和 sigset_t 布局与 bionic 匹配，
 * 不需要转换
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <errno.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* 外部标志 - 指示崩溃处理程序已安装 */
extern int g_glibc_bridge_crash_handler_installed;

/* ============================================================================
 * sigprocmask 包装
 * ============================================================================ */

int sigprocmask_wrapper(int how, const sigset_t* set, sigset_t* oldset) {
    WRAPPER_BEGIN("sigprocmask");
    int ret = sigprocmask(how, set, oldset);
    WRAPPER_RETURN(ret);
}

/* ============================================================================
 * sigaction 包装
 * 
 * 保护 glibc-bridge 的崩溃处理程序不被覆盖
 * ============================================================================ */

int sigaction_wrapper(int signum, const struct sigaction* act, struct sigaction* oldact) {
    WRAPPER_BEGIN("sigaction");
    
    /* 保护 glibc-bridge 崩溃处理程序 */
    if (g_glibc_bridge_crash_handler_installed && act != NULL) {
        if (signum == SIGSEGV || signum == SIGBUS || signum == SIGFPE || 
            signum == SIGILL || signum == SIGABRT) {
            LOG_DEBUG("sigaction_wrapper: 阻止覆盖信号 %d 的崩溃处理程序", signum);
            /* 返回成功但不实际改变处理程序 */
            if (oldact) {
                memset(oldact, 0, sizeof(struct sigaction));
            }
            CLEAR_WRAPPER();
            return 0;
        }
    }
    
    int ret = sigaction(signum, act, oldact);
    WRAPPER_RETURN(ret);
}

/* ============================================================================
 * sigset 操作函数
 * ============================================================================ */

int sigemptyset_wrapper(sigset_t* set) {
    WRAPPER_BEGIN("sigemptyset");
    int ret = sigemptyset(set);
    WRAPPER_RETURN(ret);
}

int sigfillset_wrapper(sigset_t* set) {
    WRAPPER_BEGIN("sigfillset");
    int ret = sigfillset(set);
    WRAPPER_RETURN(ret);
}

int sigaddset_wrapper(sigset_t* set, int signum) {
    WRAPPER_BEGIN("sigaddset");
    int ret = sigaddset(set, signum);
    WRAPPER_RETURN(ret);
}

int sigdelset_wrapper(sigset_t* set, int signum) {
    WRAPPER_BEGIN("sigdelset");
    int ret = sigdelset(set, signum);
    WRAPPER_RETURN(ret);
}

int sigismember_wrapper(const sigset_t* set, int signum) {
    WRAPPER_BEGIN("sigismember");
    int ret = sigismember(set, signum);
    WRAPPER_RETURN(ret);
}

int sigisemptyset_wrapper(const sigset_t* set) {
    /* glibc 扩展，bionic 可能没有 */
    if (!set) {
        errno = EINVAL;
        return -1;
    }
    
    /* 检查 sigset 是否为空 */
    sigset_t empty;
    sigemptyset(&empty);
    
    /* 比较两个 sigset */
    return memcmp(set, &empty, sizeof(sigset_t)) == 0 ? 1 : 0;
}

/* ============================================================================
 * 信号发送函数
 * ============================================================================ */

int kill_wrapper(pid_t pid, int sig) {
    LOG_DEBUG("kill_wrapper: pid=%d, sig=%d", pid, sig);
    return kill(pid, sig);
}

int killpg_wrapper(int pgrp, int sig) {
    LOG_DEBUG("killpg_wrapper: pgrp=%d, sig=%d", pgrp, sig);
    return killpg(pgrp, sig);
}

int raise_wrapper(int sig) {
    LOG_DEBUG("raise_wrapper: sig=%d", sig);
    return raise(sig);
}

int sigqueue_wrapper(pid_t pid, int sig, const union sigval value) {
    LOG_DEBUG("sigqueue_wrapper: pid=%d, sig=%d", pid, sig);
    return sigqueue(pid, sig, value);
}

/* ============================================================================
 * 信号等待函数
 * ============================================================================ */

int sigwait_wrapper(const sigset_t* set, int* sig) {
    return sigwait(set, sig);
}

int sigwaitinfo_wrapper(const sigset_t* set, siginfo_t* info) {
    return sigwaitinfo(set, info);
}

int sigtimedwait_wrapper(const sigset_t* set, siginfo_t* info, 
                          const struct timespec* timeout) {
    return sigtimedwait(set, info, timeout);
}

int sigsuspend_wrapper(const sigset_t* mask) {
    return sigsuspend(mask);
}

/* ============================================================================
 * 信号处理函数
 * ============================================================================ */

typedef void (*sighandler_t)(int);

void* signal_wrapper(int signum, void* handler) {
    LOG_DEBUG("signal_wrapper: signum=%d, handler=%p", signum, handler);
    return (void*)signal(signum, (sighandler_t)handler);
}

sighandler_t bsd_signal_wrapper(int signum, sighandler_t handler) {
    return signal_wrapper(signum, handler);
}

sighandler_t sysv_signal_wrapper(int signum, sighandler_t handler) {
    return signal_wrapper(signum, handler);
}

/* ============================================================================
 * 信号栈函数
 * ============================================================================ */

int sigaltstack_wrapper(const stack_t* ss, stack_t* old_ss) {
    return sigaltstack(ss, old_ss);
}

/* ============================================================================
 * 信号阻塞函数
 * ============================================================================ */

int sigblock_wrapper(int mask) {
    /* 已废弃，但某些程序仍使用 */
    sigset_t set;
    sigemptyset(&set);
    
    for (int i = 1; i <= 31; i++) {
        if (mask & (1 << (i - 1))) {
            sigaddset(&set, i);
        }
    }
    
    sigset_t oldset;
    if (sigprocmask(SIG_BLOCK, &set, &oldset) < 0) {
        return -1;
    }
    
    int oldmask = 0;
    for (int i = 1; i <= 31; i++) {
        if (sigismember(&oldset, i)) {
            oldmask |= (1 << (i - 1));
        }
    }
    
    return oldmask;
}

int sigsetmask_wrapper(int mask) {
    /* 已废弃 */
    sigset_t set;
    sigemptyset(&set);
    
    for (int i = 1; i <= 31; i++) {
        if (mask & (1 << (i - 1))) {
            sigaddset(&set, i);
        }
    }
    
    sigset_t oldset;
    if (sigprocmask(SIG_SETMASK, &set, &oldset) < 0) {
        return -1;
    }
    
    int oldmask = 0;
    for (int i = 1; i <= 31; i++) {
        if (sigismember(&oldset, i)) {
            oldmask |= (1 << (i - 1));
        }
    }
    
    return oldmask;
}

int siggetmask_wrapper(void) {
    /* 已废弃 */
    sigset_t set;
    if (sigprocmask(0, NULL, &set) < 0) {
        return -1;
    }
    
    int mask = 0;
    for (int i = 1; i <= 31; i++) {
        if (sigismember(&set, i)) {
            mask |= (1 << (i - 1));
        }
    }
    
    return mask;
}

/* ============================================================================
 * sigpending 包装
 * ============================================================================ */

int sigpending_wrapper(sigset_t* set) {
    return sigpending(set);
}

/* ============================================================================
 * psignal / psiginfo 包装
 * ============================================================================ */

void psignal_wrapper(int sig, const char* s) {
    psignal(sig, s);
}

void psiginfo_wrapper(const siginfo_t* pinfo, const char* s) {
    psiginfo(pinfo, s);
}

/* ============================================================================
 * strsignal 包装
 * ============================================================================ */

char* strsignal_wrapper(int sig) {
    return strsignal(sig);
}

/* ============================================================================
 * siginterrupt 包装
 * ============================================================================ */

int siginterrupt_wrapper(int sig, int flag) {
    return siginterrupt(sig, flag);
}
