//
// OSMesa bridge for Android Native Window
//
#include <malloc.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "osm_bridge.h"
#include "osmesa_loader.h" // For OSMesaCreateContextAttribs_p
#include "osmesa_loader.h" // For OSMesaCreateContextAttribs_p

#define LOG_TAG "OSMBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static __thread osm_render_window_t* currentBundle = NULL;
// Global context pointer (not thread-local) for cross-thread access
static osm_render_window_t* g_global_context = NULL;
// Dummy buffer for rendering when there's nowhere to render
// Use a small buffer (1x1) - even a 1x1 buffer is enough to initialize OSMesa context with zink
static char dummy_buffer[4]; // RGBA, 1x1
// Track if buffer is currently locked (per-thread)
static __thread bool buffer_is_locked = false;

bool osm_init(void) {
    if (!dlsym_OSMesa()) {
        LOGE("Failed to load OSMesa library");
        return false;
    }
    LOGI("OSMesa library loaded successfully");
    return true;
}

osm_render_window_t* osm_get_current(void) {
    return currentBundle;
}

osm_render_window_t* osm_init_context(osm_render_window_t* share) {
    osm_render_window_t* render_window = (osm_render_window_t*) malloc(sizeof(osm_render_window_t));
    if (render_window == NULL) {
        LOGE("Failed to allocate render window");
        return NULL;
    }
    memset(render_window, 0, sizeof(osm_render_window_t));
    
    OSMesaContext osmesa_share = NULL;
    if (share != NULL) {
        osmesa_share = share->context;
    }
    
    // For zink, try using OSMesaCreateContextAttribs with explicit attributes
    // This may help zink find the Vulkan device correctly
    OSMesaContext context = NULL;
    
    // Try OSMesaCreateContextAttribs first (if available) for better zink compatibility
    if (OSMesaCreateContextAttribs_p != NULL) {
        // Use Compatibility Profile instead of Core Profile
        // FNA3D and MojoShader may use legacy OpenGL functions that require compat profile
        // OSMESA_PROFILE = 0x33
        // OSMESA_COMPAT_PROFILE = 0x35 (not Core = 0x34)
        int attribs[] = {
            0x33, // OSMESA_PROFILE
            0x35, // OSMESA_COMPAT_PROFILE (instead of 0x34 Core)
            0x36, // OSMESA_CONTEXT_MAJOR_VERSION
            4,    // Major version 4
            0x37, // OSMESA_CONTEXT_MINOR_VERSION
            6,    // Minor version 6
            0     // Terminator
        };
        LOGI("Attempting to create OSMesa context with attributes (OpenGL 4.6 Compat)...");
        context = OSMesaCreateContextAttribs_p(attribs, osmesa_share);
        
        // If Compat profile fails, try without profile specification (let Mesa decide)
        if (context == NULL) {
            LOGI("Compat profile failed, trying without profile specification...");
            int attribs_no_profile[] = {
                0x36, // OSMESA_CONTEXT_MAJOR_VERSION
                3,    // Major version 3 (more widely supported)
                0x37, // OSMESA_CONTEXT_MINOR_VERSION
                3,    // Minor version 3
                0     // Terminator
            };
            context = OSMesaCreateContextAttribs_p(attribs_no_profile, osmesa_share);
        }
    }
    
    // Fallback to simple OSMesaCreateContext if attribs version failed or not available
    if (context == NULL) {
        LOGI("Using OSMesaCreateContext (simple version)...");
        // Use OSMESA_RGBA for correct color channel order on Android
        // OSMESA_RGBA = GL_RGBA = 0x1908
        // On little-endian Android, this produces R8G8B8A8 in memory
        // which matches ANativeWindow's WINDOW_FORMAT_RGBA_8888
        context = OSMesaCreateContext_p(GL_RGBA, osmesa_share); // OSMESA_RGBA
        if (context == NULL) {
            LOGI("GL_RGBA failed, trying OSMESA_BGRA...");
            context = OSMesaCreateContext_p(0x1, osmesa_share); // OSMESA_BGRA = 0x1
        }
    }
    
    if (context == NULL) {
        LOGE("Failed to create OSMesa context");
        free(render_window);
        return NULL;
    }
    
    render_window->context = context;
    LOGI("OSMesa context created: %p", context);
    
    // Store as global context for cross-thread access
    g_global_context = render_window;
    
    return render_window;
}

