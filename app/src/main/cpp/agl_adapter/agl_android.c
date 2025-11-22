/*
 * Android AGL Implementation for gl4es
 * Based on AmigaOS agl.c but adapted for Android EGL
 */

#include <stdlib.h>
#include <string.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <android/native_window.h>

#define LOG_TAG "GL4ES_AGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* gl4es internal functions (defined in gl4es core) */
extern void initialize_gl4es(void);  /* Must be called before any other gl4es function! */
extern void* NewGLState(void* shared_glstate, int es2only);
extern void DeleteGLState(void* oldstate);
extern void ActivateGLState(void* new_glstate);
extern void GetHardwareExtensions(int notest);
extern void* gl4es_GetProcAddress(const char* name);  /* gl4es function loader */

/* TagItem structure (compatible with AmigaOS) 
 * ⚠️ 重要：必须与SDL_androidgl4es.c中的定义完全一致！
 * ti_Tag使用unsigned int (4字节)，ti_Data使用unsigned long (8字节)
 */
struct TagItem {
    unsigned int ti_Tag;      /* 必须是4字节 */
    unsigned long ti_Data;    /* 8字节 */
};

#define TAG_DONE 0

/* AGL tag definitions (must match SDL_androidgl4es.c) */
#define GL4ES_CCT_WINDOW        1
#define GL4ES_CCT_DEPTH         2
#define GL4ES_CCT_STENCIL       3
#define GL4ES_CCT_VSYNC         4
#define GL4ES_CCT_RESIZE_VIEWPORT 5

/* AGL context structure */
typedef struct {
    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    void* glstate;      /* gl4es internal state */
} agl_context_t;

static agl_context_t* current_context = NULL;

/*
 * aglCreateContext / aglCreateContext2
 * Create an EGL context and gl4es state
 */
