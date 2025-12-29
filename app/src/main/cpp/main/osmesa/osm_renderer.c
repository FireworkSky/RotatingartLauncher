//
// OSMesa renderer integration for zink
//
#include "osm_renderer.h"
#include "osm_bridge.h"
#include "vulkan_loader.h"
#include "osmesa_loader.h" // For glGetString_p
#include <android/log.h>
#include <unistd.h> // for usleep
#include <stdlib.h> // for getenv

#define LOG_TAG "OSMRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static bool g_osm_initialized = false;
static osm_render_window_t* g_render_window = NULL;

bool osm_renderer_init(ANativeWindow* nativeWindow) {
    if (g_osm_initialized) {
        LOGI("OSMesa renderer already initialized");
        if (nativeWindow != NULL) {
            osm_renderer_set_window(nativeWindow);
        }
        return true;
    }

    LOGI("Initializing OSMesa renderer for zink...");

    // CRITICAL: Load Vulkan library BEFORE creating OSMesa context
    // zink requires Vulkan to be available when creating the screen
    if (!vulkan_loader_load()) {
        LOGE("Failed to load Vulkan library - zink requires Vulkan");
        return false;
    }
    LOGI("Vulkan library loaded, proceeding with OSMesa initialization");

    // CRITICAL: Wait a bit for Vulkan device enumeration to complete
    // zink needs Vulkan devices to be enumerated before creating context
    // This delay allows Vulkan loader to discover available devices
    LOGI("Waiting for Vulkan device enumeration...");
    usleep(200000); // 200ms delay to allow Vulkan device enumeration (increased from 100ms)
    
    // Additional check: verify that Vulkan devices are available
    // This helps ensure zink can find a GPU device before attempting to create context
    const char* vulkan_ptr = getenv("VULKAN_PTR");
    if (vulkan_ptr == NULL || vulkan_ptr[0] == '\0') {
        LOGW("VULKAN_PTR not set, Vulkan may not be properly initialized");
    } else {
        LOGI("VULKAN_PTR is set: %s", vulkan_ptr);
    }

    // Initialize OSMesa library
    if (!osm_init()) {
        LOGE("Failed to initialize OSMesa library");
        return false;
    }

    // Create OSMesa context
    LOGI("Creating OSMesa context (zink will attempt to find Vulkan device)...");
    g_render_window = osm_init_context(NULL);
    if (g_render_window == NULL) {
        LOGE("Failed to create OSMesa context");
        return false;
    }

    // IMPORTANT: Make context current FIRST so currentBundle is set
    // This is required before osm_setup_window() can work
    osm_make_current(g_render_window);

    // Set window AFTER context is current
    if (nativeWindow != NULL) {
        LOGI("Setting native window: %p", nativeWindow);
        osm_setup_window(nativeWindow);
        // Trigger surface swap to actually apply the window
        osm_render_window_t* bundle = osm_get_current();
        if (bundle != NULL) {
            bundle->state = STATE_RENDERER_NEW_WINDOW;
        }
    }

    // CRITICAL: After making context current, wait a bit and verify context is ready
    // zink needs time to initialize the Vulkan device and create the OpenGL context
    // This ensures glGetString() will return valid values instead of NULL
    LOGI("Waiting for OSMesa context to be ready...");
    usleep(100000); // 100ms delay to allow zink to fully initialize the context
    
    // Verify context is ready by checking if glGetString works
    // Use OSMesa's glGetString directly to avoid SDL wrapper issues
    if (glGetString_p != NULL) {
        const GLubyte* renderer = glGetString_p(GL_RENDERER);
        if (renderer != NULL) {
            LOGI("✓ OSMesa context is ready, renderer: %s", renderer);
        } else {
            LOGW("⚠ OSMesa context not fully ready yet (glGetString returned NULL), but continuing...");
            // Still continue, as FNA3D has fallback handling for NULL glGetString
        }
    }

    g_osm_initialized = true;
    LOGI("OSMesa renderer initialized successfully");
    return true;
}

void osm_renderer_cleanup(void) {
    if (!g_osm_initialized) {
        return;
    }

    LOGI("Cleaning up OSMesa renderer...");

    if (g_render_window != NULL) {
        osm_destroy_context(g_render_window);
        g_render_window = NULL;
    }

    g_osm_initialized = false;
    LOGI("OSMesa renderer cleaned up");
}

void osm_renderer_swap_buffers(void) {
    if (!g_osm_initialized || g_render_window == NULL) {
        return;
    }

    osm_swap_buffers();
}

void osm_renderer_set_swap_interval(int interval) {
    if (!g_osm_initialized) {
        return;
    }

    osm_swap_interval(interval);
}

bool osm_renderer_is_available(void) {
    return osm_init(); // Try to load OSMesa library
}

bool osm_renderer_is_initialized(void) {
    return g_osm_initialized;
}

ANativeWindow* osm_renderer_get_window(void) {
    if (g_render_window == NULL) {
        return NULL;
    }
    return g_render_window->nativeSurface;
}

void osm_renderer_set_window(ANativeWindow* nativeWindow) {
    if (!g_osm_initialized || g_render_window == NULL) {
        LOGE("OSMesa renderer not initialized, cannot set window");
        return;
    }

    LOGI("Setting OSMesa renderer window: %p", nativeWindow);
    
    // IMPORTANT: Set the window directly on g_render_window instead of using
    // osm_setup_window which relies on thread-local currentBundle.
    // This ensures the window is set correctly even when called from JNI
    // where currentBundle might be NULL on the calling thread.
    g_render_window->newNativeSurface = nativeWindow;
    g_render_window->state = STATE_RENDERER_NEW_WINDOW;
    LOGI("✓ Window set on global context, state=NEW_WINDOW");
}

