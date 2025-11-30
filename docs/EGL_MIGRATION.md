# SDL GL4ES EGL 迁移说明

## 概述

本次重构将SDL的OpenGL后端从**AGL (Amiga GL API)** 迁移到 **EGL (Embedded-System Graphics Library)** 标准API。

这个改动使得渲染器可以通过环境变量灵活切换，参考了 PojavLauncher 的架构设计。

---

## 🎯 主要改动

### 1. SDL GL 后端改造 (`SDL_androidgl4es.c`)

**文件位置**: `app/src/main/cpp/SDL/src/video/android/SDL_androidgl4es.c`

#### ✅ 从 AGL 到 EGL 的转变

**之前 (AGL)**:
```c
extern void* aglCreateContext2(...);
extern void aglDestroyContext(void* context);
extern void aglMakeCurrent(void* context);
extern void aglSwapBuffers(void);
```

**现在 (EGL)**:
```c
static EGLBoolean (*eglMakeCurrent_p)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
static EGLContext (*eglCreateContext_p)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
static EGLBoolean (*eglSwapBuffers_p)(EGLDisplay, EGLSurface);
// ... 更多 EGL 函数指针
```

#### 🔧 核心功能实现

1. **动态加载 EGL 库** (`load_egl_library()`)
   - 从环境变量 `FNA3D_OPENGL_LIBRARY` 读取 EGL 库路径
   - 默认使用 `libEGL.so` (Android 系统 EGL)
   - 支持自定义 EGL 实现（如 gl4es 的 libEGL）

2. **EGL 上下文管理** (`SDL_EGLContext` 结构)
   ```c
   typedef struct {
       EGLContext context;
       EGLSurface surface;
       EGLConfig config;
       EGLint format;
       ANativeWindow* native_window;
   } SDL_EGLContext;
   ```

3. **支持多种 OpenGL API 绑定**
   - `EGL_OPENGL_ES_API` - OpenGL ES (默认)
   - `EGL_OPENGL_API` - Desktop OpenGL (通过环境变量)

4. **环境变量驱动的配置**
   - `FNA3D_OPENGL_LIBRARY` - EGL 库路径
   - `FNA3D_OPENGL_DRIVER` - 驱动类型 (native/gl4es/desktop)
   - `LIBGL_ES` - OpenGL ES 版本 (1/2/3)
   - `FORCE_VSYNC` - 强制垂直同步

---

### 2. 渲染器环境变量系统 (`RuntimePreference.java`)

**文件位置**: `app/src/main/java/com/app/ralaunch/utils/RuntimePreference.java`

#### 🎨 渲染器类型

```java
public static final String RENDERER_OPENGLES3 = "opengles3";      // 原生 OpenGL ES 3
public static final String RENDERER_OPENGL_GL4ES = "opengl_gl4es"; // gl4es 翻译层
public static final String RENDERER_VULKAN = "vulkan";            // Vulkan (实验性)
public static final String RENDERER_AUTO = "auto";                // 自动选择
```

#### ⚙️ 环境变量配置

**原生 OpenGL ES 3** (推荐，性能最佳):
```bash
FNA3D_FORCE_DRIVER=OpenGL
FNA3D_OPENGL_DRIVER=native
FNA3D_OPENGL_LIBRARY=libEGL.so
LIBGL_ES=3
FNA3D_OPENGL_FORCE_ES3=1
```

**gl4es 渲染器** (兼容性最佳):
```bash
FNA3D_FORCE_DRIVER=OpenGL
FNA3D_OPENGL_DRIVER=gl4es
FNA3D_OPENGL_LIBRARY=libEGL.so
LIBGL_ES=3
LIBGL_GL=30
LIBGL_BATCH=1
LIBGL_LOGERR=1
```

**Vulkan 渲染器** (实验性):
```bash
FNA3D_FORCE_DRIVER=Vulkan
# 其他 OpenGL 变量全部清除
```

---

## 🔄 与 PojavLauncher 的对比

### 相似之处

1. ✅ **EGL 函数动态加载**
   - 两者都使用函数指针通过 `eglGetProcAddress` 加载 EGL API
   - 支持自定义 EGL 库路径

2. ✅ **环境变量驱动**
   - PojavLauncher: `POJAV_RENDERER`, `POJAVEXEC_EGL`
   - 我们的实现: `FNA3D_OPENGL_DRIVER`, `FNA3D_OPENGL_LIBRARY`

