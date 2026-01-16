/*
 * glibc-bridge ELF 共享库加载器
 * 
 * 加载 glibc 共享库（如 libstdc++.so.6）并使用我们的包装函数
 * 进行重定位。这允许 glibc C++ 程序在 Android/bionic 上运行
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <dlfcn.h>
#include <dirent.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <elf.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "../include/private.h"

/* 最大加载共享库数量 */
#define MAX_SHARED_LIBS 64

/* 包含 TLS 支持 - TLSDESC 解析器在 tls.c 中定义 */
#include "../compat/tls.h"

/* PT_GNU_EH_FRAME segment type */
#ifndef PT_GNU_EH_FRAME
#define PT_GNU_EH_FRAME 0x6474e550
#endif

/* Loaded shared library info */
typedef struct {
    char* name;              /* Library name (e.g., "libstdc++.so.6") */
    char* path;              /* Full path */
    void* base;              /* Load address */
    size_t size;             /* Total memory size */
    uintptr_t delta;         /* Load address delta */
    Elf64_Sym* symtab;       /* Symbol table */
    const char* strtab;      /* String table */
    size_t symcount;         /* Number of symbols */
    Elf64_Dyn* dynamic;      /* Dynamic section */
    int relocated;           /* Has been relocated? */
    /* For dl_iterate_phdr support */
    Elf64_Phdr* phdr;        /* Program headers (allocated copy) */
    uint16_t phnum;          /* Number of program headers */
    /* Cached eh_frame for fast _dl_find_object */
    void* eh_frame_hdr;      /* Cached PT_GNU_EH_FRAME address (NULL if not found) */
    int eh_frame_cached;     /* 1 if eh_frame_hdr has been looked up */
} shared_lib_t;

static shared_lib_t g_shared_libs[MAX_SHARED_LIBS];
static int g_shared_lib_count = 0;

/* Fake rootfs path (not static, accessed from glibc_bridge_core.c for API) */
char g_glibc_root[512] = {0};

/* Forward declarations */
extern void* glibc_bridge_resolve_symbol(const char* name);
extern void child_log(const char* msg);
extern int glibc_bridge_dl_get_log_level(void);

/* Log levels */
#define SHLIB_LOG_ERROR 1
#define SHLIB_LOG_WARN  2
#define SHLIB_LOG_INFO  3
#define SHLIB_LOG_DEBUG 4
#define SHLIB_LOG_TRACE 5

/* Helper for logging - only output if log level is high enough */
static void shlib_log(const char* msg) {
    /* Default to INFO level for backward compatibility */
    if (glibc_bridge_dl_get_log_level() >= SHLIB_LOG_INFO) {
        write(STDERR_FILENO, msg, strlen(msg));
    }
}

/* Debug level logging */
static void shlib_log_debug(const char* msg) {
    if (glibc_bridge_dl_get_log_level() >= SHLIB_LOG_DEBUG) {
        write(STDERR_FILENO, msg, strlen(msg));
    }
}

/* Symbol type flags for priority resolution */
#define SYM_TYPE_STDIO      0x01
#define SYM_TYPE_DL         0x02
#define SYM_TYPE_PRINTF     0x04
#define SYM_TYPE_DEBUG      0x08
#define SYM_TYPE_UNWIND     0x10

/* Check symbol type for priority wrapper resolution */
static int get_symbol_priority_type(const char* sym_name) {
    if (!sym_name) return 0;
    
    /* stdio symbols */
    if (strcmp(sym_name, "stdout") == 0 || strcmp(sym_name, "stderr") == 0 ||
        strcmp(sym_name, "stdin") == 0 ||
        strcmp(sym_name, "_IO_2_1_stdout_") == 0 ||
        strcmp(sym_name, "_IO_2_1_stderr_") == 0 ||
        strcmp(sym_name, "_IO_2_1_stdin_") == 0 ||
        strncmp(sym_name, "fwrite", 6) == 0 ||
        strncmp(sym_name, "fread", 5) == 0 ||
        strncmp(sym_name, "fflush", 6) == 0 ||
        strncmp(sym_name, "fprintf", 7) == 0 ||
        strncmp(sym_name, "fputc", 5) == 0 ||
        strncmp(sym_name, "fputs", 5) == 0 ||
        strncmp(sym_name, "__overflow", 10) == 0 ||
        strncmp(sym_name, "__uflow", 7) == 0 ||
        strcmp(sym_name, "__fsetlocking") == 0 ||
        strncmp(sym_name, "fopen", 5) == 0 ||
        strcmp(sym_name, "fclose") == 0 ||
        strncmp(sym_name, "fseek", 5) == 0 ||
        strncmp(sym_name, "ftell", 5) == 0 ||
        strcmp(sym_name, "fileno") == 0 ||
        strncmp(sym_name, "ferror", 6) == 0 ||
        strncmp(sym_name, "feof", 4) == 0 ||
        strncmp(sym_name, "clearerr", 8) == 0 ||
        strncmp(sym_name, "setvbuf", 7) == 0 ||
        strncmp(sym_name, "setbuf", 6) == 0) {
        return SYM_TYPE_STDIO;
    }
    
    /* dl functions */
    if (strcmp(sym_name, "dlsym") == 0 || 
        strcmp(sym_name, "dlopen") == 0 || 
        strcmp(sym_name, "dlclose") == 0 ||
        strcmp(sym_name, "dladdr") == 0) {
        return SYM_TYPE_DL;
    }
    
    /* printf family (MTE pointer fix) */
    if (strcmp(sym_name, "vsnprintf") == 0 ||
        strcmp(sym_name, "snprintf") == 0 ||
        strcmp(sym_name, "strtoull") == 0 ||
        strcmp(sym_name, "__isoc23_strtoull") == 0) {
        return SYM_TYPE_PRINTF;
    }
    
    /* debug/stack protection */
    if (strcmp(sym_name, "__stack_chk_fail") == 0 ||
        strcmp(sym_name, "abort") == 0) {
        return SYM_TYPE_DEBUG;
    }
    
    /* exception handling */
    if (strcmp(sym_name, "dl_iterate_phdr") == 0 ||
        strcmp(sym_name, "_dl_find_object") == 0) {
        return SYM_TYPE_UNWIND;
    }
    
    return 0;
}

/* Create directory recursively */
static int mkdir_recursive(const char* path) {
    char tmp[512];
    char* p = NULL;
    size_t len;
    
    snprintf(tmp, sizeof(tmp), "%s", path);
    len = strlen(tmp);
    if (len > 0 && tmp[len - 1] == '/') {
        tmp[len - 1] = 0;
    }
    
    for (p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = 0;
            mkdir(tmp, 0755);
            *p = '/';
        }
    }
    return mkdir(tmp, 0755);
}

/*
 * ============================================================================
 * Complete Fake GLIBC Rootfs Implementation
 * ============================================================================
 * 
 * Directory structure mimics a real Linux ARM64 system:
 *   /lib/                          -> main library directory
 *   /lib64 -> lib                  -> symlink for 64-bit
 *   /lib/aarch64-linux-gnu/        -> Debian/Ubuntu style
 *   /usr/lib/ -> ../lib            -> standard location
 *   /usr/lib64 -> lib              -> symlink
 *   /usr/lib/aarch64-linux-gnu/ -> ../../lib/aarch64-linux-gnu
 *   /etc/ld.so.conf                -> linker config (stub)
 *   /etc/ld.so.cache               -> linker cache (stub)
 */

/*
 * Complete list of glibc libraries - matching D:\btoglibc\usr\glibc\lib
 * 
 * Categories:
 * - STUB: Provided by bionic wrappers, create marker file
 * - REAL: Load actual glibc ELF, relocate with wrappers
 * - SKIP: Not needed for basic operation
 */

/* Core stub libraries - use bionic wrappers */
static const char* g_stub_libs_core[] = {
    /* Dynamic linker */
    "ld-linux-aarch64.so.1",
    
    /* Core C library */
    "libc.so.6",
    "libc.so",
    
    /* Math library */
    "libm.so.6",
    "libm.so",
    
    /* Thread library */
    "libpthread.so.0",
    "libpthread.a",
    
    /* Dynamic loading */
    "libdl.so.2",
    "libdl.a",
    
    /* Realtime */
    "librt.so.1",
    "librt.a",
    
    /* Utility (obsolete) */
    "libutil.so.1",
    "libutil.a",
    
    NULL
};

/* NSS (Name Service Switch) stub libraries */
static const char* g_stub_libs_nss[] = {
    "libnss_files.so.2",
    "libnss_dns.so.2",
    "libnss_compat.so",
    "libnss_compat.so.2",
    "libnss_db.so",
    "libnss_db.so.2",
    "libnss_hesiod.so",
    "libnss_hesiod.so.2",
    NULL
};

/* ICU library redirects - map glibc ICU to Android ICU */
static const struct {
    const char* prefix;      /* Match prefix (e.g. "libicuuc.so") */
    const char* android_lib; /* Android library path */
} g_icu_redirects[] = {
    {"libicuuc.so",   "/apex/com.android.i18n/lib64/libicuuc.so"},
    {"libicui18n.so", "/apex/com.android.i18n/lib64/libicui18n.so"},
    {"libicudata.so", "/apex/com.android.i18n/lib64/libicuuc.so"}, /* data embedded */
    {NULL, NULL}
};

/* Native library redirects - map glibc library names to Android native library names
 * These are compiled as Android native libs (e.g., gl4es, SDL2) */
static const struct {
    const char* glibc_prefix;   /* glibc library prefix to match */
    const char* android_name;   /* Android library name to load */
} g_native_lib_redirects[] = {
    {"libGL.so",      "libGL_gl4es.so"},     /* OpenGL -> gl4es */
    {"libGLU.so",     "libGL_gl4es.so"},     /* GLU -> gl4es (gl4es provides GLU) */
    {"libEGL.so",     "libEGL_gl4es.so"},    /* EGL -> gl4es EGL */
    {"libGLX.so",     "libGL_gl4es.so"},     /* GLX -> gl4es (provides GLX stubs) */
    {"libOpenGL.so",  "libGL_gl4es.so"},     /* OpenGL -> gl4es */
    {"libGLESv1",     NULL},                  /* GLES v1 - use system */
    {"libGLESv2",     NULL},                  /* GLES v2 - use system */
    {"libGLESv3",     NULL},                  /* GLES v3 - use system */
    {"libvulkan.so",  NULL},                  /* Vulkan - use system */
    {"libSDL2",       "libSDL2.so"},         /* SDL2 - use project SDL2 */
    {"libX11.so",     NULL},                  /* X11 - stub (not available) */
    {NULL, NULL}
};

