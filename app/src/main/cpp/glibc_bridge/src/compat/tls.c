/*
 * glibc-bridge TLS 兼容层实现
 * 
 * 在保留 bionic TLS 的同时提供 glibc 兼容的 TLS 函数
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>

#include "tls.h"
#include "../include/private.h"

/* ============================================================================
 * Thread-Local Storage for glibc Compatibility
 * ============================================================================ */

/* glibc-compatible TLS instance (per-thread) */
__thread glibc_compat_tls_t g_glibc_tls = {0};

/* Static ctype tables (glibc format, initialized once) */
static unsigned short g_ctype_b_table[384];
static int g_ctype_tolower_table[384];
static int g_ctype_toupper_table[384];

/* Pointers to middle of tables (for negative index access like glibc) */
static const unsigned short* g_ctype_b_ptr = NULL;
static const int* g_ctype_tolower_ptr = NULL;
static const int* g_ctype_toupper_ptr = NULL;

/* Initialization flag */
static int g_tls_initialized = 0;

/* ============================================================================
 * ctype Table Initialization
 * 
 * glibc's ctype functions use tables that can be indexed by character values
 * including EOF (-1). The tables are offset by 128 to allow negative indices.
 * ============================================================================ */

static void init_ctype_tables(void) {
    if (g_ctype_b_ptr != NULL) {
        return;  /* Already initialized */
    }
    
    /* Build glibc-format ctype tables using bionic's ctype functions */
    for (int c = -128; c < 256; c++) {
        int idx = c + 128;  /* Table index (0-383) */
        unsigned short flags = 0;
        
        /* Map bionic ctype results to glibc flags */
        /* Note: We use the C locale / ASCII character set */
        if (c >= 0 && c <= 127) {
            if (isupper(c))  flags |= _GLIBC_ISupper;
            if (islower(c))  flags |= _GLIBC_ISlower;
            if (isalpha(c))  flags |= _GLIBC_ISalpha;
            if (isdigit(c))  flags |= _GLIBC_ISdigit;
            if (isxdigit(c)) flags |= _GLIBC_ISxdigit;
            if (isspace(c))  flags |= _GLIBC_ISspace;
            if (isprint(c))  flags |= _GLIBC_ISprint;
            if (isgraph(c))  flags |= _GLIBC_ISgraph;
            if (isblank(c))  flags |= _GLIBC_ISblank;
            if (iscntrl(c))  flags |= _GLIBC_IScntrl;
            if (ispunct(c))  flags |= _GLIBC_ISpunct;
            if (isalnum(c))  flags |= _GLIBC_ISalnum;
            
            g_ctype_tolower_table[idx] = tolower(c);
            g_ctype_toupper_table[idx] = toupper(c);
        } else {
            /* For values outside 0-127, leave as identity */
            g_ctype_tolower_table[idx] = c;
            g_ctype_toupper_table[idx] = c;
        }
        
        g_ctype_b_table[idx] = flags;
    }
    
    /* Set pointers to middle of tables (offset 128) for negative index access */
    g_ctype_b_ptr = g_ctype_b_table + 128;
    g_ctype_tolower_ptr = g_ctype_tolower_table + 128;
    g_ctype_toupper_ptr = g_ctype_toupper_table + 128;
}

/* ============================================================================
 * Initialization
 * ============================================================================ */

void glibc_bridge_init_glibc_tls(void) {
    if (g_tls_initialized) {
        return;
    }
    
    /* Initialize ctype tables */
    init_ctype_tables();
    
    /* Setup thread-local pointers */
    g_glibc_tls.ctype_b = g_ctype_b_ptr;
    g_glibc_tls.ctype_tolower = g_ctype_tolower_ptr;
    g_glibc_tls.ctype_toupper = g_ctype_toupper_ptr;
    
    /* Sync stack guard from bionic */
    glibc_bridge_sync_stack_guard();
    
    g_tls_initialized = 1;
}

