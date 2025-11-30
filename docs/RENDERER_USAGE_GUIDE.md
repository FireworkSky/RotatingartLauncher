# 渲染器库正确使用指南

基于 FoldCraftLauncher 的实现方式

---

## 🎯 核心原理

FoldCraftLauncher 使用 **环境变量** + **LD_LIBRARY_PATH** 的方式来动态加载渲染器，而不是直接使用 `dlopen` + `LD_PRELOAD`。

### 关键环境变量

| 环境变量 | 作用 | 示例值 |
|---------|------|--------|
| `POJAV_RENDERER` | 指定渲染器类型 | `opengles2`, `vulkan_zink`, `gallium_virgl` |
| `POJAVEXEC_EGL` | 指定 EGL 库路径 | `libEGL_angle.so`, `libEGL.so` |
| `LIBGL_ES` | OpenGL ES 版本 | `2`, `3` |
| `LIBGL_GLES` | GLES 库名称 | `libGLESv2_angle.so` |
| `LD_LIBRARY_PATH` | 库搜索路径 | `/data/app/.../lib/arm64-v8a` |
| `GALLIUM_DRIVER` | Gallium 驱动名称 | `virpipe`, `zink`, `freedreno` |
| `MESA_LOADER_DRIVER_OVERRIDE` | Mesa 驱动覆盖 | `zink`, `kgsl` |

---

## 📋 渲染器配置表（基于 FoldCraftLauncher）

### 1. gl4es (Holy-GL4ES)

```kotlin
Renderer(
    name = "Holy-GL4ES",
    glName = "libgl4es_114.so",  // 我们复制为 libgl4es.so
    eglName = "libEGL.so",
    path = "",
    id = "gl4es"
)
```

**环境变量设置**：
```java
envMap.put("POJAV_RENDERER", "opengles2");
envMap.put("LIBGL_ES", "2");
envMap.put("LIBGL_MIPMAP", "3");
envMap.put("LIBGL_NORMALIZE", "1");
envMap.put("LIBGL_NOINTOVLHACK", "1");
envMap.put("LIBGL_NOERROR", "1");
```

### 2. ANGLE

```kotlin
Renderer(
    name = "ANGLE",
    glName = "libGLESv2_angle.so",
    eglName = "libEGL_angle.so",
    path = "",
    id = "angle"
)
```

**环境变量设置**：
```java
envMap.put("POJAVEXEC_EGL", "libEGL_angle.so");
envMap.put("LIBGL_GLES", "libGLESv2_angle.so");
```

### 3. Zink

```kotlin
Renderer(
    name = "Zink",
    glName = "libOSMesa_8.so",  // 我们复制为 libOSMesa.so
    eglName = "libEGL.so",
    path = "",
    id = "zink"
)
```

**环境变量设置**：
```java
envMap.put("POJAV_RENDERER", "vulkan_zink");
envMap.put("GALLIUM_DRIVER", "zink");
envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
```

### 4. VirGL

```kotlin
Renderer(
    name = "VirGLRenderer",
    glName = "libOSMesa_81.so",
    eglName = "libEGL.so",
    path = "",
    id = "virgl"
)
```

**环境变量设置**：
```java
envMap.put("POJAV_RENDERER", "gallium_virgl");
envMap.put("GALLIUM_DRIVER", "virpipe");
envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
envMap.put("MESA_GLSL_VERSION_OVERRIDE", "430");
envMap.put("OSMESA_NO_FLUSH_FRONTBUFFER", "1");
envMap.put("VTEST_SOCKET_NAME", cacheDir + "/.virgl_test");
```

### 5. Freedreno

```kotlin
Renderer(
    name = "Freedreno",
    glName = "libOSMesa_8.so",
    eglName = "libEGL.so",
    path = "",
    id = "freedreno"
)
```

**环境变量设置**：
```java
envMap.put("POJAV_RENDERER", "gallium_freedreno");
envMap.put("GALLIUM_DRIVER", "freedreno");
envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
```

---

## 🔧 正确的实现方式

### Java 层实现

**RendererConfig.java** - 更新渲染器配置：

