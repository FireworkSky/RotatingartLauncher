/*
 * glibc-bridge - 字符串处理函数包装
 * 
 * 包含 vsnprintf, snprintf, strverscmp, wordexp, parse_printf_format 等字符串相关函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdint.h>
#include <errno.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

#ifdef __ANDROID__
#include <android/log.h>
#endif

/* ============================================================================
 * vsnprintf 包装 - 修复 .NET hostpolicy MTE 指针截断问题
 * 
 * 问题: .NET hostpolicy 格式化指针时使用 18 字节缓冲区:
 *   pal::char_t buffer[STRING_LENGTH("0xffffffffffffffff")];  // = 18
 *   pal::snwprintf(buffer, ARRAY_SIZE(buffer), "0x%zx", (size_t)ptr);
 * 
 * 在启用 MTE 的 Android ARM64 上, 0xb4000076b9e9d7d0 等指针需要 19 字符
 * (18 十六进制字符 + null), 导致截断为 "0xb4000076b9e9d7d"
 * 
 * 解决方案: 检测小缓冲区的指针格式化并修复输出
 * ============================================================================ */

/* 全局存储最后格式化的 MTE 指针 (供 strtoull 使用) */
__thread unsigned long long g_last_mte_pointer = 0;
__thread char g_last_mte_string[64] = {0};

/**
 * 检查格式字符串是否包含指针格式说明符
 */
static int format_has_pointer_spec(const char* fmt) {
    if (!fmt) return 0;
    while (*fmt) {
        if (*fmt == '%') {
            fmt++;
            /* 跳过标志 */
            while (*fmt == '-' || *fmt == '+' || *fmt == ' ' || 
                   *fmt == '#' || *fmt == '0') fmt++;
            /* 跳过宽度 */
            while (*fmt >= '0' && *fmt <= '9') fmt++;
            /* 跳过精度 */
            if (*fmt == '.') {
                fmt++;
                while (*fmt >= '0' && *fmt <= '9') fmt++;
            }
            /* 检查长度修饰符和说明符 */
            if (*fmt == 'z' || *fmt == 'l') {
                fmt++;
                if (*fmt == 'l') fmt++;  /* ll */
                if (*fmt == 'x' || *fmt == 'X') return 1;
            } else if (*fmt == 'p') {
                return 1;
            }
        }
        if (*fmt) fmt++;
    }
    return 0;
}

/**
 * vsnprintf 包装，带 MTE 指针截断修复
 */
int vsnprintf_wrapper(char* str, size_t size, const char* format, va_list ap) {
#ifdef __ANDROID__
    if (format && size > 0 && size <= 32) {
        __android_log_print(ANDROID_LOG_WARN, "glibc-bridge",
            "[vsnprintf] 调用: size=%zu fmt='%.40s'", size, format);
    }
#endif
    
    if (!str || size == 0 || !format) {
        return vsnprintf(str, size, format, ap);
    }
    
    /* 检查是否是格式化指针到小缓冲区 */
    int is_ptr_fmt = format_has_pointer_spec(format);
    
    if (is_ptr_fmt && size >= 15 && size <= 20) {
        /* 使用更大的临时缓冲区 */
        char temp[64];
        int result = vsnprintf(temp, sizeof(temp), format, ap);
        
        if (result > 0 && (size_t)result >= size - 1) {
            /* 输出被截断，存储完整指针以便 strtoull 稍后恢复 */
            if (temp[0] == '0' && (temp[1] == 'x' || temp[1] == 'X')) {
                strncpy(g_last_mte_string, temp, sizeof(g_last_mte_string) - 1);
                g_last_mte_pointer = strtoull(temp + 2, NULL, 16);
            }
        }
        
        /* 复制到用户缓冲区（可能被截断） */
        strncpy(str, temp, size - 1);
        str[size - 1] = '\0';
        return result;
    }
    
    return vsnprintf(str, size, format, ap);
}

/**
 * snprintf 包装
 */
int snprintf_wrapper(char* str, size_t size, const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    int result = vsnprintf_wrapper(str, size, format, ap);
    va_end(ap);
    return result;
}

/* ============================================================================
 * 字符串转数字函数
 * ============================================================================ */

double strtof64_wrapper(const char *nptr, char **endptr) {
    return strtod(nptr, endptr);
}

int strfromf64_wrapper(char *str, size_t n, const char *format, double fp) {
    return snprintf(str, n, format, fp);
}

/* ============================================================================
 * strverscmp - 版本字符串比较
 * glibc 特有函数，bionic 没有，需要自己实现
 * ============================================================================ */

