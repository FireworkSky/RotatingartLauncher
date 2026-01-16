/*
 * glibc-bridge stdio wrapper - FILE structure conversion between glibc and bionic
 * 
 * glibc and bionic have different FILE structure layouts.
 * This module provides wrapper functions to handle the conversion.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <unistd.h>
#include <errno.h>
#include <pthread.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "glibc_bridge_private.h"

/* ============================================================================
 * glibc FILE structure (simplified _IO_FILE from glibc)
 * This is what glibc programs expect
 * ============================================================================ */

/* glibc _IO_FILE structure layout (ARM64) */
typedef struct glibc_IO_FILE {
    int _flags;                   /* High-order word is _IO_MAGIC; rest is flags */
    char *_IO_read_ptr;           /* Current read pointer */
    char *_IO_read_end;           /* End of get area */
    char *_IO_read_base;          /* Start of putback+get area */
    char *_IO_write_base;         /* Start of put area */
    char *_IO_write_ptr;          /* Current put pointer */
    char *_IO_write_end;          /* End of put area */
    char *_IO_buf_base;           /* Start of reserve area */
    char *_IO_buf_end;            /* End of reserve area */
    char *_IO_save_base;
    char *_IO_backup_base;
    char *_IO_save_end;
    void *_markers;
    struct glibc_IO_FILE *_chain;
    int _fileno;
    int _flags2;
    long _old_offset;
    unsigned short _cur_column;
    signed char _vtable_offset;
    char _shortbuf[1];
    void *_lock;
    long _offset;
    void *_codecvt;
    void *_wide_data;
    struct glibc_IO_FILE *_freeres_list;
    void *_freeres_buf;
    size_t __pad5;
    int _mode;
    char _unused2[20];
} glibc_IO_FILE;

/* glibc FILE magic and flags */
#define _IO_MAGIC         0xFBAD0000
#define _IO_MAGIC_MASK    0xFFFF0000
#define _IO_NO_READS      0x0004
#define _IO_NO_WRITES     0x0008
#define _IO_UNBUFFERED    0x0002
#define _IO_LINE_BUF      0x0200
#define _IO_LINKED        0x0080

/* ============================================================================
 * FILE mapping table
 * Maps glibc FILE* to bionic FILE*
 * ============================================================================ */

#define MAX_FILE_MAPPINGS 256

typedef struct {
    void* glibc_fp;      /* Fake glibc FILE pointer (or marker) */
    FILE* bionic_fp;     /* Actual bionic FILE pointer */
    int is_standard;     /* Is this stdin/stdout/stderr? */
} file_mapping_t;

static file_mapping_t g_file_mappings[MAX_FILE_MAPPINGS];
static pthread_mutex_t g_file_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_file_initialized = 0;

/* Fake glibc FILE structures for standard streams */
static glibc_IO_FILE g_glibc_stdin;
static glibc_IO_FILE g_glibc_stdout;
static glibc_IO_FILE g_glibc_stderr;

/* Glibc stdout/stderr/stdin are FILE* variables (pointers to FILE structures)
 * Programs will read from these addresses to get the FILE* value.
 * The GLOB_DAT relocation fills GOT with the address of these variables,
 * then code like "FILE* fp = stdout" reads the pointer value from here. */
static void* g_stdout_ptr;  /* Will point to &g_glibc_stdout */
static void* g_stderr_ptr;  /* Will point to &g_glibc_stderr */
static void* g_stdin_ptr;   /* Will point to &g_glibc_stdin */

/* Forward declaration */
FILE* glibc_bridge_get_bionic_fp(void* glibc_fp);

