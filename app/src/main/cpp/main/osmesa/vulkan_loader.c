//
// Vulkan loader for zink
//
#include "vulkan_loader.h"
#include "../vulkan_turnip_loader.h"
#include <dlfcn.h>
#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>
#include <android/log.h>

#define LOG_TAG "VulkanLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void* g_vulkan_handle = NULL;

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

bool vulkan_loader_load(void) {
    if (g_vulkan_handle != NULL) {
        LOGI("Vulkan already loaded: %p", g_vulkan_handle);
        return true;
    }

    // Check if already loaded via environment variable
    const char* vulkan_ptr_env = getenv("VULKAN_PTR");
    if (vulkan_ptr_env != NULL) {
        g_vulkan_handle = (void*)strtoull(vulkan_ptr_env, NULL, 16);
        if (g_vulkan_handle != NULL) {
            LOGI("Vulkan already loaded via VULKAN_PTR: %p", g_vulkan_handle);
            return true;
        }
    }

    // 先尝试加载 Turnip 驱动（如果启用）
    if (vulkan_turnip_loader_load()) {
        LOGI("Turnip driver loaded, using it as Vulkan driver");
        // Turnip 已经设置了 VULKAN_PTR，检查是否已设置
        const char* vulkan_ptr_env = getenv("VULKAN_PTR");
        if (vulkan_ptr_env != NULL) {
            g_vulkan_handle = (void*)strtoull(vulkan_ptr_env, NULL, 16);
            if (g_vulkan_handle != NULL) {
                LOGI("Using Turnip driver handle from VULKAN_PTR: %p", g_vulkan_handle);
                return true;
            }
        }
    }

    LOGI("Loading Vulkan library (libvulkan.so)...");
    
    // Load Vulkan library
    g_vulkan_handle = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    if (g_vulkan_handle == NULL) {
        const char* error = dlerror();
        LOGE("Failed to load libvulkan.so: %s", error ? error : "unknown error");
        return false;
    }

    LOGI("Vulkan library loaded successfully: %p", g_vulkan_handle);
    
    // Store pointer in environment variable
    set_vulkan_ptr(g_vulkan_handle);
    
    return true;
}

void* vulkan_loader_get_handle(void) {
    if (g_vulkan_handle != NULL) {
        return g_vulkan_handle;
    }
    
    // Try to get from environment variable
    const char* vulkan_ptr_env = getenv("VULKAN_PTR");
    if (vulkan_ptr_env != NULL) {
        g_vulkan_handle = (void*)strtoull(vulkan_ptr_env, NULL, 16);
        return g_vulkan_handle;
    }
    
    return NULL;
}

bool vulkan_loader_is_loaded(void) {
    return vulkan_loader_get_handle() != NULL;
}

