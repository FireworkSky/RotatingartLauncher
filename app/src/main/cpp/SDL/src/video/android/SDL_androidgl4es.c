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
#include "../SDL_egl_c.h"
#include "../SDL_sysvideo.h"

#include <android/log.h>
#include <stdlib.h>
#include <dlfcn.h>

#define LOG_TAG "SDL_GL4ES"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/* EGL 类型定义 (避免静态链接 EGL 库，使用 GL4ES_ 前缀避免冲突) */
typedef void* GL4ES_EGLDisplay;
typedef void* GL4ES_EGLSurface;
typedef void* GL4ES_EGLContext;
typedef void* GL4ES_EGLConfig;
typedef void* GL4ES_NativeWindowType;
typedef unsigned int GL4ES_EGLBoolean;
typedef int GL4ES_EGLint;

#define GL4ES_EGL_NO_DISPLAY ((GL4ES_EGLDisplay)0)
#define GL4ES_EGL_NO_SURFACE ((GL4ES_EGLSurface)0)
#define GL4ES_EGL_NO_CONTEXT ((GL4ES_EGLContext)0)
#define GL4ES_EGL_DEFAULT_DISPLAY ((GL4ES_NativeWindowType)0)
#define EGL_FALSE 0
#define EGL_TRUE 1

/* EGL 配置属性 */
#define EGL_SURFACE_TYPE 0x3033
#define EGL_WINDOW_BIT 0x0004
#define EGL_RENDERABLE_TYPE 0x3040
#define EGL_OPENGL_ES2_BIT 0x0004
#define EGL_RED_SIZE 0x3024
#define EGL_GREEN_SIZE 0x3023
#define EGL_BLUE_SIZE 0x3022
#define EGL_ALPHA_SIZE 0x3021
#define EGL_DEPTH_SIZE 0x3025
#define EGL_STENCIL_SIZE 0x3026
#define EGL_NONE 0x3038
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_WIDTH 0x3057
#define EGL_HEIGHT 0x3056

/* EGL 函数类型定义 */
typedef GL4ES_EGLDisplay (*eglGetDisplay_t)(GL4ES_NativeWindowType);
typedef GL4ES_EGLBoolean (*eglInitialize_t)(GL4ES_EGLDisplay, GL4ES_EGLint*, GL4ES_EGLint*);
typedef GL4ES_EGLBoolean (*eglChooseConfig_t)(GL4ES_EGLDisplay, const GL4ES_EGLint*, GL4ES_EGLConfig*, GL4ES_EGLint, GL4ES_EGLint*);
typedef GL4ES_EGLSurface (*eglCreateWindowSurface_t)(GL4ES_EGLDisplay, GL4ES_EGLConfig, GL4ES_NativeWindowType, const GL4ES_EGLint*);
typedef GL4ES_EGLContext (*eglCreateContext_t)(GL4ES_EGLDisplay, GL4ES_EGLConfig, GL4ES_EGLContext, const GL4ES_EGLint*);
typedef GL4ES_EGLBoolean (*eglMakeCurrent_t)(GL4ES_EGLDisplay, GL4ES_EGLSurface, GL4ES_EGLSurface, GL4ES_EGLContext);
typedef GL4ES_EGLBoolean (*eglSwapBuffers_t)(GL4ES_EGLDisplay, GL4ES_EGLSurface);
typedef GL4ES_EGLBoolean (*eglDestroyContext_t)(GL4ES_EGLDisplay, GL4ES_EGLContext);
typedef GL4ES_EGLBoolean (*eglDestroySurface_t)(GL4ES_EGLDisplay, GL4ES_EGLSurface);
typedef GL4ES_EGLBoolean (*eglTerminate_t)(GL4ES_EGLDisplay);
typedef GL4ES_EGLBoolean (*eglQuerySurface_t)(GL4ES_EGLDisplay, GL4ES_EGLSurface, GL4ES_EGLint, GL4ES_EGLint*);
typedef GL4ES_EGLBoolean (*eglSwapInterval_t)(GL4ES_EGLDisplay, GL4ES_EGLint);
typedef GL4ES_EGLint(*eglGetError_t)(void);
typedef void* (*eglGetProcAddress_t)(const char*);

