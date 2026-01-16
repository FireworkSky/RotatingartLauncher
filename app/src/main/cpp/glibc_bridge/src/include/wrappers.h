/*
 * glibc-bridge 包装函数头文件
 * 
 * 声明所有 glibc 到 bionic 的包装函数
 * 这些函数桥接 glibc ABI 和 bionic 之间的差异
 */

#ifndef GLIBC_BRIDGE_WRAPPERS_H
#define GLIBC_BRIDGE_WRAPPERS_H

#include <stdio.h>
#include <stdarg.h>
#include <wchar.h>
#include <wctype.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/statvfs.h>
#include <dirent.h>
#include <time.h>
#include <sys/select.h>
#include <pthread.h>
#include <setjmp.h>
#include <sched.h>
#include <search.h>
#include <dlfcn.h>
#include <signal.h>

/* mqueue/aio/crypt 类型前向声明 (由 wrapper_libc.c 定义完整结构) */
typedef int mqd_t;
struct mq_attr;
struct aiocb;
struct crypt_data;

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * 包装类型
 * ============================================================================ */

/* 符号包装条目 */
typedef struct {
    const char* name;       /* glibc 符号名 */
    void* wrapper;          /* 我们的包装函数（NULL = 直接使用 bionic）*/
} symbol_wrapper_t;

/* 本地化类型 - Android NDK API 28+ 支持 locale_t */
#include <locale.h>
#ifdef __ANDROID__
/* Android NDK 的 locale_t 定义在 xlocale.h 中 */
#include <xlocale.h>
#endif

/* ============================================================================
 * ctype 包装 (compat/tls.c)
 * ============================================================================ */

void* __ctype_b_loc_wrapper(void);
void* __ctype_tolower_loc_wrapper(void);
void* __ctype_toupper_loc_wrapper(void);

/* ============================================================================
 * errno 包装 (compat/tls.c)
 * ============================================================================ */

int* __errno_location_wrapper(void);
int* __h_errno_location_wrapper(void);

/* ============================================================================
 * 基本 libc 包装 (wrapper_libc.c)
 * ============================================================================ */

char* secure_getenv_wrapper(const char* name);
int __register_atfork_wrapper(void (*prepare)(void), void (*parent)(void), 
                               void (*child)(void), void* dso_handle);
void error_wrapper(int status, int errnum, const char* format, ...);

/* __libc_start_main 包装 - 程序启动关键 */
int __libc_start_main_wrapper(
    int (*main_func)(int, char**, char**),
    int argc,
    char** argv,
    int (*init)(int, char**, char**),
    void (*fini)(void),
    void (*rtld_fini)(void),
    void* stack_end);

/* 断言包装 */
void assert_fail_wrapper(const char* assertion, const char* file, 
                         unsigned int line, const char* function);

/* pthread 包装 */
int pthread_create_wrapper(pthread_t* thread, const pthread_attr_t* attr,
                           void* (*start_routine)(void*), void* arg);

/* 时间函数 */
void* localtime_wrapper(const void* timer);
void* localtime_r_wrapper(const void* timer, void* result);
void* gmtime_wrapper(const void* timer);
void* gmtime_r_wrapper(const void* timer, void* result);
size_t strftime_wrapper(char* s, size_t max, const char* format, const void* tm);

/* clock_* 函数 */
int clock_gettime_wrapper(int clk_id, struct timespec* tp);
int clock_getres_wrapper(int clk_id, struct timespec* res);
int clock_settime_wrapper(int clk_id, const struct timespec* tp);
int clock_nanosleep_wrapper(int clock_id, int flags, 
                            const struct timespec* request, struct timespec* remain);

/* nanosleep */
int nanosleep_wrapper(const struct timespec* req, struct timespec* rem);

/* 退出函数 */
void exit_wrapper(int status);
void _exit_wrapper(int status);
void _Exit_wrapper(int status);

/* 内存函数 */
void* malloc_wrapper(size_t size);
void free_wrapper(void* ptr);
void* calloc_wrapper(size_t nmemb, size_t size);
void* realloc_wrapper(void* ptr, size_t size);
void* memalign_wrapper(size_t alignment, size_t size);
int posix_memalign_wrapper(void** memptr, size_t alignment, size_t size);
void* aligned_alloc_wrapper(size_t alignment, size_t size);
void* valloc_wrapper(size_t size);
void* pvalloc_wrapper(size_t size);

int raise_wrapper(int sig);
int kill_wrapper(pid_t pid, int sig);

/* 文件描述符函数 */
int fcntl_wrapper(int fd, int cmd, ...);
int ioctl_wrapper(int fd, unsigned long request, ...);

/* 系统调用 */
long syscall_wrapper(long number, ...);

/* 环境 */
char** environ_wrapper(void);

/* 程序名 */
char* program_invocation_name_wrapper(void);
char* program_invocation_short_name_wrapper(void);

/* 动态链接 */
void* dlopen_wrapper(const char* filename, int flags);
void* dlsym_wrapper(void* handle, const char* symbol);
int dlclose_wrapper(void* handle);
char* dlerror_wrapper(void);
int dladdr_wrapper(const void* addr, Dl_info* info);

/* ============================================================================
 * stat 包装 (wrapper_stat.c)
 * ============================================================================ */

int stat_wrapper(const char* path, void* buf);
int stat64_wrapper(const char* path, void* buf);
int lstat_wrapper(const char* path, void* buf);
int lstat64_wrapper(const char* path, void* buf);
int fstat_wrapper(int fd, void* buf);
int fstat64_wrapper(int fd, void* buf);
int fstatat_wrapper(int dirfd, const char* path, void* buf, int flags);
int fstatat64_wrapper(int dirfd, const char* path, void* buf, int flags);

