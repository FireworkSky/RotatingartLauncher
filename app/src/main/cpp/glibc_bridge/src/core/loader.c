/*
 * glibc-bridge ELF 加载器
 * 
 * ELF 解析、加载和重定位
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <elf.h>

#include "../include/private.h"

/* ============================================================================
 * Memory Allocation
 * ============================================================================ */

void* alloc_exec_memory(size_t size, uintptr_t hint) {
    size = (size + 4095) & ~4095;  /* Page align */
    
    void* ptr = mmap((void*)hint, size,
                     PROT_READ | PROT_WRITE | PROT_EXEC,
                     MAP_PRIVATE | MAP_ANONYMOUS | (hint ? MAP_FIXED_NOREPLACE : 0),
                     -1, 0);
    
    if (ptr == MAP_FAILED) {
        /* Try without hint */
        if (hint) {
            ptr = mmap(NULL, size,
                       PROT_READ | PROT_WRITE | PROT_EXEC,
                       MAP_PRIVATE | MAP_ANONYMOUS,
                       -1, 0);
        }
    }
    
    return (ptr == MAP_FAILED) ? NULL : ptr;
}

void free_memory(void* ptr, size_t size) {
    if (ptr && ptr != MAP_FAILED) {
        munmap(ptr, size);
    }
}

void* alloc_stack(size_t size) {
    /* Allocate stack with guard page at the bottom */
    size_t page_size = 4096;
    size_t total_size = size + page_size;  /* Extra page for guard */
    
    void* stack = mmap(NULL, total_size,
                       PROT_READ | PROT_WRITE,
                       MAP_PRIVATE | MAP_ANONYMOUS | MAP_STACK,
                       -1, 0);
    if (stack == MAP_FAILED) return NULL;
    
    /* Make the first page a guard page (no access) */
    mprotect(stack, page_size, PROT_NONE);
    
    /* Return pointer after guard page */
    return (char*)stack + page_size;
}

void free_stack(void* stack, size_t size) {
    if (stack && stack != MAP_FAILED) {
        /* Stack was allocated with guard page before it */
        size_t page_size = 4096;
        void* real_base = (char*)stack - page_size;
        munmap(real_base, size + page_size);
    }
}

/* ============================================================================
 * ELF Parsing
 * ============================================================================ */

elfheader_t* elf_parse_header(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) {
        LOG_ERROR("Cannot open file: %s (%s)", path, strerror(errno));
        return NULL;
    }
    
    elfheader_t* elf = (elfheader_t*)calloc(1, sizeof(elfheader_t));
    if (!elf) {
        fclose(f);
        return NULL;
    }
    
    elf->path = strdup(path);
    
    /* Read ELF header */
    if (fread(&elf->ehdr, sizeof(Elf64_Ehdr), 1, f) != 1) {
        LOG_ERROR("Failed to read ELF header");
        goto error;
    }
    
    /* Validate magic */
    if (memcmp(elf->ehdr.e_ident, ELFMAG, SELFMAG) != 0) {
        LOG_ERROR("Invalid ELF magic");
        goto error;
    }
    
    /* Check 64-bit */
    if (elf->ehdr.e_ident[EI_CLASS] != ELFCLASS64) {
        LOG_ERROR("Not a 64-bit ELF");
        goto error;
    }
    
    /* Check ARM64 */
    if (elf->ehdr.e_machine != EM_AARCH64) {
        LOG_ERROR("Not an ARM64 ELF (e_machine=%d)", elf->ehdr.e_machine);
        goto error;
    }
    
    /* Check executable or shared object */
    if (elf->ehdr.e_type != ET_EXEC && elf->ehdr.e_type != ET_DYN) {
        LOG_ERROR("Not an executable (e_type=%d)", elf->ehdr.e_type);
        goto error;
    }
    
    elf->is_pie = (elf->ehdr.e_type == ET_DYN);
    
    /* Read program headers */
    elf->phnum = elf->ehdr.e_phnum;
    elf->phdr = (Elf64_Phdr*)calloc(elf->phnum, sizeof(Elf64_Phdr));
    if (!elf->phdr) {
        LOG_ERROR("Failed to allocate program headers");
        goto error;
    }
    
    fseek(f, elf->ehdr.e_phoff, SEEK_SET);
    if (fread(elf->phdr, sizeof(Elf64_Phdr), elf->phnum, f) != (size_t)elf->phnum) {
        LOG_ERROR("Failed to read program headers");
        goto error;
    }
    
    /* Get entry point */
    elf->entrypoint = elf->ehdr.e_entry;
    
    /* Check for interpreter (dynamic linking) and TLS */
    elf->is_static = 1;
    elf->interp = NULL;
    
    for (int i = 0; i < elf->phnum; i++) {
        if (elf->phdr[i].p_type == PT_INTERP) {
            elf->is_static = 0;
            /* Read interpreter path */
            size_t interp_len = elf->phdr[i].p_filesz;
            elf->interp = (char*)malloc(interp_len + 1);
            if (elf->interp) {
                fseek(f, elf->phdr[i].p_offset, SEEK_SET);
                if (fread(elf->interp, 1, interp_len, f) == interp_len) {
                    elf->interp[interp_len] = '\0';
                    LOG_DEBUG("  Interpreter: %s", elf->interp);
                } else {
                    free(elf->interp);
                    elf->interp = NULL;
                }
            }
        }
        if (elf->phdr[i].p_type == PT_TLS) {
            elf->tlssize = elf->phdr[i].p_memsz;
            elf->tlsalign = elf->phdr[i].p_align;
        }
    }
    
    fclose(f);
    
    LOG_DEBUG("ELF parsed: %s", path);
    LOG_DEBUG("  Type: %s %s", 
              elf->is_pie ? "PIE" : "EXEC",
              elf->is_static ? "(static)" : "(dynamic)");
    LOG_DEBUG("  Entry: 0x%lx", (unsigned long)elf->entrypoint);
    LOG_DEBUG("  PHnum: %d", elf->phnum);
    
    return elf;
    
