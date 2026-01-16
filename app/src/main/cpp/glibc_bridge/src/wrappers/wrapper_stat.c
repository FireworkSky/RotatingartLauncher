/*
 * glibc-bridge - File System Wrapper Functions
 * 
 * Wrappers for glibc's stat/file system functions.
 * 
 * Key differences between glibc and bionic ARM64:
 * 1. glibc uses versioned stat functions (__xstat64, etc.) while
 *    bionic uses direct stat/fstat calls.
 * 2. struct stat layout differs between glibc and bionic ARM64:
 *    - glibc: st_nlink at offset 16 (8 bytes), st_mode at offset 24
 *    - bionic ARM64: st_mode at offset 16, st_nlink at offset 20 (4 bytes)
 * 3. Paths are redirected to fake glibc rootfs when appropriate.
 */

#define _GNU_SOURCE
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/statvfs.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <limits.h>
#include <stdarg.h>
#include <dirent.h>
#include <stdint.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "../include/wrappers.h"
#include "../include/private.h"  /* GLIBC_BRIDGE_LOG */
#include "wrapper_path.h"  /* Common wrapper utilities */

/* ============================================================================
 * stat() family wrappers
 * glibc: __xstat64(ver, path, buf) → bionic: stat(path, buf)
 * Paths are translated to fake glibc rootfs
 * ============================================================================ */

int __fxstat64_wrapper(int ver, int fd, void* buf) {
    SET_WRAPPER("fstat");
    (void)ver;  /* Ignore version, bionic doesn't use it */
    
    int ret = fstat(fd, (struct stat*)buf);
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

int __xstat64_wrapper(int ver, const char* path, void* buf) {
    SET_WRAPPER("stat");
    (void)ver;
    
    int ret = stat(wrapper_translate_path(path), (struct stat*)buf);
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

int __lxstat64_wrapper(int ver, const char* path, void* buf) {
    SET_WRAPPER("lstat");
    (void)ver;
    
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "[__lxstat64] >>> ENTER: path=%s buf=%p", path, buf);
#endif
    
    const char* translated = wrapper_translate_path(path);
    int ret = lstat(translated, (struct stat*)buf);
    
#ifdef __ANDROID__
    if (ret == 0) {
        struct stat* st = (struct stat*)buf;
        __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
            "[__lxstat64] OK: path=%s mode=0%o size=%lld uid=%d gid=%d",
            path, st->st_mode, (long long)st->st_size, st->st_uid, st->st_gid);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
            "[__lxstat64] FAILED: path=%s errno=%d", path, errno);
    }
    __android_log_print(ANDROID_LOG_ERROR, "glibc-bridge",
        "[__lxstat64] <<< EXIT: ret=%d", ret);
#endif
    
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