int statx_wrapper(int dirfd, const char *pathname, int flags, unsigned int mask, void *statxbuf);

int open_wrapper(const char* path, int flags, ...);
int open64_wrapper(const char* path, int flags, ...);
int openat_wrapper(int dirfd, const char* path, int flags, ...);
int openat64_wrapper(int dirfd, const char* path, int flags, ...);
int creat_wrapper(const char* path, mode_t mode);
int creat64_wrapper(const char* path, mode_t mode);

FILE* fopen_wrapper(const char* pathname, const char* mode);
FILE* fopen64_wrapper(const char* pathname, const char* mode);
FILE* freopen_wrapper(const char* pathname, const char* mode, FILE* stream);
FILE* freopen64_wrapper(const char* pathname, const char* mode, FILE* stream);

int access_wrapper(const char* path, int mode);
int faccessat_wrapper(int dirfd, const char* path, int mode, int flags);

ssize_t readlink_wrapper(const char* path, char* buf, size_t bufsiz);
ssize_t readlinkat_wrapper(int dirfd, const char* path, char* buf, size_t bufsiz);

char* realpath_wrapper(const char* path, char* resolved_path);

int chdir_wrapper(const char* path);
int mkdir_wrapper(const char* path, mode_t mode);
int mkdirat_wrapper(int dirfd, const char* path, mode_t mode);
int rmdir_wrapper(const char* path);
int unlink_wrapper(const char* path);
int unlinkat_wrapper(int dirfd, const char* path, int flags);

int rename_wrapper(const char* oldpath, const char* newpath);
int renameat_wrapper(int olddirfd, const char* oldpath, int newdirfd, const char* newpath);

int symlink_wrapper(const char* target, const char* linkpath);
int symlinkat_wrapper(const char* target, int newdirfd, const char* linkpath);
int link_wrapper(const char* oldpath, const char* newpath);
int linkat_wrapper(int olddirfd, const char* oldpath, int newdirfd, const char* newpath, int flags);

int chmod_wrapper(const char* path, mode_t mode);
int fchmod_wrapper(int fd, mode_t mode);
int fchmodat_wrapper(int dirfd, const char* path, mode_t mode, int flags);

int chown_wrapper(const char* path, uid_t owner, gid_t group);
int fchown_wrapper(int fd, uid_t owner, gid_t group);
int fchownat_wrapper(int dirfd, const char* path, uid_t owner, gid_t group, int flags);
int lchown_wrapper(const char* path, uid_t owner, gid_t group);

int truncate_wrapper(const char* path, off_t length);
int truncate64_wrapper(const char* path, off64_t length);
int ftruncate_wrapper(int fd, off_t length);
int ftruncate64_wrapper(int fd, off64_t length);

DIR* opendir_wrapper(const char* name);
DIR* fdopendir_wrapper(int fd);
struct dirent* readdir_wrapper(DIR* dirp);
struct dirent* readdir64_wrapper(DIR* dirp);
int closedir_wrapper(DIR* dirp);
void rewinddir_wrapper(DIR* dirp);
void seekdir_wrapper(DIR* dirp, long loc);
long telldir_wrapper(DIR* dirp);
int dirfd_wrapper(DIR* dirp);

int execve_wrapper(const char* path, char* const argv[], char* const envp[]);
int execv_wrapper(const char* path, char* const argv[]);
int execvp_wrapper(const char* file, char* const argv[]);
int execvpe_wrapper(const char* file, char* const argv[], char* const envp[]);

int utimes_wrapper(const char* filename, const struct timeval times[2]);
int futimes_wrapper(int fd, const struct timeval times[2]);
int lutimes_wrapper(const char* filename, const struct timeval times[2]);
int utimensat_wrapper(int dirfd, const char* pathname, const struct timespec times[2], int flags);
int futimens_wrapper(int fd, const struct timespec times[2]);

int utime_wrapper(const char* filename, const void* times);

char* getcwd_wrapper(char* buf, size_t size);
char* getwd_wrapper(char* buf);
char* get_current_dir_name_wrapper(void);

/* ============================================================================
 * locale 包装 (wrapper_locale.c)
 * ============================================================================ */

locale_t newlocale_wrapper(int category_mask, const char* locale, locale_t base);
void freelocale_wrapper(locale_t locobj);
locale_t uselocale_wrapper(locale_t locobj);
locale_t duplocale_wrapper(locale_t locobj);
char* setlocale_wrapper(int category, const char* locale);

int strcoll_l_wrapper(const char* s1, const char* s2, locale_t loc);
size_t strxfrm_l_wrapper(char* dest, const char* src, size_t n, locale_t loc);
int wcscoll_l_wrapper(const wchar_t* ws1, const wchar_t* ws2, locale_t loc);
size_t wcsxfrm_l_wrapper(wchar_t* dest, const wchar_t* src, size_t n, locale_t loc);

int isalnum_l_wrapper(int c, locale_t loc);
int isalpha_l_wrapper(int c, locale_t loc);
int isblank_l_wrapper(int c, locale_t loc);
int iscntrl_l_wrapper(int c, locale_t loc);
int isdigit_l_wrapper(int c, locale_t loc);
int isgraph_l_wrapper(int c, locale_t loc);
int islower_l_wrapper(int c, locale_t loc);
int isprint_l_wrapper(int c, locale_t loc);
int ispunct_l_wrapper(int c, locale_t loc);
int isspace_l_wrapper(int c, locale_t loc);
int isupper_l_wrapper(int c, locale_t loc);
int isxdigit_l_wrapper(int c, locale_t loc);
int tolower_l_wrapper(int c, locale_t loc);
int toupper_l_wrapper(int c, locale_t loc);

