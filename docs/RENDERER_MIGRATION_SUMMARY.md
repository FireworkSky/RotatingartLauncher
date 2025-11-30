# 渲染器系统迁移总结

## 概述

完成了从旧的渲染器系统到新的 RALCORE 环境变量驱动系统的迁移。

## 主要变更

### 1. 核心架构重构

**旧系统（已移除）：**
- 使用固定的渲染器常量：`RENDERER_OPENGLES3`, `RENDERER_OPENGL_GL4ES`, `RENDERER_VULKAN`
- 手动 dlopen + LD_PRELOAD 机制
- 复杂的 JNI 接口

**新系统：**
- 使用 `RendererConfig` 统一管理渲染器
- 基于环境变量（`RALCORE_RENDERER`, `RALCORE_EGL`）
- 简化的纯 Java 实现（通过 `Os.setenv()`）
- SDL 原生层自动读取环境变量

### 2. 支持的渲染器

| 渲染器 ID | 名称 | 描述 | 最低要求 |
|----------|------|------|---------|
| `native` | Native OpenGL ES | 系统原生 EGL/OpenGL ES | 无 |
| `gl4es` | Holy GL4ES | OpenGL 2.1 → GLES 2.0 翻译层 | 无 |
| `angle` | ANGLE (Vulkan) | OpenGL ES over Vulkan (Google) | Android 7.0+ |
| `zink` | Zink (Mesa) | OpenGL 4.6 over Vulkan | Android 7.0+ |
| `virgl` | VirGL Renderer | Gallium3D VirGL (OpenGL 4.3) | Android 7.0+ |
| `freedreno` | Freedreno (Adreno) | Mesa Freedreno for Adreno GPU | Android 7.0+ |

### 3. 文件变更

#### 新增文件
- `RendererConfig.java` - 渲染器配置和环境变量管理
- `RendererLoader.java` - 简化的渲染器加载器
- `SDL_androidrenderer.c` - SDL 原生层环境变量读取

#### 修改文件
- `RuntimePreference.java`
  - 标记旧常量为 `@Deprecated`
  - 添加 `normalizeRendererValue()` 进行旧→新 ID 映射
  - 简化 `applyRendererEnvironment()` 使用新系统

- `SettingsFragment.java`
  - 动态生成渲染器选项（基于设备兼容性）
  - 移除硬编码的旧渲染器选项
  - 使用 `RendererConfig.getCompatibleRenderers()`

- `strings.xml`
  - 移除旧的渲染器字符串（`renderer_opengles3`, `renderer_opengl`, `renderer_vulkan`）
  - 保留新的渲染器字符串（`renderer_native`, `renderer_gl4es`, 等）

- `GameActivity.java`
  - 无需修改（通过 `RuntimePreference.applyRendererEnvironment()` 自动集成）

### 4. 向后兼容性

**旧设置迁移：**
- `opengles3` → `native`
- `opengl_gl4es` → `gl4es`
- `vulkan` → `native` (暂不支持)
- `opengl_native` → `native`

通过 `normalizeRendererValue()` 自动转换，用户无需手动更新设置。

### 5. 环境变量映射

| 渲染器 | Java 层设置 | Native 层读取 |
|--------|------------|-------------|
| **gl4es** | `RALCORE_RENDERER=opengles2` | 映射到 `"gl4es"` |
| **angle** | `RALCORE_EGL=libEGL_angle.so` | 检测到 `"angle"` |
| **zink** | `RALCORE_RENDERER=vulkan_zink` | 映射到 `"zink"` |
| **virgl** | `RALCORE_RENDERER=gallium_virgl` | 映射到 `"virgl"` |
| **freedreno** | `RALCORE_RENDERER=gallium_freedreno` | 映射到 `"freedreno"` |
| **native** | 无环境变量 | 默认 `"native"` |

### 6. 数据流

