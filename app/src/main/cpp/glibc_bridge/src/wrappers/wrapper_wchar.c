/*
 * glibc-bridge - 宽字符函数包装
 * 
 * 包含 wcschr, wcsrchr, wcspbrk, wmemcpy, wcstod 等宽字符相关函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <wctype.h>
#include <errno.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * 宽字符串搜索函数
 * ============================================================================ */

wchar_t* wcschr_wrapper(const wchar_t *wcs, wchar_t wc) {
    return wcschr(wcs, wc);
}

wchar_t* wcsrchr_wrapper(const wchar_t *wcs, wchar_t wc) {
    return wcsrchr(wcs, wc);
}

wchar_t* wcspbrk_wrapper(const wchar_t *wcs, const wchar_t *accept) {
    return wcspbrk(wcs, accept);
}

wchar_t* wcsstr_wrapper(const wchar_t *haystack, const wchar_t *needle) {
    return wcsstr(haystack, needle);
}

size_t wcsspn_wrapper(const wchar_t *wcs, const wchar_t *accept) {
    return wcsspn(wcs, accept);
}

size_t wcscspn_wrapper(const wchar_t *wcs, const wchar_t *reject) {
    return wcscspn(wcs, reject);
}

wchar_t* wcstok_wrapper(wchar_t *wcs, const wchar_t *delim, wchar_t **ptr) {
    return wcstok(wcs, delim, ptr);
}

/* ============================================================================
 * 宽字符串复制/连接函数
 * ============================================================================ */

wchar_t* wmemcpy_wrapper(wchar_t *dest, const wchar_t *src, size_t n) {
    return wmemcpy(dest, src, n);
}

wchar_t* wmemmove_wrapper(wchar_t *dest, const wchar_t *src, size_t n) {
    return wmemmove(dest, src, n);
}

wchar_t* wmemset_wrapper(wchar_t *wcs, wchar_t wc, size_t n) {
    return wmemset(wcs, wc, n);
}

wchar_t* wcscpy_wrapper(wchar_t *dest, const wchar_t *src) {
    return wcscpy(dest, src);
}

wchar_t* wcsncpy_wrapper(wchar_t *dest, const wchar_t *src, size_t n) {
    return wcsncpy(dest, src, n);
}

wchar_t* wcscat_wrapper(wchar_t *dest, const wchar_t *src) {
    return wcscat(dest, src);
}

wchar_t* wcsncat_wrapper(wchar_t *dest, const wchar_t *src, size_t n) {
    return wcsncat(dest, src, n);
}

/* ============================================================================
 * 宽字符串比较函数
 * ============================================================================ */

int wcscmp_wrapper(const wchar_t *s1, const wchar_t *s2) {
    return wcscmp(s1, s2);
}

int wcsncmp_wrapper(const wchar_t *s1, const wchar_t *s2, size_t n) {
    return wcsncmp(s1, s2, n);
}

int wmemcmp_wrapper(const wchar_t *s1, const wchar_t *s2, size_t n) {
    return wmemcmp(s1, s2, n);
}

int wcscasecmp_wrapper(const wchar_t *s1, const wchar_t *s2) {
    return wcscasecmp(s1, s2);
}

int wcsncasecmp_wrapper(const wchar_t *s1, const wchar_t *s2, size_t n) {
    return wcsncasecmp(s1, s2, n);
}

int wcscoll_wrapper(const wchar_t *s1, const wchar_t *s2) {
    return wcscoll(s1, s2);
}

size_t wcsxfrm_wrapper(wchar_t *dest, const wchar_t *src, size_t n) {
    return wcsxfrm(dest, src, n);
}

/* ============================================================================
 * 宽字符串长度函数
 * ============================================================================ */

size_t wcslen_wrapper(const wchar_t *s) {
    return wcslen(s);
}

size_t wcsnlen_wrapper(const wchar_t *s, size_t maxlen) {
    return wcsnlen(s, maxlen);
}

wchar_t* wmemchr_wrapper(const wchar_t *s, wchar_t c, size_t n) {
    return wmemchr(s, c, n);
}

/* ============================================================================
 * 宽字符转数字函数
 * ============================================================================ */

double wcstod_wrapper(const wchar_t *nptr, wchar_t **endptr) {
    return wcstod(nptr, endptr);
}

float wcstof_wrapper(const wchar_t *nptr, wchar_t **endptr) {
    return wcstof(nptr, endptr);
}

long double wcstold_wrapper(const wchar_t *nptr, wchar_t **endptr) {
    return wcstold(nptr, endptr);
}

long wcstol_wrapper(const wchar_t *nptr, wchar_t **endptr, int base) {
    return wcstol(nptr, endptr, base);
}

unsigned long wcstoul_wrapper(const wchar_t *nptr, wchar_t **endptr, int base) {
    return wcstoul(nptr, endptr, base);
}

long long wcstoll_wrapper(const wchar_t *nptr, wchar_t **endptr, int base) {
    return wcstoll(nptr, endptr, base);
}

unsigned long long wcstoull_wrapper(const wchar_t *nptr, wchar_t **endptr, int base) {
    return wcstoull(nptr, endptr, base);
}

/* ============================================================================
 * 多字节/宽字符转换函数
 * ============================================================================ */