int strverscmp_wrapper(const char* s1, const char* s2) {
    if (!s1 || !s2) {
        if (!s1 && !s2) return 0;
        return s1 ? 1 : -1;
    }
    
    while (*s1 && *s2) {
        /* 跳过前导零 */
        while (*s1 == '0' && *(s1+1) >= '0' && *(s1+1) <= '9') s1++;
        while (*s2 == '0' && *(s2+1) >= '0' && *(s2+1) <= '9') s2++;
        
        /* 检查是否两者都是数字 */
        int is_digit1 = (*s1 >= '0' && *s1 <= '9');
        int is_digit2 = (*s2 >= '0' && *s2 <= '9');
        
        if (is_digit1 && is_digit2) {
            /* 数字比较 */
            const char* num_start1 = s1;
            const char* num_start2 = s2;
            
            while (*s1 >= '0' && *s1 <= '9') s1++;
            while (*s2 >= '0' && *s2 <= '9') s2++;
            
            int len1 = (int)(s1 - num_start1);
            int len2 = (int)(s2 - num_start2);
            
            /* 长度不同，数字大的更大 */
            if (len1 != len2) return len1 - len2;
            
            /* 长度相同，逐字符比较 */
            int cmp = memcmp(num_start1, num_start2, len1);
            if (cmp != 0) return cmp;
        } else if (is_digit1) {
            return 1;  /* 数字大于非数字 */
        } else if (is_digit2) {
            return -1;
        } else {
            /* 字符比较 */
            if (*s1 != *s2) return (unsigned char)*s1 - (unsigned char)*s2;
            s1++;
            s2++;
        }
    }
    
    return (unsigned char)*s1 - (unsigned char)*s2;
}

/* ============================================================================
 * wordexp - 单词展开
 * glibc 特有，Android 不支持，提供简单实现
 * ============================================================================ */

/* wordexp 结构 */
typedef struct {
    size_t we_wordc;     /* 单词计数 */
    char** we_wordv;     /* 单词数组 */
    size_t we_offs;      /* 偏移量 */
} wordexp_internal_t;

int wordexp_wrapper(const char* words, void* pwordexp, int flags) {
    (void)flags;
    
    wordexp_internal_t* we = (wordexp_internal_t*)pwordexp;
    if (!words || !we) {
        return 1;  /* WRDE_NOSPACE */
    }
    
    LOG_DEBUG("wordexp_wrapper: words='%s'", words);
    
    /* 简单实现：不做展开，只分词 */
    we->we_wordc = 0;
    we->we_wordv = NULL;
    we->we_offs = 0;
    
    /* 计算单词数 */
    const char* p = words;
    int word_count = 0;
    int in_word = 0;
    
    while (*p) {
        if (*p == ' ' || *p == '\t' || *p == '\n') {
            in_word = 0;
        } else if (!in_word) {
            in_word = 1;
            word_count++;
        }
        p++;
    }
    
    if (word_count == 0) {
        return 0;
    }
    
    /* 分配数组 */
    we->we_wordv = calloc(word_count + 1, sizeof(char*));
    if (!we->we_wordv) {
        return 1;  /* WRDE_NOSPACE */
    }
    
    /* 分词 */
    p = words;
    int idx = 0;
    while (*p && idx < word_count) {
        /* 跳过空白 */
        while (*p == ' ' || *p == '\t' || *p == '\n') p++;
        if (!*p) break;
        
        /* 找到单词开始 */
        const char* word_start = p;
        while (*p && *p != ' ' && *p != '\t' && *p != '\n') p++;
        
        /* 复制单词 */
        size_t word_len = p - word_start;
        we->we_wordv[idx] = malloc(word_len + 1);
        if (!we->we_wordv[idx]) {
            /* 清理 */
            for (int i = 0; i < idx; i++) free(we->we_wordv[i]);
            free(we->we_wordv);
            we->we_wordv = NULL;
            return 1;
        }
        memcpy(we->we_wordv[idx], word_start, word_len);
        we->we_wordv[idx][word_len] = '\0';
        idx++;
    }
    
    we->we_wordc = idx;
    we->we_wordv[idx] = NULL;
    
    return 0;
}

void wordfree_wrapper(void* pwordexp) {
    wordexp_internal_t* we = (wordexp_internal_t*)pwordexp;
    if (!we) return;
    
    LOG_DEBUG("wordfree_wrapper");
    
    if (we->we_wordv) {
        for (size_t i = 0; i < we->we_wordc; i++) {
            free(we->we_wordv[i]);
        }
        free(we->we_wordv);
        we->we_wordv = NULL;
    }
    we->we_wordc = 0;
}

/* ============================================================================
 * parse_printf_format - 解析 printf 格式字符串
 * glibc 扩展，bionic 不支持
 * ============================================================================ */

