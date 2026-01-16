/*
 * glibc-bridge 动态链接器 - 符号表
 *
 * Maps glibc symbol names to wrapper functions or NULL (use bionic).
 * This is the central registry of all symbol mappings.
 */

#define _GNU_SOURCE
#include <stddef.h>
#include <stdlib.h>
#include <inttypes.h>
#include <wchar.h>

#include "../include/wrappers.h"

/* External data symbols that need to be exported directly (not as functions) */
extern void (*obstack_alloc_failed_handler)(void);

/* ============================================================================
 * Global State
 * ============================================================================ */

/* __libc_single_threaded - glibc global variable */
/* 0 = multi-threaded, non-zero = single-threaded */
static char g_libc_single_threaded = 0;

char* glibc_bridge_get_libc_single_threaded(void) {
    return &g_libc_single_threaded;
}

/* ============================================================================
 * Symbol Wrapper Table
 * 
 * Maps glibc symbols to:
 *   - wrapper function pointer (when ABI differs)
 *   - NULL (when bionic's implementation is compatible)
 * ============================================================================ */

static const symbol_wrapper_t g_symbol_wrappers[] = {
    /* ================================================================
     * CRITICAL: glibc startup function
     * ================================================================ */
    {"__libc_start_main",       (void*)__libc_start_main_wrapper},
    
    /* ================================================================
     * ctype functions - glibc uses different implementation
     * ================================================================ */
    {"__ctype_b_loc",           (void*)__ctype_b_loc_wrapper},
    {"__ctype_tolower_loc",     (void*)__ctype_tolower_loc_wrapper},
    {"__ctype_toupper_loc",     (void*)__ctype_toupper_loc_wrapper},
    
    /* ================================================================
     * errno - glibc uses __errno_location
     * ================================================================ */
    {"__errno_location",        (void*)__errno_location_wrapper},
    
    /* ================================================================
     * Global variables (data symbols) - resolved at runtime
     * Note: These return the address of the variable, not a function
     * ================================================================ */
    {"environ",                 NULL},   /* Resolved by glibc_bridge_resolve_symbol special handling */
    {"__environ",               NULL},   /* alias for environ */
    
    /* getopt global state variables */
    {"optarg",                  NULL},   /* Resolved by glibc_bridge_resolve_symbol special handling */
    {"optind",                  NULL},   /* Resolved by glibc_bridge_resolve_symbol special handling */
    {"opterr",                  NULL},   /* Resolved by glibc_bridge_resolve_symbol special handling */
    {"optopt",                  NULL},   /* Resolved by glibc_bridge_resolve_symbol special handling */
    
    /* ================================================================
     * glibc-specific functions
     * ================================================================ */
    {"secure_getenv",           (void*)secure_getenv_wrapper},
    {"__register_atfork",       (void*)__register_atfork_wrapper},
    {"error",                   (void*)error_wrapper},
    
    /* ================================================================
     * CXA/TM functions - must use wrappers to handle dso_handle properly
     * bionic's __cxa_atexit validates dso_handle against its soinfo list,
     * which doesn't include our glibc libraries.
     * 
     * IMPORTANT: libstdc++.so.6 exports __cxa_thread_atexit which internally
     * calls __cxa_thread_atexit_impl. We must intercept BOTH to prevent
     * bionic's __cxa_thread_atexit_impl from being called (which validates
     * dso_handle against bionic's soinfo list and crashes).
     * ================================================================ */
    {"__cxa_finalize",          (void*)__cxa_finalize_wrapper},
    {"__cxa_thread_atexit",     (void*)__cxa_thread_atexit_wrapper},
    {"__cxa_thread_atexit_impl", (void*)__cxa_thread_atexit_impl_wrapper},
    /* NOTE: __cxa_atexit is defined later with __cxa_atexit_wrapper */
    {"__gmon_start__",          (void*)__gmon_start___stub},
    {"_ITM_deregisterTMCloneTable", (void*)_ITM_deregisterTMCloneTable_stub},
    {"_ITM_registerTMCloneTable", (void*)_ITM_registerTMCloneTable_stub},
    
    /* ================================================================
     * LTTng stubs (for .NET CoreCLR tracing)
     * ================================================================ */
    {"lttng_probe_register",    (void*)lttng_probe_register_stub},
    {"lttng_probe_unregister",  (void*)lttng_probe_unregister_stub},
    
    /* ================================================================
     * h_errno location (network errors)
     * ================================================================ */
    {"__h_errno_location",      (void*)__h_errno_location_wrapper},
    
    /* ================================================================
     * Memory allocation (valloc/pvalloc)
     * ================================================================ */
    {"valloc",                  (void*)valloc_wrapper},
    {"pvalloc",                 (void*)pvalloc_wrapper},
    
    /* ================================================================
     * String functions
     * ================================================================ */
    {"strverscmp",              (void*)strverscmp_wrapper},
    {"__xpg_basename",          (void*)__xpg_basename_wrapper},
    {"rawmemchr",               (void*)rawmemchr_wrapper},
    {"__rawmemchr",             (void*)rawmemchr_wrapper},
    
    /* ================================================================
     * File system internal functions
     * ================================================================ */
    {"__xmknod",                (void*)__xmknod_wrapper},
    
    /* ================================================================
     * wordexp
     * ================================================================ */
    {"wordexp",                 (void*)wordexp_wrapper},
    {"wordfree",                (void*)wordfree_wrapper},
    
    /* ================================================================
     * stdio - FILE structure conversion wrappers
     * Note: fopen/fopen64/freopen are defined later with path translation
     * ================================================================ */
    {"fclose",                  (void*)fclose_wrapper},
    {"fread",                   (void*)fread_wrapper},
    {"fwrite",                  (void*)fwrite_wrapper},
    {"fgets",                   (void*)fgets_wrapper},
    {"fputs",                   (void*)fputs_wrapper},
    {"fgetc",                   (void*)fgetc_wrapper},
    {"fputc",                   (void*)fputc_wrapper},
    {"getc",                    (void*)getc_wrapper},
    {"_IO_getc",                (void*)getc_wrapper},  /* glibc internal name for getc */
    {"putc",                    (void*)putc_wrapper},
    {"_IO_putc",                (void*)putc_wrapper},  /* glibc internal name for putc */
    {"ungetc",                  (void*)ungetc_wrapper},
    {"fprintf",                 (void*)fprintf_wrapper},
    {"vfprintf",                (void*)vfprintf_wrapper},
    {"fseek",                   (void*)fseek_wrapper},
    {"fseeko",                  (void*)fseeko_wrapper},
    {"fseeko64",                (void*)fseeko64_wrapper},
    {"ftell",                   (void*)ftell_wrapper},
    {"ftello",                  (void*)ftello_wrapper},
    {"ftello64",                (void*)ftello64_wrapper},
    {"rewind",                  (void*)rewind_wrapper},
    {"fflush",                  (void*)fflush_wrapper},
    {"feof",                    (void*)feof_wrapper},
    {"ferror",                  (void*)ferror_wrapper},
    {"clearerr",                (void*)clearerr_wrapper},
    {"fileno",                  (void*)fileno_wrapper},
    {"setvbuf",                 (void*)setvbuf_wrapper},
    {"setbuf",                  (void*)setbuf_wrapper},
    {"flockfile",               (void*)flockfile_wrapper},
    {"funlockfile",             (void*)funlockfile_wrapper},
    {"ftrylockfile",            (void*)ftrylockfile_wrapper},
    {"__uflow",                 (void*)__uflow_wrapper},
    {"__overflow",              (void*)__overflow_wrapper},
    
    /* ================================================================
     * Direct bionic pass-through (no FILE conversion needed)
     * ================================================================ */
    {"printf",                  (void*)printf_wrapper},
    {"vprintf",                 (void*)vprintf_wrapper},
    {"puts",                    (void*)puts_wrapper},
    {"sprintf",                 NULL},
    {"snprintf",                (void*)snprintf_wrapper},
    {"strtof64",                (void*)strtof64_wrapper},   /* C2x _Float64 */
    {"strfromf64",              (void*)strfromf64_wrapper}, /* C2x _Float64 */
    {"putchar",                 NULL},
    /* Memory allocation - pass through to bionic */
    {"malloc",                  NULL},   /* void* malloc(size_t) */
    {"free",                    NULL},    /* void free(void*) */
    {"calloc",                  NULL},   /* void* calloc(size_t, size_t) */
    {"realloc",                 NULL},   /* void* realloc(void*, size_t) */
    /* Memory functions */
    {"memset",                  NULL},  /* void* memset(void*, int, size_t) - int treated as L */
    {"memcpy",                  NULL},  /* void* memcpy(void*, void*, size_t) */
    {"memmove",                 NULL},  /* void* memmove(void*, void*, size_t) */
    {"memcmp",                  NULL},  /* int memcmp(void*, void*, size_t) */
    {"memchr",                  NULL},   /* void* memchr(void*, int, size_t) - simplified */
    /* String functions */
    {"strlen",                  NULL},    /* size_t strlen(const char*) */
    {"strcpy",                  NULL},   /* char* strcpy(char*, const char*) */
    {"strncpy",                 NULL},  /* char* strncpy(char*, const char*, size_t) */
    {"strcat",                  NULL},   /* char* strcat(char*, const char*) */
    {"strncat",                 NULL},  /* char* strncat(char*, const char*, size_t) */
    {"strcmp",                  NULL},   /* int strcmp(const char*, const char*) */
    {"strncmp",                 NULL},  /* int strncmp(const char*, const char*, size_t) */
    {"strchr",                  NULL},   /* char* strchr(const char*, int) - simplified */
    {"strrchr",                 NULL},   /* char* strrchr(const char*, int) - simplified */
    {"strstr",                  NULL},   /* char* strstr(const char*, const char*) */
    {"strdup",                  (void*)strdup_wrapper},    /* char* strdup(const char*) */
    {"__strdup",                (void*)strdup_wrapper},    /* char* __strdup(const char*) -> strdup */
    {"strndup",                 NULL},   /* char* strndup(const char*, size_t) */
    {"getenv",                  NULL},    /* char* getenv(const char*) */
    {"exit",                    (void*)exit_wrapper},
    {"_exit",                   NULL},  /* Let _exit pass through */
    {"atexit",                  (void*)atexit_wrapper},
    {"__cxa_atexit",            (void*)__cxa_atexit_wrapper},
    {"abort",                   (void*)abort_wrapper},
    {"atoi",                    NULL},
    {"atol",                    NULL},
    {"atof",                    NULL},       /* double atof(const char*) */
    {"strtol",                  NULL},
    {"strtoul",                 NULL},
    {"strtod",                  NULL},      /* double strtod(const char*, char**) */
    {"strtof",                  NULL},      /* float strtof(const char*, char**) */
    {"strtold",                 NULL},      /* long double strtold(const char*, char**) - treat as double */
    {"qsort",                   qsort_wrapper},
    {"bsearch",                 bsearch_wrapper},
    {"lfind",                   lfind_wrapper},
    {"lsearch",                 lsearch_wrapper},
    {"tsearch",                 tsearch_wrapper},
    {"tfind",                   tfind_wrapper},
    {"tdelete",                 tdelete_wrapper},
    {"twalk",                   twalk_wrapper},
    {"tdestroy",                tdestroy_wrapper},
    {"rand",                    NULL},
    {"srand",                   NULL},
    {"time",                    NULL},
    /* Math functions - return double */
    {"sqrt",                    NULL},       /* double sqrt(double) */
    {"sin",                     NULL},
    {"cos",                     NULL},
    {"tan",                     NULL},
    {"log",                     NULL},
    {"exp",                     NULL},
    {"pow",                     NULL},      /* double pow(double, double) */
    {"atan2",                   NULL},      /* double atan2(double y, double x) */
    {"hypot",                   NULL},      /* double hypot(double, double) */
    {"remainder",               NULL},      /* double remainder(double, double) */
    {"copysign",                NULL},      /* double copysign(double, double) */
    {"fdim",                    NULL},      /* double fdim(double, double) */
    {"fmax",                    NULL},      /* double fmax(double, double) */
    {"fmin",                    NULL},      /* double fmin(double, double) */
    {"floor",                   NULL},
    {"ceil",                    NULL},
    {"fabs",                    NULL},
    {"fmod",                    NULL},      /* double fmod(double, double) */
    {"fmodf",                   NULL},      /* float fmodf(float, float) */
    {"nearbyint",               NULL},       /* double nearbyint(double) */
    {"nearbyintf",              NULL},       /* float nearbyintf(float) */
    {"nearbyintl",              NULL},   /* long double */
    {"rint",                    NULL},       /* double rint(double) */
    {"rintf",                   NULL},       /* float rintf(float) */
    {"round",                   NULL},       /* double round(double) */
    {"lgamma",                  NULL},       /* double lgamma(double) */
    {"lgammaf",                 NULL},       /* float lgammaf(float) */
    {"tgamma",                  NULL},       /* double tgamma(double) */
    {"tgammaf",                 NULL},       /* float tgammaf(float) */
    {"roundf",                  NULL},       /* float roundf(float) */
    {"trunc",                   NULL},       /* double trunc(double) */
    {"truncf",                  NULL},       /* float truncf(float) */
    {"floorf",                  NULL},       /* float floorf(float) */
    {"ceilf",                   NULL},       /* float ceilf(float) */
    {"fabsf",                   NULL},       /* float fabsf(float) */
    {"sqrtf",                   NULL},       /* float sqrtf(float) */
    {"sinf",                    NULL},       /* float sinf(float) */
    {"cosf",                    NULL},       /* float cosf(float) */
    {"tanf",                    NULL},       /* float tanf(float) */
    {"logf",                    NULL},       /* float logf(float) */
    {"expf",                    NULL},       /* float expf(float) */
    {"powf",                    NULL},      /* float powf(float, float) */
    {"atan2f",                  NULL},      /* float atan2f(float, float) */
    {"cabs",                    (void*)cabs_wrapper},      /* double cabs(double complex) - complex passed as 2 doubles */
    {"carg",                    (void*)carg_wrapper},      /* double carg(double complex) - complex passed as 2 doubles */
    {"cabsf",                   (void*)cabsf_wrapper},     /* float cabsf(float complex) */
    {"cargf",                   (void*)cargf_wrapper},     /* float cargf(float complex) */
    {"creal",                   (void*)creal_wrapper},     /* double creal(double complex) */
    {"cimag",                   (void*)cimag_wrapper},     /* double cimag(double complex) */
    {"csqrt",                   NULL},       /* double complex csqrt(double complex) */
    {"cexp",                    NULL},       /* double complex cexp(double complex) */
    {"clog",                    NULL},       /* double complex clog(double complex) */
    {"cpow",                    NULL},      /* double complex cpow(double complex, double complex) */
    {"csin",                    NULL},       /* double complex csin(double complex) */
    {"ccos",                    NULL},       /* double complex ccos(double complex) */
    {"ctan",                    NULL},       /* double complex ctan(double complex) */
    {"conj",                    NULL},       /* double complex conj(double complex) */
    {"abs",                     NULL},
    {"labs",                    NULL},
    {"div",                     NULL},    /* div_t div(int, int) - struct return in RAX */
    {"ldiv",                    NULL},   /* ldiv_t ldiv(long, long) - struct return in RAX:RDX */
    {"lldiv",                   NULL},  /* lldiv_t lldiv(long long, long long) - struct return in RAX:RDX */
    {"close",                   NULL},
    {"read",                    NULL},
    {"write",                   NULL},
    {"pread",                   NULL},   /* ssize_t pread(int, void*, size_t, off_t) */
    {"pread64",                 NULL},   /* ssize_t pread64(int, void*, size_t, off64_t) */
    {"pwrite",                  NULL},   /* ssize_t pwrite(int, void*, size_t, off_t) */
    {"pwrite64",                NULL},   /* ssize_t pwrite64(int, void*, size_t, off64_t) */
    {"lseek",                   NULL},
    {"mmap",                    NULL},
    {"munmap",                  NULL},
    {"mprotect",                NULL},
    {"mlock",                   (void*)mlock_wrapper},
    {"munlock",                 (void*)munlock_wrapper},
    {"mlockall",                (void*)mlockall_wrapper},
    {"munlockall",              (void*)munlockall_wrapper},
    {"madvise",                 (void*)madvise_wrapper},
    {"vsnprintf",               (void*)vsnprintf_wrapper},
    {"mmap64",                  NULL},
    {"lseek64",                 NULL},
    {"getcwd",                  NULL},
    {"strerror",                (void*)strerror_wrapper},
    {"strcasecmp",              NULL},
    {"strncasecmp",             NULL},
    {"toupper",                 NULL},
    {"tolower",                 NULL},
    {"isgraph",                 (void*)isgraph_wrapper},
    {"getaddrinfo",             NULL},  /* Direct pass-through */
    {"freeaddrinfo",            NULL},
    {"inet_pton",               NULL},  /* Direct pass-through */
    {"inet_ntop",               NULL},
    /* Wide character functions - direct pass-through */
    {"wcschr",                  NULL},
    {"wcsrchr",                 NULL},
    {"wcspbrk",                 NULL},
    {"wmemcpy",                 NULL},
    {"wmemset",                 NULL},
    {"wcstod",                  NULL},     /* double wcstod(const wchar_t*, wchar_t**) */
    {"gmtime",                  NULL},
    {"localtime",               NULL},
    {"strftime",                NULL},
    {"difftime",                NULL},  /* double difftime(time_t, time_t) */
    {"clock_gettime",           NULL},  /* Direct pass-through */
    {"nanosleep",               NULL},  /* Direct pass-through */
    {"sched_yield",             NULL},
    {"pthread_create",          (void*)pthread_create_wrapper},
    {"pthread_join",            NULL},
    {"pthread_exit",            NULL},
    {"pthread_self",            NULL},
    {"pthread_once",            NULL},
    {"pthread_mutex_init",      NULL},
    {"pthread_mutex_lock",      NULL},
    {"pthread_mutex_unlock",    NULL},
    {"pthread_mutex_trylock",   NULL},
    {"pthread_mutex_destroy",   NULL},
    {"pthread_mutexattr_init",  NULL},
    {"pthread_mutexattr_destroy", NULL},
    {"pthread_mutexattr_settype", NULL},
    {"pthread_cond_init",       NULL},
    {"pthread_cond_destroy",    NULL},
    {"pthread_cond_wait",       NULL},
    {"pthread_cond_signal",     NULL},
    {"pthread_cond_broadcast",  NULL},
    {"pthread_cond_timedwait",  NULL},
    {"pthread_condattr_init",   NULL},
    {"pthread_condattr_destroy", NULL},
    {"pthread_condattr_setclock", NULL},
    {"pthread_key_create",      NULL},
    {"pthread_key_delete",      NULL},
    {"pthread_getspecific",     NULL},
    {"pthread_setspecific",     NULL},
    {"pthread_attr_init",       NULL},
    {"pthread_attr_destroy",    NULL},
    {"pthread_attr_setstacksize", NULL},
    {"pthread_attr_setdetachstate", NULL},
    {"pthread_attr_getstack",   NULL},
    {"pthread_getattr_np",      NULL},
    {"pthread_getcpuclockid",   NULL},
    {"pthread_getschedparam",   NULL},
    {"pthread_setschedparam",   NULL},
    {"pthread_setname_np",      NULL},
    {"pthread_getaffinity_np",  NULL},  /* May need wrapper on some systems */
    {"pthread_sigmask",         (void*)pthread_sigmask_wrapper},
    {"pthread_kill",            (void*)pthread_kill_wrapper},
    {"pthread_cancel",          (void*)pthread_cancel_wrapper},
    {"pthread_setcancelstate",  (void*)pthread_setcancelstate_wrapper},
    {"pthread_setcanceltype",   (void*)pthread_setcanceltype_wrapper},
    {"pthread_testcancel",      (void*)pthread_testcancel_wrapper},
    
    /* ================================================================
     * Scheduler and CPU functions
     * ================================================================ */
    {"sched_yield",             NULL},
    {"sched_getaffinity",       (void*)sched_getaffinity_wrapper},
    {"sched_setaffinity",       (void*)sched_setaffinity_wrapper},
    {"sched_getcpu",            NULL},
    {"sched_get_priority_max",  NULL},
    {"sched_get_priority_min",  NULL},
    {"syscall",                 (void*)syscall_wrapper},  /* Intercept membarrier blocked by seccomp */
    {"__sched_cpucount",        NULL},  /* glibc CPU_COUNT implementation */
    {"prctl",                   NULL},
    {"getrlimit",               NULL},
    {"getrlimit64",             NULL},
    {"setrlimit",               NULL},
    {"setrlimit64",             NULL},
    /* Note: sysconf is defined later with sysconf_wrapper */
    
    /* I/O multiplexing - select/pselect have wrappers defined later */
    {"poll",                    NULL},
    {"ppoll",                   NULL},
    /* Note: select and pselect have wrappers defined later in this file */
    {"epoll_create",            NULL},
    {"epoll_create1",           NULL},
    {"epoll_ctl",               NULL},
    {"epoll_wait",              NULL},
    {"epoll_pwait",             NULL},
    {"eventfd",                 NULL},
    {"eventfd_read",            NULL},
    {"eventfd_write",           NULL},
    
    {"dlopen",                  (void*)dlopen_wrapper},
    {"dlsym",                   (void*)dlsym_wrapper},
    {"dladdr",                  (void*)dladdr_wrapper},
    {"dlclose",                 (void*)dlclose_wrapper},
    {"dlerror",                 NULL},
    
    /* ================================================================
     * Standard streams - data symbols
     * ================================================================ */
    {"stdout",                  NULL},
    {"stderr",                  NULL},
    {"stdin",                   NULL},
    /* glibc internal FILE symbols */
    {"_IO_2_1_stdout_",         NULL},
    {"_IO_2_1_stderr_",         NULL},
    {"_IO_2_1_stdin_",          NULL},
    
    /* ================================================================
     * glibc stat wrappers - __xstat family
     * ================================================================ */
    {"__fxstat64",              (void*)__fxstat64_wrapper},
    {"__xstat64",               (void*)__xstat64_wrapper},
    {"__lxstat64",              (void*)__lxstat64_wrapper},
    {"__fxstatat64",            (void*)__fxstatat64_wrapper},
    {"__fxstat",                (void*)__fxstat64_wrapper},
    {"__xstat",                 (void*)__xstat64_wrapper},
    {"__lxstat",                (void*)__lxstat64_wrapper},
    {"__fxstatat",              (void*)__fxstatat64_wrapper},
    
    /* ================================================================
     * Direct stat functions
     * ================================================================ */
    {"stat",                    (void*)stat_wrapper},
    {"fstat",                   (void*)fstat_wrapper},
    {"lstat",                   (void*)lstat_wrapper},
    {"fstatat",                 (void*)fstatat_wrapper},
    {"stat64",                  (void*)stat64_wrapper},
    {"fstat64",                 (void*)fstat64_wrapper},
    {"lstat64",                 (void*)lstat64_wrapper},
    {"fstatat64",               (void*)fstatat64_wrapper},
    
    /* ================================================================
     * statfs/statvfs
     * ================================================================ */
    {"statfs",                  (void*)statfs_wrapper},
    {"fstatfs",                 (void*)fstatfs_wrapper},
    {"statfs64",                (void*)statfs64_wrapper},
    {"fstatfs64",               (void*)fstatfs64_wrapper},
    {"statvfs",                 (void*)statvfs_wrapper},
    {"fstatvfs",                (void*)fstatvfs_wrapper},
    {"statvfs64",               (void*)statvfs64_wrapper},
    {"fstatvfs64",              (void*)fstatvfs64_wrapper},
    
    /* ================================================================
     * Path/link operations
     * ================================================================ */
    {"realpath",                (void*)realpath_wrapper},
    {"readlink",                (void*)readlink_wrapper},
    {"readlinkat",              (void*)readlinkat_wrapper},
    {"access",                  (void*)access_wrapper},
    {"faccessat",               (void*)faccessat_wrapper},
    {"chmod",                   (void*)chmod_wrapper},
    {"fchmod",                  (void*)fchmod_wrapper},
    {"fchmodat",                (void*)fchmodat_wrapper},
    {"chown",                   (void*)chown_wrapper},
    {"fchown",                  (void*)fchown_wrapper},
    {"fchownat",                (void*)fchownat_wrapper},
    {"lchown",                  (void*)lchown_wrapper},
    
    /* ================================================================
     * File open/create operations
     * ================================================================ */
    {"openat",                  (void*)openat_wrapper},
    {"creat",                   (void*)creat_wrapper},
    {"creat64",                 (void*)creat64_wrapper},
    
    /* ================================================================
     * Directory operations
     * ================================================================ */
    {"mkdir",                   (void*)mkdir_wrapper},
    {"mkdirat",                 (void*)mkdirat_wrapper},
    {"rmdir",                   (void*)rmdir_wrapper},
    
    /* ================================================================
     * Link operations
     * ================================================================ */
    {"link",                    (void*)link_wrapper},
    {"linkat",                  (void*)linkat_wrapper},
    {"symlink",                 (void*)symlink_wrapper},
    {"symlinkat",               (void*)symlinkat_wrapper},
    {"unlink",                  (void*)unlink_wrapper},
    {"unlinkat",                (void*)unlinkat_wrapper},
    {"rename",                  (void*)rename_wrapper},
    {"renameat",                (void*)renameat_wrapper},
    {"renameat2",               (void*)renameat2_wrapper},
    
    /* ================================================================
     * File descriptor operations
     * ================================================================ */
    {"dup",                     (void*)dup_wrapper},
    {"dup2",                    (void*)dup2_wrapper},
    {"dup3",                    (void*)dup3_wrapper},
    {"fcntl",                   (void*)fcntl_wrapper},
    {"fcntl64",                 (void*)fcntl_wrapper},
    {"ftruncate",               (void*)ftruncate_wrapper},
    {"ftruncate64",             (void*)ftruncate64_wrapper},
    {"truncate",                (void*)truncate_wrapper},
    {"truncate64",              (void*)truncate64_wrapper},
    
    /* ================================================================
     * Pipe operations
     * ================================================================ */
    {"pipe",                    (void*)pipe_wrapper},
    {"pipe2",                   (void*)pipe2_wrapper},
    
    /* ================================================================
     * Time modification
     * ================================================================ */
    {"utimensat",               (void*)utimensat_wrapper},
    {"futimens",                (void*)futimens_wrapper},
    
    /* ================================================================
     * mkstemp family
     * ================================================================ */
    {"mkstemp",                 (void*)mkstemp_wrapper},
    {"mkostemp",                (void*)mkostemp_wrapper},
    {"mkstemp64",               (void*)mkstemp64_wrapper},
    {"mkdtemp",                 (void*)mkdtemp_wrapper},
    
    /* ================================================================
     * Directory operations (path translated to fake rootfs)
     * ================================================================ */
    {"opendir",                 (void*)opendir_wrapper},
    {"fdopendir",               (void*)fdopendir_wrapper},
    {"closedir",                (void*)closedir_wrapper},
    {"readdir",                 (void*)readdir_wrapper},
    {"readdir64",               (void*)readdir_wrapper},  /* Same as readdir on aarch64 */
    {"readdir_r",               (void*)readdir_r_wrapper},
    {"readdir64_r",             (void*)readdir_r_wrapper}, /* Same as readdir_r on aarch64 */
    {"rewinddir",               (void*)rewinddir_wrapper},
    {"seekdir",                 (void*)seekdir_wrapper},
    {"telldir",                 (void*)telldir_wrapper},
    {"dirfd",                   (void*)dirfd_wrapper},
    {"scandir",                 (void*)scandir_wrapper},
    
    /* ================================================================
     * File open/fopen (path translated to fake rootfs)
     * ================================================================ */
    {"open",                    (void*)open_wrapper},
    {"open64",                  (void*)open64_wrapper},
    {"fopen",                   (void*)fopen_wrapper},
    {"fopen64",                 (void*)fopen64_wrapper},
    {"freopen",                 (void*)freopen_wrapper},
    {"freopen64",               (void*)freopen64_wrapper},
    {"tmpfile",                 (void*)tmpfile_wrapper},
    {"tmpfile64",               (void*)tmpfile64_wrapper},
    {"chdir",                   (void*)chdir_wrapper},
    {"fchdir",                  (void*)fchdir_wrapper},
    
    /* ================================================================
     * Memory/string functions - BSD names
     * ================================================================ */
    {"bcmp",                    (void*)bcmp_wrapper},
    {"bcopy",                   (void*)bcopy_wrapper},
    {"bzero",                   (void*)bzero_wrapper},
    {"explicit_bzero",          (void*)explicit_bzero_wrapper},
    
    /* ================================================================
     * C++ iostream initialization - use libstdc++ implementation
     * Set to NULL so they resolve from libstdc++.so.6
     * ================================================================ */
    {"_ZNSt8ios_base4InitC1Ev", NULL},  /* -> libstdc++ */
    {"_ZNSt8ios_base4InitC2Ev", NULL},  /* -> libstdc++ */
    {"_ZNSt8ios_base4InitD1Ev", NULL},  /* -> libstdc++ */
    {"_ZNSt8ios_base4InitD2Ev", NULL},  /* -> libstdc++ */
    {"_ZNSt8ios_baseD2Ev",      NULL},  /* -> libstdc++ */
    {"_ZSt9terminatev",         NULL},  /* -> libstdc++ */
    
    /* ================================================================
     * C++ exception throwing - use libstdc++ implementation
     * ================================================================ */
    {"_ZSt19__throw_logic_errorPKc",      NULL},  /* -> libstdc++ */
    {"_ZSt20__throw_length_errorPKc",     NULL},  /* -> libstdc++ */
    {"_ZSt20__throw_out_of_rangePKc",     NULL},  /* -> libstdc++ */
    {"_ZSt24__throw_out_of_range_fmtPKcz",NULL},  /* -> libstdc++ */
    {"_ZSt24__throw_invalid_argumentPKc", NULL},  /* -> libstdc++ */
    {"_ZSt16__throw_bad_castv",           NULL},  /* -> libstdc++ */
    
    /* ================================================================
     * getdelim/getline
     * ================================================================ */
    {"__getdelim",              (void*)getdelim_wrapper},
    {"getdelim",                (void*)getdelim_wrapper},
    {"getline",                 (void*)getline_wrapper},
    {"__fsetlocking",           (void*)__fsetlocking_wrapper},
    
    /* popen/pclose */
    {"popen",                   (void*)popen_wrapper},
    {"pclose",                  (void*)pclose_wrapper},
    
    /* ================================================================
     * C++ support - bionic's libc++
     * ================================================================ */
    {"_Znwm",                   NULL},  /* operator new(size_t) */
    {"_ZdlPv",                  NULL},  /* operator delete(void*) */
    {"_Znam",                   NULL},  /* operator new[](size_t) */
    {"_ZdaPv",                  NULL},  /* operator delete[](void*) */
    {"_ZdlPvm",                 NULL},  /* operator delete(void*, size_t) */
    {"_ZdaPvm",                 NULL},  /* operator delete[](void*, size_t) */
    
    /* ================================================================
     * C++ exceptions - bionic's libc++
     * ================================================================ */
    {"__cxa_begin_catch",       NULL},
    {"__cxa_end_catch",         NULL},
    {"__cxa_rethrow",           NULL},
    {"__cxa_throw",             NULL},
    {"__cxa_allocate_exception", NULL},
    {"__cxa_free_exception",    NULL},
    {"__cxa_call_unexpected",   NULL},
    {"__cxa_guard_acquire",     NULL},
    {"__cxa_guard_release",     NULL},
    {"__cxa_guard_abort",       NULL},
    {"__gxx_personality_v0",    NULL},
    {"_Unwind_Resume",          NULL},
    
    /* ================================================================
     * Stack protection
     * ================================================================ */
    {"__stack_chk_fail",        (void*)__stack_chk_fail_wrapper},
    {"__stack_chk_guard",       NULL},  /* Resolved specially in glibc_bridge_resolve_symbol */
    
    /* ================================================================
     * FORTIFY_SOURCE functions (security hardening)
     * ================================================================ */
    {"__explicit_bzero_chk",    (void*)__explicit_bzero_chk_wrapper},
    {"__mbstowcs_chk",          (void*)__mbstowcs_chk_wrapper},
    {"__wcstombs_chk",          (void*)__wcstombs_chk_wrapper},
    {"__memcpy_chk",            (void*)__memcpy_chk_wrapper},
    {"__memmove_chk",           (void*)__memmove_chk_wrapper},
    {"__memset_chk",            (void*)__memset_chk_wrapper},
    {"__strcpy_chk",            (void*)__strcpy_chk_wrapper},
    {"__strncpy_chk",           (void*)__strncpy_chk_wrapper},
    {"__strcat_chk",            (void*)__strcat_chk_wrapper},
    {"__strncat_chk",           (void*)__strncat_chk_wrapper},
    {"__readlinkat_chk",        (void*)__readlinkat_chk_wrapper},
    {"__openat64_2",            (void*)__openat64_2_wrapper},
    
    /* ================================================================
     * glibc-specific functions (not in bionic)
     * ================================================================ */
    {"parse_printf_format",     (void*)parse_printf_format_wrapper},
    {"strerrorname_np",         (void*)strerrorname_np_wrapper},
    {"strerrordesc_np",         (void*)strerrordesc_np_wrapper},
    {"get_current_dir_name",    (void*)get_current_dir_name_wrapper},
    {"getdtablesize",           (void*)getdtablesize_wrapper},
    {"sigisemptyset",           (void*)sigisemptyset_wrapper},
    
    /* ================================================================
     * Linux-specific syscall wrappers (stubs for newer syscalls)
     * ================================================================ */
    {"open_tree",               (void*)open_tree_wrapper},
    {"pidfd_open",              (void*)pidfd_open_wrapper},
    {"pidfd_send_signal",       (void*)pidfd_send_signal_wrapper},
    {"name_to_handle_at",       (void*)name_to_handle_at_wrapper},
    
    /* ================================================================
     * Locale functions (_l suffix)
     * ================================================================ */
    {"__newlocale",             (void*)newlocale_wrapper},
    {"newlocale",               (void*)newlocale_wrapper},
    {"__freelocale",            (void*)freelocale_wrapper},
    {"freelocale",              (void*)freelocale_wrapper},
    {"__duplocale",             (void*)duplocale_wrapper},
    {"duplocale",               (void*)duplocale_wrapper},
    {"__uselocale",             (void*)uselocale_wrapper},
    {"uselocale",               (void*)uselocale_wrapper},
    {"__strtod_l",              (void*)strtod_l_wrapper},
    {"strtod_l",                (void*)strtod_l_wrapper},
    {"__strtof_l",              (void*)strtof_l_wrapper},
    {"strtof_l",                (void*)strtof_l_wrapper},
    {"__strtold_l",             (void*)strtold_l_wrapper},
    {"strtold_l",               (void*)strtold_l_wrapper},
    {"__strcoll_l",             (void*)strcoll_l_wrapper},
    {"strcoll_l",               (void*)strcoll_l_wrapper},
    {"__strxfrm_l",             (void*)strxfrm_l_wrapper},
    {"strxfrm_l",               (void*)strxfrm_l_wrapper},
    {"__wcscoll_l",             (void*)wcscoll_l_wrapper},
    {"wcscoll_l",               (void*)wcscoll_l_wrapper},
    {"__wcsxfrm_l",             (void*)wcsxfrm_l_wrapper},
    {"wcsxfrm_l",               (void*)wcsxfrm_l_wrapper},
    {"__towlower_l",            (void*)towlower_l_wrapper},
    {"towlower_l",              (void*)towlower_l_wrapper},
    {"__towupper_l",            (void*)towupper_l_wrapper},
    {"towupper_l",              (void*)towupper_l_wrapper},
    {"__wctype_l",              (void*)wctype_l_wrapper},
    {"wctype_l",                (void*)wctype_l_wrapper},
    {"__iswctype_l",            (void*)iswctype_l_wrapper},
    {"iswctype_l",              (void*)iswctype_l_wrapper},
    
    /* ================================================================
     * ctype _l functions (character classification with locale)
     * ================================================================ */
    {"__isalpha_l",             (void*)isalpha_l_wrapper},
    {"isalpha_l",               (void*)isalpha_l_wrapper},
    {"__isdigit_l",             (void*)isdigit_l_wrapper},
    {"isdigit_l",               (void*)isdigit_l_wrapper},
    {"__isalnum_l",             (void*)isalnum_l_wrapper},
    {"isalnum_l",               (void*)isalnum_l_wrapper},
    {"__isspace_l",             (void*)isspace_l_wrapper},
    {"isspace_l",               (void*)isspace_l_wrapper},
    {"__isupper_l",             (void*)isupper_l_wrapper},
    {"isupper_l",               (void*)isupper_l_wrapper},
    {"__islower_l",             (void*)islower_l_wrapper},
    {"islower_l",               (void*)islower_l_wrapper},
    {"__isprint_l",             (void*)isprint_l_wrapper},
    {"isprint_l",               (void*)isprint_l_wrapper},
    {"__ispunct_l",             (void*)ispunct_l_wrapper},
    {"ispunct_l",               (void*)ispunct_l_wrapper},
    {"__isgraph_l",             (void*)isgraph_l_wrapper},
    {"isgraph_l",               (void*)isgraph_l_wrapper},
    {"__iscntrl_l",             (void*)iscntrl_l_wrapper},
    {"iscntrl_l",               (void*)iscntrl_l_wrapper},
    {"__isxdigit_l",            (void*)isxdigit_l_wrapper},
    {"isxdigit_l",              (void*)isxdigit_l_wrapper},
    {"__isblank_l",             (void*)isblank_l_wrapper},
    {"isblank_l",               (void*)isblank_l_wrapper},
    {"__tolower_l",             (void*)tolower_l_wrapper},
    {"tolower_l",               (void*)tolower_l_wrapper},
    {"__toupper_l",             (void*)toupper_l_wrapper},
    {"toupper_l",               (void*)toupper_l_wrapper},
    
    /* ================================================================
     * wctype _l functions (wide character classification with locale)
     * ================================================================ */
    {"__iswalpha_l",            (void*)iswalpha_l_wrapper},
    {"iswalpha_l",              (void*)iswalpha_l_wrapper},
    {"__iswdigit_l",            (void*)iswdigit_l_wrapper},
    {"iswdigit_l",              (void*)iswdigit_l_wrapper},
    {"__iswspace_l",            (void*)iswspace_l_wrapper},
    {"iswspace_l",              (void*)iswspace_l_wrapper},
    {"__iswupper_l",            (void*)iswupper_l_wrapper},
    {"iswupper_l",              (void*)iswupper_l_wrapper},
    {"__iswlower_l",            (void*)iswlower_l_wrapper},
    {"iswlower_l",              (void*)iswlower_l_wrapper},
    {"__iswprint_l",            (void*)iswprint_l_wrapper},
    {"iswprint_l",              (void*)iswprint_l_wrapper},
    
    {"__strftime_l",            (void*)strftime_l_wrapper},
    {"strftime_l",              (void*)strftime_l_wrapper},
    {"__wcsftime_l",            (void*)wcsftime_l_wrapper},
    {"wcsftime_l",              (void*)wcsftime_l_wrapper},
    {"__nl_langinfo_l",         (void*)nl_langinfo_l_wrapper},
    {"nl_langinfo_l",           (void*)nl_langinfo_l_wrapper},
    {"nl_langinfo",             (void*)nl_langinfo_wrapper},
    
    /* ================================================================
     * strerror with locale
     * ================================================================ */
    {"__strerror_l",            (void*)strerror_l_wrapper},
    {"strerror_l",              (void*)strerror_l_wrapper},
    {"__xpg_strerror_r",        (void*)__xpg_strerror_r_wrapper},
    {"strerror_r",              (void*)strerror_r_wrapper},
    
    /* ================================================================
     * sysconf wrapper (glibc/bionic constant translation)
     * ================================================================ */
    {"sysconf",                 (void*)sysconf_wrapper},
    
    /* ================================================================
     * getsid wrapper
     * ================================================================ */
    {"getsid",                  (void*)getsid_wrapper},
    
    /* ================================================================
     * socket wrapper (errno sync + fake root fallback)
     * ================================================================ */
    {"socket",                  (void*)socket_wrapper},
    {"setsockopt",              (void*)setsockopt_wrapper},  /* Uses proot bypass */
    {"getsockopt",              (void*)getsockopt_wrapper},  /* Uses proot bypass */
    
    /* ================================================================
     * signal wrappers (fix for Android forked process)
     * ================================================================ */
    {"signal",                  (void*)signal_wrapper},
    
    /* ================================================================
     * strtol/strtoul with locale (int base variants)
     * ================================================================ */
    {"__strtol_l",              (void*)strtol_l_wrapper},
    {"strtol_l",                (void*)strtol_l_wrapper},
    {"__strtoll_l",             (void*)strtoll_l_wrapper},
    {"strtoll_l",               (void*)strtoll_l_wrapper},
    {"__strtoul_l",             (void*)strtoul_l_wrapper},
    {"strtoul_l",               (void*)strtoul_l_wrapper},
    {"__strtoull_l",            (void*)strtoull_l_wrapper},
    {"strtoull_l",              (void*)strtoull_l_wrapper},
    
    /* ================================================================
     * iconv - Character set conversion (proot bypass)
     * ================================================================ */
    {"iconv_open",              (void*)iconv_open_wrapper},
    {"iconv",                   (void*)iconv_wrapper},
    {"iconv_close",             (void*)iconv_close_wrapper},
    
    /* ================================================================
     * getopt - Command line parsing (proot bypass)
     * ================================================================ */
    {"getopt",                  (void*)getopt_wrapper},
    {"getopt_long",             NULL},  /* TODO: Add wrapper */
    {"getopt_long_only",        NULL},  /* TODO: Add wrapper */
    
    /* ================================================================
     * FORTIFY functions (_chk suffix)
     * ================================================================ */
    {"__wmemset_chk",           (void*)wmemset_chk_wrapper},
    {"__wmemcpy_chk",           (void*)wmemcpy_chk_wrapper},
    {"__wmemmove_chk",          (void*)wmemmove_chk_wrapper},
    {"__mbsnrtowcs_chk",        (void*)mbsnrtowcs_chk_wrapper},
    {"__mbsrtowcs_chk",         (void*)mbsrtowcs_chk_wrapper},
    {"__fprintf_chk",           (void*)fprintf_chk_wrapper},
    {"__sprintf_chk",           (void*)sprintf_chk_wrapper},
    {"__snprintf_chk",          (void*)snprintf_chk_wrapper},
    
    /* ================================================================
     * C99 format functions (glibc-specific names)
     * ================================================================ */
    /* All scanf variants need proper varargs handling */
    {"__isoc99_sscanf",         (void*)__isoc99_sscanf_wrapper},
    {"__isoc99_scanf",          (void*)__isoc99_scanf_wrapper},
    {"__isoc99_fscanf",         (void*)__isoc99_fscanf_wrapper},
    {"__isoc99_vsscanf",        (void*)__isoc99_vsscanf_wrapper},
    {"__isoc99_vscanf",         (void*)__isoc99_vscanf_wrapper},
    {"__isoc99_vfscanf",        (void*)__isoc99_vfscanf_wrapper},
    {"__isoc23_sscanf",         (void*)__isoc99_sscanf_wrapper},
    {"__isoc23_scanf",          (void*)__isoc99_scanf_wrapper},
    {"__isoc23_fscanf",         (void*)__isoc99_fscanf_wrapper},
    
    /* Standard scanf family (non-prefixed versions)
     * sscanf: iFppV - varargs handling (str, format, + varargs via registers) */
    {"sscanf",                  (void*)__isoc99_sscanf_wrapper},
    {"scanf",                   (void*)__isoc99_scanf_wrapper},
    {"fscanf",                  (void*)__isoc99_fscanf_wrapper},
    {"vsscanf",                 (void*)__isoc99_vsscanf_wrapper},
    {"vscanf",                  (void*)__isoc99_vscanf_wrapper},
    {"vfscanf",                 (void*)__isoc99_vfscanf_wrapper},
    
    /* I/O multiplexing - select=iFipppp, pselect=iFippppp */
    {"select",                  (void*)select_wrapper},
    {"pselect",                 (void*)pselect_wrapper},
    
    /* ================================================================
     * gettext (internationalization)
     * ================================================================ */
    {"gettext",                 (void*)gettext_wrapper},
    {"dgettext",                (void*)dgettext_wrapper},
    {"dcgettext",               (void*)dcgettext_wrapper},
    {"ngettext",                (void*)ngettext_wrapper},
    {"bindtextdomain",          (void*)bindtextdomain_wrapper},
    {"bind_textdomain_codeset", (void*)bind_textdomain_codeset_wrapper},
    {"textdomain",              (void*)textdomain_wrapper},
    
    /* ================================================================
     * Other glibc-specific
     * ================================================================ */
    {"__assert_fail",           (void*)assert_fail_wrapper},
    {"__getauxval",             (void*)getauxval_internal_wrapper},
    {"getauxval",               (void*)getauxval_internal_wrapper},
    {"__pthread_key_create",    (void*)pthread_key_create_wrapper},
    
    /* ================================================================
     * ITM (Transactional Memory) - weak, can be NULL
     * ================================================================ */
    {"_ITM_addUserCommitAction", NULL},
    {"_ITM_memcpyRtWn",         NULL},
    {"_ITM_memcpyRnWt",         NULL},
    {"_ITM_RU1",                NULL},
    {"_ITM_RU8",                NULL},
    
    /* ================================================================
     * GCJ/Java interop (legacy, not used in modern systems)
     * ================================================================ */
    {"_Jv_RegisterClasses",     (void*)_Jv_RegisterClasses_stub},
    
    /* ================================================================
     * Profiling (duplicate removed - defined earlier with stub)
     * ================================================================ */
    
    /* ================================================================
     * Dynamic linker internals
     * ================================================================ */
    {"_dl_find_object",         (void*)dl_find_object_wrapper},
    {"dl_iterate_phdr",         (void*)dl_iterate_phdr_wrapper},
    
    /* ================================================================
     * glibc global data
     * ================================================================ */
    {"__libc_single_threaded",  (void*)glibc_bridge_get_libc_single_threaded},
    
    /* ================================================================
     * C23 functions (glibc 2.38+) - redirect to standard versions
     * ================================================================ */
    {"__isoc23_strtol",         (void*)strtol},
    {"__isoc23_strtoul",        (void*)strtoul},
    {"__isoc23_strtoll",        (void*)strtoll},
    {"__isoc23_strtoull",       (void*)isoc23_strtoull_wrapper},
    {"__isoc23_strtoimax",      (void*)strtoimax},
    {"__isoc23_strtoumax",      (void*)strtoumax},
    
    {"strtoull",                (void*)strtoull_wrapper},
    {"__isoc23_wcstol",         (void*)wcstol},
    {"__isoc23_wcstoul",        (void*)wcstoul},
    {"__isoc23_wcstoll",        (void*)wcstoll},
    {"__isoc23_wcstoull",       (void*)wcstoull},
    
    /* ================================================================
     * FORTIFY printf family (wrapper_fortify.c)
     * ================================================================ */
    {"__printf_chk",            (void*)printf_chk_wrapper},
    {"__vprintf_chk",           (void*)vprintf_chk_wrapper},
    {"__vfprintf_chk",          (void*)vfprintf_chk_wrapper},
    {"__vsprintf_chk",          (void*)vsprintf_chk_wrapper},
    {"__vsnprintf_chk",         (void*)vsnprintf_chk_wrapper},
    {"__vdprintf_chk",          (void*)vdprintf_chk_wrapper},
    {"__vfwprintf_chk",         (void*)vfwprintf_chk_wrapper},
    {"__vsyslog_chk",           (void*)vsyslog_chk_wrapper},
    {"__syslog_chk",            (void*)syslog_chk_wrapper},
    {"__fdelt_chk",             (void*)fdelt_chk_wrapper},
    {"__open64_2",              (void*)open64_2_wrapper},
    
    /* ================================================================
     * Math extensions (wrapper_math_ext.c)
     * ================================================================ */
    {"exp10",                   (void*)exp10_wrapper},
    {"exp10f",                  (void*)exp10f_wrapper},
    {"exp10l",                  (void*)exp10l_wrapper},
    {"pow10",                   (void*)pow10_wrapper},
    {"pow10f",                  (void*)pow10f_wrapper},
    {"pow10l",                  (void*)pow10l_wrapper},
    
    /* ================================================================
     * ucontext (wrapper_ucontext.c) - stubs
     * ================================================================ */
    {"getcontext",              (void*)getcontext_wrapper},
    {"setcontext",              (void*)setcontext_wrapper},
    {"swapcontext",             (void*)swapcontext_wrapper},
    {"makecontext",             (void*)makecontext_wrapper},
    {"__sigsetjmp",             (void*)sigsetjmp_wrapper},
    
    /* ================================================================
     * pthread extensions (wrapper_pthread_ext.c)
     * ================================================================ */
    {"pthread_setattr_default_np", (void*)pthread_setattr_default_np_wrapper},
    {"pthread_getattr_default_np", (void*)pthread_getattr_default_np_wrapper},
    {"pthread_attr_setaffinity_np", (void*)pthread_attr_setaffinity_np_wrapper},
    {"pthread_attr_getaffinity_np", (void*)pthread_attr_getaffinity_np_wrapper},
    {"_pthread_cleanup_push",   (void*)pthread_cleanup_push_wrapper},
    {"_pthread_cleanup_pop",    (void*)pthread_cleanup_pop_wrapper},

    /* ================================================================
     * obstack (wrapper_obstack.c)
     * NOTE: obstack_alloc_failed_handler is a DATA symbol (global variable),
     * not a function. box64 directly writes to it. We export the address
     * of our variable using extern declaration.
     * ================================================================ */
    {"obstack_alloc_failed_handler", (void*)&obstack_alloc_failed_handler},
    {"_obstack_begin",          (void*)obstack_begin_wrapper},
    {"_obstack_begin_1",        (void*)obstack_begin_1_wrapper},
    {"_obstack_free",           (void*)obstack_free_wrapper},
    {"obstack_vprintf",         (void*)obstack_vprintf_wrapper},
    {"obstack_printf",          (void*)obstack_printf_wrapper},
    {"__obstack_vprintf_chk",   (void*)obstack_vprintf_chk_wrapper},
    
    /* ================================================================
     * sysinfo (wrapper_sysinfo.c)
     * ================================================================ */
    {"__sysconf",               (void*)sysconf_internal_wrapper},
    {"getcpu",                  (void*)getcpu_wrapper},
    {"malloc_trim",             (void*)malloc_trim_wrapper},
    {"__libc_malloc",           (void*)libc_malloc_wrapper},
    {"__libc_calloc",           (void*)libc_calloc_wrapper},
    {"__libc_realloc",          (void*)libc_realloc_wrapper},
    {"__libc_free",             (void*)libc_free_wrapper},
    {"shm_unlink",              (void*)shm_unlink_wrapper},
    {"dlinfo",                  (void*)dlinfo_wrapper},
    {"fts64_open",              (void*)fts64_open_wrapper},
    {"fts64_read",              (void*)fts64_read_wrapper},
    {"fts64_close",             (void*)fts64_close_wrapper},
    {"globfree64",              (void*)globfree64_wrapper},
    {"getprotobyname_r",        (void*)getprotobyname_r_wrapper},
    {"__isoc99_vwscanf",        (void*)isoc99_vwscanf_wrapper},
    {"__isoc99_vswscanf",       (void*)isoc99_vswscanf_wrapper},
    {"__isoc99_vfwscanf",       (void*)isoc99_vfwscanf_wrapper},
    {"shm_open",                (void*)shm_open_wrapper},
    {"__libc_memalign",         (void*)libc_memalign_wrapper},
    {"__res_state",             (void*)res_state_wrapper},
    {"getprotobynumber_r",      (void*)getprotobynumber_r_wrapper},
    {"glob64",                  (void*)glob64_wrapper},
    {"fnmatch",                 NULL},  /* Direct bionic - compatible */
    
    /* ================================================================
     * User/Group info - direct bionic (compatible struct passwd/group)
     * ================================================================ */
    {"getpwuid",                NULL},
    {"getpwuid_r",              NULL},
    {"getpwnam",                NULL},
    {"getpwnam_r",              NULL},
    {"getpwent",                NULL},
    {"setpwent",                NULL},
    {"endpwent",                NULL},
    {"getgrgid",                NULL},
    {"getgrgid_r",              NULL},
    {"getgrnam",                NULL},
    {"getgrnam_r",              NULL},
    {"getgrent",                NULL},
    {"setgrent",                NULL},
    {"endgrent",                NULL},
    {"getgrouplist",            NULL},
    {"getgroups",               NULL},
    
    /* ================================================================
     * FORTIFY additions
     * ================================================================ */
    {"__vasprintf_chk",         (void*)vasprintf_chk_wrapper},
    {"__vswprintf_chk",         (void*)vswprintf_chk_wrapper},
    {"__vwprintf_chk",          (void*)vwprintf_chk_wrapper},
    {"__longjmp_chk",           (void*)longjmp_chk_wrapper},
    
    /* Wide string fortify functions */
    {"__swprintf_chk",          (void*)swprintf_chk_wrapper},
    {"__wcscat_chk",            (void*)wcscat_chk_wrapper},
    {"__wcscpy_chk",            (void*)wcscpy_chk_wrapper},
    {"__wcsncat_chk",           (void*)wcsncat_chk_wrapper},
    {"__wcsncpy_chk",           (void*)wcsncpy_chk_wrapper},
    
    /* String fortify functions */
    {"__asprintf_chk",          (void*)asprintf_chk_wrapper},
    {"__realpath_chk",          (void*)realpath_chk_wrapper},
    {"__stpcpy_chk",            (void*)stpcpy_chk_wrapper},
    {"__stpncpy_chk",           (void*)stpncpy_chk_wrapper},
    
    /* ================================================================
     * pthread mutex extensions
     * ================================================================ */
    {"pthread_mutexattr_setrobust",     (void*)pthread_mutexattr_setrobust_wrapper},
    {"pthread_mutexattr_getrobust",     (void*)pthread_mutexattr_getrobust_wrapper},
    {"pthread_mutexattr_setprioceiling",(void*)pthread_mutexattr_setprioceiling_wrapper},
    {"pthread_mutexattr_getprioceiling",(void*)pthread_mutexattr_getprioceiling_wrapper},
    {"pthread_mutex_consistent",       (void*)pthread_mutex_consistent_wrapper},
    {"__pthread_register_cancel",       (void*)pthread_register_cancel_wrapper},
    {"__pthread_unregister_cancel",     (void*)pthread_unregister_cancel_wrapper},
    {"__pthread_unwind_next",           (void*)pthread_unwind_next_wrapper},
    
    /* ================================================================
     * obstack additions
     * ================================================================ */
    {"obstack_free",            (void*)obstack_free_direct_wrapper},
    {"_obstack_newchunk",       (void*)obstack_newchunk_wrapper},
    
    /* ================================================================
     * stdio extensions (wrapper_stdio_ext.c)
     * ================================================================ */
    {"fopencookie",             (void*)fopencookie_wrapper},
    
    /* ================================================================
     * libcrypt - Password encryption
     * ================================================================ */
    {"crypt",                   (void*)crypt_wrapper},
    {"crypt_r",                 (void*)crypt_r_wrapper},
    
    /* ================================================================
     * POSIX Message Queue (mqueue) functions
     * ================================================================ */
    {"mq_open",                 (void*)mq_open_wrapper},
    {"mq_close",                (void*)mq_close_wrapper},
    {"mq_unlink",               (void*)mq_unlink_wrapper},
    {"mq_send",                 (void*)mq_send_wrapper},
    {"mq_receive",              (void*)mq_receive_wrapper},
    {"mq_getattr",              (void*)mq_getattr_wrapper},
    {"mq_setattr",              (void*)mq_setattr_wrapper},
    
    /* ================================================================
     * POSIX AIO (Asynchronous I/O) functions
     * ================================================================ */
    {"aio_read",                (void*)aio_read_wrapper},
    {"aio_read64",              (void*)aio_read_wrapper},    /* 64-bit alias */
    {"aio_write",               (void*)aio_write_wrapper},
    {"aio_write64",             (void*)aio_write_wrapper},   /* 64-bit alias */
    {"aio_error",               (void*)aio_error_wrapper},
    {"aio_error64",             (void*)aio_error_wrapper},   /* 64-bit alias */
    {"aio_return",              (void*)aio_return_wrapper},
    {"aio_return64",            (void*)aio_return_wrapper},  /* 64-bit alias */
    {"aio_suspend",             (void*)aio_suspend_wrapper},
    {"aio_suspend64",           (void*)aio_suspend_wrapper}, /* 64-bit alias */
    {"aio_cancel",              (void*)aio_cancel_wrapper},
    {"aio_cancel64",            (void*)aio_cancel_wrapper},  /* 64-bit alias */
    {"aio_fsync",               (void*)aio_fsync_wrapper},
    {"aio_fsync64",             (void*)aio_fsync_wrapper},   /* 64-bit alias */
    {"lio_listio",              (void*)lio_listio_wrapper},
    {"lio_listio64",            (void*)lio_listio_wrapper},  /* 64-bit alias */
    
    /* ================================================================
     * System V IPC (memory-based implementation)
     * ================================================================ */
    {"shmget",                  (void*)shmget_wrapper},
    {"shmat",                   (void*)shmat_wrapper},
    {"shmdt",                   (void*)shmdt_wrapper},
    {"shmctl",                  (void*)shmctl_wrapper},
    {"semget",                  (void*)semget_wrapper},
    {"semop",                   (void*)semop_wrapper},
    {"semctl",                  (void*)semctl_wrapper},
    {"msgget",                  (void*)msgget_wrapper},
    {"msgsnd",                  (void*)msgsnd_wrapper},
    {"msgrcv",                  (void*)msgrcv_wrapper},
    {"msgctl",                  (void*)msgctl_wrapper},
    
    /* File creation functions */
    {"mkfifo",                  (void*)mkfifo_wrapper},
    {"mknod",                   (void*)mknod_wrapper},
    {"mknodat",                 (void*)mknodat_wrapper},
    
    /* System configuration functions - emulated (not in bionic) */
    {"confstr",                 (void*)confstr_wrapper},    /* size_t confstr(int, char*, size_t) */
    {"pathconf",                NULL},     /* long pathconf(const char*, int) */
    {"fpathconf",               NULL},     /* long fpathconf(int, int) */
    
    /* .NET CoreCLR PAL (Platform Abstraction Layer) stubs */
    {"PAL_RegisterModule",      (void*)PAL_RegisterModule_wrapper},  /* int PAL_RegisterModule(const char*) */
    
    /* Terminal functions - pass through to bionic */
    {"isatty",                  NULL},
    {"ttyname",                 NULL},
    {"ttyname_r",               NULL},
    {"ctermid",                 NULL},
    
    /* Additional string functions - pass through to bionic */
    {"strnlen",                 NULL},
    {"stpcpy",                  NULL},
    {"stpncpy",                 NULL},
    
    /* Math helper functions - pass through to bionic */
    {"frexp",                   NULL},
    {"frexpf",                  NULL},
    {"ldexp",                   NULL},
    {"ldexpf",                  NULL},
    {"modf",                    NULL},
    {"modff",                   NULL},
    
    /* Signal handling functions */
    {"sigprocmask",             (void*)sigprocmask_wrapper},
    {"sigaction",               (void*)sigaction_wrapper},
    {"sigemptyset",             (void*)sigemptyset_wrapper},
    {"sigfillset",              (void*)sigfillset_wrapper},
    {"sigaddset",               (void*)sigaddset_wrapper},
    {"sigdelset",               (void*)sigdelset_wrapper},
    {"sigismember",             (void*)sigismember_wrapper},
    {"kill",                    (void*)kill_wrapper},
    {"raise",                   (void*)raise_wrapper},
    
    {NULL, NULL}  /* Terminator */
};

/* ============================================================================
 * Public API
 * ============================================================================ */

const symbol_wrapper_t* glibc_bridge_get_symbol_table(void) {
    return g_symbol_wrappers;
}

