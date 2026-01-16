/*
 * glibc-bridge - 杂项函数包装
 * 
 * 包含 iconv, getopt, getline, __fsetlocking 等杂项函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdint.h>
#include <errno.h>
#include <ctype.h>
#include <getopt.h>
#include <wchar.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * iconv 实现 (Android bionic 不包含 iconv)
 * ============================================================================ */

#define ICONV_MAGIC 0x49434F4E

typedef struct {
    uint32_t magic;
    char from[32];
    char to[32];
    int passthrough;
} iconv_handle_t;

void* iconv_open_wrapper(const char* tocode, const char* fromcode) {
    LOG_DEBUG("iconv_open_wrapper: %s -> %s", fromcode, tocode);
    
    iconv_handle_t* h = malloc(sizeof(iconv_handle_t));
    if (!h) { 
        errno = ENOMEM; 
        return (void*)-1; 
    }
    
    h->magic = ICONV_MAGIC;
    strncpy(h->from, fromcode, sizeof(h->from) - 1);
    strncpy(h->to, tocode, sizeof(h->to) - 1);
    h->from[sizeof(h->from) - 1] = '\0';
    h->to[sizeof(h->to) - 1] = '\0';
    
    /* 检查是否可以直接传递 */
    h->passthrough = (strcasecmp(fromcode, tocode) == 0 ||
                     (strcasecmp(fromcode, "UTF-8") == 0 && strcasecmp(tocode, "ASCII") == 0) ||
                     (strcasecmp(fromcode, "ASCII") == 0 && strcasecmp(tocode, "UTF-8") == 0));
    
    return h;
}

size_t iconv_wrapper(void* cd, char** inbuf, size_t* inbytesleft,
                     char** outbuf, size_t* outbytesleft) {
    if (!cd || cd == (void*)-1) { 
        errno = EBADF; 
        return (size_t)-1; 
    }
    
    iconv_handle_t* h = (iconv_handle_t*)cd;
    if (h->magic != ICONV_MAGIC) { 
        errno = EBADF; 
        return (size_t)-1; 
    }
    
    if (!inbuf || !*inbuf || !outbuf || !*outbuf) {
        return 0;
    }
    
    /* 简单的字节拷贝转换 */
    size_t copy_len = *inbytesleft < *outbytesleft ? *inbytesleft : *outbytesleft;
    memcpy(*outbuf, *inbuf, copy_len);
    *inbuf += copy_len;
    *inbytesleft -= copy_len;
    *outbuf += copy_len;
    *outbytesleft -= copy_len;
    
    if (*inbytesleft > 0) { 
        errno = E2BIG; 
        return (size_t)-1; 
    }
    
    return 0;
}

int iconv_close_wrapper(void* cd) {
    if (!cd || cd == (void*)-1) return 0;
    
    iconv_handle_t* h = (iconv_handle_t*)cd;
    if (h->magic == ICONV_MAGIC) {
        free(h);
    }
    
    return 0;
}

/* ============================================================================
 * getopt 系列包装
 * ============================================================================ */

int getopt_wrapper(int argc, char* const argv[], const char* optstring) {
    LOG_DEBUG("getopt_wrapper: argc=%d optstring=%s", argc, optstring);
    return getopt(argc, argv, optstring);
}

int getopt_long_wrapper(int argc, char* const argv[], const char* optstring,
                        const void* longopts, int* longindex) {
    LOG_DEBUG("getopt_long_wrapper: argc=%d optstring=%s", argc, optstring);
    return getopt_long(argc, argv, optstring, (const struct option*)longopts, longindex);
}

int getopt_long_only_wrapper(int argc, char* const argv[], const char* optstring,
                             const struct option* longopts, int* longindex) {
    LOG_DEBUG("getopt_long_only_wrapper: argc=%d optstring=%s", argc, optstring);
    return getopt_long_only(argc, argv, optstring, longopts, longindex);
}

/* ============================================================================
 * getline / getdelim 包装
 * ============================================================================ */

ssize_t getdelim_wrapper(char** lineptr, size_t* n, int delim, FILE* stream) {
    return getdelim(lineptr, n, delim, stream);
}