/* ============================================================================
 * FORTIFY 包装 (wrapper_fortify.c)
 * ============================================================================ */

void* __memcpy_chk_wrapper(void* dest, const void* src, size_t len, size_t destlen);
void* __memmove_chk_wrapper(void* dest, const void* src, size_t len, size_t destlen);
void* __memset_chk_wrapper(void* dest, int c, size_t len, size_t destlen);
char* __strcpy_chk_wrapper(char* dest, const char* src, size_t destlen);
char* __strncpy_chk_wrapper(char* dest, const char* src, size_t len, size_t destlen);
char* __strcat_chk_wrapper(char* dest, const char* src, size_t destlen);
char* __strncat_chk_wrapper(char* dest, const char* src, size_t len, size_t destlen);
int __sprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* format, ...);
int __snprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* format, ...);
int __vsprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* format, va_list ap);
int __vsnprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* format, va_list ap);
int __fprintf_chk_wrapper(void* stream, int flag, const char* format, ...);
int __printf_chk_wrapper(int flag, const char* format, ...);

/* ============================================================================
 * stdio 包装 (compat/stdio.c)
 * ============================================================================ */

void* glibc_bridge_get_stdin(void);
void* glibc_bridge_get_stdout(void);
void* glibc_bridge_get_stderr(void);
void* glibc_bridge_get_glibc_stdin_struct(void);
void* glibc_bridge_get_glibc_stdout_struct(void);
void* glibc_bridge_get_glibc_stderr_struct(void);

size_t fread_wrapper(void* ptr, size_t size, size_t count, void* stream);
size_t fwrite_wrapper(const void* ptr, size_t size, size_t count, void* stream);
char* fgets_wrapper(char* str, int n, void* stream);
int fgetc_wrapper(void* stream);
int getc_wrapper(void* stream);
int ungetc_wrapper(int c, void* stream);
int fputs_wrapper(const char* str, void* stream);
int puts_wrapper(const char* str);
int fputc_wrapper(int c, void* stream);
int putc_wrapper(int c, void* stream);
int fprintf_wrapper(void* stream, const char* format, ...);
int vfprintf_wrapper(void* stream, const char* format, va_list args);
int fscanf_wrapper(void* stream, const char* format, ...);
int vfscanf_wrapper(void* stream, const char* format, va_list args);
int fseek_wrapper(void* stream, long offset, int whence);
int fseeko_wrapper(void* stream, off_t offset, int whence);
int fseeko64_wrapper(void* stream, off64_t offset, int whence);
long ftell_wrapper(void* stream);
off_t ftello_wrapper(void* stream);
off64_t ftello64_wrapper(void* stream);
void rewind_wrapper(void* stream);
int fgetpos_wrapper(void* stream, void* pos);
int fsetpos_wrapper(void* stream, const void* pos);
int fflush_wrapper(void* stream);
int feof_wrapper(void* stream);
int ferror_wrapper(void* stream);
void clearerr_wrapper(void* stream);
int fileno_wrapper(void* stream);
int setvbuf_wrapper(void* stream, char* buf, int mode, size_t size);
void setbuf_wrapper(void* stream, char* buf);
void setbuffer_wrapper(void* stream, char* buf, size_t size);
void setlinebuf_wrapper(void* stream);
void flockfile_wrapper(void* stream);
void funlockfile_wrapper(void* stream);
int ftrylockfile_wrapper(void* stream);
int fclose_wrapper(void* stream);
void* tmpfile_wrapper(void);
void* tmpfile64_wrapper(void);

int __uflow_wrapper(void* stream);
int __overflow_wrapper(void* stream, int c);

/* ============================================================================
 * gettext 包装 (wrapper_gettext.c)
 * ============================================================================ */

char* gettext_wrapper(const char* msgid);
char* dgettext_wrapper(const char* domainname, const char* msgid);
char* dcgettext_wrapper(const char* domainname, const char* msgid, int category);
char* ngettext_wrapper(const char* msgid1, const char* msgid2, unsigned long n);
char* dngettext_wrapper(const char* domainname, const char* msgid1, const char* msgid2, unsigned long n);
char* dcngettext_wrapper(const char* domainname, const char* msgid1, const char* msgid2, unsigned long n, int category);
char* textdomain_wrapper(const char* domainname);
char* bindtextdomain_wrapper(const char* domainname, const char* dirname);
char* bind_textdomain_codeset_wrapper(const char* domainname, const char* codeset);

/* ============================================================================
 * sysinfo 包装 (wrapper_sysinfo.c)
 * ============================================================================ */

int uname_wrapper(void* buf);
int sysinfo_wrapper(void* info);
long sysconf_wrapper(int name);
size_t confstr_wrapper(int name, char* buf, size_t len);
int getrlimit_wrapper(int resource, void* rlim);
int setrlimit_wrapper(int resource, const void* rlim);
int prlimit_wrapper(int pid, int resource, const void* new_limit, void* old_limit);
int getrusage_wrapper(int who, void* usage);

long get_nprocs_wrapper(void);
long get_nprocs_conf_wrapper(void);
long get_phys_pages_wrapper(void);
long get_avphys_pages_wrapper(void);