void osm_set_no_render_buffer(ANativeWindow_Buffer* buffer) {
    // Use a small dummy buffer - even a 1x1 buffer is enough to initialize OSMesa context
    buffer->bits = dummy_buffer;
    buffer->width = 1;
    buffer->height = 1;
    buffer->stride = 1; // stride = width for dummy buffer
}

void osm_swap_surfaces(osm_render_window_t* bundle) {
    // If we have a newNativeSurface to switch to
    if (bundle->newNativeSurface != NULL) {
        // Release old surface if different
        if (bundle->nativeSurface != NULL && bundle->newNativeSurface != bundle->nativeSurface) {
            if (!bundle->disable_rendering) {
                LOGI("Unlocking for cleanup...");
                ANativeWindow_unlockAndPost(bundle->nativeSurface);
            }
            ANativeWindow_release(bundle->nativeSurface);
        }
        
        LOGI("Switching to new native surface: %p", bundle->newNativeSurface);
        bundle->nativeSurface = bundle->newNativeSurface;
        bundle->newNativeSurface = NULL;
        ANativeWindow_acquire(bundle->nativeSurface);
        // Use WINDOW_FORMAT_RGBA_8888 (1) to match OSMesa's RGBA output
        // OSMesa with GL_RGBA format outputs R,G,B,A bytes which matches WINDOW_FORMAT_RGBA_8888
        ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, 1); // WINDOW_FORMAT_RGBA_8888
        bundle->disable_rendering = false;
        LOGI("osm_swap_surfaces: Set buffer format to RGBA_8888 for OSMesa RGBA output");
        return;
    }
    
    // If we already have a working native surface, keep using it
    if (bundle->nativeSurface != NULL && !bundle->disable_rendering) {
        LOGI("Keeping existing native surface: %p", bundle->nativeSurface);
        return;
    }
    
    // No surface available, switch to dummy framebuffer
    LOGI("No native surface available, switching to dummy framebuffer");
    if (bundle->nativeSurface != NULL) {
        ANativeWindow_release(bundle->nativeSurface);
        bundle->nativeSurface = NULL;
    }
    osm_set_no_render_buffer(&bundle->buffer);
    bundle->disable_rendering = true;
}

void osm_release_window(void) {
    if (currentBundle != NULL) {
        currentBundle->newNativeSurface = NULL;
        osm_swap_surfaces(currentBundle);
    }
}

void osm_apply_current_ll(osm_render_window_t* bundle) {
    ANativeWindow_Buffer* buffer = &bundle->buffer;
    GLboolean result = OSMesaMakeCurrent_p(bundle->context, buffer->bits, GL_UNSIGNED_BYTE, buffer->width, buffer->height);
    if (!result) {
        LOGE("OSMesaMakeCurrent FAILED! context=%p bits=%p size=%dx%d", 
            bundle->context, buffer->bits, buffer->width, buffer->height);
    }
    if (buffer->stride != bundle->last_stride) {
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buffer->stride);
        LOGI("osm_apply_current_ll: Set OSMESA_ROW_LENGTH to %d", buffer->stride);
    }
    bundle->last_stride = buffer->stride;
    // CRITICAL: Always ensure Y_UP is set correctly after MakeCurrent
    OSMesaPixelStore_p(OSMESA_Y_UP, 0);
    
    // CRITICAL: Set viewport to match buffer dimensions
    // FNA3D might have cached wrong viewport from initial 1x1 dummy buffer
    static void (*glViewport_p)(GLint, GLint, GLsizei, GLsizei) = NULL;
    static void (*glScissor_p)(GLint, GLint, GLsizei, GLsizei) = NULL;
    if (glViewport_p == NULL && OSMesaGetProcAddress_p != NULL) {
        glViewport_p = (void (*)(GLint, GLint, GLsizei, GLsizei))OSMesaGetProcAddress_p("glViewport");
        glScissor_p = (void (*)(GLint, GLint, GLsizei, GLsizei))OSMesaGetProcAddress_p("glScissor");
    }
    if (glViewport_p != NULL && buffer->width > 1 && buffer->height > 1) {
        glViewport_p(0, 0, buffer->width, buffer->height);
        if (glScissor_p != NULL) {
            glScissor_p(0, 0, buffer->width, buffer->height);
        }
    }
}

