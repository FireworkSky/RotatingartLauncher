cmake_minimum_required(VERSION 3.22.1)

# Android/bionic 不会把 DT_NEEDED 里的 libX.so.0 回退匹配到 APK 内的 libX.so。
# 开启该平台变量后 CMake 不再生成版本化 SONAME（文件与 SONAME 均为 libX.so），
# 链接方记录的依赖名即为 libX.so，可在 APK lib 目录按文件名解析。
# 必须在 project() 之前设置。
set(CMAKE_PLATFORM_NO_VERSIONED_SONAME ON)

# RAL: 不生成 RUNPATH/RPATH。APK 内库由 bionic 按文件名在应用 nativeLibraryDir 解析，
# 构建机的绝对路径在设备上无效，残留会导致 libmain.so 加载失败。
set(CMAKE_SKIP_RPATH TRUE)
set(CMAKE_SKIP_BUILD_RPATH TRUE)
set(CMAKE_SKIP_INSTALL_RPATH TRUE)
set(CMAKE_BUILD_WITH_INSTALL_RPATH TRUE)

project("main")

# =========================
# 基础配置
# =========================
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# =========================
# libs 根目录
# =========================
set(LIBS_DIR ${CMAKE_SOURCE_DIR}/libs)

include(cmake/Gl4esOptions.cmake)
include(cmake/SdlOptions.cmake)
include(cmake/FaudioOptions.cmake)
include(cmake/Fna3dOptions.cmake)

add_subdirectory(${LIBS_DIR}/dotnethost)
# FMOD_SDL 是 SDL2 时代的桥接库（源码 include <SDL.h>），SDL3 分支已不需要，
# 且 AGP 会无视 EXCLUDE_FROM_ALL 强制构建它，故整体不注册该子目录。
# add_subdirectory(${LIBS_DIR}/FMOD_SDL_BUILD_DEPS)
add_subdirectory(${LIBS_DIR}/dobby)
add_subdirectory(${LIBS_DIR}/spdlog-1.17.0)

# =========================
# SDL2（真实 SDL2 + Wayland 后端，替代 sdl2-compat）
# =========================
# 已移除 sdl2-compat：它以 SDL3 模拟 SDL2 ABI，对按真实 SDL2 语义编写的游戏兼容性不足。
# 改为内建真实 SDL2（2.32.10，Wayland 视频后端）：
#   - Wayland 后端不需要 Android Java 绑定层（org.libsdl.app.*），
#     故可与 SDL3 的 Java 绑定共存于同一 APK，不会发生类冲突。
#   - 画面由应用内 Wayland 合成器呈现到 Android Surface。
# SDL2-Wayland 构建落地后再在此接入（add_subdirectory 或 import 预编译库）。
#
# 仍需沿用「文件名 libSDL2.so + SONAME libSDL2-2.0.so.0」策略：
# 例如 Terraria 的 libnfd.so 记录 DT_NEEDED=libSDL2-2.0.so.0，
# 预加载 libSDL2.so 后其 SONAME 与之一致，即可按已加载库命中。

# =========================
# 主程序（保持独立）
# =========================

add_library(${CMAKE_PROJECT_NAME} SHARED
        src/logger.cpp
        src/jni_entry.cpp
        src/osm_renderer.cpp
        src/dotnet/corehost_hooks.cpp
        src/dotnet/dotnet_launcher.cpp
        src/mono/mono_launcher.cpp
        src/sdl/sdl_entry.cpp
        src/sdl/sdl_input_bridge_extend.cpp
        src/native_stdio.cpp
        src/game_launcher.cpp
        src/shared_envvars.cpp
        src/thread_affinity_manager.cpp
)

# RAL: 曾在此编译内嵌 Wayland 合成器（core/src/wlcomp + vendored libwayland-server/libffi）。
# 该方案已废弃并删除；SDL2 路径改用 SDL 官方 Android 视频后端 + org.libsdl.app2 胶水层，
# 详见 scripts/sdl2/README.md。

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        SDL3::SDL3
        # RAL: 不要链接 FAudio/FNA3D —— libmain 不引用它们任何符号，但会因此写入
        # DT_NEEDED=libFAudio.so / libFNA3D.so；这两个文件名在包内刻意不存在
        # （见文件末尾的 SDL2/SDL3 双版本说明），而 SDL3 的 Java 层 SDLActivity 会
        # 在 GameLauncher 预载之前就 System.loadLibrary("main")，于是直接 dlopen 失败：
        #   library "libFAudio.so" not found: needed by libmain.so
        mojoshader
        dotnethost
        dobby
        spdlog::spdlog
        ${CXX_SHARED_LIB}
)

# 包含头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include
        ${FNA3D_SOURCE_DIR}/include
        ${DOTNETHOST_SOURCE_DIR}/include
        ${CMAKE_SOURCE_DIR}/liblinkernsbypass
        ${CMAKE_CURRENT_SOURCE_DIR}
        includes
)

# =========================
# 安装
# =========================
# RAL: FNA3D / FAudio 需要「SDL2 版」和「SDL3 版」两套（运行时按设置切换）。
# 游戏侧 P/Invoke 是 dlopen("libFNA3D.so")（无路径），bionic 按「文件路径」命中，
# 所以包内不能存在名为 libFNA3D.so / libFAudio.so 的文件，否则它永远优先。
# 做法：两套都以 _sdl2 / _sdl3 命名，SONAME 统一为 libFNA3D.so / libFAudio.so；
# bionic 在按文件名找不到文件时会回退到「已加载库的 SONAME 匹配」，
# 于是 GameLauncher 预载哪一套，游戏侧拿到的就是哪一套。
# （AGP 按 CMake codemodel 收集各 target 产物，因此必须改 OUTPUT_NAME；
#   _sdl2 版由 scripts/sdl2/fna_sdl2.sh 用同一份源码编译后放进 jniLibs。）
# NO_SONAME 关掉 CMake 自带的 -soname（它来自 OUTPUT_NAME，会排在自定义 flag 之后覆盖掉），
# 再由 target_link_options 显式指定统一 SONAME。
set_target_properties(FNA3D PROPERTIES OUTPUT_NAME "FNA3D_sdl3" NO_SONAME TRUE)
target_link_options(FNA3D PRIVATE "-Wl,-soname,libFNA3D.so")
set_target_properties(FAudio-shared PROPERTIES OUTPUT_NAME "FAudio_sdl3" NO_SONAME TRUE)
target_link_options(FAudio-shared PRIVATE "-Wl,-soname,libFAudio.so")

# fmod_SDL 是 SDL2 时代的桥接库（include <SDL.h>），SDL3 分支不再使用，已移除
install(TARGETS SDL3-shared FAudio-shared FNA3D dotnethost
        DESTINATION lib)

install(TARGETS GL_gl4es EGL_gl4es
        DESTINATION lib)