pid_t getpid_wrapper(void);
pid_t getppid_wrapper(void);
uid_t getuid_wrapper(void);
uid_t geteuid_wrapper(void);
gid_t getgid_wrapper(void);
gid_t getegid_wrapper(void);
pid_t getsid_wrapper(pid_t pid);

int setuid_wrapper(uid_t uid);
int setgid_wrapper(gid_t gid);
int seteuid_wrapper(uid_t euid);
int setegid_wrapper(gid_t egid);
int setreuid_wrapper(uid_t ruid, uid_t euid);
int setregid_wrapper(gid_t rgid, gid_t egid);
int setresuid_wrapper(uid_t ruid, uid_t euid, uid_t suid);
int setresgid_wrapper(gid_t rgid, gid_t egid, gid_t sgid);

/* ============================================================================
 * C++ 支持 (wrapper_cxx.c)
 * ============================================================================ */

void* __cxa_allocate_exception_wrapper(size_t thrown_size);
void __cxa_free_exception_wrapper(void* thrown_exception);
void __cxa_throw_wrapper(void* thrown_exception, void* tinfo, void (*dest)(void*));
void* __cxa_begin_catch_wrapper(void* exc);
void __cxa_end_catch_wrapper(void);
void __cxa_rethrow_wrapper(void);

void __cxa_pure_virtual_wrapper(void);
int __cxa_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle);
void __cxa_finalize_wrapper(void* d);
int __cxa_guard_acquire_wrapper(void* guard);
void __cxa_guard_release_wrapper(void* guard);
void __cxa_guard_abort_wrapper(void* guard);
char* __cxa_demangle_wrapper(const char* mangled, char* buf, size_t* len, int* status);
int __cxa_thread_atexit_wrapper(void (*func)(void*), void* arg, void* dso_handle);
int __cxa_thread_atexit_impl_wrapper(void (*func)(void*), void* arg, void* dso_handle);

/* ============================================================================
 * TM/profiling stubs
 * ============================================================================ */

void __gmon_start___stub(void);
void _ITM_deregisterTMCloneTable_stub(void);
void _ITM_registerTMCloneTable_stub(void);

/* ============================================================================
 * LTTng stubs (用于 .NET CoreCLR 跟踪)
 * ============================================================================ */

int lttng_probe_register_stub(void* probe);
void lttng_probe_unregister_stub(void* probe);

void* __gxx_personality_v0_wrapper(int version, int actions, uint64_t exception_class,
                                    void* exception_object, void* context);

void* operator_new_wrapper(size_t size);
void* operator_new_nothrow_wrapper(size_t size, const void* nothrow);
void* operator_new_array_wrapper(size_t size);
void* operator_new_array_nothrow_wrapper(size_t size, const void* nothrow);
void operator_delete_wrapper(void* ptr);
void operator_delete_nothrow_wrapper(void* ptr, const void* nothrow);
void operator_delete_array_wrapper(void* ptr);
void operator_delete_array_nothrow_wrapper(void* ptr, const void* nothrow);
void operator_delete_sized_wrapper(void* ptr, size_t size);
void operator_delete_array_sized_wrapper(void* ptr, size_t size);

/* ============================================================================
 * ucontext 包装 (wrapper_ucontext.c)
 * ============================================================================ */

int getcontext_wrapper(void* ucp);
int setcontext_wrapper(const void* ucp);
void makecontext_wrapper(void* ucp, void (*func)(void), int argc, ...);
int swapcontext_wrapper(void* oucp, const void* ucp);

/* ============================================================================
 * 符号表访问
 * ============================================================================ */

/* 获取符号包装表 */
const symbol_wrapper_t* glibc_bridge_get_symbol_wrappers(void);
const symbol_wrapper_t* glibc_bridge_get_symbol_table(void);
int glibc_bridge_get_symbol_wrapper_count(void);

/* 按名称查找包装函数 */
void* glibc_bridge_find_wrapper(const char* name);

/* ============================================================================
 * 其他兼容函数
 * ============================================================================ */

/* 字符串函数 */
int strverscmp_wrapper(const char* s1, const char* s2);
char* __xpg_basename_wrapper(char* path);
void* rawmemchr_wrapper(const void* s, int c);
char* strdup_wrapper(const char* s);



/* wordexp */
int wordexp_wrapper(const char* words, void* pwordexp, int flags);
void wordfree_wrapper(void* pwordexp);

/* 标准输入输出 */
int printf_wrapper(const char* format, ...);
int vprintf_wrapper(const char* format, va_list ap);
int snprintf_wrapper(char* str, size_t size, const char* format, ...);

/* C2x _Float64 支持 */
double strtof64_wrapper(const char* nptr, char** endptr);
int strfromf64_wrapper(char* str, size_t n, const char* format, double fp);

/* 进程控制 */
int atexit_wrapper(void (*func)(void));
void abort_wrapper(void);

/* 排序/搜索 */
void qsort_wrapper(void* base, size_t nmemb, size_t size, int (*compar)(const void*, const void*));
void* bsearch_wrapper(const void* key, const void* base, size_t nmemb, size_t size, int (*compar)(const void*, const void*));
void* lfind_wrapper(const void* key, const void* base, size_t* nmemb, size_t size, int (*compar)(const void*, const void*));
void* lsearch_wrapper(const void* key, void* base, size_t* nmemb, size_t size, int (*compar)(const void*, const void*));
void* tsearch_wrapper(const void* key, void** rootp, int (*compar)(const void*, const void*));
void* tfind_wrapper(const void* key, void* const* rootp, int (*compar)(const void*, const void*));
void* tdelete_wrapper(const void* key, void** rootp, int (*compar)(const void*, const void*));
void twalk_wrapper(const void* root, void (*action)(const void*, VISIT, int));
void tdestroy_wrapper(void* root, void (*free_node)(void*));