ssize_t getline_wrapper(char** lineptr, size_t* n, FILE* stream) {
    return getline(lineptr, n, stream);
}

/* ============================================================================
 * __fsetlocking - 设置文件流锁定模式
 * glibc 扩展，bionic 不支持
 * ============================================================================ */

#define FSETLOCKING_QUERY    0
#define FSETLOCKING_INTERNAL 1
#define FSETLOCKING_BYCALLER 2

int __fsetlocking_wrapper(FILE* fp, int type) {
    (void)fp;
    
    LOG_DEBUG("__fsetlocking_wrapper: type=%d", type);
    
    /* bionic 不支持此函数，返回默认值 */
    switch (type) {
        case FSETLOCKING_QUERY:
            return FSETLOCKING_INTERNAL;
        case FSETLOCKING_INTERNAL:
        case FSETLOCKING_BYCALLER:
            return FSETLOCKING_INTERNAL;
        default:
            return -1;
    }
}

/* ============================================================================
 * pclose 包装
 * ============================================================================ */

int pclose_wrapper(FILE* stream) {
    LOG_DEBUG("pclose_wrapper: stream=%p", (void*)stream);
    return pclose(stream);
}

/* ============================================================================
 * isoc99 scanf 系列包装
 * 使用固定参数以匹配 box64 期望的 ABI
 * ============================================================================ */

int __isoc99_sscanf_wrapper(const char* str, const char* format, 
                            uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3) {
    /* 通过临时数组传递参数给 sscanf */
    return sscanf(str, format, (void*)a0, (void*)a1, (void*)a2, (void*)a3);
}

int __isoc99_scanf_wrapper(const char* format, 
                           uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4) {
    return scanf(format, (void*)a0, (void*)a1, (void*)a2, (void*)a3, (void*)a4);
}

int __isoc99_fscanf_wrapper(FILE* stream, const char* format, 
                            uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3) {
    return fscanf(stream, format, (void*)a0, (void*)a1, (void*)a2, (void*)a3);
}

int __isoc99_vsscanf_wrapper(const char* str, const char* format, va_list ap) {
    return vsscanf(str, format, ap);
}

int __isoc99_vscanf_wrapper(const char* format, va_list ap) {
    return vscanf(format, ap);
}

int __isoc99_vfscanf_wrapper(FILE* stream, const char* format, va_list ap) {
    return vfscanf(stream, format, ap);
}

/* ============================================================================
 * isgraph 包装
 * ============================================================================ */

int isgraph_wrapper(int c) {
    return isgraph(c);
}

/* ============================================================================
 * crypt 包装 (密码加密)
 * Android 支持有限
 * ============================================================================ */

/* crypt_data 结构 (Android 可能没有完整定义) */
#ifndef _CRYPT_H
struct crypt_data {
    char keysched[16 * 8];
    char sb0[32768];
    char sb1[32768];
    char sb2[32768];
    char sb3[32768];
    char crypt_3_buf[14];
    char current_salt[2];
    long int current_saltbits;
    int  direction;
    int  initialized;
};
#endif

/* bionic 不提供 crypt/crypt_r，提供简单的 stub 实现 */
static __thread char g_crypt_result[128];

char* crypt_wrapper(const char* key, const char* salt) {
    LOG_DEBUG("crypt_wrapper: salt='%s' (stub)", salt);
    (void)key;
    /* 返回一个假的加密结果，格式为 $id$salt$hash */
    if (salt && salt[0] == '$') {
        snprintf(g_crypt_result, sizeof(g_crypt_result), "%s", salt);
    } else {
        snprintf(g_crypt_result, sizeof(g_crypt_result), "%s", salt ? salt : "xx");
    }
    return g_crypt_result;
}

char* crypt_r_wrapper(const char* key, const char* salt, struct crypt_data* data) {
    LOG_DEBUG("crypt_r_wrapper: salt='%s' (stub)", salt);
    (void)key;
    (void)data;
    return crypt_wrapper(key, salt);
}

/* ============================================================================
 * random 系列包装
 * ============================================================================ */

long random_wrapper(void) {
    return random();
}

void srandom_wrapper(unsigned int seed) {
    srandom(seed);
}

