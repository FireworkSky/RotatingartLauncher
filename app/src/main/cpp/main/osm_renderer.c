/**
 * OSMesa Renderer Bridge Implementation
 *
 * Provides OpenGL rendering via Mesa OSMesa + Zink (OpenGL over Vulkan).
 * Renders to an off-screen RGBA buffer, then copies it to ANativeWindow each frame.
 *
 * Architecture:
 *   Game -> FNA3D -> OpenGL calls -> OSMesa (Mesa + Zink) -> Vulkan -> Turnip -> GPU
 *                                                                  -> ANativeWindow (display)
 */

#include "osm_renderer.h"

#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <errno.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <android/native_window.h>
#include "logging/app_log.h"

#define LOG_TAG "OSMRenderer"

/* ==================== Turnip Dependency Stubs ==================== */
/*
 * Turnip (Mesa's Adreno Vulkan driver) normally links against libhardware.so
 * and libsync.so, which are not accessible from the app namespace on Android 7+.
 * We remove those NEEDED deps with patchelf and provide our own implementations
 * here. These symbols become globally visible via RTLD_GLOBAL promotion.
 */

/* hw_get_module stub - only used by Android HAL interface which we don't use */
__attribute__((visibility("default")))
int hw_get_module(const char *id, const void **module)
{
    (void)id;
    (void)module;
    return -ENOENT;
}

/* sync_wait - waits for an Android sync fence fd to signal */
struct sync_merge_data {
    char name[32];
    int  fd2;
    int  fence;
    unsigned int flags;
    unsigned int pad;
};
#define SYNC_IOC_MAGIC '>'
#define SYNC_IOC_MERGE _IOWR(SYNC_IOC_MAGIC, 3, struct sync_merge_data)

__attribute__((visibility("default")))
int sync_wait(int fd, int timeout)
{
    struct pollfd pfd = { .fd = fd, .events = POLLIN };
    int ret;
    do {
        ret = poll(&pfd, 1, timeout);
    } while (ret == -1 && errno == EINTR);
    if (ret > 0) {
        if (pfd.revents & (POLLERR | POLLNVAL)) {
            errno = EINVAL;
            return -1;
        }
        return 0;
    }
    if (ret == 0) {
        errno = ETIME;
        return -1;
    }
    return ret;
}

/* sync_merge - merges two sync fence fds into one */
__attribute__((visibility("default")))
int sync_merge(const char *name, int fd1, int fd2)
{
    struct sync_merge_data data;
    memset(&data, 0, sizeof(data));
    if (name) strncpy(data.name, name, 31);
    data.fd2 = fd2;
    int ret = ioctl(fd1, SYNC_IOC_MERGE, &data);
    if (ret < 0) return -1;
    return data.fence;
}

/* ==================== OSMesa Type Definitions ==================== */

typedef void* OSMesaContext;

/* OSMesa context format constants */
#define OSMESA_RGBA   0x1908
#define OSMESA_ROW_LENGTH 0x10
#define OSMESA_Y_UP   0x11

/* OSMesa function pointer types */
typedef OSMesaContext (*pfn_OSMesaCreateContextExt)(unsigned format, int depthBits,
                                                    int stencilBits, int accumBits,
                                                    OSMesaContext sharelist);
typedef void  (*pfn_OSMesaDestroyContext)(OSMesaContext ctx);
typedef int   (*pfn_OSMesaMakeCurrent)(OSMesaContext ctx, void *buffer,
                                        unsigned type, int width, int height);
typedef void  (*pfn_OSMesaPixelStore)(int pname, int value);
typedef void* (*pfn_OSMesaGetProcAddress)(const char *funcName);

/* ==================== State ==================== */

static struct {
    /* Library handle */
    void *lib_handle;

    /* Function pointers */
    pfn_OSMesaCreateContextExt  CreateContextExt;
    pfn_OSMesaDestroyContext    DestroyContext;
    pfn_OSMesaMakeCurrent       MakeCurrent;
    pfn_OSMesaPixelStore        PixelStore;
    pfn_OSMesaGetProcAddress    GetProcAddress;

    /* Context and buffer */
    OSMesaContext context;
    void *color_buffer;
    int width;
    int height;

    /* Native window */
    ANativeWindow *window;

    /* State flags */
    bool available;
    bool initialized;
    bool lib_checked;
} g_osm = {0};

/* ==================== Library Loading ==================== */

/**
 * Try to dlopen libOSMesa.so from various locations.
 */
