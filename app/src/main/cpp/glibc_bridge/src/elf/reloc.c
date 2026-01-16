/*
 * glibc-bridge 动态链接器 - 重定位处理
 * 
 * 使用我们的自定义符号解析器处理 ELF 重定位
 * 处理 AArch64 的 RELA 和 PLT 重定位
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <elf.h>

#include "../include/private.h"
#include "../include/wrappers.h"
#include "log.h"

/* glibc_bridge_resolve_symbol handles all symbol resolution:
 * wrappers -> shared libs -> ELF internal -> bionic */

/* ============================================================================
 * AArch64 Relocation Types
 * ============================================================================ */

#ifndef R_AARCH64_GLOB_DAT
#define R_AARCH64_GLOB_DAT      1025
#endif
#ifndef R_AARCH64_JUMP_SLOT
#define R_AARCH64_JUMP_SLOT     1026
#endif
#ifndef R_AARCH64_RELATIVE
#define R_AARCH64_RELATIVE      1027
#endif
#ifndef R_AARCH64_ABS64
#define R_AARCH64_ABS64         257
#endif

/* ============================================================================
 * Dynamic Linking Relocation
 * 
 * Process relocations using our symbol resolver
 * ============================================================================ */

int glibc_bridge_relocate_dynamic(elfheader_t* elf) {
    char buf[256];
    int loglvl = glibc_bridge_dl_get_log_level();
    
    if (!elf || !elf->image) {
        return -1;
    }
    
    if (loglvl >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        snprintf(buf, sizeof(buf), "[RELOC] image=%p delta=0x%lx\n", 
                 elf->image, (unsigned long)elf->delta);
        write(STDERR_FILENO, buf, strlen(buf));
    }
    
    /* Find dynamic section */
    Elf64_Dyn* dyn = NULL;
    for (int i = 0; i < elf->phnum; i++) {
        if (elf->phdr[i].p_type == PT_DYNAMIC) {
            /* Dynamic section address = p_vaddr + delta */
            dyn = (Elf64_Dyn*)(elf->phdr[i].p_vaddr + elf->delta);
            if (loglvl >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
                snprintf(buf, sizeof(buf), "[RELOC] PT_DYNAMIC at %p\n", dyn);
                write(STDERR_FILENO, buf, strlen(buf));
            }
            break;
        }
    }
    
    if (!dyn) {
        glibc_bridge_dl_log_info("[RELOC] No PT_DYNAMIC - static\n");
        return 0;
    }
    
    /* Parse dynamic section */
    Elf64_Sym* symtab = NULL;
    const char* strtab = NULL;
    Elf64_Rela* rela = NULL;
    Elf64_Rela* pltrel = NULL;
    size_t relasz = 0;
    size_t pltrelsz = 0;
    size_t syment = sizeof(Elf64_Sym);
    uintptr_t strtab_addr = 0;
    uintptr_t symtab_addr = 0;
    
    for (Elf64_Dyn* d = dyn; d->d_tag != DT_NULL; d++) {
        uintptr_t val = d->d_un.d_ptr + elf->delta;
        
        switch (d->d_tag) {
            case DT_SYMTAB:  
                symtab = (Elf64_Sym*)val; 
                symtab_addr = d->d_un.d_ptr;
                break;
            case DT_STRTAB:  
                strtab = (const char*)val; 
                strtab_addr = d->d_un.d_ptr;
                break;
            case DT_SYMENT:  syment = d->d_un.d_val; break;
            case DT_RELA:    rela = (Elf64_Rela*)val; break;
            case DT_RELASZ:  relasz = d->d_un.d_val; break;
            case DT_JMPREL:  pltrel = (Elf64_Rela*)val; break;
            case DT_PLTRELSZ: pltrelsz = d->d_un.d_val; break;
        }
    }
    
    if (loglvl >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        snprintf(buf, sizeof(buf), "[RELOC] symtab=%p strtab=%p\n", symtab, strtab);
        write(STDERR_FILENO, buf, strlen(buf));
        snprintf(buf, sizeof(buf), "[RELOC] rela=%p relasz=%zu\n", rela, relasz);
        write(STDERR_FILENO, buf, strlen(buf));
        snprintf(buf, sizeof(buf), "[RELOC] pltrel=%p pltrelsz=%zu\n", pltrel, pltrelsz);
        write(STDERR_FILENO, buf, strlen(buf));
    }
    
    if (!symtab || !strtab) {
        glibc_bridge_dl_log_warn("[RELOC] Missing symbol or string table\n");
        return 0;
    }
    
    /* Calculate approximate symbol count */
    size_t symcount = 0;
    if (strtab_addr > symtab_addr && syment > 0) {
        symcount = (strtab_addr - symtab_addr) / syment;
    } else {
        /* Fallback: estimate from relocation entries' max symbol index */
        size_t max_idx = 0;
        if (rela && relasz > 0) {
            size_t count = relasz / sizeof(Elf64_Rela);
            for (size_t i = 0; i < count; i++) {
                uint32_t idx = ELF64_R_SYM(rela[i].r_info);
                if (idx > max_idx) max_idx = idx;
            }
        }
        if (pltrel && pltrelsz > 0) {
            size_t count = pltrelsz / sizeof(Elf64_Rela);
            for (size_t i = 0; i < count; i++) {
                uint32_t idx = ELF64_R_SYM(pltrel[i].r_info);
                if (idx > max_idx) max_idx = idx;
            }
        }
        symcount = max_idx + 100; /* Add some buffer */
    }
    
    if (loglvl >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        snprintf(buf, sizeof(buf), "[RELOC] symcount=%zu (estimated)\n", symcount);
        write(STDERR_FILENO, buf, strlen(buf));
    }
    
    /* Set symbol context for internal symbol resolution */
    glibc_bridge_set_symbol_context(elf, symtab, strtab, symcount);
    
    /* Process RELA relocations (.rela.dyn section) */
    if (rela && relasz > 0) {
        size_t count = relasz / sizeof(Elf64_Rela);
        if (loglvl >= GLIBC_BRIDGE_DL_LOG_INFO) {
            snprintf(buf, sizeof(buf), "[RELOC] Processing %zu RELA entries\n", count);
            write(STDERR_FILENO, buf, strlen(buf));
        }
        
        int relative_count = 0;
        int glob_dat_count = 0;
        int abs64_count = 0;
        
        for (size_t i = 0; i < count; i++) {
            Elf64_Rela* r = &rela[i];
            uint32_t type = ELF64_R_TYPE(r->r_info);
            uint32_t sym_idx = ELF64_R_SYM(r->r_info);
            
            uintptr_t* target = (uintptr_t*)(r->r_offset + elf->delta);
            uintptr_t sym_val = 0;
            
            if (sym_idx != 0) {
                /* Safety check: ensure sym_idx is within bounds */
                if (sym_idx >= symcount) {
                    if (loglvl >= GLIBC_BRIDGE_DL_LOG_WARN) {
                        snprintf(buf, sizeof(buf), 
                                 "[RELOC] WARN: sym_idx %u >= symcount %zu, skipping\n", 
                                 sym_idx, symcount);
                        write(STDERR_FILENO, buf, strlen(buf));
                    }
                    continue;
                }
                
                uint32_t name_offset = symtab[sym_idx].st_name;
                
                /* Safety check for strtab offset */
                if (name_offset > 0x100000) {  /* Sanity limit */
                    if (loglvl >= GLIBC_BRIDGE_DL_LOG_WARN) {
                        snprintf(buf, sizeof(buf), 
                                 "[RELOC] WARN: name_offset %u too large, skipping\n", 
                                 name_offset);
                        write(STDERR_FILENO, buf, strlen(buf));
                    }
                    continue;
                }
                
                const char* sym_name = strtab + name_offset;
                
                /* Resolve symbol using unified resolver:
                 * 1) wrappers 2) shared libs (libstdc++ etc.) 3) ELF internal 4) bionic */
                void* resolved = glibc_bridge_resolve_symbol(sym_name);
                
                /* Debug: Log GLOB_DAT symbol resolutions at DEBUG level */
                if (type == R_AARCH64_GLOB_DAT && loglvl >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
                    snprintf(buf, sizeof(buf), 
                             "[GLOB_DAT] [%zu/%zu] %.40s -> %p\n", i, count, sym_name, resolved);
                    write(STDERR_FILENO, buf, strlen(buf));
                }
                
                if (resolved) {
                    sym_val = (uintptr_t)resolved;
                } else if (ELF64_ST_BIND(symtab[sym_idx].st_info) != STB_WEAK) {
                    if (loglvl >= GLIBC_BRIDGE_DL_LOG_ERROR) {
                        snprintf(buf, sizeof(buf), 
                                 "[RELOC] ERROR: Undefined symbol: %.100s\n", sym_name);
                        write(STDERR_FILENO, buf, strlen(buf));
                    }
                    /* Don't fail - try to continue */
                    continue;
                }
            }
            
            switch (type) {
                case R_AARCH64_GLOB_DAT:
                    *target = sym_val + r->r_addend;
                    glob_dat_count++;
                    break;
                case R_AARCH64_JUMP_SLOT:
                    *target = sym_val + r->r_addend;
                    break;
                case R_AARCH64_RELATIVE:
                    *target = elf->delta + r->r_addend;
                    relative_count++;
                    break;
                case R_AARCH64_ABS64:
                    *target = sym_val + r->r_addend;
                    abs64_count++;
                    break;
            }
        }
        
        if (loglvl >= GLIBC_BRIDGE_DL_LOG_INFO) {
            snprintf(buf, sizeof(buf), 
                     "[RELOC] RELA done: %d RELATIVE, %d GLOB_DAT, %d ABS64\n", 
                     relative_count, glob_dat_count, abs64_count);
            write(STDERR_FILENO, buf, strlen(buf));
        }
    }
    
    /* Process PLT relocations (.rela.plt section) */
    if (pltrel && pltrelsz > 0) {
        size_t count = pltrelsz / sizeof(Elf64_Rela);
        if (loglvl >= GLIBC_BRIDGE_DL_LOG_INFO) {
            snprintf(buf, sizeof(buf), "[RELOC] Processing %zu PLT entries\n", count);
            write(STDERR_FILENO, buf, strlen(buf));
        }
        
        for (size_t i = 0; i < count; i++) {
            Elf64_Rela* r = &pltrel[i];
            uint32_t sym_idx = ELF64_R_SYM(r->r_info);
            
            if (sym_idx == 0) continue;
            
            /* Safety check */
            if (sym_idx >= symcount) {
                if (loglvl >= GLIBC_BRIDGE_DL_LOG_WARN) {
                    snprintf(buf, sizeof(buf), 
                             "[RELOC] WARN: PLT sym_idx %u >= symcount %zu, skipping\n", 
                             sym_idx, symcount);
                    write(STDERR_FILENO, buf, strlen(buf));
                }
                continue;
            }
            
            uint32_t name_offset = symtab[sym_idx].st_name;
            
            /* Safety check for strtab offset */
            if (name_offset > 0x100000) {  /* Sanity limit */
                if (loglvl >= GLIBC_BRIDGE_DL_LOG_WARN) {
                    snprintf(buf, sizeof(buf), 
                             "[RELOC] WARN: PLT name_offset %u too large, skipping\n", 
                             name_offset);
                    write(STDERR_FILENO, buf, strlen(buf));
                }
                continue;
            }
            
            const char* sym_name = strtab + name_offset;

            /* Resolve symbol using unified resolver */
            void* resolved = glibc_bridge_resolve_symbol(sym_name);

            /* ALWAYS log dlopen/dlsym resolution for debugging */
            if (strcmp(sym_name, "dlopen") == 0 || strcmp(sym_name, "dlsym") == 0) {
                snprintf(buf, sizeof(buf),
                         "[RELOC] !!! PLT[%s] resolved to %p (dlopen_wrapper=%p)\n",
                         sym_name, resolved, (void*)dlopen_wrapper);
                write(STDERR_FILENO, buf, strlen(buf));
            }

            if (resolved) {
                uintptr_t* target = (uintptr_t*)(r->r_offset + elf->delta);
                *target = (uintptr_t)resolved;
            }
        }
    }
    
    if (loglvl >= GLIBC_BRIDGE_DL_LOG_INFO) {
        write(STDERR_FILENO, "[RELOC] Relocations completed\n", 30);
    }
    
    /* DEBUG level: Verify critical GOT entries */
    if (loglvl >= GLIBC_BRIDGE_DL_LOG_DEBUG) {
        write(STDERR_FILENO, "[DEBUG] Checking GOT entries:\n", 30);
        
        uintptr_t* main_got = (uintptr_t*)(0x10ff0 + elf->delta);
        snprintf(buf, sizeof(buf), "[DEBUG] GOT[main]=%p->0x%lx\n", 
                 (void*)main_got, (unsigned long)*main_got);
        write(STDERR_FILENO, buf, strlen(buf));
        
        uintptr_t* init_got = (uintptr_t*)(0x10fe8 + elf->delta);
        snprintf(buf, sizeof(buf), "[DEBUG] GOT[init]=%p->0x%lx\n",
                 (void*)init_got, (unsigned long)*init_got);
        write(STDERR_FILENO, buf, strlen(buf));
        
        uintptr_t* libc_start_plt = (uintptr_t*)(0x10f88 + elf->delta);
        snprintf(buf, sizeof(buf), 
                 "[DEBUG] PLT[__libc_start_main]=%p->0x%lx (expect %p)\n",
                 (void*)libc_start_plt, (unsigned long)*libc_start_plt,
                 (void*)__libc_start_main_wrapper);
        write(STDERR_FILENO, buf, strlen(buf));
    }
    
    return 0;
}





