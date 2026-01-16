/*
 * glibc-bridge - Locale Wrapper Functions
 * 
 * Wrappers for glibc-specific locale functions (_l suffix).
 * These are required by libstdc++ for locale-aware operations.
 * Since bionic has limited locale support, we provide minimal implementations.
 */

#define _GNU_SOURCE
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <wctype.h>
#include <time.h>
#include <locale.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "../include/wrappers.h"
#include "../compat/tls.h"

/* ============================================================================
 * glibc locale structure (must match glibc's __locale_struct)
 * ============================================================================ */

struct glibc_locale_struct {
    void* __locales[13];           /* Locale data pointers */
    const unsigned short int* __ctype_b;    /* ctype classification table */
    const int* __ctype_tolower;    /* tolower conversion table */
    const int* __ctype_toupper;    /* toupper conversion table */
    const char* __names[13];       /* Locale names */
};

/* Global C locale instance */
static struct glibc_locale_struct g_c_locale;
static int g_locale_initialized = 0;

/* Initialize the C locale with proper ctype tables */
static void init_c_locale(void) {
    if (g_locale_initialized) return;
    
    memset(&g_c_locale, 0, sizeof(g_c_locale));
    
    /* Get ctype tables from glibc_bridge_tls */
    const unsigned short int** ctype_b = glibc_bridge_ctype_b_loc();
    const int** ctype_tolower = glibc_bridge_ctype_tolower_loc();
    const int** ctype_toupper = glibc_bridge_ctype_toupper_loc();
    
    if (ctype_b) g_c_locale.__ctype_b = *ctype_b;
    if (ctype_tolower) g_c_locale.__ctype_tolower = *ctype_tolower;
    if (ctype_toupper) g_c_locale.__ctype_toupper = *ctype_toupper;
    
    /* Set default names and __locales pointers */
    /* glibc expects __locales[i] to be valid pointers for some operations */
    for (int i = 0; i < 13; i++) {
        g_c_locale.__names[i] = "C";
        /* Point __locales to ourselves as a sentinel value */
        g_c_locale.__locales[i] = (void*)&g_c_locale;
    }
    
    g_locale_initialized = 1;
    

}

/* ============================================================================
 * nl_langinfo stub
 * bionic doesn't have nl_langinfo, so we provide a stub
 * ============================================================================ */

static char* my_nl_langinfo(int item) {
    (void)item;
    /* Return reasonable defaults */
    return (char*)"UTF-8";
}

/* ============================================================================
 * Locale Management
 * Create proper locale structures with ctype tables
 * ============================================================================ */

locale_t newlocale_wrapper(int mask, const char* locale, locale_t base) {
    (void)mask;
    (void)locale;
    (void)base;
    
    /* Initialize the C locale if needed */
    init_c_locale();
    
    /* Return pointer to our global C locale */
    return (locale_t)&g_c_locale;
}

void freelocale_wrapper(locale_t loc) {
    /* Don't free our global C locale */
    (void)loc;
}

locale_t duplocale_wrapper(locale_t loc) {
    (void)loc;
    init_c_locale();
    return (locale_t)&g_c_locale;
}

locale_t uselocale_wrapper(locale_t loc) {
    (void)loc;
    init_c_locale();
    return (locale_t)&g_c_locale;
}

/* ============================================================================
 * String/Number Conversion with Locale
 * ============================================================================ */

double strtod_l_wrapper(const char* str, char** endptr, locale_t loc) {
    (void)loc;
    return strtod(str, endptr);
}

float strtof_l_wrapper(const char* str, char** endptr, locale_t loc) {
    (void)loc;
    return strtof(str, endptr);
}

long double strtold_l_wrapper(const char* str, char** endptr, locale_t loc) {
    (void)loc;
    return strtold(str, endptr);
}

/* ============================================================================
 * String Comparison with Locale
 * ============================================================================ */

int strcoll_l_wrapper(const char* s1, const char* s2, locale_t loc) {
    (void)loc;
    return strcoll(s1, s2);
}

size_t strxfrm_l_wrapper(char* dest, const char* src, size_t n, locale_t loc) {
    (void)loc;
    return strxfrm(dest, src, n);
}

int wcscoll_l_wrapper(const wchar_t* s1, const wchar_t* s2, locale_t loc) {
    (void)loc;
    return wcscoll(s1, s2);
}

size_t wcsxfrm_l_wrapper(wchar_t* dest, const wchar_t* src, size_t n, locale_t loc) {
    (void)loc;
    return wcsxfrm(dest, src, n);
}

