/*
  Simple DirectMedia Layer
  Copyright (C) 1997-2024 Sam Lantinga <slouken@libsdl.org>

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.
*/

#include "../../SDL_internal.h"

#if SDL_VIDEO_DRIVER_ANDROID && defined(SDL_VIDEO_OPENGL) && defined(SDL_VIDEO_OPENGL_GL4ES)

#include "SDL_androidvideo.h"
#include "SDL_androidwindow.h"
#include "SDL_androidgl.h"
#include "../SDL_sysvideo.h"

#include <android/log.h>
#include <android/native_window.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <EGL/egl.h>

#define LOG_TAG "SDL_GL4ES_EGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ===== EGL Function Pointers =====
 * 动态加载EGL函数，支持多种EGL实现（系统原生、gl4es等）
 */
static EGLBoolean (*eglMakeCurrent_p)(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) = NULL;
static EGLBoolean (*eglDestroyContext_p)(EGLDisplay dpy, EGLContext ctx) = NULL;
static EGLBoolean (*eglDestroySurface_p)(EGLDisplay dpy, EGLSurface surface) = NULL;
static EGLBoolean (*eglTerminate_p)(EGLDisplay dpy) = NULL;
static EGLBoolean (*eglReleaseThread_p)(void) = NULL;
static EGLContext (*eglGetCurrentContext_p)(void) = NULL;
static EGLDisplay (*eglGetDisplay_p)(NativeDisplayType display) = NULL;
static EGLBoolean (*eglInitialize_p)(EGLDisplay dpy, EGLint *major, EGLint *minor) = NULL;
static EGLBoolean (*eglChooseConfig_p)(EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config) = NULL;
static EGLBoolean (*eglGetConfigAttrib_p)(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value) = NULL;
static EGLBoolean (*eglBindAPI_p)(EGLenum api) = NULL;
static EGLSurface (*eglCreatePbufferSurface_p)(EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list) = NULL;
static EGLSurface (*eglCreateWindowSurface_p)(EGLDisplay dpy, EGLConfig config, NativeWindowType window, const EGLint *attrib_list) = NULL;
static EGLBoolean (*eglSwapBuffers_p)(EGLDisplay dpy, EGLSurface draw) = NULL;
static EGLint (*eglGetError_p)(void) = NULL;
static EGLContext (*eglCreateContext_p)(EGLDisplay dpy, EGLConfig config, EGLContext share_list, const EGLint *attrib_list) = NULL;
static EGLBoolean (*eglSwapInterval_p)(EGLDisplay dpy, EGLint interval) = NULL;
static EGLSurface (*eglGetCurrentSurface_p)(EGLint readdraw) = NULL;
static EGLBoolean (*eglQuerySurface_p)(EGLDisplay display, EGLSurface surface, EGLint attribute, EGLint *value) = NULL;
static __eglMustCastToProperFunctionPointerType (*eglGetProcAddress_p)(const char *procname) = NULL;

/* ===== EGL Context Management =====
 * 存储EGL上下文、显示和配置信息
 */
typedef struct {
    EGLContext context;
    EGLSurface surface;
    EGLConfig config;
    EGLint format;
    ANativeWindow* native_window;
} SDL_EGLContext;

static EGLDisplay g_egl_display = EGL_NO_DISPLAY;
static SDL_EGLContext* g_current_context = NULL;
static void* g_egl_library = NULL;

/* ===== EGL Loader =====
 * 动态加载EGL库和函数指针
 * 支持从环境变量 FNA3D_OPENGL_LIBRARY 指定EGL库路径
 */