void glibc_bridge_sync_stack_guard(void) {
    /* Copy stack canary from bionic TLS to our glibc-compat TLS */
    void** bionic_tls = bionic_get_tls();
    if (bionic_tls) {
        /* Stack guard is at slot 6 in bionic TLS */
        uintptr_t canary = (uintptr_t)bionic_tls[BIONIC_TLS_SLOT_STACK_GUARD];
        g_glibc_tls.stack_guard = canary;
        
        /* Debug: verify the canary is accessible at the expected offset */
        uintptr_t* check_ptr = (uintptr_t*)((char*)&g_glibc_tls + 0x28);
        LOG_INFO("Stack guard synced: canary=0x%lx, at &g_glibc_tls+0x28=0x%lx (match=%s)",
                 (unsigned long)canary, (unsigned long)*check_ptr,
                 (canary == *check_ptr) ? "YES" : "NO!");
    } else {
        LOG_ERROR("Failed to get bionic TLS for stack guard!");
    }
}

/* ============================================================================
 * errno Support
 * ============================================================================ */

int* glibc_bridge_errno_location(void) {
    /* glibc programs call __errno_location() to get &errno.
     * We return a pointer to our thread-local errno. */
    return &g_glibc_tls.glibc_errno;
}

/* Wrapper for __errno_location (glibc) */
int* __errno_location_wrapper(void) {
    /* Return glibc errno location directly.
     * Do NOT sync from bionic here - that would overwrite values
     * the glibc program just set via errno = X.
     * 
     * Sync should happen in wrapper functions after calling bionic:
     *   glibc_errno = bionic_errno; (copy error status back)
     */
    return &g_glibc_tls.glibc_errno;
}

/* Helper to sync bionic errno to glibc errno after wrapper calls bionic */
void glibc_bridge_sync_errno_from_bionic(void) {
    int bionic_errno = errno;
    g_glibc_tls.glibc_errno = bionic_errno;
    
    /* Auto-log errors if in wrapper context */
    if (bionic_errno != 0) {
        extern void glibc_bridge_log_bionic_error(int);
        glibc_bridge_log_bionic_error(bionic_errno);
    }
}

/* Sync errno silently (no logging) - use when call succeeded */
void glibc_bridge_sync_errno_silent(void) {
    g_glibc_tls.glibc_errno = errno;
}

/* ============================================================================
 * ctype Wrappers
 * ============================================================================ */

const unsigned short** glibc_bridge_ctype_b_loc(void) {
    /* Initialize global tables if needed */
    if (g_ctype_b_ptr == NULL) {
        init_ctype_tables();
    }
    /* Always ensure thread-local pointer is set (TLS is per-thread!) */
    if (g_glibc_tls.ctype_b == NULL) {
        g_glibc_tls.ctype_b = g_ctype_b_ptr;
    }
    return &g_glibc_tls.ctype_b;
}

const int** glibc_bridge_ctype_tolower_loc(void) {
    /* Initialize global tables if needed */
    if (g_ctype_tolower_ptr == NULL) {
        init_ctype_tables();
    }
    /* Always ensure thread-local pointer is set (TLS is per-thread!) */
    if (g_glibc_tls.ctype_tolower == NULL) {
        g_glibc_tls.ctype_tolower = g_ctype_tolower_ptr;
    }
    return &g_glibc_tls.ctype_tolower;
}

const int** glibc_bridge_ctype_toupper_loc(void) {
    /* Initialize global tables if needed */
    if (g_ctype_toupper_ptr == NULL) {
        init_ctype_tables();
    }
    /* Always ensure thread-local pointer is set (TLS is per-thread!) */
    if (g_glibc_tls.ctype_toupper == NULL) {
        g_glibc_tls.ctype_toupper = g_ctype_toupper_ptr;
    }
    return &g_glibc_tls.ctype_toupper;
}

/* ============================================================================
 * Exported Wrapper Functions (these names match glibc symbols)
 * ============================================================================ */

/* glibc __ctype_b_loc - returns pointer to character classification table */
const unsigned short** __ctype_b_loc_wrapper(void) {
    return glibc_bridge_ctype_b_loc();
}

/* glibc __ctype_tolower_loc - returns pointer to tolower table */
const int** __ctype_tolower_loc_wrapper(void) {
    return glibc_bridge_ctype_tolower_loc();
}

/* glibc __ctype_toupper_loc - returns pointer to toupper table */
const int** __ctype_toupper_loc_wrapper(void) {
    return glibc_bridge_ctype_toupper_loc();
}

/* ============================================================================
 * Exports with original glibc names (for Box64 to find via dlsym)
 * ============================================================================ */