void osm_make_current(osm_render_window_t* bundle) {
    if (bundle == NULL) {
        // Technically this does nothing as it's not possible to unbind a context in OSMesa
        OSMesaMakeCurrent_p(NULL, NULL, 0, 0, 0);
        currentBundle = NULL;
        return;
    }
    
    currentBundle = bundle;
    
    // CRITICAL: Use a small dummy buffer FIRST to initialize context
    // This ensures OSMesa context is ready even before native surface is available
    // Surface management is handled by osm_swap_buffers, not here
    osm_set_no_render_buffer(&bundle->buffer);
    
    // Make context current with the dummy buffer FIRST
    // This is critical for zink - it needs a valid buffer to initialize properly
    osm_apply_current_ll(bundle);
    OSMesaPixelStore_p(OSMESA_Y_UP, 0);
    
    // Force context initialization with glClear/glFinish
    // This ensures zink's OpenGL context is fully ready before any shader operations
    if (glClearColor_p != NULL && glClear_p != NULL && glFinish_p != NULL) {
        glClearColor_p(0.0f, 0.0f, 0.0f, 1.0f);
        glClear_p(0x00004000); // GL_COLOR_BUFFER_BIT
        glFinish_p();
        LOGI("OSMesa context initialized with glClear/glFinish");
    }
    
    // DON'T call osm_swap_surfaces here!
    // Surface switching is handled entirely by osm_swap_buffers when it sees STATE_RENDERER_NEW_WINDOW
    // This avoids the double-swap bug where newNativeSurface gets consumed before osm_swap_buffers runs
    
    // Verify context is ready
    if (glGetString_p != NULL) {
        const GLubyte* test = glGetString_p(GL_VERSION);
        if (test == NULL) {
            LOGW("OSMesa context made current but glGetString(GL_VERSION) returned NULL - context may not be fully ready");
        } else {
            LOGI("OSMesa context is ready, OpenGL version: %s", test);
        }
    }
}