static int load_egl_library(void)
{
    const char* egl_lib_path = getenv("FNA3D_OPENGL_LIBRARY");
    if (!egl_lib_path || egl_lib_path[0] == '\0') {
        egl_lib_path = "libEGL.so";
    }

    LOGI("Loading EGL library: %s", egl_lib_path);
    g_egl_library = dlopen(egl_lib_path, RTLD_LOCAL | RTLD_LAZY);

    if (!g_egl_library) {
        LOGE("Failed to load EGL library: %s", dlerror());
        return -1;
    }

    /* 加载eglGetProcAddress，其他函数通过它获取 */
    eglGetProcAddress_p = dlsym(g_egl_library, "eglGetProcAddress");
    if (!eglGetProcAddress_p) {
        LOGE("Failed to load eglGetProcAddress: %s", dlerror());
        dlclose(g_egl_library);
        g_egl_library = NULL;
        return -1;
    }

    /* 通过eglGetProcAddress加载所有EGL函数 */
    #define LOAD_EGL_FUNC(name) \
        name##_p = (void*)eglGetProcAddress_p(#name); \
        if (!name##_p) { \
            LOGE("Failed to load " #name); \
            dlclose(g_egl_library); \
            g_egl_library = NULL; \
            return -1; \
        }

    LOAD_EGL_FUNC(eglBindAPI)
    LOAD_EGL_FUNC(eglChooseConfig)
    LOAD_EGL_FUNC(eglCreateContext)
    LOAD_EGL_FUNC(eglCreatePbufferSurface)
    LOAD_EGL_FUNC(eglCreateWindowSurface)
    LOAD_EGL_FUNC(eglDestroyContext)
    LOAD_EGL_FUNC(eglDestroySurface)
    LOAD_EGL_FUNC(eglGetConfigAttrib)
    LOAD_EGL_FUNC(eglGetCurrentContext)
    LOAD_EGL_FUNC(eglGetDisplay)
    LOAD_EGL_FUNC(eglGetError)
    LOAD_EGL_FUNC(eglInitialize)
    LOAD_EGL_FUNC(eglMakeCurrent)
    LOAD_EGL_FUNC(eglSwapBuffers)
    LOAD_EGL_FUNC(eglReleaseThread)
    LOAD_EGL_FUNC(eglSwapInterval)
    LOAD_EGL_FUNC(eglTerminate)
    LOAD_EGL_FUNC(eglGetCurrentSurface)
    LOAD_EGL_FUNC(eglQuerySurface)

    #undef LOAD_EGL_FUNC

    LOGI("✅ EGL library loaded successfully");
    return 0;
}

int
Android_GL4ES_LoadLibrary(_THIS, const char* path)
{
    LOGI("🔵 Android_GL4ES_LoadLibrary called (EGL backend)");
    LOGI("   path=%s, _this=%p", path ? path : "(null)", _this);

    /* 加载EGL函数 */
    if (load_egl_library() < 0) {
        SDL_SetError("Failed to load EGL library");
        return -1;
    }

    /* 初始化EGL Display */
    g_egl_display = eglGetDisplay_p(EGL_DEFAULT_DISPLAY);
    if (g_egl_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay(EGL_DEFAULT_DISPLAY) returned EGL_NO_DISPLAY");
        SDL_SetError("eglGetDisplay failed");
        return -1;
    }

    if (eglInitialize_p(g_egl_display, NULL, NULL) != EGL_TRUE) {
        LOGE("eglInitialize() failed: 0x%04x", eglGetError_p());
        SDL_SetError("eglInitialize failed");
        return -1;
    }

    LOGI("✅ EGL initialized successfully (display=%p)", g_egl_display);
    return 0;
}

void*
Android_GL4ES_GetProcAddress(_THIS, const char* proc)
{
    if (!proc) {
        LOGE("GetProcAddress: proc is NULL");
        return NULL;
    }

    void* func = NULL;
    if (eglGetProcAddress_p) {
        func = (void*)eglGetProcAddress_p(proc);
    }

    if (!func) {
        /* OpenGL 扩展函数返回 NULL 是正常的（不是所有驱动都支持所有扩展）
         * 不要设置 SDL_SetError，这样游戏可以正确处理扩展不可用的情况 */
        LOGI("GetProcAddress: '%s' not found (extension may not be available)", proc);
    }

    return func;
}

void
Android_GL4ES_UnloadLibrary(_THIS)
{
    LOGI("Android_GL4ES_UnloadLibrary called");

    if (g_egl_display != EGL_NO_DISPLAY) {
        if (eglTerminate_p) {
            eglTerminate_p(g_egl_display);
        }
        g_egl_display = EGL_NO_DISPLAY;
    }

    if (g_egl_library) {
        dlclose(g_egl_library);
        g_egl_library = NULL;
    }

    /* 清空所有函数指针 */
    eglMakeCurrent_p = NULL;
    eglDestroyContext_p = NULL;
    eglDestroySurface_p = NULL;
    eglTerminate_p = NULL;
    eglReleaseThread_p = NULL;
    eglGetCurrentContext_p = NULL;
    eglGetDisplay_p = NULL;
    eglInitialize_p = NULL;
    eglChooseConfig_p = NULL;
    eglGetConfigAttrib_p = NULL;
    eglBindAPI_p = NULL;
    eglCreatePbufferSurface_p = NULL;
    eglCreateWindowSurface_p = NULL;
    eglSwapBuffers_p = NULL;
    eglGetError_p = NULL;
    eglCreateContext_p = NULL;
    eglSwapInterval_p = NULL;
    eglGetCurrentSurface_p = NULL;
    eglQuerySurface_p = NULL;
    eglGetProcAddress_p = NULL;

    LOGI("✅ EGL library unloaded");
}

SDL_GLContext
Android_GL4ES_CreateContext(_THIS, SDL_Window* window)
{
    LOGI("🎯 Android_GL4ES_CreateContext called for window '%s'", window ? window->title : "NULL");

    SDL_WindowData* data = (SDL_WindowData*)window->driverdata;
    if (!data || !data->native_window) {
        LOGE("Window has no driver data or native window");
        SDL_SetError("Window has no native window");
        return NULL;
    }

    /* 分配EGL上下文结构 */
    SDL_EGLContext* egl_ctx = (SDL_EGLContext*)SDL_calloc(1, sizeof(SDL_EGLContext));
    if (!egl_ctx) {
        SDL_SetError("Out of memory");
        return NULL;
    }

    /* EGL配置属性 */
    const EGLint egl_attribs[] = {
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, _this->gl_config.depth_size,
        EGL_STENCIL_SIZE, _this->gl_config.stencil_size,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_NONE
    };

    EGLint num_configs = 0;
    if (eglChooseConfig_p(g_egl_display, egl_attribs, NULL, 0, &num_configs) != EGL_TRUE) {
        LOGE("eglChooseConfig failed: 0x%04x", eglGetError_p());
        SDL_free(egl_ctx);
        SDL_SetError("eglChooseConfig failed");
        return NULL;
    }

    if (num_configs == 0) {
        LOGE("No matching EGL config found");
        SDL_free(egl_ctx);
        SDL_SetError("No matching EGL config");
        return NULL;
    }

    /* 选择第一个匹配的配置 */
    eglChooseConfig_p(g_egl_display, egl_attribs, &egl_ctx->config, 1, &num_configs);
    eglGetConfigAttrib_p(g_egl_display, egl_ctx->config, EGL_NATIVE_VISUAL_ID, &egl_ctx->format);

    /* 检查环境变量决定绑定OpenGL ES还是Desktop OpenGL */
    const char* renderer = getenv("FNA3D_OPENGL_DRIVER");
    EGLBoolean bind_result;

    if (renderer && strncmp(renderer, "desktop", 7) == 0) {
        LOGI("Binding to Desktop OpenGL API");
        bind_result = eglBindAPI_p(EGL_OPENGL_API);
    } else {
        LOGI("Binding to OpenGL ES API");
        bind_result = eglBindAPI_p(EGL_OPENGL_ES_API);
    }

    if (!bind_result) {
        LOGE("eglBindAPI failed: 0x%04x", eglGetError_p());
    }

    /* 从环境变量获取OpenGL ES版本 */
    const char* libgl_es_str = getenv("LIBGL_ES");
    int libgl_es = 2; /* 默认ES 2.0 */
    if (libgl_es_str) {
        libgl_es = atoi(libgl_es_str);
        if (libgl_es < 1 || libgl_es > 3) {
            libgl_es = 2;
        }
    }
    LOGI("Creating OpenGL ES %d context", libgl_es);

    const EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, libgl_es,
        EGL_NONE
    };

    egl_ctx->context = eglCreateContext_p(g_egl_display, egl_ctx->config,
                                          EGL_NO_CONTEXT, context_attribs);

    if (egl_ctx->context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%04x", eglGetError_p());
        SDL_free(egl_ctx);
        SDL_SetError("eglCreateContext failed");
        return NULL;
    }

    /* 创建窗口表面 */
    ANativeWindow_acquire(data->native_window);
    ANativeWindow_setBuffersGeometry(data->native_window, 0, 0, egl_ctx->format);

    egl_ctx->surface = eglCreateWindowSurface_p(g_egl_display, egl_ctx->config,
                                                 data->native_window, NULL);
    if (egl_ctx->surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%04x", eglGetError_p());
        eglDestroyContext_p(g_egl_display, egl_ctx->context);
        ANativeWindow_release(data->native_window);
        SDL_free(egl_ctx);
        SDL_SetError("eglCreateWindowSurface failed");
        return NULL;
    }

    egl_ctx->native_window = data->native_window;

    /* 激活上下文 */
    if (eglMakeCurrent_p(g_egl_display, egl_ctx->surface, egl_ctx->surface,
                         egl_ctx->context) != EGL_TRUE) {
        LOGE("eglMakeCurrent failed: 0x%04x", eglGetError_p());
        eglDestroySurface_p(g_egl_display, egl_ctx->surface);
        eglDestroyContext_p(g_egl_display, egl_ctx->context);
        ANativeWindow_release(data->native_window);
        SDL_free(egl_ctx);
        SDL_SetError("eglMakeCurrent failed");
        return NULL;
    }

    g_current_context = egl_ctx;

    LOGI("✅ EGL context created successfully (context=%p, surface=%p)",
         egl_ctx->context, egl_ctx->surface);

    return (SDL_GLContext)egl_ctx;
}

