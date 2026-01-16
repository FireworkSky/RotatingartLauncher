/*
 * glibc-bridge - FORTIFY Wrapper Functions
 * 
 * Wrappers for glibc's FORTIFY_SOURCE checked functions (_chk suffix).
 * These are security-hardened versions that check buffer sizes.
 * Our wrappers simply forward to the unchecked versions since
 * the checks are done at compile time in glibc.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <wchar.h>
#include <syslog.h>
#include <fcntl.h>
#include <setjmp.h>
#include <sys/select.h>

#include "../include/wrappers.h"

/* ============================================================================
 * Wide Character Memory Functions
 * ============================================================================ */

wchar_t* wmemset_chk_wrapper(wchar_t* s, wchar_t c, size_t n, size_t destlen) {
    (void)destlen;
    return wmemset(s, c, n);
}

wchar_t* wmemcpy_chk_wrapper(wchar_t* dest, const wchar_t* src, 
                              size_t n, size_t destlen) {
    (void)destlen;
    return wmemcpy(dest, src, n);
}

wchar_t* wmemmove_chk_wrapper(wchar_t* dest, const wchar_t* src, 
                               size_t n, size_t destlen) {
    (void)destlen;
    return wmemmove(dest, src, n);
}

/* ============================================================================
 * Multibyte String Conversion Functions
 * ============================================================================ */

size_t mbsnrtowcs_chk_wrapper(wchar_t* dest, const char** src, 
                               size_t nms, size_t len, mbstate_t* ps, 
                               size_t destlen) {
    (void)destlen;
    return mbsnrtowcs(dest, src, nms, len, ps);
}

size_t mbsrtowcs_chk_wrapper(wchar_t* dest, const char** src, 
                              size_t len, mbstate_t* ps, size_t destlen) {
    (void)destlen;
    return mbsrtowcs(dest, src, len, ps);
}

/* ============================================================================
 * printf Family Functions
 * ============================================================================ */

int fprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, ...) {
    (void)flag;
    va_list ap;
    va_start(ap, fmt);
    int ret = vfprintf(stream, fmt, ap);
    va_end(ap);
    return ret;
}

int sprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* fmt, ...) {
    (void)flag;
    (void)strlen;
    va_list ap;
    va_start(ap, fmt);
    int ret = vsprintf(str, fmt, ap);
    va_end(ap);
    return ret;
}

int snprintf_chk_wrapper(char* str, size_t maxlen, int flag, 
                         size_t strlen, const char* fmt, ...) {
    (void)flag;
    (void)strlen;
    va_list ap;
    va_start(ap, fmt);
    int ret = vsnprintf(str, maxlen, fmt, ap);
    va_end(ap);
    return ret;
}

int printf_chk_wrapper(int flag, const char* fmt, ...) {
    (void)flag;
    va_list ap;
    va_start(ap, fmt);
    int ret = vprintf(fmt, ap);
    va_end(ap);
    return ret;
}

int vprintf_chk_wrapper(int flag, const char* fmt, va_list ap) {
    (void)flag;
    return vprintf(fmt, ap);
}

int vfprintf_chk_wrapper(FILE* stream, int flag, const char* fmt, va_list ap) {
    (void)flag;
    return vfprintf(stream, fmt, ap);
}

int vsprintf_chk_wrapper(char* str, int flag, size_t strlen, const char* fmt, va_list ap) {
    (void)flag;
    (void)strlen;
    return vsprintf(str, fmt, ap);
}

int vsnprintf_chk_wrapper(char* str, size_t maxlen, int flag, size_t strlen, const char* fmt, va_list ap) {
    (void)flag;
    (void)strlen;
    return vsnprintf(str, maxlen, fmt, ap);
}

int vdprintf_chk_wrapper(int fd, int flag, const char* fmt, va_list ap) {
    (void)flag;
    return vdprintf(fd, fmt, ap);
}

int vfwprintf_chk_wrapper(FILE* stream, int flag, const wchar_t* fmt, va_list ap) {
    (void)flag;
    return vfwprintf(stream, fmt, ap);
}

void vsyslog_chk_wrapper(int priority, int flag, const char* fmt, va_list ap) {
    (void)flag;
    vsyslog(priority, fmt, ap);
}

void syslog_chk_wrapper(int priority, int flag, const char* fmt, ...) {
    (void)flag;
    va_list ap;
    va_start(ap, fmt);
    vsyslog(priority, fmt, ap);
    va_end(ap);
}

int open64_2_wrapper(const char* path, int flags) {
    return open(path, flags);
}

int vasprintf_chk_wrapper(char** strp, int flag, const char* fmt, va_list ap) {
    (void)flag;
    return vasprintf(strp, fmt, ap);
}

int vswprintf_chk_wrapper(wchar_t* s, size_t maxlen, int flag, size_t slen, const wchar_t* fmt, va_list ap) {
    (void)flag;
    (void)slen;
    return vswprintf(s, maxlen, fmt, ap);
}

int vwprintf_chk_wrapper(int flag, const wchar_t* fmt, va_list ap) {
    (void)flag;
    return vwprintf(fmt, ap);
}

void longjmp_chk_wrapper(jmp_buf env, int val) {
    longjmp(env, val);
}

void chk_fail_wrapper(void) {
    abort();
}

/* ============================================================================
 * Wide String Functions
 * ============================================================================ */

int swprintf_chk_wrapper(wchar_t* s, size_t maxlen, int flag, 
                          size_t slen, const wchar_t* fmt, ...) {
    (void)flag;
    (void)slen;
    va_list ap;
    va_start(ap, fmt);
    int ret = vswprintf(s, maxlen, fmt, ap);
    va_end(ap);
    return ret;
}

wchar_t* wcscat_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t destlen) {
    (void)destlen;
    return wcscat(dest, src);
}

wchar_t* wcscpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t destlen) {
    (void)destlen;
    return wcscpy(dest, src);
}

wchar_t* wcsncat_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen) {
    (void)destlen;
    return wcsncat(dest, src, n);
}

wchar_t* wcsncpy_chk_wrapper(wchar_t* dest, const wchar_t* src, size_t n, size_t destlen) {
    (void)destlen;
    return wcsncpy(dest, src, n);
}



int asprintf_chk_wrapper(char** strp, int flag, const char* fmt, ...) {
    (void)flag;
    va_list ap;
    va_start(ap, fmt);
    int ret = vasprintf(strp, fmt, ap);
    va_end(ap);
    return ret;
}

char* realpath_chk_wrapper(const char* path, char* resolved_path, size_t resolved_len) {
    (void)resolved_len;
    return realpath(path, resolved_path);
}

char* stpcpy_chk_wrapper(char* dest, const char* src, size_t destlen) {
    (void)destlen;
    return stpcpy(dest, src);
}

char* stpncpy_chk_wrapper(char* dest, const char* src, size_t n, size_t destlen) {
    (void)destlen;
    return stpncpy(dest, src, n);
}


#ifndef NFDBITS
#define NFDBITS (8 * sizeof(unsigned long))
#endif

long fdelt_chk_wrapper(long fd) {
    if (fd < 0 || fd >= FD_SETSIZE) {
        /* In glibc this would abort, but we just clamp to valid range */
        if (fd < 0) fd = 0;
        if (fd >= FD_SETSIZE) fd = FD_SETSIZE - 1;
    }
    /* NFDBITS is typically 64 on 64-bit systems */
    return fd / NFDBITS;
}