int __fxstatat64_wrapper(int ver, int dirfd, const char* path, 
                         void* buf, int flags) {
    SET_WRAPPER("fstatat");
    (void)ver;
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    
    int ret = fstatat(dirfd, t_path, (struct stat*)buf, flags);
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

/* ============================================================================
 * Direct stat wrappers (glibc may redirect these to __xstat variants)
 * bionic has direct implementations, paths translated to fake rootfs
 * ============================================================================ */

int stat_wrapper(const char* path, void* buf) {
    SET_WRAPPER("stat");
    
    int ret = stat(wrapper_translate_path(path), (struct stat*)buf);
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

int fstat_wrapper(int fd, void* buf) {
    SET_WRAPPER("fstat");
    
    int ret = fstat(fd, (struct stat*)buf);
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

int lstat_wrapper(const char* path, void* buf) {
    SET_WRAPPER("lstat");
    
    int ret = lstat(wrapper_translate_path(path), (struct stat*)buf);
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

int fstatat_wrapper(int dirfd, const char* path, void* buf, int flags) {
    SET_WRAPPER("fstatat");
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    
    int ret = fstatat(dirfd, t_path, (struct stat*)buf, flags);
    SYNC_ERRNO_IF_FAIL(ret);
    CLEAR_WRAPPER();
    return ret;
}

/* stat64 variants - on 64-bit systems, these are the same as stat */
int stat64_wrapper(const char* path, void* buf) {
    return stat_wrapper(path, buf);  /* Reuse stat_wrapper with conversion */
}

int fstat64_wrapper(int fd, void* buf) {
    return fstat_wrapper(fd, buf);  /* Reuse fstat_wrapper with conversion */
}

int lstat64_wrapper(const char* path, void* buf) {
    return lstat_wrapper(path, buf);  /* Reuse lstat_wrapper with conversion */
}

int fstatat64_wrapper(int dirfd, const char* path, void* buf, int flags) {
    return fstatat_wrapper(dirfd, path, buf, flags);  /* Reuse with conversion */
}

/* ============================================================================
 * statfs/statvfs wrappers
 * ============================================================================ */

int statfs_wrapper(const char* path, struct statfs* buf) {
    return statfs(wrapper_translate_path(path), buf);
}

int fstatfs_wrapper(int fd, struct statfs* buf) {
    return fstatfs(fd, buf);
}

int statfs64_wrapper(const char* path, struct statfs* buf) {
    return statfs(wrapper_translate_path(path), buf);  /* 64-bit: same as statfs */
}

int fstatfs64_wrapper(int fd, struct statfs* buf) {
    return fstatfs(fd, buf);
}

int statvfs_wrapper(const char* path, struct statvfs* buf) {
    return statvfs(wrapper_translate_path(path), buf);
}

int fstatvfs_wrapper(int fd, struct statvfs* buf) {
    return fstatvfs(fd, buf);
}

int statvfs64_wrapper(const char* path, struct statvfs* buf) {
    return statvfs(wrapper_translate_path(path), buf);
}

int fstatvfs64_wrapper(int fd, struct statvfs* buf) {
    return fstatvfs(fd, buf);
}

/* ============================================================================
 * Path operations
 * ============================================================================ */

static __thread char reverse_translated_path[PATH_MAX];

/* External reference to current ELF path from wrapper_libc.c */
extern char* __progname_full;

char* realpath_wrapper(const char* path, char* resolved_path) {
    /* Intercept /proc/self/exe to return the glibc ELF path instead of app_process64 */
    if (path && __progname_full && 
        (strcmp(path, "/proc/self/exe") == 0 || strcmp(path, "/proc/curproc/exe") == 0)) {
        /* Return the ELF path we're actually running */
        if (resolved_path) {
            strncpy(resolved_path, __progname_full, PATH_MAX - 1);
            resolved_path[PATH_MAX - 1] = '\0';
            return resolved_path;
        } else {
            /* If no buffer provided, allocate one (caller must free) */
            return strdup(__progname_full);
        }
    }
    
    char* result = realpath(wrapper_translate_path(path), resolved_path);
    if (result) {
        /* Reverse translate the result */
        const char* reversed = wrapper_reverse_translate_path(result, reverse_translated_path, sizeof(reverse_translated_path));
        if (reversed != result && resolved_path) {
            /* Copy reversed path to output buffer */
            strncpy(resolved_path, reversed, PATH_MAX - 1);
            resolved_path[PATH_MAX - 1] = '\0';
            return resolved_path;
        } else if (reversed != result) {
            /* No output buffer provided, return our buffer */
            return reverse_translated_path;
        }
    }
    return result;
}

ssize_t readlink_wrapper(const char* path, char* buf, size_t bufsiz) {
    /* Intercept /proc/self/exe to return the glibc ELF path instead of app_process64 */
    if (path && __progname_full && 
        (strcmp(path, "/proc/self/exe") == 0 || strcmp(path, "/proc/curproc/exe") == 0)) {
        size_t len = strlen(__progname_full);
        if (len >= bufsiz) {
            len = bufsiz - 1;
        }
        memcpy(buf, __progname_full, len);
        /* Note: readlink does NOT null-terminate */
        return (ssize_t)len;
    }
    return readlink(wrapper_translate_path(path), buf, bufsiz);
}

ssize_t readlinkat_wrapper(int dirfd, const char* path, char* buf, size_t bufsiz) {
    /* Intercept /proc/self/exe to return the glibc ELF path */
    if (path && __progname_full && dirfd == AT_FDCWD &&
        (strcmp(path, "/proc/self/exe") == 0 || strcmp(path, "/proc/curproc/exe") == 0)) {
        size_t len = strlen(__progname_full);
        if (len >= bufsiz) {
            len = bufsiz - 1;
        }
        memcpy(buf, __progname_full, len);
        return (ssize_t)len;
    }
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    return readlinkat(dirfd, t_path, buf, bufsiz);
}

/* ============================================================================
 * File access wrappers
 * ============================================================================ */

int access_wrapper(const char* path, int mode) {
    return access(wrapper_translate_path(path), mode);
}

int faccessat_wrapper(int dirfd, const char* path, int mode, int flags) {
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    return faccessat(dirfd, t_path, mode, flags);
}

/* ============================================================================
 * File permission wrappers  
 * ============================================================================ */

int chmod_wrapper(const char* path, mode_t mode) {
    return chmod(wrapper_translate_path(path), mode);
}

int fchmod_wrapper(int fd, mode_t mode) {
    return fchmod(fd, mode);
}

int fchmodat_wrapper(int dirfd, const char* path, mode_t mode, int flags) {
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    return fchmodat(dirfd, t_path, mode, flags);
}

int chown_wrapper(const char* path, uid_t owner, gid_t group) {
    return chown(wrapper_translate_path(path), owner, group);
}

int fchown_wrapper(int fd, uid_t owner, gid_t group) {
    return fchown(fd, owner, group);
}

int fchownat_wrapper(int dirfd, const char* path, uid_t owner, gid_t group, int flags) {
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    return fchownat(dirfd, t_path, owner, group, flags);
}

int lchown_wrapper(const char* path, uid_t owner, gid_t group) {
    return lchown(wrapper_translate_path(path), owner, group);
}

/* ============================================================================
 * File open/create wrappers
 * ============================================================================ */

int openat_wrapper(int dirfd, const char* path, int flags, ...) {
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, int);  /* mode_t is promoted to int */
        va_end(ap);
    }
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    return openat(dirfd, t_path, flags, mode);
}

int creat_wrapper(const char* path, mode_t mode) {
    return creat(wrapper_translate_path(path), mode);
}

int creat64_wrapper(const char* path, mode_t mode) {
    return creat(wrapper_translate_path(path), mode);
}

/* ============================================================================
 * Directory operations
 * ============================================================================ */

int mkdir_wrapper(const char* path, mode_t mode) {
    return mkdir(wrapper_translate_path(path), mode);
}

int mkdirat_wrapper(int dirfd, const char* path, mode_t mode) {
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    return mkdirat(dirfd, t_path, mode);
}

int rmdir_wrapper(const char* path) {
    return rmdir(wrapper_translate_path(path));
}

/* ============================================================================
 * Link operations
 * ============================================================================ */

int link_wrapper(const char* oldpath, const char* newpath) {
    return link(wrapper_translate_path(oldpath), wrapper_translate_path(newpath));
}

int linkat_wrapper(int olddirfd, const char* oldpath, int newdirfd, 
                   const char* newpath, int flags) {
    const char* t_old = (olddirfd == AT_FDCWD && oldpath && oldpath[0] == '/') 
                        ? wrapper_translate_path(oldpath) : oldpath;
    const char* t_new = (newdirfd == AT_FDCWD && newpath && newpath[0] == '/') 
                        ? wrapper_translate_path(newpath) : newpath;
    return linkat(olddirfd, t_old, newdirfd, t_new, flags);
}

int symlink_wrapper(const char* target, const char* linkpath) {
    return symlink(target, wrapper_translate_path(linkpath));
}

int symlinkat_wrapper(const char* target, int newdirfd, const char* linkpath) {
    const char* t_path = (newdirfd == AT_FDCWD && linkpath && linkpath[0] == '/') 
                         ? wrapper_translate_path(linkpath) : linkpath;
    return symlinkat(target, newdirfd, t_path);
}

int unlink_wrapper(const char* path) {
    return unlink(wrapper_translate_path(path));
}

int unlinkat_wrapper(int dirfd, const char* path, int flags) {
    const char* t_path = (dirfd == AT_FDCWD && path && path[0] == '/') 
                         ? wrapper_translate_path(path) : path;
    return unlinkat(dirfd, t_path, flags);
}

int rename_wrapper(const char* oldpath, const char* newpath) {
    return rename(wrapper_translate_path(oldpath), wrapper_translate_path(newpath));
}

int renameat_wrapper(int olddirfd, const char* oldpath, int newdirfd, const char* newpath) {
    const char* t_old = (olddirfd == AT_FDCWD && oldpath && oldpath[0] == '/') 
                        ? wrapper_translate_path(oldpath) : oldpath;
    const char* t_new = (newdirfd == AT_FDCWD && newpath && newpath[0] == '/') 
                        ? wrapper_translate_path(newpath) : newpath;
    return renameat(olddirfd, t_old, newdirfd, t_new);
}

/* renameat2 may not be available on older Android, use syscall */
#include <sys/syscall.h>

int renameat2_wrapper(int olddirfd, const char* oldpath, int newdirfd, 
                      const char* newpath, unsigned int flags) {
    const char* t_old = (olddirfd == AT_FDCWD && oldpath && oldpath[0] == '/') 
                        ? wrapper_translate_path(oldpath) : oldpath;
    const char* t_new = (newdirfd == AT_FDCWD && newpath && newpath[0] == '/') 
                        ? wrapper_translate_path(newpath) : newpath;
#ifdef SYS_renameat2
    return syscall(SYS_renameat2, olddirfd, t_old, newdirfd, t_new, flags);
#else
    /* Fallback: if flags is 0, use regular renameat */
    if (flags == 0) {
        return renameat(olddirfd, t_old, newdirfd, t_new);
    }
    errno = ENOSYS;
    return -1;
#endif
}

/* ============================================================================
 * File descriptor operations
 * ============================================================================ */

int dup_wrapper(int oldfd) {
    return dup(oldfd);
}

int dup2_wrapper(int oldfd, int newfd) {
    return dup2(oldfd, newfd);
}

int dup3_wrapper(int oldfd, int newfd, int flags) {
    return dup3(oldfd, newfd, flags);
}

/* fcntl_wrapper 定义在 wrapper_fs.c 中 */

int ftruncate_wrapper(int fd, off_t length) {
    return ftruncate(fd, length);
}

int ftruncate64_wrapper(int fd, off64_t length) {
    return ftruncate64(fd, length);
}

int truncate_wrapper(const char* path, off_t length) {
    return truncate(wrapper_translate_path(path), length);
}

int truncate64_wrapper(const char* path, off64_t length) {
    return truncate64(wrapper_translate_path(path), length);
}

/* ============================================================================
 * Pipe operations
 * ============================================================================ */

int pipe_wrapper(int pipefd[2]) {
    return pipe(pipefd);
}

int pipe2_wrapper(int pipefd[2], int flags) {
    return pipe2(pipefd, flags);
}

/* ============================================================================
 * utimensat/futimens - time modification
 * ============================================================================ */

int utimensat_wrapper(int dirfd, const char* path, 
                      const struct timespec times[2], int flags) {
    return utimensat(dirfd, path, times, flags);
}

int futimens_wrapper(int fd, const struct timespec times[2]) {
    return futimens(fd, times);
}

/* ============================================================================
 * mkstemp family
 * NOTE: mkstemp modifies the template in place, so we need special handling
 * for path translation.
 * ============================================================================ */

/* Helper to ensure parent directory exists */
static void ensure_parent_dir(const char* path) {
    char dir[PATH_MAX];
    strncpy(dir, path, sizeof(dir) - 1);
    dir[sizeof(dir) - 1] = '\0';
    
    /* Find last slash */
    char* last_slash = strrchr(dir, '/');
    if (last_slash && last_slash != dir) {
        *last_slash = '\0';
        /* Create directory with rwx for user */
        mkdir(dir, 0755);
    }
}

int mkstemp_wrapper(char* template) {
    /* Check if path needs translation */
    if (template && template[0] == '/' && wrapper_should_translate_path(template)) {
        /* Translate to fake rootfs */
        char translated[PATH_MAX];
        const char* glibc_root = glibc_bridge_get_glibc_root();
        if (glibc_root) {
            snprintf(translated, sizeof(translated), "%s%s", glibc_root, template);
            
            /* Ensure parent directory exists (e.g., glibc-root/tmp) */
            ensure_parent_dir(translated);
            
            int fd = mkstemp(translated);
            if (fd >= 0) {
                /* Copy translated path back (minus glibc_root prefix) to keep XXXXXX suffix */
                size_t root_len = strlen(glibc_root);
                strncpy(template, translated + root_len, strlen(template));
            }
            return fd;
        }
    }
    return mkstemp(template);
}

int mkostemp_wrapper(char* template, int flags) {
    if (template && template[0] == '/' && wrapper_should_translate_path(template)) {
        char translated[PATH_MAX];
        const char* glibc_root = glibc_bridge_get_glibc_root();
        if (glibc_root) {
            snprintf(translated, sizeof(translated), "%s%s", glibc_root, template);
            int fd = mkostemp(translated, flags);
            if (fd >= 0) {
                size_t root_len = strlen(glibc_root);
                strncpy(template, translated + root_len, strlen(template));
            }
            return fd;
        }
    }
    return mkostemp(template, flags);
}

int mkstemp64_wrapper(char* template) {
    return mkstemp_wrapper(template);  /* Use wrapper with path translation */
}

char* mkdtemp_wrapper(char* template) {
    if (template && template[0] == '/' && wrapper_should_translate_path(template)) {
        char translated[PATH_MAX];
        const char* glibc_root = glibc_bridge_get_glibc_root();
        if (glibc_root) {
            snprintf(translated, sizeof(translated), "%s%s", glibc_root, template);
            char* result = mkdtemp(translated);
            if (result) {
                size_t root_len = strlen(glibc_root);
                strncpy(template, translated + root_len, strlen(template));
                return template;  /* Return original buffer with updated path */
            }
            return NULL;
        }
    }
    return mkdtemp(template);
}

/* ============================================================================
 * Directory reading wrappers - opendir, readdir, etc.
 * ============================================================================ */

DIR* opendir_wrapper(const char* name) {
    const char* translated = wrapper_translate_path(name);
    DIR* result = opendir(translated);
    LOG_DEBUG("opendir(%s) -> translated=%s -> DIR*=%p", 
              name ? name : "NULL", translated ? translated : "NULL", (void*)result);
    if (!result) {
        LOG_WARN("opendir failed for %s: errno=%d (%s)",
                  translated, errno, strerror(errno));
    }
    return result;
}

DIR* fdopendir_wrapper(int fd) {
    return fdopendir(fd);
}

int closedir_wrapper(DIR* dirp) {
    return closedir(dirp);
}

struct dirent* readdir_wrapper(DIR* dirp) {
    if (!dirp) {
        LOG_ERROR("readdir called with NULL DIR* - returning NULL instead of crashing");
        errno = EBADF;
        return NULL;
    }
    return readdir(dirp);
}

int readdir_r_wrapper(DIR* dirp, struct dirent* entry, struct dirent** result) {
    return readdir_r(dirp, entry, result);
}

void rewinddir_wrapper(DIR* dirp) {
    if (!dirp) {
        LOG_ERROR("rewinddir called with NULL DIR* - ignoring");
        return;
    }
    rewinddir(dirp);
}

void seekdir_wrapper(DIR* dirp, long loc) {
    if (!dirp) {
        LOG_ERROR("seekdir called with NULL DIR* - ignoring");
        return;
    }
    seekdir(dirp, loc);
}

long telldir_wrapper(DIR* dirp) {
    if (!dirp) {
        LOG_ERROR("telldir called with NULL DIR* - returning -1");
        errno = EBADF;
        return -1;
    }
    return telldir(dirp);
}

int dirfd_wrapper(DIR* dirp) {
    if (!dirp) {
        LOG_ERROR("dirfd called with NULL DIR* - returning -1");
        errno = EBADF;
        return -1;
    }
    return dirfd(dirp);
}

int scandir_wrapper(const char* dirp, struct dirent*** namelist,
                    int (*filter)(const struct dirent*),
                    int (*compar)(const struct dirent**, const struct dirent**)) {
    return scandir(wrapper_translate_path(dirp), namelist, filter, compar);
}

/* ============================================================================
 * open/fopen wrappers with path translation  
 * ============================================================================ */

/* External: internal fopen functions with FILE mapping */
extern void* glibc_bridge_fopen_internal(const char* path, const char* mode);
extern void* glibc_bridge_fopen64_internal(const char* path, const char* mode);
extern void* glibc_bridge_freopen_internal(const char* path, const char* mode, void* stream);

/* External: /proc/self/maps virtualization (fd-based) */
extern int glibc_bridge_is_proc_maps(const char* path);
extern int glibc_bridge_open_proc_maps_fd(void);

int open_wrapper(const char* pathname, int flags, ...) {
    WRAPPER_BEGIN("open");
    
    /* Virtualize /proc/self/maps for read access */
    if (glibc_bridge_is_proc_maps(pathname) && (flags & O_ACCMODE) == O_RDONLY) {
        int ret = glibc_bridge_open_proc_maps_fd();
        WRAPPER_RETURN(ret);
    }
    
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, int);
        va_end(ap);
    }
    int ret = open(wrapper_translate_path(pathname), flags, mode);
    WRAPPER_RETURN(ret);
}