/* EGL 函数指针 (动态加载) */
static void* g_egl_handle = NULL;
static eglGetDisplay_t p_eglGetDisplay = NULL;
static eglInitialize_t p_eglInitialize = NULL;
static eglChooseConfig_t p_eglChooseConfig = NULL;
static eglCreateWindowSurface_t p_eglCreateWindowSurface = NULL;
static eglCreateContext_t p_eglCreateContext = NULL;
static eglMakeCurrent_t p_eglMakeCurrent = NULL;
static eglSwapBuffers_t p_eglSwapBuffers = NULL;
static eglDestroyContext_t p_eglDestroyContext = NULL;
static eglDestroySurface_t p_eglDestroySurface = NULL;
static eglTerminate_t p_eglTerminate = NULL;
static eglQuerySurface_t p_eglQuerySurface = NULL;
static eglSwapInterval_t p_eglSwapInterval = NULL;
static eglGetError_t p_eglGetError = NULL;
static eglGetProcAddress_t p_eglGetProcAddress = NULL;

/*
 * gl4es 标准接口函数类型 (来自 gl4esinit.h)
 * - set_getprocaddress: 设置 EGL GetProcAddress 回调
 * - set_getmainfbsize: 设置获取帧缓冲区大小的回调
 * - gl4es_GetProcAddress: 获取被 gl4es 包装过的 GL 函数
 */
typedef void (*set_getprocaddress_t)(void *(*new_proc_address)(const char *));
typedef void (*set_getmainfbsize_t)(void (*new_getMainFBSize)(int* width, int* height));
typedef void* (*gl4es_GetProcAddress_t)(const char *name);

/* gl4es 库句柄和函数指针 */
static void* g_gl4es_handle = NULL;
static set_getprocaddress_t p_set_getprocaddress = NULL;
static set_getmainfbsize_t p_set_getmainfbsize = NULL;
static gl4es_GetProcAddress_t p_gl4es_GetProcAddress = NULL;
static int g_gl4es_initialized = 0;

/* EGL context 状态 */
static GL4ES_EGLDisplay g_egl_display = NULL;
static GL4ES_EGLContext g_egl_context = NULL;
static GL4ES_EGLSurface g_egl_surface = NULL;
static GL4ES_EGLConfig g_egl_config = NULL;
static SDL_Window* g_current_window = NULL;

/* 当前窗口尺寸 (用于 gl4es 回调) */
static int g_window_width = 0;
static int g_window_height = 0;