__attribute__((visibility("default")))
const unsigned short** __ctype_b_loc(void) {
    return __ctype_b_loc_wrapper();
}

__attribute__((visibility("default")))
const int** __ctype_tolower_loc(void) {
    return __ctype_tolower_loc_wrapper();
}

__attribute__((visibility("default")))
const int** __ctype_toupper_loc(void) {
    return __ctype_toupper_loc_wrapper();
}

/* ============================================================================
 * Dynamic Library TLS Storage
 * 
 * This provides TLS storage for dynamically loaded glibc libraries.
 * The storage is zeroed on first access, which is important because
 * uninitialized TLS variables should be zero.
 * ============================================================================ */

/* Thread-local TLS storage for dynamic libraries - initialized to zero */
static __thread char g_dynlib_tls_storage[GLIBC_BRIDGE_DYNLIB_TLS_SIZE] __attribute__((aligned(16)));
static __thread int g_dynlib_tls_initialized = 0;

/* Get pointer to our dynamic library TLS storage */
void* glibc_bridge_get_dynlib_tls_base(void) {
    if (!g_dynlib_tls_initialized) {
        /* Ensure it's zeroed (should be by default, but be safe) */
        memset(g_dynlib_tls_storage, 0, GLIBC_BRIDGE_DYNLIB_TLS_SIZE);
        g_dynlib_tls_initialized = 1;
    }
    return g_dynlib_tls_storage;
}

/* ============================================================================
 * TLSDESC Resolver Implementation
 * 
 * AArch64 TLSDESC calling convention:
 * - x0 points to the TLS descriptor
 * - Returns offset from thread pointer in x0
 * - Caller will do: mrs xN, TPIDR_EL0; add xN, xN, x0 to get TLS variable address
 * - Must preserve all registers except x0
 * 
 * We compute a "fake offset" so that TPIDR_EL0 + fake_offset points to our
 * custom TLS storage.
 * ============================================================================ */

/* C function to compute fake TLS offset */
intptr_t glibc_bridge_tlsdesc_resolve_impl(void* desc) {
    uintptr_t real_offset = ((uintptr_t*)desc)[1];  /* arg at offset 8 */
    void* base = glibc_bridge_get_dynlib_tls_base();
    
    /* Get current TPIDR_EL0 */
    uintptr_t tpidr;
    __asm__ volatile("mrs %0, TPIDR_EL0" : "=r"(tpidr));
    
    /* Compute fake offset: target_addr - tpidr */
    uintptr_t target_addr = (uintptr_t)base + real_offset;
    intptr_t fake_offset = (intptr_t)(target_addr - tpidr);
    
    return fake_offset;
}

/* Assembly wrapper that calls the C function
 * This needs to save/restore all registers that the caller might expect preserved */
__attribute__((naked))
void glibc_bridge_tlsdesc_resolver_static(void) {
    __asm__ volatile(
        /* Save registers that we'll clobber (caller-saved + lr) */
        "stp x29, x30, [sp, #-16]!\n"
        "stp x1, x2, [sp, #-16]!\n"
        "stp x3, x4, [sp, #-16]!\n"
        "stp x5, x6, [sp, #-16]!\n"
        "stp x7, x8, [sp, #-16]!\n"
        "stp x9, x10, [sp, #-16]!\n"
        "stp x11, x12, [sp, #-16]!\n"
        "stp x13, x14, [sp, #-16]!\n"
        "stp x15, x16, [sp, #-16]!\n"
        "stp x17, x18, [sp, #-16]!\n"
        
        /* x0 already has the descriptor pointer */
        "bl glibc_bridge_tlsdesc_resolve_impl\n"
        
        /* Restore registers (x0 has result) */
        "ldp x17, x18, [sp], #16\n"
        "ldp x15, x16, [sp], #16\n"
        "ldp x13, x14, [sp], #16\n"
        "ldp x11, x12, [sp], #16\n"
        "ldp x9, x10, [sp], #16\n"
        "ldp x7, x8, [sp], #16\n"
        "ldp x5, x6, [sp], #16\n"
        "ldp x3, x4, [sp], #16\n"
        "ldp x1, x2, [sp], #16\n"
        "ldp x29, x30, [sp], #16\n"
        
        "ret\n"
    );
}

