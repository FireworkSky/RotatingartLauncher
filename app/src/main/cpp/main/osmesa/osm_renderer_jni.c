//
// OSMesa renderer JNI interface
//
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "osm_renderer.h"
#include "vulkan_loader.h"
#include "app_logger.h"

#define LOG_TAG "OSMRendererJNI"
#define LOGI(...) app_logger_log(LOG_LEVEL_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) app_logger_log(LOG_LEVEL_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * @brief JNI: Initialize OSMesa renderer
 * Java: nativeInitOSMRenderer(Surface surface)
 */
JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_renderer_OSMRenderer_nativeInit(JNIEnv *env, jclass clazz, jobject surface) {
    ANativeWindow* nativeWindow = NULL;
    
    if (surface != NULL) {
        nativeWindow = ANativeWindow_fromSurface(env, surface);
        if (nativeWindow == NULL) {
            LOGE("Failed to get native window from surface");
            return JNI_FALSE;
        }
        LOGI("Got native window from surface: %p", nativeWindow);
    }
    
    bool result = osm_renderer_init(nativeWindow);
    
    if (nativeWindow != NULL) {
        // Don't release here, OSMesa will manage it
    }
    
    return result ? JNI_TRUE : JNI_FALSE;
}

/**
 * @brief JNI: Cleanup OSMesa renderer
 * Java: nativeCleanup()
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_renderer_OSMRenderer_nativeCleanup(JNIEnv *env, jclass clazz) {
    osm_renderer_cleanup();
}

/**
 * @brief JNI: Swap buffers
 * Java: nativeSwapBuffers()
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_renderer_OSMRenderer_nativeSwapBuffers(JNIEnv *env, jclass clazz) {
    osm_renderer_swap_buffers();
}

/**
 * @brief JNI: Set swap interval
 * Java: nativeSetSwapInterval(int interval)
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_renderer_OSMRenderer_nativeSetSwapInterval(JNIEnv *env, jclass clazz, jint interval) {
    osm_renderer_set_swap_interval(interval);
}

/**
 * @brief JNI: Check if OSMesa is available
 * Java: nativeIsAvailable()
 */
JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_renderer_OSMRenderer_nativeIsAvailable(JNIEnv *env, jclass clazz) {
    return osm_renderer_is_available() ? JNI_TRUE : JNI_FALSE;
}

/**
 * @brief JNI: Set renderer window
 * Java: nativeSetWindow(Surface surface)
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_renderer_OSMRenderer_nativeSetWindow(JNIEnv *env, jclass clazz, jobject surface) {
    ANativeWindow* nativeWindow = NULL;
    
    if (surface != NULL) {
        nativeWindow = ANativeWindow_fromSurface(env, surface);
        if (nativeWindow == NULL) {
            LOGE("Failed to get native window from surface");
            return;
        }
        LOGI("Setting OSMesa window from surface: %p", nativeWindow);
    }
    
    osm_renderer_set_window(nativeWindow);
    
    // Don't release here, OSMesa will manage it
}

/**
 * @brief JNI: Load Vulkan library
 * Java: nativeLoadVulkanInternal()
 * 
 * Loads Vulkan library which is required for zink renderer.
 * This must be called before OSMesa initialization when using zink.
 */
JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_renderer_OSMRenderer_nativeLoadVulkanInternal(JNIEnv *env, jclass clazz) {
    LOGI("Loading Vulkan library for zink renderer...");
    bool result = vulkan_loader_load();
    if (result) {
        LOGI("Vulkan library loaded successfully");
    } else {
        LOGE("Failed to load Vulkan library");
    }
    return result ? JNI_TRUE : JNI_FALSE;
}

