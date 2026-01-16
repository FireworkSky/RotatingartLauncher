//
// Vulkan driver linker hook for Turnip
// Based on zomdroid's implementation
//
#include <android/dlext.h>
#include <android/log.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <dlfcn.h>

#define LOG_TAG "TurnipLinkerHook"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Real loader functions from libdl.so
static void* (*loader_dlopen)(const char* filename, int flags, const void* caller);
static void* (*loader_dlsym)(void* handle, const char* symbol, const void* caller);
static void* (*loader_android_dlopen_ext)(const char* filename,
                                          int flag,
                                          const android_dlextinfo* extinfo,
                                          const void* caller_addr);

// Pre-loaded handles
static void* vulkan_driver_handle = NULL;   // Turnip driver
static void* vulkan_loader_handle = NULL;   // SONAME-patched libvulkan.so

static const char* sphal_namespaces[] = {"sphal", "vendor", "default"};
static const int sphal_ns_count = sizeof(sphal_namespaces) / sizeof(char*);

// Initialize function pointers from libdl.so
__attribute__((visibility("default"), used))
void turnip_linker_set_proc_addrs(void* _loader_dlopen_fn, 
                                   void* _loader_dlsym_fn,
                                   void* _loader_android_dlopen_ext_fn) {
    loader_dlopen = _loader_dlopen_fn;
    loader_dlsym = _loader_dlsym_fn;
    loader_android_dlopen_ext = _loader_android_dlopen_ext_fn;
    LOGI("Linker hook proc addrs set: dlopen=%p dlsym=%p android_dlopen_ext=%p",
         loader_dlopen, loader_dlsym, loader_android_dlopen_ext);
}

// Set the pre-loaded Turnip driver handle
__attribute__((visibility("default"), used))
void turnip_linker_set_vulkan_driver_handle(void* handle) {
    vulkan_driver_handle = handle;
    LOGI("Vulkan driver handle set: %p (Turnip)", handle);
}

// Set the pre-loaded libvulkan.so handle (SONAME-patched)
__attribute__((visibility("default"), used))
void turnip_linker_set_vulkan_loader_handle(void* handle) {
    vulkan_loader_handle = handle;
    LOGI("Vulkan loader handle set: %p (patched libvulkan.so)", handle);
}

// Initialize the linker hook (currently just validation)
__attribute__((visibility("default"), used))
int turnip_linker_init() {
    if (!loader_dlopen || !loader_dlsym || !loader_android_dlopen_ext) {
        LOGE("Linker hook not properly initialized - missing function pointers");
        return -1;
    }
    LOGI("Linker hook initialized successfully");
    return 0;
}

// Hook dlopen: intercept libvulkan.so requests
__attribute__((visibility("default"), used))
void* dlopen(const char* filename, int flags) {
    LOGD("dlopen(filename=%s, flags=0x%x)", filename ? filename : "NULL", flags);
    
    if (filename == NULL) {
        return loader_dlopen(NULL, flags, __builtin_return_address(0));
    }
    
    // When someone requests libvulkan.so, return our pre-loaded patched version
    if (strcmp(filename, "libvulkan.so") == 0 && vulkan_loader_handle) {
        LOGI("dlopen: Intercepting libvulkan.so -> returning patched loader handle %p", vulkan_loader_handle);
        return vulkan_loader_handle;
    }
    
    return loader_dlopen(filename, flags, __builtin_return_address(0));
}

__attribute__((visibility("default"), used))
void* dlsym(void* handle, const char* sym_name) {

    return loader_dlsym(handle, sym_name, __builtin_return_address(0));
}

// Hook android_dlopen_ext: intercept vulkan driver loading
// This is called by libvulkan.so when it tries to load the actual GPU driver
__attribute__((visibility("default"), used))
void* android_dlopen_ext(const char* filename, int flags, const android_dlextinfo* extinfo) {
    LOGD("android_dlopen_ext(filename=%s, flags=0x%x)", filename ? filename : "NULL", flags);
    
    // When libvulkan.so tries to load vulkan.xxx.so (the actual driver), return Turnip
    if (filename && strstr(filename, "vulkan.") && vulkan_driver_handle) {
        LOGI("android_dlopen_ext: Intercepting vulkan driver '%s' -> returning Turnip handle %p", 
             filename, vulkan_driver_handle);
        return vulkan_driver_handle;
    }
    
    return loader_android_dlopen_ext(filename, flags, extinfo, &android_dlopen_ext);
}

// Hook android_load_sphal_library: HAL layer loading (another path for driver loading)
__attribute__((visibility("default"), used))
void* android_load_sphal_library(const char* filename, int flags) {
    LOGD("android_load_sphal_library(filename=%s, flags=0x%x)", filename ? filename : "NULL", flags);
    
    // Intercept vulkan driver loading here too
    if (filename && strstr(filename, "vulkan.") && vulkan_driver_handle) {
        LOGI("android_load_sphal_library: Intercepting vulkan driver '%s' -> returning Turnip handle %p",
             filename, vulkan_driver_handle);
        return vulkan_driver_handle;
    }
    
    // For other libraries, load from sphal namespace
    struct android_namespace_t* sphal_ns = NULL;
    
    // android_get_exported_namespace is not directly available, we need to work around
    // Try to load from various namespaces
    android_dlextinfo info;
    info.flags = ANDROID_DLEXT_USE_NAMESPACE;
    
    // Just pass through to regular android_dlopen_ext for non-vulkan libraries
    return loader_android_dlopen_ext(filename, flags, NULL, &android_dlopen_ext);
}

// Stub for atrace (required by some libraries)
__attribute__((visibility("default"), used))
uint64_t atrace_get_enabled_tags() {
    return 0;
}