/* Other stub libraries */
static const char* g_stub_libs_other[] = {
    /* Resolver */
    "libresolv.so",
    "libresolv.so.2",
    "libresolv.a",
    
    /* Async name lookup */
    "libanl.so",
    "libanl.so.1",
    "libanl.a",
    
    /* Thread debug */
    "libthread_db.so",
    "libthread_db.so.1",
    
    /* NIS/NIS+ */
    "libnsl.so.1",
    
    /* Broken locale */
    "libBrokenLocale.so",
    "libBrokenLocale.so.1",
    "libBrokenLocale.a",
    
    /* Malloc debug */
    "libc_malloc_debug.so",
    "libc_malloc_debug.so.0",
    
    /* Memory usage profiler */
    "libmemusage.so",
    
    /* PC profiler */
    "libpcprofile.so",
    
    /* Static libraries (stubs) */
    "libc.a",
    "libm.a",
    "libg.a",
    "libmcheck.a",
    "libc_nonshared.a",
    
    NULL
};

/* Sanitizer libraries - stub */
static const char* g_stub_libs_sanitizer[] = {
    "libasan.so",
    "libasan.so.8",
    "libasan.so.8.0.0",
    "liblsan.so",
    "liblsan.so.0",
    "liblsan.so.0.0.0",
    "libtsan.so",
    "libtsan.so.2",
    "libtsan.so.2.0.0",
    "libubsan.so",
    "libubsan.so.1",
    "libubsan.so.1.0.0",
    NULL
};

/* OpenMP and other GCC runtime - stub or real depending on need */
static const char* g_stub_libs_gcc[] = {
    "libgomp.so",
    "libgomp.so.1",
    "libgomp.so.1.0.0",
    "libitm.so",
    "libitm.so.1",
    "libitm.so.1.0.0",
    "libatomic.so",
    "libatomic.so.1",
    "libatomic.so.1.2.0",
    NULL
};

/* CRT objects - not libraries but needed for completeness */
static const char* g_crt_objects[] = {
    "crt1.o",
    "crti.o",
    "crtn.o",
    "gcrt1.o",
    "grcrt1.o",
    "Mcrt1.o",
    "rcrt1.o",
    "Scrt1.o",
    NULL
};

/* Real glibc libraries - load as ELF, relocate with wrappers */
/* Only list files that actually need to be copied (not symlinks) */
static const char* g_real_glibc_libs[] = {
    "libstdc++.so.6",
    "libgcc_s.so.1",
    NULL
};

/* Create a stub library marker file */
static int create_stub_lib(const char* dir, const char* name) {
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", dir, name);
    
    /* Skip if already exists */
    if (access(path, F_OK) == 0) return 0;
    
    int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0755);
    if (fd < 0) return -1;
    
    /* Write stub marker - minimal ELF-like header so tools don't complain */
    const char stub[] = "BTA64STUB\0";
    write(fd, stub, sizeof(stub));
    close(fd);
    return 0;
}

/* Create symlink helper - handles existing directories */
static void create_symlink(const char* target, const char* link_path) {
    struct stat st;
    if (lstat(link_path, &st) == 0) {
        if (S_ISDIR(st.st_mode)) {
            /* If it's a directory, try to remove it (only if empty) */
            rmdir(link_path);
        } else {
            /* Remove file or existing symlink */
            unlink(link_path);
        }
    }
    symlink(target, link_path);
}

/* Initialize fake glibc rootfs structure */
int glibc_bridge_init_glibc_root(const char* app_files_dir) {
    char buf[512];
    char path[512];
    
    if (!app_files_dir || !app_files_dir[0]) {
        shlib_log("[FAKEFS] No app files dir\n");
        return -1;
    }
    
    /* Use the provided path directly as glibc_root
     * This is typically the extracted rootfs.tar.xz directory (e.g., filesDir/rootfs)
     * which already contains a complete Linux filesystem structure */
    strncpy(g_glibc_root, app_files_dir, sizeof(g_glibc_root) - 1);
    g_glibc_root[sizeof(g_glibc_root) - 1] = '\0';
    
    snprintf(buf, sizeof(buf), "[FAKEFS] Initializing glibc rootfs at: %s\n", g_glibc_root);
    shlib_log_debug(buf);
    
    /* Check if this is an already extracted rootfs (has /usr/lib directory) */
    snprintf(path, sizeof(path), "%s/usr/lib", g_glibc_root);
    struct stat st;
    int is_extracted_rootfs = (stat(path, &st) == 0 && S_ISDIR(st.st_mode));
    
    if (is_extracted_rootfs) {
        snprintf(buf, sizeof(buf), "[FAKEFS] Detected extracted rootfs, skipping directory creation\n");
        shlib_log_debug(buf);
    }
    
    /* ========================================
     * Create directory structure (only if not extracted rootfs)
     * ======================================== */
    
    if (!is_extracted_rootfs) {
        /* /lib - main library directory */
        snprintf(path, sizeof(path), "%s/lib", g_glibc_root);
        mkdir_recursive(path);
        
        /* /lib/aarch64-linux-gnu - Debian/Ubuntu style */
        snprintf(path, sizeof(path), "%s/lib/aarch64-linux-gnu", g_glibc_root);
        mkdir_recursive(path);
        
        /* /lib64 -> lib */
        snprintf(path, sizeof(path), "%s/lib64", g_glibc_root);
        create_symlink("lib", path);
        
        /* /usr directory */
        snprintf(path, sizeof(path), "%s/usr", g_glibc_root);
        mkdir_recursive(path);
        
        /* /usr/lib -> ../lib */
        snprintf(path, sizeof(path), "%s/usr/lib", g_glibc_root);
        create_symlink("../lib", path);
        
        /* /usr/lib64 -> lib */
        snprintf(path, sizeof(path), "%s/usr/lib64", g_glibc_root);
        create_symlink("lib", path);
        
        /* /etc directory */
        snprintf(path, sizeof(path), "%s/etc", g_glibc_root);
        mkdir_recursive(path);
        
        /* /tmp directory */
        snprintf(path, sizeof(path), "%s/tmp", g_glibc_root);
        mkdir_recursive(path);
        
        /* /var/tmp directory */
        snprintf(path, sizeof(path), "%s/var/tmp", g_glibc_root);
        mkdir_recursive(path);
        
        /* /proc - will be bind-mounted or simulated */
        snprintf(path, sizeof(path), "%s/proc", g_glibc_root);
        mkdir_recursive(path);
        
        /* /sys - will be bind-mounted or simulated */
        snprintf(path, sizeof(path), "%s/sys", g_glibc_root);
        mkdir_recursive(path);
        
        /* /dev - will be bind-mounted or simulated */
        snprintf(path, sizeof(path), "%s/dev", g_glibc_root);
        mkdir_recursive(path);
        
        shlib_log_debug("[FAKEFS] Directory structure created\n");
    }
    
    /* ========================================
     * Ensure critical symlinks exist (even for extracted rootfs)
     * Some tar extractors may not preserve symlinks correctly on Android
     * ======================================== */
    
    /* /lib64 -> lib (if lib64 is an empty directory, replace with symlink) */
    snprintf(path, sizeof(path), "%s/lib64", g_glibc_root);
    if (lstat(path, &st) == 0) {
        if (S_ISDIR(st.st_mode)) {
            /* Check if directory is empty */
            DIR* dir = opendir(path);
            if (dir) {
                int is_empty = 1;
                struct dirent* entry;
                while ((entry = readdir(dir)) != NULL) {
                    if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0) {
                        is_empty = 0;
                        break;
                    }
                }
                closedir(dir);
                
                if (is_empty) {
                    rmdir(path);
                    create_symlink("lib", path);
                    shlib_log_debug("[FAKEFS] Replaced empty lib64 directory with symlink\n");
                }
            }
        }
    } else {
        /* lib64 doesn't exist, create symlink */
        create_symlink("lib", path);
    }
    
    /* ========================================
     * Create stub libraries in /lib (only if NOT extracted rootfs)
     * For extracted rootfs, real glibc libraries already exist
     * ======================================== */
    char lib_dir[512];
    snprintf(lib_dir, sizeof(lib_dir), "%s/lib", g_glibc_root);
    
    char gnu_dir[512];
    snprintf(gnu_dir, sizeof(gnu_dir), "%s/lib/aarch64-linux-gnu", g_glibc_root);
    
    if (!is_extracted_rootfs) {
        int stub_count = 0;
        
        /* Core libraries */
        shlib_log_debug("[FAKEFS] Creating core stub libraries...\n");
        for (int i = 0; g_stub_libs_core[i]; i++) {
            create_stub_lib(lib_dir, g_stub_libs_core[i]);
            create_stub_lib(gnu_dir, g_stub_libs_core[i]);
            stub_count++;
        }
        
        /* NSS libraries */
        shlib_log_debug("[FAKEFS] Creating NSS stub libraries...\n");
        for (int i = 0; g_stub_libs_nss[i]; i++) {
            create_stub_lib(lib_dir, g_stub_libs_nss[i]);
            create_stub_lib(gnu_dir, g_stub_libs_nss[i]);
            stub_count++;
        }
        
        /* Other libraries */
        shlib_log_debug("[FAKEFS] Creating other stub libraries...\n");
        for (int i = 0; g_stub_libs_other[i]; i++) {
            create_stub_lib(lib_dir, g_stub_libs_other[i]);
            create_stub_lib(gnu_dir, g_stub_libs_other[i]);
            stub_count++;
        }
        
        /* Sanitizer libraries */
        shlib_log_debug("[FAKEFS] Creating sanitizer stub libraries...\n");
        for (int i = 0; g_stub_libs_sanitizer[i]; i++) {
            create_stub_lib(lib_dir, g_stub_libs_sanitizer[i]);
            stub_count++;
        }
        
        /* GCC runtime libraries */
        shlib_log_debug("[FAKEFS] Creating GCC runtime stub libraries...\n");
        for (int i = 0; g_stub_libs_gcc[i]; i++) {
            create_stub_lib(lib_dir, g_stub_libs_gcc[i]);
            stub_count++;
        }
        
        /* CRT objects */
        shlib_log_debug("[FAKEFS] Creating CRT objects...\n");
        for (int i = 0; g_crt_objects[i]; i++) {
            create_stub_lib(lib_dir, g_crt_objects[i]);
            stub_count++;
        }
        
        snprintf(buf, sizeof(buf), "[FAKEFS] Created %d stub files\n", stub_count);
        shlib_log_debug(buf);
        
        /* ========================================
         * Create gconv directory (character conversion)
         * ======================================== */
        char gconv_dir[512];
        snprintf(gconv_dir, sizeof(gconv_dir), "%s/lib/gconv", g_glibc_root);
        mkdir_recursive(gconv_dir);
        
        /* Create gconv-modules file */
        snprintf(path, sizeof(path), "%s/gconv-modules", gconv_dir);
        int gconv_fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (gconv_fd >= 0) {
            const char* gconv_header = "# glibc-bridge gconv modules stub\n# Character conversions handled by bionic\n";
            write(gconv_fd, gconv_header, strlen(gconv_header));
            close(gconv_fd);
        }
        
        /* Create gconv-modules.d directory */
        snprintf(path, sizeof(path), "%s/gconv-modules.d", gconv_dir);
        mkdir_recursive(path);
        
        shlib_log_debug("[FAKEFS] Created gconv directory\n");
    } else {
        shlib_log_debug("[FAKEFS] Skipping stub library creation (using extracted rootfs)\n");
    }
    
    /* ========================================
     * Create locale and config files (only if NOT extracted rootfs)
     * For extracted rootfs, these files already exist
     * ======================================== */
    if (!is_extracted_rootfs) {
        /* Create locale directory */
        char locale_dir[512];
        snprintf(locale_dir, sizeof(locale_dir), "%s/lib/locale", g_glibc_root);
        mkdir_recursive(locale_dir);
        
        /* Create locale-archive stub */
        snprintf(path, sizeof(path), "%s/locale-archive", locale_dir);
        int locale_fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (locale_fd >= 0) {
            const char* locale_stub = "GLIBC_BRIDGE_LOCALE";
            write(locale_fd, locale_stub, strlen(locale_stub));
            close(locale_fd);
        }
        
        /* Create common locale directories */
        const char* common_locales[] = {
            "C.UTF-8", "en_US.UTF-8", "POSIX", NULL
        };
        for (int i = 0; common_locales[i]; i++) {
            snprintf(path, sizeof(path), "%s/%s", locale_dir, common_locales[i]);
            mkdir_recursive(path);
        }
        
        shlib_log_debug("[FAKEFS] Created locale directory\n");
        
        /* Create getconf directory */
        char getconf_dir[512];
        snprintf(getconf_dir, sizeof(getconf_dir), "%s/lib/getconf", g_glibc_root);
        mkdir_recursive(getconf_dir);
        
        /* Create config files */
        
        /* /etc/ld.so.conf */
        snprintf(path, sizeof(path), "%s/etc/ld.so.conf", g_glibc_root);
        int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            const char* conf = "/lib\n/lib/aarch64-linux-gnu\n/usr/lib\n";
            write(fd, conf, strlen(conf));
            close(fd);
        }
        
        /* /etc/ld.so.cache - empty stub */
        snprintf(path, sizeof(path), "%s/etc/ld.so.cache", g_glibc_root);
        fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) close(fd);
        
        /* /etc/passwd - minimal */
        snprintf(path, sizeof(path), "%s/etc/passwd", g_glibc_root);
        fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            const char* passwd = "root:x:0:0:root:/root:/bin/sh\nnobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin\n";
            write(fd, passwd, strlen(passwd));
            close(fd);
        }
        
        /* /etc/group - minimal */
        snprintf(path, sizeof(path), "%s/etc/group", g_glibc_root);
        fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            const char* group = "root:x:0:\nnogroup:x:65534:\n";
            write(fd, group, strlen(group));
            close(fd);
        }
        
        /* /etc/nsswitch.conf */
        snprintf(path, sizeof(path), "%s/etc/nsswitch.conf", g_glibc_root);
        fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            const char* nsswitch = 
                "passwd:     files\n"
                "group:      files\n"
                "shadow:     files\n"
                "hosts:      files dns\n"
                "networks:   files\n"
                "protocols:  files\n"
                "services:   files\n"
                "ethers:     files\n"
                "rpc:        files\n";
            write(fd, nsswitch, strlen(nsswitch));
            close(fd);
        }
        
        /* /etc/gai.conf */
        snprintf(path, sizeof(path), "%s/etc/gai.conf", g_glibc_root);
        fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            const char* gai = "# glibc-bridge gai.conf\nprecedence ::ffff:0:0/96 100\n";
            write(fd, gai, strlen(gai));
            close(fd);
        }
        
        /* /etc/locale.gen */
        snprintf(path, sizeof(path), "%s/etc/locale.gen", g_glibc_root);
        fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            const char* locale_gen = "en_US.UTF-8 UTF-8\nC.UTF-8 UTF-8\n";
            write(fd, locale_gen, strlen(locale_gen));
            close(fd);
        }
        
        shlib_log_debug("[FAKEFS] Config files created\n");
    }
    
    /* ========================================
     * Create symlinks to host system files (always needed)
     * These allow the rootfs to access host network config, etc.
     * ======================================== */
    
    /* /etc/localtime -> /etc/localtime on host (or UTC) */
    snprintf(path, sizeof(path), "%s/etc/localtime", g_glibc_root);
    if (access(path, F_OK) != 0) {
        create_symlink("/etc/localtime", path);
    }
    
    /* /etc/resolv.conf -> host resolv.conf */
    snprintf(path, sizeof(path), "%s/etc/resolv.conf", g_glibc_root);
    if (access(path, F_OK) != 0) {
        create_symlink("/etc/resolv.conf", path);
    }
    
    /* /etc/hosts -> host */
    snprintf(path, sizeof(path), "%s/etc/hosts", g_glibc_root);
    if (access(path, F_OK) != 0) {
        create_symlink("/etc/hosts", path);
    }
    
    /* /etc/environment - only create if not exists */
    snprintf(path, sizeof(path), "%s/etc/environment", g_glibc_root);
    if (access(path, F_OK) != 0) {
        int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            const char* env = "LANG=C.UTF-8\nLC_ALL=C.UTF-8\n";
            write(fd, env, strlen(env));
            close(fd);
        }
    }
    
    shlib_log_debug("[FAKEFS] Rootfs initialization complete\n");
    
    snprintf(buf, sizeof(buf), "[FAKEFS] Complete! Root: %s\n", g_glibc_root);
    shlib_log_debug(buf);
    
    return 0;
}