int open64_wrapper(const char* pathname, int flags, ...) {
    WRAPPER_BEGIN("open64");
    
    /* Virtualize /proc/self/maps for read access */
    if (glibc_bridge_is_proc_maps(pathname) && (flags & O_ACCMODE) == O_RDONLY) {
        int ret = glibc_bridge_open_proc_maps_fd();
        WRAPPER_RETURN(ret);
    }
    
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, int);
        va_end(ap);
    }
    int ret = open64(wrapper_translate_path(pathname), flags, mode);
    WRAPPER_RETURN(ret);
}

extern FILE* glibc_bridge_fopen_proc_maps(void);

FILE* fopen_wrapper(const char* pathname, const char* mode) {
    /* Virtualize /proc/self/maps to include glibc-bridge-loaded libraries */
    if (glibc_bridge_is_proc_maps(pathname) && mode && mode[0] == 'r') {
        return glibc_bridge_fopen_proc_maps();
    }
    return (FILE*)glibc_bridge_fopen_internal(wrapper_translate_path(pathname), mode);
}

FILE* fopen64_wrapper(const char* pathname, const char* mode) {
    /* Virtualize /proc/self/maps to include glibc-bridge-loaded libraries */
    if (glibc_bridge_is_proc_maps(pathname) && mode && mode[0] == 'r') {
        return glibc_bridge_fopen_proc_maps();
    }
    return (FILE*)glibc_bridge_fopen64_internal(wrapper_translate_path(pathname), mode);
}

FILE* freopen_wrapper(const char* pathname, const char* mode, FILE* stream) {
    return (FILE*)glibc_bridge_freopen_internal(wrapper_translate_path(pathname), mode, stream);
}

FILE* freopen64_wrapper(const char* pathname, const char* mode, FILE* stream) {
    return (FILE*)glibc_bridge_freopen_internal(wrapper_translate_path(pathname), mode, stream);
}

/* ============================================================================
 * chdir/getcwd with path translation
 * ============================================================================ */

int chdir_wrapper(const char* path) {
    return chdir(wrapper_translate_path(path));
}

int fchdir_wrapper(int fd) {
    return fchdir(fd);
}

