/*
 * glibc-bridge 执行器
 * 
 * 栈设置、TLS 初始化和执行
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <time.h>
#include <alloca.h>
#include <limits.h>
#include <signal.h>
#include <setjmp.h>
#include <sys/mman.h>
#include <sys/auxv.h>
#include <sys/wait.h>
#include <sys/select.h>
#include <ucontext.h>

#include "../include/private.h"

/* ============================================================================
 * Direct Execution Exit Handler (for JNI compatibility - no fork mode)
 * ============================================================================ */

/* Global exit handler state - used by exit wrapper to return control */
static sigjmp_buf g_exit_jump_buf;
static volatile int g_exit_handler_active = 0;
static volatile int g_exit_code = 0;

/* Called by exit wrapper when direct execution mode is active */
void glibc_bridge_exit_handler(int code) {
    if (g_exit_handler_active) {
        g_exit_code = code;
        LOG_INFO("Direct execution: exit(%d) called, returning to caller", code);
        siglongjmp(g_exit_jump_buf, 1);
    } else {
        /* Fallback to real exit if handler not active */
        _exit(code);
    }
}

/* Check if exit handler is active (called by exit wrapper) */
int glibc_bridge_exit_handler_active(void) {
    return g_exit_handler_active;
}

/* ============================================================================
 * ARM64 Signal Handler for Crash Debugging
 * ============================================================================ */

/* Write to both stderr and a backup fd in case stderr is redirected */
static int g_crash_log_fd = -1;

/* Global flag to indicate crash handler is installed - checked by sigaction_wrapper */
int g_glibc_bridge_crash_handler_installed = 0;

static void crash_write(const char* buf, int len) {
    write(STDERR_FILENO, buf, len);
    if (g_crash_log_fd >= 0) {
        write(g_crash_log_fd, buf, len);
    }
}

static void glibc_bridge_crash_handler(int sig, siginfo_t* info, void* ucontext) {
    /* Get register context */
    ucontext_t* uc = (ucontext_t*)ucontext;
    
    /* Print crash info */
    char buf[512];
    int len;
    
    len = snprintf(buf, sizeof(buf), 
        "\n=== glibc-bridge CRASH HANDLER ===\n"
        "Signal: %d (%s)\n"
        "Fault addr: %p\n"
        "si_code: %d\n",
        sig, 
        sig == SIGSEGV ? "SIGSEGV" : 
        sig == SIGBUS ? "SIGBUS" : 
        sig == SIGFPE ? "SIGFPE" : 
        sig == SIGILL ? "SIGILL" : "UNKNOWN",
        info->si_addr,
        info->si_code);
    crash_write(buf, len);
    
#ifdef __aarch64__
    /* ARM64 registers from ucontext */
    struct sigcontext* sc = &uc->uc_mcontext;
    
    len = snprintf(buf, sizeof(buf),
        "PC:  0x%016lx\n"
        "SP:  0x%016lx\n"
        "LR:  0x%016lx (x30)\n"
        "FP:  0x%016lx (x29)\n",
        (unsigned long)sc->pc,
        (unsigned long)sc->sp,
        (unsigned long)sc->regs[30],
        (unsigned long)sc->regs[29]);
    crash_write(buf, len);
    
    /* Print x0-x28 */
    len = snprintf(buf, sizeof(buf), "Registers:\n");
    crash_write(buf, len);
    
    for (int i = 0; i < 29; i += 4) {
        len = snprintf(buf, sizeof(buf),
            "  x%-2d: 0x%016lx  x%-2d: 0x%016lx  x%-2d: 0x%016lx  x%-2d: 0x%016lx\n",
            i, (unsigned long)sc->regs[i],
            i+1, (unsigned long)sc->regs[i+1],
            i+2, (unsigned long)sc->regs[i+2],
            i+3 < 29 ? i+3 : 28, (unsigned long)sc->regs[i+3 < 29 ? i+3 : 28]);
        crash_write(buf, len);
    }
    
    /* Try to print a simple backtrace using frame pointer */
    len = snprintf(buf, sizeof(buf), "\nBacktrace (FP chain):\n");
    crash_write(buf, len);
    
    unsigned long fp = sc->regs[29];  /* x29 = frame pointer */
    unsigned long lr = sc->regs[30];  /* x30 = link register */
    
    len = snprintf(buf, sizeof(buf), "  #0  pc 0x%016lx\n", (unsigned long)sc->pc);
    crash_write(buf, len);
    
    for (int frame = 1; frame < 20 && fp != 0; frame++) {
        len = snprintf(buf, sizeof(buf), "  #%-2d lr 0x%016lx  (fp=0x%016lx)\n", 
                       frame, lr, fp);
        crash_write(buf, len);
        
        /* Try to read next frame - be careful with invalid addresses */
        unsigned long* fp_ptr = (unsigned long*)fp;
        
        /* Basic sanity check on fp */
        if (fp < 0x1000 || (fp & 0x7) != 0) break;
        
        /* Read next fp and lr from stack */
        unsigned long next_fp = fp_ptr[0];
        unsigned long next_lr = fp_ptr[1];
        
        if (next_fp == 0 || next_fp == fp) break;
        
        fp = next_fp;
        lr = next_lr;
    }
#endif
    
    len = snprintf(buf, sizeof(buf), "=== END CRASH INFO ===\n\n");
    crash_write(buf, len);
    
    /* Re-raise signal with default handler to generate core dump / tombstone */
    signal(sig, SIG_DFL);
    raise(sig);
}

