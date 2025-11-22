# ANGLE 渲染器修复说明

## 问题描述

ANGLE 渲染器需要同时加载两个库才能正常工作：
1. `libEGL_angle.so` - EGL 实现
2. `libGLESv2_angle.so` - OpenGL ES 实现

之前的实现只加载了 EGL 库，导致 ANGLE 渲染器无法正常工作。

## 修复内容

### 1. 添加 GLES 库句柄

```c
static void* egl_handle = NULL;
static void* gles_handle = NULL;  /* For ANGLE: libGLESv2_angle.so */
static bool is_angle = false;
```

### 2. 标记 ANGLE 渲染器

在 `get_egl_library_for_renderer()` 函数中：

```c
} else if (SDL_strcasecmp(renderer, "angle") == 0) {
    current_renderer = "angle";
    is_gl4es = false;
    is_angle = true;  // 标记为 ANGLE
    return "libEGL_angle.so";
}
```

### 3. 加载 ANGLE GLES 库

在 EGL 库加载成功后：

```c
/* Special handling for ANGLE: load GLESv2 library */
if (is_angle) {
    const char* gles_lib = SDL_getenv("LIBGL_GLES");
    if (gles_lib == NULL || gles_lib[0] == '\0') {
        gles_lib = "libGLESv2_angle.so";
    }

    /* Load GLES library with RTLD_GLOBAL to make symbols available */
    gles_handle = dlopen(gles_lib, RTLD_GLOBAL | RTLD_NOW);
    if (gles_handle == NULL) {
        LOGE("✗ Failed to load ANGLE GLES library %s: %s", gles_lib, dlerror());
        dlclose(egl_handle);
        egl_handle = NULL;
        return false;
    }
    LOGI("✓ Loaded ANGLE GLES library: %s", gles_lib);
}
```

## 关键点

1. **RTLD_GLOBAL**：使用 `RTLD_GLOBAL` 标志加载 `libGLESv2_angle.so`，确保其符号在全局符号表中可用
2. **RTLD_NOW**：立即解析所有符号，确保库完整加载
3. **环境变量**：支持通过 `LIBGL_GLES` 环境变量自定义 GLES 库路径（默认为 `libGLESv2_angle.so`）

## 使用方法

### 方式 1：通过渲染器名称

```java
// Java 层配置
RendererConfig.setRenderer(context, RendererConfig.RENDERER_ANGLE);
```

这会自动设置：
- `RALCORE_EGL=libEGL_angle.so`
- `LIBGL_GLES=libGLESv2_angle.so`

### 方式 2：手动设置环境变量

```bash
export RALCORE_RENDERER=angle
# 或
export RALCORE_EGL=libEGL_angle.so
export LIBGL_GLES=libGLESv2_angle.so
```

## 验证方法

查看日志输出：

```
SDL_EGL_Loader: ================================================================
SDL_EGL_Loader:   SDL Android EGL Dynamic Loader
SDL_EGL_Loader:   RALCORE_EGL: libEGL_angle.so
SDL_EGL_Loader: ================================================================
SDL_EGL_Loader: ✓ Loaded EGL library: libEGL_angle.so
SDL_EGL_Loader: ✓ Loaded ANGLE GLES library: libGLESv2_angle.so
SDL_EGL_Loader: ✅ EGL loader initialized: angle
SDL_Android: ✓ Loaded EGL renderer: angle
```

## ANGLE 工作原理

ANGLE (Almost Native Graphics Layer Engine) 的架构：

```
应用 OpenGL ES API 调用
    ↓
libGLESv2_angle.so (OpenGL ES → 中间层)
    ↓
libEGL_angle.so (EGL 接口)
    ↓
Vulkan / Metal / D3D11 (后端)
    ↓
GPU
```

## 对比：gl4es vs ANGLE

| 特性 | gl4es | ANGLE |
|------|-------|-------|
| 库数量 | 1个（libgl4es.so） | 2个（libEGL + libGLESv2） |
| API | OpenGL 2.1 → GLES 2.0 | GLES 2.0/3.0 → Vulkan/D3D |
| 后端 | GLES 2.0 | Vulkan / D3D11 / Metal |
| 使用场景 | 老游戏兼容 | 现代图形 API 转换 |
| 性能 | 中等 | 高（Vulkan后端） |

## 修改的文件

- `SDL_android_egl_loader.c` - 添加 ANGLE 双库加载逻辑
- `SDL_android_egl_loader.h` - 无需修改（接口保持不变）

## 测试状态

- [x] 编译通过
- [ ] 运行时测试
- [ ] 性能测试
- [ ] 与其他渲染器兼容性测试

## 已知问题

无

## 参考

- ANGLE 项目：https://github.com/google/angle
- ANGLE 文档：https://chromium.googlesource.com/angle/angle/+/main/doc/
