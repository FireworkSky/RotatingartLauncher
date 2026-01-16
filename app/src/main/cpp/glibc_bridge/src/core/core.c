#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <fcntl.h>

/*
 * glibc-bridge 核心实现
 * 
 * glibc-bridge 运行时的主 API 实现
 */

#include "../include/api.h"
#include "../include/private.h"

/* Global log level */
int g_glibc_bridge_log_level = GLIBC_BRIDGE_LOG_LVL_INFO;

/* ============================================================================
 * Version and Error Strings
 * ============================================================================ */

const char* glibc_bridge_version(void) {
    return GLIBC_BRIDGE_VERSION_STRING;
}

const char* glibc_bridge_strerror(glibc_bridge_error_t error) {
    switch (error) {
        case GLIBC_BRIDGE_OK:                  return "Success";
        case GLIBC_BRIDGE_ERROR_INVALID_ARG:   return "Invalid argument";
        case GLIBC_BRIDGE_ERROR_FILE_NOT_FOUND: return "File not found";
        case GLIBC_BRIDGE_ERROR_INVALID_ELF:   return "Invalid ELF file";
        case GLIBC_BRIDGE_ERROR_LOAD_FAILED:   return "Failed to load ELF";
        case GLIBC_BRIDGE_ERROR_EXEC_FAILED:   return "Failed to execute";
        case GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY: return "Out of memory";
        case GLIBC_BRIDGE_ERROR_NOT_SUPPORTED: return "Not supported";
        case GLIBC_BRIDGE_ERROR_FORK_FAILED:   return "Fork failed";
        default:
            if (error <= GLIBC_BRIDGE_ERROR_SIGNAL && error > GLIBC_BRIDGE_ERROR_SIGNAL - 64) {
                static char buf[64];
                snprintf(buf, sizeof(buf), "Killed by signal %d", -(error - GLIBC_BRIDGE_ERROR_SIGNAL));
                return buf;
            }
            return "Unknown error";
    }
}

/* ============================================================================
 * Initialization and Cleanup
 * ============================================================================ */

glibc_bridge_t glibc_bridge_init(const glibc_bridge_config_t* config) {
    glibc_bridge_t bta = (glibc_bridge_t)calloc(1, sizeof(struct glibc_bridge_s));
    if (!bta) {
        LOG_ERROR("Failed to allocate glibc-bridge context");
        return NULL;
    }
    
    /* Apply configuration */
    if (config) {
        bta->config = *config;
    } else {
        glibc_bridge_config_t default_config = GLIBC_BRIDGE_CONFIG_DEFAULT;
        bta->config = default_config;
    }
    
    g_glibc_bridge_log_level = bta->config.log_level;
    
    /* Initialize ELF array */
    bta->elf_capacity = 4;
    bta->elfs = (elfheader_t**)calloc(bta->elf_capacity, sizeof(elfheader_t*));
    if (!bta->elfs) {
        free(bta);
        LOG_ERROR("Failed to allocate ELF array");
        return NULL;
    }
    
    
    LOG_INFO("glibc-bridge initialized (version %s)", GLIBC_BRIDGE_VERSION_STRING);
    return bta;
}

void glibc_bridge_cleanup(glibc_bridge_t bta) {
    if (!bta) return;
    
    /* Free all loaded ELFs */
    for (int i = 0; i < bta->elf_count; i++) {
        if (bta->elfs[i]) {
            elf_free(bta->elfs[i]);
        }
    }
    free(bta->elfs);
    
    /* Free stack */
    if (bta->stack) {
        free_stack(bta->stack, bta->stack_size);
    }
    
    /* Free TLS */
    if (bta->tls) {
        free_glibc_tls(bta->tls);
    }
    
    /* Free output buffers */
    free(bta->stdout_buf);
    free(bta->stderr_buf);
    
    
    free(bta);
    LOG_INFO("glibc-bridge cleanup complete");
}

void glibc_bridge_set_log_level(glibc_bridge_t bta, glibc_bridge_log_level_t level) {
    if (bta) {
        bta->config.log_level = level;
    }
    g_glibc_bridge_log_level = level;
}

void glibc_bridge_set_lib_path(glibc_bridge_t bta, const char* lib_path) {
    if (bta) {
        bta->config.lib_path = lib_path;
    }
}

/* ============================================================================
 * ELF Loading
 * ============================================================================ */