/* ============================================================================
 * Character Classification with Locale (ctype _l functions)
 * ============================================================================ */

#include <ctype.h>

/* Character classification _l functions */
int isalpha_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isalpha(c);
}

int isdigit_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isdigit(c);
}

int isalnum_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isalnum(c);
}

int isspace_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isspace(c);
}

int isupper_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isupper(c);
}

int islower_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return islower(c);
}

int isprint_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isprint(c);
}

int ispunct_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return ispunct(c);
}

int isgraph_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isgraph(c);
}

int iscntrl_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return iscntrl(c);
}

int isxdigit_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isxdigit(c);
}

int isblank_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return isblank(c);
}

/* Character conversion _l functions */
int tolower_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return tolower(c);
}

int toupper_l_wrapper(int c, locale_t loc) {
    (void)loc;
    return toupper(c);
}

/* ============================================================================
 * Wide Character Classification with Locale (wctype _l functions)
 * ============================================================================ */

wint_t towlower_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return towlower(wc);
}

wint_t towupper_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return towupper(wc);
}

wctype_t wctype_l_wrapper(const char* name, locale_t loc) {
    (void)loc;
    return wctype(name);
}

int iswctype_l_wrapper(wint_t wc, wctype_t desc, locale_t loc) {
    (void)loc;
    return iswctype(wc, desc);
}

int iswalpha_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return iswalpha(wc);
}

int iswdigit_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return iswdigit(wc);
}

int iswspace_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return iswspace(wc);
}

int iswupper_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return iswupper(wc);
}

int iswlower_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return iswlower(wc);
}

int iswprint_l_wrapper(wint_t wc, locale_t loc) {
    (void)loc;
    return iswprint(wc);
}

/* ============================================================================
 * Time Formatting with Locale
 * ============================================================================ */

size_t strftime_l_wrapper(char* s, size_t max, const char* fmt, 
                          const struct tm* tm, locale_t loc) {
    (void)loc;
    return strftime(s, max, fmt, tm);
}

size_t wcsftime_l_wrapper(wchar_t* s, size_t max, const wchar_t* fmt, 
                          const struct tm* tm, locale_t loc) {
    (void)loc;
    return wcsftime(s, max, fmt, tm);
}

/* ============================================================================
 * Language Info
 * ============================================================================ */

char* nl_langinfo_l_wrapper(int item, locale_t loc) {
    (void)loc;
    return my_nl_langinfo(item);
}

char* nl_langinfo_wrapper(int item) {
    return my_nl_langinfo(item);
}

/* ============================================================================
 * strerror with Locale
 * ============================================================================ */

/* Basic strerror wrapper */
char* strerror_wrapper(int errnum) {
    return strerror(errnum);
}

char* strerror_l_wrapper(int errnum, locale_t loc) {
    (void)loc;
    return strerror(errnum);
}

/* GNU strerror_r returns char* (may return buf or static string)
 * Bionic also uses GNU version with char* return */
static __thread char strerror_r_buf[256];

char* __xpg_strerror_r_wrapper(int errnum, char* buf, size_t buflen) {
    /* POSIX/XSI version - fill buffer and return it */
    char* result = strerror_r(errnum, buf, buflen);
    (void)result;
    return buf;  /* Always return the provided buffer for XSI version */
}

char* strerror_r_wrapper(int errnum, char* buf, size_t buflen) {
    /* GNU version - return char* */
    /* Bionic already uses GNU version */
    if (buf && buflen > 0) {
        return strerror_r(errnum, buf, buflen);
    }
    /* Fallback to thread-local buffer */
    return strerror_r(errnum, strerror_r_buf, sizeof(strerror_r_buf));
}

/* strtol/strtoul with locale */
long strtol_l_wrapper(const char* str, char** endptr, int base, locale_t loc) {
    (void)loc;
    return strtol(str, endptr, base);
}

long long strtoll_l_wrapper(const char* str, char** endptr, int base, locale_t loc) {
    (void)loc;
    return strtoll(str, endptr, base);
}

unsigned long strtoul_l_wrapper(const char* str, char** endptr, int base, locale_t loc) {
    (void)loc;
    return strtoul(str, endptr, base);
}

unsigned long long strtoull_l_wrapper(const char* str, char** endptr, int base, locale_t loc) {
    (void)loc;
    return strtoull(str, endptr, base);
}