char* initstate_wrapper(unsigned int seed, char* state, size_t n) {
    return initstate(seed, state, n);
}

char* setstate_wrapper(char* state) {
    return setstate(state);
}

/* random_r 系列 */
struct random_data_compat {
    int32_t* fptr;
    int32_t* rptr;
    int32_t* state;
    int rand_type;
    int rand_deg;
    int rand_sep;
    int32_t* end_ptr;
};

int random_r_wrapper(struct random_data_compat* buf, int32_t* result) {
    (void)buf;
    *result = random();
    return 0;
}

int srandom_r_wrapper(unsigned int seed, struct random_data_compat* buf) {
    (void)buf;
    srandom(seed);
    return 0;
}

/* ============================================================================
 * getenv / setenv / unsetenv 包装
 * ============================================================================ */

char* getenv_wrapper(const char* name) {
    return getenv(name);
}

int setenv_wrapper(const char* name, const char* value, int overwrite) {
    return setenv(name, value, overwrite);
}

int unsetenv_wrapper(const char* name) {
    return unsetenv(name);
}

int putenv_wrapper(char* string) {
    return putenv(string);
}

int clearenv_wrapper(void) {
    return clearenv();
}

/* ============================================================================
 * basename / dirname 包装
 * ============================================================================ */

#include <libgen.h>

char* basename_wrapper(char* path) {
    return basename(path);
}

char* dirname_wrapper(char* path) {
    return dirname(path);
}

/* ============================================================================
 * secure_getenv - 安全获取环境变量 (glibc 扩展)
 * ============================================================================ */

char* secure_getenv_wrapper(const char* name) {
    /* Android 没有 secure_getenv，回退到 getenv */
    /* 在生产环境中，如果 setuid/setgid 应该返回 NULL */
    return getenv(name);
}

/* ============================================================================
 * popen / pclose 包装
 * ============================================================================ */

FILE* popen_wrapper(const char* command, const char* type) {
    LOG_DEBUG("popen_wrapper: command='%s', type='%s'", command, type);
    return popen(command, type);
}

/* ============================================================================
 * valloc / pvalloc - 页对齐内存分配
 * ============================================================================ */

#include <unistd.h>
#include <malloc.h>

void* valloc_wrapper(size_t size) {
    return memalign((size_t)sysconf(_SC_PAGESIZE), size);
}

void* pvalloc_wrapper(size_t size) {
    size_t pagesize = (size_t)sysconf(_SC_PAGESIZE);
    /* 向上取整到页大小 */
    size_t aligned_size = (size + pagesize - 1) & ~(pagesize - 1);
    if (aligned_size < size) {
        /* 溢出 */
        errno = ENOMEM;
        return NULL;
    }
    return memalign(pagesize, aligned_size);
}

/* ============================================================================
 * __h_errno_location - 获取 h_errno 地址
 * ============================================================================ */

#include <netdb.h>

static __thread int g_h_errno = 0;

int* __h_errno_location_wrapper(void) {
    return &g_h_errno;
}

/* ============================================================================
 * 栈保护函数
 * ============================================================================ */

void __stack_chk_fail_wrapper(void) {
    LOG_DEBUG("__stack_chk_fail_wrapper: Stack smashing detected!");
    abort();
}

/* ============================================================================
 * FORTIFY 检查函数
 * ============================================================================ */

void __explicit_bzero_chk_wrapper(void* dest, size_t len, size_t destlen) {
    if (len > destlen) {
        LOG_DEBUG("explicit_bzero_chk: buffer overflow detected");
        abort();
    }
    memset(dest, 0, len);
    __asm__ __volatile__("" : : "r"(dest) : "memory");
}

size_t __mbstowcs_chk_wrapper(wchar_t* dest, const char* src, size_t len, size_t destlen) {
    if (len > destlen) {
        LOG_DEBUG("mbstowcs_chk: buffer overflow detected");
        abort();
    }
    return mbstowcs(dest, src, len);
}

size_t __wcstombs_chk_wrapper(char* dest, const wchar_t* src, size_t len, size_t destlen) {
    if (len > destlen) {
        LOG_DEBUG("wcstombs_chk: buffer overflow detected");
        abort();
    }
    return wcstombs(dest, src, len);
}