__attribute__((visibility("default")))
void osm_swap_buffers(void) {
    static int frame_count = 0;
    static bool first_frame_logged = false;
    
    // Function pointers - load once
    static void (*glBindFramebuffer_p)(GLenum, GLuint) = NULL;
    static void (*glGetIntegerv_p)(GLenum, GLint*) = NULL;
    static void (*glViewport_p)(GLint, GLint, GLsizei, GLsizei) = NULL;
    static void (*glScissor_p)(GLint, GLint, GLsizei, GLsizei) = NULL;
    static void (*glFlush_p)(void) = NULL;
    static void (*glBlitFramebuffer_p)(GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLbitfield, GLenum) = NULL;
    static GLenum (*glGetError_p)(void) = NULL;
    static bool funcs_loaded = false;
    
    if (currentBundle == NULL) {
        LOGW("osm_swap_buffers: currentBundle is NULL!");
        return;
    }
    
    // Load GL functions once
    if (!funcs_loaded && OSMesaGetProcAddress_p != NULL) {
        glBindFramebuffer_p = (void (*)(GLenum, GLuint))OSMesaGetProcAddress_p("glBindFramebuffer");
        glGetIntegerv_p = (void (*)(GLenum, GLint*))OSMesaGetProcAddress_p("glGetIntegerv");
        glViewport_p = (void (*)(GLint, GLint, GLsizei, GLsizei))OSMesaGetProcAddress_p("glViewport");
        glScissor_p = (void (*)(GLint, GLint, GLsizei, GLsizei))OSMesaGetProcAddress_p("glScissor");
        glFlush_p = (void (*)(void))OSMesaGetProcAddress_p("glFlush");
        glBlitFramebuffer_p = (void (*)(GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLbitfield, GLenum))
            OSMesaGetProcAddress_p("glBlitFramebuffer");
        glGetError_p = (GLenum (*)(void))OSMesaGetProcAddress_p("glGetError");
        funcs_loaded = true;
        LOGI("osm_swap_buffers: GL functions loaded - glBlitFramebuffer=%p", glBlitFramebuffer_p);
    }
    
    // Always log first frame state for debugging
    if (!first_frame_logged) {
        LOGI("osm_swap_buffers: FIRST FRAME - state=%d, nativeSurface=%p, newNativeSurface=%p, disable_rendering=%d, buffer_locked=%d",
            currentBundle->state, currentBundle->nativeSurface, 
            currentBundle->newNativeSurface, currentBundle->disable_rendering, buffer_is_locked);
        first_frame_logged = true;
    }
    
    if (currentBundle->state == STATE_RENDERER_NEW_WINDOW) {
        LOGI("osm_swap_buffers: switching to new window (state=NEW_WINDOW)");
        osm_swap_surfaces(currentBundle);
        currentBundle->state = STATE_RENDERER_ALIVE;
        buffer_is_locked = false; // Need to re-lock for new surface
    }
    
    // CRITICAL FIX: Double-buffering pattern
    // 1. First, flush and post the CURRENT frame (if buffer is locked)
    // 2. Then lock and prepare the NEXT buffer for rendering
    
    if (buffer_is_locked && currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering) {
        ANativeWindow_Buffer* buf = &currentBundle->buffer;
        
        // CRITICAL FIX: Get current FBO and check if we need to blit from FBO to default framebuffer
        // FNA3D uses a faux-backbuffer (FBO) for rendering, then blits to FBO 0
        // OSMesa's flush_front ONLY flushes FBO 0 to the user buffer!
        GLint current_fb = 0;
        GLint current_read_fb = 0;
        GLint current_draw_fb = 0;
        
        if (glGetIntegerv_p != NULL) {
            glGetIntegerv_p(0x8CA6, &current_fb); // GL_FRAMEBUFFER_BINDING
            glGetIntegerv_p(0x8CA8, &current_read_fb); // GL_READ_FRAMEBUFFER_BINDING  
            glGetIntegerv_p(0x8CA9, &current_draw_fb); // GL_DRAW_FRAMEBUFFER_BINDING
        }
        
        if (frame_count < 10) {
            LOGI("osm_swap_buffers: frame %d - current FB=%d, read=%d, draw=%d",
                frame_count, current_fb, current_read_fb, current_draw_fb);
        }
        
        // CRITICAL: If rendering was done to a non-zero FBO, we need to blit it to FBO 0
        // Because OSMesa only flushes FBO 0 to the user-provided buffer
        if (current_fb != 0 || current_draw_fb != 0) {
            GLint fbo_to_blit = (current_draw_fb != 0) ? current_draw_fb : current_fb;
            
            if (glBlitFramebuffer_p != NULL && glBindFramebuffer_p != NULL) {
                // Set up read from the FBO that was rendered to
                glBindFramebuffer_p(0x8CA8, fbo_to_blit); // GL_READ_FRAMEBUFFER
                // Set up write to default framebuffer (FBO 0)
                glBindFramebuffer_p(0x8CA9, 0); // GL_DRAW_FRAMEBUFFER
                
                // Blit the FBO content to default framebuffer
                glBlitFramebuffer_p(
                    0, 0, buf->width, buf->height,  // src rect
                    0, 0, buf->width, buf->height,  // dst rect
                    0x00004000,  // GL_COLOR_BUFFER_BIT
                    0x2600       // GL_NEAREST
                );
                
                // Now bind FBO 0 as the active framebuffer so glFinish flushes it
                glBindFramebuffer_p(0x8D40, 0); // GL_FRAMEBUFFER
                
                if (frame_count < 10) {
                    LOGI("osm_swap_buffers: Blitted FBO %d to FBO 0 (%dx%d)", 
                        fbo_to_blit, buf->width, buf->height);
                }
            } else {
                // Fallback: just bind to FBO 0
                if (glBindFramebuffer_p != NULL) {
                    glBindFramebuffer_p(0x8D40, 0); // GL_FRAMEBUFFER
                }
                if (frame_count < 5) {
                    LOGW("osm_swap_buffers: No glBlitFramebuffer, just binding FBO 0");
                }
            }
        }
        
        // Set viewport to match buffer dimensions
        if (glViewport_p != NULL) {
            glViewport_p(0, 0, buf->width, buf->height);
        }
        if (glScissor_p != NULL) {
            glScissor_p(0, 0, buf->width, buf->height);
        }
        
        // Check for GL errors before flush
        if (frame_count < 10 && glGetError_p != NULL) {
            GLenum err = glGetError_p();
            if (err != 0) {
                LOGE("osm_swap_buffers: GL ERROR before flush: 0x%x", err);
            }
        }
        
        // CRITICAL: Call glFlush first to trigger OSMesa's flush_front
        // OSMesa's flush_front copies the internal texture to the user buffer
        if (glFlush_p != NULL) {
            glFlush_p();
        }
        
        // Then call glFinish to ensure all rendering is complete
        if (glFinish_p != NULL) {
            glFinish_p();
        }
        
        // DEBUG: Check if buffer has non-zero data after flush
        if (frame_count < 10) {
            uint32_t* pixels = (uint32_t*)buf->bits;
            
            // Sample corners and center
            uint32_t p_topleft = pixels[0];
            uint32_t p_topright = pixels[buf->width - 1];
            uint32_t p_center = pixels[(buf->height/2) * buf->stride + buf->width/2];
            uint32_t p_bottomleft = pixels[(buf->height-1) * buf->stride];
            uint32_t p_bottomright = pixels[(buf->height-1) * buf->stride + buf->width-1];
            
            // Count non-black pixels
            int nonblack_count = 0;
            for (int y = 0; y < buf->height; y += 20) {
                for (int x = 0; x < buf->width; x += 20) {
                    uint32_t pixel = pixels[y * buf->stride + x];
                    if ((pixel & 0x00FFFFFF) != 0x00000000) {
                        nonblack_count++;
                    }
                }
            }
            
            LOGI("osm_swap_buffers: frame %d - buffer %dx%d - nonblack=%d TL=0x%08x C=0x%08x BR=0x%08x",
                frame_count, buf->width, buf->height, nonblack_count, 
                p_topleft, p_center, p_bottomright);
        }
        
        // Post the current frame
        int post_result = ANativeWindow_unlockAndPost(currentBundle->nativeSurface);
        if (post_result != 0) {
            LOGE("osm_swap_buffers: ANativeWindow_unlockAndPost failed: %d", post_result);
            osm_release_window();
        }
        buffer_is_locked = false;
    }
    
    // Now lock and prepare the next buffer for the next frame's rendering
    if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering) {
        int lock_result = ANativeWindow_lock(currentBundle->nativeSurface, &currentBundle->buffer, NULL);
        if (lock_result != 0) {
            LOGE("osm_swap_buffers: ANativeWindow_lock failed: %d", lock_result);
            osm_release_window();
            buffer_is_locked = false;
            return;
        }
        
        // Make OSMesa target this buffer for the NEXT frame's rendering
        osm_apply_current_ll(currentBundle);
        buffer_is_locked = true;
        
        // Log buffer info on first successful lock
        if (frame_count == 0) {
            LOGI("osm_swap_buffers: buffer locked SUCCESS - %dx%d, stride=%d, bits=%p",
                currentBundle->buffer.width, currentBundle->buffer.height,
                currentBundle->buffer.stride, currentBundle->buffer.bits);
        }
    } else {
        // Rendering disabled - use dummy buffer
        if (frame_count == 0) {
            LOGW("osm_swap_buffers: RENDERING DISABLED - nativeSurface=%p, disable_rendering=%d",
                currentBundle->nativeSurface, currentBundle->disable_rendering);
        }
        osm_set_no_render_buffer(&currentBundle->buffer);
        osm_apply_current_ll(currentBundle);
        buffer_is_locked = false;
    }
    
    frame_count++;
    // Log every 120 frames (approximately every 2 seconds at 60fps)
    if (frame_count % 120 == 0) {
        LOGI("osm_swap_buffers: frame %d (nativeSurface=%p, disabled=%d)", 
            frame_count, currentBundle->nativeSurface, currentBundle->disable_rendering);
    }
}