/* 文件系统内部函数 */
int __xmknod_wrapper(int ver, const char* path, mode_t mode, dev_t* dev);

/* statfs/statvfs */
int statfs_wrapper(const char* path, struct statfs* buf);
int fstatfs_wrapper(int fd, struct statfs* buf);
int statfs64_wrapper(const char* path, struct statfs* buf);
int fstatfs64_wrapper(int fd, struct statfs* buf);
int statvfs_wrapper(const char* path, struct statvfs* buf);
int fstatvfs_wrapper(int fd, struct statvfs* buf);
int statvfs64_wrapper(const char* path, struct statvfs* buf);
int fstatvfs64_wrapper(int fd, struct statvfs* buf);

/* getdelim/getline */
ssize_t getdelim_wrapper(char** lineptr, size_t* n, int delim, FILE* stream);
ssize_t getline_wrapper(char** lineptr, size_t* n, FILE* stream);
int __fsetlocking_wrapper(FILE* fp, int type);

/* popen/pclose */
FILE* popen_wrapper(const char* command, const char* type);
int pclose_wrapper(FILE* stream);

/* 信号处理 */
int sigprocmask_wrapper(int how, const sigset_t* set, sigset_t* oldset);
int sigaction_wrapper(int signum, const struct sigaction* act, struct sigaction* oldact);
int sigemptyset_wrapper(sigset_t* set);
int sigfillset_wrapper(sigset_t* set);
int sigaddset_wrapper(sigset_t* set, int signum);
int sigdelset_wrapper(sigset_t* set, int signum);
int sigismember_wrapper(const sigset_t* set, int signum);
int sigisemptyset_wrapper(const sigset_t* set);
int pidfd_send_signal_wrapper(int pidfd, int sig, siginfo_t* info, unsigned int flags);
int name_to_handle_at_wrapper(int dirfd, const char* pathname, void* handle, int* mount_id, int flags);

/* scanf 系列 */
int __isoc99_sscanf_wrapper(const char* str, const char* format, uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3);
int __isoc99_scanf_wrapper(const char* format, uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4);
int __isoc99_fscanf_wrapper(FILE* stream, const char* format, uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3);
int __isoc99_vsscanf_wrapper(const char* str, const char* format, va_list ap);
int __isoc99_vscanf_wrapper(const char* format, va_list ap);
int __isoc99_vfscanf_wrapper(FILE* stream, const char* format, va_list ap);

/* select/pselect */
int select_wrapper(int nfds, fd_set* readfds, fd_set* writefds, fd_set* exceptfds, struct timeval* timeout);
int pselect_wrapper(int nfds, fd_set* readfds, fd_set* writefds, fd_set* exceptfds, const struct timespec* timeout, const sigset_t* sigmask);

/* pthread_key */
int pthread_key_create_wrapper(pthread_key_t* key, void (*destructor)(void*));

/* crypt */
char* crypt_wrapper(const char* key, const char* salt);
char* crypt_r_wrapper(const char* key, const char* salt, struct crypt_data* data);

/* POSIX 消息队列 */
mqd_t mq_open_wrapper(const char* name, int oflag, ...);
int mq_close_wrapper(mqd_t mqdes);
int mq_unlink_wrapper(const char* name);
int mq_send_wrapper(mqd_t mqdes, const char* msg_ptr, size_t msg_len, unsigned int msg_prio);
ssize_t mq_receive_wrapper(mqd_t mqdes, char* msg_ptr, size_t msg_len, unsigned int* msg_prio);
int mq_getattr_wrapper(mqd_t mqdes, struct mq_attr* attr);
int mq_setattr_wrapper(mqd_t mqdes, const struct mq_attr* newattr, struct mq_attr* oldattr);

/* POSIX AIO (异步IO) */
int aio_read_wrapper(struct aiocb* aiocbp);
int aio_write_wrapper(struct aiocb* aiocbp);
int aio_error_wrapper(const struct aiocb* aiocbp);
ssize_t aio_return_wrapper(struct aiocb* aiocbp);
int aio_suspend_wrapper(const struct aiocb* const aiocb_list[], int nitems, const struct timespec* timeout);
int aio_cancel_wrapper(int fd, struct aiocb* aiocbp);
int aio_fsync_wrapper(int op, struct aiocb* aiocbp);
int lio_listio_wrapper(int mode, struct aiocb* const aiocb_list[], int nitems, struct sigevent* sevp);

/* System V IPC */
int shmget_wrapper(key_t key, size_t size, int shmflg);
void* shmat_wrapper(int shmid, const void* shmaddr, int shmflg);
int shmdt_wrapper(const void* shmaddr);
int shmctl_wrapper(int shmid, int cmd, void* buf);
int semget_wrapper(key_t key, int nsems, int semflg);
int semop_wrapper(int semid, void* sops, size_t nsops);
int semctl_wrapper(int semid, int semnum, int cmd, ...);
int msgget_wrapper(key_t key, int msgflg);
int msgsnd_wrapper(int msqid, const void* msgp, size_t msgsz, int msgflg);
ssize_t msgrcv_wrapper(int msqid, void* msgp, size_t msgsz, long msgtyp, int msgflg);
int msgctl_wrapper(int msqid, int cmd, void* buf);