void* __memcpy_chk_wrapper(void* dest, const void* src, size_t len, size_t destlen) {
    if (len > destlen) {
        LOG_DEBUG("memcpy_chk: buffer overflow detected");
        abort();
    }
    return memcpy(dest, src, len);
}

void* __memmove_chk_wrapper(void* dest, const void* src, size_t len, size_t destlen) {
    if (len > destlen) {
        LOG_DEBUG("memmove_chk: buffer overflow detected");
        abort();
    }
    return memmove(dest, src, len);
}

void* __memset_chk_wrapper(void* dest, int c, size_t len, size_t destlen) {
    if (len > destlen) {
        LOG_DEBUG("memset_chk: buffer overflow detected");
        abort();
    }
    return memset(dest, c, len);
}

char* __strcpy_chk_wrapper(char* dest, const char* src, size_t destlen) {
    size_t srclen = strlen(src) + 1;
    if (srclen > destlen) {
        LOG_DEBUG("strcpy_chk: buffer overflow detected");
        abort();
    }
    return strcpy(dest, src);
}

char* __strncpy_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen) {
    if (n > destlen) {
        LOG_DEBUG("strncpy_chk: buffer overflow detected");
        abort();
    }
    return strncpy(dest, src, n);
}

char* __strcat_chk_wrapper(char* dest, const char* src, size_t destlen) {
    size_t destsize = strlen(dest);
    size_t srcsize = strlen(src);
    if (destsize + srcsize + 1 > destlen) {
        LOG_DEBUG("strcat_chk: buffer overflow detected");
        abort();
    }
    return strcat(dest, src);
}

char* __strncat_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen) {
    size_t destsize = strlen(dest);
    size_t srcsize = strnlen(src, n);
    if (destsize + srcsize + 1 > destlen) {
        LOG_DEBUG("strncat_chk: buffer overflow detected");
        abort();
    }
    return strncat(dest, src, n);
}

/* ============================================================================
 * readlinkat_chk - 检查版本的 readlinkat
 * ============================================================================ */

ssize_t __readlinkat_chk_wrapper(int dirfd, const char* path, char* buf, 
                                  size_t len, size_t buflen) {
    if (len > buflen) {
        LOG_DEBUG("readlinkat_chk: buffer overflow detected");
        abort();
    }
    return readlinkat(dirfd, path, buf, len);
}

/* ============================================================================
 * openat64 包装
 * ============================================================================ */

#include <fcntl.h>

int __openat64_2_wrapper(int dirfd, const char* pathname, int flags) {
    /* _2 版本在没有提供 O_CREAT 时不需要 mode */
    return openat(dirfd, pathname, flags);
}

/* ============================================================================
 * strerrorname_np / strerrordesc_np - GNU 扩展
 * ============================================================================ */

const char* strerrorname_np_wrapper(int errnum) {
    /* 返回 errno 名称 (如 "EINVAL") */
    static __thread char buf[32];
    snprintf(buf, sizeof(buf), "E%d", errnum);
    return buf;
}

const char* strerrordesc_np_wrapper(int errnum) {
    /* 返回错误描述 */
    return strerror(errnum);
}

/* ============================================================================
 * get_current_dir_name - GNU 扩展
 * ============================================================================ */

char* get_current_dir_name_wrapper(void) {
    /* 分配内存并返回当前目录 */
    size_t size = 256;
    char* buf = malloc(size);
    if (!buf) return NULL;
    
    while (getcwd(buf, size) == NULL) {
        if (errno != ERANGE) {
            free(buf);
            return NULL;
        }
        size *= 2;
        char* newbuf = realloc(buf, size);
        if (!newbuf) {
            free(buf);
            return NULL;
        }
        buf = newbuf;
    }
    return buf;
}

/* ============================================================================
 * strtoull 包装
 * ============================================================================ */

unsigned long long strtoull_wrapper(const char* nptr, char** endptr, int base) {
    return strtoull(nptr, endptr, base);
}

unsigned long long isoc23_strtoull_wrapper(const char* nptr, char** endptr, int base) {
    /* ISO C23 版本与标准版本相同 */
    return strtoull(nptr, endptr, base);
}