3. ✅ **多渲染器支持**
   - 都支持 gl4es、原生 OpenGL ES、Vulkan

### 差异之处

| 特性 | PojavLauncher | 本项目 |
|------|--------------|--------|
| **架构** | Bridge Table 抽象层 | 直接 EGL API |
| **上下文管理** | `basic_render_window_t` | `SDL_EGLContext` |
| **表面切换** | 支持运行时窗口切换 | 固定窗口（FNA 特性） |
| **目标** | Minecraft (LWJGL) | FNA 游戏框架 |

---

## 📦 编译要求

### CMakeLists.txt 配置

确保以下配置存在：

```cmake
# app/src/main/cpp/CMakeLists.txt
set(SDL_VIDEO_OPENGL ON CACHE BOOL "Enable OpenGL support" FORCE)
set(SDL_VIDEO_OPENGL_ES ON CACHE BOOL "Enable OpenGL ES" FORCE)
set(SDL_VIDEO_OPENGL_EGL ON CACHE BOOL "Enable EGL" FORCE)
add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)

# GL 和 shaderconv 必须是静态库（已静态链接到 libmain.so）
add_library(GL STATIC ...)
add_library(shaderconv STATIC ...)
```

### 依赖库

- **Android NDK 28+**
- **EGL/egl.h** (NDK 自带)
- **GL4ESPlus** (静态库)
- **FNA3D**
- **SDL2**

---

## 🚀 使用方法

### 在 Java 代码中设置渲染器

```java
// 设置渲染器偏好
RuntimePreference.setRenderer(context, RuntimePreference.RENDERER_OPENGLES3);

// 应用环境变量（在游戏启动前调用）
RuntimePreference.applyRendererEnvironment(context);

// 启动游戏
GameLauncher.launch(context, gameInfo);
```

### 运行时切换渲染器

用户可以在设置界面选择渲染器，下次游戏启动时生效：

1. 打开设置 → 开发者选项
2. 选择渲染器类型
3. 重启游戏

---

## 🐛 已知问题

### ⚠️ gl4es 使用注意事项

当前 gl4es 是**静态链接**到 `libmain.so`，并不提供独立的 `libEGL.so`。

如果要使用 gl4es 的 EGL 实现，有两种方案：

**方案 A**: 使用系统 EGL + gl4es 的 OpenGL 函数
```bash
FNA3D_OPENGL_LIBRARY=libEGL.so         # 系统 EGL
FNA3D_OPENGL_DRIVER=gl4es              # gl4es 提供 GL 函数
```

**方案 B**: 编译 gl4es 为共享库并导出 EGL
```cmake
# 需要修改 gl4es 的 CMakeLists.txt
add_library(GL SHARED ...)
install(TARGETS GL DESTINATION ${CMAKE_INSTALL_LIBDIR})
```

### 🔧 调试建议

启用详细日志：
```bash
LIBGL_DEBUG=1      # gl4es 调试日志
LIBGL_LOGERR=1     # gl4es 错误日志
```

查看 logcat 输出：
```bash
adb logcat -s SDL_GL4ES_EGL:V FNA3D:V
```

---

## 📝 迁移检查清单

- [x] SDL GL 后端改为 EGL API
- [x] 动态加载 EGL 函数指针
- [x] 支持环境变量配置渲染器
- [x] 更新 RuntimePreference 环境变量逻辑
- [x] 添加 gl4es 性能优化选项
- [x] 支持原生 OpenGL ES 3
- [ ] 测试原生渲染器
- [ ] 测试 gl4es 渲染器
- [ ] 测试 Vulkan 渲染器
- [ ] 性能对比测试

---

## 🎯 下一步计划

1. **构建测试**: 编译验证所有改动
2. **功能测试**: 测试三种渲染器模式
3. **性能测试**: 对比原生 vs gl4es 性能
4. **Bug 修复**: 根据测试结果修复问题
5. **文档完善**: 更新用户手册

---

## 📚 参考资料

- [EGL 1.5 Specification](https://www.khronos.org/registry/EGL/specs/eglspec.1.5.pdf)
- [PojavLauncher EGL Implementation](https://github.com/PojavLauncherTeam/PojavLauncher)
- [GL4ES Documentation](https://github.com/ptitSeb/gl4es)
- [FNA3D Graphics API](https://github.com/FNA-XNA/FNA3D)

---

**更新日期**: 2025-01-12
**版本**: v1.0 (EGL Migration)