/* mkfifo/mknod */
int mkfifo_wrapper(const char* pathname, mode_t mode);
int mknod_wrapper(const char* pathname, mode_t mode, dev_t dev);
int mknodat_wrapper(int dirfd, const char* pathname, mode_t mode, dev_t dev);

/* iconv (proot_bypass.c) */
void* iconv_open_wrapper(const char* tocode, const char* fromcode);
int iconv_close_wrapper(void* cd);
size_t iconv_wrapper(void* cd, char** inbuf, size_t* inbytesleft, char** outbuf, size_t* outbytesleft);

/* socket (proot_bypass.c) */
int setsockopt_wrapper(int sockfd, int level, int optname, const void* optval, unsigned int optlen);
int getsockopt_wrapper(int sockfd, int level, int optname, void* optval, unsigned int* optlen);

/* getopt (proot_bypass.c) */
int getopt_wrapper(int argc, char* const argv[], const char* optstring);
int getopt_long_wrapper(int argc, char* const argv[], const char* optstring, 
                        const void* longopts, int* longindex);
char* optarg_wrapper(void);
int* optind_wrapper(void);
int* opterr_wrapper(void);
int* optopt_wrapper(void);

/* ============================================================================
 * 复数数学函数 (wrapper_math_ext.c)
 * ============================================================================ */

double cabs_wrapper(double real, double imag);
double carg_wrapper(double real, double imag);
float cabsf_wrapper(float real, float imag);
float cargf_wrapper(float real, float imag);
double creal_wrapper(double real, double imag);
double cimag_wrapper(double real, double imag);

/* ============================================================================
 * mlock 系列 (wrapper_mlock.c)
 * ============================================================================ */

int mlock_wrapper(const void* addr, size_t len);
int munlock_wrapper(const void* addr, size_t len);
int mlockall_wrapper(int flags);
int munlockall_wrapper(void);
int madvise_wrapper(void* addr, size_t length, int advice);

/* ============================================================================
 * 更多标准IO函数
 * ============================================================================ */

int vsnprintf_wrapper(char* str, size_t size, const char* format, va_list ap);
char* strerror_wrapper(int errnum);
int isgraph_wrapper(int c);

/* ============================================================================
 * pthread 取消函数
 * ============================================================================ */

int pthread_cancel_wrapper(pthread_t thread);
int pthread_setcancelstate_wrapper(int state, int* oldstate);
int pthread_setcanceltype_wrapper(int type, int* oldtype);
int pthread_kill_wrapper(pthread_t thread, int sig);
int pthread_sigmask_wrapper(int how, const sigset_t* set, sigset_t* oldset);
void pthread_testcancel_wrapper(void);

/* ============================================================================
 * 调度器函数
 * ============================================================================ */

int sched_getaffinity_wrapper(pid_t pid, size_t cpusetsize, void* mask);
int sched_setaffinity_wrapper(pid_t pid, size_t cpusetsize, const void* mask);

/* ============================================================================
 * xstat 系列 (wrapper_stat.c)
 * ============================================================================ */

int __fxstat64_wrapper(int ver, int fd, void* buf);
int __xstat64_wrapper(int ver, const char* path, void* buf);
int __lxstat64_wrapper(int ver, const char* path, void* buf);
int __fxstatat64_wrapper(int ver, int dirfd, const char* path, void* buf, int flags);

/* ============================================================================
 * statfs/statvfs - 声明在 wrapper_stat.c
 * ============================================================================ */

/* ============================================================================
 * 文件操作扩展
 * ============================================================================ */

int renameat2_wrapper(int olddirfd, const char* oldpath, int newdirfd, const char* newpath, unsigned int flags);
int dup_wrapper(int oldfd);
int dup2_wrapper(int oldfd, int newfd);
int dup3_wrapper(int oldfd, int newfd, int flags);
int pipe_wrapper(int pipefd[2]);
int pipe2_wrapper(int pipefd[2], int flags);
int fchdir_wrapper(int fd);

/* ============================================================================
 * mkstemp 系列
 * ============================================================================ */

int mkstemp_wrapper(char* tmpl);
int mkostemp_wrapper(char* tmpl, int flags);
int mkstemp64_wrapper(char* tmpl);
char* mkdtemp_wrapper(char* tmpl);

/* ============================================================================
 * 目录操作扩展
 * ============================================================================ */

int readdir_r_wrapper(DIR* dirp, struct dirent* entry, struct dirent** result);
int scandir_wrapper(const char* dirp, struct dirent*** namelist,
                    int (*filter)(const struct dirent*), int (*compar)(const struct dirent**, const struct dirent**));

/* ============================================================================
 * BSD 内存函数
 * ============================================================================ */

int bcmp_wrapper(const void* s1, const void* s2, size_t n);
void bcopy_wrapper(const void* src, void* dest, size_t n);
void bzero_wrapper(void* s, size_t n);
void explicit_bzero_wrapper(void* s, size_t n);



void __stack_chk_fail_wrapper(void);

/* ============================================================================
 * FORTIFY 扩展
 * ============================================================================ */

void __explicit_bzero_chk_wrapper(void* s, size_t n, size_t destlen);
size_t __mbstowcs_chk_wrapper(wchar_t* dst, const char* src, size_t len, size_t dstlen);
size_t __wcstombs_chk_wrapper(char* dst, const wchar_t* src, size_t len, size_t dstlen);
ssize_t __readlinkat_chk_wrapper(int dirfd, const char* path, char* buf, size_t len, size_t buflen);
int __openat64_2_wrapper(int dirfd, const char* path, int flags);

/* ============================================================================
 * glibc 特有函数
 * ============================================================================ */

