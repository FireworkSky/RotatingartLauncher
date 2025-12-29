//
// OSMesa bridge for Android Native Window
//
#ifndef OSM_BRIDGE_H
#define OSM_BRIDGE_H

#include <android/native_window.h>
#include <stdbool.h>
#include "osmesa_loader.h"

// Render window state
#define STATE_RENDERER_NEW_WINDOW 1
#define STATE_RENDERER_ALIVE 2

/**
 * @brief OSMesa render window structure
 */
typedef struct {
    char state;
    struct ANativeWindow *nativeSurface;
    struct ANativeWindow *newNativeSurface;
    ANativeWindow_Buffer buffer;
    int32_t last_stride;
    bool disable_rendering;
    OSMesaContext context;
} osm_render_window_t;

/**
 * @brief Initialize OSMesa bridge
 * @return true on success, false on failure
 */
bool osm_init(void);

/**
 * @brief Get current render window
 * @return Current render window or NULL
 */
osm_render_window_t* osm_get_current(void);

/**
 * @brief Initialize OSMesa context
 * @param share Shared context (can be NULL)
 * @return New render window or NULL on failure
 */
osm_render_window_t* osm_init_context(osm_render_window_t* share);

/**
 * @brief Make OSMesa context current
 * @param bundle Render window bundle
 */
void osm_make_current(osm_render_window_t* bundle);

/**
 * @brief Swap buffers (render to native window)
 * 
 * EXPORTED: This function is exported for SDL swap buffers integration
 */
__attribute__((visibility("default")))
void osm_swap_buffers(void);

/**
 * @brief Setup window for rendering
 * @param nativeWindow Android native window
 */
void osm_setup_window(ANativeWindow* nativeWindow);

/**
 * @brief Set swap interval (vsync)
 * @param swapInterval Swap interval (0 = immediate, 1 = vsync)
 */
void osm_swap_interval(int swapInterval);

/**
 * @brief Release current window
 */
void osm_release_window(void);

/**
 * @brief Cleanup OSMesa context
 * @param bundle Render window bundle
 */
void osm_destroy_context(osm_render_window_t* bundle);

/**
 * @brief Ensure OSMesa context is current on the calling thread
 * Call this before any OpenGL operations if context might have been lost
 * @return true if context is current and ready, false otherwise
 * 
 * EXPORTED: This function is exported for cross-library access
 */
__attribute__((visibility("default")))
bool osm_ensure_current(void);

/**
 * @brief Get the global render window (for use from other modules)
 * @return Global render window or NULL
 * 
 * EXPORTED: This function is exported for cross-library access
 */
__attribute__((visibility("default")))
osm_render_window_t* osm_get_global_context(void);

#endif // OSM_BRIDGE_H

