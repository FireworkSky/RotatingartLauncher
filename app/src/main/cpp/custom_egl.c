#include "custom_egl.h"
#include <android/log.h>
#include <stdlib.h>
#include <dlfcn.h>

#define LOG_TAG "CustomEGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

int CustomEGL_Init(ANativeWindow* nativeWindow, CustomEGLContext* ctx) {
    if (!nativeWindow || !ctx) {
        LOGE("Invalid parameters");
        return -1;
    }

    ctx->nativeWindow = nativeWindow;

    // 获取EGL display
    LOGI("Getting EGL display...");
    ctx->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (ctx->display == EGL_NO_DISPLAY) {
        LOGE("Failed to get EGL display: 0x%x", eglGetError());
        return -1;
    }

    // 初始化EGL
    EGLint major, minor;
    LOGI("Initializing EGL...");
    if (!eglInitialize(ctx->display, &major, &minor)) {
        LOGE("Failed to initialize EGL: 0x%x", eglGetError());
        return -1;
    }
    LOGI("EGL initialized: version %d.%d", major, minor);

    // 选择配置
    EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,  // gl4es需要ES2
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };

    EGLint numConfigs;
    LOGI("Choosing EGL config...");
    if (!eglChooseConfig(ctx->display, configAttribs, &ctx->config, 1, &numConfigs) || numConfigs == 0) {
        LOGE("Failed to choose EGL config: 0x%x", eglGetError());
        return -1;
    }
    LOGI("EGL config chosen");

    // 绑定OpenGL ES API
    LOGI("Binding OpenGL ES API...");
    if (!eglBindAPI(EGL_OPENGL_ES_API)) {
        LOGE("Failed to bind OpenGL ES API: 0x%x", eglGetError());
        return -1;
    }

    // 创建上下文（ES 2.0 for gl4es）
    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };
    LOGI("Creating EGL context...");
    ctx->context = eglCreateContext(ctx->display, ctx->config, EGL_NO_CONTEXT, contextAttribs);
    if (ctx->context == EGL_NO_CONTEXT) {
        LOGE("Failed to create EGL context: 0x%x", eglGetError());
        return -1;
    }
    LOGI("EGL context created");

    // 创建窗口surface
    LOGI("Creating EGL window surface...");
    ctx->surface = eglCreateWindowSurface(ctx->display, ctx->config, nativeWindow, NULL);
    if (ctx->surface == EGL_NO_SURFACE) {
        LOGE("Failed to create EGL window surface: 0x%x", eglGetError());
        eglDestroyContext(ctx->display, ctx->context);
        return -1;
    }
    LOGI("EGL window surface created");

    // 使上下文生效
    LOGI("Making EGL context current...");
    if (!eglMakeCurrent(ctx->display, ctx->surface, ctx->surface, ctx->context)) {
        LOGE("Failed to make EGL context current: 0x%x", eglGetError());
        eglDestroySurface(ctx->display, ctx->surface);
        eglDestroyContext(ctx->display, ctx->context);
        return -1;
    }
    LOGI("EGL context made current");

    // 设置swap interval (vsync)
    eglSwapInterval(ctx->display, 1);

    LOGI("[OK] Custom EGL initialization complete!");
    return 0;
}

void CustomEGL_SwapBuffers(CustomEGLContext* ctx) {
    if (ctx && ctx->display != EGL_NO_DISPLAY && ctx->surface != EGL_NO_SURFACE) {
        eglSwapBuffers(ctx->display, ctx->surface);
    }
}

void CustomEGL_Cleanup(CustomEGLContext* ctx) {
    if (!ctx) return;

    LOGI("Cleaning up custom EGL...");

    if (ctx->display != EGL_NO_DISPLAY) {
        eglMakeCurrent(ctx->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

        if (ctx->context != EGL_NO_CONTEXT) {
            eglDestroyContext(ctx->display, ctx->context);
            ctx->context = EGL_NO_CONTEXT;
        }

        if (ctx->surface != EGL_NO_SURFACE) {
            eglDestroySurface(ctx->display, ctx->surface);
            ctx->surface = EGL_NO_SURFACE;
        }

        eglTerminate(ctx->display);
        ctx->display = EGL_NO_DISPLAY;
    }

    ctx->nativeWindow = NULL;
    LOGI("Custom EGL cleanup complete");
}