/* Initialize the file mapping system */
void glibc_bridge_stdio_init(void) {
    if (g_file_initialized) return;
    
    pthread_mutex_lock(&g_file_mutex);
    if (g_file_initialized) {
        pthread_mutex_unlock(&g_file_mutex);
        return;
    }
    
    memset(g_file_mappings, 0, sizeof(g_file_mappings));
    
    /* Initialize fake glibc structures for standard streams */
    memset(&g_glibc_stdin, 0, sizeof(g_glibc_stdin));
    memset(&g_glibc_stdout, 0, sizeof(g_glibc_stdout));
    memset(&g_glibc_stderr, 0, sizeof(g_glibc_stderr));
    
    /* Set fileno for identification */
    g_glibc_stdin._fileno = STDIN_FILENO;
    g_glibc_stdout._fileno = STDOUT_FILENO;
    g_glibc_stderr._fileno = STDERR_FILENO;
    
    /* Set flags with magic number - required for libstdc++ to recognize FILE* */
    g_glibc_stdin._flags = _IO_MAGIC | _IO_NO_WRITES | _IO_LINKED;
    g_glibc_stdout._flags = _IO_MAGIC | _IO_NO_READS | _IO_LINKED;
    g_glibc_stderr._flags = _IO_MAGIC | _IO_NO_READS | _IO_UNBUFFERED | _IO_LINKED;
    
    /* Set mode for text streams */
    g_glibc_stdin._mode = -1;
    g_glibc_stdout._mode = -1;
    g_glibc_stderr._mode = -1;
    
    /* Map standard streams */
    g_file_mappings[0].glibc_fp = &g_glibc_stdin;
    g_file_mappings[0].bionic_fp = stdin;
    g_file_mappings[0].is_standard = 1;
    
    g_file_mappings[1].glibc_fp = &g_glibc_stdout;
    g_file_mappings[1].bionic_fp = stdout;
    g_file_mappings[1].is_standard = 1;
    
    g_file_mappings[2].glibc_fp = &g_glibc_stderr;
    g_file_mappings[2].bionic_fp = stderr;
    g_file_mappings[2].is_standard = 1;
    
    /* Initialize the FILE* pointer variables (these are what the stdout/stderr/stdin
     * symbols point to - programs read FILE* values from these addresses) */
    g_stdin_ptr = &g_glibc_stdin;
    g_stdout_ptr = &g_glibc_stdout;
    g_stderr_ptr = &g_glibc_stderr;
    
    g_file_initialized = 1;
    pthread_mutex_unlock(&g_file_mutex);
}

/* Get bionic FILE* from glibc FILE* 
 * This is used by other wrappers (like getdelim/getline) to convert 
 * glibc FILE* to bionic FILE* before calling bionic functions.
 */
FILE* glibc_bridge_get_bionic_fp(void* glibc_fp) {
    if (!glibc_fp) return NULL;
    
    /* Reject obviously invalid pointers (small values that are likely NULL+offset bugs) */
    if ((uintptr_t)glibc_fp < 0x1000) {
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_WARN, "GLIBC_BRIDGE_STDIO", 
            "Invalid FILE* pointer: %p (too small)", glibc_fp);
#endif
        return NULL;
    }
    
    /* Make sure stdio is initialized */
    glibc_bridge_stdio_init();
    
#ifdef __ANDROID__
    /* Debug: Log the incoming pointer and our known FILE* addresses */
    static int debug_count = 0;
    if (debug_count < 10) {
        __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_STDIO", 
            "glibc_bridge_get_bionic_fp(%p) glibc_stdout=%p glibc_stderr=%p bionic_stdout=%p bionic_stderr=%p",
            glibc_fp, (void*)&g_glibc_stdout, (void*)&g_glibc_stderr, 
            (void*)stdout, (void*)stderr);
        debug_count++;
    }
#endif
    
    /* Check standard streams first */
    if (glibc_fp == &g_glibc_stdin) return stdin;
    if (glibc_fp == &g_glibc_stdout) return stdout;
    if (glibc_fp == &g_glibc_stderr) return stderr;
    
    /* Check if it's already a bionic standard stream */
    if (glibc_fp == stdin) return stdin;
    if (glibc_fp == stdout) return stdout;
    if (glibc_fp == stderr) return stderr;
    
    /* Check mapping table */
    pthread_mutex_lock(&g_file_mutex);
    for (int i = 0; i < MAX_FILE_MAPPINGS; i++) {
        if (g_file_mappings[i].glibc_fp == glibc_fp) {
            FILE* fp = g_file_mappings[i].bionic_fp;
            pthread_mutex_unlock(&g_file_mutex);
            return fp;
        }
    }
    pthread_mutex_unlock(&g_file_mutex);
    
    /* For pointers not in our mapping, check if address looks valid before dereferencing */
    /* On Android/ARM64, MTE (Memory Tagging Extension) uses high bits as memory tags */
    /* Real address is in lower 48 bits, high 8 bits may be MTE tags (e.g., 0xb4...) */
    uintptr_t addr = (uintptr_t)glibc_fp;
    uintptr_t real_addr = addr & 0x0000FFFFFFFFFFFFULL;  /* Mask off top 16 bits for MTE tags */
    if (real_addr < 0x100000) {
        /* Address too low - likely invalid */
        return (FILE*)glibc_fp;
    }
    
    /* Check if it looks like a glibc FILE* by checking the magic number in _flags */
    glibc_IO_FILE* gfile = (glibc_IO_FILE*)glibc_fp;
    
    /* Only treat as glibc FILE* if it has the correct magic number */
    if ((gfile->_flags & _IO_MAGIC_MASK) == _IO_MAGIC) {
        /* This is a glibc FILE* - check fileno for standard streams */
        if (gfile->_fileno >= 0 && gfile->_fileno < 1024) {
            if (gfile->_fileno == STDIN_FILENO) return stdin;
            if (gfile->_fileno == STDOUT_FILENO) return stdout;
            if (gfile->_fileno == STDERR_FILENO) return stderr;
            
#ifdef __ANDROID__
            __android_log_print(ANDROID_LOG_DEBUG, "GLIBC_BRIDGE_STDIO", 
                "Unknown glibc FILE* %p with fd=%d", glibc_fp, gfile->_fileno);
#endif
        }
    }
    
    /* Not a glibc FILE* or not found - assume it's already bionic FILE* */
    return (FILE*)glibc_fp;
}

