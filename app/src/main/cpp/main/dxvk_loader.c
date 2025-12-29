/**
 * dxvk_loader.c
 * 
 * DXVK 渲染器加载器实现
 * 
 * DXVK 是一个将 Direct3D 8/9/10/11 翻译为 Vulkan 的层
 * 用于在 Android 上运行使用 Direct3D 的应用程序
 */

#include "dxvk_loader.h"
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "DXVKLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// DXVK 组件库句柄
static void* g_dxvk_dxgi = NULL;
static void* g_dxvk_d3d8 = NULL;
static void* g_dxvk_d3d9 = NULL;
static void* g_dxvk_d3d10 = NULL;
static void* g_dxvk_d3d11 = NULL;

static bool g_dxvk_initialized = false;

// DXVK 版本
static const char* DXVK_VERSION = "2.7.1-android";

/**
 * @brief 获取库文件的完整路径
 */
static char* get_lib_path(const char* lib_name) {
    static char path[512];
    const char* native_dir = getenv("RALCORE_NATIVEDIR");
    
    if (native_dir != NULL) {
        snprintf(path, sizeof(path), "%s/%s", native_dir, lib_name);
    } else {
        // 默认路径
        snprintf(path, sizeof(path), "%s", lib_name);
    }
    
    return path;
}

bool dxvk_loader_init(void) {
    if (g_dxvk_initialized) {
        LOGI("DXVK already initialized");
        return true;
    }
    
    LOGI("========================================");
    LOGI("  Initializing DXVK Renderer v%s", DXVK_VERSION);
    LOGI("========================================");
    
    // 设置 DXVK 使用 SDL2 作为 WSI
    setenv("DXVK_WSI_DRIVER", "SDL2", 1);
    LOGI("Set DXVK_WSI_DRIVER=SDL2");
    
    // 启用 DXVK HUD（可选，用于调试）
    const char* hud_env = getenv("DXVK_HUD");
    if (hud_env == NULL) {
        // 默认显示 FPS 和版本
        // setenv("DXVK_HUD", "fps,version", 1);
        // LOGI("Set DXVK_HUD=fps,version");
    }
    
    // 设置日志级别
    const char* log_level = getenv("DXVK_LOG_LEVEL");
    if (log_level == NULL) {
        setenv("DXVK_LOG_LEVEL", "info", 1);
        LOGI("Set DXVK_LOG_LEVEL=info");
    }
    
    // 检查 DXGI 库是否可用
    if (!dxvk_loader_is_available()) {
        LOGE("DXVK libraries not available");
        return false;
    }
    
    // 预加载 DXGI（所有 D3D 组件都需要）
    g_dxvk_dxgi = dxvk_loader_load_component("dxgi");
    if (g_dxvk_dxgi == NULL) {
        LOGE("Failed to load DXVK DXGI");
        return false;
    }
    
    g_dxvk_initialized = true;
    LOGI("✓ DXVK initialized successfully");
    
    return true;
}

void* dxvk_loader_load_component(const char* component) {
    char lib_name[64];
    void** handle_ptr = NULL;
    
    if (strcmp(component, "dxgi") == 0) {
        snprintf(lib_name, sizeof(lib_name), "libdxvk_dxgi.so");
        handle_ptr = &g_dxvk_dxgi;
    } else if (strcmp(component, "d3d8") == 0) {
        snprintf(lib_name, sizeof(lib_name), "libdxvk_d3d8.so");
        handle_ptr = &g_dxvk_d3d8;
    } else if (strcmp(component, "d3d9") == 0) {
        snprintf(lib_name, sizeof(lib_name), "libdxvk_d3d9.so");
        handle_ptr = &g_dxvk_d3d9;
    } else if (strcmp(component, "d3d10") == 0) {
        snprintf(lib_name, sizeof(lib_name), "libdxvk_d3d10core.so");
        handle_ptr = &g_dxvk_d3d10;
    } else if (strcmp(component, "d3d11") == 0) {
        snprintf(lib_name, sizeof(lib_name), "libdxvk_d3d11.so");
        handle_ptr = &g_dxvk_d3d11;
    } else {
        LOGE("Unknown DXVK component: %s", component);
        return NULL;
    }
    
    // 检查是否已加载
    if (*handle_ptr != NULL) {
        LOGI("DXVK %s already loaded", component);
        return *handle_ptr;
    }
    
    // 加载库
    char* path = get_lib_path(lib_name);
    LOGI("Loading DXVK component: %s from %s", component, path);
    
    void* handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (handle == NULL) {
        // 尝试直接加载（在 LD_LIBRARY_PATH 中）
        handle = dlopen(lib_name, RTLD_NOW | RTLD_GLOBAL);
    }
    
    if (handle == NULL) {
        const char* error = dlerror();
        LOGE("Failed to load %s: %s", lib_name, error ? error : "unknown error");
        return NULL;
    }
    
    *handle_ptr = handle;
    LOGI("✓ Loaded DXVK %s: %p", component, handle);
    
    return handle;
}

bool dxvk_loader_is_available(void) {
    // 检查 DXGI 库是否存在
    char* path = get_lib_path("libdxvk_dxgi.so");
    
    void* handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        // 尝试直接加载
        handle = dlopen("libdxvk_dxgi.so", RTLD_NOW | RTLD_LOCAL);
    }
    
    if (handle != NULL) {
        dlclose(handle);
        return true;
    }
    
    return false;
}

const char* dxvk_loader_get_version(void) {
    return DXVK_VERSION;
}

void dxvk_loader_cleanup(void) {
    LOGI("Cleaning up DXVK...");
    
    if (g_dxvk_d3d11 != NULL) {
        dlclose(g_dxvk_d3d11);
        g_dxvk_d3d11 = NULL;
    }
    
    if (g_dxvk_d3d10 != NULL) {
        dlclose(g_dxvk_d3d10);
        g_dxvk_d3d10 = NULL;
    }
    
    if (g_dxvk_d3d9 != NULL) {
        dlclose(g_dxvk_d3d9);
        g_dxvk_d3d9 = NULL;
    }
    
    if (g_dxvk_d3d8 != NULL) {
        dlclose(g_dxvk_d3d8);
        g_dxvk_d3d8 = NULL;
    }
    
    if (g_dxvk_dxgi != NULL) {
        dlclose(g_dxvk_dxgi);
        g_dxvk_dxgi = NULL;
    }
    
    g_dxvk_initialized = false;
    LOGI("DXVK cleaned up");
}

// ==================== JNI 接口 ====================

#include <jni.h>

JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_renderer_DXVKLoader_nativeInit(JNIEnv *env, jclass clazz) {
    return dxvk_loader_init() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_renderer_DXVKLoader_nativeIsAvailable(JNIEnv *env, jclass clazz) {
    return dxvk_loader_is_available() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_renderer_DXVKLoader_nativeGetVersion(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, dxvk_loader_get_version());
}

JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_renderer_DXVKLoader_nativeLoadComponent(JNIEnv *env, jclass clazz, jstring component) {
    if (component == NULL) {
        return JNI_FALSE;
    }
    
    const char* comp_str = (*env)->GetStringUTFChars(env, component, NULL);
    if (comp_str == NULL) {
        return JNI_FALSE;
    }
    
    void* handle = dxvk_loader_load_component(comp_str);
    (*env)->ReleaseStringUTFChars(env, component, comp_str);
    
    return handle != NULL ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_app_ralaunch_renderer_DXVKLoader_nativeCleanup(JNIEnv *env, jclass clazz) {
    dxvk_loader_cleanup();
}




