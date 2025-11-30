# SDL 动态渲染器加载实现方案

## 概述

基于 lwjgl3 的 `SharedLibrary` 和 PojavLauncher 的动态加载机制，为 SDL 实现完全的运行时渲染器切换，无需重新编译。

## 核心原理

### 1. lwjgl3 的动态加载机制

```java
// lwjgl3/opengl/GL.java
public static void create(String libName) {
    SharedLibrary GL = Library.loadNative(GL.class, "org.lwjgl.opengl", libName);
    create(GL);
}

// 动态获取函数指针
long GetProcAddress = library.getFunctionAddress("glXGetProcAddress");
```

**关键点**：
- 通过 `SharedLibrary` 接口抽象库加载
- 使用 `FunctionProvider` 动态获取函数指针
- 支持多个渲染后端（Desktop GL, OSMesa, EGL等）

### 2. PojavLauncher 的方法

```java
// JREUtils.java
public static String loadGraphicsLibrary() {
    String renderLibrary;
    switch (LOCAL_RENDERER) {
        case "opengles2":
            renderLibrary = "libgl4es_114.so";
            break;
        case "vulkan_zink":
            renderLibrary = "libOSMesa.so";
            break;
    }

    // 使用 dlopen 加载
    dlopen(renderLibrary);
    return renderLibrary;
}
```

**关键点**：
- 通过 `dlopen` 运行时加载渲染器库
- 使用环境变量 `LOCAL_RENDERER` 控制选择
- 支持运行时 fallback 到其他渲染器

## 实现方案

### 方案 A：修改 SDL 使用完全动态加载（推荐）

#### 修改 `SDL_androidvideo.c`

```c
// 新增：动态渲染器加载器结构
typedef struct {
    const char* name;
    const char* egl_library;
    const char* gles_library;
    SDL_bool need_preload;
} RendererBackend;

static const RendererBackend RENDERER_BACKENDS[] = {
    {"native", NULL, NULL, SDL_FALSE},                    // 系统默认
    {"gl4es", "libgl4es.so", "libgl4es.so", SDL_TRUE},   // gl4es
    {"angle", "libEGL_angle.so", "libGLESv2_angle.so", SDL_TRUE},  // ANGLE
    {NULL, NULL, NULL, SDL_FALSE}
};

// 新增：动态加载渲染器函数
static SDL_bool Android_LoadRenderer(const char* renderer_name) {
    const RendererBackend* backend = NULL;

    // 查找渲染器配置
    for (int i = 0; RENDERER_BACKENDS[i].name; i++) {
        if (SDL_strcasecmp(RENDERER_BACKENDS[i].name, renderer_name) == 0) {
            backend = &RENDERER_BACKENDS[i];
            break;
        }
    }

    if (!backend) {
        SDL_Log("Unknown renderer: %s, falling back to native", renderer_name);
        backend = &RENDERER_BACKENDS[0];
    }

    // 如果需要预加载
    if (backend->need_preload && backend->egl_library) {
        void* handle = dlopen(backend->egl_library, RTLD_NOW | RTLD_GLOBAL);
        if (!handle) {
            SDL_Log("Failed to load %s: %s", backend->egl_library, dlerror());
            return SDL_FALSE;
        }
        SDL_Log("✓ Loaded %s (handle=%p)", backend->egl_library, handle);

        // 设置 LD_PRELOAD
        setenv("LD_PRELOAD", backend->egl_library, 1);
    }

    return SDL_TRUE;
}

// 修改：Android_CreateDevice
static SDL_VideoDevice *Android_CreateDevice(void) {
    SDL_VideoDevice *device;

    // ... 现有代码 ...

    // 🔥 新增：从环境变量读取渲染器配置
    const char* renderer = SDL_getenv("SDL_RENDERER");
    if (!renderer) {
        renderer = "native";  // 默认使用系统渲染器
    }

    SDL_Log("================================================================");
    SDL_Log("  SDL Dynamic Renderer Loading");
    SDL_Log("  Selected: %s", renderer);
    SDL_Log("================================================================");

    // 🔥 动态加载渲染器
    if (!Android_LoadRenderer(renderer)) {
        SDL_Log("⚠️ Renderer loading failed, trying native fallback");
        Android_LoadRenderer("native");
    }

    // 🔥 使用通用的 EGL 接口（所有渲染器都提供标准 EGL 接口）
    device->GL_LoadLibrary = SDL_EGL_LoadLibrary;
    device->GL_GetProcAddress = SDL_EGL_GetProcAddress;
    device->GL_UnloadLibrary = SDL_EGL_UnloadLibrary;
    device->GL_CreateContext = SDL_EGL_CreateContext;
    device->GL_MakeCurrent = SDL_EGL_MakeCurrent;
    device->GL_SwapWindow = SDL_EGL_SwapBuffers;
    device->GL_DeleteContext = SDL_EGL_DeleteContext;

    // ... 其余代码 ...
}
```

#### 修改 `SDL_EGL_LoadLibrary`（在 `SDL_egl.c` 中）

