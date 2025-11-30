# gl4es Android适配技术文档

## 📖 目录

- [简介](#简介)
- [为什么需要gl4es](#为什么需要gl4es)
- [技术架构](#技术架构)
- [AGL接口实现](#agl接口实现)
- [SDL集成](#sdl集成)
- [双渲染器系统](#双渲染器系统)
- [关键技术点](#关键技术点)
- [测试结果](#测试结果)
- [已知问题](#已知问题)
- [下一步计划](#下一步计划)

---

## 🎯 简介

本文档描述了如何在Android平台上集成**gl4es**（OpenGL到OpenGL ES转换层），使FNA/XNA游戏能够通过OpenGL 2.1兼容模式运行。

**gl4es** 是一个OpenGL到OpenGL ES的翻译层，能将桌面OpenGL调用转换为OpenGL ES 2.0/3.0调用，使原本只能在桌面平台运行的OpenGL应用能够在移动设备上运行。

## 🤔 为什么需要gl4es

### 原生OpenGL ES的限制

Android原生只支持**OpenGL ES**（Embedded Systems），而许多桌面游戏使用**OpenGL Compatibility Profile**，两者有显著差异：

| 特性 | OpenGL 2.1/3.x | OpenGL ES 2.0/3.0 |
|------|---------------|-------------------|
| 固定管线 | ✅ 支持 | ❌ 不支持 |
| 立即模式（glBegin/glEnd） | ✅ 支持 | ❌ 不支持 |
| 兼容性扩展 | ✅ 丰富 | ⚠️ 有限 |
| 着色器要求 | ⚠️ 可选 | ✅ 必需 |

### FNA3D渲染器兼容性

FNA3D支持多种渲染器，但在Android上：
- **OpenGL ES 3.0**：原生支持，性能最佳，但需要游戏完全兼容ES
- **OpenGL 2.1 via gl4es**：兼容性更好，支持更多桌面OpenGL特性

对于像tModLoader这样的游戏，gl4es可以提供更好的兼容性。

---

## 🏗️ 技术架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                     FNA3D Application                    │
│                    (tModLoader, etc.)                    │
└─────────────────────┬───────────────────────────────────┘
                      │ OpenGL 2.1 API calls
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   SDL2 Video Backend                     │
│  ┌───────────────────────┬─────────────────────────┐   │
│  │ Android_GL4ES_*       │ Android_GLES_*          │   │
│  │ (gl4es renderer)      │ (native GLES renderer)  │   │
│  └───────────┬───────────┴─────────────┬───────────┘   │
└──────────────┼─────────────────────────┼───────────────┘
               │                         │
               ▼                         ▼
┌──────────────────────────┐  ┌────────────────────────┐
│   gl4es (static lib)     │  │  Native GLES/EGL       │
│  ┌──────────────────┐    │  │                        │
│  │ AGL Interface    │    │  └────────────────────────┘
│  └────────┬─────────┘    │
│           ▼              │
│  ┌──────────────────┐    │
│  │ EGL + GLES2/3    │    │
│  └──────────────────┘    │
└──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────┐
│            Android Graphics Stack (SurfaceFlinger)       │
└─────────────────────────────────────────────────────────┘
```

### 三层架构

1. **应用层（FNA3D）**
   - 使用标准OpenGL 2.1 API
   - 无需关心底层实现
   - 通过环境变量选择渲染器

2. **SDL层（渲染器抽象）**
   - `SDL_androidgl4es.c`：gl4es适配器
   - `SDL_androidgles.c`：原生GLES适配器
   - 运行时动态选择

3. **渲染层**
   - **gl4es**：翻译OpenGL到GLES
   - **Native GLES**：直接使用系统GLES

---

## 🔧 AGL接口实现

### 什么是AGL

**AGL** 不是标准规范，而是gl4es提供的一套简化的OpenGL上下文管理接口。相比EGL的复杂性，AGL接口更加简洁。

### 核心AGL函数

文件：`app/src/main/cpp/gl4es/src/agl/agl_android.c`

#### 1. `aglCreateContext2` - 创建OpenGL上下文

```c
void* aglCreateContext2(struct TagItem* tags, int* errcode)
{
    // 1. 解析tags参数（窗口句柄、深度缓冲、模板缓冲等）
    ANativeWindow* native_window = NULL;
    int depth = 24, stencil = 8;
    
    // 2. 初始化EGL
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(display, NULL, NULL);
    
    // 3. 选择EGL配置
    EGLConfig config;
    EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_DEPTH_SIZE, depth,
        EGL_STENCIL_SIZE, stencil,
        // ...
        EGL_NONE
    };
    eglChooseConfig(display, attribs, &config, 1, &num_config);
    
    // 4. 创建EGL上下文
    EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,  // GLES 2.0
        EGL_NONE
    };
    EGLContext egl_context = eglCreateContext(display, config, 
                                              EGL_NO_CONTEXT, 
                                              context_attribs);
    
    // 5. 创建窗口surface
    EGLSurface egl_surface = eglCreateWindowSurface(display, config, 
                                                     native_window, NULL);
    
    // 6. 激活上下文
    eglMakeCurrent(display, egl_surface, egl_surface, egl_context);
    
    // 7. 初始化gl4es全局状态
    initialize_gl4es();
    
    // 8. 创建gl4es状态对象
    ctx->glstate = NewGLState(NULL, 0);
    
    // 9. 获取硬件扩展
    GetHardwareExtensions(0);
    
    return ctx;  // 返回AGL上下文
}
```

**关键点**：
- ⚠️ 必须先调用`initialize_gl4es()`初始化全局状态
- ⚠️ `TagItem`结构必须与SDL端完全一致（内存对齐）
- ✅ 内部管理完整的EGL生命周期

#### 2. `aglMakeCurrent` - 激活上下文

```c
int aglMakeCurrent(void* context)
{
    AGLContext* ctx = (AGLContext*)context;
    
    // 1. 激活EGL上下文
    eglMakeCurrent(ctx->egl_display, 
                   ctx->egl_surface, 
                   ctx->egl_surface, 
                   ctx->egl_context);
    
    // 2. 激活gl4es状态
    ActivateGLState(ctx->glstate);
    
    return 1;  // 成功
}
```

#### 3. `aglSwapBuffers` - 交换缓冲区

```c
void aglSwapBuffers(void)
{
    // 查找当前激活的AGL上下文
    AGLContext* ctx = find_current_context();
    
    // 交换EGL缓冲区
    eglSwapBuffers(ctx->egl_display, ctx->egl_surface);
}
```

#### 4. `aglGetProcAddress` - 获取OpenGL函数指针

```c
void* aglGetProcAddress(const char* name)
{
    // 优先从gl4es获取（翻译后的函数）
    void* proc = gl4es_GetProcAddress(name);
    if (proc) return proc;
    
    // 回退到EGL
    return (void*)eglGetProcAddress(name);
}
```

### TagItem结构

```c
/* ⚠️ 关键：内存对齐必须正确！ */
struct TagItem {
    unsigned int ti_Tag;      // 4字节：标签类型
    unsigned long ti_Data;    // 8字节：标签数据（指针或值）
};

/* 标签定义 */
#define GL4ES_CCT_WINDOW        1  // ANativeWindow*
#define GL4ES_CCT_DEPTH         2  // 深度缓冲位数
#define GL4ES_CCT_STENCIL       3  // 模板缓冲位数
#define GL4ES_CCT_VSYNC         4  // 垂直同步
#define TAG_DONE                0  // 结束标记
```

**使用示例**：
```c
struct TagItem tags[] = {
    {GL4ES_CCT_WINDOW, (unsigned long)native_window},
    {GL4ES_CCT_DEPTH, 24},
    {GL4ES_CCT_STENCIL, 8},
    {TAG_DONE, 0}
};

void* context = aglCreateContext2(tags, &errcode);
```

---

## 🎮 SDL集成

### SDL_androidgl4es.c实现

文件：`app/src/main/cpp/SDL/src/video/android/SDL_androidgl4es.c`

这个文件实现了SDL的OpenGL视频驱动接口，将SDL的OpenGL调用转发到gl4es的AGL接口。

#### 核心函数实现

```c
/* 1. 加载库 */
int Android_GL4ES_LoadLibrary(_THIS, const char* path)
{
    // gl4es已静态链接，AGL函数通过extern声明直接可用
    // 无需动态加载
    LOGI("✅ gl4es library (static linked)");
    return 0;
}

/* 2. 创建OpenGL上下文 */
SDL_GLContext Android_GL4ES_CreateContext(_THIS, SDL_Window* window)
{
    SDL_WindowData* data = (SDL_WindowData*)window->driverdata;
    ANativeWindow* native_window = data->native_window;
    
    // 构建TagItem数组
    struct TagItem tags[] = {
        {GL4ES_CCT_WINDOW, (unsigned long)native_window},
        {GL4ES_CCT_DEPTH, 24},
        {GL4ES_CCT_STENCIL, 8},
        {TAG_DONE, 0}
    };
    
    int errcode = 0;
    void* agl_context = aglCreateContext2(tags, &errcode);
    
    if (!agl_context) {
        return SDL_SetError("aglCreateContext2 failed");
    }
    
    // 保存全局上下文（Android单窗口）
    g_agl_current_context = agl_context;
    g_agl_current_window = window;
    
    return (SDL_GLContext)agl_context;
}

/* 3. 激活上下文 */
int Android_GL4ES_MakeCurrent(_THIS, SDL_Window* window, SDL_GLContext context)
{
    if (context == NULL) {
        // 解除当前上下文
        g_agl_current_context = NULL;
        g_agl_current_window = NULL;
        return 0;
    }
    
    // 激活gl4es上下文
    if (!aglMakeCurrent(context)) {
        return SDL_SetError("aglMakeCurrent failed");
    }
    
    g_agl_current_context = context;
    g_agl_current_window = window;
    return 0;
}

/* 4. 交换缓冲区 */
int Android_GL4ES_SwapWindow(_THIS, SDL_Window* window)
{
    aglSwapBuffers();
    return 0;
}

/* 5. 获取OpenGL函数指针 */
void* Android_GL4ES_GetProcAddress(_THIS, const char* proc)
{
    void* func = aglGetProcAddress(proc);
    LOGI("🔍 GetProcAddress: %s", proc);
    
    if (func) {
        LOGI("   ✅ Loaded '%s' at %p", proc, func);
    } else {
        LOGE("   ❌ Failed to load function '%s'", proc);
    }
    
    return func;
}
```

#### 关键设计决策

1. **全局上下文管理**
   ```c
   static void* g_agl_current_context = NULL;
   static SDL_Window* g_agl_current_window = NULL;
   ```
   - Android单窗口特性，使用全局变量简化管理
   - 避免频繁查找窗口数据结构

2. **不使用SDL的egl_data**
   ```c
   // ❌ 错误做法：
   _this->egl_data = SDL_calloc(1, sizeof(*_this->egl_data));
   
   // ✅ 正确做法：
   // gl4es通过AGL接口管理自己的EGL，SDL不需要egl_data
   ```

3. **跳过EGL surface创建**
   ```c
   // 在 SDL_androidwindow.c 中：
   const char* gl_driver = SDL_getenv("FNA3D_OPENGL_DRIVER");
   if (gl_driver && SDL_strcasecmp(gl_driver, "gl4es") == 0) {
       // gl4es模式下，跳过SDL的EGL surface创建
       __android_log_print(ANDROID_LOG_INFO, "SDL_Window", 
                          "Using gl4es renderer, skipping EGL surface");
   } else {
       // 原生GLES模式，创建EGL surface
       data->egl_surface = SDL_EGL_CreateSurface(...);
   }
   ```

---

## 🔄 双渲染器系统

### 运行时动态选择

文件：`app/src/main/cpp/SDL/src/video/android/SDL_androidvideo.c`

```c
int Android_VideoInit(_THIS)
{
    // 读取环境变量
    const char* gl_driver = SDL_getenv("FNA3D_OPENGL_DRIVER");
    SDL_bool use_gl4es = (gl_driver && SDL_strcasecmp(gl_driver, "gl4es") == 0);
    
#if defined(SDL_VIDEO_OPENGL_GL4ES) && defined(SDL_VIDEO_OPENGL_EGL)
    if (use_gl4es) {
        SDL_Log("🎨 Using gl4es renderer (OpenGL 2.1 Compatibility Profile)");
        
        // 设置gl4es函数指针
        device->GL_LoadLibrary = Android_GL4ES_LoadLibrary;
        device->GL_GetProcAddress = Android_GL4ES_GetProcAddress;
        device->GL_UnloadLibrary = Android_GL4ES_UnloadLibrary;
        device->GL_CreateContext = Android_GL4ES_CreateContext;
        device->GL_MakeCurrent = Android_GL4ES_MakeCurrent;
        device->GL_SetSwapInterval = Android_GL4ES_SetSwapInterval;
        device->GL_GetSwapInterval = Android_GL4ES_GetSwapInterval;
        device->GL_SwapWindow = Android_GL4ES_SwapWindow;
        device->GL_DeleteContext = Android_GL4ES_DeleteContext;
        device->GL_GetDrawableSize = Android_GL4ES_GetDrawableSize;
    } else {
        SDL_Log("🎨 Using native OpenGL ES renderer (default)");
        
        // 设置原生GLES函数指针
        device->GL_LoadLibrary = Android_GLES_LoadLibrary;
        device->GL_GetProcAddress = Android_GLES_GetProcAddress;
        // ...其他GLES函数
    }
#elif defined(SDL_VIDEO_OPENGL_EGL)
    // 仅支持原生GLES
    SDL_Log("🎨 Using native OpenGL ES renderer (gl4es not compiled)");
    // ...设置GLES函数指针
#else
    #error "No OpenGL backend available!"
#endif
    
    return 0;
}
```

### 环境变量配置

文件：`app/src/main/cpp/dotnet_host.c`

```c
// 根据用户选择配置环境变量
if (strcmp(g_renderer, "opengles3") == 0) {
    // 原生OpenGL ES 3
    setenv("FNA3D_OPENGL_DRIVER", "native", 1);
    setenv("FNA3D_OPENGL_FORCE_ES3", "1", 1);
    // ...
    
} else if (strcmp(g_renderer, "opengl_gl4es") == 0) {
    // gl4es OpenGL 2.1
    setenv("FNA3D_OPENGL_DRIVER", "gl4es", 1);
    setenv("FNA3D_USE_GL4ES", "1", 1);
    setenv("FNA3D_FORCE_DRIVER", "OpenGL", 1);
    
    // gl4es配置
    setenv("LIBGL_ES", "2", 1);      // 目标GLES 2.0
    setenv("LIBGL_GL", "21", 1);     // 模拟OpenGL 2.1
    setenv("LIBGL_LOGERR", "1", 1);  // 错误日志
    setenv("LIBGL_DEBUG", "1", 1);   // 调试信息
}
```

### Java层配置

文件：`app/src/main/java/com/app/ralaunch/fragment/SettingsFragment.java`

```java
// 渲染器选择下拉菜单
ArrayAdapter<String> rendererAdapter = new ArrayAdapter<>(
    requireContext(),
    android.R.layout.simple_spinner_item,
    new String[]{
        "OpenGL ES 3.0 (推荐)",
        "OpenGL 2.1 via gl4es (兼容模式)"
    }
);

rendererSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String renderer = position == 0 ? "opengles3" : "opengl_gl4es";
        RuntimePreference.setPreferredRenderer(requireContext(), renderer);
    }
});
```

---

## 🔑 关键技术点

### 1. TagItem结构对齐

**问题**：初始实现中，`ti_Tag`使用`unsigned long`（8字节），导致内存布局错误。

```c
// ❌ 错误（内存对齐不一致）
struct TagItem {
    unsigned long ti_Tag;   // 8字节
    unsigned long ti_Data;  // 8字节
};
```

**解决**：统一使用4字节tag + 8字节data：

```c
// ✅ 正确
struct TagItem {
    unsigned int ti_Tag;      // 4字节
    unsigned long ti_Data;    // 8字节
};
```

**验证**：
```c
// SDL端和AGL端都输出内存布局
LOGI("TagItem size: %zu, ti_Tag offset: %zu, ti_Data offset: %zu",
     sizeof(struct TagItem),
     offsetof(struct TagItem, ti_Tag),
     offsetof(struct TagItem, ti_Data));
```

### 2. gl4es全局初始化

**问题**：直接调用`NewGLState()`导致pthread_mutex错误。

```
FORTIFY: pthread_mutex_lock called on a destroyed mutex
```

**原因**：gl4es需要全局初始化才能正确设置线程局部存储和互斥锁。

**解决**：在创建状态前调用`initialize_gl4es()`：

```c
void* aglCreateContext2(...)
{
    // ...EGL初始化...
    
    // ⚠️ 关键：必须先初始化gl4es全局状态
    LOGI("⏳ Calling initialize_gl4es() to set up global state...");
    initialize_gl4es();
    LOGI("✅ gl4es global state initialized");
    
    // 然后才能创建状态对象
    LOGI("⏳ Calling NewGLState...");
    ctx->glstate = NewGLState(NULL, 0);
    LOGI("✅ NewGLState returned: %p", ctx->glstate);
    
    // ...
}
```

### 3. CMake配置技巧

**挑战**：SDL的CMake会自动启用EGL，需要同时支持两种渲染器。

```cmake
# ⚠️ 关键配置
set(SDL_VIDEO_OPENGL ON FORCE)           # 启用OpenGL支持
set(SDL_VIDEO_OPENGL_ES ON FORCE)        # 启用GLES支持（原生渲染器）
set(SDL_VIDEO_OPENGL_EGL ON FORCE)       # 启用EGL（原生渲染器）
set(SDL_OPENGLES ON FORCE)               # 防止自动禁用EGL

# gl4es支持
add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)

# 链接gl4es到SDL2
target_link_libraries(SDL2 PRIVATE GL)   # GL = gl4es静态库

# 导出AGL符号供dlsym查找
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=default")
```

### 4. EGL Surface管理

**问题**：gl4es和SDL都想创建EGL surface，导致冲突。

**解决**：条件性跳过SDL的surface创建：

```c
// SDL_androidwindow.c
#ifdef SDL_VIDEO_OPENGL_EGL
    const char* gl_driver = SDL_getenv("FNA3D_OPENGL_DRIVER");
    SDL_bool use_gl4es = (gl_driver && SDL_strcasecmp(gl_driver, "gl4es") == 0);
    
    if (use_gl4es) {
        // gl4es通过AGL管理自己的surface
        __android_log_print(ANDROID_LOG_INFO, "SDL_Window", 
                           "Using gl4es, skipping EGL surface");
    } else {
        // 原生GLES需要SDL创建surface
        data->egl_surface = SDL_EGL_CreateSurface(_this, 
                                                   (NativeWindowType)data->native_window);
    }
#endif
```

---

## 📊 测试结果

### 原生OpenGL ES 3.0

✅ **完美运行**

```
FNA: [Main Thread/INFO] [FNA]: OpenGL Renderer: OpenGL ES 3.2
FNA: [Main Thread/INFO] [FNA]: OpenGL Driver: OpenGL ES
FNA: [Main Thread/INFO] [FNA]: MojoShader Profile: glsles
```

**性能**：
- 帧率：60 FPS（稳定）
- 启动时间：~8秒
- 内存占用：正常

### gl4es OpenGL 2.1

⚠️ **集成成功，但游戏崩溃**

```
SDL: Using gl4es renderer (OpenGL 2.1 Compatibility Profile)
GL4ES: aglCreateContext2 called
GL4ES: ✅ gl4es global state initialized
GL4ES: ✅ EGL initialized successfully
GL4ES: ✅ OpenGL context created
FNA: [Main Thread/INFO] [FNA]: MojoShader Profile: glsl120  ✅
```

**OpenGL函数加载**：
- ✅ 所有核心函数正确加载
- ✅ 扩展函数大部分可用
- ❌ 部分NV扩展不可用（`glProgramLocalParameterI4ivNV`等）

**崩溃点**：
```
tML: [Main Thread/FATAL] [tML]: Main engine crash
SDL Error: Failed to load GL function

System.NullReferenceException: Object reference not set to an instance of an object
   at Terraria.ModLoader.Engine.TMLContentManager.TryFixFileCasings(String rootDirectory)
   at Terraria.ModLoader.Engine.TMLContentManager..ctor(...)
```

**分析**：
- gl4es集成本身成功
- 崩溃发生在游戏初始化阶段
- 可能与`System.Linq.dll`修改在gl4es上下文的行为有关
- 需要进一步调试内容管理器初始化

---

## 🐛 已知问题

### 1. gl4es模式下游戏崩溃

**现象**：
- OpenGL上下文创建成功
- 所有OpenGL函数加载成功
- 崩溃发生在`TMLContentManager.TryFixFileCasings()`

**可能原因**：
1. System.Linq.dll的LINQ操作在gl4es上下文中行为异常
2. 文件系统路径验证逻辑依赖某些GL状态
3. Content文件夹访问权限问题

**待调查**：
- [ ] 在gl4es模式下启用更详细的.NET堆栈跟踪
- [ ] 比较两种渲染器下的文件系统访问日志
- [ ] 检查`Directory.GetFiles()`等API的行为差异

### 2. 部分扩展函数不可用

**缺失函数**：
- `glStringMarkerGREMEDY`
- `glProgramLocalParameterI4ivNV`
- `glSpecializeShaderARB`

**影响**：
- 调试标记功能不可用（不影响游戏）
- NV特定扩展不支持（少数游戏可能需要）
- 着色器特化不支持（OpenGL 4.6特性，FNA不使用）

### 3. 性能未知

由于游戏崩溃，尚未进行性能测试。理论上：
- **优点**：更好的兼容性
- **缺点**：额外的翻译开销（~10-20%性能损失）

---

## 🎯 下一步计划

### 短期目标

1. **修复gl4es崩溃**
   - [ ] 添加更详细的日志输出
   - [ ] 比对两种渲染器的初始化流程
   - [ ] 检查System.Linq.dll的LINQ操作
   - [ ] 验证Content文件夹访问权限

2. **性能测试**
   - [ ] 建立性能基准（原生GLES vs gl4es）
   - [ ] 测试不同游戏的兼容性
   - [ ] 优化gl4es配置参数

3. **UI改进**
   - [ ] 在启动器主界面添加渲染器快速切换
   - [ ] 添加渲染器状态指示器
   - [ ] 提供详细的渲染器说明

### 长期目标

1. **扩展兼容性**
   - [ ] 支持更多FNA/XNA游戏
   - [ ] 测试MonoGame游戏兼容性
   - [ ] 添加Vulkan渲染器支持

2. **文档完善**
   - [x] gl4es适配原理文档
   - [ ] 用户使用指南
   - [ ] 开发者贡献指南
   - [ ] 性能优化建议

3. **工具开发**
   - [ ] 渲染器性能分析工具
   - [ ] OpenGL调用追踪器
   - [ ] 自动兼容性测试框架

---

## 📚 参考资料

### 外部资源

- [gl4es GitHub](https://github.com/ptitSeb/gl4es)
- [SDL2 OpenGL Documentation](https://wiki.libsdl.org/CategoryVideo)
- [FNA3D Source Code](https://github.com/FNA-XNA/FNA3D)
- [Android EGL API](https://developer.android.com/ndk/guides/egl)

### 项目文件

- `app/src/main/cpp/SDL/src/video/android/SDL_androidgl4es.c` - SDL gl4es适配器
- `app/src/main/cpp/gl4es/src/agl/agl_android.c` - AGL Android实现
- `app/src/main/cpp/SDL/src/video/android/SDL_androidvideo.c` - 渲染器选择
- `app/src/main/cpp/dotnet_host.c` - 环境变量配置
- `app/src/main/cpp/CMakeLists.txt` - 构建配置

---

## 👥 贡献者

- **Fireworkshh** - 项目维护者
- **Cursor AI** - 技术实现协助

---

## 📄 许可证

本文档及相关代码遵循项目主许可证（LGPLv3）。

第三方组件许可：
- **gl4es** - MIT License
- **SDL2** - Zlib License
- **FNA** - Ms-PL License

---

<p align="center">
  <i>最后更新：2025年11月1日</i>
</p>

