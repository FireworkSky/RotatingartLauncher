/**
 * OSMesa Renderer Bridge
 *
 * Provides the bridge functions that SDL_androidgl.c expects for OSMesa/Zink rendering.
 * OSMesa renders to an off-screen buffer, which is then copied to the ANativeWindow.
 *
 * Functions exported (called by SDL via dlsym):
 *   osm_renderer_is_available()   - Check if OSMesa library can be loaded
 *   osm_renderer_is_initialized() - Check if OSMesa context is created
 *   osm_renderer_init()           - Create OSMesa context and attach to native window
 *   osm_swap_buffers()            - Copy render buffer to native window
 *   osm_renderer_destroy()        - Cleanup
 */

#ifndef OSM_RENDERER_H
#define OSM_RENDERER_H

#include <stdbool.h>
#include <android/native_window.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Check if OSMesa library is available (can be loaded via dlopen).
 */
bool osm_renderer_is_available(void);

/**
 * Check if OSMesa context has been initialized.
 */
bool osm_renderer_is_initialized(void);

/**
 * Initialize OSMesa renderer:
 * 1. Load libOSMesa.so
 * 2. Create OSMesa context
 * 3. Allocate color buffer matching native window size
 * 4. Make context current
 *
 * @param window The ANativeWindow to render to
 * @return true on success
 */
bool osm_renderer_init(ANativeWindow *window);

/**
 * Copy the OSMesa render buffer to the ANativeWindow.
 * Called once per frame after OpenGL rendering is complete.
 */
void osm_swap_buffers(void);

/**
 * Destroy OSMesa context and free resources.
 */
void osm_renderer_destroy(void);

#ifdef __cplusplus
}
#endif

#endif /* OSM_RENDERER_H */
