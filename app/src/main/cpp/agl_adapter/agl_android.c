//
// AGL (AmigaOS GL) 适配层 - 让 SDL 可以通过 aglGetProcAddress 调用 MobileGL
// MobileGL 使用标准的 C 导出符号，我们可以通过 dlsym 查找函数
//

#include "agl_android.h"

#include <android/log.h>
#include <android/native_window.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "AGL-Adapter"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

struct AGLContext {
    EGLDisplay display;
    EGLSurface surface;
    EGLContext context;
    ANativeWindow* window;
    EGLConfig config;
    EGLint depth_size;
    EGLint stencil_size;
    EGLint vsync;
};

static int mobilegl_initialized = 0;
static struct AGLContext* g_current_context = NULL;

static void initialize_mobilegl(void) {
    if (mobilegl_initialized) {
        return;
    }

    typedef void (*MG_Initialize_t)(void);
    MG_Initialize_t mg_init = (MG_Initialize_t)dlsym(RTLD_DEFAULT, "_ZN8MobileGL14MG_InitializeEv");
    if (mg_init) {
        mg_init();
        LOGI("MobileGL manually initialized via MG_Initialize()");
    } else {
        LOGI("MobileGL should be auto-initialized (constructor)");
    }

    mobilegl_initialized = 1;
    LOGI("MobileGL AGL adapter initialized");
}

__attribute__((visibility("default")))
void* aglGetProcAddress(const char* name) {
    if (!name) {
        return NULL;
    }

    initialize_mobilegl();

    void* func = dlsym(RTLD_DEFAULT, name);
    if (!func && name[0] != '\0' && !(name[0] == 'g' && name[1] == 'l')) {
        char gl_name[256];
        snprintf(gl_name, sizeof(gl_name), "gl%s", name);
        func = dlsym(RTLD_DEFAULT, gl_name);
    }

    if (!func) {
        func = eglGetProcAddress(name);
    }

    if (func) {
        LOGI("Found function: %s at %p", name, func);
    } else {
        static int warn_count = 0;
        if (warn_count < 10) {
            LOGW("Function not found: %s", name);
            warn_count++;
        }
    }

    return func;
}

__attribute__((visibility("default")))
int aglInit(void) {
    initialize_mobilegl();
    return 1;
}

static struct AGLContext* agl_context_create(const struct TagItem* tags, unsigned long* errcode) {
    ANativeWindow* window = NULL;
    EGLint depth = 24;
    EGLint stencil = 8;
    EGLint vsync = 0;
    int contextMajor = 3;
    int contextMinor = 0;

    for (const struct TagItem* item = tags; item && item->ti_Tag != TAG_DONE; ++item) {
        switch (item->ti_Tag) {
            case GL4ES_CCT_WINDOW:
                window = (ANativeWindow*)item->ti_Data;
                break;
            case GL4ES_CCT_DEPTH:
                depth = (EGLint)item->ti_Data;
                break;
            case GL4ES_CCT_STENCIL:
                stencil = (EGLint)item->ti_Data;
                break;
            case GL4ES_CCT_VSYNC:
                vsync = (EGLint)item->ti_Data;
                break;
            default:
                break;
        }
    }

    const char* envMajor = getenv("MOBILEGL_EGL_CONTEXT_MAJOR");
    if (envMajor && *envMajor) {
        contextMajor = atoi(envMajor);
    }
    const char* envMinor = getenv("MOBILEGL_EGL_CONTEXT_MINOR");
    if (envMinor && *envMinor) {
        contextMinor = atoi(envMinor);
    }

    LOGI("agl_context_create: request EGL context %d.%d (depth=%d, stencil=%d, vsync=%d)",
         contextMajor, contextMinor, depth, stencil, vsync);