/* printf 参数类型常量 */
#define PA_INT          0
#define PA_CHAR         1
#define PA_STRING       2
#define PA_POINTER      3
#define PA_FLOAT        4
#define PA_DOUBLE       5
#define PA_FLAG_LONG    (1 << 8)

size_t parse_printf_format_wrapper(const char* fmt, size_t n, int* argtypes) {
    if (!fmt) return 0;
    
    size_t count = 0;
    const char* p = fmt;
    
    while (*p) {
        if (*p != '%') {
            p++;
            continue;
        }
        p++;  /* 跳过 '%' */
        
        if (*p == '%') {
            p++;
            continue;
        }
        
        /* 跳过标志 */
        while (*p == '-' || *p == '+' || *p == ' ' || 
               *p == '#' || *p == '0' || *p == '\'') {
            p++;
        }
        
        /* 跳过宽度 */
        if (*p == '*') {
            if (count < n && argtypes) argtypes[count] = PA_INT;
            count++;
            p++;
        } else {
            while (*p >= '0' && *p <= '9') p++;
        }
        
        /* 跳过精度 */
        if (*p == '.') {
            p++;
            if (*p == '*') {
                if (count < n && argtypes) argtypes[count] = PA_INT;
                count++;
                p++;
            } else {
                while (*p >= '0' && *p <= '9') p++;
            }
        }
        
        /* 处理长度修饰符 */
        int is_long = 0;
        if (*p == 'l') {
            is_long = 1;
            p++;
            if (*p == 'l') { is_long = 2; p++; }
        } else if (*p == 'h' || *p == 'L' || *p == 'z' || 
                   *p == 'j' || *p == 't' || *p == 'q') {
            p++;
        }
        
        /* 处理转换说明符 */
        int type = PA_INT;
        switch (*p) {
            case 'd': case 'i': case 'o': case 'u': case 'x': case 'X':
                type = PA_INT | (is_long ? PA_FLAG_LONG : 0);
                break;
            case 's':
                type = PA_STRING;
                break;
            case 'p':
                type = PA_POINTER;
                break;
            case 'c':
                type = PA_CHAR;
                break;
            case 'f': case 'F': case 'e': case 'E': case 'g': case 'G': case 'a': case 'A':
                type = PA_DOUBLE;
                break;
            case 'n':
                type = PA_INT | PA_FLAG_LONG;
                break;
        }
        
        if (count < n && argtypes) argtypes[count] = type;
        count++;
        
        if (*p) p++;
    }
    
    return count;
}

/* ============================================================================
 * 字节操作函数 (BSD 兼容)
 * ============================================================================ */

int bcmp_wrapper(const void* s1, const void* s2, size_t n) {
    return memcmp(s1, s2, n);
}

void bcopy_wrapper(const void* src, void* dest, size_t n) {
    memmove(dest, src, n);
}

void bzero_wrapper(void* s, size_t n) {
    memset(s, 0, n);
}

void explicit_bzero_wrapper(void* s, size_t n) {
    memset(s, 0, n);
    /* 阻止编译器优化掉 memset */
    __asm__ __volatile__("" : : "r"(s) : "memory");
}

/* ============================================================================
 * 字符串复制和搜索函数
 * ============================================================================ */

char* strdup_wrapper(const char* s) {
    return strdup(s);
}

char* strndup_wrapper(const char* s, size_t n) {
    return strndup(s, n);
}

void* rawmemchr_wrapper(const void* s, int c) {
    /* rawmemchr 不检查边界，假设字符一定存在 */
    return memchr(s, c, SIZE_MAX);
}

void* mempcpy_wrapper(void* dest, const void* src, size_t n) {
    return (char*)memcpy(dest, src, n) + n;
}

/* ============================================================================
 * basename 变体
 * ============================================================================ */

#include <libgen.h>

char* __xpg_basename_wrapper(char* path) {
    /* XPG4 版本的 basename 会修改参数 */
    return basename(path);
}

/* ============================================================================
 * argz/envz 函数 (glibc 扩展)
 * ============================================================================ */

size_t __argz_count_wrapper(const char* argz, size_t len) {
    size_t count = 0;
    const char* p = argz;
    while (p < argz + len) {
        count++;
        p += strlen(p) + 1;
    }
    return count;
}

/* ============================================================================
 * err.h 函数
 * ============================================================================ */

#include <err.h>

void err_wrapper(int eval, const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    verr(eval, fmt, ap);
    va_end(ap);
}

void errx_wrapper(int eval, const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    verrx(eval, fmt, ap);
    va_end(ap);
}

void warn_wrapper(const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vwarn(fmt, ap);
    va_end(ap);
}

void warnx_wrapper(const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vwarnx(fmt, ap);
    va_end(ap);
}