/* Add a file mapping */
static void* add_file_mapping(FILE* bionic_fp) {
    if (!bionic_fp) return NULL;
    
    pthread_mutex_lock(&g_file_mutex);
    
    /* Find empty slot */
    for (int i = 3; i < MAX_FILE_MAPPINGS; i++) {  /* Skip 0-2 for std streams */
        if (g_file_mappings[i].glibc_fp == NULL) {
            /* Allocate fake glibc FILE structure */
            glibc_IO_FILE* fake = (glibc_IO_FILE*)calloc(1, sizeof(glibc_IO_FILE));
            if (!fake) {
                pthread_mutex_unlock(&g_file_mutex);
                return NULL;
            }
            
            fake->_fileno = fileno(bionic_fp);
            
            g_file_mappings[i].glibc_fp = fake;
            g_file_mappings[i].bionic_fp = bionic_fp;
            g_file_mappings[i].is_standard = 0;
            
            pthread_mutex_unlock(&g_file_mutex);
            return fake;
        }
    }
    
    pthread_mutex_unlock(&g_file_mutex);
    return NULL;  /* No free slots */
}

/* Remove a file mapping */
static void remove_file_mapping(void* glibc_fp) {
    if (!glibc_fp) return;
    
    pthread_mutex_lock(&g_file_mutex);
    for (int i = 3; i < MAX_FILE_MAPPINGS; i++) {  /* Don't remove std streams */
        if (g_file_mappings[i].glibc_fp == glibc_fp) {
            free(g_file_mappings[i].glibc_fp);
            g_file_mappings[i].glibc_fp = NULL;
            g_file_mappings[i].bionic_fp = NULL;
            break;
        }
    }
    pthread_mutex_unlock(&g_file_mutex);
}

/* ============================================================================
 * stdio wrapper functions
 * These are called by glibc programs
 * ============================================================================ */

/* Get pointers to our fake glibc standard streams */
void* glibc_bridge_get_stdin(void) {
    glibc_bridge_stdio_init();
    /* Return address of the FILE* variable, not the FILE* itself.
     * Programs will read from this address to get the actual FILE* value.
     * Example: FILE* fp = stdin; -> fp = *(&g_stdin_ptr) = &g_glibc_stdin */
    return &g_stdin_ptr;
}

void* glibc_bridge_get_stdout(void) {
    glibc_bridge_stdio_init();
    /* Return address of the FILE* variable, not the FILE* itself.
     * Programs will read from this address to get the actual FILE* value.
     * Example: FILE* fp = stdout; -> fp = *(&g_stdout_ptr) = &g_glibc_stdout */
    return &g_stdout_ptr;
}

void* glibc_bridge_get_stderr(void) {
    glibc_bridge_stdio_init();
    /* Return address of the FILE* variable, not the FILE* itself.
     * Programs will read from this address to get the actual FILE* value.
     * Example: FILE* fp = stderr; -> fp = *(&g_stderr_ptr) = &g_glibc_stderr */
    return &g_stderr_ptr;
}

/* Get direct access to the glibc _IO_FILE structures 
 * Used for _IO_2_1_stdout_ etc symbols which reference the structure directly */