void osm_setup_window(ANativeWindow* nativeWindow) {
    if (currentBundle != NULL) {
        LOGI("Setting up window for current bundle");
        currentBundle->state = STATE_RENDERER_NEW_WINDOW;
        currentBundle->newNativeSurface = nativeWindow;
    }
}

void osm_swap_interval(int swapInterval) {
    // OSMesa doesn't have direct swap interval control
    // This would need to be handled at the native window level if needed
    (void) swapInterval;
}

void osm_destroy_context(osm_render_window_t* bundle) {
    if (bundle == NULL) {
        return;
    }
    
    if (bundle->context != NULL) {
        OSMesaDestroyContext_p(bundle->context);
        bundle->context = NULL;
    }
    
    if (bundle->nativeSurface != NULL) {
        if (!bundle->disable_rendering) {
            ANativeWindow_unlockAndPost(bundle->nativeSurface);
        }
        ANativeWindow_release(bundle->nativeSurface);
        bundle->nativeSurface = NULL;
    }
    
    if (bundle->newNativeSurface != NULL) {
        ANativeWindow_release(bundle->newNativeSurface);
        bundle->newNativeSurface = NULL;
    }
    
    free(bundle);
    
    if (currentBundle == bundle) {
        currentBundle = NULL;
    }
    
    if (g_global_context == bundle) {
        g_global_context = NULL;
    }
}