/* 动态加载 EGL 库 */
static int load_egl_library(void)
{
    if (g_egl_handle != NULL) {
        return 0; /* Already loaded */
    }

    LOGI("🔵 Loading libEGL.so dynamically...");

    g_egl_handle = dlopen("libEGL.so", RTLD_NOW | RTLD_GLOBAL);
    if (!g_egl_handle) {
        LOGE("❌ Failed to load libEGL.so: %s", dlerror());
        return -1;
    }

    LOGI("✅ libEGL.so loaded at %p", g_egl_handle);

    /* 加载 EGL 函数 */
    #define LOAD_EGL(name) p_##name = (name##_t)dlsym(g_egl_handle, #name); \
        if (!p_##name) LOGE("   Warning: " #name " not found");

    LOAD_EGL(eglGetDisplay);
    LOAD_EGL(eglInitialize);
    LOAD_EGL(eglChooseConfig);
    LOAD_EGL(eglCreateWindowSurface);
    LOAD_EGL(eglCreateContext);
    LOAD_EGL(eglMakeCurrent);
    LOAD_EGL(eglSwapBuffers);
    LOAD_EGL(eglDestroyContext);
    LOAD_EGL(eglDestroySurface);
    LOAD_EGL(eglTerminate);
    LOAD_EGL(eglQuerySurface);
    LOAD_EGL(eglSwapInterval);
    LOAD_EGL(eglGetError);
    LOAD_EGL(eglGetProcAddress);

    #undef LOAD_EGL

    if (!p_eglGetDisplay || !p_eglInitialize || !p_eglCreateContext || !p_eglMakeCurrent) {
        LOGE("❌ Failed to load required EGL functions");
        dlclose(g_egl_handle);
        g_egl_handle = NULL;
        return -1;
    }

    LOGI("✅ All EGL functions loaded successfully");
    return 0;
}

/* gl4es 回调：获取帧缓冲区大小 */
static void gl4es_getMainFBSize(int* width, int* height)
{
    if (width) *width = g_window_width;
    if (height) *height = g_window_height;
    LOGD("gl4es_getMainFBSize: %dx%d", g_window_width, g_window_height);
}

/* gl4es 回调：获取 EGL 函数地址 */
static void* gl4es_eglGetProcAddress(const char* name)
{
    void* proc = NULL;

    if (p_eglGetProcAddress) {
        proc = p_eglGetProcAddress(name);
    }

    if (!proc) {
        /* 尝试从 libGLESv2.so 获取 */
        static void* gles_handle = NULL;
        if (!gles_handle) {
            gles_handle = dlopen("libGLESv2.so", RTLD_NOW | RTLD_GLOBAL);
        }
        if (gles_handle) {
            proc = dlsym(gles_handle, name);
        }
    }
    return proc;
}

/* 动态加载 gl4es 库并获取函数指针 */
static int load_gl4es_library(void)
{
    if (g_gl4es_handle != NULL) {
        return 0; /* Already loaded */
    }

    LOGI("🔵 Loading libgl4es.so dynamically...");

    g_gl4es_handle = dlopen("libgl4es.so", RTLD_NOW | RTLD_GLOBAL);
    if (!g_gl4es_handle) {
        LOGE("❌ Failed to load libgl4es.so: %s", dlerror());
        return -1;
    }

    LOGI("✅ libgl4es.so loaded at %p", g_gl4es_handle);

    /* 加载标准 gl4es 接口函数 */
    p_set_getprocaddress = (set_getprocaddress_t)dlsym(g_gl4es_handle, "set_getprocaddress");
    p_set_getmainfbsize = (set_getmainfbsize_t)dlsym(g_gl4es_handle, "set_getmainfbsize");
    p_gl4es_GetProcAddress = (gl4es_GetProcAddress_t)dlsym(g_gl4es_handle, "gl4es_GetProcAddress");

    if (!p_gl4es_GetProcAddress) {
        LOGE("❌ Failed to load gl4es_GetProcAddress");
        dlclose(g_gl4es_handle);
        g_gl4es_handle = NULL;
        return -1;
    }

    LOGI("✅ gl4es functions loaded:");
    LOGI("   set_getprocaddress: %p", (void*)p_set_getprocaddress);
    LOGI("   set_getmainfbsize: %p", (void*)p_set_getmainfbsize);
    LOGI("   gl4es_GetProcAddress: %p", (void*)p_gl4es_GetProcAddress);

    return 0;
}

/* 初始化 gl4es */
static int initialize_gl4es(int width, int height)
{
    if (g_gl4es_initialized) {
        return 0;
    }

    LOGI("🎯 Initializing gl4es with size %dx%d", width, height);

    g_window_width = width;
    g_window_height = height;

    /* 设置 gl4es 回调 */
    if (p_set_getprocaddress) {
        LOGI("   Setting GetProcAddress callback");
        p_set_getprocaddress(gl4es_eglGetProcAddress);
    }

    if (p_set_getmainfbsize) {
        LOGI("   Setting GetMainFBSize callback");
        p_set_getmainfbsize(gl4es_getMainFBSize);
    }

    g_gl4es_initialized = 1;
    LOGI("✅ gl4es initialized successfully");

    return 0;
}

/* 创建 EGL context */
static int create_egl_context(SDL_VideoDevice* _this, SDL_Window* window)
{
    SDL_WindowData* data = (SDL_WindowData*)window->driverdata;
    if (!data || !data->native_window) {
        LOGE("❌ No native window");
        return -1;
    }

    /* 先加载 EGL 库 */
    if (load_egl_library() != 0) {
        LOGE("❌ Failed to load EGL library");
        return -1;
    }

    LOGI("🔵 Creating EGL context for gl4es...");

    /* 获取 EGL display */
    g_egl_display = p_eglGetDisplay(GL4ES_EGL_DEFAULT_DISPLAY);
    if (g_egl_display == GL4ES_EGL_NO_DISPLAY) {
        LOGE("❌ eglGetDisplay failed");
        return -1;
    }

    /* 初始化 EGL */
    GL4ES_EGLint major, minor;
    if (!p_eglInitialize(g_egl_display, &major, &minor)) {
        LOGE("❌ eglInitialize failed");
        return -1;
    }
    LOGI("   EGL version: %d.%d", major, minor);

    /* 选择 EGL 配置 - 请求 OpenGL ES 2.0 或 3.0 */
    GL4ES_EGLint config_attribs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, _this->gl_config.depth_size > 0 ? _this->gl_config.depth_size : 24,
        EGL_STENCIL_SIZE, _this->gl_config.stencil_size > 0 ? _this->gl_config.stencil_size : 8,
        EGL_NONE
    };

    GL4ES_EGLint num_configs;
    if (!p_eglChooseConfig(g_egl_display, config_attribs, &g_egl_config, 1, &num_configs) || num_configs == 0) {
        LOGE("❌ eglChooseConfig failed");
        return -1;
    }
    LOGI("   Found %d EGL configs", num_configs);

    /* 创建 EGL surface */
    g_egl_surface = p_eglCreateWindowSurface(g_egl_display, g_egl_config, (GL4ES_NativeWindowType)data->native_window, NULL);
    if (g_egl_surface == GL4ES_EGL_NO_SURFACE) {
        LOGE("❌ eglCreateWindowSurface failed: 0x%x", p_eglGetError ? p_eglGetError() : 0);
        return -1;
    }
    LOGI("   EGL surface created");

    /* 创建 OpenGL ES 2.0 context (gl4es 需要 ES2) */
    GL4ES_EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };

    g_egl_context = p_eglCreateContext(g_egl_display, g_egl_config, GL4ES_EGL_NO_CONTEXT, context_attribs);
    if (g_egl_context == GL4ES_EGL_NO_CONTEXT) {
        LOGE("❌ eglCreateContext failed: 0x%x", p_eglGetError ? p_eglGetError() : 0);
        p_eglDestroySurface(g_egl_display, g_egl_surface);
        g_egl_surface = GL4ES_EGL_NO_SURFACE;
        return -1;
    }
    LOGI("   EGL context created");

    /* Make context current */
    if (!p_eglMakeCurrent(g_egl_display, g_egl_surface, g_egl_surface, g_egl_context)) {
        LOGE("❌ eglMakeCurrent failed: 0x%x", p_eglGetError ? p_eglGetError() : 0);
        p_eglDestroyContext(g_egl_display, g_egl_context);
        p_eglDestroySurface(g_egl_display, g_egl_surface);
        g_egl_context = GL4ES_EGL_NO_CONTEXT;
        g_egl_surface = GL4ES_EGL_NO_SURFACE;
        return -1;
    }

    LOGI("✅ EGL context is current");

    /* 获取窗口尺寸 */
    GL4ES_EGLint width = 0, height = 0;
    if (p_eglQuerySurface) {
        p_eglQuerySurface(g_egl_display, g_egl_surface, EGL_WIDTH, &width);
        p_eglQuerySurface(g_egl_display, g_egl_surface, EGL_HEIGHT, &height);
    }
    LOGI("   Surface size: %dx%d", width, height);

    g_window_width = width;
    g_window_height = height;

    return 0;
}