glibc_bridge_elf_t glibc_bridge_load(glibc_bridge_t bta, const char* path) {
    if (!bta || !path) {
        LOG_ERROR("Invalid arguments to glibc_bridge_load");
        return NULL;
    }
    
    /* Check file exists */
    if (access(path, R_OK) != 0) {
        LOG_ERROR("Cannot access file: %s", path);
        return NULL;
    }
    
    LOG_INFO("Loading ELF: %s", path);
    
    /* Parse ELF header */
    elfheader_t* elf = elf_parse_header(path);
    if (!elf) {
        LOG_ERROR("Failed to parse ELF header: %s", path);
        return NULL;
    }
    
    /* Load into memory */
    if (elf_load_memory(elf) != 0) {
        LOG_ERROR("Failed to load ELF into memory: %s", path);
        elf_free(elf);
        return NULL;
    }
    
    /* Relocate */
    if (elf_relocate(elf) != 0) {
        LOG_ERROR("Failed to relocate ELF: %s", path);
        elf_free(elf);
        return NULL;
    }
    
    /* Add to context */
    if (bta->elf_count >= bta->elf_capacity) {
        int new_cap = bta->elf_capacity * 2;
        elfheader_t** new_elfs = realloc(bta->elfs, new_cap * sizeof(elfheader_t*));
        if (!new_elfs) {
            LOG_ERROR("Failed to expand ELF array");
            elf_free(elf);
            return NULL;
        }
        bta->elfs = new_elfs;
        bta->elf_capacity = new_cap;
    }
    bta->elfs[bta->elf_count++] = elf;
    
    /* Create handle */
    glibc_bridge_elf_t handle = (glibc_bridge_elf_t)calloc(1, sizeof(struct glibc_bridge_elf_s));
    if (!handle) {
        LOG_ERROR("Failed to allocate ELF handle");
        return NULL;
    }
    handle->bta = bta;
    handle->elf = elf;
    handle->loaded = 1;
    
    LOG_INFO("ELF loaded successfully: %s", path);
    LOG_INFO("  Entry point: 0x%lx", (unsigned long)(elf->entrypoint + elf->delta));
    LOG_INFO("  Load address: 0x%lx", (unsigned long)elf->delta);
    LOG_INFO("  Memory size: 0x%lx", (unsigned long)elf->memsz);
    
    return handle;
}

glibc_bridge_error_t glibc_bridge_elf_info(glibc_bridge_elf_t handle, glibc_bridge_elf_info_t* info) {
    if (!handle || !info || !handle->elf) {
        return GLIBC_BRIDGE_ERROR_INVALID_ARG;
    }
    
    elfheader_t* elf = handle->elf;
    
    info->path = elf->path;
    info->is_64bit = (elf->ehdr.e_ident[EI_CLASS] == ELFCLASS64);
    info->is_arm64 = (elf->ehdr.e_machine == EM_AARCH64);
    info->is_static = elf->is_static;
    info->is_pie = elf->is_pie;
    info->entry_point = elf->entrypoint + elf->delta;
    info->load_addr = elf->delta;
    info->memory_size = elf->memsz;
    
    return GLIBC_BRIDGE_OK;
}

void glibc_bridge_unload(glibc_bridge_elf_t handle) {
    if (!handle) return;
    
    /* Note: ELF is freed when glibc_bridge_cleanup is called */
    /* Here we just mark it as unloaded */
    handle->loaded = 0;
    free(handle);
}

/* ============================================================================
 * Execution
 * ============================================================================ */

int glibc_bridge_run(glibc_bridge_t bta, glibc_bridge_elf_t handle, 
              int argc, char** argv, char** envp,
              glibc_bridge_result_t* result) {
    if (!bta || !handle || !handle->elf) {
        return GLIBC_BRIDGE_ERROR_INVALID_ARG;
    }
    
    elfheader_t* elf = handle->elf;
    
    /* Build argv if not provided */
    char* default_argv[2] = { (char*)elf->path, NULL };
    if (argc == 0 || argv == NULL) {
        argc = 1;
        argv = default_argv;
    }
    
    /* Use environ if envp not provided */
    if (!envp) {
        extern char** environ;
        envp = environ;
    }
    
    LOG_INFO("Running ELF: %s (argc=%d)", elf->path, argc);
    
    /* Choose execution mode:
     * - direct_execution: Run in current process (required for JNI compatibility)
     * - forked: Run in child process (isolates crashes but breaks JNI)
     */
    if (bta->config.direct_execution) {
        LOG_INFO("Using direct execution mode (JNI compatible)");
        return run_elf_direct(bta, elf, argc, argv, envp, result);
    } else {
        LOG_INFO("Using forked execution mode");
        return run_elf_forked(bta, elf, argc, argv, envp, result);
    }
}