void* glibc_bridge_get_glibc_stdin_struct(void) {
    glibc_bridge_stdio_init();
    return &g_glibc_stdin;
}

void* glibc_bridge_get_glibc_stdout_struct(void) {
    glibc_bridge_stdio_init();
    return &g_glibc_stdout;
}

void* glibc_bridge_get_glibc_stderr_struct(void) {
    glibc_bridge_stdio_init();
    return &g_glibc_stderr;
}

/* fopen internal - called by wrapper_stat.c with translated path */
void* glibc_bridge_fopen_internal(const char* path, const char* mode) {
    glibc_bridge_stdio_init();
    FILE* fp = fopen(path, mode);
    if (!fp) return NULL;
    return add_file_mapping(fp);
}

void* glibc_bridge_fopen64_internal(const char* path, const char* mode) {
    return glibc_bridge_fopen_internal(path, mode);  /* bionic doesn't distinguish */
}

/* tmpfile wrapper - creates temporary file and adds to mapping table */
void* tmpfile_wrapper(void) {
    glibc_bridge_stdio_init();
    FILE* fp = tmpfile();
    if (!fp) return NULL;
    return add_file_mapping(fp);
}

/* tmpfile64 wrapper */
void* tmpfile64_wrapper(void) {
    return tmpfile_wrapper();  /* bionic doesn't distinguish */
}

void* glibc_bridge_freopen_internal(const char* path, const char* mode, void* stream) {
    glibc_bridge_stdio_init();
    FILE* bionic_fp = glibc_bridge_get_bionic_fp(stream);
    FILE* new_fp = freopen(path, mode, bionic_fp);
    if (!new_fp) return NULL;
    
    /* If stream was a mapped file, update the mapping */
    if (stream != &g_glibc_stdin && stream != &g_glibc_stdout && 
        stream != &g_glibc_stderr) {
        /* The mapping is still valid, bionic_fp was modified in place */
        return stream;
    }
    
    return stream;
}

/* fclose wrapper */
int fclose_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) {
        errno = EBADF;
        return EOF;
    }
    int ret = fclose(fp);
    
    /* Remove mapping if it exists */
    if (stream != &g_glibc_stdin && stream != &g_glibc_stdout && 
        stream != &g_glibc_stderr) {
        remove_file_mapping(stream);
    }
    
    return ret;
}

/* Read operations */
size_t fread_wrapper(void* ptr, size_t size, size_t count, void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return 0; }
    return fread(ptr, size, count, fp);
}

char* fgets_wrapper(char* str, int n, void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return NULL; }
    return fgets(str, n, fp);
}

int fgetc_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return fgetc(fp);
}

int getc_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return getc(fp);
}

int ungetc_wrapper(int c, void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return ungetc(c, fp);
}

/* Write operations */
size_t fwrite_wrapper(const void* ptr, size_t size, size_t count, void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { 
        errno = EBADF; 
        return 0; 
    }
    return fwrite(ptr, size, count, fp);
}

int fputs_wrapper(const char* str, void* stream) {
    static int debug_count = 0;
    /* Always log first 10 calls and any stderr calls */
    if (debug_count < 10 || stream == &g_glibc_stderr) {
        char buf[128];
        snprintf(buf, sizeof(buf), "[FPUTS] stream=%p stderr=%p\n", stream, (void*)&g_glibc_stderr);
        write(STDERR_FILENO, buf, strlen(buf));
        debug_count++;
    }
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return fputs(str, fp);
}

int puts_wrapper(const char* str) {
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_BRIDGE", "[puts] %s", str ? str : "(null)");
#endif
    if (str) {
        return puts(str);
    }
    return -1;
}

int printf_wrapper(const char* format, ...) {
    va_list args;
    va_start(args, format);
#ifdef __ANDROID__
    /* Format to buffer and log to logcat */
    char buf[1024];
    int len = vsnprintf(buf, sizeof(buf), format, args);
    va_end(args);
    if (len > 0) {
        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_BRIDGE", "[printf] %s", buf);
    }
    /* Also print to stdout */
    va_start(args, format);
    int ret = vprintf(format, args);
    va_end(args);
    return ret;
#else
    int ret = vprintf(format, args);
    va_end(args);
    return ret;
#endif
}