int
Android_GL4ES_LoadLibrary(_THIS, const char* path)
{
    LOGI("🔵 Android_GL4ES_LoadLibrary called");
    LOGI("   path=%s, _this=%p", path ? path : "(null)", (void*)_this);

    if (load_gl4es_library() != 0) {
        SDL_SetError("Failed to load gl4es library");
        return -1;
    }

    LOGI("✅ Android_GL4ES_LoadLibrary returning 0 (success)");
    return 0;
}

void*
Android_GL4ES_GetProcAddress(_THIS, const char* proc)
{
    void* func = NULL;

    if (!p_gl4es_GetProcAddress) {
        LOGE("❌ gl4es not loaded, cannot get proc address for '%s'", proc);
        return NULL;
    }

    /* 通过 gl4es 获取函数，gl4es 会返回包装过的 GL 函数 */
    func = p_gl4es_GetProcAddress(proc);

    if (func) {
        LOGD("   ✅ gl4es: '%s' -> %p", proc, func);
    } else {
        /* 如果 gl4es 没有，尝试从 EGL 获取 */
        if (p_eglGetProcAddress) {
            func = p_eglGetProcAddress(proc);
        }
        if (func) {
            LOGD("   ✅ EGL: '%s' -> %p", proc, func);
        } else {
            LOGD("   ❌ Not found: '%s'", proc);
        }
    }

    return func;
}

