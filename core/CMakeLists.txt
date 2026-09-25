cmake_minimum_required(VERSION 3.22.1)

# Android/bionic 不会把 DT_NEEDED 里的 libX.so.0 回退匹配到 APK 内的 libX.so。
# 开启该平台变量后 CMake 不再生成版本化 SONAME（文件与 SONAME 均为 libX.so），
# 链接方记录的依赖名即为 libX.so，可在 APK lib 目录按文件名解析。
# 必须在 project() 之前设置。
set(CMAKE_PLATFORM_NO_VERSIONED_SONAME ON)

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
# SDL2 兼容层（sdl2-compat）
# =========================
# 用 SDL3 实现 SDL2 ABI，产出 libSDL2.so，供仍按 SDL2 链接的程序/库使用。
# 编译期复用上面的 SDL3（SDL3::Headers 已由 core/libs/SDL 定义），
# 运行期 sdl2-compat 会自行 dlopen("libSDL3.so")，与 APK 内的 libSDL3.so 对应。
set(SDL2COMPAT_TESTS   OFF CACHE BOOL "" FORCE)
set(SDL2COMPAT_INSTALL OFF CACHE BOOL "" FORCE)
set(SDL2COMPAT_WERROR  OFF CACHE BOOL "" FORCE)
set(SDL2COMPAT_SHARED  ON  CACHE BOOL "" FORCE)
# Android 上 SDL2COMPAT_STATIC=ON 会直接 FATAL_ERROR
set(SDL2COMPAT_STATIC  OFF CACHE BOOL "" FORCE)
set(SDL2COMPAT_ASAN    OFF CACHE BOOL "" FORCE)
# Android 上必须关闭 X11，否则 sdl2-compat 会 find_package(X11 REQUIRED) 直接失败
set(SDL2COMPAT_X11     OFF CACHE BOOL "" FORCE)
add_subdirectory(${LIBS_DIR}/sdl2-compat)

# 这两个静态库本项目不用（AGP 会把所有目标都编一遍，排除掉省时省事）
set_target_properties(SDL2main SDL2_test PROPERTIES EXCLUDE_FROM_ALL TRUE)

# SDL2 使用 Linux 惯例的 SONAME，以兼容游戏自带库的依赖名。
# 例如 Terraria 的 libnfd.so 记录的是 DT_NEEDED=libSDL2-2.0.so.0，
# 而文件名保持 libSDL2.so，故 DT_NEEDED=libSDL2.so 的库也能按文件名命中同一份库。
# NO_SONAME 让 CMake 不再自行追加 -soname（否则它会覆盖下面指定的名字），
# 再通过 LINK_FLAGS 显式指定 Linux 惯例的 SONAME。
set_target_properties(SDL2 PROPERTIES NO_SONAME TRUE)
set_target_properties(SDL2 PROPERTIES LINK_FLAGS "-Wl,-soname,libSDL2-2.0.so.0")


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

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        SDL3::SDL3
        FAudio-shared
        FNA3D
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
# fmod_SDL 是 SDL2 时代的桥接库（include <SDL.h>），SDL3 分支不再使用，已移除
install(TARGETS SDL3-shared FAudio-shared FNA3D dotnethost SDL2
        DESTINATION lib)

install(TARGETS GL_gl4es EGL_gl4es
        DESTINATION lib)