int vprintf_wrapper(const char* format, va_list args) {
#ifdef __ANDROID__
    char buf[1024];
    va_list args_copy;
    va_copy(args_copy, args);
    int len = vsnprintf(buf, sizeof(buf), format, args_copy);
    va_end(args_copy);
    if (len > 0) {
        __android_log_print(ANDROID_LOG_INFO, "GLIBC_BRIDGE_BRIDGE", "[printf] %s", buf);
    }
#endif
    return vprintf(format, args);
}

int fputc_wrapper(int c, void* stream) {
    static int debug_count = 0;
    /* Always log first 10 calls and any stderr calls */
    if (debug_count < 10 || stream == &g_glibc_stderr) {
        char buf[128];
        snprintf(buf, sizeof(buf), "[FPUTC] c=%d stream=%p stderr=%p\n", c, stream, (void*)&g_glibc_stderr);
        write(STDERR_FILENO, buf, strlen(buf));
        debug_count++;
    }
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return fputc(c, fp);
}

int putc_wrapper(int c, void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return putc(c, fp);
}

/* Formatted I/O */
int fprintf_wrapper(void* stream, const char* format, ...) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    va_list args;
    va_start(args, format);
    int ret = vfprintf(fp, format, args);
    va_end(args);
    return ret;
}

int vfprintf_wrapper(void* stream, const char* format, va_list args) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return vfprintf(fp, format, args);
}

int fscanf_wrapper(void* stream, const char* format, ...) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    va_list args;
    va_start(args, format);
    int ret = vfscanf(fp, format, args);
    va_end(args);
    return ret;
}

int vfscanf_wrapper(void* stream, const char* format, va_list args) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return vfscanf(fp, format, args);
}

/* Position operations */
int fseek_wrapper(void* stream, long offset, int whence) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return fseek(fp, offset, whence);
}

int fseeko_wrapper(void* stream, off_t offset, int whence) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return fseeko(fp, offset, whence);
}

int fseeko64_wrapper(void* stream, off64_t offset, int whence) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return fseeko64(fp, offset, whence);
}

long ftell_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1L; }
    return ftell(fp);
}

off_t ftello_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return ftello(fp);
}

off64_t ftello64_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return ftello64(fp);
}

void rewind_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (fp) rewind(fp);
}

int fgetpos_wrapper(void* stream, fpos_t* pos) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return fgetpos(fp, pos);
}

int fsetpos_wrapper(void* stream, const fpos_t* pos) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return fsetpos(fp, pos);
}

/* Status operations */
int fflush_wrapper(void* stream) {
    if (!stream) return fflush(NULL);  /* Flush all */
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return fflush(fp);
}

int feof_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) return 0;
    return feof(fp);
}

int ferror_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) return 1;  /* Return error if invalid stream */
    return ferror(fp);
}

void clearerr_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (fp) clearerr(fp);
}

int fileno_wrapper(void* stream) {
    
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) {
        errno = EBADF;
        return -1;
    }
    return fileno(fp);
}

/* Buffer operations */
int setvbuf_wrapper(void* stream, char* buf, int mode, size_t size) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return -1; }
    return setvbuf(fp, buf, mode, size);
}

void setbuf_wrapper(void* stream, char* buf) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (fp) setbuf(fp, buf);
}

void setbuffer_wrapper(void* stream, char* buf, size_t size) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (fp) setvbuf(fp, buf, buf ? _IOFBF : _IONBF, size);
}

void setlinebuf_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (fp) setvbuf(fp, NULL, _IOLBF, 0);
}

/* Lock operations (for thread safety) */
void flockfile_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (fp) flockfile(fp);
}

void funlockfile_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (fp) funlockfile(fp);
}

int ftrylockfile_wrapper(void* stream) {
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) return -1;
    return ftrylockfile(fp);
}

/* Misc */
int __uflow_wrapper(void* stream) {
    /* __uflow is internal glibc function, use fgetc instead */
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return fgetc(fp);
}

int __overflow_wrapper(void* stream, int c) {
    static int debug_count = 0;
    if (debug_count < 10 || stream == &g_glibc_stderr) {
        char buf[128];
        snprintf(buf, sizeof(buf), "[OVERFLOW] c=%d stream=%p stderr=%p\n", c, stream, (void*)&g_glibc_stderr);
        write(STDERR_FILENO, buf, strlen(buf));
        debug_count++;
    }
    /* __overflow is internal glibc function, use fputc instead */
    FILE* fp = glibc_bridge_get_bionic_fp(stream);
    if (!fp) { errno = EBADF; return EOF; }
    return fputc(c, fp);
}