void
Android_GL4ES_UnloadLibrary(_THIS)
{
    LOGI("Android_GL4ES_UnloadLibrary called");

    /* 清理 EGL */
    if (g_egl_display != GL4ES_EGL_NO_DISPLAY && p_eglMakeCurrent) {
        p_eglMakeCurrent(g_egl_display, GL4ES_EGL_NO_SURFACE, GL4ES_EGL_NO_SURFACE, GL4ES_EGL_NO_CONTEXT);
        if (g_egl_context != GL4ES_EGL_NO_CONTEXT && p_eglDestroyContext) {
            p_eglDestroyContext(g_egl_display, g_egl_context);
            g_egl_context = GL4ES_EGL_NO_CONTEXT;
        }
        if (g_egl_surface != GL4ES_EGL_NO_SURFACE && p_eglDestroySurface) {
            p_eglDestroySurface(g_egl_display, g_egl_surface);
            g_egl_surface = GL4ES_EGL_NO_SURFACE;
        }
        if (p_eglTerminate) {
            p_eglTerminate(g_egl_display);
        }
        g_egl_display = GL4ES_EGL_NO_DISPLAY;
    }

    g_gl4es_initialized = 0;
    g_current_window = NULL;

    LOGI("✅ gl4es unloaded");
}

SDL_GLContext
Android_GL4ES_CreateContext(_THIS, SDL_Window* window)
{
    LOGI("🎯 Android_GL4ES_CreateContext called for window '%s'", window ? window->title : "NULL");

    if (!window) {
        SDL_SetError("Window is NULL");
        return NULL;
    }

    /* 创建 EGL context */
    if (create_egl_context(_this, window) != 0) {
        SDL_SetError("Failed to create EGL context for gl4es");
        return NULL;
    }

    /* 初始化 gl4es */
    if (initialize_gl4es(g_window_width, g_window_height) != 0) {
        SDL_SetError("Failed to initialize gl4es");
        return NULL;
    }

    g_current_window = window;

    /* 返回 EGL context 作为 SDL_GLContext */
    LOGI("✅ gl4es context created successfully");
    return (SDL_GLContext)g_egl_context;
}