/* Copy a library from app files to glibc-root/lib */
int glibc_bridge_install_glibc_lib(const char* app_files_dir, const char* libname) {
    char buf[512];
    char src[512], dst[512];
    
    snprintf(src, sizeof(src), "%s/%s", app_files_dir, libname);
    snprintf(dst, sizeof(dst), "%s/lib/%s", g_glibc_root, libname);
    
    /* Check if source exists */
    if (access(src, R_OK) != 0) {
        snprintf(buf, sizeof(buf), "[FAKEFS] Source not found: %s\n", src);
        shlib_log_debug(buf);
        return -1;
    }
    
    /* Copy file */
    int src_fd = open(src, O_RDONLY);
    if (src_fd < 0) return -1;
    
    int dst_fd = open(dst, O_CREAT | O_WRONLY | O_TRUNC, 0755);
    if (dst_fd < 0) {
        close(src_fd);
        return -1;
    }
    
    char buffer[8192];
    ssize_t n;
    while ((n = read(src_fd, buffer, sizeof(buffer))) > 0) {
        write(dst_fd, buffer, n);
    }
    
    close(src_fd);
    close(dst_fd);
    
    snprintf(buf, sizeof(buf), "[FAKEFS] Installed: %s\n", libname);
    shlib_log_debug(buf);
    return 0;
}

/* Get glibc root path */
const char* glibc_bridge_get_glibc_root(void) {
    return g_glibc_root[0] ? g_glibc_root : NULL;
}

/* Setup complete fake rootfs environment */
int glibc_bridge_setup_fake_rootfs(const char* app_files_dir) {
    char buf[512];
    
    /* IMPORTANT: Reset shared library state before setting up rootfs
     * This is critical when running multiple ELFs in sequence (e.g., dotnet --info then dotnet app.dll)
     * because fork() inherits parent's memory state but the addresses may be invalid in child */
    for (int i = 0; i < g_shared_lib_count; i++) {
        /* Don't free - just reset pointers since memory layout may differ */
        g_shared_libs[i].base = NULL;
        g_shared_libs[i].symtab = NULL;
        g_shared_libs[i].strtab = NULL;
        g_shared_libs[i].dynamic = NULL;
        g_shared_libs[i].relocated = 0;
    }
    g_shared_lib_count = 0;
    
    /* Initialize directory structure and stub libs */
    if (glibc_bridge_init_glibc_root(app_files_dir) != 0) {
        return -1;
    }
    
    /* Install real glibc libraries from app files (if available) */
    shlib_log_debug("[FAKEFS] Checking for real glibc libraries...\n");
    int libs_installed = 0;
    for (int i = 0; g_real_glibc_libs[i]; i++) {
        char src[512];
        snprintf(src, sizeof(src), "%s/%s", app_files_dir, g_real_glibc_libs[i]);
        if (access(src, R_OK) == 0) {
            if (glibc_bridge_install_glibc_lib(app_files_dir, g_real_glibc_libs[i]) == 0) {
                snprintf(buf, sizeof(buf), "[FAKEFS]   installed: %s\n", g_real_glibc_libs[i]);
                shlib_log_debug(buf);
                libs_installed++;
            }
        } else {
            snprintf(buf, sizeof(buf), "[FAKEFS]   not found: %s (using wrappers)\n", g_real_glibc_libs[i]);
            shlib_log_debug(buf);
        }
    }
    
    if (libs_installed > 0) {
        /* Create version-specific symlinks only if libraries were installed */
        char lib_dir[512];
        snprintf(lib_dir, sizeof(lib_dir), "%s/lib", g_glibc_root);
        
        /* libstdc++.so -> libstdc++.so.6 */
        char link_path[512];
        snprintf(link_path, sizeof(link_path), "%s/libstdc++.so", lib_dir);
        create_symlink("libstdc++.so.6", link_path);
        
        /* libgcc_s.so -> libgcc_s.so.1 */
        snprintf(link_path, sizeof(link_path), "%s/libgcc_s.so", lib_dir);
        create_symlink("libgcc_s.so.1", link_path);
    }
    
    shlib_log_debug("[FAKEFS] Fake rootfs setup complete!\n");
    
    /* Print summary */
    snprintf(buf, sizeof(buf), 
        "[FAKEFS] Summary:\n"
        "[FAKEFS]   Root: %s\n"
        "[FAKEFS]   Libraries: %s/lib/\n"
        "[FAKEFS]   Config: %s/etc/\n",
        g_glibc_root, g_glibc_root, g_glibc_root);
    shlib_log_debug(buf);
    
    return 0;
}

/* Get library search paths for the fake rootfs */
const char* glibc_bridge_get_library_paths(void) {
    static char paths[1024];
    if (g_glibc_root[0]) {
        snprintf(paths, sizeof(paths), 
            "%s/lib:%s/lib/aarch64-linux-gnu:%s/usr/lib",
            g_glibc_root, g_glibc_root, g_glibc_root);
        return paths;
    }
    return NULL;
}

