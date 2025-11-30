# 渲染器库配置说明

## 📦 已添加的渲染器库

从 FoldCraftLauncher 复制的所有渲染器库已成功集成到项目中。

### 库文件位置
```
app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libgl4es.so              (2.6 MB)  - gl4es OpenGL 2.1 兼容层
│   ├── libEGL_angle.so          (304 KB)  - ANGLE EGL 库
│   ├── libGLESv2_angle.so       (5.4 MB)  - ANGLE GLES 库
│   ├── libOSMesa.so             (13 MB)   - Zink/OSMesa (OpenGL over Vulkan)
│   ├── libvulkan_freedreno.so   (10 MB)   - Vulkan Freedreno 驱动
│   └── libVkLayer_khronos_timeline_semaphore.so (210 KB)
│
└── armeabi-v7a/
    ├── libgl4es.so              (1.7 MB)  - gl4es OpenGL 2.1 兼容层
    ├── libEGL_angle.so          (138 KB)  - ANGLE EGL 库
    ├── libGLESv2_angle.so       (3.4 MB)  - ANGLE GLES 库
    └── libOSMesa.so             (12 MB)   - Zink/OSMesa (OpenGL over Vulkan)
```

---

## 🎮 支持的渲染器

| 渲染器 ID | 显示名称 | 库文件 | 说明 | 兼容性 |
|----------|---------|--------|------|--------|
| **native** | Native OpenGL ES | 系统默认 | Android 系统原生渲染器 | ✅ 所有设备 |
| **gl4es** | gl4es (OpenGL 2.1) | libgl4es.so | OpenGL 2.1 → GLES 2.0 翻译层 | ✅ 所有设备 |
| **angle** | ANGLE (Vulkan) | libEGL_angle.so<br>libGLESv2_angle.so | OpenGL ES over Vulkan | ✅ Android 7.0+ |
| **zink** | Zink (Mesa) | libOSMesa.so | OpenGL over Vulkan (Mesa) | ✅ Android 7.0+ |
| **vulkan** | Vulkan Native | libvulkan_freedreno.so | Vulkan 原生驱动 | ⚠️ Adreno GPU only |

---

## 📊 渲染器特性对比

### gl4es
- **优势**：
  - 最佳兼容性，支持所有 Android 设备
  - 提供 OpenGL 2.1 完整支持
  - 兼容老旧游戏和应用
- **劣势**：
  - 性能略低于原生 GLES
  - 翻译层存在一定开销

### ANGLE
- **优势**：
  - Google 官方维护
  - OpenGL ES over Vulkan，性能优秀
  - 良好的跨平台兼容性
- **劣势**：
  - 需要 Vulkan 支持 (Android 7.0+)
  - 库体积较大

### Zink/OSMesa
- **优势**：
  - Mesa 实现，功能完整
  - 支持 OpenGL 4.6 特性
  - 适合需要高版本 OpenGL 的应用
- **劣势**：
  - 库体积最大 (13 MB)
  - 需要 Vulkan 支持
  - 性能开销较大

### Vulkan Native
- **优势**：
  - 原生 Vulkan，性能最佳
  - 低延迟，高帧率
- **劣势**：
  - 仅支持 Adreno GPU (Qualcomm)
  - 需要游戏原生支持 Vulkan

---

## 🔧 如何使用

### 1. Java 层配置

在 `GameActivity.java` 中使用渲染器加载器：

```java
@Override
public void loadLibraries() {
    try {
        // 从设置中读取用户选择的渲染器
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String rendererId = prefs.getString("renderer", RendererConfig.RENDERER_NATIVE_GLES);

        AppLogger.info(TAG, "Loading renderer: " + rendererId);

        // 加载渲染器（必须在 SDL 加载之前！）
        boolean success = RendererLoader.loadRenderer(this, rendererId);
        if (!success) {
            AppLogger.warn(TAG, "Renderer loading failed, using native fallback");
            RendererLoader.loadRenderer(this, RendererConfig.RENDERER_NATIVE_GLES);
        }
    } catch (Exception e) {
        AppLogger.error(TAG, "Renderer error: " + e.getMessage());
    }

    // 然后加载 SDL 和其他库
    super.loadLibraries();
}
```

### 2. 环境变量配置 (Native 层)

SDL 会自动从环境变量读取渲染器配置：

