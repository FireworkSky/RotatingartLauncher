/*
 * glibc-bridge - 文件系统特殊操作包装
 * 
 * 包含 mkfifo, mknod, fcntl, open_tree, pidfd_* 等特殊文件操作
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <linux/fcntl.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * mkfifo - 创建 FIFO (命名管道)
 * ============================================================================ */

int mkfifo_wrapper(const char* pathname, mode_t mode) {
    LOG_DEBUG("mkfifo_wrapper: pathname='%s', mode=0%o", pathname, mode);
    
    int result = mkfifo(pathname, mode);
    
    if (result < 0) {
        int saved_errno = errno;
        
        /* 尝试创建普通文件作为替代 */
        if (saved_errno == EPERM || saved_errno == EACCES || saved_errno == EROFS) {
            int fd = open(pathname, O_CREAT | O_EXCL | O_RDWR, mode);
            if (fd >= 0) {
                close(fd);
                return 0;
            }
            if (errno == EEXIST) return 0;
        }
        
        if (saved_errno == EEXIST) return 0;
        errno = saved_errno;
    }
    
    return result;
}

/* ============================================================================
 * mknod - 创建特殊或普通文件
 * ============================================================================ */

int mknod_wrapper(const char* pathname, mode_t mode, dev_t dev) {
    LOG_DEBUG("mknod_wrapper: pathname='%s', mode=0%o, dev=%lu", 
              pathname, mode, (unsigned long)dev);
    
    int result = mknod(pathname, mode, dev);
    
    if (result < 0) {
        int saved_errno = errno;
        mode_t file_type = mode & S_IFMT;
        
        /* 处理权限错误 */
        if (saved_errno == EPERM || saved_errno == EACCES || saved_errno == ENOTSUP) {
            if (file_type == S_IFREG || file_type == 0 || file_type == S_IFIFO) {
                int fd = open(pathname, O_CREAT | O_EXCL | O_RDWR, mode & 0777);
                if (fd >= 0) { 
                    close(fd); 
                    return 0; 
                }
                if (errno == EEXIST) return 0;
            }
            /* 设备文件在 Android 上无法创建，返回成功 */
            if (file_type == S_IFCHR || file_type == S_IFBLK) return 0;
        }
        
        if (saved_errno == EEXIST) return 0;
        errno = saved_errno;
    }
    
    return result;
}

/* ============================================================================
 * mknodat - 相对目录创建特殊文件
 * ============================================================================ */

int mknodat_wrapper(int dirfd, const char* pathname, mode_t mode, dev_t dev) {
    LOG_DEBUG("mknodat_wrapper: dirfd=%d, pathname='%s', mode=0%o, dev=%lu",
              dirfd, pathname, mode, (unsigned long)dev);
    
    return mknodat(dirfd, pathname, mode, dev);
}

/* ============================================================================
 * __xmknod - glibc 内部 mknod 接口
 * ============================================================================ */

int __xmknod_wrapper(int ver, const char* path, mode_t mode, dev_t* dev) {
    LOG_DEBUG("__xmknod_wrapper: ver=%d, path='%s', mode=0%o", ver, path, mode);
    
    (void)ver;  /* 版本参数在现代系统上被忽略 */
    
    dev_t device = dev ? *dev : 0;
    return mknod_wrapper(path, mode, device);
}

int __xmknodat_wrapper(int ver, int fd, const char* path, mode_t mode, dev_t* dev) {
    LOG_DEBUG("__xmknodat_wrapper: ver=%d, fd=%d, path='%s', mode=0%o", 
              ver, fd, path, mode);
    
    (void)ver;
    
    dev_t device = dev ? *dev : 0;
    return mknodat_wrapper(fd, path, mode, device);
}

/* ============================================================================
 * fcntl 包装
 * 
 * 处理 glibc 和 bionic 之间的 F_* 常量差异
 * ============================================================================ */

/* glibc fcntl 常量 */
#define GLIBC_F_GETLK   5
#define GLIBC_F_SETLK   6
#define GLIBC_F_SETLKW  7
#define GLIBC_F_GETLK64 12
#define GLIBC_F_SETLK64 13
#define GLIBC_F_SETLKW64 14

