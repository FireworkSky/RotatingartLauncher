/*
 * glibc-bridge - Root 兼容层
 * 
 * 身份和信号处理的基本兼容
 */

#ifndef GLIBC_BRIDGE_ROOT_H
#define GLIBC_BRIDGE_ROOT_H

#include <sys/types.h>
#include <signal.h>

/* 初始化 */
void glibc_bridge_root_init(void);

/* 信号处理 */
typedef void (*glibc_bridge_signal_handler_t)(int);
glibc_bridge_signal_handler_t glibc_bridge_signal(int signum, glibc_bridge_signal_handler_t handler);
int glibc_bridge_raise(int sig);

/* 身份函数 */
uid_t glibc_bridge_getuid(void);
uid_t glibc_bridge_geteuid(void);
gid_t glibc_bridge_getgid(void);
gid_t glibc_bridge_getegid(void);

int glibc_bridge_setuid(uid_t uid);
int glibc_bridge_setgid(gid_t gid);
int glibc_bridge_seteuid(uid_t euid);
int glibc_bridge_setegid(gid_t egid);

/* Capability (Android 不支持) */
int glibc_bridge_capget(void* hdrp, void* datap);
int glibc_bridge_capset(void* hdrp, const void* datap);

#endif /* GLIBC_BRIDGE_ROOT_H */
