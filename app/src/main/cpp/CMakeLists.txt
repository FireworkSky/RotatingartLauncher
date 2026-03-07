cmake_minimum_required(VERSION 3.22.1)
project("main")

# ===== FNA3D .NET 游戏启动器 =====
set(SDL_SOURCE_DIR ${CMAKE_SOURCE_DIR}/SDL)
set(FNA3D_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FNA3D)
set(FAUDIO_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FAudio)

# gl4es
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

# SDL
set(SDL_VIDEO_OPENGL ON CACHE BOOL "Enable OpenGL support (for gl4es)" FORCE)
set(SDL_VIDEO_OPENGL_ES ON CACHE BOOL "Enable OpenGL ES (native renderer)" FORCE)
set(SDL_VIDEO_OPENGL_EGL ON CACHE BOOL "Enable EGL" FORCE)
set(SDL_OPENGLES ON CACHE BOOL "Enable OpenGL ES" FORCE)
add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)

find_library(CXX_SHARED_LIB c++_shared REQUIRED)
message(STATUS "Using libc++ shared: ${CXX_SHARED_LIB}")
set(CMAKE_ANDROID_STL_TYPE c++_shared)

add_subdirectory(${SDL_SOURCE_DIR})

# === THÊM ĐOẠN NÀY: Xác định đúng tên target SDL ===
if(TARGET SDL2)
    set(SDL_TARGET SDL2)
    message(STATUS "Found SDL target: SDL2")
elseif(TARGET SDL2-shared)
    set(SDL_TARGET SDL2-shared)
    message(STATUS "Found SDL target: SDL2-shared")
elseif(TARGET SDL3-shared)
    set(SDL_TARGET SDL3-shared)
    message(STATUS "Found SDL target: SDL3-shared")
elseif(TARGET SDL3)
    set(SDL_TARGET SDL3)
    message(STATUS "Found SDL target: SDL3")
else()
    message(FATAL_ERROR "Cannot find any SDL target! Check SDL subdirectory.")
endif()

# FAudio
set(BUILD_SDL3 OFF CACHE BOOL "Build against SDL 2.0" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared library" FORCE)
set(BUILD_UTILS OFF CACHE BOOL "Don't build utils" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "Don't build tests" FORCE)
set(XNASONG ON CACHE BOOL "Enable XNA_Song" FORCE)
set(SDL2_INCLUDE_DIRS ${SDL_SOURCE_DIR}/include)
set(SDL2_LIBRARIES ${SDL_TARGET})
add_subdirectory(${FAUDIO_SOURCE_DIR})

# dotnethost
set(DOTNETHOST_SOURCE_DIR ${CMAKE_SOURCE_DIR}/dotnethost)
add_subdirectory(${DOTNETHOST_SOURCE_DIR})

# liblinkernsbypass
add_subdirectory(${CMAKE_SOURCE_DIR}/liblinkernsbypass)

# FNA3D
set(BUILD_SDL3 OFF CACHE BOOL "Build against SDL 2.0" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared library" FORCE)
set(TRACING_SUPPORT OFF CACHE BOOL "Disable tracing" FORCE)
set(BUILD_DXVK_NATIVE OFF CACHE BOOL "Disable DXVK Native / D3D11 support" FORCE)
set(SDL2_INCLUDE_DIRS ${SDL_SOURCE_DIR}/include)
set(SDL2_LIBRARIES ${SDL_TARGET})
set(_OLD_CMAKE_INSTALL_PREFIX ${CMAKE_INSTALL_PREFIX})
set(CMAKE_SKIP_INSTALL_RULES ON)
add_subdirectory(${FNA3D_SOURCE_DIR})
set(CMAKE_SKIP_INSTALL_RULES OFF)
set(CMAKE_INSTALL_PREFIX ${_OLD_CMAKE_INSTALL_PREFIX})

set(CMAKE_CXX_STANDARD 20)

# FMOD_SDL
add_subdirectory(FMOD_SDL_BUILD_DEPS)

# Main
add_subdirectory(main)

# === SỬA DÒNG INSTALL: Dùng biến ${SDL_TARGET} thay vì SDL2 ===
install(TARGETS main ${SDL_TARGET} FAudio-shared FNA3D dotnethost fmod_SDL DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(TARGETS GL_gl4es EGL_gl4es DESTINATION ${CMAKE_INSTALL_LIBDIR}) 
