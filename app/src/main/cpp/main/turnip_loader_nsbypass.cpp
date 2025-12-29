// Turnip loader using liblinkernsbypass to bypass Android namespace restrictions
// Based on zomdroid's implementation
#include <jni.h>
#include <android/log.h>
#include <android/dlext.h>
#include <dlfcn.h>
#include <string>
#include <cstdlib>
#include <cstdint>
#include <unistd.h>
#include "android_linker_ns.h"
#include "hardware/hwvulkan.h"  // Use our HAL header

#define LOG_TAG "TurnipLoaderNS"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void* g_turnip_handle = nullptr;
static void* g_libvulkan_handle = nullptr;
static PFN_vkGetInstanceProcAddr g_vkGetInstanceProcAddr = nullptr;
static struct android_namespace_t* g_driver_namespace = nullptr;

extern "C" {

static int load_linker_hook(const char* lib_dir, const char* cache_dir) {
    LOGI("Loading linker hook...");
    
    // Step 1: Load libdl.so to get __loader_* functions
    void* libdl = dlopen("libdl.so", RTLD_LAZY);
    if (!libdl) {
        LOGE("Failed to load libdl.so: %s", dlerror());
        return -1;
    }
    
    void* loader_dlopen_fn = dlsym(libdl, "__loader_dlopen");
    void* loader_dlsym_fn = dlsym(libdl, "__loader_dlsym");
    void* loader_android_dlopen_ext_fn = dlsym(libdl, "__loader_android_dlopen_ext");
    
    if (!loader_dlopen_fn || !loader_dlsym_fn || !loader_android_dlopen_ext_fn) {
        LOGE("Failed to get __loader_* functions from libdl.so");
        dlclose(libdl);
        return -1;
    }
    
    LOGI("Got __loader functions: dlopen=%p dlsym=%p android_dlopen_ext=%p",
         loader_dlopen_fn, loader_dlsym_fn, loader_android_dlopen_ext_fn);
    
    // Step 2: Load linkerhook library into namespace
    void* linkerhook = linkernsbypass_namespace_dlopen("liblinkerhook.so", RTLD_LOCAL, g_driver_namespace);
    if (!linkerhook) {
        LOGE("Failed to load liblinkerhook.so: %s", dlerror());
        dlclose(libdl);
        return -1;
    }
    LOGI("Loaded liblinkerhook.so: %p", linkerhook);
    
    // Get linkerhook functions
    auto turnip_linker_set_proc_addrs = 
        (void (*)(void*, void*, void*))dlsym(linkerhook, "turnip_linker_set_proc_addrs");
    auto turnip_linker_init = 
        (int (*)())dlsym(linkerhook, "turnip_linker_init");
    auto turnip_linker_set_vulkan_loader_handle = 
        (void (*)(void*))dlsym(linkerhook, "turnip_linker_set_vulkan_loader_handle");
    auto turnip_linker_set_vulkan_driver_handle = 
        (void (*)(void*))dlsym(linkerhook, "turnip_linker_set_vulkan_driver_handle");
    
    if (!turnip_linker_set_proc_addrs || !turnip_linker_init || 
        !turnip_linker_set_vulkan_loader_handle || !turnip_linker_set_vulkan_driver_handle) {
        LOGE("Failed to get linkerhook functions");
        dlclose(libdl);
        return -1;
    }
    
    // Step 3: Initialize linkerhook with __loader functions
    turnip_linker_set_proc_addrs(loader_dlopen_fn, loader_dlsym_fn, loader_android_dlopen_ext_fn);
    
    if (turnip_linker_init() != 0) {
        LOGE("Failed to initialize linker hook");
        dlclose(libdl);
        return -1;
    }
    
    // Step 4: Load SONAME-patched libvulkan.so
    // This creates a unique copy of libvulkan.so that will use our hooks
    LOGI("Loading SONAME-patched libvulkan.so...");
    void* vulkan_loader = linkernsbypass_namespace_dlopen_unique(
        "/system/lib64/libvulkan.so",
        cache_dir,  // Cache directory for patched file
        RTLD_LOCAL,
        g_driver_namespace
    );
    
    if (!vulkan_loader) {
        LOGE("Failed to load patched libvulkan.so: %s", dlerror());
        dlclose(libdl);
        return -1;
    }
    LOGI("Loaded patched libvulkan.so: %p", vulkan_loader);
    
    // Step 5: Load Turnip driver
    LOGI("Loading Turnip driver (libvulkan_freedreno.so)...");
    void* vulkan_driver = linkernsbypass_namespace_dlopen(
        "libvulkan_freedreno.so",
        RTLD_LOCAL,
        g_driver_namespace
    );
    
    if (!vulkan_driver) {
        LOGE("Failed to load Turnip: %s", dlerror());
        dlclose(libdl);
        return -1;
    }
    LOGI("Loaded Turnip: %p", vulkan_driver);
    
    // Step 6: Set handles in linkerhook
    turnip_linker_set_vulkan_loader_handle(vulkan_loader);
    turnip_linker_set_vulkan_driver_handle(vulkan_driver);
    
    // Save handles
    g_libvulkan_handle = vulkan_loader;
    g_turnip_handle = vulkan_driver;
    
    // IMPORTANT: Get vkGetInstanceProcAddr from patched libvulkan.so (NOT from Turnip directly!)
    // The patched libvulkan.so provides WSI extensions (VK_KHR_android_surface, VK_KHR_surface)
    // which are required for creating Vulkan instances on Android.
    // Through the linkerhook, libvulkan.so will use Turnip as the underlying driver.
    g_vkGetInstanceProcAddr = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_loader, "vkGetInstanceProcAddr");
    
    if (!g_vkGetInstanceProcAddr) {
        LOGE("vkGetInstanceProcAddr not found in patched libvulkan.so: %s", dlerror());
        return -1;
    }
    LOGI("Got vkGetInstanceProcAddr from patched libvulkan.so: %p", g_vkGetInstanceProcAddr);
    
    // Verify Turnip is loaded by checking for HMI (just for logging, not used)
    void* hmi = dlsym(vulkan_driver, "HMI");
    if (hmi) {
        LOGI("Turnip HMI found at: %p (driver is valid)", hmi);
    } else {
        LOGW("Turnip HMI not found, driver may not work correctly");
    }
    
    LOGI("Linker hook setup complete!");
    
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_renderer_TurnipLoader_nativeLoadTurnip(JNIEnv* env, jclass clazz, 
                                                              jstring nativeLibDir, jstring cacheDir) {
    // Check if liblinkernsbypass loaded successfully
    if (!linkernsbypass_load_status()) {
        LOGE("liblinkernsbypass failed to load");
        return JNI_FALSE;
    }
    
    const char* lib_dir_raw = env->GetStringUTFChars(nativeLibDir, nullptr);
    const char* cache_dir_raw = env->GetStringUTFChars(cacheDir, nullptr);
    std::string lib_dir(lib_dir_raw);
    std::string cache_dir(cache_dir_raw);
    env->ReleaseStringUTFChars(nativeLibDir, lib_dir_raw);
    env->ReleaseStringUTFChars(cacheDir, cache_dir_raw);
    
    LOGI("=== Turnip Loader (zomdroid-style) ===");
    LOGI("Library dir: %s", lib_dir.c_str());
    LOGI("Cache dir: %s", cache_dir.c_str());
    
    // Create namespace with access to system libraries and our libraries
    // Important: /system/lib64 MUST come first for libcutils.so and other dependencies
    std::string search_path = "/system/lib64:/vendor/lib64:";
    search_path += lib_dir;
    
    LOGI("Creating namespace with search path: %s", search_path.c_str());
    
    g_driver_namespace = android_create_namespace(
        "turnip-driver",
        search_path.c_str(),
        search_path.c_str(),
        ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED,
        "/system:/data:/vendor:/apex",
        nullptr
    );
    
    if (!g_driver_namespace) {
        LOGE("Failed to create Turnip namespace");
        return JNI_FALSE;
    }
    LOGI("Created namespace successfully");
    
    // Link critical system libraries
    LOGI("Linking system libraries to namespace...");
    if (android_link_namespaces(g_driver_namespace, nullptr, "ld-android.so")) {
        LOGI("  ✓ ld-android.so");
    }
    if (android_link_namespaces(g_driver_namespace, nullptr, "libnativeloader.so")) {
        LOGI("  ✓ libnativeloader.so");
    }
    if (android_link_namespaces(g_driver_namespace, nullptr, "libnativeloader_lazy.so")) {
        LOGI("  ✓ libnativeloader_lazy.so");
    }
    
    // Load linker hook and setup Turnip
    if (load_linker_hook(lib_dir.c_str(), cache_dir.c_str()) != 0) {
        LOGE("Failed to setup linker hook");
        return JNI_FALSE;
    }
    
    // Set environment variables for DXVK
    // Use patched libvulkan.so handle - it provides WSI extensions and uses Turnip via linkerhook
    char handle_str[32];
    snprintf(handle_str, sizeof(handle_str), "0x%lx", reinterpret_cast<uintptr_t>(g_libvulkan_handle));
    setenv("VULKAN_PTR", handle_str, 1);
    LOGI("Set VULKAN_PTR=%s (patched libvulkan.so handle)", handle_str);
    
    char procaddr_str[32];
    snprintf(procaddr_str, sizeof(procaddr_str), "0x%lx", reinterpret_cast<uintptr_t>(g_vkGetInstanceProcAddr));
    setenv("VK_GET_INSTANCE_PROC_ADDR", procaddr_str, 1);
    LOGI("Set VK_GET_INSTANCE_PROC_ADDR=%s", procaddr_str);
    
    LOGI("=== Turnip loaded successfully! ===");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_app_ralaunch_renderer_TurnipLoader_nativeGetTurnipHandle(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(g_turnip_handle);
}

JNIEXPORT jlong JNICALL
Java_com_app_ralaunch_renderer_TurnipLoader_nativeGetVkGetInstanceProcAddr(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(g_vkGetInstanceProcAddr);
}

JNIEXPORT jlong JNICALL
Java_com_app_ralaunch_renderer_TurnipLoader_nativeGetVulkanLoaderHandle(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(g_libvulkan_handle);
}

} // extern "C"