```c
// SDL_androidvideo.c 会读取这些环境变量：
SDL_RENDERER=gl4es
// 或
FNA3D_OPENGL_DRIVER=angle
```

这些环境变量由 Java 层的 `RendererLoader.nativeSetEnv()` 设置。

### 3. 运行时切换

**方法 A：通过设置界面**
```java
// 在设置界面中添加渲染器选择器
List<RendererConfig.RendererInfo> renderers =
    RendererConfig.getCompatibleRenderers(context);

// 用户选择后保存到 SharedPreferences
prefs.edit().putString("renderer", selectedRendererId).apply();

// 提示用户重启游戏
```

**方法 B：通过代码直接设置**
```java
// 强制使用 gl4es
RendererLoader.loadRenderer(this, RendererConfig.RENDERER_GL4ES);
```

---

## 🧪 测试和验证

### 查看日志

```bash
adb logcat | grep -E "RendererLoader|SDL_Renderer|Android_Load"
```

### 预期日志输出

**gl4es 渲染器**：
```
I/RendererLoader: ================================================================
I/RendererLoader:   Loading Renderer: gl4es
I/RendererLoader: ================================================================
I/RendererLoader:   EGL Library: libgl4es.so
I/RendererLoader:   Loading with dlopen(RTLD_NOW | RTLD_GLOBAL)...
I/RendererLoader:   ✓ dlopen success, handle = 0x...
I/RendererLoader:   ✓ LD_PRELOAD = /data/app/.../libgl4es.so
I/RendererLoader:   ✓ gl4es environment configured
I/SDL_Renderer  : ✅ Renderer initialized: gl4es
```

**ANGLE 渲染器**：
```
I/RendererLoader:   Selected: angle
I/RendererLoader:   EGL Library: libEGL_angle.so
I/RendererLoader:   GLES Library: libGLESv2_angle.so
I/SDL_Renderer  : ✅ Renderer initialized: angle
```

---

## ⚠️ 注意事项

### 1. 加载顺序
**必须在 SDL 加载之前**调用 `RendererLoader.loadRenderer()`！

```java
// ❌ 错误示例
super.loadLibraries();
RendererLoader.loadRenderer(this, "gl4es");  // 太晚了！

// ✅ 正确示例
RendererLoader.loadRenderer(this, "gl4es");
super.loadLibraries();
```

### 2. 库体积优化

如果 APK 体积过大，可以选择性移除不需要的渲染器：

```bash
# 只保留 native + gl4es
rm app/src/main/jniLibs/*/libEGL_angle.so
rm app/src/main/jniLibs/*/libGLESv2_angle.so
rm app/src/main/jniLibs/*/libOSMesa.so
```

### 3. 兼容性检测

使用 `RendererConfig.getCompatibleRenderers()` 自动检测设备兼容性：

```java
List<RendererConfig.RendererInfo> compatible =
    RendererConfig.getCompatibleRenderers(context);

for (RendererConfig.RendererInfo renderer : compatible) {
    Log.i(TAG, "Available: " + renderer.displayName);
}
```

---

## 🐛 故障排除

### 问题 1: "dlopen failed: library not found"

**原因**：库文件未正确打包到 APK

**解决**：
```bash
# 清理并重新编译
./gradlew clean
./gradlew assembleDebug

# 验证 APK 中的库
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libgl4es
```

### 问题 2: 渲染器加载成功但黑屏

**原因**：渲染器与游戏不兼容

**解决**：
1. 检查 logcat 中的 OpenGL 错误
2. 尝试其他渲染器
3. 回退到 native 渲染器

### 问题 3: ANGLE 或 Zink 加载失败

**原因**：设备不支持 Vulkan

**解决**：
```java
// 检查 Vulkan 支持
PackageManager pm = context.getPackageManager();
boolean hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL);

if (!hasVulkan) {
    Log.w(TAG, "Vulkan not supported, use gl4es or native");
}
```

---

## 📚 参考资料

- [gl4es GitHub](https://github.com/ptitSeb/gl4es)
- [ANGLE Project](https://chromium.googlesource.com/angle/angle/)
- [Mesa3D Zink Driver](https://docs.mesa3d.org/drivers/zink.html)
- [Vulkan on Android](https://developer.android.com/ndk/guides/graphics/getting-started)

---

**渲染器库已完全集成！** 🎉

现在您可以在运行时动态切换渲染器，无需重新编译应用。
