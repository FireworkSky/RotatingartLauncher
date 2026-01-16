/*
 * glibc-bridge - Path Translation Utilities
 * 
 * Path translation between glibc rootfs and Android filesystem.
 * Provides wrapper_translate_path() for mapping paths.
 */

#include "wrapper_path.h"
#include <string.h>
#include <limits.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>

/* Thread-local wrapper name - defined here */
__thread const char* g_current_wrapper_name = NULL;

/* Thread-local buffer for translated paths */
static __thread char translated_path[PATH_MAX];
static __thread char reverse_translated_path[PATH_MAX];

/* ============================================================================
 * Path Translation Implementation
 * ============================================================================ */

int wrapper_should_translate_path(const char* path) {
    if (!path || path[0] != '/') {
        return 0;  /* Relative path or NULL - no translation */
    }
    
    const char* glibc_root = glibc_bridge_get_glibc_root();
    if (!glibc_root || !glibc_root[0]) {
        return 0;  /* No fake rootfs set up */
    }
    
    /* Already points to glibc root? No double translation */
    if (strncmp(path, glibc_root, strlen(glibc_root)) == 0) {
        return 0;
    }
    
    /* Special case: translate /system/build.prop to fake rootfs so MonoMod
     * detects Linux instead of Android. The file won't exist in fake rootfs,
     * so File.Exists("/system/build.prop") will return false. */
    if (strcmp(path, "/system/build.prop") == 0) {
        return 1;  /* Translate this specific file */
    }
    
    /* Pass-through paths (real Android paths) */
    if (strncmp(path, "/proc", 5) == 0 ||
        strncmp(path, "/dev", 4) == 0 ||
        strncmp(path, "/sys", 4) == 0 ||
        strncmp(path, "/data", 5) == 0 ||
        strncmp(path, "/storage", 8) == 0 ||
        strncmp(path, "/sdcard", 7) == 0 ||
        strncmp(path, "/system", 7) == 0 ||
        strncmp(path, "/vendor", 7) == 0 ||
        strncmp(path, "/apex", 5) == 0 ||
        strncmp(path, "/linkerconfig", 13) == 0) {
        return 0;  /* These must access real Android paths */
    }
    
    return 1;  /* Should translate */
}

const char* wrapper_translate_path(const char* path) {
    if (!wrapper_should_translate_path(path)) {
        return path;
    }
    
    const char* glibc_root = glibc_bridge_get_glibc_root();
    if (!glibc_root) {
        return path;
    }
    
    /* Translate: /xxx -> $GLIBC_ROOT/xxx */
    snprintf(translated_path, sizeof(translated_path), "%s%s", glibc_root, path);
    return translated_path;
}

const char* wrapper_reverse_translate_path(const char* path, char* out_buf, size_t buf_size) {
    if (!path) return NULL;
    
    const char* glibc_root = glibc_bridge_get_glibc_root();
    if (!glibc_root || !glibc_root[0]) {
        return path;
    }
    
    size_t root_len = strlen(glibc_root);
    if (strncmp(path, glibc_root, root_len) == 0) {
        /* Path starts with glibc_root - remove the prefix */
        const char* suffix = path + root_len;
        if (*suffix == '\0') {
            /* Exactly glibc_root -> "/" */
            if (out_buf) {
                snprintf(out_buf, buf_size, "/");
                return out_buf;
            } else {
                snprintf(reverse_translated_path, sizeof(reverse_translated_path), "/");
                return reverse_translated_path;
            }
        } else {
            /* glibc_root/xxx -> /xxx */
            if (out_buf) {
                snprintf(out_buf, buf_size, "%s", suffix);
                return out_buf;
            } else {
                snprintf(reverse_translated_path, sizeof(reverse_translated_path), "%s", suffix);
                return reverse_translated_path;
            }
        }
    }
    return path;
}

/* ============================================================================
 * Error Reporting Implementation
 * ============================================================================ */

void wrapper_error_abort(const char* prefix, const char* message) {
    char buf[256];
    snprintf(buf, sizeof(buf), "[WRAPPER] %s: %s\n", 
             prefix ? prefix : "Error", 
             message ? message : "unknown");
    write(STDERR_FILENO, buf, strlen(buf));
    abort();
}

