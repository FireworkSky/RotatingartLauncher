

#include "vulkan_turnip_loader.h"
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <unistd.h>
#include <inttypes.h>

#define LOG_TAG "TurnipLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static void* g_turnip_handle = NULL;

static void set_vulkan_ptr(void* ptr) {
    if (ptr == NULL) {
        unsetenv("VULKAN_PTR");
        return;
    }
    char envval[64];
    snprintf(envval, sizeof(envval), "%" PRIxPTR, (uintptr_t)ptr);
    setenv("VULKAN_PTR", envval, 1);
    LOGI("VULKAN_PTR set to: %s (handle: %p)", envval, ptr);
}

/**
 * @brief 加载 Turnip Vulkan 驱动
 * 
 * 直接从应用的 native 库目录加载 libvulkan_freedreno.so
 * 不需要 liblinkerhook.so 或 linker namespace bypass
 */
bool vulkan_turnip_loader_load(void) {
    // 检查环境变量
    const char* load_turnip = getenv("RALCORE_LOAD_TURNIP");
    if (load_turnip == NULL || strcmp(load_turnip, "1") != 0) {
        LOGI("RALCORE_LOAD_TURNIP not set or disabled, skipping Turnip loading");
        return false;
    }

    if (g_turnip_handle != NULL) {
        LOGI("Turnip driver already loaded: %p", g_turnip_handle);
        return true;
    }

    LOGI("========================================");
    LOGI("  Attempting to load Turnip Vulkan driver");
    LOGI("========================================");

    // 获取 native 库目录
    const char* native_dir = getenv("RALCORE_NATIVEDIR");
    if (native_dir == NULL) {
        native_dir = getenv("ANDROID_APP_NATIVE_LIB_DIR");
    }
    LOGI("Native lib directory: %s", native_dir ? native_dir : "(not set)");

    // 尝试从多个路径加载 Turnip 驱动
    const char* lib_names[] = {
        "libvulkan_freedreno.so",  // 直接名称（如果在 LD_LIBRARY_PATH 中）
        NULL
    };
    
    // 构建完整路径
    char full_path[512];
    
    for (int i = 0; lib_names[i] != NULL; i++) {
        // 首先尝试直接加载（系统路径）
        LOGI("Trying to load: %s", lib_names[i]);
        g_turnip_handle = dlopen(lib_names[i], RTLD_NOW | RTLD_LOCAL);
        
        if (g_turnip_handle != NULL) {
            LOGI("✓ Turnip driver loaded from system path: %p", g_turnip_handle);
            set_vulkan_ptr(g_turnip_handle);
            return true;
        }
        LOGW("  Failed: %s", dlerror());
        
        // 尝试从 native 目录加载
        if (native_dir != NULL) {
            snprintf(full_path, sizeof(full_path), "%s/%s", native_dir, lib_names[i]);
            LOGI("Trying to load: %s", full_path);
            g_turnip_handle = dlopen(full_path, RTLD_NOW | RTLD_LOCAL);
            
            if (g_turnip_handle != NULL) {
                LOGI("✓ Turnip driver loaded from native dir: %p", g_turnip_handle);
                set_vulkan_ptr(g_turnip_handle);
                return true;
            }
            LOGW("  Failed: %s", dlerror());
        }
    }

    LOGE("✗ Failed to load Turnip driver from any path");
    LOGI("  Note: Turnip requires libvulkan_freedreno.so in the app's native library directory");
    
    return false;
}

/**
 * @brief 获取 Turnip 驱动句柄
 */
void* vulkan_turnip_loader_get_handle(void) {
    return g_turnip_handle;
}

/**
 * @brief 检查 Turnip 是否已加载
 */
bool vulkan_turnip_loader_is_loaded(void) {
    return g_turnip_handle != NULL;
}
