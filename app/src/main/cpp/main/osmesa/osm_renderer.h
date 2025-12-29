//
// OSMesa renderer integration for zink
//
#ifndef OSM_RENDERER_H
#define OSM_RENDERER_H

#include <android/native_window.h>
#include <stdbool.h>

/**
 * @brief Initialize OSMesa renderer for zink
 * @param nativeWindow Android native window (can be NULL for offscreen)
 * @return true on success, false on failure
 */
bool osm_renderer_init(ANativeWindow* nativeWindow);

/**
 * @brief Cleanup OSMesa renderer
 */
void osm_renderer_cleanup(void);

/**
 * @brief Swap buffers (render to window)
 */
void osm_renderer_swap_buffers(void);

/**
 * @brief Set swap interval (vsync)
 * @param interval Swap interval (0 = immediate, 1 = vsync)
 */
void osm_renderer_set_swap_interval(int interval);

/**
 * @brief Check if OSMesa renderer is available
 * @return true if available, false otherwise
 */
bool osm_renderer_is_available(void);

/**
 * @brief Check if OSMesa renderer is initialized
 * @return true if initialized, false otherwise
 */
bool osm_renderer_is_initialized(void);

/**
 * @brief Get current renderer window
 * @return Current window or NULL
 */
ANativeWindow* osm_renderer_get_window(void);

/**
 * @brief Set renderer window
 * @param nativeWindow Android native window
 */
void osm_renderer_set_window(ANativeWindow* nativeWindow);

#endif // OSM_RENDERER_H

