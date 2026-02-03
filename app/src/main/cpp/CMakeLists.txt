cmake_minimum_required(VERSION 3.22.1)
project("main")

# ===== FNA3D .NET 游戏启动器 =====
# 此分支专注于运行 .NET FNA 游戏（如 Terraria、Celeste 等）

set(SDL_SOURCE_DIR ${CMAKE_SOURCE_DIR}/SDL)
set(FNA3D_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FNA3D)
set(FAUDIO_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FAudio)

# 添加 gl4es 子项目
set(ANDROID ON CACHE BOOL "Targeting an Android device" FORCE)
set(USE_ANDROID_LOG ON CACHE BOOL "Use Android log instead of stdio" FORCE)
set(EGL_WRAPPER ON CACHE BOOL "Build EGL wrapper" FORCE)
set(NOX11 ON CACHE BOOL "Not using X11" FORCE)
set(GLX_STUBS ON CACHE BOOL "Enable GLX function stubs for GLEW compatibility" FORCE)
set(STATICLIB OFF CACHE BOOL "Build shared library" FORCE)
set(GL4ES_SOURCE_DIR ${CMAKE_SOURCE_DIR}/gl4es)

find_library(log-lib log)
add_compile_options(-Wno-return-mismatch -Wno-int-conversion)
add_subdirectory(${GL4ES_SOURCE_DIR})
remove_definitions(-Wno-return-mismatch -Wno-int-conversion)

# 配置 SDL
set(SDL_VIDEO_OPENGL ON CACHE BOOL "Enable OpenGL support (for gl4es)" FORCE)
set(SDL_VIDEO_OPENGL_ES ON CACHE BOOL "Enable OpenGL ES (native renderer)" FORCE)
set(SDL_VIDEO_OPENGL_EGL ON CACHE BOOL "Enable EGL" FORCE)
set(SDL_OPENGLES ON CACHE BOOL "Enable OpenGL ES" FORCE)
add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)

find_library(CXX_SHARED_LIB c++_shared REQUIRED)
message(STATUS "Using libc++ shared: ${CXX_SHARED_LIB}")
set(CMAKE_ANDROID_STL_TYPE c++_shared)

add_subdirectory(${SDL_SOURCE_DIR})

# FAudio 音频库
set(BUILD_SDL3 OFF CACHE BOOL "Build against SDL 2.0" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared library" FORCE)
set(BUILD_UTILS OFF CACHE BOOL "Don't build utils" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "Don't build tests" FORCE)
set(XNASONG ON CACHE BOOL "Enable XNA_Song" FORCE)
set(SDL2_INCLUDE_DIRS ${SDL_SOURCE_DIR}/include)
set(SDL2_LIBRARIES SDL2)
add_subdirectory(${FAUDIO_SOURCE_DIR})

# netcorehost (.NET Core Hosting)
set(NETCOREHOST_SOURCE_DIR ${CMAKE_SOURCE_DIR}/netcorehost)
add_subdirectory(${NETCOREHOST_SOURCE_DIR})

# liblinkernsbypass（绕过 Android 命名空间限制）
add_subdirectory(${CMAKE_SOURCE_DIR}/liblinkernsbypass)

# FNA3D 图形库
set(BUILD_SDL3 OFF CACHE BOOL "Build against SDL 2.0" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared library" FORCE)
set(TRACING_SUPPORT OFF CACHE BOOL "Disable tracing" FORCE)
set(BUILD_DXVK_NATIVE OFF CACHE BOOL "Disable DXVK Native / D3D11 support" FORCE)
set(SDL2_INCLUDE_DIRS ${SDL_SOURCE_DIR}/include)
set(SDL2_LIBRARIES SDL2)
set(_OLD_CMAKE_INSTALL_PREFIX ${CMAKE_INSTALL_PREFIX})
set(CMAKE_SKIP_INSTALL_RULES ON)
add_subdirectory(${FNA3D_SOURCE_DIR})
set(CMAKE_SKIP_INSTALL_RULES OFF)
set(CMAKE_INSTALL_PREFIX ${_OLD_CMAKE_INSTALL_PREFIX})

set(CMAKE_CXX_STANDARD 20)

# FMOD_SDL 音频插件
add_subdirectory(FMOD_SDL_BUILD_DEPS)

# 主程序
add_subdirectory(main)

# 安装目标
install(TARGETS main SDL2 FAudio-shared FNA3D netcorehost fmod_SDL DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(TARGETS GL_gl4es EGL_gl4es DESTINATION ${CMAKE_INSTALL_LIBDIR})