/* Resolve a library name to full path in fake rootfs */
int glibc_bridge_resolve_lib_path(const char* libname, char* out, size_t out_size) {
    if (!libname || !out || !g_glibc_root[0]) return -1;
    
    const char* basename = strrchr(libname, '/');
    basename = basename ? basename + 1 : libname;
    
    /* Try /lib first */
    snprintf(out, out_size, "%s/lib/%s", g_glibc_root, basename);
    if (access(out, R_OK) == 0) return 0;
    
    /* Try /lib/aarch64-linux-gnu */
    snprintf(out, out_size, "%s/lib/aarch64-linux-gnu/%s", g_glibc_root, basename);
    if (access(out, R_OK) == 0) return 0;
    
    return -1;
}

/* Find a loaded library by name */
static shared_lib_t* find_shared_lib(const char* name) {
    for (int i = 0; i < g_shared_lib_count; i++) {
        if (g_shared_libs[i].name && strcmp(g_shared_libs[i].name, name) == 0) {
            return &g_shared_libs[i];
        }
        /* Also check basename */
        if (g_shared_libs[i].path) {
            const char* basename = strrchr(g_shared_libs[i].path, '/');
            basename = basename ? basename + 1 : g_shared_libs[i].path;
            if (strcmp(basename, name) == 0) {
                return &g_shared_libs[i];
            }
        }
    }
    return NULL;
}

/* GNU UNIQUE binding (for locale facet ids, etc.) */
#ifndef STB_GNU_UNIQUE
#define STB_GNU_UNIQUE 10
#endif

/* Internal: resolve symbol from loaded shared libraries 
 * If check_relocated is true, only search relocated libraries (for dlsym)
 * If false, search all loaded libraries (for relocation process) */
static void* resolve_from_shared_libs_internal(const char* name, int check_relocated) {
    if (!name || !name[0]) return NULL;
    
    for (int i = 0; i < g_shared_lib_count; i++) {
        shared_lib_t* lib = &g_shared_libs[i];
        
        /* Skip libraries that are not fully loaded */
        if (!lib->symtab || !lib->strtab || !lib->base) continue;
        if (lib->symcount == 0) continue;
        
        /* Optionally skip non-relocated libraries */
        if (check_relocated && !lib->relocated) continue;
        
        /* Calculate strtab bounds for safety */
        uintptr_t lib_start = (uintptr_t)lib->base;
        uintptr_t lib_end = lib_start + lib->size;
        uintptr_t strtab_addr = (uintptr_t)lib->strtab;
        
        /* Verify strtab is within library bounds */
        if (strtab_addr < lib_start || strtab_addr >= lib_end) continue;
        
        /* Calculate actual symbol count from memory layout */
        size_t actual_symcount = lib->symcount;
        size_t max_possible = lib->size / sizeof(Elf64_Sym);  /* Based on library size */
        if ((uintptr_t)lib->strtab > (uintptr_t)lib->symtab) {
            size_t layout_count = ((uintptr_t)lib->strtab - (uintptr_t)lib->symtab) / sizeof(Elf64_Sym);
            if (layout_count > actual_symcount && layout_count <= max_possible) {
                actual_symcount = layout_count;
            }
        }
        
        for (size_t j = 0; j < actual_symcount; j++) {
            Elf64_Sym* sym = &lib->symtab[j];
            
            /* Skip undefined or weak undefined symbols */
            if (sym->st_shndx == SHN_UNDEF) continue;
            if (sym->st_value == 0) continue;
            
            /* Check binding - we want GLOBAL, WEAK, or GNU_UNIQUE */
            unsigned char bind = ELF64_ST_BIND(sym->st_info);
            if (bind != STB_GLOBAL && bind != STB_WEAK && bind != STB_GNU_UNIQUE) continue;
            
            /* Validate st_name - must be within strtab bounds */
            if (sym->st_name == 0) continue;
            
            /* Extra safety: limit st_name to reasonable value */
            if (sym->st_name > 0x200000) continue;  /* 2MB limit for strtab offset */
            
            uintptr_t name_addr = strtab_addr + sym->st_name;
            if (name_addr >= lib_end) continue;
            
            /* Extra safety: ensure we have at least 1 byte before lib_end */
            if (name_addr + 1 >= lib_end) continue;
            
            const char* sym_name = (const char*)name_addr;
            
            /* Use strncmp with a reasonable limit to avoid reading past end of memory */
            size_t max_len = lib_end - name_addr;
            if (max_len > 512) max_len = 512;
            
            if (strncmp(sym_name, name, max_len) == 0 && strlen(name) < max_len) {
                return (void*)(sym->st_value + lib->delta);
            }
        }
    }
    return NULL;
}

/* Resolve symbol from loaded shared libraries (for dlsym - only relocated libs) */
void* glibc_bridge_resolve_from_shared_libs(const char* name) {
    return resolve_from_shared_libs_internal(name, 1);
}

/* Load ELF shared library into memory */
static int load_elf_shlib(const char* path, shared_lib_t* lib) {
    char buf[512];
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        snprintf(buf, sizeof(buf), "[SHLIB] Cannot open: %s\n", path);
        shlib_log(buf);

        return -1;
    }

    /* Read ELF header */
    Elf64_Ehdr ehdr;
    if (read(fd, &ehdr, sizeof(ehdr)) != sizeof(ehdr)) {
        snprintf(buf, sizeof(buf), "[SHLIB] Failed to read ELF header: %s\n", path);
        shlib_log(buf);

        close(fd);
        return -1;
    }

    /* Verify ELF */
    if (memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0 ||
        ehdr.e_ident[EI_CLASS] != ELFCLASS64 ||
        ehdr.e_machine != EM_AARCH64 ||
        ehdr.e_type != ET_DYN) {
        snprintf(buf, sizeof(buf), "[SHLIB] Not a valid ARM64 shared library: %s\n", path);
        shlib_log(buf);

        close(fd);
        return -1;
    }
    
    /* Read program headers */
    Elf64_Phdr* phdrs = malloc(ehdr.e_phnum * sizeof(Elf64_Phdr));
    if (!phdrs) {
        close(fd);
        return -1;
    }
    
    lseek(fd, ehdr.e_phoff, SEEK_SET);
    if (read(fd, phdrs, ehdr.e_phnum * sizeof(Elf64_Phdr)) != 
        (ssize_t)(ehdr.e_phnum * sizeof(Elf64_Phdr))) {
        snprintf(buf, sizeof(buf), "[SHLIB] Failed to read program headers: %s\n", path);
        shlib_log(buf);
        free(phdrs);
        close(fd);
        return -1;
    }
    
    /* Calculate memory range needed */
    uintptr_t min_vaddr = UINTPTR_MAX;
    uintptr_t max_vaddr = 0;
    
    for (int i = 0; i < ehdr.e_phnum; i++) {
        if (phdrs[i].p_type == PT_LOAD) {
            if (phdrs[i].p_vaddr < min_vaddr) {
                min_vaddr = phdrs[i].p_vaddr;
            }
            uintptr_t end = phdrs[i].p_vaddr + phdrs[i].p_memsz;
            if (end > max_vaddr) {
                max_vaddr = end;
            }
        }
    }
    
    size_t total_size = max_vaddr - min_vaddr;
    total_size = (total_size + 4095) & ~4095;  /* Page align */
    
    /* Map memory for the library */
    void* base = mmap(NULL, total_size, PROT_READ | PROT_WRITE | PROT_EXEC,
                      MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (base == MAP_FAILED) {
        snprintf(buf, sizeof(buf), "[SHLIB] mmap failed for %s: %s\n", path, strerror(errno));
        shlib_log(buf);
        free(phdrs);
        close(fd);
        return -1;
    }
    
    lib->base = base;
    lib->size = total_size;
    lib->delta = (uintptr_t)base - min_vaddr;
    
    snprintf(buf, sizeof(buf), "[SHLIB] Loading %s at %p (delta=0x%lx)\n", 
             path, base, (unsigned long)lib->delta);
    shlib_log_debug(buf);
    
    /* Load segments */
    for (int i = 0; i < ehdr.e_phnum; i++) {
        if (phdrs[i].p_type == PT_LOAD) {
            void* seg_addr = (void*)(phdrs[i].p_vaddr + lib->delta);
            
            /* Read file content */
            lseek(fd, phdrs[i].p_offset, SEEK_SET);
            if (phdrs[i].p_filesz > 0) {
                read(fd, seg_addr, phdrs[i].p_filesz);
            }
            
            /* Zero BSS */
            if (phdrs[i].p_memsz > phdrs[i].p_filesz) {
                memset((char*)seg_addr + phdrs[i].p_filesz, 0, 
                       phdrs[i].p_memsz - phdrs[i].p_filesz);
            }
        }
        else if (phdrs[i].p_type == PT_DYNAMIC) {
            lib->dynamic = (Elf64_Dyn*)(phdrs[i].p_vaddr + lib->delta);
        }
    }
    
    /* Save program headers for dl_iterate_phdr */
    lib->phdr = phdrs;  /* Keep allocated, don't free */
    lib->phnum = ehdr.e_phnum;
    
    close(fd);
    
    /* Parse dynamic section */
    uint32_t* sysv_hash = NULL;
    uint32_t* gnu_hash = NULL;
    
    if (lib->dynamic) {
        for (Elf64_Dyn* dyn = lib->dynamic; dyn->d_tag != DT_NULL; dyn++) {
            switch (dyn->d_tag) {
                case DT_SYMTAB:
                    lib->symtab = (Elf64_Sym*)(dyn->d_un.d_ptr + lib->delta);
                    break;
                case DT_STRTAB:
                    lib->strtab = (const char*)(dyn->d_un.d_ptr + lib->delta);
                    break;
                case DT_HASH:
                    sysv_hash = (uint32_t*)(dyn->d_un.d_ptr + lib->delta);
                    break;
                case DT_GNU_HASH:
                    gnu_hash = (uint32_t*)(dyn->d_un.d_ptr + lib->delta);
                    break;
            }
        }
        
        /* Calculate symbol count from hash tables (most reliable) */
        if (gnu_hash) {
            /* GNU hash format: [nbuckets, symndx, maskwords, shift2, bloom[], buckets[], chain[]] 
             * The chain[] array contains (nchain - symndx) entries.
             * We scan buckets to find max symbol index, then walk ALL chains to find the true max. */
            uint32_t nbuckets = gnu_hash[0];
            uint32_t symndx = gnu_hash[1];
            uint32_t maskwords = gnu_hash[2];
            /* uint32_t shift2 = gnu_hash[3]; */
            
            uint32_t* buckets = &gnu_hash[4 + maskwords * (sizeof(size_t) == 8 ? 2 : 1)];
            uint32_t* chain = &buckets[nbuckets];
            
            /* Find max symbol index by scanning ALL buckets and their chains */
            uint32_t max_symidx = symndx;
            size_t max_possible = lib->size / sizeof(Elf64_Sym);  /* Safety limit based on lib size */
            for (uint32_t b = 0; b < nbuckets; b++) {
                if (buckets[b] == 0) continue;
                
                /* Walk the entire chain for this bucket */
                uint32_t idx = buckets[b];
                while (1) {
                    if (idx > max_symidx) max_symidx = idx;
                    /* Check if this is the last entry (LSB is 1) */
                    if (chain[idx - symndx] & 1) break;
                    idx++;
                    if (idx > max_possible) break; /* Safety limit based on lib size */
                }
            }
            lib->symcount = max_symidx + 1;
            
            /* Also check if symtab extends beyond GNU hash indexed symbols */
            if (lib->symtab && lib->strtab && (uintptr_t)lib->strtab > (uintptr_t)lib->symtab) {
                size_t layout_count = ((uintptr_t)lib->strtab - (uintptr_t)lib->symtab) / sizeof(Elf64_Sym);
                if (layout_count > lib->symcount && layout_count <= max_possible) {
                    lib->symcount = layout_count;
                }
            }
        } else if (sysv_hash) {
            /* SysV hash format: [nbuckets, nchain, buckets[], chain[]]
             * nchain is the symbol count */
            lib->symcount = sysv_hash[1];
        } else if (lib->symtab && lib->strtab) {
            /* Fallback: estimate from layout */
            size_t max_possible = lib->size / sizeof(Elf64_Sym);
            if ((uintptr_t)lib->strtab > (uintptr_t)lib->symtab) {
                lib->symcount = ((uintptr_t)lib->strtab - (uintptr_t)lib->symtab) / sizeof(Elf64_Sym);
                if (lib->symcount > max_possible) lib->symcount = max_possible;
            } else {
                /* strtab is before symtab - estimate from lib size */
                lib->symcount = max_possible / 10;  /* Conservative estimate: ~10% of max */
                if (lib->symcount < 100) lib->symcount = 100;
            }
        }
    }
    
    lib->path = strdup(path);
    const char* basename = strrchr(path, '/');
    lib->name = strdup(basename ? basename + 1 : path);
    
    snprintf(buf, sizeof(buf), "[SHLIB] Loaded %s: symtab=%p strtab=%p symcount=%zu (hash=%s)\n",
             lib->name, (void*)lib->symtab, (void*)lib->strtab, lib->symcount,
             gnu_hash ? "GNU" : (sysv_hash ? "SysV" : "est"));
    shlib_log(buf);  /* Log at INFO level for debugging */
    
    return 0;
}