    if (!window) {
        LOGE("aglCreateContext2: window is NULL");
        if (errcode) *errcode = 1;
        return NULL;
    }

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        if (errcode) *errcode = 2;
        return NULL;
    }

    if (!eglInitialize(display, NULL, NULL)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        if (errcode) *errcode = 3;
        return NULL;
    }

    EGLint config_attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, depth,
        EGL_STENCIL_SIZE, stencil,
        EGL_NONE
    };

    EGLConfig config = NULL;
    EGLint num_config = 0;
    if (!eglChooseConfig(display, config_attribs, &config, 1, &num_config) || num_config == 0) {
        LOGE("eglChooseConfig failed: 0x%x", eglGetError());
        eglTerminate(display);
        if (errcode) *errcode = 4;
        return NULL;
    }

    EGLint native_format = 0;
    if (eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &native_format)) {
        ANativeWindow_setBuffersGeometry(window, 0, 0, native_format);
    } else {
        LOGW("eglGetConfigAttrib(EGL_NATIVE_VISUAL_ID) failed: 0x%x", eglGetError());
    }

    EGLSurface surface = eglCreateWindowSurface(display, config, window, NULL);
    if (surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        eglTerminate(display);
        if (errcode) *errcode = 5;
        return NULL;
    }

    EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, contextMajor,
        EGL_NONE
    };

    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attribs);
    if (context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        eglDestroySurface(display, surface);
        eglTerminate(display);
        if (errcode) *errcode = 6;
        return NULL;
    }

    if (!eglMakeCurrent(display, surface, surface, context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        eglDestroyContext(display, context);
        eglDestroySurface(display, surface);
        eglTerminate(display);
        if (errcode) *errcode = 7;
        return NULL;
    }

    if (vsync >= 0) {
        eglSwapInterval(display, vsync ? 1 : 0);
    }

    struct AGLContext* ctx = (struct AGLContext*)calloc(1, sizeof(struct AGLContext));
    if (!ctx) {
        LOGE("aglCreateContext2: out of memory");
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(display, context);
        eglDestroySurface(display, surface);
        eglTerminate(display);
        if (errcode) *errcode = 8;
        return NULL;
    }

    ctx->display = display;
    ctx->surface = surface;
    ctx->context = context;
    ctx->window = window;
    ctx->config = config;
    ctx->depth_size = depth;
    ctx->stencil_size = stencil;
    ctx->vsync = vsync;

    if (errcode) *errcode = 0;
    return ctx;
}

static void agl_context_destroy(struct AGLContext* ctx) {
    if (!ctx) return;

    if (ctx->display != EGL_NO_DISPLAY) {
        eglMakeCurrent(ctx->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (ctx->context != EGL_NO_CONTEXT) {
            eglDestroyContext(ctx->display, ctx->context);
        }
        if (ctx->surface != EGL_NO_SURFACE) {
            eglDestroySurface(ctx->display, ctx->surface);
        }
        eglTerminate(ctx->display);
    }

    free(ctx);
}

__attribute__((visibility("default")))
void* aglCreateContext2(unsigned long* errcode, const struct TagItem* tags) {
    initialize_mobilegl();
    struct AGLContext* ctx = agl_context_create(tags, errcode);
    if (ctx) {
        g_current_context = ctx;
        LOGI("aglCreateContext2 succeeded: ctx=%p", (void*)ctx);
    } else {
        LOGE("aglCreateContext2 failed");
    }
    return ctx;
}

__attribute__((visibility("default")))
void aglDestroyContext(void* context) {
    struct AGLContext* ctx = (struct AGLContext*)context;
    if (!ctx) return;

    LOGI("aglDestroyContext: ctx=%p", context);
    if (g_current_context == ctx) {
        g_current_context = NULL;
    }
    agl_context_destroy(ctx);
}

__attribute__((visibility("default")))
int aglMakeCurrent(void* context) {
    if (!context) {
        if (g_current_context && g_current_context->display != EGL_NO_DISPLAY) {
            eglMakeCurrent(g_current_context->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        }
        g_current_context = NULL;
        return 1;
    }

    struct AGLContext* ctx = (struct AGLContext*)context;
    if (!eglMakeCurrent(ctx->display, ctx->surface, ctx->surface, ctx->context)) {
        LOGE("aglMakeCurrent: eglMakeCurrent failed: 0x%x", eglGetError());
        return 0;
    }

    g_current_context = ctx;
    if (ctx->vsync >= 0) {
        eglSwapInterval(ctx->display, ctx->vsync ? 1 : 0);
    }
    return 1;
}

__attribute__((visibility("default")))
void aglSwapBuffers(void) {
    if (!g_current_context) {
        LOGW("aglSwapBuffers called without current context");
        return;
    }
    
    // 检查 Surface 是否仍然有效
    EGLint surface_valid = 0;
    if (eglQuerySurface(g_current_context->display, g_current_context->surface, EGL_WIDTH, &surface_valid) == EGL_FALSE) {
        EGLint error = eglGetError();
        if (error == EGL_BAD_SURFACE) {
            LOGW("aglSwapBuffers: Surface is invalid (0x%x), need to recreate", error);
            // Surface 已失效,不执行 swapBuffers
            // SDL 会在下一帧重新创建 Surface
            return;
        }
    }
    
    if (!eglSwapBuffers(g_current_context->display, g_current_context->surface)) {
        EGLint error = eglGetError();
        LOGE("aglSwapBuffers failed: 0x%x", error);
        
        // 如果是 BAD_SURFACE,标记需要重建
        if (error == EGL_BAD_SURFACE || error == EGL_BAD_NATIVE_WINDOW) {
            LOGW("Surface lost, will be recreated by SDL");
        }
    }
}

__attribute__((visibility("default")))
void aglQuit(void) {
    if (g_current_context) {
        agl_context_destroy(g_current_context);
        g_current_context = NULL;
    }
    mobilegl_initialized = 0;
}

