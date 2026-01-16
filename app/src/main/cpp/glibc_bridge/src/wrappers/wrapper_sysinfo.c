/*
 * glibc-bridge - System Info Wrappers
 * 
 * System information and low-level glibc functions
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <malloc.h>
#include <sys/auxv.h>
#include <sys/stat.h>

/* ============================================================================
 * sysconf wrapper (internal glibc name)
 * ============================================================================ */

long sysconf_internal_wrapper(int name) {
    return sysconf(name);
}

/* ============================================================================
 * CPU information
 * ============================================================================ */

int getcpu_wrapper(unsigned* cpu, unsigned* node) {
    if (cpu) *cpu = 0;
    if (node) *node = 0;
    return 0;
}

/* ============================================================================
 * Memory management extensions
 * ============================================================================ */

int malloc_trim_wrapper(size_t pad) {
    (void)pad;
    /* Android doesn't have malloc_trim, return 0 (no memory returned) */
    return 0;
}

/* Internal libc malloc names (same as regular malloc) */
void* libc_malloc_wrapper(size_t size) {
    return malloc(size);
}

void* libc_calloc_wrapper(size_t nmemb, size_t size) {
    return calloc(nmemb, size);
}

void* libc_realloc_wrapper(void* ptr, size_t size) {
    return realloc(ptr, size);
}

void libc_free_wrapper(void* ptr) {
    free(ptr);
}

/* ============================================================================
 * Shared memory
 * ============================================================================ */

int shm_unlink_wrapper(const char* name) {
    (void)name;
    /* Not available on Android without root */
    errno = ENOENT;
    return -1;
}

/* dlinfo_wrapper 定义在 wrapper_dl.c 中 */

/* ============================================================================
 * FTS64 (file tree walk) - 64-bit version
 * ============================================================================ */

void* fts64_open_wrapper(char* const* path_argv, int options, 
                         int (*compar)(const void**, const void**)) {
    (void)path_argv;
    (void)options;
    (void)compar;
    errno = ENOSYS;
    return NULL;
}

void* fts64_read_wrapper(void* ftsp) {
    (void)ftsp;
    return NULL;
}

int fts64_close_wrapper(void* ftsp) {
    (void)ftsp;
    return 0;
}

/* ============================================================================
 * glob64 (64-bit glob)
 * ============================================================================ */

void globfree64_wrapper(void* pglob) {
    (void)pglob;
}

/* ============================================================================
 * Network protocol lookup
 * ============================================================================ */

int getprotobyname_r_wrapper(const char* name, void* result_buf,
                             char* buf, size_t buflen, void** result) {
    (void)name;
    (void)result_buf;
    (void)buf;
    (void)buflen;
    if (result) *result = NULL;
    return ENOENT;
}

/* ============================================================================
 * Wide character scanf (not fully supported)
 * ============================================================================ */

int isoc99_vwscanf_wrapper(const void* format, void* ap) {
    (void)format;
    (void)ap;
    errno = ENOSYS;
    return -1;
}

int isoc99_vswscanf_wrapper(const void* s, const void* format, void* ap) {
    (void)s;
    (void)format;
    (void)ap;
    errno = ENOSYS;
    return -1;
}

int isoc99_vfwscanf_wrapper(void* stream, const void* format, void* ap) {
    (void)stream;
    (void)format;
    (void)ap;
    errno = ENOSYS;
    return -1;
}

/* ============================================================================
 * Shared memory open
 * ============================================================================ */

int shm_open_wrapper(const char* name, int oflag, mode_t mode) {
    (void)name;
    (void)oflag;
    (void)mode;
    /* Not available on Android without root */
    errno = ENOENT;
    return -1;
}

/* ============================================================================
 * Memory alignment
 * ============================================================================ */

void* libc_memalign_wrapper(size_t alignment, size_t size) {
    return memalign(alignment, size);
}

/* ============================================================================
 * Auxiliary vector
 * ============================================================================ */

unsigned long getauxval_internal_wrapper(unsigned long type) {
    return getauxval(type);
}

/* ============================================================================
 * DNS resolver state
 * ============================================================================ */

/* Dummy res_state structure */
static struct {
    int retrans;
    int retry;
    unsigned long options;
    int nscount;
    /* ... simplified */
} g_dummy_res_state = {0};

void* res_state_wrapper(void) {
    return &g_dummy_res_state;
}

/* ============================================================================
 * Network protocol lookup (number)
 * ============================================================================ */

int getprotobynumber_r_wrapper(int proto, void* result_buf,
                               char* buf, size_t buflen, void** result) {
    (void)proto;
    (void)result_buf;
    (void)buf;
    (void)buflen;
    if (result) *result = NULL;
    return ENOENT;
}

/* ============================================================================
 * glob64 (64-bit glob)
 * ============================================================================ */

int glob64_wrapper(const char* pattern, int flags,
                   int (*errfunc)(const char*, int), void* pglob) {
    (void)pattern;
    (void)flags;
    (void)errfunc;
    (void)pglob;
    errno = ENOSYS;
    return -1; /* GLOB_ABORTED */
}