osm_render_window_t* osm_get_global_context(void) {
    return g_global_context;
}

bool osm_ensure_current(void) {
    // If we already have a current context on this thread, just verify it
    if (currentBundle != NULL) {
        // Context is already current, verify it's ready
        if (OSMesaGetCurrentContext_p != NULL) {
            OSMesaContext ctx = OSMesaGetCurrentContext_p();
            if (ctx != NULL) {
                LOGI("OSMesa context already current on this thread: %p", ctx);
                return true;
            }
        }
    }
    
    // Try to use the global context
    if (g_global_context == NULL) {
        LOGE("No OSMesa context available (g_global_context is NULL)");
        return false;
    }
    
    LOGI("Making OSMesa context current on this thread...");
    
    // Make the global context current on this thread
    osm_make_current(g_global_context);
    
    // CRITICAL: If we have a native surface ready, lock it and prepare for rendering NOW
    // This ensures FNA3D renders to the correct buffer from the very first frame
    if (currentBundle != NULL && currentBundle->newNativeSurface != NULL && !buffer_is_locked) {
        LOGI("Preparing native surface for first render...");
        osm_swap_surfaces(currentBundle);
        currentBundle->state = STATE_RENDERER_ALIVE;
        
        if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering) {
            int lock_result = ANativeWindow_lock(currentBundle->nativeSurface, &currentBundle->buffer, NULL);
            if (lock_result == 0) {
                osm_apply_current_ll(currentBundle);
                buffer_is_locked = true;
                
                // CRITICAL: Set initial viewport to full buffer size
                // FNA3D will query glGetIntegerv(GL_VIEWPORT) and use this for its backbuffer
                static void (*glViewport_init)(GLint, GLint, GLsizei, GLsizei) = NULL;
                if (glViewport_init == NULL && OSMesaGetProcAddress_p != NULL) {
                    glViewport_init = (void (*)(GLint, GLint, GLsizei, GLsizei))OSMesaGetProcAddress_p("glViewport");
                }
                if (glViewport_init != NULL) {
                    glViewport_init(0, 0, currentBundle->buffer.width, currentBundle->buffer.height);
                    LOGI("✓ Initial viewport set to %dx%d", 
                        currentBundle->buffer.width, currentBundle->buffer.height);
                }
                
                LOGI("✓ Native surface prepared for first render: %dx%d", 
                    currentBundle->buffer.width, currentBundle->buffer.height);
            } else {
                LOGW("Failed to lock native surface for first render: %d", lock_result);
            }
        }
    }
    
    // Verify it's now current
    if (OSMesaGetCurrentContext_p != NULL) {
        OSMesaContext ctx = OSMesaGetCurrentContext_p();
        if (ctx != NULL) {
            LOGI("✓ OSMesa context now current: %p", ctx);
            
            // Also verify OpenGL is functional
            if (glGetString_p != NULL) {
                const GLubyte* version = glGetString_p(GL_VERSION);
                if (version != NULL) {
                    LOGI("✓ OpenGL is functional, version: %s", version);
                    return true;
                } else {
                    LOGW("⚠ OSMesa context current but glGetString(GL_VERSION) returned NULL");
                }
            }
            return true;
        }
    }
    
    LOGE("Failed to make OSMesa context current");
    return false;
}