```
用户在设置中选择渲染器
    ↓
SettingsManager.setFnaRenderer(rendererId)  // 可以是新 ID 或旧 ID
    ↓
GameActivity.loadLibraries()
    ↓
RuntimePreference.applyRendererEnvironment(context)
    ↓
normalizeRendererValue(preferredRenderer)  // 旧 ID → 新 ID
    ↓
mapRendererToConfigId(normalized)  // 处理 "auto"
    ↓
RendererLoader.loadRenderer(context, rendererId)
    ↓
RendererConfig.getRendererEnv(rendererId)  // 获取环境变量映射
    ↓
Os.setenv("RALCORE_RENDERER", ...) / Os.setenv("RALCORE_EGL", ...)
    ↓
SDL 初始化
    ↓
SDL_androidrenderer.c: GetRendererFromEnv()
    ↓
读取 RALCORE_* 环境变量 → 映射到渲染器名称
    ↓
Android_LoadRenderer(renderer_name)
    ↓
dlopen() 加载对应的渲染器库
```

## 编译结果

```
BUILD SUCCESSFUL in 1s
68 actionable tasks: 13 executed, 55 up-to-date

警告（预期）：
- removing resource renderer_opengl
- removing resource renderer_opengles3
- removing resource renderer_vulkan
```

## 优势

1. **简化架构**：移除了复杂的 JNI 和 dlopen 代码
2. **动态检测**：自动检测设备兼容的渲染器
3. **易于扩展**：添加新渲染器只需在 `RendererConfig` 中配置
4. **环境变量驱动**：统一的配置方式，与 FoldCraftLauncher 兼容
5. **向后兼容**：自动迁移旧设置，用户无感知

## 测试建议

1. **基本功能测试**：
   - 打开设置 → 渲染器选项
   - 验证只显示兼容的渲染器
   - 切换不同渲染器
   - 验证设置保存

2. **渲染测试**：
   - 测试每个渲染器能否正常启动游戏
   - 验证环境变量是否正确设置（查看 logcat）
   - 测试渲染性能和兼容性

3. **迁移测试**：
   - 从旧版本升级
   - 验证旧的渲染器设置自动转换
   - 确认无崩溃和数据丢失

## 日志示例

```
RuntimePreference: ========================================
RuntimePreference: 渲染器环境变量已应用 (RALCORE Backend)
RuntimePreference:   渲染器偏好: opengles3
RuntimePreference:   渲染器 ID: native
RuntimePreference:   当前渲染器: native
RuntimePreference: ========================================

SDL_Renderer: ================================================================
SDL_Renderer:   SDL Dynamic Renderer Loading
SDL_Renderer:   Requested: (null)
SDL_Renderer:   Environment: RALCORE_RENDERER/RALCORE_EGL -> native
SDL_Renderer: ================================================================
SDL_Renderer:   Selected: native
SDL_Renderer:   Using system libEGL.so and libGLESv2.so
SDL_Renderer: ✅ Renderer 'native' loaded successfully
```

## 维护指南

### 添加新渲染器

1. 在 `RendererConfig.ALL_RENDERERS` 添加配置
2. 在 `RendererConfig.getRendererEnv()` 添加环境变量映射
3. 在 `SDL_androidrenderer.c: GetRendererFromEnv()` 添加环境变量→名称映射
4. 在 `strings.xml` 添加字符串资源
5. 将渲染器库添加到 `jniLibs/`

### 移除渲染器

1. 从 `RendererConfig.ALL_RENDERERS` 移除
2. 从 `getRendererEnv()` 移除对应 case
3. 从 `GetRendererFromEnv()` 移除映射
4. 从 `strings.xml` 移除字符串
5. 从 `jniLibs/` 删除库文件

## 相关文件

- `app/src/main/java/com/app/ralaunch/renderer/RendererConfig.java`
- `app/src/main/java/com/app/ralaunch/renderer/RendererLoader.java`
- `app/src/main/java/com/app/ralaunch/utils/RuntimePreference.java`
- `app/src/main/java/com/app/ralaunch/fragment/SettingsFragment.java`
- `app/src/main/cpp/SDL/src/video/android/SDL_androidrenderer.c`
- `app/src/main/res/values/strings.xml`
- `app/src/main/jniLibs/arm64-v8a/` (渲染器库)
