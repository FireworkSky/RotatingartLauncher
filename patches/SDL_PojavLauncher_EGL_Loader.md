# SDL PojavLauncher-风格 EGL 动态加载器实现

## 概述

参考 PojavLauncher 的实现，为 SDL 添加了动态 EGL 加载功能，支持运行时切换渲染器（gl4es, ANGLE, Zink 等），无需重新编译。

## 核心原理

### PojavLauncher 的方法

1. **函数指针表系统**
   - 所有 EGL 函数通过函数指针调用（如 `eglMakeCurrent_p`）
   - 使用 `eglGetProcAddress` 动态加载所有 EGL 函数
   - 不直接链接 libEGL.so

2. **环境变量驱动**
   - `POJAVEXEC_EGL` - 指定 EGL 库路径
   - `POJAV_RENDERER` - 渲染器类型
   - `LIBGL_ES` - ES 版本（gl4es 需要设置为 2）

3. **gl4es 特殊处理**
   - gl4es 提供桌面 OpenGL 2.1 API
   - 但底层需要 **GLES 2.0 上下文**
   - 通过 `eglBindAPI(EGL_OPENGL_ES_API)` + `EGL_CONTEXT_CLIENT_VERSION=2`

## 实现文件

### 1. SDL_android_egl_loader.h
```c
// 头文件，定义 EGL 函数指针和加载器 API
extern EGLBoolean (*SDL_eglMakeCurrent_p)(...)
extern bool SDL_Android_LoadEGL(void);
extern bool SDL_Android_IsGL4ES(void);
```

### 2. SDL_android_egl_loader.c
```c
// 实现文件，包含：
// - EGL 动态加载逻辑
// - 渲染器检测和映射
// - gl4es 环境变量配置
```

### 3. SDL_androidgl.c (修改)
```c
int Android_GLES_LoadLibrary(_THIS, const char *path) {
    // 使用 PojavLauncher 风格的动态加载
    SDL_Android_LoadEGL();
    return SDL_EGL_LoadLibrary(_this, NULL, ...);
}
```

### 4. SDL_egl.c (修改)
```c
#ifdef SDL_VIDEO_DRIVER_ANDROID
    // 检测 gl4es 并强制创建 ES2 上下文
    if (SDL_Android_IsGL4ES() && !profile_es) {
        // 创建 GLES 2.0 上下文而非桌面 OpenGL
        attribs[0] = EGL_CONTEXT_CLIENT_VERSION;
        attribs[1] = 2;
    }
#endif
```

### 5. FNA3D_Driver_OpenGL.c (修改)
```c
// 检测 gl4es 并设置兼容性模式
if (SDL_getenv("FNA3D_OPENGL_DRIVER") == "gl4es") {
    forceCompat = 1;  // OpenGL 2.1 兼容模式
}
```

## 环境变量

### RALCORE_EGL
直接指定 EGL 库路径（最高优先级）
```bash
export RALCORE_EGL=libgl4es.so
```

### RALCORE_RENDERER
指定渲染器类型
```bash
export RALCORE_RENDERER=opengles2  # 使用 gl4es
export RALCORE_RENDERER=vulkan_zink  # 使用 Zink
```

### FNA3D_OPENGL_DRIVER
FNA3D 提示
```bash
export FNA3D_OPENGL_DRIVER=gl4es
```

### LIBGL_ES (自动设置)
gl4es 版本控制，加载器会自动设置为 2

## 渲染器映射

| 渲染器名称 | 库文件 | 说明 |
|-----------|--------|------|
| gl4es | libgl4es.so | OpenGL 2.1 → GLES 2.0 转换层 |
| angle | libEGL_angle.so | ANGLE (OpenGL ES → D3D/Vulkan) |
| zink | libOSMesa.so | OpenGL → Vulkan |
| native | libEGL.so | 系统原生 EGL |

## gl4es 工作流程

1. **加载器阶段**
   ```
   检测到 gl4es → 加载 libgl4es.so → 设置 LIBGL_ES=2
   ```