error:
    fclose(f);
    elf_free(elf);
    return NULL;
}

/* ============================================================================
 * ELF Loading
 * ============================================================================ */

int elf_load_memory(elfheader_t* elf) {
    if (!elf || !elf->phdr) {
        return -1;
    }
    
    /* Calculate total memory range */
    uintptr_t min_addr = UINTPTR_MAX;
    uintptr_t max_addr = 0;
    
    for (int i = 0; i < elf->phnum; i++) {
        Elf64_Phdr* ph = &elf->phdr[i];
        if (ph->p_type != PT_LOAD) continue;
        
        uintptr_t start = ph->p_vaddr;
        uintptr_t end = start + ph->p_memsz;
        
        if (start < min_addr) min_addr = start;
        if (end > max_addr) max_addr = end;
    }
    
    if (min_addr >= max_addr) {
        LOG_ERROR("No loadable segments");
        return -1;
    }
    
    /* Page align */
    min_addr &= ~0xFFF;
    max_addr = (max_addr + 0xFFF) & ~0xFFF;
    
    size_t total_size = max_addr - min_addr;
    elf->memsz = total_size;
    
    LOG_DEBUG("Memory range: 0x%lx - 0x%lx (size: 0x%lx)",
              (unsigned long)min_addr, (unsigned long)max_addr, 
              (unsigned long)total_size);
    
    /* Allocate memory */
    uintptr_t hint = elf->is_pie ? 0 : min_addr;
    void* base = alloc_exec_memory(total_size, hint);
    if (!base) {
        LOG_ERROR("Failed to allocate memory for ELF");
        return -1;
    }
    
    elf->image = base;
    elf->delta = (uintptr_t)base - min_addr;
    
    LOG_DEBUG("Loaded at: %p (delta: 0x%lx)", base, (unsigned long)elf->delta);
    
    /* Open file for loading */
    FILE* f = fopen(elf->path, "rb");
    if (!f) {
        free_memory(base, total_size);
        return -1;
    }
    
    /* Load segments */
    for (int i = 0; i < elf->phnum; i++) {
        Elf64_Phdr* ph = &elf->phdr[i];
        if (ph->p_type != PT_LOAD) continue;
        
        uintptr_t dest = ph->p_vaddr + elf->delta;
        
        /* Clear memory */
        memset((void*)dest, 0, ph->p_memsz);
        
        /* Load from file */
        if (ph->p_filesz > 0) {
            fseek(f, ph->p_offset, SEEK_SET);
            if (fread((void*)dest, 1, ph->p_filesz, f) != ph->p_filesz) {
                LOG_ERROR("Failed to load segment %d", i);
                fclose(f);
                return -1;
            }
        }
        
        LOG_DEBUG("Loaded segment %d: 0x%lx (size: 0x%lx)", 
                  i, (unsigned long)dest, (unsigned long)ph->p_memsz);
    }
    
    fclose(f);
    return 0;
}