static void glibc_bridge_install_crash_handlers(void) {
    /* Save a copy of stderr before it might be redirected */
    g_crash_log_fd = dup(STDERR_FILENO);
    
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = glibc_bridge_crash_handler;
    /* SA_RESETHAND: reset handler to default after first signal (avoid infinite loop) */
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESETHAND;
    sigemptyset(&sa.sa_mask);
    
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    sigaction(SIGFPE, &sa, NULL);
    sigaction(SIGILL, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    
    /* Mark that our crash handlers are installed */
    g_glibc_bridge_crash_handler_installed = 1;
}

/* ============================================================================
 * Constants
 * ============================================================================ */

#define DEFAULT_STACK_SIZE  (32 * 1024 * 1024)   /* 32MB - increased for complex math */
#define STACK_ALIGN         16

/* Auxiliary vector types */
#ifndef AT_NULL
#define AT_NULL         0
#define AT_IGNORE       1
#define AT_PHDR         3
#define AT_PHENT        4
#define AT_PHNUM        5
#define AT_PAGESZ       6
#define AT_BASE         7
#define AT_FLAGS        8
#define AT_ENTRY        9
#define AT_UID          11
#define AT_EUID         12
#define AT_GID          13
#define AT_EGID         14
#define AT_PLATFORM     15
#define AT_HWCAP        16
#define AT_CLKTCK       17
#define AT_SECURE       23
#define AT_RANDOM       25
#define AT_HWCAP2       26
#define AT_EXECFN       31
#endif

/* TLS constants */
#define TLS_TCB_SIZE        16      /* tcbhead_t: dtv(8) + private(8) */
#define TLS_PRE_TCB_SIZE    2048    /* struct pthread size (estimated) */
#define TLS_POST_TCB_SIZE   256     /* DTV array space */
#define TLS_EXTRA_SIZE      65536   /* Extra space for dynamically loaded libraries TLS */

/* ============================================================================
 * Stack Setup
 * ============================================================================ */

uintptr_t setup_stack(void* stack_base, size_t stack_size,
                      int argc, char** argv, char** envp,
                      elfheader_t* elf) {
    uintptr_t stack_top = (uintptr_t)stack_base + stack_size;
    uintptr_t sp = stack_top;
    
    /* === Step 1: Place strings at stack top === */
    
    /* 16-byte random value for AT_RANDOM */
    sp -= 16;
    sp &= ~0xF;
    uintptr_t p_random = sp;
    
    unsigned int seed = (unsigned int)time(NULL) ^ (unsigned int)getpid();
    srand(seed);
    for (int i = 0; i < 16; i++) {
        ((uint8_t*)p_random)[i] = rand() & 0xFF;
    }
    
    /* Platform string */
    const char* platform = "aarch64";
    size_t platform_len = strlen(platform) + 1;
    sp -= platform_len;
    memcpy((void*)sp, platform, platform_len);
    uintptr_t p_platform = sp;
    
    /* Count environment variables */
    int envc = 0;
    if (envp) {
        while (envp[envc]) envc++;
    }
    
    /* Environment variable strings */
    uintptr_t* env_ptrs = (uintptr_t*)alloca((envc + 1) * sizeof(uintptr_t));
    for (int i = envc - 1; i >= 0; i--) {
        size_t len = strlen(envp[i]) + 1;
        sp -= len;
        memcpy((void*)sp, envp[i], len);
        env_ptrs[i] = sp;
    }
    env_ptrs[envc] = 0;
    
    /* Argument strings */
    uintptr_t* arg_ptrs = (uintptr_t*)alloca((argc + 1) * sizeof(uintptr_t));
    for (int i = argc - 1; i >= 0; i--) {
        size_t len = strlen(argv[i]) + 1;
        sp -= len;
        memcpy((void*)sp, argv[i], len);
        arg_ptrs[i] = sp;
    }
    arg_ptrs[argc] = 0;
    
    uintptr_t p_execfn = arg_ptrs[0];
    
    /* Align to 16 bytes */
    sp &= ~0xF;
    
    /* === Step 2: Calculate space needed === */
    
    int auxv_count = 20;
    size_t auxv_size = (auxv_count + 1) * 16;
    size_t envp_size = (envc + 1) * sizeof(uintptr_t);
    size_t argv_size = (argc + 1) * sizeof(uintptr_t);
    size_t argc_size = sizeof(uintptr_t);
    size_t total = auxv_size + envp_size + argv_size + argc_size;
    
    sp -= total;
    sp &= ~0xF;
    
    /* === Step 3: Fill stack content === */
    
    uintptr_t* stack = (uintptr_t*)sp;
    int idx = 0;
    
    /* argc */
    stack[idx++] = argc;
    
    /* argv pointers */
    for (int i = 0; i < argc; i++) {
        stack[idx++] = arg_ptrs[i];
    }
    stack[idx++] = 0;  /* NULL terminator */
    
    /* envp pointers */
    for (int i = 0; i < envc; i++) {
        stack[idx++] = env_ptrs[i];
    }
    stack[idx++] = 0;  /* NULL terminator */
    
    /* Auxiliary vector */
    #define PUSH_AUX(type, val) do { stack[idx++] = (type); stack[idx++] = (val); } while(0)
    
    uintptr_t elf_base = (uintptr_t)elf->image;
    uintptr_t phdr_addr = elf_base + elf->ehdr.e_phoff;
    
    PUSH_AUX(AT_PHDR, phdr_addr);
    PUSH_AUX(AT_PHENT, elf->ehdr.e_phentsize);
    PUSH_AUX(AT_PHNUM, elf->phnum);
    PUSH_AUX(AT_PAGESZ, getpagesize());
    PUSH_AUX(AT_BASE, 0);
    PUSH_AUX(AT_FLAGS, 0);
    PUSH_AUX(AT_ENTRY, elf->entrypoint + elf->delta);
    PUSH_AUX(AT_UID, getuid());
    PUSH_AUX(AT_EUID, geteuid());
    PUSH_AUX(AT_GID, getgid());
    PUSH_AUX(AT_EGID, getegid());
    PUSH_AUX(AT_PLATFORM, p_platform);
    PUSH_AUX(AT_HWCAP, getauxval(AT_HWCAP));
    PUSH_AUX(AT_CLKTCK, sysconf(_SC_CLK_TCK));
    PUSH_AUX(AT_SECURE, 0);
    PUSH_AUX(AT_RANDOM, p_random);
    PUSH_AUX(AT_HWCAP2, getauxval(AT_HWCAP2));
    PUSH_AUX(AT_EXECFN, p_execfn);
    PUSH_AUX(AT_NULL, 0);
    
    #undef PUSH_AUX
    
    return sp;
}

/* ============================================================================
 * TLS Setup
 * ============================================================================ */

glibc_tls_t* setup_glibc_tls(elfheader_t* elf) {
    /*
     * glibc ARM64 TLS layout:
     * [TLS module data][Extra TLS for dyn libs][struct pthread (2KB)][tcbhead_t (16B)][DTV array]
     *                                                                  ^ tpidr_el0 points here
     * 
     * We allocate extra space for dynamically loaded libraries (like libcoreclr.so)
     * that may have their own TLS variables.
     */
    
    size_t tls_data_size = elf->tlssize ? elf->tlssize : 0;
    size_t total_size = tls_data_size + TLS_EXTRA_SIZE + TLS_PRE_TCB_SIZE + TLS_TCB_SIZE + TLS_POST_TCB_SIZE;
    total_size = (total_size + 15) & ~15;
    
    /* Allocate TLS memory */
    void* tls_block = mmap(NULL, total_size,
                           PROT_READ | PROT_WRITE,
                           MAP_PRIVATE | MAP_ANONYMOUS,
                           -1, 0);
    if (tls_block == MAP_FAILED) {
        LOG_ERROR("Failed to allocate TLS: %s", strerror(errno));
        return NULL;
    }
    
    memset(tls_block, 0, total_size);
    
    /* Calculate addresses - include extra space for dynamic libraries */
    void* pthread_struct = (void*)((uintptr_t)tls_block + tls_data_size + TLS_EXTRA_SIZE);
    void* tcb = (void*)((uintptr_t)pthread_struct + TLS_PRE_TCB_SIZE);
    void* dtv_base = (void*)((uintptr_t)tcb + TLS_TCB_SIZE);
    
    /* Setup DTV */
    uint64_t* dtv = (uint64_t*)dtv_base;
    dtv[0] = 1;  /* generation counter */
    dtv[1] = 0;
    dtv[2] = (uint64_t)tls_block;
    
    /* Setup tcbhead_t */
    uint64_t* tcb_ptr = (uint64_t*)tcb;
    tcb_ptr[0] = (uint64_t)(dtv + 1);  /* dtv pointer */
    tcb_ptr[1] = 0;                     /* private */
    
    /* Setup pthread struct header */
    uint64_t* pthread_ptr = (uint64_t*)pthread_struct;
    pthread_ptr[0] = (uint64_t)tcb;
    
    /* Setup stack canary */
    uint64_t canary = 0;
    int fd = open("/dev/urandom", O_RDONLY);
    if (fd >= 0) {
        read(fd, &canary, sizeof(canary));
        close(fd);
    }
    
    if (TLS_PRE_TCB_SIZE >= 0x30) {
        *(uint64_t*)((uintptr_t)pthread_struct + 0x28) = canary;
    }
    *(uint64_t*)((uintptr_t)tcb + 0x28) = canary;
    
    /* Create handle */
    glibc_tls_t* tls = (glibc_tls_t*)malloc(sizeof(glibc_tls_t));
    if (!tls) {
        munmap(tls_block, total_size);
        return NULL;
    }
    
    tls->tls_block = tls_block;
    tls->tls_size = total_size;
    tls->tcb = tcb;
    
    LOG_DEBUG("TLS setup: block=%p, tcb=%p, size=%zu", tls_block, tcb, total_size);
    
    return tls;
}

void free_glibc_tls(glibc_tls_t* tls) {
    if (tls) {
        if (tls->tls_block) {
            munmap(tls->tls_block, tls->tls_size);
        }
        free(tls);
    }
}

void set_tls_register(void* tcb) {
    __asm__ volatile("msr tpidr_el0, %0" : : "r" (tcb));
}

/* ============================================================================
 * Entry Point Jump
 * ============================================================================ */

__attribute__((noreturn))
void jump_to_entry(uintptr_t entry, uintptr_t sp) {
    __asm__ volatile(
        "mov x19, %[entry]\n"
        "mov sp, %[sp]\n"
        "mov x0, #0\n"
        "mov x1, #0\n"
        "mov x2, #0\n"
        "mov x3, #0\n"
        "mov x4, #0\n"
        "mov x5, #0\n"
        "mov x6, #0\n"
        "mov x7, #0\n"
        "mov x8, #0\n"
        "mov x9, #0\n"
        "mov x10, #0\n"
        "mov x11, #0\n"
        "mov x12, #0\n"
        "mov x13, #0\n"
        "mov x14, #0\n"
        "mov x15, #0\n"
        "mov x16, #0\n"
        "mov x17, #0\n"
        "mov x18, #0\n"
        "mov x20, #0\n"
        "mov x21, #0\n"
        "mov x22, #0\n"
        "mov x23, #0\n"
        "mov x24, #0\n"
        "mov x25, #0\n"
        "mov x26, #0\n"
        "mov x27, #0\n"
        "mov x28, #0\n"
        "mov x29, #0\n"
        "mov x30, #0\n"
        "br x19\n"
        :
        : [sp] "r" (sp), [entry] "r" (entry)
        : "memory", "x19"
    );
    
    __builtin_unreachable();
}

/* ============================================================================
 * Forked Execution with Output Capture
 * ============================================================================ */

/* Run dynamic linked ELF using wrapper-based linking (no external libs needed) */
static void run_dynamic_elf_wrapped(glibc_bridge_t bta, elfheader_t* elf,
                                    uintptr_t sp, glibc_tls_t* tls) {
    
    /* Set log level from environment if not set (default: INFO=3)
     * GLIBC_BRIDGE_LOG_LEVEL: 0=NONE, 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG, 5=TRACE */
    if (!getenv("GLIBC_BRIDGE_LOG_LEVEL")) {
        setenv("GLIBC_BRIDGE_LOG_LEVEL", "3", 0);  /* Default INFO level */
    }
    
    /* Initialize error hook for capturing bionic call failures */
    extern void glibc_bridge_error_hook_init(void);
    glibc_bridge_error_hook_init();
    
    /* Initialize root compatibility layer */
    extern void glibc_bridge_root_init(void);
    glibc_bridge_root_init();
    
    /* Use write() syscall for logging in child process */
    const char* msg1 = "[CHILD] Running dynamic ELF with wrapper-based linking\n";
    write(STDERR_FILENO, msg1, strlen(msg1));
    
    /* Initialize fake glibc rootfs and load dependencies */
    extern int glibc_bridge_setup_fake_rootfs(const char*);
    extern int glibc_bridge_load_elf_dependencies(elfheader_t*, const char*);
    extern const char* glibc_bridge_get_glibc_root(void);
    extern const char* g_app_files_dir;
    
    /* Get rootfs path - prefer g_app_files_dir, fallback to glibc_root */
    const char* rootfs_path = g_app_files_dir;
    if (!rootfs_path || !rootfs_path[0]) {
        rootfs_path = glibc_bridge_get_glibc_root();
    }
    
    if (rootfs_path && rootfs_path[0]) {
        char buf[256];
        snprintf(buf, sizeof(buf), "[CHILD] Setting up fake glibc rootfs (from %s)...\n", rootfs_path);
        write(STDERR_FILENO, buf, strlen(buf));
        
        if (glibc_bridge_setup_fake_rootfs(rootfs_path) == 0) {
            const char* glibc_root = glibc_bridge_get_glibc_root();
            snprintf(buf, sizeof(buf), "[CHILD] Fake rootfs ready: %s\n", glibc_root ? glibc_root : "NULL");
            write(STDERR_FILENO, buf, strlen(buf));
        }
        
        /* Load and relocate ELF dependencies (uses g_glibc_root internally) */
        write(STDERR_FILENO, "[CHILD] Loading ELF dependencies...\n", 37);
        glibc_bridge_load_elf_dependencies(elf, NULL);
    }
    
    /* Relocate using our symbol wrappers */
    int reloc_result = glibc_bridge_relocate_dynamic(elf);
    if (reloc_result < 0) {
        const char* msg_err = "[CHILD] ERROR: Failed to relocate dynamic ELF\n";
        write(STDERR_FILENO, msg_err, strlen(msg_err));
        _exit(126);
    }
    
    char buf[128];
    snprintf(buf, sizeof(buf), "[CHILD] Relocation complete, entry=0x%lx\n", 
             (unsigned long)(elf->entrypoint + elf->delta));
    write(STDERR_FILENO, buf, strlen(buf));
    
    /* Get entry point */
    uintptr_t entry = elf->entrypoint + elf->delta;
    
    /* TLS Strategy:
     * - Keep bionic TLS (tpidr_el0) intact so bionic functions in wrappers work
     * - Initialize our glibc compatibility TLS layer (thread-local variables)
     * - glibc-specific functions (__ctype_b_loc, __errno_location, etc.)
     *   will use our TLS wrappers instead of real glibc TLS
     */
    (void)tls;  /* We don't use the glibc TLS block anymore */
    
    /* Initialize glibc TLS compatibility layer */
    glibc_bridge_init_glibc_tls();
    const char* msg_tls = "[CHILD] glibc TLS compatibility layer initialized (bionic TLS preserved)\n";
    write(STDERR_FILENO, msg_tls, strlen(msg_tls));
    
    /* Debug: Verify stack contents before jumping (use simple hex output) */
    {
        const char* msg_sp = "[DEBUG] sp=0x";
        write(STDERR_FILENO, msg_sp, strlen(msg_sp));
        
        /* Simple hex output for sp */
        char hex[17];
        uintptr_t val = sp;
        for (int i = 15; i >= 0; i--) {
            int digit = val & 0xF;
            hex[i] = digit < 10 ? '0' + digit : 'a' + digit - 10;
            val >>= 4;
        }
        hex[16] = '\n';
        write(STDERR_FILENO, hex, 17);
        
        /* Read argc from stack */
        uintptr_t* stack_ptr = (uintptr_t*)sp;
        const char* msg_argc = "[DEBUG] argc=";
        write(STDERR_FILENO, msg_argc, strlen(msg_argc));
        
        val = stack_ptr[0];
        for (int i = 15; i >= 0; i--) {
            int digit = val & 0xF;
            hex[i] = digit < 10 ? '0' + digit : 'a' + digit - 10;
            val >>= 4;
        }
        hex[16] = '\n';
        write(STDERR_FILENO, hex, 17);
        
        /* Read argv[0] pointer from stack */
        const char* msg_argv = "[DEBUG] argv0=0x";
        write(STDERR_FILENO, msg_argv, strlen(msg_argv));
        
        val = stack_ptr[1];
        for (int i = 15; i >= 0; i--) {
            int digit = val & 0xF;
            hex[i] = digit < 10 ? '0' + digit : 'a' + digit - 10;
            val >>= 4;
        }
        hex[16] = '\n';
        write(STDERR_FILENO, hex, 17);
    }
    
    const char* msg_jump = "[CHILD] Jumping to entry point...\n";
    write(STDERR_FILENO, msg_jump, strlen(msg_jump));
    
    /* Force flush stderr */
    fsync(STDERR_FILENO);
    
    /* Jump to entry */
    jump_to_entry(entry, sp);
}

/* ============================================================================
 * Direct Execution (No Fork) - Required for JNI compatibility
 * ============================================================================ */

int run_elf_direct(glibc_bridge_t bta, elfheader_t* elf,
                   int argc, char** argv, char** envp,
                   glibc_bridge_result_t* result) {
    
    LOG_INFO("=== Direct Execution Mode (no fork, JNI compatible) ===");
    
    /* Check if dynamic linked */
    int is_dynamic = !elf->is_static && elf->interp != NULL;
    
    if (is_dynamic) {
        LOG_INFO("Detected dynamic linked ELF (interpreter: %s)", elf->interp);
    }
    
    /* Allocate stack */
    size_t stack_size = bta->config.stack_size;
    if (stack_size == 0) stack_size = DEFAULT_STACK_SIZE;
    
    void* stack = alloc_stack(stack_size);
    if (!stack) {
        LOG_ERROR("Failed to allocate stack");
        return GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY;
    }
    
    /* Setup stack */
    uintptr_t sp = setup_stack(stack, stack_size, argc, argv, envp, elf);
    
    /* Setup TLS */
    glibc_tls_t* tls = NULL;
    if (bta->config.use_tls) {
        tls = setup_glibc_tls(elf);
        if (!tls) {
            free_stack(stack, stack_size);
            return GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY;
        }
    }
    
    /* Install crash handlers */
    glibc_bridge_install_crash_handlers();
    
    /* Set log level from environment if not set */
    if (!getenv("GLIBC_BRIDGE_LOG_LEVEL")) {
        setenv("GLIBC_BRIDGE_LOG_LEVEL", "3", 0);
    }
    
    /* Initialize error hook for capturing bionic call failures */
    extern void glibc_bridge_error_hook_init(void);
    glibc_bridge_error_hook_init();
    
    /* Initialize root compatibility layer */
    extern void glibc_bridge_root_init(void);
    glibc_bridge_root_init();
    
    LOG_INFO("[DIRECT] Running dynamic ELF with wrapper-based linking");
    
    /* Initialize fake glibc rootfs and load dependencies */
    extern int glibc_bridge_setup_fake_rootfs(const char*);
    extern int glibc_bridge_load_elf_dependencies(elfheader_t*, const char*);
    extern const char* glibc_bridge_get_glibc_root(void);
    extern const char* g_app_files_dir;
    
    const char* rootfs_path = g_app_files_dir;
    if (!rootfs_path || !rootfs_path[0]) {
        rootfs_path = glibc_bridge_get_glibc_root();
    }
    
    if (rootfs_path && rootfs_path[0]) {
        LOG_INFO("[DIRECT] Setting up fake glibc rootfs (from %s)...", rootfs_path);
        
        if (glibc_bridge_setup_fake_rootfs(rootfs_path) == 0) {
            const char* glibc_root = glibc_bridge_get_glibc_root();
            LOG_INFO("[DIRECT] Fake rootfs ready: %s", glibc_root ? glibc_root : "NULL");
        }
        
        LOG_INFO("[DIRECT] Loading ELF dependencies...");
        glibc_bridge_load_elf_dependencies(elf, NULL);
    }
    
    /* Relocate using our symbol wrappers */
    int reloc_result = glibc_bridge_relocate_dynamic(elf);
    if (reloc_result < 0) {
        LOG_ERROR("[DIRECT] Failed to relocate dynamic ELF");
        free_stack(stack, stack_size);
        free_glibc_tls(tls);
        return GLIBC_BRIDGE_ERROR_EXEC_FAILED;
    }
    
    LOG_INFO("[DIRECT] Relocation complete, entry=0x%lx", 
             (unsigned long)(elf->entrypoint + elf->delta));
    
    /* Initialize glibc TLS compatibility layer */
    glibc_bridge_init_glibc_tls();
    LOG_INFO("[DIRECT] glibc TLS compatibility layer initialized");
    
    /* Get entry point */
    uintptr_t entry = elf->entrypoint + elf->delta;
    
    /* Setup exit handler for direct execution mode */
    g_exit_handler_active = 1;
    
    int exit_code = 0;
    if (sigsetjmp(g_exit_jump_buf, 1) == 0) {
        /* First call - jump to entry point */
        LOG_INFO("[DIRECT] Jumping to entry point 0x%lx...", (unsigned long)entry);
        
        /* Note: jump_to_entry is noreturn, but exit() will call our handler
         * which will longjmp back here */
        jump_to_entry(entry, sp);
        
        /* Should never reach here unless exit handler works */
        exit_code = 0;
    } else {
        /* Returned from exit handler via longjmp */
        exit_code = g_exit_code;
        LOG_INFO("[DIRECT] Program exited with code: %d", exit_code);
    }
    
    /* Disable exit handler */
    g_exit_handler_active = 0;
    
    /* Cleanup */
    free_stack(stack, stack_size);
    free_glibc_tls(tls);
    
    /* Process result */
    if (result) {
        result->exited = 1;
        result->exit_code = exit_code;
        result->signal = 0;
    }
    
    return exit_code;
}

int run_elf_forked(glibc_bridge_t bta, elfheader_t* elf,
                   int argc, char** argv, char** envp,
                   glibc_bridge_result_t* result) {
    
    /* Check if dynamic linked */
    int is_dynamic = !elf->is_static && elf->interp != NULL;
    
    if (is_dynamic) {
        LOG_INFO("Detected dynamic linked ELF (interpreter: %s)", elf->interp);
    }
    
    /* Allocate stack */
    size_t stack_size = bta->config.stack_size;
    if (stack_size == 0) stack_size = DEFAULT_STACK_SIZE;
    
    void* stack = alloc_stack(stack_size);
    if (!stack) {
        LOG_ERROR("Failed to allocate stack");
        return GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY;
    }
    
    /* Setup stack */
    uintptr_t sp = setup_stack(stack, stack_size, argc, argv, envp, elf);
    
    /* Setup TLS */
    glibc_tls_t* tls = NULL;
    if (bta->config.use_tls) {
        tls = setup_glibc_tls(elf);
        if (!tls) {
            free_stack(stack, stack_size);
            return GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY;
        }
    }
    
    /* Create pipes for output capture */
    int stdout_pipe[2] = {-1, -1};
    int stderr_pipe[2] = {-1, -1};
    
    if (bta->config.redirect_output) {
        if (pipe(stdout_pipe) < 0 || pipe(stderr_pipe) < 0) {
            LOG_ERROR("Failed to create pipes: %s", strerror(errno));
            if (stdout_pipe[0] >= 0) { close(stdout_pipe[0]); close(stdout_pipe[1]); }
            free_stack(stack, stack_size);
            free_glibc_tls(tls);
            return GLIBC_BRIDGE_ERROR_FORK_FAILED;
        }
    }
    
    /* Get entry point */
    uintptr_t entry = elf->entrypoint + elf->delta;
    
    LOG_INFO("Forking to execute ELF...");
    
    pid_t pid = fork();
    
    if (pid < 0) {
        LOG_ERROR("fork() failed: %s", strerror(errno));
        if (bta->config.redirect_output) {
            close(stdout_pipe[0]); close(stdout_pipe[1]);
            close(stderr_pipe[0]); close(stderr_pipe[1]);
        }
        free_stack(stack, stack_size);
        free_glibc_tls(tls);
        return GLIBC_BRIDGE_ERROR_FORK_FAILED;
    }
    
    if (pid == 0) {
        /* Child process */
        
        /* Install crash handlers for debugging */
        glibc_bridge_install_crash_handlers();
        
        /* Close read ends of pipes */
        if (bta->config.redirect_output) {
            close(stdout_pipe[0]);
            close(stderr_pipe[0]);
        }
        
        /* Redirect output */
        if (bta->config.redirect_output) {
            dup2(stdout_pipe[1], STDOUT_FILENO);
            dup2(stderr_pipe[1], STDERR_FILENO);
            close(stdout_pipe[1]);
            close(stderr_pipe[1]);
        }
        
        /* Handle dynamic vs static execution */
        if (is_dynamic) {
            /* Dynamic linked: use wrapper-based linking (no external libs!) */
            run_dynamic_elf_wrapped(bta, elf, sp, tls);
            /* Should not return */
            _exit(127);
        } else {
            /* Static linked: direct execution */
            
            /* Set TLS */
            if (tls) {
                set_tls_register(tls->tcb);
            }
            
            /* Jump to entry */
            jump_to_entry(entry, sp);
        }
        
        _exit(199);
    }
    
    /* Parent process */
    LOG_INFO("Child process pid=%d", pid);
    
    /* Close write ends of pipes */
    if (bta->config.redirect_output) {
        close(stdout_pipe[1]);
        close(stderr_pipe[1]);
        
        /* Read output from pipes */
        fd_set readfds;
        char buf[1024];
        int max_fd = (stdout_pipe[0] > stderr_pipe[0]) ? stdout_pipe[0] : stderr_pipe[0];
        int stdout_open = 1, stderr_open = 1;
        
        while (stdout_open || stderr_open) {
            FD_ZERO(&readfds);
            if (stdout_open) FD_SET(stdout_pipe[0], &readfds);
            if (stderr_open) FD_SET(stderr_pipe[0], &readfds);
            
            struct timeval tv = { .tv_sec = 0, .tv_usec = 100000 };
            int ret = select(max_fd + 1, &readfds, NULL, NULL, &tv);
            
            if (ret < 0) {
                if (errno == EINTR) continue;
                break;
            }
            
            if (ret == 0) {
                int wstatus;
                pid_t wpid = waitpid(pid, &wstatus, WNOHANG);
                if (wpid == pid) {
                    /* Child exited, drain remaining output */
                    while (stdout_open) {
                        ssize_t n = read(stdout_pipe[0], buf, sizeof(buf) - 1);
                        if (n <= 0) { stdout_open = 0; break; }
                        buf[n] = '\0';
                        LOG_INFO("[STDOUT] %s", buf);
                    }
                    while (stderr_open) {
                        ssize_t n = read(stderr_pipe[0], buf, sizeof(buf) - 1);
                        if (n <= 0) { stderr_open = 0; break; }
                        buf[n] = '\0';
                        LOG_INFO("[STDERR] %s", buf);
                    }
                    break;
                }
                continue;
            }
            
            if (stdout_open && FD_ISSET(stdout_pipe[0], &readfds)) {
                ssize_t n = read(stdout_pipe[0], buf, sizeof(buf) - 1);
                if (n <= 0) {
                    stdout_open = 0;
                } else {
                    buf[n] = '\0';
                    char* line = buf;
                    char* newline;
                    while ((newline = strchr(line, '\n')) != NULL) {
                        *newline = '\0';
                        LOG_INFO("[STDOUT] %s", line);
                        line = newline + 1;
                    }
                    if (*line) LOG_INFO("[STDOUT] %s", line);
                }
            }
            
            if (stderr_open && FD_ISSET(stderr_pipe[0], &readfds)) {
                ssize_t n = read(stderr_pipe[0], buf, sizeof(buf) - 1);
                if (n <= 0) {
                    stderr_open = 0;
                } else {
                    buf[n] = '\0';
                    char* line = buf;
                    char* newline;
                    while ((newline = strchr(line, '\n')) != NULL) {
                        *newline = '\0';
                        LOG_INFO("[STDERR] %s", line);
                        line = newline + 1;
                    }
                    if (*line) LOG_INFO("[STDERR] %s", line);
                }
            }
        }
        
        close(stdout_pipe[0]);
        close(stderr_pipe[0]);
    }
    
    /* Wait for child */
    int status;
    waitpid(pid, &status, 0);
    
    /* Cleanup */
    free_stack(stack, stack_size);
    free_glibc_tls(tls);
    
    /* Process result */
    int ret_code;
    if (WIFEXITED(status)) {
        ret_code = WEXITSTATUS(status);
        LOG_INFO("ELF exited with code: %d", ret_code);
        if (result) {
            result->exited = 1;
            result->exit_code = ret_code;
            result->signal = 0;
        }
    } else if (WIFSIGNALED(status)) {
        int sig = WTERMSIG(status);
        LOG_ERROR("ELF killed by signal: %d", sig);
        ret_code = GLIBC_BRIDGE_ERROR_SIGNAL - sig;
        if (result) {
            result->exited = 0;
            result->exit_code = 0;
            result->signal = sig;
        }
    } else {
        ret_code = GLIBC_BRIDGE_ERROR_EXEC_FAILED;
    }
    
    return ret_code;
}