2. **FNA3D 阶段**
   ```
   检测到 gl4es → forceCompat=1 → SDL_GL_CONTEXT_PROFILE_COMPATIBILITY
   ```

3. **SDL EGL 阶段**
   ```
   检测到 gl4es → 强制 ES2 上下文:
   eglBindAPI(EGL_OPENGL_ES_API)
   eglCreateContext(..., EGL_CONTEXT_CLIENT_VERSION=2, ...)
   ```

4. **运行时**
   ```
   应用调用 OpenGL 2.1 API → gl4es 转换 → GLES 2.0 → GPU
   ```

## 关键差异：SDL vs PojavLauncher

### PojavLauncher (LWJGL3)
- 所有 EGL 调用都通过函数指针
- Java 层也使用 JNI 函数指针
- 完全隔离的 EGL 实现

### SDL (本实现)
- 动态加载器初始化 EGL
- SDL 内部仍使用标准 EGL 函数
- 通过 NULL path 跳过重复加载

## 编译说明

新增文件会被 SDL 构建系统自动包含。如果需要手动指定：

```cmake
# CMakeLists.txt
set(SDL_ANDROID_SOURCES
    SDL/src/video/android/SDL_android_egl_loader.c
    SDL/src/video/android/SDL_android_egl_loader.h
    # ... 其他文件
)
```

## 测试

### 测试 gl4es
```bash
adb shell setprop debug.ralcore.renderer opengles2
# 或
export FNA3D_OPENGL_DRIVER=gl4es
```

预期日志：
```
SDL_EGL_Loader: ✓ Loaded EGL library: libgl4es.so
SDL_EGL_Loader: ✓ gl4es environment configured (LIBGL_ES=2)
SDL_Android: ✓ Loaded EGL renderer: gl4es
FNA3D: gl4es detected, using desktop OpenGL compatibility profile
SDL_EGL: Creating GLES 2.0 context for gl4es compatibility layer
FNA: OpenGL Renderer: ... (via gl4es)
FNA: MojoShader Profile: glsl120
```

### 测试原生 GLES3
```bash
# 不设置任何环境变量，或：
export RALCORE_RENDERER=native
```

预期日志：
```
SDL_EGL_Loader: ✓ Loaded EGL library (fallback): libEGL.so
SDL_Android: ✓ Loaded EGL renderer: native
FNA: OpenGL Renderer: Adreno (TM) 740
FNA: OpenGL Driver: OpenGL ES 3.2 ...
FNA: MojoShader Profile: glsles3
```

## 相关文件

- `D:/PojavLauncher-3_openjdk/app_pojavlauncher/src/main/jni/ctxbridges/egl_loader.c` - 参考实现
- `D:/PojavLauncher-3_openjdk/app_pojavlauncher/src/main/jni/ctxbridges/gl_bridge.c` - gl4es 集成参考

## 已知问题

1. ~~FNA3D 使用 `SDL_GetHint()` 而非 `SDL_getenv()`~~
   - ✅ 已修复：改为 `SDL_getenv()`

2. ~~SDL 为 COMPATIBILITY profile 创建桌面 OpenGL 上下文~~
   - ✅ 已修复：检测 gl4es 并强制 ES2

3. ~~需要验证函数指针在所有 SDL EGL 调用中正确使用~~
   - ⚠️ 当前实现：加载器初始化 → SDL 使用标准函数
   - 📝 可选优化：完全使用函数指针（完整 PojavLauncher 风格）

## 下一步

- [x] 实现动态 EGL 加载器
- [x] 修改 SDL 使用加载器
- [x] 修改 FNA3D 检测 gl4es
- [x] 测试 gl4es 渲染器
- [ ] 测试 ANGLE 渲染器
- [ ] 测试 Zink 渲染器
- [ ] 性能对比测试

## 作者

基于 PojavLauncher 项目的 egl_loader 实现
适配到 SDL + FNA3D 架构