size_t parse_printf_format_wrapper(const char* fmt, size_t n, int* argtypes);
const char* strerrorname_np_wrapper(int errnum);
const char* strerrordesc_np_wrapper(int errnum);
int getdtablesize_wrapper(void);


/* ============================================================================
 * Linux syscall 包装
 * ============================================================================ */

int open_tree_wrapper(int dirfd, const char* pathname, unsigned int flags);
int pidfd_open_wrapper(pid_t pid, unsigned int flags);

/* ============================================================================
 * locale _l 函数扩展
 * ============================================================================ */

double strtod_l_wrapper(const char* nptr, char** endptr, locale_t loc);
float strtof_l_wrapper(const char* nptr, char** endptr, locale_t loc);
long double strtold_l_wrapper(const char* nptr, char** endptr, locale_t loc);
long strtol_l_wrapper(const char* nptr, char** endptr, int base, locale_t loc);
long long strtoll_l_wrapper(const char* nptr, char** endptr, int base, locale_t loc);
unsigned long strtoul_l_wrapper(const char* nptr, char** endptr, int base, locale_t loc);
unsigned long long strtoull_l_wrapper(const char* nptr, char** endptr, int base, locale_t loc);

wint_t towlower_l_wrapper(wint_t wc, locale_t loc);
wint_t towupper_l_wrapper(wint_t wc, locale_t loc);
wctype_t wctype_l_wrapper(const char* name, locale_t loc);
int iswctype_l_wrapper(wint_t wc, wctype_t desc, locale_t loc);

int iswalpha_l_wrapper(wint_t wc, locale_t loc);
int iswdigit_l_wrapper(wint_t wc, locale_t loc);
int iswspace_l_wrapper(wint_t wc, locale_t loc);
int iswupper_l_wrapper(wint_t wc, locale_t loc);
int iswlower_l_wrapper(wint_t wc, locale_t loc);
int iswprint_l_wrapper(wint_t wc, locale_t loc);
int isgraph_l_wrapper(int c, locale_t loc);

size_t strftime_l_wrapper(char* s, size_t max, const char* fmt, const struct tm* tm, locale_t loc);
size_t wcsftime_l_wrapper(wchar_t* s, size_t max, const wchar_t* fmt, const struct tm* tm, locale_t loc);
char* nl_langinfo_l_wrapper(int item, locale_t loc);
char* nl_langinfo_wrapper(int item);

char* strerror_l_wrapper(int errnum, locale_t loc);
char* __xpg_strerror_r_wrapper(int errnum, char* buf, size_t buflen);
char* strerror_r_wrapper(int errnum, char* buf, size_t buflen);

/* ============================================================================
 * socket/signal 扩展
 * ============================================================================ */

int socket_wrapper(int domain, int type, int protocol);
void* signal_wrapper(int signum, void* handler);

/* ============================================================================
 * C99 格式函数 (声明在 wrapper_libc.c)
 * ============================================================================ */

/* ============================================================================
 * select/pselect (声明在 wrapper_libc.c)
 * ============================================================================ */

/* ============================================================================
 * FORTIFY wchar 函数
 * ============================================================================ */

wchar_t* wmemset_chk_wrapper(wchar_t* s, wchar_t c, size_t n, size_t destlen);
wchar_t* wmemcpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);
wchar_t* wmemmove_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);
size_t mbsnrtowcs_chk_wrapper(wchar_t* dest, const char** src, size_t nms, size_t len, mbstate_t* ps, size_t dstlen);
size_t mbsrtowcs_chk_wrapper(wchar_t* dest, const char** src, size_t len, mbstate_t* ps, size_t dstlen);
int fprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, ...);
int sprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* format, ...);
int snprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* format, ...);

/* ============================================================================
 * getauxval/pthread_key
 * ============================================================================ */

unsigned long getauxval_internal_wrapper(unsigned long type);

/* ============================================================================
 * Java/GCJ 兼容
 * ============================================================================ */

void _Jv_RegisterClasses_stub(void* classes);

/* ============================================================================
 * 动态链接器内部函数
 * ============================================================================ */

int dl_find_object_wrapper(void* address, void* result);
int dl_iterate_phdr_wrapper(int (*callback)(void*, size_t, void*), void* data);

/* ============================================================================
 * libc 全局变量
 * ============================================================================ */

char* glibc_bridge_get_libc_single_threaded(void);

/* ============================================================================
 * C23 函数
 * ============================================================================ */

unsigned long long isoc23_strtoull_wrapper(const char* nptr, char** endptr, int base);
unsigned long long strtoull_wrapper(const char* nptr, char** endptr, int base);

/* ============================================================================
 * FORTIFY printf 扩展
 * ============================================================================ */

int printf_chk_wrapper(int flag, const char* format, ...);
int vprintf_chk_wrapper(int flag, const char* format, va_list ap);
int vfprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, va_list ap);
int vsprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* format, va_list ap);
int vsnprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* format, va_list ap);
int vdprintf_chk_wrapper(int fd, int flag, const char* format, va_list ap);
int vfwprintf_chk_wrapper(FILE* stream, int flag, const wchar_t* fmt, va_list ap);
void vsyslog_chk_wrapper(int priority, int flag, const char* format, va_list ap);
void syslog_chk_wrapper(int priority, int flag, const char* format, ...);
long fdelt_chk_wrapper(long d);
int open64_2_wrapper(const char* path, int flags);

/* ============================================================================
 * 数学扩展
 * ============================================================================ */