/* ============================================================================
 * ELF Relocation
 * ============================================================================ */

int elf_relocate(elfheader_t* elf) {
    if (!elf || !elf->image) {
        return -1;
    }
    
    /* For static executables, relocation is simpler */
    if (elf->is_static && elf->delta == 0) {
        LOG_DEBUG("Static executable at fixed address, no relocation needed");
        return 0;
    }
    
    /* Find dynamic section */
    Elf64_Dyn* dyn = NULL;
    size_t dyn_count = 0;
    
    for (int i = 0; i < elf->phnum; i++) {
        if (elf->phdr[i].p_type == PT_DYNAMIC) {
            dyn = (Elf64_Dyn*)(elf->phdr[i].p_vaddr + elf->delta);
            dyn_count = elf->phdr[i].p_memsz / sizeof(Elf64_Dyn);
            break;
        }
    }
    
    if (!dyn) {
        LOG_DEBUG("No dynamic section (static executable)");
        return 0;
    }
    
    /* Parse dynamic entries */
    Elf64_Rela* rela = NULL;
    size_t relasz = 0;
    Elf64_Rela* pltrel = NULL;
    size_t pltrelsz = 0;
    
    for (size_t i = 0; i < dyn_count && dyn[i].d_tag != DT_NULL; i++) {
        switch (dyn[i].d_tag) {
            case DT_RELA:
                rela = (Elf64_Rela*)(dyn[i].d_un.d_ptr + elf->delta);
                break;
            case DT_RELASZ:
                relasz = dyn[i].d_un.d_val;
                break;
            case DT_JMPREL:
                pltrel = (Elf64_Rela*)(dyn[i].d_un.d_ptr + elf->delta);
                break;
            case DT_PLTRELSZ:
                pltrelsz = dyn[i].d_un.d_val;
                break;
        }
    }
    
    /* Apply RELA relocations */
    if (rela && relasz > 0) {
        size_t count = relasz / sizeof(Elf64_Rela);
        LOG_DEBUG("Applying %zu RELA relocations", count);
        
        for (size_t i = 0; i < count; i++) {
            uintptr_t* target = (uintptr_t*)(rela[i].r_offset + elf->delta);
            int type = ELF64_R_TYPE(rela[i].r_info);
            
            switch (type) {
                case R_AARCH64_RELATIVE:
                    *target = elf->delta + rela[i].r_addend;
                    break;
                /* Add more relocation types as needed */
            }
        }
    }
    
    /* Apply PLT relocations */
    if (pltrel && pltrelsz > 0) {
        size_t count = pltrelsz / sizeof(Elf64_Rela);
        LOG_DEBUG("Applying %zu PLT relocations", count);
        
        for (size_t i = 0; i < count; i++) {
            uintptr_t* target = (uintptr_t*)(pltrel[i].r_offset + elf->delta);
            int type = ELF64_R_TYPE(pltrel[i].r_info);
            
            switch (type) {
                case R_AARCH64_RELATIVE:
                    *target = elf->delta + pltrel[i].r_addend;
                    break;
                case R_AARCH64_JUMP_SLOT:
                    /* For static executables, these should already be resolved */
                    if (elf->delta != 0) {
                        *target += elf->delta;
                    }
                    break;
            }
        }
    }
    
    return 0;
}

/* ============================================================================
 * Cleanup
 * ============================================================================ */

void elf_free(elfheader_t* elf) {
    if (!elf) return;
    
    if (elf->image) {
        free_memory(elf->image, elf->memsz);
    }
    free(elf->phdr);
    free(elf->path);
    free(elf->tlsdata);
    free(elf->interp);
    free(elf);
}