int glibc_bridge_exec(glibc_bridge_t bta, const char* path,
               int argc, char** argv, char** envp,
               glibc_bridge_result_t* result) {
    if (!bta || !path) {
        return GLIBC_BRIDGE_ERROR_INVALID_ARG;
    }
    
    /* Load ELF */
    glibc_bridge_elf_t elf = glibc_bridge_load(bta, path);
    if (!elf) {
        return GLIBC_BRIDGE_ERROR_LOAD_FAILED;
    }
    
    /* Run */
    int ret = glibc_bridge_run(bta, elf, argc, argv, envp, result);
    
    /* Unload */
    glibc_bridge_unload(elf);
    
    return ret;
}

void glibc_bridge_result_free(glibc_bridge_result_t* result) {
    if (!result) return;
    free(result->stdout_buf);
    free(result->stderr_buf);
    result->stdout_buf = NULL;
    result->stderr_buf = NULL;
    result->stdout_len = 0;
    result->stderr_len = 0;
}

/* ============================================================================
 * Global Runtime State
 * ============================================================================ */

static glibc_bridge_t g_global_bridge = NULL;
static int g_global_initialized = 0;

/* External: glibc root from glibc_bridge_sharedlib.c */
extern char g_glibc_root[512];

/* ============================================================================
 * Internal Initialization
 * ============================================================================ */

/**
 * Initialize glibc-bridge runtime (internal, called automatically)
 */
static int glibc_bridge_ensure_initialized(const char* rootfs_path) {
    if (g_global_initialized && g_global_bridge) {
        return 0;
    }
    
    glibc_bridge_config_t config = GLIBC_BRIDGE_CONFIG_DEFAULT;
    config.log_level = GLIBC_BRIDGE_LOG_INFO;
    config.redirect_output = 1;
    config.use_tls = 1;
    config.stack_size = 8 * 1024 * 1024;  // 8MB
    
    if (rootfs_path) {
        config.lib_path = rootfs_path;
        /* Also set global root for shared library loading */
        strncpy(g_glibc_root, rootfs_path, sizeof(g_glibc_root) - 1);
        g_glibc_root[sizeof(g_glibc_root) - 1] = '\0';
    }
    
    g_global_bridge = glibc_bridge_init(&config);
    if (!g_global_bridge) {
        LOG_ERROR("Failed to initialize glibc-bridge runtime");
        return -1;
    }
    
    g_global_initialized = 1;
    LOG_INFO("glibc-bridge runtime initialized");
    return 0;
}

/* ============================================================================
 * Main Entry Point
 * ============================================================================ */

/**
 * Execute glibc program - Main entry point
 * Automatically initializes if not already initialized
 */
int glibc_bridge_execute(const char* path, int argc, char** argv, char** envp, const char* rootfs_path) {
    if (!path) {
        return GLIBC_BRIDGE_ERROR_INVALID_ARG;
    }

    /* Ensure runtime is initialized */
    if (glibc_bridge_ensure_initialized(rootfs_path) != 0) {
        return GLIBC_BRIDGE_ERROR_OUT_OF_MEMORY;
    }

    /* Set LD_LIBRARY_PATH if rootfs_path provided */
    if (rootfs_path) {
        char ld_path[1024];
        snprintf(ld_path, sizeof(ld_path), "%s/lib:%s/lib/aarch64-linux-gnu",
                 rootfs_path, rootfs_path);
        setenv("LD_LIBRARY_PATH", ld_path, 1);
    }

    /* IMPORTANT: Set all envp variables to process environment
     * box64 uses getenv() to read BOX64_* variables, not the stack envp
     * So we must call setenv() for each variable before execution */
    if (envp) {
        for (int i = 0; envp[i] != NULL; i++) {
            char* eq = strchr(envp[i], '=');
            if (eq) {
                /* Temporarily null-terminate at '=' to get variable name */
                *eq = '\0';
                const char* name = envp[i];
                const char* value = eq + 1;
                setenv(name, value, 1);  /* overwrite=1 */
                LOG_DEBUG("setenv: %s=%s", name, value);
                *eq = '=';  /* Restore the string */
            }
        }
    }

    /* Execute program */
    glibc_bridge_result_t result = {0};
    int ret = glibc_bridge_exec(g_global_bridge, path, argc, argv, envp, &result);
    
    if (result.exited) {
        ret = result.exit_code;
    }
    
    glibc_bridge_result_free(&result);
    return ret;
}

/* ============================================================================
 * Utility Functions
 * ============================================================================ */

int glibc_bridge_is_valid_elf(const char* path) {
    if (!path) return 0;
    
    FILE* f = fopen(path, "rb");
    if (!f) return 0;
    
    unsigned char magic[16];
    if (fread(magic, 1, 16, f) != 16) {
        fclose(f);
        return 0;
    }
    fclose(f);
    
    /* Check ELF magic */
    if (magic[0] != 0x7f || magic[1] != 'E' || magic[2] != 'L' || magic[3] != 'F') {
        return 0;
    }
    
    return 1;
}