int mblen_wrapper(const char *s, size_t n) {
    return mblen(s, n);
}

int mbtowc_wrapper(wchar_t *pwc, const char *s, size_t n) {
    return mbtowc(pwc, s, n);
}

int wctomb_wrapper(char *s, wchar_t wc) {
    return wctomb(s, wc);
}

size_t mbstowcs_wrapper(wchar_t *dest, const char *src, size_t n) {
    return mbstowcs(dest, src, n);
}

size_t wcstombs_wrapper(char *dest, const wchar_t *src, size_t n) {
    return wcstombs(dest, src, n);
}

size_t mbrlen_wrapper(const char *s, size_t n, mbstate_t *ps) {
    return mbrlen(s, n, ps);
}

size_t mbrtowc_wrapper(wchar_t *pwc, const char *s, size_t n, mbstate_t *ps) {
    return mbrtowc(pwc, s, n, ps);
}

size_t wcrtomb_wrapper(char *s, wchar_t wc, mbstate_t *ps) {
    return wcrtomb(s, wc, ps);
}

size_t mbsrtowcs_wrapper(wchar_t *dest, const char **src, size_t len, mbstate_t *ps) {
    return mbsrtowcs(dest, src, len, ps);
}

size_t wcsrtombs_wrapper(char *dest, const wchar_t **src, size_t len, mbstate_t *ps) {
    return wcsrtombs(dest, src, len, ps);
}

/* ============================================================================
 * 宽字符格式化输入输出
 * ============================================================================ */

int fwprintf_wrapper(FILE *stream, const wchar_t *format, ...) {
    va_list ap;
    va_start(ap, format);
    int ret = vfwprintf(stream, format, ap);
    va_end(ap);
    return ret;
}

int wprintf_wrapper(const wchar_t *format, ...) {
    va_list ap;
    va_start(ap, format);
    int ret = vwprintf(format, ap);
    va_end(ap);
    return ret;
}

int swprintf_wrapper(wchar_t *wcs, size_t maxlen, const wchar_t *format, ...) {
    va_list ap;
    va_start(ap, format);
    int ret = vswprintf(wcs, maxlen, format, ap);
    va_end(ap);
    return ret;
}

int vfwprintf_wrapper(FILE *stream, const wchar_t *format, va_list ap) {
    return vfwprintf(stream, format, ap);
}

int vwprintf_wrapper(const wchar_t *format, va_list ap) {
    return vwprintf(format, ap);
}

int vswprintf_wrapper(wchar_t *wcs, size_t maxlen, const wchar_t *format, va_list ap) {
    return vswprintf(wcs, maxlen, format, ap);
}

/* ============================================================================
 * 宽字符分类函数
 * ============================================================================ */

int iswalnum_wrapper(wint_t wc) {
    return iswalnum(wc);
}

int iswalpha_wrapper(wint_t wc) {
    return iswalpha(wc);
}

int iswblank_wrapper(wint_t wc) {
    return iswblank(wc);
}

int iswcntrl_wrapper(wint_t wc) {
    return iswcntrl(wc);
}

int iswdigit_wrapper(wint_t wc) {
    return iswdigit(wc);
}

int iswgraph_wrapper(wint_t wc) {
    return iswgraph(wc);
}

int iswlower_wrapper(wint_t wc) {
    return iswlower(wc);
}

int iswprint_wrapper(wint_t wc) {
    return iswprint(wc);
}

int iswpunct_wrapper(wint_t wc) {
    return iswpunct(wc);
}

int iswspace_wrapper(wint_t wc) {
    return iswspace(wc);
}

int iswupper_wrapper(wint_t wc) {
    return iswupper(wc);
}

int iswxdigit_wrapper(wint_t wc) {
    return iswxdigit(wc);
}

wint_t towlower_wrapper(wint_t wc) {
    return towlower(wc);
}

wint_t towupper_wrapper(wint_t wc) {
    return towupper(wc);
}

/* ============================================================================
 * 宽字符文件输入输出
 * ============================================================================ */

wint_t fgetwc_wrapper(FILE *stream) {
    return fgetwc(stream);
}

wchar_t *fgetws_wrapper(wchar_t *ws, int n, FILE *stream) {
    return fgetws(ws, n, stream);
}

wint_t fputwc_wrapper(wchar_t wc, FILE *stream) {
    return fputwc(wc, stream);
}

int fputws_wrapper(const wchar_t *ws, FILE *stream) {
    return fputws(ws, stream);
}

wint_t getwc_wrapper(FILE *stream) {
    return getwc(stream);
}

wint_t getwchar_wrapper(void) {
    return getwchar();
}

wint_t putwc_wrapper(wchar_t wc, FILE *stream) {
    return putwc(wc, stream);
}

wint_t putwchar_wrapper(wchar_t wc) {
    return putwchar(wc);
}

wint_t ungetwc_wrapper(wint_t wc, FILE *stream) {
    return ungetwc(wc, stream);
}

/* ============================================================================
 * 宽字符时间格式化
 * ============================================================================ */

size_t wcsftime_wrapper(wchar_t *wcs, size_t maxsize,
                         const wchar_t *format, const struct tm *timeptr) {
    return wcsftime(wcs, maxsize, format, timeptr);
}
