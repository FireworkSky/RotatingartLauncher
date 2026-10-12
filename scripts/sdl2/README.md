# SDL2 路径（org.libsdl.app2 胶水层）的原生库构建

RAL 支持在「SDL2」与「SDL3」两条运行路径之间切换（设置 → 运行时的下方）。
两套 SDL 各自带一份 Java 胶水层，本工程用 **包名 +2** 把它们分开，从而可以共存于同一个 APK：

| 路径 | Java 胶水层 | 入口 Activity | 视频/音频后端 |
| --- | --- | --- | --- |
| SDL3 | `org.libsdl.app.SDLActivity` | `feature/game/ui/legacy/GameActivity` | SDL3 自带 Android 后端（外接 SDL 的 Video/Input） |
| SDL2 | `org.libsdl.app2.SDLActivity` | `feature/game/ui/sdl2/GameActivitySdl2` | 系统 EGL/GLES + AAudio（厂商 GPU 直通） |

SDL2 的胶水层源码位于 `app/src/main/java/org/libsdl/app2/`（来自 SDL2 官方 `android-project`，
仅改包名 + 一处 `shouldStartNativeMain()` 扩展点，用于让 RAL 用托管游戏入口替代 SDL 的 native main）。

## 依赖

* NDK：`$NDK`（默认 `/data/data/com.termux/files/usr/opt/android-ndk`）
* `patchelf`（仅用于给 FNA3D/FAudio 统一 SONAME）

## 脚本

| 脚本 | 产物 | 说明 |
| --- | --- | --- |
| `build-sdl2.sh` | `libSDL2.so` | 打补丁（JNI 前缀 `org_libsdl_app` → `org_libsdl_app2`、恢复 Java 绑定注册、Android 调用守卫）后用**官方 `build-scripts/androidbuildlibs.sh`（ndk-build）**构建：产物天然无版本号、只带 Android 平台驱动 |
| `build-fna-sdl2.sh` | `libFNA3D_sdl2.so`、`libFAudio_sdl2.so` | 用工程内 `core/libs/{FNA3D,FAudio}` 源码 `-DBUILD_SDL3=OFF` 编出 SDL2 版，并写入 `app/src/main/jniLibs/arm64-v8a/` |
| `build-mesa-wayland.sh` | `libEGL_ral.so` 等 | **已废弃**：早期「内嵌 Wayland 合成器 + Mesa 软件光栅」方案遗留，SDL2 现走厂商 GPU，不再需要 |

## 用法

```bash
# 1) SDL2 本体（官方 ndk-build）
SRC=$HOME/sdl2src WORK=$HOME/sdl2wayland bash build-sdl2.sh \
  && cp $HOME/sdl2wayland/ndklibs/arm64-v8a/libSDL2.so \
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