int
Android_GL4ES_MakeCurrent(_THIS, SDL_Window* window, SDL_GLContext context)
{
    if (!window || !context) {
        /* Unbind context */
        if (g_egl_display != GL4ES_EGL_NO_DISPLAY && p_eglMakeCurrent) {
            p_eglMakeCurrent(g_egl_display, GL4ES_EGL_NO_SURFACE, GL4ES_EGL_NO_SURFACE, GL4ES_EGL_NO_CONTEXT);
        }
        return 0;
    }

    if (g_egl_display == GL4ES_EGL_NO_DISPLAY || g_egl_surface == GL4ES_EGL_NO_SURFACE) {
        LOGE("❌ EGL not initialized");
        return -1;
    }

    if (!p_eglMakeCurrent || !p_eglMakeCurrent(g_egl_display, g_egl_surface, g_egl_surface, (GL4ES_EGLContext)context)) {
        LOGE("❌ eglMakeCurrent failed: 0x%x", p_eglGetError ? p_eglGetError() : 0);
        return -1;
    }

    g_current_window = window;
    return 0;
}

int
Android_GL4ES_SwapWindow(_THIS, SDL_Window* window)
{
    if (g_egl_display == GL4ES_EGL_NO_DISPLAY || g_egl_surface == GL4ES_EGL_NO_SURFACE) {
        LOGE("❌ Cannot swap: EGL not initialized");
        return -1;
    }

    if (!p_eglSwapBuffers || !p_eglSwapBuffers(g_egl_display, g_egl_surface)) {
        LOGE("❌ eglSwapBuffers failed: 0x%x", p_eglGetError ? p_eglGetError() : 0);
        return -1;
    }

    return 0;
}

void
Android_GL4ES_DeleteContext(_THIS, SDL_GLContext context)
{
    LOGI("Android_GL4ES_DeleteContext called with context=%p", context);

    if (g_egl_display != GL4ES_EGL_NO_DISPLAY && p_eglMakeCurrent) {
        p_eglMakeCurrent(g_egl_display, GL4ES_EGL_NO_SURFACE, GL4ES_EGL_NO_SURFACE, GL4ES_EGL_NO_CONTEXT);

        if (context && (GL4ES_EGLContext)context == g_egl_context && p_eglDestroyContext) {
            p_eglDestroyContext(g_egl_display, g_egl_context);
            g_egl_context = GL4ES_EGL_NO_CONTEXT;
        }

        if (g_egl_surface != GL4ES_EGL_NO_SURFACE && p_eglDestroySurface) {
            p_eglDestroySurface(g_egl_display, g_egl_surface);
            g_egl_surface = GL4ES_EGL_NO_SURFACE;
        }
    }

    g_current_window = NULL;
    g_gl4es_initialized = 0;

    LOGI("✅ gl4es context deleted");
}

void
Android_GL4ES_GetDrawableSize(_THIS, SDL_Window* window, int* w, int* h)
{
    if (g_egl_display != GL4ES_EGL_NO_DISPLAY && g_egl_surface != GL4ES_EGL_NO_SURFACE && p_eglQuerySurface) {
        GL4ES_EGLint width = 0, height = 0;
        p_eglQuerySurface(g_egl_display, g_egl_surface, EGL_WIDTH, &width);
        p_eglQuerySurface(g_egl_display, g_egl_surface, EGL_HEIGHT, &height);
        if (w) *w = width;
        if (h) *h = height;
    } else if (window) {
        if (w) *w = window->w;
        if (h) *h = window->h;
    }
}

int
Android_GL4ES_SetSwapInterval(_THIS, int interval)
{
    LOGI("Android_GL4ES_SetSwapInterval: %d", interval);

    if (g_egl_display != GL4ES_EGL_NO_DISPLAY && p_eglSwapInterval) {
        if (p_eglSwapInterval(g_egl_display, interval)) {
            return 0;
        }
    }

    return -1;
}

int
Android_GL4ES_GetSwapInterval(_THIS)
{
    /* EGL doesn't provide a way to query swap interval, return default */
    return 1;
}

#endif /* SDL_VIDEO_DRIVER_ANDROID && SDL_VIDEO_OPENGL && SDL_VIDEO_OPENGL_GL4ES */
