/*
 * glibc-bridge 动态链接器 - 符号解析
 * 
 * 通过以下方式在动态链接期间解析符号：
 * 1. 包装符号表（symbol_table.c）
 * 2. 已加载的 glibc 共享库（libstdc++.so.6、libgcc_s.so.1 等）
 * 3. 当前加载的 ELF 符号表（如果已设置）
 * 4. bionic 的默认符号解析
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>

#include "../include/private.h"
#include "../include/wrappers.h"
#include "log.h"

/* External: resolve symbol from loaded shared libraries (glibc_bridge_sharedlib.c) */
extern void* glibc_bridge_resolve_from_shared_libs(const char* name);

/* ============================================================================
 * Global State
 * ============================================================================ */

/* Current ELF context for symbol resolution */
static elfheader_t* g_current_elf = NULL;
static Elf64_Sym* g_current_symtab = NULL;
static const char* g_current_strtab = NULL;
static size_t g_current_symcount = 0;

/* ============================================================================
 * Symbol Context Management
 * ============================================================================ */

/**
 * Set symbol context for internal symbol resolution
 * Called when loading an ELF to enable resolution of its internal symbols
 */
void glibc_bridge_set_symbol_context(elfheader_t* elf, Elf64_Sym* symtab, 
                                     const char* strtab, size_t symcount) {
    g_current_elf = elf;
    g_current_symtab = symtab;
    g_current_strtab = strtab;
    g_current_symcount = symcount;
}

/**
 * Get current ELF being loaded
 */
elfheader_t* glibc_bridge_get_current_elf(void) {
    return g_current_elf;
}

/* ============================================================================
 * Symbol Resolution
 * ============================================================================ */

/**
 * Lookup symbol in wrapper table
 * Returns wrapper function pointer or NULL if not found
 */
void* glibc_bridge_lookup_symbol(const char* name) {
    const symbol_wrapper_t* table = glibc_bridge_get_symbol_table();
    
    for (const symbol_wrapper_t* w = table; w->name; w++) {
        if (strcmp(w->name, name) == 0) {
            if (w->wrapper) {
                return w->wrapper;
            } else {
                /* NULL wrapper means use bionic directly */
                return dlsym(RTLD_DEFAULT, name);
            }
        }
    }
    
    return NULL;
}

/**
 * Resolve symbol (returns wrapper, shared lib, bionic, or internal function)
 * 
 * Resolution order:
 * 1. Check wrapper symbol table
 * 2. Check loaded glibc shared libraries (libstdc++.so.6, libgcc_s.so.1, etc.)
 * 3. Check current ELF's symbol table (if set)
 * 4. Try bionic's dlsym(RTLD_DEFAULT)
 */
__attribute__((visibility("default")))
void* glibc_bridge_resolve_symbol(const char* name) {
    void* result = NULL;
    
    /* Safety check */
    if (!name || !name[0]) {
        return NULL;
    }
    
    /* Handle glibc version suffix (e.g., strtoull@@GLIBC_2.17 -> strtoull) */
    char clean_name[256];
    const char* at_sign = strstr(name, "@@");
    if (!at_sign) {
        at_sign = strchr(name, '@');
    }
    if (at_sign) {
        size_t len = at_sign - name;
        if (len < sizeof(clean_name)) {
            memcpy(clean_name, name, len);
            clean_name[len] = '\0';
            name = clean_name;
        }
    }
    
    /* Step 1: Check wrapper symbol table */
    result = glibc_bridge_lookup_symbol(name);
    if (result) {
        return result;
    }
    
    /* Step 2: Check loaded glibc shared libraries (libstdc++.so.6, libgcc_s.so.1, etc.)
     * This is critical for C++ symbols like _ZNSt8ios_base4InitD1Ev that are
     * defined in libstdc++ but not in our wrapper table */
    result = glibc_bridge_resolve_from_shared_libs(name);
    if (result) {
        return result;
    }
    
    /* Step 3: Check current ELF's symbol table (for internal symbols) */
    if (g_current_symtab && g_current_strtab && g_current_symcount > 0) {
        for (size_t i = 0; i < g_current_symcount; i++) {
            Elf64_Sym* sym = &g_current_symtab[i];
            
            /* Skip undefined symbols */
            if (sym->st_shndx == SHN_UNDEF) continue;
            if (sym->st_value == 0) continue;
            
            /* Check binding - we want GLOBAL or WEAK */
            unsigned char bind = ELF64_ST_BIND(sym->st_info);
            if (bind != STB_GLOBAL && bind != STB_WEAK) continue;
            
            /* Get symbol name */
            if (sym->st_name == 0) continue;
            const char* sym_name = g_current_strtab + sym->st_name;
            
            if (strcmp(sym_name, name) == 0) {
                /* Found in current ELF */
                if (g_current_elf) {
                    return (void*)(sym->st_value + g_current_elf->delta);
                } else {
                    return (void*)sym->st_value;
                }
            }
        }
    }
    
    /* Step 4: Try bionic's default resolution */
    result = dlsym(RTLD_DEFAULT, name);
    if (result) {
        return result;
    }
    
    /* Not found */
    return NULL;
}