double exp10_wrapper(double x);
float exp10f_wrapper(float x);
long double exp10l_wrapper(long double x);
double pow10_wrapper(double x);
float pow10f_wrapper(float x);
long double pow10l_wrapper(long double x);

/* ============================================================================
 * sigsetjmp 扩展
 * ============================================================================ */

int sigsetjmp_wrapper(void* env, int savemask);

/* ============================================================================
 * pthread 扩展
 * ============================================================================ */

int pthread_setattr_default_np_wrapper(const pthread_attr_t* attr);
int pthread_getattr_default_np_wrapper(pthread_attr_t* attr);
int pthread_attr_setaffinity_np_wrapper(pthread_attr_t* attr, size_t cpusetsize, const void* cpuset);
int pthread_attr_getaffinity_np_wrapper(const pthread_attr_t* attr, size_t cpusetsize, void* cpuset);
void pthread_cleanup_push_wrapper(void (*routine)(void*), void* arg);
void pthread_cleanup_pop_wrapper(int execute);

/* ============================================================================
 * obstack
 * ============================================================================ */

extern void (*obstack_alloc_failed_handler)(void);
int obstack_begin_wrapper(void* h, size_t size, size_t alignment, 
                          void* (*chunkfun)(size_t), void (*freefun)(void*));
int obstack_begin_1_wrapper(void* h, size_t size, size_t alignment,
                            void* (*chunkfun)(void*, size_t), void (*freefun)(void*, void*), void* arg);
void obstack_free_wrapper(void* h, void* obj);
int obstack_vprintf_wrapper(void* h, const char* format, va_list ap);
int obstack_printf_wrapper(void* h, const char* format, ...);
int obstack_vprintf_chk_wrapper(void* h, int flag, const char* format, va_list ap);
void obstack_free_direct_wrapper(void* h, void* obj);
void obstack_newchunk_wrapper(void* h, size_t length);

/* ============================================================================
 * sysinfo 扩展
 * ============================================================================ */

long sysconf_internal_wrapper(int name);
int getcpu_wrapper(unsigned* cpu, unsigned* node);
int malloc_trim_wrapper(size_t pad);
void* libc_malloc_wrapper(size_t size);
void* libc_calloc_wrapper(size_t nmemb, size_t size);
void* libc_realloc_wrapper(void* ptr, size_t size);
void libc_free_wrapper(void* ptr);
int shm_unlink_wrapper(const char* name);
int dlinfo_wrapper(void* handle, int request, void* info);
void* fts64_open_wrapper(char* const* path_argv, int options, int (*compar)(const void**, const void**));
void* fts64_read_wrapper(void* ftsp);
int fts64_close_wrapper(void* ftsp);
void globfree64_wrapper(void* pglob);
int getprotobyname_r_wrapper(const char* name, void* result_buf, char* buf, size_t buflen, void** result);
int isoc99_vwscanf_wrapper(const wchar_t* format, va_list ap);
int isoc99_vswscanf_wrapper(const wchar_t* str, const wchar_t* format, va_list ap);
int isoc99_vfwscanf_wrapper(void* stream, const wchar_t* format, va_list ap);
int shm_open_wrapper(const char* name, int oflag, int mode);
void* libc_memalign_wrapper(size_t alignment, size_t size);
void* res_state_wrapper(void);
int getprotobynumber_r_wrapper(int proto, void* result_buf, char* buf, size_t buflen, void** result);
int glob64_wrapper(const char* pattern, int flags, int (*errfunc)(const char*, int), void* pglob);

/* ============================================================================
 * FORTIFY 扩展 (字符串)
 * ============================================================================ */

int vasprintf_chk_wrapper(char** strp, int flag, const char* format, va_list ap);
int vswprintf_chk_wrapper(wchar_t* s, size_t maxlen, int flag, size_t slen, const wchar_t* format, va_list ap);
int vwprintf_chk_wrapper(int flag, const wchar_t* format, va_list ap);
void longjmp_chk_wrapper(jmp_buf env, int val);
int swprintf_chk_wrapper(wchar_t* s, size_t n, int flag, size_t slen, const wchar_t* format, ...);
wchar_t* wcscat_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t destlen);
wchar_t* wcscpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t destlen);
wchar_t* wcsncat_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);
wchar_t* wcsncpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen);
int asprintf_chk_wrapper(char** strp, int flag, const char* format, ...);
char* realpath_chk_wrapper(const char* path, char* resolved_path, size_t resolved_len);
char* stpcpy_chk_wrapper(char* dest, const char* src, size_t destlen);
char* stpncpy_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen);

/* ============================================================================
 * pthread mutex 扩展
 * ============================================================================ */

int pthread_mutexattr_setrobust_wrapper(void* attr, int robustness);
int pthread_mutexattr_getrobust_wrapper(const void* attr, int* robustness);
int pthread_mutexattr_setprioceiling_wrapper(void* attr, int prioceiling);
int pthread_mutexattr_getprioceiling_wrapper(const void* attr, int* prioceiling);
int pthread_mutex_consistent_wrapper(void* mutex);
void pthread_register_cancel_wrapper(void* buf);
void pthread_unregister_cancel_wrapper(void* buf);
void pthread_unwind_next_wrapper(void* buf);

/* ============================================================================
 * stdio 扩展
 * ============================================================================ */

void* fopencookie_wrapper(void* cookie, const char* mode, void* io_funcs);





int PAL_RegisterModule_wrapper(const char* name);

#ifdef __cplusplus
}
#endif

#endif /* GLIBC_BRIDGE_WRAPPERS_H */