static bool load_osmesa_library(void)
{
    if (g_osm.lib_handle) return true;

    /* Try paths in order of priority */
    const char *paths[] = {
        NULL,  /* Will be filled with FNA3D_OPENGL_LIBRARY if set */
        "libOSMesa.so",
        NULL
    };

    /* Check FNA3D_OPENGL_LIBRARY first */
    const char *fna3d_lib = getenv("FNA3D_OPENGL_LIBRARY");
    if (fna3d_lib && strstr(fna3d_lib, "osmesa") != NULL) {
        paths[0] = fna3d_lib;
    } else if (fna3d_lib && strstr(fna3d_lib, "OSMesa") != NULL) {
        paths[0] = fna3d_lib;
    }

    for (int i = 0; i < 3; i++) {
        if (!paths[i]) continue;
        LOGI(LOG_TAG, "Trying to load OSMesa: %s", paths[i]);
        g_osm.lib_handle = dlopen(paths[i], RTLD_NOW | RTLD_GLOBAL);
        if (g_osm.lib_handle) {
            LOGI(LOG_TAG, "Loaded OSMesa from: %s", paths[i]);
            break;
        }
        LOGW(LOG_TAG, "  dlopen failed: %s", dlerror());
    }

    if (!g_osm.lib_handle) {
        LOGE(LOG_TAG, "Failed to load libOSMesa.so from any path");
        return false;
    }

    /* Resolve function pointers */
    g_osm.CreateContextExt = (pfn_OSMesaCreateContextExt)dlsym(g_osm.lib_handle, "OSMesaCreateContextExt");
    g_osm.DestroyContext   = (pfn_OSMesaDestroyContext)dlsym(g_osm.lib_handle, "OSMesaDestroyContext");
    g_osm.MakeCurrent      = (pfn_OSMesaMakeCurrent)dlsym(g_osm.lib_handle, "OSMesaMakeCurrent");
    g_osm.PixelStore       = (pfn_OSMesaPixelStore)dlsym(g_osm.lib_handle, "OSMesaPixelStore");
    g_osm.GetProcAddress   = (pfn_OSMesaGetProcAddress)dlsym(g_osm.lib_handle, "OSMesaGetProcAddress");

    if (!g_osm.CreateContextExt || !g_osm.DestroyContext ||
        !g_osm.MakeCurrent || !g_osm.PixelStore) {
        LOGE(LOG_TAG, "Failed to resolve OSMesa function pointers:");
        LOGE(LOG_TAG, "  CreateContextExt=%p DestroyContext=%p MakeCurrent=%p PixelStore=%p",
             g_osm.CreateContextExt, g_osm.DestroyContext,
             g_osm.MakeCurrent, g_osm.PixelStore);
        dlclose(g_osm.lib_handle);
        g_osm.lib_handle = NULL;
        return false;
    }

    LOGI(LOG_TAG, "OSMesa function pointers resolved successfully");
    if (g_osm.GetProcAddress) {
        LOGI(LOG_TAG, "  OSMesaGetProcAddress available");
    }

    return true;
}

/* ==================== Public API ==================== */

bool osm_renderer_is_available(void)
{
    if (!g_osm.lib_checked) {
        g_osm.lib_checked = true;
        g_osm.available = load_osmesa_library();
    }
    return g_osm.available;
}

bool osm_renderer_is_initialized(void)
{
    return g_osm.initialized;
}

bool osm_renderer_init(ANativeWindow *window)
{
    if (!window) {
        LOGE(LOG_TAG, "osm_renderer_init: window is NULL");
        return false;
    }

    if (g_osm.initialized) {
        LOGW(LOG_TAG, "osm_renderer_init: already initialized, destroying old context");
        osm_renderer_destroy();
    }

    /* Load library if not already loaded */
    if (!osm_renderer_is_available()) {
        LOGE(LOG_TAG, "osm_renderer_init: OSMesa library not available");
        return false;
    }

    /* Pre-load Turnip (Mesa Vulkan driver for Adreno GPUs) with RTLD_GLOBAL.
     * Zink (inside libOSMesa.so) will dlopen("libvulkan_freedreno.so") to get
     * Vulkan entry points. We pre-load it here to ensure it's available and
     * its symbols are globally visible.
     *
     * Turnip depends on libsync.so and libhardware.so which are provided as
     * stub libraries in jniLibs (since system versions aren't accessible from
     * the app namespace on Android 7+).
     */
    void *turnip_handle = dlopen("libvulkan_freedreno.so", RTLD_NOW | RTLD_GLOBAL);
    if (turnip_handle) {
        LOGI(LOG_TAG, "Pre-loaded Turnip (libvulkan_freedreno.so) with RTLD_GLOBAL for Zink");
    } else {
        LOGW(LOG_TAG, "Failed to pre-load Turnip: %s", dlerror());
        /* Fall back to system Vulkan if Turnip is not available */
        void *vulkan_handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_GLOBAL);
        if (vulkan_handle) {
            LOGI(LOG_TAG, "Fallback: Pre-loaded system libvulkan.so");
        } else {
            LOGW(LOG_TAG, "Failed to load any Vulkan library: %s", dlerror());
        }
    }

    /* Get window dimensions */
    g_osm.width = ANativeWindow_getWidth(window);
    g_osm.height = ANativeWindow_getHeight(window);
    g_osm.window = window;

    LOGI(LOG_TAG, "Initializing OSMesa context: %dx%d", g_osm.width, g_osm.height);

    /* Create OSMesa context (RGBA, 24-bit depth, 8-bit stencil, no accum) */
    g_osm.context = g_osm.CreateContextExt(OSMESA_RGBA, 24, 8, 0, NULL);
    if (!g_osm.context) {
        LOGE(LOG_TAG, "Failed to create OSMesa context (Zink/Vulkan may be unavailable)");
        return false;
    }
    LOGI(LOG_TAG, "OSMesa context created");

    /* Allocate color buffer (RGBA, 4 bytes per pixel) */
    size_t buffer_size = (size_t)g_osm.width * g_osm.height * 4;
    g_osm.color_buffer = malloc(buffer_size);
    if (!g_osm.color_buffer) {
        LOGE(LOG_TAG, "Failed to allocate color buffer (%zu bytes)", buffer_size);
        g_osm.DestroyContext(g_osm.context);
        g_osm.context = NULL;
        return false;
    }
    memset(g_osm.color_buffer, 0, buffer_size);
    LOGI(LOG_TAG, "Color buffer allocated: %zu bytes", buffer_size);

    /* Make context current with our buffer */
    /* GL_UNSIGNED_BYTE = 0x1401 */
    if (!g_osm.MakeCurrent(g_osm.context, g_osm.color_buffer, 0x1401,
                            g_osm.width, g_osm.height)) {
        LOGE(LOG_TAG, "OSMesaMakeCurrent failed");
        free(g_osm.color_buffer);
        g_osm.color_buffer = NULL;
        g_osm.DestroyContext(g_osm.context);
        g_osm.context = NULL;
        return false;
    }

    /* Set Y orientation (Y_UP = 0 means top-left origin, matching Android) */
    g_osm.PixelStore(OSMESA_Y_UP, 0);

    /* Set native window buffer format (RGBA_8888 = 1) */
    ANativeWindow_setBuffersGeometry(window, g_osm.width, g_osm.height,
                                     WINDOW_FORMAT_RGBA_8888);

    g_osm.initialized = true;
    LOGI(LOG_TAG, "OSMesa renderer initialized successfully (%dx%d)", g_osm.width, g_osm.height);

    return true;
}

