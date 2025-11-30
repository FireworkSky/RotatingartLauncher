# OSMesa 视频接口集成到 Zink 渲染器

## 概述

本文档说明如何将 OSMesa 视频接口集成到 zink 渲染器中，实现离屏渲染到 Android Native Window。

## 架构

```
┌─────────────────┐
│   Zink Driver   │
│  (OpenGL 4.6)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   OSMesa API    │
│  (Off-screen)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  OSM Bridge     │
│  (ANativeWindow)│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Android Surface │
└─────────────────┘
```

## 文件结构

### C++ 文件

- `app/src/main/cpp/GL/osmesa.h` - OSMesa API 头文件
- `app/src/main/cpp/utils/loader_dlopen.h/c` - 动态库加载工具
- `app/src/main/cpp/osmesa/osmesa_loader.h/c` - OSMesa 库加载器
- `app/src/main/cpp/osmesa/osm_bridge.h/c` - OSMesa 与 Android Native Window 桥接
- `app/src/main/cpp/osmesa/osm_renderer.h/c` - OSMesa 渲染器封装
- `app/src/main/cpp/osmesa/osm_renderer_jni.c` - JNI 接口

### Java 文件

- `app/src/main/java/com/app/ralaunch/renderer/OSMRenderer.java` - Java 封装类

## 使用方法

### 1. 在 Java 中初始化 OSMesa 渲染器

```java
import com.app.ralaunch.renderer.OSMRenderer;
import android.view.Surface;

// 检查 OSMesa 是否可用
if (OSMRenderer.isAvailable()) {
    // 获取 Surface（例如从 SurfaceView 或 TextureView）
    Surface surface = surfaceView.getHolder().getSurface();
    
    // 初始化 OSMesa 渲染器
    if (OSMRenderer.init(surface)) {
        Log.i(TAG, "OSMesa renderer initialized");
    }
}
```

### 2. 在渲染循环中交换缓冲区

```java
// 在渲染循环中
while (rendering) {
    // ... OpenGL 渲染调用 ...
    
    // 交换缓冲区（渲染到窗口）
    OSMRenderer.swapBuffers();
}
```

### 3. 清理资源

```java
// 应用退出时
OSMRenderer.cleanup();
```

### 4. 设置窗口（窗口大小改变时）

```java
// 窗口大小改变时
Surface newSurface = surfaceView.getHolder().getSurface();
OSMRenderer.setWindow(newSurface);
```

## 环境变量配置

为了使用 OSMesa 作为 zink 的视频接口，需要设置以下环境变量：

```java
// 在 RendererLoader 中设置
RendererLoader.nativeSetEnv("GALLIUM_DRIVER", "zink");
RendererLoader.nativeSetEnv("MESA_GL_VERSION_OVERRIDE", "4.6");
RendererLoader.nativeSetEnv("MESA_GLSL_VERSION_OVERRIDE", "460");
```

## 集成到渲染器系统

### 在 RendererLoader 中集成

修改 `RendererLoader.java`，在选择 zink 渲染器时初始化 OSMesa：

```java
public static boolean loadRenderer(Context context, String rendererId) {
    // ... 现有的渲染器加载逻辑 ...
    
    if (RendererConfig.RENDERER_ZINK.equals(rendererId)) {
        // 初始化 OSMesa 渲染器
        if (OSMRenderer.isAvailable()) {
            // 获取 Surface（需要从 Activity 传递）
            Surface surface = getSurfaceFromActivity(context);
            if (surface != null) {
                OSMRenderer.init(surface);
            }
        }
    }
    
    // ... 其他渲染器逻辑 ...
}
```

## 工作原理

1. **OSMesa 库加载**：`osmesa_loader.c` 动态加载 `libOSMesa_25.so` 或 `libOSMesa.so`
2. **上下文创建**：`osm_bridge.c` 创建 OSMesa 上下文
3. **窗口绑定**：将 OSMesa 上下文绑定到 Android Native Window 的缓冲区
4. **渲染**：Zink 通过 OSMesa API 进行离屏渲染
5. **显示**：通过 `ANativeWindow_lock/unlockAndPost` 将渲染结果显示到屏幕

## 优势

- **离屏渲染**：OSMesa 支持完全离屏渲染，不依赖 EGL
- **Zink 兼容**：Zink 可以通过 OSMesa 接口工作，无需 EGL
- **灵活性**：可以轻松切换渲染目标
- **性能**：直接渲染到 Native Window，减少拷贝

## 注意事项

1. **库依赖**：确保 `libOSMesa_25.so` 或 `libOSMesa.so` 在 `jniLibs` 中
2. **线程安全**：OSMesa 上下文是线程本地的，需要在同一线程中使用
3. **窗口管理**：窗口大小改变时需要重新设置窗口
4. **资源清理**：应用退出时必须调用 `cleanup()` 释放资源

## 故障排除

### OSMesa 库加载失败

- 检查 `jniLibs/arm64-v8a/libOSMesa_25.so` 是否存在
- 检查库的架构是否匹配（arm64-v8a, armeabi-v7a 等）

### 渲染不显示

- 确保 Surface 有效且未释放
- 检查 `osm_renderer_swap_buffers()` 是否被调用
- 查看 logcat 中的 OSMesa 相关日志

### 性能问题

- 调整 swap interval（vsync）
- 检查缓冲区大小是否合理
- 考虑使用多线程渲染

## 参考

- [Mesa OSMesa 文档](https://docs.mesa3d.org/osmesa.html)
- [PojavLauncher OSMesa 实现](https://github.com/PojavLauncherTeam/PojavLauncher)