__attribute__((visibility("default")))
void* aglCreateContext2(unsigned long* errcode, struct TagItem* tags)
{
    LOGI("aglCreateContext2 called, tags=%p, errcode=%p", tags, errcode);
    
    /* Parse tags for configuration */
    ANativeWindow* native_window = NULL;
    int depth_size = 24;
    int stencil_size = 8;
    
    if (!tags) {
        LOGE("tags is NULL!");
        if (errcode) *errcode = 1;
        return NULL;
    }
    
    LOGI("Parsing tags... (GL4ES_CCT_WINDOW=%u, DEPTH=%u, STENCIL=%u)", 
         GL4ES_CCT_WINDOW, GL4ES_CCT_DEPTH, GL4ES_CCT_STENCIL);
    for (int i = 0; tags[i].ti_Tag != TAG_DONE; ++i) {
        LOGI("  Tag[%d]: ti_Tag=%u (0x%x), ti_Data=0x%lx", i, tags[i].ti_Tag, tags[i].ti_Tag, tags[i].ti_Data);
        
        if (tags[i].ti_Tag == GL4ES_CCT_WINDOW) {
            native_window = (ANativeWindow*)tags[i].ti_Data;
            LOGI("    -> Window: %p", native_window);
        } else if (tags[i].ti_Tag == GL4ES_CCT_DEPTH) {
            depth_size = (int)tags[i].ti_Data;
            LOGI("    -> Depth: %d", depth_size);
        } else if (tags[i].ti_Tag == GL4ES_CCT_STENCIL) {
            stencil_size = (int)tags[i].ti_Data;
            LOGI("    -> Stencil: %d", stencil_size);
        } else if (tags[i].ti_Tag == GL4ES_CCT_VSYNC) {
            LOGI("    -> VSync: %lu", tags[i].ti_Data);
        } else if (tags[i].ti_Tag == GL4ES_CCT_RESIZE_VIEWPORT) {
            LOGI("    -> Resize viewport: %lu", tags[i].ti_Data);
        } else {
            LOGI("    -> Unknown tag %u (expected WINDOW=%u, DEPTH=%u, STENCIL=%u)", 
                 tags[i].ti_Tag, GL4ES_CCT_WINDOW, GL4ES_CCT_DEPTH, GL4ES_CCT_STENCIL);
        }
    }
    LOGI("Tags parsed: native_window=%p, depth=%d, stencil=%d", native_window, depth_size, stencil_size);
    
    if (!native_window) {
        LOGE("ANativeWindow is required for aglCreateContext2!");
        if (errcode) *errcode = 1;
        return NULL;
    }
    
    agl_context_t* ctx = (agl_context_t*)malloc(sizeof(agl_context_t));
    if (!ctx) {
        LOGE("Failed to allocate context");
        if (errcode) *errcode = 2;
        return NULL;
    }
    memset(ctx, 0, sizeof(agl_context_t));
    
    /* Get EGL display */
    ctx->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (ctx->display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        free(ctx);
        if (errcode) *errcode = 3;
        return NULL;
    }
    
    /* Initialize EGL */
    EGLint major, minor;
    if (!eglInitialize(ctx->display, &major, &minor)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        free(ctx);
        if (errcode) *errcode = 4;
        return NULL;
    }
    
    LOGI("EGL %d.%d initialized", major, minor);
    
    /* Choose config */
    EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, depth_size,
        EGL_STENCIL_SIZE, stencil_size,
        EGL_NONE
    };
    
    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(ctx->display, configAttribs, &config, 1, &numConfigs) || numConfigs == 0) {
        LOGE("eglChooseConfig failed: 0x%x", eglGetError());
        eglTerminate(ctx->display);
        free(ctx);
        if (errcode) *errcode = 5;
        return NULL;
    }
    
    LOGI("EGL config chosen");
    
    /* Create EGL context */
    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };
    
    ctx->context = eglCreateContext(ctx->display, config, EGL_NO_CONTEXT, contextAttribs);
    if (ctx->context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        eglTerminate(ctx->display);
        free(ctx);
        if (errcode) *errcode = 6;
        return NULL;
    }
    
    LOGI("EGL context created");
    
    /* Create window surface using the provided ANativeWindow */
    ctx->surface = eglCreateWindowSurface(ctx->display, config, native_window, NULL);
    if (ctx->surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        eglDestroyContext(ctx->display, ctx->context);
        eglTerminate(ctx->display);
        free(ctx);
        if (errcode) *errcode = 7;
        return NULL;
    }
    
    LOGI("EGL window surface created");
    
    /* Make context current to initialize gl4es state */
    if (!eglMakeCurrent(ctx->display, ctx->surface, ctx->surface, ctx->context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        eglDestroySurface(ctx->display, ctx->surface);
        eglDestroyContext(ctx->display, ctx->context);
        eglTerminate(ctx->display);
        free(ctx);
        if (errcode) *errcode = 8;
        return NULL;
    }
    
    LOGI("EGL context made current");
    
    /* Initialize gl4es global state (must be called before NewGLState) */
    LOGI("⏳ Calling initialize_gl4es() to set up global state...");
    initialize_gl4es();
    LOGI("✅ gl4es global state initialized");
    
    /* Create gl4es state */
    LOGI("⏳ Calling NewGLState (may initialize threads/mutexes)...");
    ctx->glstate = NewGLState(NULL, 0);
    if (!ctx->glstate) {
        LOGE("Failed to create gl4es state");
        eglMakeCurrent(ctx->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(ctx->display, ctx->surface);
        eglDestroyContext(ctx->display, ctx->context);
        eglTerminate(ctx->display);
        free(ctx);
        if (errcode) *errcode = 9;
        return NULL;
    }
    
    LOGI("✅ gl4es state created: %p", ctx->glstate);
    
    LOGI("⏳ Calling ActivateGLState...");
    ActivateGLState(ctx->glstate);
    LOGI("✅ gl4es state activated");
    
    LOGI("⏳ Calling GetHardwareExtensions...");
    GetHardwareExtensions(0);
    LOGI("✅ gl4es hardware extensions initialized");
    
    LOGI("✓ Context %p created successfully!", ctx);
    
    if (errcode) *errcode = 0;
    return ctx;
}

__attribute__((visibility("default")))
void* aglCreateContext(unsigned long* errcode, struct TagItem* tags)
{
    return aglCreateContext2(errcode, tags);
}

/*
 * aglDestroyContext
 * Destroy EGL context and gl4es state
 */
__attribute__((visibility("default")))
void aglDestroyContext(void* context)
{
    if (!context) {
        LOGE("aglDestroyContext: NULL context");
        return;
    }
    
    LOGI("aglDestroyContext: %p", context);
    
    agl_context_t* ctx = (agl_context_t*)context;
    
    /* Make context current before cleanup */
    if (ctx->context != EGL_NO_CONTEXT) {
        eglMakeCurrent(ctx->display, ctx->surface, ctx->surface, ctx->context);
    }
    
    /* Delete gl4es state */
    if (ctx->glstate) {
        DeleteGLState(ctx->glstate);
        ctx->glstate = NULL;
    }
    
    /* Destroy EGL resources */
    if (ctx->context != EGL_NO_CONTEXT) {
        eglMakeCurrent(ctx->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(ctx->display, ctx->surface);
        eglDestroyContext(ctx->display, ctx->context);
    }
    
    if (ctx->display != EGL_NO_DISPLAY) {
        eglTerminate(ctx->display);
    }
    
    if (current_context == ctx) {
        current_context = NULL;
    }
    
    free(ctx);
}

/*
 * aglMakeCurrent
 * Make EGL context current and activate gl4es state
 */
__attribute__((visibility("default")))
void aglMakeCurrent(void* context)
{
    if (!context) {
        LOGE("aglMakeCurrent: NULL context");
        return;
    }
    
    agl_context_t* ctx = (agl_context_t*)context;
    
    if (ctx->context == EGL_NO_CONTEXT) {
        LOGE("aglMakeCurrent: Invalid EGL context");
        return;
    }
    
    if (!eglMakeCurrent(ctx->display, ctx->surface, ctx->surface, ctx->context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        return;
    }
    
    if (ctx->glstate) {
        ActivateGLState(ctx->glstate);
    }
    
    current_context = ctx;
}

/*
 * aglSwapBuffers
 * Swap EGL buffers
 */
__attribute__((visibility("default")))
void aglSwapBuffers(void)
{
    if (!current_context) {
        LOGE("aglSwapBuffers: No current context");
        return;
    }
    
    agl_context_t* ctx = current_context;
    
    if (!eglSwapBuffers(ctx->display, ctx->surface)) {
        LOGE("eglSwapBuffers failed: 0x%x", eglGetError());
    }
}

/*
 * aglGetProcAddress
 * Get OpenGL function pointer (via gl4es)
 */
__attribute__((visibility("default")))
void* aglGetProcAddress(const char* name)
{
    if (!name) {
        return NULL;
    }
    
    /* First try gl4es function loader */
    void* func = gl4es_GetProcAddress(name);
    
    /* If not found in gl4es, try EGL */
    if (!func) {
        func = (void*)eglGetProcAddress(name);
    }
    
    return func;
}

/*
 * aglSetParams2
 * Set AGL parameters (placeholder for compatibility)
 */
__attribute__((visibility("default")))
int aglSetParams2(struct TagItem* tags)
{
    /* Not implemented for Android yet */
    /* Could be used to update window surface, etc. */
    return 0;
}

/*
 * aglSetBitmap
 * Set bitmap (placeholder for AmigaOS compatibility)
 */
__attribute__((visibility("default")))
void aglSetBitmap(void* bitmap)
{
    /* Not applicable for Android */
}