int fcntl_wrapper(int fd, int cmd, ...) {
    va_list ap;
    va_start(ap, cmd);
    
    LOG_DEBUG("fcntl_wrapper: fd=%d, cmd=%d", fd, cmd);
    
    int result;
    
    /* 转换 glibc 命令到 bionic */
    int bionic_cmd = cmd;
    
    switch (cmd) {
        case GLIBC_F_GETLK:
        case GLIBC_F_GETLK64:
            bionic_cmd = F_GETLK;
            break;
        case GLIBC_F_SETLK:
        case GLIBC_F_SETLK64:
            bionic_cmd = F_SETLK;
            break;
        case GLIBC_F_SETLKW:
        case GLIBC_F_SETLKW64:
            bionic_cmd = F_SETLKW;
            break;
    }
    
    /* 根据命令类型获取参数 */
    switch (bionic_cmd) {
        case F_DUPFD:
        case F_DUPFD_CLOEXEC:
        case F_SETFD:
        case F_SETFL:
        case F_SETOWN:
        case F_SETSIG:
        case F_SETLEASE:
        case F_NOTIFY:
        case F_SETPIPE_SZ: {
            int arg = va_arg(ap, int);
            result = fcntl(fd, bionic_cmd, arg);
            break;
        }
        
        case F_GETLK:
        case F_SETLK:
        case F_SETLKW: {
            struct flock* lock = va_arg(ap, struct flock*);
            result = fcntl(fd, bionic_cmd, lock);
            break;
        }
        
        default:
            result = fcntl(fd, bionic_cmd);
            break;
    }
    
    va_end(ap);
    return result;
}

/* ============================================================================
 * open_tree - 打开文件系统子树 (Linux 5.2+)
 * Android 可能不支持
 * ============================================================================ */

#ifndef __NR_open_tree
#define __NR_open_tree 428
#endif

int open_tree_wrapper(int dirfd, const char* pathname, unsigned int flags) {
    LOG_DEBUG("open_tree_wrapper: dirfd=%d, pathname='%s', flags=0x%x",
              dirfd, pathname, flags);
    
    int result = syscall(__NR_open_tree, dirfd, pathname, flags);
    
    if (result < 0) {
        LOG_DEBUG("open_tree_wrapper: 失败，errno=%d (%s)", errno, strerror(errno));
    }
    
    return result;
}

/* ============================================================================
 * pidfd_* - 进程文件描述符操作 (Linux 5.3+)
 * ============================================================================ */

#ifndef __NR_pidfd_open
#define __NR_pidfd_open 434
#endif

#ifndef __NR_pidfd_send_signal
#define __NR_pidfd_send_signal 424
#endif

int pidfd_open_wrapper(pid_t pid, unsigned int flags) {
    LOG_DEBUG("pidfd_open_wrapper: pid=%d, flags=0x%x", pid, flags);
    return syscall(__NR_pidfd_open, pid, flags);
}

int pidfd_send_signal_wrapper(int pidfd, int sig, siginfo_t* info, unsigned int flags) {
    LOG_DEBUG("pidfd_send_signal_wrapper: pidfd=%d, sig=%d, flags=0x%x", pidfd, sig, flags);
    return syscall(__NR_pidfd_send_signal, pidfd, sig, info, flags);
}

/* ============================================================================
 * name_to_handle_at / open_by_handle_at
 * ============================================================================ */

int name_to_handle_at_wrapper(int dirfd, const char* pathname, void* handle, 
                               int* mount_id, int flags) {
    LOG_DEBUG("name_to_handle_at_wrapper: pathname='%s'", pathname);
    
    /* 这些函数在 Android 上支持有限 */
    errno = ENOTSUP;
    (void)dirfd;
    (void)handle;
    (void)mount_id;
    (void)flags;
    return -1;
}

int open_by_handle_at_wrapper(int mount_fd, void* handle, int flags) {
    LOG_DEBUG("open_by_handle_at_wrapper");
    
    errno = ENOTSUP;
    (void)mount_fd;
    (void)handle;
    (void)flags;
    return -1;
}

/* ============================================================================
 * renameat2 - 带标志的重命名 (Linux 3.15+)
 * ============================================================================ */

/* renameat2_wrapper 定义在 wrapper_stat.c 中（有路径翻译）*/

/* ============================================================================
 * statx - 扩展文件状态 (Linux 4.11+)
 * ============================================================================ */

#ifndef __NR_statx
#define __NR_statx 291
#endif

int statx_wrapper(int dirfd, const char* pathname, int flags,
                   unsigned int mask, void* statxbuf) {
    LOG_DEBUG("statx_wrapper: pathname='%s', flags=0x%x, mask=0x%x",
              pathname, flags, mask);
    
    return syscall(__NR_statx, dirfd, pathname, flags, mask, statxbuf);
}
