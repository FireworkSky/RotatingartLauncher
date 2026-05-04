cmake_minimum_required(VERSION 3.22.1)
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
add_subdirectory(${LIBS_DIR}/FMOD_SDL_BUILD_DEPS)
add_subdirectory(${LIBS_DIR}/dobby)
add_subdirectory(${LIBS_DIR}/spdlog-1.17.0)

# =========================
# 主程序（保持独立）
# =========================

add_library(${CMAKE_PROJECT_NAME} SHARED
        src/logger.cpp
        src/jni_entry.cpp
        src/osm_renderer.cpp
        src/dotnet/corehost_hooks.cpp
        src/dotnet/dotnet_launcher.cpp
        src/sdl/sdl_entry.cpp
        src/sdl/sdl_input_bridge_extend.cpp
        src/native_stdio.cpp
        src/game_launcher.cpp
        src/shared_envvars.cpp
        src/thread_affinity_manager.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        SDL2
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
install(TARGETS SDL2 FAudio-shared FNA3D dotnethost fmod_SDL
        DESTINATION lib)

install(TARGETS GL_gl4es EGL_gl4es
        DESTINATION lib)