int
Android_GL4ES_MakeCurrent(_THIS, SDL_Window* window, SDL_GLContext context)
{
    SDL_EGLContext* egl_ctx = (SDL_EGLContext*)context;

    if (!window || !context) {
        /* 解绑当前上下文 */
        if (eglMakeCurrent_p(g_egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE,
                             EGL_NO_CONTEXT) == EGL_TRUE) {
            g_current_context = NULL;
            LOGI("Unbound current context");
            return 0;
        } else {
            LOGE("Failed to unbind context: 0x%04x", eglGetError_p());
            return -1;
        }
    }

    if (eglMakeCurrent_p(g_egl_display, egl_ctx->surface, egl_ctx->surface,
                         egl_ctx->context) == EGL_TRUE) {
        g_current_context = egl_ctx;
        return 0;
    } else {
        LOGE("eglMakeCurrent failed: 0x%04x", eglGetError_p());
        SDL_SetError("eglMakeCurrent failed");
        return -1;
    }
}

int
Android_GL4ES_SwapWindow(_THIS, SDL_Window* window)
{
    if (!g_current_context || !g_current_context->surface) {
        LOGE("No current EGL context or surface");
        return -1;
    }

    if (eglSwapBuffers_p(g_egl_display, g_current_context->surface) != EGL_TRUE) {
        EGLint error = eglGetError_p();
        if (error == EGL_BAD_SURFACE) {
            LOGE("eglSwapBuffers: Bad surface, recreating...");
            /* 表面可能已失效，尝试重新创建 */
            /* 这里可以添加表面重新创建逻辑 */
        }
        LOGE("eglSwapBuffers failed: 0x%04x", error);
        return -1;
    }

    return 0;
}