```java
package com.app.ralaunch.renderer;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RendererConfig {

    // 渲染器 ID 常量
    public static final String RENDERER_NATIVE_GLES = "native";
    public static final String RENDERER_GL4ES = "gl4es";
    public static final String RENDERER_ANGLE = "angle";
    public static final String RENDERER_ZINK = "zink";
    public static final String RENDERER_VIRGL = "virgl";
    public static final String RENDERER_FREEDRENO = "freedreno";

    public static class RendererInfo {
        public final String id;
        public final String displayName;
        public final String description;
        public final String glLibrary;        // GL 库文件名
        public final String eglLibrary;       // EGL 库文件名
        public final int minAndroidVersion;

        public RendererInfo(String id, String displayName, String description,
                          String glLibrary, String eglLibrary, int minAndroidVersion) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.glLibrary = glLibrary;
            this.eglLibrary = eglLibrary;
            this.minAndroidVersion = minAndroidVersion;
        }
    }

    private static final RendererInfo[] ALL_RENDERERS = {
        new RendererInfo(
            RENDERER_NATIVE_GLES,
            "Native OpenGL ES",
            "Android 系统原生渲染器，最佳兼容性",
            null,
            null,
            0
        ),
        new RendererInfo(
            RENDERER_GL4ES,
            "Holy GL4ES",
            "OpenGL 2.1 兼容层，支持老旧游戏",
            "libgl4es.so",
            "libEGL.so",
            0
        ),
        new RendererInfo(
            RENDERER_ANGLE,
            "ANGLE",
            "OpenGL ES over Vulkan (Google)",
            "libGLESv2_angle.so",
            "libEGL_angle.so",
            Build.VERSION_CODES.N  // Android 7.0+
        ),
        new RendererInfo(
            RENDERER_ZINK,
            "Zink (Mesa)",
            "OpenGL 4.6 over Vulkan",
            "libOSMesa.so",
            "libEGL.so",
            Build.VERSION_CODES.N  // Android 7.0+
        ),
        new RendererInfo(
            RENDERER_VIRGL,
            "VirGL Renderer",
            "Gallium3D VirGL (OpenGL 4.3)",
            "libOSMesa.so",
            "libEGL.so",
            Build.VERSION_CODES.N
        ),
        new RendererInfo(
            RENDERER_FREEDRENO,
            "Freedreno (Adreno)",
            "Mesa Freedreno for Qualcomm Adreno",
            "libOSMesa.so",
            "libEGL.so",
            Build.VERSION_CODES.N
        )
    };

    /**
     * 获取渲染器环境变量配置
     */
    public static Map<String, String> getRendererEnv(Context context, String rendererId) {
        Map<String, String> envMap = new HashMap<>();

        switch (rendererId) {
            case RENDERER_GL4ES:
                envMap.put("POJAV_RENDERER", "opengles2");
                envMap.put("LIBGL_ES", "2");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_NOERROR", "1");
                break;

            case RENDERER_ANGLE:
                envMap.put("POJAVEXEC_EGL", "libEGL_angle.so");
                envMap.put("LIBGL_GLES", "libGLESv2_angle.so");
                break;

            case RENDERER_ZINK:
                envMap.put("POJAV_RENDERER", "vulkan_zink");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
                envMap.put("force_glsl_extensions_warn", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                break;

            case RENDERER_VIRGL:
                envMap.put("POJAV_RENDERER", "gallium_virgl");
                envMap.put("GALLIUM_DRIVER", "virpipe");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "430");
                envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
                envMap.put("OSMESA_NO_FLUSH_FRONTBUFFER", "1");
                envMap.put("VTEST_SOCKET_NAME",
                    new File(context.getCacheDir(), ".virgl_test").getAbsolutePath());
                break;

            case RENDERER_FREEDRENO:
                envMap.put("POJAV_RENDERER", "gallium_freedreno");
                envMap.put("GALLIUM_DRIVER", "freedreno");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GLSL_CACHE_DIR", context.getCacheDir().getAbsolutePath());
                break;

            case RENDERER_NATIVE_GLES:
            default:
                // Native 渲染器不需要额外环境变量
                break;
        }

        return envMap;
    }

    /**
     * 获取兼容的渲染器列表
     */
    public static List<RendererInfo> getCompatibleRenderers(Context context) {
        List<RendererInfo> compatible = new ArrayList<>();
        int currentVersion = Build.VERSION.SDK_INT;
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;

        for (RendererInfo renderer : ALL_RENDERERS) {
            // 检查 Android 版本
            if (currentVersion < renderer.minAndroidVersion) {
                continue;
            }

            // 检查库文件是否存在（native 渲染器除外）
            if (renderer.id.equals(RENDERER_NATIVE_GLES)) {
                compatible.add(renderer);
                continue;
            }

            if (renderer.glLibrary != null) {
                File glLib = new File(nativeLibDir, renderer.glLibrary);
                File eglLib = new File(nativeLibDir, renderer.eglLibrary);

                if (glLib.exists() && eglLib.exists()) {
                    compatible.add(renderer);
                }
            }
        }

        return compatible;
    }
}
```

---

## 🚀 GameActivity 集成

**更新 GameActivity.java**：