void osm_swap_buffers(void)
{
    if (!g_osm.initialized || !g_osm.window || !g_osm.color_buffer) {
        return;
    }

    /* Check if window size changed */
    int cur_w = ANativeWindow_getWidth(g_osm.window);
    int cur_h = ANativeWindow_getHeight(g_osm.window);

    if (cur_w != g_osm.width || cur_h != g_osm.height) {
        LOGI(LOG_TAG, "Window resized: %dx%d -> %dx%d, recreating buffer",
             g_osm.width, g_osm.height, cur_w, cur_h);

        g_osm.width = cur_w;
        g_osm.height = cur_h;

        /* Reallocate buffer */
        size_t buffer_size = (size_t)g_osm.width * g_osm.height * 4;
        void *new_buffer = realloc(g_osm.color_buffer, buffer_size);
        if (!new_buffer) {
            LOGE(LOG_TAG, "Failed to reallocate color buffer");
            return;
        }
        g_osm.color_buffer = new_buffer;

        /* Re-make context current with new buffer */
        g_osm.MakeCurrent(g_osm.context, g_osm.color_buffer, 0x1401,
                           g_osm.width, g_osm.height);

        ANativeWindow_setBuffersGeometry(g_osm.window, g_osm.width, g_osm.height,
                                         WINDOW_FORMAT_RGBA_8888);
    }

    /* Lock the native window buffer */
    ANativeWindow_Buffer native_buffer;
    if (ANativeWindow_lock(g_osm.window, &native_buffer, NULL) != 0) {
        LOGE(LOG_TAG, "ANativeWindow_lock failed");
        return;
    }

    /* Copy OSMesa render buffer -> native window buffer */
    const uint8_t *src = (const uint8_t *)g_osm.color_buffer;
    uint8_t *dst = (uint8_t *)native_buffer.bits;
    int src_stride = g_osm.width * 4;
    int dst_stride = native_buffer.stride * 4;  /* stride is in pixels */
    int copy_width = (g_osm.width < native_buffer.width ? g_osm.width : native_buffer.width) * 4;
    int copy_height = g_osm.height < native_buffer.height ? g_osm.height : native_buffer.height;

    for (int y = 0; y < copy_height; y++) {
        memcpy(dst + y * dst_stride, src + y * src_stride, copy_width);
    }

    /* Unlock and post to display */
    ANativeWindow_unlockAndPost(g_osm.window);
}

void osm_renderer_destroy(void)
{
    LOGI(LOG_TAG, "Destroying OSMesa renderer");

    if (g_osm.context && g_osm.DestroyContext) {
        g_osm.DestroyContext(g_osm.context);
        g_osm.context = NULL;
    }

    if (g_osm.color_buffer) {
        free(g_osm.color_buffer);
        g_osm.color_buffer = NULL;
    }

    g_osm.window = NULL;
    g_osm.width = 0;
    g_osm.height = 0;
    g_osm.initialized = false;

    LOGI(LOG_TAG, "OSMesa renderer destroyed");
}
