cmake_minimum_required(VERSION 3.22.1)
project("main")

# 设置路径
set(SDL_SOURCE_DIR ${CMAKE_SOURCE_DIR}/SDL)
set(GL4ES_SOURCE_DIR ${CMAKE_SOURCE_DIR}/gl4es)
set(FNA3D_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FNA3D)
set(FAUDIO_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FAudio)
set(THEORAFILE_SOURCE_DIR ${CMAKE_SOURCE_DIR}/Theorafile)
#set(LGOG_SOURCE_DIR ${CMAKE_SOURCE_DIR}/lgogdownloader)
# 移除 rustcorehost，现在直接使用 CoreCLR API

# ⚠️ 重要：gl4es必须在SDL之前添加，这样SDL才能找到正确的OpenGL库
# 添加gl4es子项目
add_subdirectory(${GL4ES_SOURCE_DIR})

# 配置SDL：支持原生GLES和gl4es两种渲染器
# 默认启用原生GLES（Android标准），gl4es作为可选项通过运行时切换
set(SDL_VIDEO_OPENGL ON CACHE BOOL "Enable OpenGL support" FORCE)
set(SDL_VIDEO_OPENGL_ES ON CACHE BOOL "Enable OpenGL ES (native renderer)" FORCE)
set(SDL_VIDEO_OPENGL_EGL ON CACHE BOOL "Enable EGL (for native GLES)" FORCE)
set(SDL_OPENGLES ON CACHE BOOL "Enable OpenGL ES" FORCE)
# 添加全局编译定义，让SDL知道我们支持gl4es和native GLES
add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)

# 添加SDL子项目
add_subdirectory(${SDL_SOURCE_DIR})

# SDL2 需要链接 gl4es (静态)，用于 OpenGL 符号
target_link_libraries(SDL2 PRIVATE GL)

# 添加 FAudio 子项目
# 配置 FAudio 使用 SDL2
set(BUILD_SDL3 OFF CACHE BOOL "Build against SDL 2.0" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared library" FORCE)
set(BUILD_UTILS OFF CACHE BOOL "Don't build utils" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "Don't build tests" FORCE)
set(XNASONG ON CACHE BOOL "Enable XNA_Song" FORCE)
set(FAUDIO_INSTALL OFF CACHE BOOL "Disable install" FORCE)
# 设置 SDL2 变量供 FAudio 使用
set(SDL2_INCLUDE_DIRS ${SDL_SOURCE_DIR}/include)
set(SDL2_LIBRARIES SDL2)
add_subdirectory(${FAUDIO_SOURCE_DIR})

# 添加 Theorafile 子项目
add_subdirectory(${THEORAFILE_SOURCE_DIR})

# 添加 netcorehost 子项目（.NET Core Hosting）
set(NETCOREHOST_SOURCE_DIR ${CMAKE_SOURCE_DIR}/netcorehost)
add_subdirectory(${NETCOREHOST_SOURCE_DIR})

# 添加 FNA3D 子项目
# 配置 FNA3D 使用 SDL2 而不是 SDL3
set(BUILD_SDL3 OFF CACHE BOOL "Build against SDL 2.0" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared library" FORCE)
set(TRACING_SUPPORT OFF CACHE BOOL "Disable tracing" FORCE)
# 设置 SDL2 变量供 FNA3D 使用
set(SDL2_INCLUDE_DIRS ${SDL_SOURCE_DIR}/include)
set(SDL2_LIBRARIES SDL2)
# 暂存当前的 CMAKE_INSTALL_PREFIX
set(_OLD_CMAKE_INSTALL_PREFIX ${CMAKE_INSTALL_PREFIX})
# 设置为不安装（跳过安装规则）
set(CMAKE_SKIP_INSTALL_RULES ON)
add_subdirectory(${FNA3D_SOURCE_DIR})
# 恢复设置
set(CMAKE_SKIP_INSTALL_RULES OFF)
set(CMAKE_INSTALL_PREFIX ${_OLD_CMAKE_INSTALL_PREFIX})

# 添加你的主程序源码
add_library(${CMAKE_PROJECT_NAME} SHARED
        sdl_entry.c
        netcorehost_launcher.cpp
        framework_utils.cpp
        jni_bridge.c
        error_handler.c
)

# 链接库
# ⚠️ SDL+OpenGL方案（Gish项目验证）
# 
# 架构：SDL (OpenGL) → gl4es静态库 → GLES后端
# 
# 链接顺序很重要：
# 1. GL (gl4es) 必须在SDL2之前
#    - SDL2需要OpenGL符号
#    - gl4es提供这些符号
# 2. gl4es内部链接到系统的EGL和GLESv2
#    - 用于实际的GLES调用
# 查找 nethost 静态库
set(NETHOST_LIB_DIR ${CMAKE_SOURCE_DIR}/../jnilibs/${ANDROID_ABI})
set(NETHOST_LIB ${NETHOST_LIB_DIR}/libnethost.a)
if(NOT EXISTS ${NETHOST_LIB})
    message(WARNING "nethost library not found at ${NETHOST_LIB}, skipping")
    set(NETHOST_LIB "")
endif()

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        GL          # gl4es静态库，提供OpenGL API
        SDL2        # SDL2使用OpenGL后端
        dl          # 用于dlopen/dlsym
        FNA3D       # FNA3D图形库
        netcorehost # .NET Core Hosting库
       # lgogdownloader
)

# 如果找到 nethost 静态库，则链接它
if(NETHOST_LIB)
    target_link_libraries(${CMAKE_PROJECT_NAME} ${NETHOST_LIB})
    message(STATUS "Linking nethost static library: ${NETHOST_LIB}")
endif()

# ⚠️ 关键：导出 gl4es 符号，使其对 dlsym 可见
# 这样SDL可以通过dlsym(NULL, "aglCreateContext2")找到这些函数
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        -Wl,--export-dynamic    # 导出所有符号到动态符号表
)

# 包含 SDL、gl4es、FNA3D 和 netcorehost 头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include
        ${GL4ES_SOURCE_DIR}/include
        ${FNA3D_SOURCE_DIR}/include
        ${NETCOREHOST_SOURCE_DIR}/include
        ${LGOG_SOURCE_DIR}/include
)

# 安装共享库到APK的lib目录
# 这确保所有动态链接的so文件都被打包到APK中
install(TARGETS ${CMAKE_PROJECT_NAME} SDL2 FAudio-shared FNA3D DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(TARGETS mojoshader theorafile DESTINATION ${CMAKE_INSTALL_LIBDIR})