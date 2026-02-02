# SDL2 RAL 补丁集

基于 SDL 2.30.1，用于 RotatingartLauncher 的定制修改。

## 补丁列表

| 补丁文件 | 功能 | SDL3 迁移难度 |
|---------|------|--------------|
| `01_platform_spoof.patch` | 平台伪装 (Android→Linux) | ✅ 简单 |
| `02_assert_fix.patch` | 断言时禁止最小化窗口 | ✅ 简单 |
| `03_aaudio_lowlatency.patch` | AAudio 低延迟可选 | ⚠️ API 变化 |
| `04_android_jni.patch` | JNI 扩展 (核心) | ❌ 需重写 |
| `05_android_jni_h.patch` | JNI 头文件 | ❌ 需重写 |
| `06_mouse_range.patch` | 虚拟鼠标范围限制 | ⚠️ 事件系统变化 |
| `07_touch_multitouch.patch` | 多点触控转鼠标 | ⚠️ 事件系统变化 |
| `08_joystick_rumble.patch` | 手柄震动支持 | ⚠️ API 变化 |
| `09_egl_renderers.patch` | EGL 扩展 (gl4es/zink) | ⚠️ 渲染器变化 |
| `10_android_gl.patch` | Android GL 加载扩展 | ❌ SDL_GPU 替代 |
| `11_android_mouse.patch` | Android 鼠标直接控制 | ⚠️ 事件系统变化 |
| `12_android_video.patch` | 动态渲染器 + 显示模式 | ⚠️ 视频系统变化 |
| `13_android_window.patch` | 强制全屏窗口 | ✅ 简单 |
| `14_main_entry.patch` | 自定义入口点 | ✅ 简单 |
| `15_sensor_fix.patch` | ALooper API 修复 | ✅ 可能已修复 |

## 新增文件 (C/C++)

| 文件 | 功能 |
|-----|------|
| `16_new_android_renderer.c` | 动态渲染器加载实现 |
| `16_new_android_renderer.h` | 动态渲染器接口 |

## Java 补丁

| 补丁文件 | 功能 | SDL3 迁移难度 |
|---------|------|--------------|
| `17_java_SDLActivity.patch` | Activity 核心扩展 | ❌ 需重写 |
| `18_java_SDLControllerManager.patch` | 手柄管理+震动 | ⚠️ API 变化 |
| `19_java_SDLSurface.patch` | Surface 触摸/鼠标处理 | ⚠️ 事件变化 |
| `20_java_SDL.patch` | SDL 工具类 | ✅ 简单 |
| `21_java_SDLAudioManager.patch` | 音频管理器 | ⚠️ API 变化 |

## 新增文件 (Java)

| 文件 | 功能 |
|-----|------|
| `22_new_VirtualXboxController.java` | 虚拟 Xbox 手柄实现 |

## SDL3 迁移策略

### 阶段 1: 可直接迁移的补丁
- `01_platform_spoof.patch` - 直接修改返回值
- `02_assert_fix.patch` - 找到对应位置修改
- `13_android_window.patch` - 窗口 API 基本兼容
- `14_main_entry.patch` - 入口点机制类似

### 阶段 2: 需要适配的补丁
- `03_aaudio_lowlatency.patch` - SDL3 音频 API 变化
- `06_mouse_range.patch` - SDL3 事件系统变化
- `07_touch_multitouch.patch` - SDL3 触摸 API 变化
- `08_joystick_rumble.patch` - SDL3 手柄 API 变化
- `11_android_mouse.patch` - 适配新事件系统

### 阶段 3: 需要重新实现的功能
- `04_android_jni.patch` - SDL3 重新设计了 JNI 架构
- `09_egl_renderers.patch` - SDL3 使用 SDL_GPU 替代
- `10_android_gl.patch` - SDL3 使用 SDL_GPU

### 阶段 4: 可能不再需要
- `15_sensor_fix.patch` - SDL3 可能已修复
- 动态渲染器 - SDL3 的 SDL_GPU 原生支持 Vulkan

## 应用补丁

```bash
# 应用单个补丁
cd SDL
git apply ../SDL_patches/patches/01_platform_spoof.patch

# 应用所有补丁 (需要手动解决冲突)
for patch in ../SDL_patches/patches/*.patch; do
    git apply "$patch" || echo "Failed: $patch"
done
```

## 注意事项

1. **SDL3 的 SDL_GPU** - 原生支持 Vulkan、Metal、D3D12，可能不再需要 gl4es/zink 相关修改
2. **JNI 架构变化** - SDL3 重新设计了 Android JNI，需要重新实现所有 JNI 扩展
3. **事件系统** - SDL3 的事件处理有较大变化，触摸/鼠标相关补丁需要适配