```java
@Override
public void loadLibraries() {
    try {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String rendererId = prefs.getString("renderer", RendererConfig.RENDERER_NATIVE_GLES);

        AppLogger.info(TAG, "================================================");
        AppLogger.info(TAG, "  Renderer Configuration");
        AppLogger.info(TAG, "  Selected: " + rendererId);
        AppLogger.info(TAG, "================================================");

        // 设置渲染器环境变量
        Map<String, String> rendererEnv = RendererConfig.getRendererEnv(this, rendererId);
        for (Map.Entry<String, String> entry : rendererEnv.entrySet()) {
            setEnvironmentVariable(entry.getKey(), entry.getValue());
            AppLogger.info(TAG, "ENV: " + entry.getKey() + "=" + entry.getValue());
        }

    } catch (Exception e) {
        AppLogger.error(TAG, "Renderer configuration error: " + e.getMessage());
    }

    // 加载 SDL 和其他库
    super.loadLibraries();
}

// 添加辅助方法
private void setEnvironmentVariable(String key, String value) {
    try {
        Os.setenv(key, value, true);
    } catch (Exception e) {
        AppLogger.error(TAG, "Failed to set env " + key + ": " + e.getMessage());
    }
}
```

---

## ⚙️ SDL 层适配

**更新 SDL_androidvideo.c**：

```c
/* 从环境变量读取渲染器配置 */
{
    const char* pojav_renderer = SDL_getenv("POJAV_RENDERER");
    const char* renderer_name;

    // 根据 POJAV_RENDERER 映射到 SDL 渲染器名称
    if (pojav_renderer && strcmp(pojav_renderer, "opengles2") == 0) {
        renderer_name = "gl4es";
    } else if (pojav_renderer && strcmp(pojav_renderer, "vulkan_zink") == 0) {
        renderer_name = "zink";
    } else if (pojav_renderer && strcmp(pojav_renderer, "gallium_virgl") == 0) {
        renderer_name = "virgl";
    } else if (pojav_renderer && strcmp(pojav_renderer, "gallium_freedreno") == 0) {
        renderer_name = "freedreno";
    } else {
        // 检查 POJAVEXEC_EGL 是否设置了 ANGLE
        const char* egl_lib = SDL_getenv("POJAVEXEC_EGL");
        if (egl_lib && strstr(egl_lib, "angle")) {
            renderer_name = "angle";
        } else {
            renderer_name = "native";
        }
    }

    SDL_LogInfo(SDL_LOG_CATEGORY_VIDEO,
                "POJAV_RENDERER=%s, using SDL renderer: %s",
                pojav_renderer ? pojav_renderer : "none",
                renderer_name);

    // 动态加载渲染器（基于环境变量，库已通过 LD_LIBRARY_PATH 可见）
    if (!Android_LoadRenderer(renderer_name)) {
        SDL_LogWarn(SDL_LOG_CATEGORY_VIDEO,
                    "Failed to load renderer '%s', falling back to native",
                    renderer_name);
        Android_LoadRenderer("native");
    }

    /* 设置 GL 函数指针 */
    if (!Android_SetupGLFunctions(device)) {
        SDL_LogError(SDL_LOG_CATEGORY_VIDEO, "Failed to setup GL functions");
        SDL_free(data);
        SDL_free(device);
        return NULL;
    }

    SDL_LogInfo(SDL_LOG_CATEGORY_VIDEO,
                "✅ Renderer initialized: %s",
                Android_GetCurrentRenderer());
}
```

---

## 📊 测试验证

### 测试步骤

1. **编译项目**：
```bash
./gradlew assembleDebug
```

2. **安装到设备**：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

3. **测试不同渲染器**：

**测试 gl4es**：
```bash
adb shell am start -n com.app.ralaunch/.MainActivity
adb logcat | grep -E "Renderer|POJAV_RENDERER|GL4ES"
```

**预期日志**：
```
I/GameActivity: ENV: POJAV_RENDERER=opengles2
I/GameActivity: ENV: LIBGL_ES=2
I/SDL_Renderer: POJAV_RENDERER=opengles2, using SDL renderer: gl4es
I/SDL_Renderer: ✅ Renderer initialized: gl4es
```

**测试 ANGLE**：
```
I/GameActivity: ENV: POJAVEXEC_EGL=libEGL_angle.so
I/SDL_Renderer: ✅ Renderer initialized: angle
```

**测试 Zink**：
```
I/GameActivity: ENV: POJAV_RENDERER=vulkan_zink
I/GameActivity: ENV: GALLIUM_DRIVER=zink
I/SDL_Renderer: ✅ Renderer initialized: zink
```

---

## ✅ 总结

### 关键区别

| 方面 | 我们之前的实现 | FoldCraftLauncher 方式 |
|-----|-------------|---------------------|
| 加载方式 | dlopen + LD_PRELOAD | 环境变量 + LD_LIBRARY_PATH |
| 配置方法 | JNI setenv | Java Os.setenv |
| 库路径 | 绝对路径 | 通过 LD_LIBRARY_PATH 自动查找 |
| 复杂度 | 较高 | 较低 |

### 优势

1. **无需手动 dlopen**：库通过 LD_LIBRARY_PATH 自动可见
2. **更简单的实现**：只需设置环境变量
3. **更好的兼容性**：符合 PojavLauncher 生态
4. **易于维护**：环境变量集中管理

这才是生产级的正确实现方式！🎉