/* Relocate a shared library */
static int relocate_shlib(shared_lib_t* lib) {
    if (!lib->dynamic || lib->relocated) return 0;
    
    char buf[256];
    Elf64_Rela* rela = NULL;
    size_t relasz = 0;
    Elf64_Rela* jmprel = NULL;
    size_t pltrelsz = 0;
    Elf64_Sym* symtab = lib->symtab;
    const char* strtab = lib->strtab;
    
    /* Find relocation tables */
    for (Elf64_Dyn* dyn = lib->dynamic; dyn->d_tag != DT_NULL; dyn++) {
        switch (dyn->d_tag) {
            case DT_RELA:
                rela = (Elf64_Rela*)(dyn->d_un.d_ptr + lib->delta);
                break;
            case DT_RELASZ:
                relasz = dyn->d_un.d_val;
                break;
            case DT_JMPREL:
                jmprel = (Elf64_Rela*)(dyn->d_un.d_ptr + lib->delta);
                break;
            case DT_PLTRELSZ:
                pltrelsz = dyn->d_un.d_val;
                break;
        }
    }
    
    snprintf(buf, sizeof(buf), "[SHLIB] Relocating %s\n", lib->name);
    shlib_log_debug(buf);
    
    /* Process RELA relocations */
    if (rela && relasz > 0 && symtab && strtab) {
        size_t count = relasz / sizeof(Elf64_Rela);
        for (size_t i = 0; i < count; i++) {
            Elf64_Rela* r = &rela[i];
            uint32_t type = ELF64_R_TYPE(r->r_info);
            uint32_t sym_idx = ELF64_R_SYM(r->r_info);
            uintptr_t* target = (uintptr_t*)(r->r_offset + lib->delta);
            
            switch (type) {
                case R_AARCH64_RELATIVE:
                    *target = lib->delta + r->r_addend;
                    break;
                
                /* R_AARCH64_TLSDESC (1031) - TLS descriptor relocation
                 * The descriptor is 16 bytes: [func_ptr, arg]
                 * For static TLS, func returns the arg directly (offset from thread pointer)
                 * We use our own resolver: ldr x0, [x0, #8]; ret */
                case 1031: { /* R_AARCH64_TLSDESC */
                    /* TLS descriptor format for AArch64:
                     * target[0] = resolver function pointer
                     * target[1] = argument (TLS offset + addend) */
                    target[0] = (uintptr_t)glibc_bridge_tlsdesc_resolver_static;
                    target[1] = (uintptr_t)r->r_addend;  /* For *ABS*, just use addend as offset */
                    snprintf(buf, sizeof(buf), "[SHLIB] TLSDESC at %p: resolver=%p, arg=0x%lx\n", 
                             (void*)target, (void*)glibc_bridge_tlsdesc_resolver_static, (unsigned long)r->r_addend);
                    shlib_log_debug(buf);
                    break;
                }
                    
                case R_AARCH64_GLOB_DAT:
                case R_AARCH64_JUMP_SLOT:
                case R_AARCH64_ABS64: {
                    if (sym_idx == 0) break;
                    
                    const char* sym_name = strtab + symtab[sym_idx].st_name;
                    void* sym_addr = NULL;
                    
                    /* Check if symbol needs priority wrapper resolution */
                    int sym_type = get_symbol_priority_type(sym_name);
                    if (sym_type) {
                        sym_addr = glibc_bridge_resolve_symbol(sym_name);
                    }
                    
                    /* Special handling for __stack_chk_guard - critical for stack protection */
                    if (strcmp(sym_name, "__stack_chk_guard") == 0) {
                        extern uintptr_t __stack_chk_guard;
                        sym_addr = (void*)&__stack_chk_guard;
                        snprintf(buf, sizeof(buf), "[SHLIB] !!! __stack_chk_guard -> %p (value=0x%lx) for %s\n", 
                                 sym_addr, (unsigned long)__stack_chk_guard, lib->name);
                        shlib_log(buf);
                    }
                    
                    /* For other symbols: try shared libs first (including non-relocated), then wrappers */
                    if (!sym_addr) {
                        sym_addr = resolve_from_shared_libs_internal(sym_name, 0);
                    }
                    if (!sym_addr) {
                        sym_addr = glibc_bridge_resolve_symbol(sym_name);
                    }
                    
                    if (sym_addr) {
                        *target = (uintptr_t)sym_addr + r->r_addend;
                        /* Debug: log __stack_chk_guard GOT write */
                        if (strcmp(sym_name, "__stack_chk_guard") == 0) {
                            snprintf(buf, sizeof(buf), "[SHLIB] GOT[__stack_chk_guard] = %p (target=%p)\n", 
                                     (void*)*target, (void*)target);
                            shlib_log(buf);
                        }
                    } else {
                        /* Try bionic as last resort */
                        sym_addr = dlsym(RTLD_DEFAULT, sym_name);
                        if (sym_addr) {
                            *target = (uintptr_t)sym_addr + r->r_addend;
                            snprintf(buf, sizeof(buf), "[SHLIB] RELA '%s' -> bionic fallback %p\n", sym_name, sym_addr);
                            shlib_log_debug(buf);
                        } else {
                            /* Check if this is a weak symbol - if so, set to 0 */
                            unsigned char st_bind = ELF64_ST_BIND(symtab[sym_idx].st_info);
                            if (st_bind == STB_WEAK) {
                                /* Weak symbols can be unresolved - set to 0 + addend */
                                *target = (uintptr_t)r->r_addend;
                                snprintf(buf, sizeof(buf), "[SHLIB] RELA weak '%s' -> 0x%lx (target=%p)\n", 
                                         sym_name, (unsigned long)*target, (void*)target);
                                shlib_log_debug(buf);
                            } else {
                                /* Log unresolved non-weak symbols - keep at INFO level as warnings */
                                snprintf(buf, sizeof(buf), "[SHLIB] WARN: Unresolved RELA symbol '%s'\n", sym_name);
                                shlib_log(buf);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
    
    /* Process PLT relocations */
    if (jmprel && pltrelsz > 0 && symtab && strtab) {
        size_t count = pltrelsz / sizeof(Elf64_Rela);
        for (size_t i = 0; i < count; i++) {
            Elf64_Rela* r = &jmprel[i];
            uint32_t type = ELF64_R_TYPE(r->r_info);
            uint32_t sym_idx = ELF64_R_SYM(r->r_info);
            uintptr_t* target = (uintptr_t*)(r->r_offset + lib->delta);
            
            /* Handle TLSDESC in PLT section */
            if (type == 1031) { /* R_AARCH64_TLSDESC */
                target[0] = (uintptr_t)glibc_bridge_tlsdesc_resolver_static;
                target[1] = (uintptr_t)r->r_addend;
                snprintf(buf, sizeof(buf), "[SHLIB] PLT TLSDESC at %p: resolver=%p, arg=0x%lx\n", 
                         (void*)target, (void*)glibc_bridge_tlsdesc_resolver_static, (unsigned long)r->r_addend);
                shlib_log_debug(buf);
                continue;
            }
            
            if (sym_idx == 0) continue;
            
            const char* sym_name = strtab + symtab[sym_idx].st_name;
            void* sym_addr = NULL;
            
            /* Check if symbol needs priority wrapper resolution */
            int sym_type = get_symbol_priority_type(sym_name);
            if (sym_type) {
                sym_addr = glibc_bridge_resolve_symbol(sym_name);
            }
            
            /* For other symbols: try shared libs first (including non-relocated), then wrappers */
            if (!sym_addr) {
                sym_addr = resolve_from_shared_libs_internal(sym_name, 0);
            }
            if (!sym_addr) {
                sym_addr = glibc_bridge_resolve_symbol(sym_name);
            }
            
            if (sym_addr) {
                *target = (uintptr_t)sym_addr;
            } else {
                /* Try bionic as last resort */
                sym_addr = dlsym(RTLD_DEFAULT, sym_name);
                if (sym_addr) {
                    *target = (uintptr_t)sym_addr;
                    snprintf(buf, sizeof(buf), "[SHLIB] PLT '%s' -> bionic fallback %p\n", sym_name, sym_addr);
                    shlib_log_debug(buf);
                } else {
                    /* Check if this is a weak symbol */
                    unsigned char st_bind = ELF64_ST_BIND(symtab[sym_idx].st_info);
                    if (st_bind == STB_WEAK) {
                        /* Weak symbols can be unresolved - set to 0 */
                        *target = 0;
                        snprintf(buf, sizeof(buf), "[SHLIB] PLT weak '%s' -> 0 (target=%p)\n", 
                                 sym_name, (void*)target);
                        shlib_log_debug(buf);
                    } else {
                        /* Log unresolved non-weak PLT symbols */
                        snprintf(buf, sizeof(buf), "[SHLIB] WARN: Unresolved PLT symbol '%s' (target=%p)\n", 
                                 sym_name, (void*)target);
                        shlib_log(buf);
                    }
                }
            }
        }
    }
    
    lib->relocated = 1;
    snprintf(buf, sizeof(buf), "[SHLIB] Relocated %s\n", lib->name);
    shlib_log_debug(buf);
    
    return 0;
}

/* Run initialization functions for a shared library */
static void run_shlib_init(shared_lib_t* lib) {
    if (!lib->dynamic) return;
    
    char buf[256];
    void (*init_func)(void) = NULL;
    void (**init_array)(void) = NULL;
    size_t init_arraysz = 0;
    
    /* Find init function and init_array from dynamic section */
    for (Elf64_Dyn* dyn = lib->dynamic; dyn->d_tag != DT_NULL; dyn++) {
        switch (dyn->d_tag) {
            case DT_INIT:
                init_func = (void (*)(void))(dyn->d_un.d_ptr + lib->delta);
                break;
            case DT_INIT_ARRAY:
                init_array = (void (**)(void))(dyn->d_un.d_ptr + lib->delta);
                break;
            case DT_INIT_ARRAYSZ:
                init_arraysz = dyn->d_un.d_val;
                break;
        }
    }
    
    /* Call DT_INIT function first */
    if (init_func) {
        snprintf(buf, sizeof(buf), "[SHLIB] Running init for %s: %p\n", lib->name, (void*)init_func);
        shlib_log_debug(buf);
        init_func();
    }
    
    /* Then call init_array functions */
    if (init_array && init_arraysz > 0) {
        size_t count = init_arraysz / sizeof(void*);
        snprintf(buf, sizeof(buf), "[SHLIB] Running %zu init_array entries for %s\n", count, lib->name);
        shlib_log_debug(buf);
        
        for (size_t i = 0; i < count; i++) {
            if (init_array[i]) {
                snprintf(buf, sizeof(buf), "[SHLIB] init_array[%zu] = %p\n", i, (void*)init_array[i]);
                shlib_log_debug(buf);
                init_array[i]();
                snprintf(buf, sizeof(buf), "[SHLIB] init_array[%zu] done\n", i);
                shlib_log_debug(buf);
            }
        }
    }
    snprintf(buf, sizeof(buf), "[SHLIB] Init complete for %s\n", lib->name);
    shlib_log_debug(buf);
}

/* Helper: check library name against array */
static int match_lib_array(const char* basename, const char* const* arr) {
    for (int i = 0; arr[i]; i++) {
        if (strcmp(basename, arr[i]) == 0) return 1;
    }
    return 0;
}

/* Check if a library is a stub (wrapper-provided) */
static int is_stub_library(const char* libname) {
    if (!libname) return 0;
    
    const char* basename = strrchr(libname, '/');
    basename = basename ? basename + 1 : libname;
    
    /* Check against all stub categories */
    if (match_lib_array(basename, g_stub_libs_core)) return 1;
    if (match_lib_array(basename, g_stub_libs_nss)) return 1;
    if (match_lib_array(basename, g_stub_libs_other)) return 1;
    if (match_lib_array(basename, g_stub_libs_sanitizer)) return 1;
    if (match_lib_array(basename, g_stub_libs_gcc)) return 1;
    
    /* Check for versioned variants */
    if (strncmp(basename, "libc.so", 7) == 0) return 1;
    if (strncmp(basename, "libm.so", 7) == 0) return 1;
    if (strncmp(basename, "libpthread.so", 13) == 0) return 1;
    if (strncmp(basename, "libdl.so", 8) == 0) return 1;
    if (strncmp(basename, "librt.so", 8) == 0) return 1;
    if (strncmp(basename, "ld-linux", 8) == 0) return 1;
    
    return 0;
}

/* Check if library is a real glibc lib that should be loaded */
static int is_real_glibc_library(const char* libname) {
    if (!libname) return 0;
    
    const char* basename = strrchr(libname, '/');
    basename = basename ? basename + 1 : libname;
    
    for (int i = 0; g_real_glibc_libs[i]; i++) {
        if (strcmp(basename, g_real_glibc_libs[i]) == 0) return 1;
    }
    
    /* Also match versioned libstdc++ */
    if (strncmp(basename, "libstdc++.so", 12) == 0) return 1;
    if (strncmp(basename, "libgcc_s.so", 11) == 0) return 1;
    
    return 0;
}

/* Standard library subdirectories (Debian/Ubuntu multiarch) */
static const char* g_lib_subdirs[] = {
    "usr/lib/aarch64-linux-gnu",  /* Primary: Debian multiarch */
    "lib/aarch64-linux-gnu",      /* Debian multiarch /lib */
    "lib",                        /* Standard /lib */
    "usr/lib",                    /* Standard /usr/lib */
    "lib64",                      /* /lib64 */
    NULL
};

/* Find library path (returns 1 if found) */
static int find_library_path_ex(const char* name, const char* extra_search, char* out_path, size_t out_size) {
    if (!name || !out_path || !out_size) return 0;
    
    /* 1. Handle absolute paths first */
    if (name[0] == '/') {
        strncpy(out_path, name, out_size - 1);
        out_path[out_size - 1] = '\0';
        if (access(out_path, R_OK) == 0) return 1;
    }
    
    /* 2. Search in extra_search path (for dlopen) */
    if (extra_search && extra_search[0]) {
        snprintf(out_path, out_size, "%s/%s", extra_search, name);
        if (access(out_path, R_OK) == 0) return 1;
        
        /* Also search subdirectories in extra_search */
        for (const char** subdir = g_lib_subdirs; *subdir; subdir++) {
            snprintf(out_path, out_size, "%s/%s/%s", extra_search, *subdir, name);
            if (access(out_path, R_OK) == 0) return 1;
        }
    }
    
    /* 3. Search in g_glibc_root */
    if (g_glibc_root[0]) {
        for (const char** subdir = g_lib_subdirs; *subdir; subdir++) {
            snprintf(out_path, out_size, "%s/%s/%s", g_glibc_root, *subdir, name);
            if (access(out_path, R_OK) == 0) return 1;
        }
    }
    
    return 0;
}

/* Find library path in standard locations */
static int find_library_path(const char* name, char* out_path, size_t out_size) {
    return find_library_path_ex(name, NULL, out_path, out_size);
}

/* Load a shared library and its dependencies */
int glibc_bridge_load_shared_lib(const char* name, const char* search_path) {
    char buf[256];
    

    
    /* Skip stub libraries - they use wrappers */
    if (is_stub_library(name)) {

        return 0;
    }
    
    /* Check native library redirects (libGL.so -> libGL_gl4es.so, etc.)
     * These are Android native libraries that should be loaded via bionic dlopen */
    const char* libbasename = strrchr(name, '/');
    libbasename = libbasename ? libbasename + 1 : name;
    for (int i = 0; g_native_lib_redirects[i].glibc_prefix; i++) {
        if (strncmp(libbasename, g_native_lib_redirects[i].glibc_prefix, 
                    strlen(g_native_lib_redirects[i].glibc_prefix)) == 0) {
            
            const char* target_lib = g_native_lib_redirects[i].android_name;
            if (!target_lib) {
                /* NULL means use system library - skip loading from rootfs */
                snprintf(buf, sizeof(buf), "[SHLIB] %s -> system (skip rootfs)\n", libbasename);
                shlib_log(buf);
                return 0;
            }
            
            snprintf(buf, sizeof(buf), "[SHLIB] %s -> native %s\n", libbasename, target_lib);
            shlib_log(buf);
            
            /* Try dlopen for Android native library */
            void* handle = dlopen(target_lib, RTLD_LAZY | RTLD_GLOBAL);
            if (handle) {
                snprintf(buf, sizeof(buf), "[SHLIB] Native %s loaded -> %p\n", target_lib, handle);
                shlib_log(buf);
                return 0;  /* Success - don't continue to rootfs loading */
            }
            
            snprintf(buf, sizeof(buf), "[SHLIB] Native %s not found: %s\n", target_lib, dlerror());
            shlib_log(buf);
            return -1;  /* Failed to load required native library */
        }
    }
    
    /* Check if already loaded */
    if (find_shared_lib(name)) {

        return 0;
    }
    
    /* Check ICU redirects */
    const char* basename = strrchr(name, '/');
    basename = basename ? basename + 1 : name;
    for (int i = 0; g_icu_redirects[i].prefix; i++) {
        if (strncmp(basename, g_icu_redirects[i].prefix, strlen(g_icu_redirects[i].prefix)) == 0) {
            if (dlopen(g_icu_redirects[i].android_lib, RTLD_NOW | RTLD_GLOBAL)) return 0;
        }
    }
    
    if (g_shared_lib_count >= MAX_SHARED_LIBS) {
        shlib_log("[SHLIB] Too many shared libraries\n");

        return -1;
    }
    
    /* Find and load library (search_path has priority, then g_glibc_root) */
    char full_path[512];
    if (!find_library_path_ex(name, search_path, full_path, sizeof(full_path))) {
        snprintf(buf, sizeof(buf), "[SHLIB] Library not found: %s\n", name);
        shlib_log(buf);

        return -1;
    }
    

    
    shared_lib_t* lib = &g_shared_libs[g_shared_lib_count];
    memset(lib, 0, sizeof(*lib));
    
    int elf_result = load_elf_shlib(full_path, lib);
    if (elf_result != 0) {
        snprintf(buf, sizeof(buf), "[SHLIB] Failed to load: %s\n", full_path);
        shlib_log(buf);

        return -1;
    }
    
    snprintf(buf, sizeof(buf), "[SHLIB] Loaded %s (symcount=%zu)\n", lib->name, lib->symcount);
    shlib_log(buf);
    g_shared_lib_count++;
    
    /* Recursively load dependencies (DT_NEEDED) */
    if (lib->dynamic && lib->strtab) {
        for (Elf64_Dyn* dyn = lib->dynamic; dyn->d_tag != DT_NULL; dyn++) {
            if (dyn->d_tag == DT_NEEDED) {
                const char* needed = lib->strtab + dyn->d_un.d_val;
                glibc_bridge_load_shared_lib(needed, NULL);
            }
        }
    }
    
    return 0;
}

/* Load and relocate all shared libraries for an ELF (uses g_glibc_root internally) */
int glibc_bridge_load_elf_dependencies(elfheader_t* elf, const char* search_path) {
    (void)search_path;  /* Unused - library search uses g_glibc_root */
    char buf[128];
    
    if (!elf || !elf->phdr) return 0;
    
    /* Find PT_DYNAMIC segment */
    Elf64_Dyn* dynamic = NULL;
    for (int i = 0; i < elf->phnum; i++) {
        if (elf->phdr[i].p_type == PT_DYNAMIC) {
            dynamic = (Elf64_Dyn*)(elf->phdr[i].p_vaddr + elf->delta);
            break;
        }
    }
    if (!dynamic) return 0;
    
    /* Find strtab */
    const char* strtab = NULL;
    for (Elf64_Dyn* dyn = dynamic; dyn->d_tag != DT_NULL; dyn++) {
        if (dyn->d_tag == DT_STRTAB) {
            strtab = (const char*)(dyn->d_un.d_ptr + elf->delta);
            break;
        }
    }
    if (!strtab) return 0;
    
    /* Load DT_NEEDED dependencies */
    for (Elf64_Dyn* dyn = dynamic; dyn->d_tag != DT_NULL; dyn++) {
        if (dyn->d_tag == DT_NEEDED) {
            const char* needed = strtab + dyn->d_un.d_val;
            
            /* Log and load (glibc_bridge_load_shared_lib handles stub libs internally) */
            snprintf(buf, sizeof(buf), "[DEPS] %s\n", needed);
            shlib_log(buf);
            glibc_bridge_load_shared_lib(needed, NULL);
        }
    }
    
    /* Relocate all loaded libraries */
    for (int i = 0; i < g_shared_lib_count; i++) {
        relocate_shlib(&g_shared_libs[i]);
    }
    
    /* Run initialization functions for all loaded libraries (in load order) */
    shlib_log("[DEPS] Running shared library initializers...\n");
    for (int i = 0; i < g_shared_lib_count; i++) {
        run_shlib_init(&g_shared_libs[i]);
    }
    
    return 0;
}

/* ============================================================================
 * dlopen 风格的库加载 - 加载、重定位并初始化 glibc 库
 * ============================================================================ */

/* For stub libraries, we return dlopen(NULL) handle which is a valid bionic handle.
 * This avoids crashes when box64 tries to use the handle with bionic functions.
 * We track that stub libraries use this handle, and intercept dlsym to return
 * our wrapper functions instead of bionic's. */
static void* g_stub_library_handle = NULL;

/* Check if handle is for stub libraries */
int glibc_bridge_is_stub_handle(void* handle) {
    return (g_stub_library_handle != NULL && handle == g_stub_library_handle);
}

void* glibc_bridge_dlopen_glibc_lib(const char* path) {
    char buf[256];
    int start_idx = g_shared_lib_count;

    /* 从路径提取库名称 */
    const char* libname = strrchr(path, '/');
    libname = libname ? libname + 1 : path;


    /* Check if this is a stub library FIRST
     * Stub libraries are provided by glibc-bridge wrappers, no need to load ELF */
    if (is_stub_library(libname)) {
        snprintf(buf, sizeof(buf), "[DLOPEN] %s is a stub library (handled by wrappers)\n", libname);
        shlib_log(buf);

        /* Return a valid bionic handle (dlopen(NULL)) for stub libraries.
         * This prevents crashes when box64/bionic tries to validate the handle.
         * We intercept dlsym calls on this handle in dlsym_wrapper. */
        if (g_stub_library_handle == NULL) {
            g_stub_library_handle = dlopen(NULL, RTLD_LAZY | RTLD_GLOBAL);
            snprintf(buf, sizeof(buf), "[DLOPEN] Created stub handle: %p\n", g_stub_library_handle);
            shlib_log(buf);
        }
        return g_stub_library_handle;
    }

    /* Check if this is a native library that should be loaded via bionic dlopen
     * These are Android native libraries like gl4es, SDL2, etc. */
    for (int i = 0; g_native_lib_redirects[i].glibc_prefix; i++) {
        if (strncmp(libname, g_native_lib_redirects[i].glibc_prefix, 
                    strlen(g_native_lib_redirects[i].glibc_prefix)) == 0) {
            
            const char* target_lib = g_native_lib_redirects[i].android_name;
            if (!target_lib) {
                /* NULL means use system library with original name */
                target_lib = libname;
            }
            
            snprintf(buf, sizeof(buf), "[DLOPEN] %s -> native %s, using bionic dlopen\n", libname, target_lib);
            shlib_log(buf);
            
            /* Try direct dlopen first (will search APK's lib directory) */
            void* handle = dlopen(target_lib, RTLD_LAZY | RTLD_GLOBAL);
            if (handle) {
                snprintf(buf, sizeof(buf), "[DLOPEN] Native %s loaded -> %p\n", target_lib, handle);
                shlib_log(buf);
                return handle;
            }
            
            snprintf(buf, sizeof(buf), "[DLOPEN] Native %s not found: %s\n", target_lib, dlerror());
            shlib_log(buf);
            return NULL;
        }
    }

    /* 从完整路径提取搜索路径 */
    char search_path[512] = {0};
    if (path[0] == '/') {
        const char* last_slash = strrchr(path, '/');
        if (last_slash) {
            size_t dir_len = last_slash - path;
            if (dir_len < sizeof(search_path)) {
                memcpy(search_path, path, dir_len);
                search_path[dir_len] = '\0';
            }
        }
    }



    snprintf(buf, sizeof(buf), "[DLOPEN] Loading %s\n", libname);
    shlib_log(buf);

    /* 加载库及其依赖项 */
    int load_result = glibc_bridge_load_shared_lib(libname, search_path);

    if (load_result != 0) {
        snprintf(buf, sizeof(buf), "[DLOPEN] Failed to load %s\n", libname);
        shlib_log(buf);
        return NULL;
    }

    /* 重定位新加载的库 */
    for (int i = start_idx; i < g_shared_lib_count; i++) {
        relocate_shlib(&g_shared_libs[i]);
    }

    /* 运行新加载库的初始化函数（逆序，确保依赖项先初始化） */
    for (int i = g_shared_lib_count - 1; i >= start_idx; i--) {
        run_shlib_init(&g_shared_libs[i]);
    }

    /* 返回句柄 */
    shared_lib_t* lib = find_shared_lib(libname);
    if (lib) {
        snprintf(buf, sizeof(buf), "[DLOPEN] %s loaded at %p\n", libname, lib->base);
        shlib_log(buf);
        return (void*)lib;
    }

    return NULL;
}

/* 从句柄查找符号 */
void* glibc_bridge_dlsym_from_handle(void* handle, const char* name) {
    char buf[256];
    if (!handle || !name || !name[0]) return NULL;

    /* Special handling for stub handle - use glibc_bridge_resolve_symbol directly
     * Stub libraries (libc.so.6, libpthread.so.0, etc.) are provided by glibc-bridge wrappers */
    if (glibc_bridge_is_stub_handle(handle)) {
        void* sym = glibc_bridge_resolve_symbol(name);
        if (sym) {
            snprintf(buf, sizeof(buf), "[SHLIB] dlsym(STUB, '%s') -> wrapper %p\n", name, sym);
            shlib_log(buf);
        } else {
            snprintf(buf, sizeof(buf), "[SHLIB] dlsym(STUB, '%s') -> NOT FOUND in wrappers\n", name);
            shlib_log(buf);
        }
        return sym;
    }

    shared_lib_t* lib = (shared_lib_t*)handle;

    /* 验证句柄有效性 */
    int valid = 0;
    for (int i = 0; i < g_shared_lib_count; i++) {
        if (&g_shared_libs[i] == lib) {
            valid = 1;
            break;
        }
    }
    if (!valid) {
        snprintf(buf, sizeof(buf), "[SHLIB] dlsym: invalid handle %p for '%s'\n", handle, name);
        shlib_log(buf);
        return NULL;
    }
    
    /* 库必须已重定位才能查找符号 */
    if (!lib->relocated || !lib->symtab || !lib->strtab || !lib->base) {
        snprintf(buf, sizeof(buf), "[SHLIB] dlsym: %s not ready for '%s'\n", lib->name, name);
        shlib_log(buf);
        return NULL;
    }
    
    /* Calculate strtab bounds for safe access */
    uintptr_t lib_start = (uintptr_t)lib->base;
    uintptr_t lib_end = lib_start + lib->size;
    uintptr_t strtab_addr = (uintptr_t)lib->strtab;
    uintptr_t symtab_addr = (uintptr_t)lib->symtab;
    
    /* Verify symtab and strtab are within library bounds */
    if (strtab_addr < lib_start || strtab_addr >= lib_end ||
        symtab_addr < lib_start || symtab_addr >= lib_end) {
        snprintf(buf, sizeof(buf), "[SHLIB] dlsym: %s tables out of bounds (base=%p strtab=%p symtab=%p size=0x%zx delta=0x%lx)\n",
                 lib->name, lib->base, lib->strtab, lib->symtab, lib->size, (unsigned long)lib->delta);
        shlib_log(buf);
        return NULL;
    }
    
    /* Calculate safe strtab range */
    size_t strtab_max_offset = lib_end - strtab_addr;
    
    /* Calculate actual symbol count from memory layout */
    size_t actual_symcount = lib->symcount;
    size_t max_possible = lib->size / sizeof(Elf64_Sym);  /* Based on library size */
    if ((uintptr_t)lib->strtab > (uintptr_t)lib->symtab) {
        size_t layout_count = ((uintptr_t)lib->strtab - (uintptr_t)lib->symtab) / sizeof(Elf64_Sym);
        if (layout_count > actual_symcount && layout_count <= max_possible) {
            actual_symcount = layout_count;
        }
    }
    
    /* 在库的符号表中查找 */
    int found_similar = 0;
    for (size_t i = 0; i < actual_symcount; i++) {
        Elf64_Sym* sym = &lib->symtab[i];
        if (sym->st_name == 0) continue;
        
        /* Bounds check for st_name */
        if (sym->st_name >= strtab_max_offset) {
            continue;  /* Skip invalid symbol */
        }
        
        const char* sym_name = lib->strtab + sym->st_name;
        
        /* Debug: print symbols that start with "corehost" */
        if (strncmp(sym_name, "corehost", 8) == 0) {
            unsigned char bind = ELF64_ST_BIND(sym->st_info);
            unsigned char type = ELF64_ST_TYPE(sym->st_info);
            snprintf(buf, sizeof(buf), "[SHLIB] Found corehost symbol: '%s' shndx=%d bind=%d type=%d value=0x%lx\n",
                     sym_name, sym->st_shndx, bind, type, (unsigned long)sym->st_value);
            shlib_log(buf);
            found_similar = 1;
        }
        
        if (sym->st_shndx == SHN_UNDEF) continue;
        
        unsigned char bind = ELF64_ST_BIND(sym->st_info);
        if (bind != STB_GLOBAL && bind != STB_WEAK) continue;
        
        if (strcmp(sym_name, name) == 0) {
            /* 使用 delta 而不是 base 来计算正确的地址 */
            void* addr = (void*)(sym->st_value + lib->delta);
            snprintf(buf, sizeof(buf), "[SHLIB] dlsym(%s, '%s') -> %p\n", lib->name, name, addr);
            shlib_log(buf);
            return addr;
        }
    }
    
    snprintf(buf, sizeof(buf), "[SHLIB] dlsym(%s, '%s') -> NOT FOUND (searched %zu/%zu symbols, found_similar=%d)\n", 
             lib->name, name, actual_symcount, lib->symcount, found_similar);
    shlib_log(buf);
    return NULL;
}

/* Cleanup */
void glibc_bridge_unload_shared_libs(void) {
    for (int i = 0; i < g_shared_lib_count; i++) {
        shared_lib_t* lib = &g_shared_libs[i];
        if (lib->base && lib->base != MAP_FAILED) {
            munmap(lib->base, lib->size);
        }
        free(lib->name);
        free(lib->path);
        free(lib->phdr);
    }
    g_shared_lib_count = 0;
}

/* Check if a handle is a glibc shared lib we loaded */
int glibc_bridge_is_glibc_handle(void* handle) {
    if (!handle) return 0;

    /* Check if this is the stub handle (dlopen(NULL) for stub libraries) */
    if (glibc_bridge_is_stub_handle(handle)) {
        return 1;
    }

    /* Check if handle is one of our loaded shared libraries */
    for (int i = 0; i < g_shared_lib_count; i++) {
        if (handle == (void*)&g_shared_libs[i]) {
            return 1;
        }
    }
    return 0;
}

/* Find library info by address (for dladdr support) */
int glibc_bridge_dladdr_lookup(const void* addr, Dl_info* info) {
    if (!addr || !info) return 0;
    
    uintptr_t target = (uintptr_t)addr;
    
    /* Search through our loaded shared libraries */
    for (int i = 0; i < g_shared_lib_count; i++) {
        shared_lib_t* lib = &g_shared_libs[i];
        uintptr_t lib_start = (uintptr_t)lib->base;
        uintptr_t lib_end = lib_start + lib->size;
        
        if (target >= lib_start && target < lib_end) {
            /* Found the library containing this address */
            info->dli_fname = lib->path;
            info->dli_fbase = lib->base;
            
            /* Find the nearest symbol */
            info->dli_sname = NULL;
            info->dli_saddr = NULL;
            
            if (lib->symtab && lib->strtab) {
                uintptr_t best_addr = 0;
                const char* best_name = NULL;
                
                for (size_t j = 0; j < lib->symcount; j++) {
                    Elf64_Sym* sym = &lib->symtab[j];
                    
                    /* Only consider defined function/object symbols */
                    if (sym->st_shndx == SHN_UNDEF) continue;
                    if (ELF64_ST_TYPE(sym->st_info) != STT_FUNC &&
                        ELF64_ST_TYPE(sym->st_info) != STT_OBJECT) continue;
                    
                    uintptr_t sym_addr = lib_start + sym->st_value;
                    
                    /* Find the largest symbol address <= target */
                    if (sym_addr <= target && sym_addr > best_addr) {
                        best_addr = sym_addr;
                        best_name = lib->strtab + sym->st_name;
                    }
                }
                
                if (best_name) {
                    info->dli_sname = best_name;
                    info->dli_saddr = (void*)best_addr;
                }
            }
            
            return 1;  /* Success */
        }
    }
    
    return 0;  /* Not found in our libraries */
}

/* ============================================================================
 * dl_iterate_phdr Implementation
 * 
 * Iterates over all loaded shared objects and calls the callback for each.
 * This is used by libgcc/libunwind for exception handling and stack unwinding.
 * 
 * The struct dl_phdr_info is compatible between glibc and bionic.
 * ============================================================================ */

/* dl_phdr_info structure - compatible with both glibc and bionic */
struct dl_phdr_info {
    Elf64_Addr dlpi_addr;           /* Base address of object */
    const char* dlpi_name;          /* Name of object */
    const Elf64_Phdr* dlpi_phdr;    /* Pointer to array of program headers */
    Elf64_Half dlpi_phnum;          /* Number of program headers */
    /* Extended fields (API level 30+) */
    unsigned long long dlpi_adds;   /* Number of library loads */
    unsigned long long dlpi_subs;   /* Number of library unloads */
    size_t dlpi_tls_modid;          /* TLS module ID */
    void* dlpi_tls_data;            /* TLS data for this module */
};

/* Global counters for dlpi_adds/dlpi_subs */
static unsigned long long g_dl_adds = 0;
static unsigned long long g_dl_subs = 0;

/* Increment load counter (call when loading a library) */
void glibc_bridge_dl_notify_load(void) {
    g_dl_adds++;
}

/* Increment unload counter (call when unloading a library) */
void glibc_bridge_dl_notify_unload(void) {
    g_dl_subs++;
}

/* dl_iterate_phdr wrapper
 * 
 * Calls callback for:
 * 1. The main executable (from glibc_bridge_runner's loaded ELF)
 * 2. All shared libraries loaded by glibc_bridge_sharedlib
 * 3. Bionic's own loaded libraries (via real dl_iterate_phdr)
 */

/* External: get main ELF info from resolver */
extern elfheader_t* glibc_bridge_get_current_elf(void);

int dl_iterate_phdr_wrapper(
    int (*callback)(struct dl_phdr_info* info, size_t size, void* data),
    void* data)
{
    struct dl_phdr_info info;
    int ret = 0;
    
    /* First, report the main executable */
    elfheader_t* main_elf = glibc_bridge_get_current_elf();
    if (main_elf && main_elf->phdr && main_elf->phnum > 0) {
        memset(&info, 0, sizeof(info));
        info.dlpi_addr = (Elf64_Addr)main_elf->delta;
        info.dlpi_name = "";
        info.dlpi_phdr = main_elf->phdr;
        info.dlpi_phnum = main_elf->phnum;
        info.dlpi_adds = g_dl_adds;
        info.dlpi_subs = g_dl_subs;
        info.dlpi_tls_modid = 0;
        info.dlpi_tls_data = NULL;
        
        ret = callback(&info, sizeof(info), data);
        if (ret != 0) return ret;
    }
    
    /* Then iterate over our loaded glibc shared libraries */
    for (int i = 0; i < g_shared_lib_count; i++) {
        shared_lib_t* lib = &g_shared_libs[i];
        if (!lib->base || !lib->phdr) continue;
        
        memset(&info, 0, sizeof(info));
        info.dlpi_addr = (Elf64_Addr)lib->delta;
        info.dlpi_name = lib->path ? lib->path : lib->name;
        info.dlpi_phdr = lib->phdr;
        info.dlpi_phnum = lib->phnum;
        info.dlpi_adds = g_dl_adds;
        info.dlpi_subs = g_dl_subs;
        info.dlpi_tls_modid = 0;
        info.dlpi_tls_data = NULL;
        
        ret = callback(&info, sizeof(info), data);
        if (ret != 0) return ret;
    }
    
    /* Also call bionic's dl_iterate_phdr for system libraries */
    extern int dl_iterate_phdr(int (*)(struct dl_phdr_info*, size_t, void*), void*);
    return dl_iterate_phdr(callback, data);
}

/* Get the number of loaded shared libraries (for debugging) */
int glibc_bridge_get_shared_lib_count(void) {
    return g_shared_lib_count;
}

/* Get info about a loaded shared library (for /proc/self/maps virtualization) */
typedef struct {
    const char* name;
    const char* path;
    void* base;
    size_t size;
} glibc_bridge_shlib_info_t;

int glibc_bridge_get_shared_lib_info(int index, glibc_bridge_shlib_info_t* info) {
    if (index < 0 || index >= g_shared_lib_count || !info) {
        return -1;
    }
    
    shared_lib_t* lib = &g_shared_libs[index];
    info->name = lib->name;
    info->path = lib->path;
    info->base = lib->base;
    info->size = lib->size;
    return 0;
}

/* ============================================================================
 * _dl_find_object support - Find EH frame info for exception handling
 * Optimized with caching to avoid repeated lookups during stack unwinding
 * ============================================================================ */

/* Cache eh_frame_hdr for a library (called once per library) */
static void cache_eh_frame(shared_lib_t* lib) {
    if (lib->eh_frame_cached) return;
    
    lib->eh_frame_hdr = NULL;
    lib->eh_frame_cached = 1;
    
    if (!lib->phdr || lib->phnum == 0) return;
    
    uintptr_t lib_start = (uintptr_t)lib->base;
    for (int j = 0; j < lib->phnum; j++) {
        if (lib->phdr[j].p_type == PT_GNU_EH_FRAME) {
            lib->eh_frame_hdr = (void*)(lib_start + lib->phdr[j].p_vaddr);
            break;
        }
    }
}

int glibc_bridge_find_eh_frame(void* addr, void** map_start, void** map_end, void** eh_frame) {
    if (!addr || !map_start || !map_end || !eh_frame) return 0;
    
    uintptr_t target = (uintptr_t)addr;
    
    /* Search through our loaded shared libraries */
    for (int i = 0; i < g_shared_lib_count; i++) {
        shared_lib_t* lib = &g_shared_libs[i];
        uintptr_t lib_start = (uintptr_t)lib->base;
        uintptr_t lib_end = lib_start + lib->size;
        
        if (target >= lib_start && target < lib_end) {
            /* Cache eh_frame on first access */
            if (!lib->eh_frame_cached) {
                cache_eh_frame(lib);
            }
            
            *map_start = lib->base;
            *map_end = (void*)lib_end;
            *eh_frame = lib->eh_frame_hdr;
            return 1;
        }
    }
    
    return 0;  /* Not in our libraries */
}

