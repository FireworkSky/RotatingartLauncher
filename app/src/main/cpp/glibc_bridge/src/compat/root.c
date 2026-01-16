/*
 * glibc-bridge - Root 兼容层
 * 
 * 提供身份和信号处理的基本兼容
 * 简单直接，不做 proot 风格的 fake root 模拟
 */

#include "root.h"
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <unistd.h>

/* ============================================================================
 * 信号处理
 * ============================================================================ */

#define MAX_SIGNALS 64
static glibc_bridge_signal_handler_t s_signal_handlers[MAX_SIGNALS] = {0};

static void internal_signal_handler(int sig) {
    if (sig >= 0 && sig < MAX_SIGNALS && s_signal_handlers[sig]) {
        s_signal_handlers[sig](sig);
    }
}

void glibc_bridge_root_init(void) {
    memset(s_signal_handlers, 0, sizeof(s_signal_handlers));
}

glibc_bridge_signal_handler_t glibc_bridge_signal(int signum, glibc_bridge_signal_handler_t handler) {
    if (signum < 0 || signum >= MAX_SIGNALS) {
        errno = EINVAL;
        return SIG_ERR;
    }
    
    glibc_bridge_signal_handler_t old_handler = s_signal_handlers[signum];
    s_signal_handlers[signum] = handler;
    
    struct sigaction sa, old_sa;
    memset(&sa, 0, sizeof(sa));
    
    if (handler == SIG_DFL || handler == SIG_IGN) {
        sa.sa_handler = handler;
    } else {
        sa.sa_handler = internal_signal_handler;
    }
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;
    
    if (sigaction(signum, &sa, &old_sa) < 0) {
        s_signal_handlers[signum] = old_handler;
        return SIG_ERR;
    }
    
    return old_handler;
}

int glibc_bridge_raise(int sig) {
    if (sig >= 0 && sig < MAX_SIGNALS && s_signal_handlers[sig] &&
        s_signal_handlers[sig] != SIG_DFL && s_signal_handlers[sig] != SIG_IGN) {
        s_signal_handlers[sig](sig);
        return 0;
    }
    return kill(getpid(), sig);
}

/* ============================================================================
 * 身份函数 - 直接转发
 * ============================================================================ */

uid_t glibc_bridge_getuid(void)  { return getuid(); }
uid_t glibc_bridge_geteuid(void) { return geteuid(); }
gid_t glibc_bridge_getgid(void)  { return getgid(); }
gid_t glibc_bridge_getegid(void) { return getegid(); }

int glibc_bridge_setuid(uid_t uid)   { return setuid(uid); }
int glibc_bridge_setgid(gid_t gid)   { return setgid(gid); }
int glibc_bridge_seteuid(uid_t euid) { return seteuid(euid); }
int glibc_bridge_setegid(gid_t egid) { return setegid(egid); }

/* ============================================================================
 * Capability - Android 不支持
 * ============================================================================ */

int glibc_bridge_capget(void* hdrp, void* datap) {
    (void)hdrp; (void)datap;
    errno = ENOSYS;
    return -1;
}

int glibc_bridge_capset(void* hdrp, const void* datap) {
    (void)hdrp; (void)datap;
    errno = ENOSYS;
    return -1;
}