```c
int SDL_EGL_LoadLibrary(_THIS, const char *egl_path, NativeDisplayType native_display, EGLenum platform) {
    // 🔥 检查是否通过 LD_PRELOAD 已加载自定义 EGL
    const char* preload = getenv("LD_PRELOAD");
    if (preload && strlen(preload) > 0) {
        SDL_Log("✓ Using preloaded EGL library: %s", preload);
        // LD_PRELOAD 已加载库，dlopen("libEGL.so") 会自动使用它
        egl_path = "libEGL.so";
    }

    // 🔥 尝试从环境变量获取自定义路径
    if (!egl_path) {
        egl_path = SDL_getenv("SDL_EGL_LIBRARY");
    }

    // 默认路径列表
    const char* egl_libraries[] = {
        egl_path,           // 用户指定的路径
        "libEGL.so",        // 标准名称
        "libEGL.so.1",      // 带版本号
        NULL
    };

    // 依次尝试加载
    for (int i = 0; egl_libraries[i]; i++) {
        if (!egl_libraries[i]) continue;

        void* dll_handle = dlopen(egl_libraries[i], RTLD_NOW | RTLD_GLOBAL);
        if (dll_handle) {
            SDL_Log("✓ Loaded EGL library: %s", egl_libraries[i]);
            _this->egl_data->egl_dll_handle = dll_handle;
            break;
        } else {
            SDL_Log("  Tried %s: %s", egl_libraries[i], dlerror());
        }
    }

    if (!_this->egl_data->egl_dll_handle) {
        return SDL_SetError("Could not load EGL library");
    }

    // ... 加载 EGL 函数指针 ...
}
```

### 方案 B：使用 Java 层控制（更简单）

保持 SDL 不变，完全通过 Java 层的 `RendererLoader` 控制：

```java
// GameActivity.java
@Override
public void loadLibraries() {
    // 1. 从设置读取渲染器选择
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String rendererId = prefs.getString("renderer", RendererConfig.RENDERER_NATIVE_GLES);

    // 2. 加载渲染器（设置 LD_PRELOAD 和环境变量）
    RendererLoader.loadRenderer(this, rendererId);

    // 3. 设置 SDL 环境变量
    RendererLoader.nativeSetEnv("SDL_RENDERER", rendererId);

    // 4. 加载 SDL 和其他库
    super.loadLibraries();  // 加载 libSDL2.so, libmain.so 等
}
```

## 环境变量控制

| 环境变量 | 作用 | 示例值 |
|---------|------|--------|
| `SDL_RENDERER` | 选择渲染器后端 | `native`, `gl4es`, `angle` |
| `LD_PRELOAD` | 预加载自定义 EGL 库 | `/data/app/.../libgl4es.so` |
| `SDL_EGL_LIBRARY` | 指定 EGL 库路径 | `/path/to/libEGL_angle.so` |
| `LIBGL_ES` | gl4es 版本配置 | `2` 或 `3` |

## 使用示例

### 示例 1：使用 Native 渲染器（默认）

```java
// 不设置任何环境变量，SDL 自动使用系统 libEGL.so
RendererLoader.loadRenderer(context, RendererConfig.RENDERER_NATIVE_GLES);
```

```
D/SDL     : ================================================================
D/SDL     :   SDL Dynamic Renderer Loading
D/SDL     :   Selected: native
D/SDL     : ================================================================
D/SDL     : ✓ Using system libEGL.so and libGLESv2.so
D/SDL     : ✓ Loaded EGL library: libEGL.so
```

### 示例 2：使用 gl4es 渲染器

```java
RendererLoader.loadRenderer(context, RendererConfig.RENDERER_GL4ES);
```

```
D/RendererLoader: ================================================================
D/RendererLoader:   Loading Renderer: gl4es
D/RendererLoader: ================================================================
D/RendererLoader:   → gl4es library: /data/app/.../libgl4es.so
D/RendererLoader:   ✓ dlopen success, handle = 0x7a8c4d5000
D/RendererLoader:   ✓ LD_PRELOAD = /data/app/.../libgl4es.so
D/RendererLoader:   ✓ gl4es environment configured
D/RendererLoader: ✅ Renderer loaded successfully: gl4es
D/SDL     : ✓ Using preloaded EGL library: /data/app/.../libgl4es.so
D/SDL     : ✓ Loaded EGL library: libEGL.so (actually gl4es via LD_PRELOAD)
```

### 示例 3：使用 ANGLE 渲染器

```java
RendererLoader.loadRenderer(context, RendererConfig.RENDERER_ANGLE);
```

```
D/RendererLoader:   → EGL library: /data/app/.../libEGL_angle.so
D/RendererLoader:   → GLES library: /data/app/.../libGLESv2_angle.so
D/RendererLoader:   ✓ ANGLE loaded successfully
D/SDL     : ✓ Loaded EGL library: libEGL.so (ANGLE via LD_PRELOAD)
```

## 对比 PojavLauncher 和 lwjgl3

| 特性 | PojavLauncher | lwjgl3 | 我们的方案 |
|------|---------------|--------|-----------|
| 动态加载方式 | dlopen + LD_PRELOAD | SharedLibrary 接口 | dlopen + LD_PRELOAD |
| 函数指针获取 | dlsym | getFunctionAddress | eglGetProcAddress |
| 渲染器切换 | 重启应用 | 重新创建 GL 上下文 | 重启应用 |
| 配置方式 | Java 变量 + 环境变量 | Java API | Java + 环境变量 |
| 无需修改源码 | ✅ | ✅ | ✅ |

## 优势

1. **完全动态** - 无需重新编译 SDL
2. **插件式架构** - 添加新渲染器只需放入 .so 文件
3. **透明切换** - SDL 无需知道使用哪个渲染器
4. **兼容性强** - 所有渲染器都提供标准 EGL 接口
5. **易于调试** - 详细的加载日志

## 建议

**推荐使用方案 B（Java 层控制）**：
- 无需修改 SDL 源码
- 通过 `RendererLoader` 完全控制
- 更容易维护和调试
- 与 PojavLauncher 的方法一致

## 下一步

1. ✅ 已创建 `RendererConfig.java` 和 `RendererLoader.java`
2. ✅ 已创建 `renderer_loader.c` JNI 实现
3. ⏳ 修改 `CMakeLists.txt` 添加 `renderer_loader.c`
4. ⏳ 在 `GameActivity.loadLibraries()` 中集成
5. ⏳ 测试不同渲染器的加载和运行
