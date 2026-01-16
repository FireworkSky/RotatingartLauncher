/*
 * glibc-bridge - gettext Wrapper Functions
 * 
 * Stub implementations for GNU gettext internationalization functions.
 * These are used by many programs for localization but Android typically
 * doesn't use gettext. We provide pass-through implementations that
 * return the original strings without translation.
 */

#define _GNU_SOURCE
#include <stddef.h>

#include "../include/wrappers.h"

/* ============================================================================
 * gettext Functions
 * 
 * These functions are supposed to translate message strings.
 * Since we don't have translation catalogs, we just return the original.
 * ============================================================================ */

char* gettext_wrapper(const char* msgid) {
    return (char*)msgid;  /* No translation, return original */
}

char* dgettext_wrapper(const char* domainname, const char* msgid) {
    (void)domainname;
    return (char*)msgid;
}

char* dcgettext_wrapper(const char* domainname, const char* msgid, int category) {
    (void)domainname;
    (void)category;
    return (char*)msgid;
}

/* ============================================================================
 * ngettext - Plural Forms
 * ============================================================================ */

char* ngettext_wrapper(const char* msgid1, const char* msgid2, unsigned long n) {
    /* Simple English plural rule: use singular for 1, plural otherwise */
    return (char*)(n == 1 ? msgid1 : msgid2);
}

/* ============================================================================
 * Text Domain Management
 * ============================================================================ */

char* bindtextdomain_wrapper(const char* domainname, const char* dirname) {
    (void)domainname;
    (void)dirname;
    return (char*)"";
}

char* bind_textdomain_codeset_wrapper(const char* domainname, const char* codeset) {
    (void)domainname;
    (void)codeset;
    return (char*)"UTF-8";
}

char* textdomain_wrapper(const char* domainname) {
    (void)domainname;
    return (char*)"messages";
}








