# SDL2 路径（org.libsdl.app2 胶水层）的原生库构建

RAL 支持在「SDL2」与「SDL3」两条运行路径之间切换（设置 → 运行时的下方）。
两套 SDL 各自带一份 Java 胶水层，本工程用 **包名 +2** 把它们分开，从而可以共存于同一个 APK：

| 路径 | Java 胶水层 | 入口 Activity | 视频/音频后端 |
| --- | --- | --- | --- |
| SDL3 | `org.libsdl.app.SDLActivity` | `feature/game/ui/legacy/GameActivity` | SDL3 自带 Android 后端（外接 SDL 的 Video/Input） |
| SDL2 | `org.libsdl.app2.SDLActivity` | `feature/game/ui/sdl2/GameActivitySdl2` | 系统 EGL/GLES + AAudio（厂商 GPU 直通） |

SDL2 的胶水层源码位于 `app/src/main/java/org/libsdl/app2/`（来自 SDL2 官方 `android-project`，
改包名并提供 `shouldStartNativeMain()` / `isNativeMainRunning()` 扩展点，让 RAL 使用托管游戏入口，
同时向实际的游戏线程发送 SDL 暂停/恢复通知）。

## 依赖

* NDK：`$NDK`（默认 `/data/data/com.termux/files/usr/opt/android-ndk`）
* `patchelf`（仅用于给 FNA3D/FAudio 统一 SONAME）

## 脚本

| 脚本 | 产物 | 说明 |
| --- | --- | --- |
| `build-sdl2.sh` | `libSDL2.so` | 打补丁（JNI 前缀 `org_libsdl_app` → `org_libsdl_app2`、恢复 Java 绑定注册、Android 调用守卫）后用**官方 `build-scripts/androidbuildlibs.sh`（ndk-build）**构建：产物天然无版本号、只带 Android 平台驱动 |
| `build-fna-sdl2.sh` | `libFNA3D_sdl2.so`、`libFAudio_sdl2.so` | 用工程内 `core/libs/{FNA3D,FAudio}` 源码 `-DBUILD_SDL3=OFF` 编出 SDL2 版，并写入 `app/src/main/jniLibs/arm64-v8a/` |

## 用法

```bash
# 1) SDL2 本体（官方 ndk-build）
SRC=$HOME/sdl2src WORK=$HOME/sdl2android bash build-sdl2.sh \
  && cp $HOME/sdl2android/ndklibs/arm64-v8a/libSDL2.so \
        ../../app/src/main/jniLibs/arm64-v8a/libSDL2.so
# 2) FNA3D / FAudio 的 SDL2 版（直接写入 jniLibs）
bash build-fna-sdl2.sh
```

## 关键约定

1. **JNI 前缀必须与胶水层包名一致**：SDL2 native 侧用 `SDL_JAVA_PREFIX = org_libsdl_app2`，
   注册的类名是 `org/libsdl/app2/SDLActivity…`；改包名时这两处必须同步，否则 SDL2 的
   Java 桥接层完全失效（表现为 `nativeSetScreenResolution` 等找不到实现）。
2. **FNA3D / FAudio 的 SDL2 与 SDL3 版同名冲突**：两套都以 `_sdl2` / `_sdl3` 命名，
   SONAME 统一为 `libFNA3D.so` / `libFAudio.so`，包内**不存在**名为 `libFNA3D.so` 的文件。
   游戏侧 `dlopen("libFNA3D.so")` 按文件名找不到文件时会回退到「已加载库的 SONAME 匹配」，
   于是 `GameLauncher` 预载哪一套就生效哪一套。
3. **不能把 FNA3D/FAudio 链进 `libmain.so`**：SDL3 的 Java 层会自己 `loadLibrary("main")`，
   而那两个文件名在包内刻意不存在，链接进去会直接 dlopen 失败。
4. **SDL2 音频必须显式 `SDL_AUDIODRIVER=aaudio`**：OpenSL ES 驱动的
   `OnlyHasDefaultOutputDevice = TRUE`，SDL 会拒绝第二次打开默认设备
   （"Audio device already open"），而 tModLoader 要开两个（FNA + LegacyAudioSystem）。
5. **托管游戏线程也必须参与 SDL 生命周期**：`GameActivitySdl2` 不创建 `mSDLThread`，
   由 `isNativeMainRunning()` 报告 `ral-game` 是否存活。暂停和恢复仍经过 SDL 原有状态机，
   使游戏事件循环在 Surface 销毁前备份并解绑 EGL 上下文、重建后恢复。不要把托管线程塞进
   `mSDLThread`，否则退出时 SDL 的 `join()` 会在 UI 线程等待托管运行时。
6. **FPS 数据来自 SDL2 成功的 EGL 交换帧**：构建脚本的补丁9复用 SDL3 的滑动窗口采样，
   写入既有 `RAL_FPS` / `RAL_FRAME_TIME`，后台停留超过一秒时丢弃旧样本。修改该补丁后
   必须重编译并更新 `app/src/main/jniLibs/arm64-v8a/libSDL2.so`，单独重编译 Kotlin 不会生效。

## 回归检查

```bash
# JDK（JAVA_HOME 或 PATH）+ Python：执行实际 SDLActivity 状态机，覆盖托管/默认线程、
# 首次启动、重复暂停和 Surface 重建后恢复。
python scripts/sdl2/tests/check_lifecycle.py

# NDK + ADB 已连接 arm64 设备：编译实际 FPS 采样代码并在设备上运行，
# 用可控时间验证 60 FPS、30 FPS 和后台停留 10 秒后的采样恢复。
python scripts/sdl2/tests/check_fps.py --ndk /path/to/android-ndk
```

游戏实测：启用 FPS 显示，启动 SDL2 游戏；按 Home 切后台后返回，重复三次。
确认游戏恢复渲染、FPS 显示数值，日志包含 `nativePause()` / `nativeResume()`，
没有连续 `EGL_BAD_SURFACE`。设备型号、Android 版本及实测结果应随问题记录。
本次无需迁移设置或存档。

2026-11-08 实测：23113RKC6C（Android 16），tModLoader v2026.8.3.0 / SDL2 启动菜单。
安装 `:app:assembleDebug` 生成的 APK 后，连续三次按 Home、等待 Surface 销毁再返回：
游戏 PID 始终为 13090，日志各有三次 `nativePause()` / `nativeResume()`，
`EGL_BAD_SURFACE` / `FATAL EXCEPTION` / `Fatal signal` 为零；FPS 恢复到约 60（16.6 ms）。
本轮未进入世界，世界内及长时间后台的行为仍需后续实测。