void
Android_GL4ES_DeleteContext(_THIS, SDL_GLContext context)
{
    SDL_EGLContext* egl_ctx = (SDL_EGLContext*)context;

    if (!egl_ctx) {
        LOGI("DeleteContext: context is NULL");
        return;
    }

    LOGI("Deleting EGL context %p", egl_ctx);

    /* 如果是当前上下文，先解绑 */
    if (g_current_context == egl_ctx) {
        eglMakeCurrent_p(g_egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        g_current_context = NULL;
    }

    /* 销毁表面 */
    if (egl_ctx->surface != EGL_NO_SURFACE) {
        eglDestroySurface_p(g_egl_display, egl_ctx->surface);
    }

    /* 销毁上下文 */
    if (egl_ctx->context != EGL_NO_CONTEXT) {
        eglDestroyContext_p(g_egl_display, egl_ctx->context);
    }

    /* 释放Native Window */
    if (egl_ctx->native_window) {
        ANativeWindow_release(egl_ctx->native_window);
    }

    SDL_free(egl_ctx);
    LOGI("✅ EGL context deleted");
}

void
Android_GL4ES_GetDrawableSize(_THIS, SDL_Window* window, int* w, int* h)
{
    if (!g_current_context || !g_current_context->surface) {
        /* 没有当前上下文，返回窗口大小 */
        if (w) *w = window->w;
        if (h) *h = window->h;
        return;
    }

    /* 从EGL Surface查询实际尺寸（最可靠的方式） */
    EGLint surface_width = 0, surface_height = 0;
    if (eglQuerySurface_p(g_egl_display, g_current_context->surface,
                          EGL_WIDTH, &surface_width) == EGL_TRUE &&
        eglQuerySurface_p(g_egl_display, g_current_context->surface,
                          EGL_HEIGHT, &surface_height) == EGL_TRUE) {
        if (w) *w = surface_width;
        if (h) *h = surface_height;
    } else {
        /* 查询失败，回退到窗口大小 */
        if (w) *w = window->w;
        if (h) *h = window->h;
    }
}

int
Android_GL4ES_SetSwapInterval(_THIS, int interval)
{
    LOGI("SetSwapInterval: %d", interval);

    /* 检查是否强制VSync（环境变量） */
    const char* force_vsync = getenv("FORCE_VSYNC");
    if (force_vsync && strcmp(force_vsync, "true") == 0) {
        interval = 1;
        LOGI("FORCE_VSYNC enabled, using interval=1");
    }

    if (eglSwapInterval_p(g_egl_display, interval) == EGL_TRUE) {
        return 0;
    } else {
        LOGE("eglSwapInterval failed: 0x%04x", eglGetError_p());
        return -1;
    }
}

int
Android_GL4ES_GetSwapInterval(_THIS)
{
    /* EGL没有标准的查询SwapInterval API，返回默认值 */
    return 1;
}

#endif /* SDL_VIDEO_DRIVER_ANDROID && SDL_VIDEO_OPENGL && SDL_VIDEO_OPENGL_GL4ES */

