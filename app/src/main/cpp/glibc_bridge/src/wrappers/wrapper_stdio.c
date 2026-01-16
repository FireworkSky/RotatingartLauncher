/*
 * glibc-bridge - stdio Wrappers
 * 
 * glibc stdio functions not in Bionic (fopencookie, etc.)
 * 
 * Reference:
 *   glibc: glibc-master/libio/iofopncook.c
 *   bionic: platform_bionic-main/libc/stdio/local.h (funopen)
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdint.h>

/*
 * glibc's fopencookie uses this callback signature:
 *   ssize_t read(void* cookie, char* buf, size_t size)
 *   ssize_t write(void* cookie, const char* buf, size_t size)
 *   int seek(void* cookie, off64_t* offset, int whence)
 *   int close(void* cookie)
 *
 * Bionic's funopen uses:
 *   int read(void* cookie, char* buf, int size)
 *   int write(void* cookie, const char* buf, int size)
 *   fpos_t seek(void* cookie, fpos_t offset, int whence)
 *   int close(void* cookie)
 */

/* glibc cookie_io_functions_t structure */
typedef struct {
    ssize_t (*read)(void*, char*, size_t);
    ssize_t (*write)(void*, const char*, size_t);
    int (*seek)(void*, off64_t*, int);
    int (*close)(void*);
} glibc_cookie_io_functions_t;

/* Internal structure to hold both cookie and glibc callbacks */
typedef struct {
    void* user_cookie;
    glibc_cookie_io_functions_t funcs;
} glibc_bridge_cookie_wrapper_t;

/* Adapter: glibc read -> bionic funopen read */
static int fopencookie_read_adapter(void* wrapper, char* buf, int size) {
    glibc_bridge_cookie_wrapper_t* w = (glibc_bridge_cookie_wrapper_t*)wrapper;
    if (!w->funcs.read) return -1;
    
    ssize_t ret = w->funcs.read(w->user_cookie, buf, (size_t)size);
    return (int)ret;
}

/* Adapter: glibc write -> bionic funopen write */
static int fopencookie_write_adapter(void* wrapper, const char* buf, int size) {
    glibc_bridge_cookie_wrapper_t* w = (glibc_bridge_cookie_wrapper_t*)wrapper;
    if (!w->funcs.write) return -1;
    
    ssize_t ret = w->funcs.write(w->user_cookie, buf, (size_t)size);
    return (int)ret;
}

/* Adapter: glibc seek -> bionic funopen seek */
static fpos_t fopencookie_seek_adapter(void* wrapper, fpos_t offset, int whence) {
    glibc_bridge_cookie_wrapper_t* w = (glibc_bridge_cookie_wrapper_t*)wrapper;
    if (!w->funcs.seek) return -1;
    
    off64_t off = (off64_t)offset;
    int ret = w->funcs.seek(w->user_cookie, &off, whence);
    if (ret != 0) return -1;
    
    return (fpos_t)off;
}

/* Adapter: glibc close -> bionic funopen close */
static int fopencookie_close_adapter(void* wrapper) {
    glibc_bridge_cookie_wrapper_t* w = (glibc_bridge_cookie_wrapper_t*)wrapper;
    int ret = 0;
    
    if (w->funcs.close) {
        ret = w->funcs.close(w->user_cookie);
    }
    
    free(w);
    return ret;
}

/*
 * fopencookie - glibc extension for custom FILE streams
 * 
 * Creates a FILE* that uses user-provided callbacks for I/O operations.
 * Implemented by wrapping Bionic's funopen().
 */
FILE* fopencookie_wrapper(void* cookie, const char* mode, 
                          glibc_cookie_io_functions_t io_funcs) {
    /* Allocate our wrapper structure */
    glibc_bridge_cookie_wrapper_t* wrapper = malloc(sizeof(glibc_bridge_cookie_wrapper_t));
    if (!wrapper) {
        errno = ENOMEM;
        return NULL;
    }
    
    wrapper->user_cookie = cookie;
    wrapper->funcs = io_funcs;
    
    /* Determine which callbacks to pass to funopen based on mode */
    int (*readfn)(void*, char*, int) = NULL;
    int (*writefn)(void*, const char*, int) = NULL;
    fpos_t (*seekfn)(void*, fpos_t, int) = NULL;
    int (*closefn)(void*) = fopencookie_close_adapter;
    
    /* Parse mode string like glibc does */
    const char* m = mode;
    int read_mode = 0, write_mode = 0;
    
    if (*m == 'r') {
        read_mode = 1;
        m++;
    } else if (*m == 'w' || *m == 'a') {
        write_mode = 1;
        m++;
    } else {
        free(wrapper);
        errno = EINVAL;
        return NULL;
    }
    
    /* Check for '+' which enables both read and write */
    while (*m) {
        if (*m == '+') {
            read_mode = 1;
            write_mode = 1;
        }
        m++;
    }
    
    if (read_mode && io_funcs.read) {
        readfn = fopencookie_read_adapter;
    }
    if (write_mode && io_funcs.write) {
        writefn = fopencookie_write_adapter;
    }
    if (io_funcs.seek) {
        seekfn = fopencookie_seek_adapter;
    }
    
    /* Call Bionic's funopen */
    FILE* fp = funopen(wrapper, readfn, writefn, seekfn, closefn);
    if (!fp) {
        free(wrapper);
        return NULL;
    }
    
    return fp;
}

/* cookie_io_functions_t is used by glibc - define type alias for symbol table */
typedef glibc_cookie_io_functions_t cookie_io_functions_t;








