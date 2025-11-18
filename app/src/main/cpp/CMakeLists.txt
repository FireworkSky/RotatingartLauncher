cmake_minimum_required(VERSION 3.22.1)
project("main")

# 设置路径
set(SDL_SOURCE_DIR ${CMAKE_SOURCE_DIR}/SDL)
set(FNA3D_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FNA3D)
set(FAUDIO_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FAudio)
set(THEORAFILE_SOURCE_DIR ${CMAKE_SOURCE_DIR}/Theorafile)
#set(LGOG_SOURCE_DIR ${CMAKE_SOURCE_DIR}/lgogdownloader)
# 移除 rustcorehost，现在直接使用 CoreCLR API


# 配置SDL：支持原生GLES和MobileGL两种渲染器
# 默认启用原生GLES（Android标准），MobileGL作为可选项通过运行时切换
set(SDL_VIDEO_OPENGL ON CACHE BOOL "Enable OpenGL support" FORCE)
set(SDL_VIDEO_OPENGL_ES ON CACHE BOOL "Enable OpenGL ES (native renderer)" FORCE)
set(SDL_VIDEO_OPENGL_EGL ON CACHE BOOL "Enable EGL (for native GLES)" FORCE)
set(SDL_OPENGLES ON CACHE BOOL "Enable OpenGL ES" FORCE)
# 添加全局编译定义，让SDL知道我们支持MobileGL和native GLES
add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)

find_library(CXX_SHARED_LIB c++_shared REQUIRED)
message(STATUS "Using libc++ shared: ${CXX_SHARED_LIB}")
set(CMAKE_ANDROID_STL_TYPE c++_shared)

# 添加SDL子项目
add_subdirectory(${SDL_SOURCE_DIR})

# SDL2 不再静态链接 GL，而是运行时动态加载 EGL
# target_link_libraries(SDL2 PRIVATE GL)

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



# 添加 NetCoreHost Manager 库（JNI包装）
add_library(netcorehost_manager SHARED
        netcorehost_manager.cpp
        netcorehost_manager_jni.cpp
        app_logger.c
)

# NetCoreHost Manager 链接 netcorehost
target_link_libraries(netcorehost_manager
        android
        log
        netcorehost
)

# NetCoreHost Manager 包含头文件
target_include_directories(netcorehost_manager PRIVATE
        ${NETCOREHOST_SOURCE_DIR}/include
)

# 添加你的主程序源码
add_library(${CMAKE_PROJECT_NAME} SHARED
        sdl_entry.c
        netcorehost_launcher.cpp
        corehost_trace_redirect.cpp
        And64InlineHook/And64InlineHook.cpp
        framework_utils.cpp
        jni_bridge.c
        error_handler.c
        console_bridge.c
        app_logger.c
        app_logger_jni.c

)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        # GL 现在是共享库，运行时动态加载，不再静态链接
        SDL2        # SDL2使用EGL后端
        dl          # 用于dlopen/dlsym（动态加载EGL库）
        FAudio-shared  # FAudio音频库（FNA需要）
        FNA3D       # FNA3D图形库
        theorafile  # Theorafile视频库（FNA需要）
        mojoshader  # MojoShader着色器库（FNA3D需要）
        netcorehost # .NET Core Hosting库
        c++_shared
        ${CXX_SHARED_LIB}
       # lgogdownloader
)

# 不再需要导出 gl4es 符号，因为 GL 是独立的共享库，通过 dlopen 加载
# target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
#         -Wl,--export-dynamic
# )

# 包含 SDL、gl4es、FNA3D 和 netcorehost 头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include

        ${FNA3D_SOURCE_DIR}/include
        ${NETCOREHOST_SOURCE_DIR}/include
        ${LGOG_SOURCE_DIR}/include
)

# 安装共享库到APK的lib目录
# 包括：libmain.so, SDL2, FAudio, FNA3D, netcorehost, netcorehost_manager, mojoshader, theorafile
# GL (libGL.so) 和 shaderconv 通过 GL4ESPlus 的 CMakeLists.txt 单独安装
install(TARGETS ${CMAKE_PROJECT_NAME} SDL2 FAudio-shared FNA3D netcorehost netcorehost_manager DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(TARGETS mojoshader theorafile DESTINATION ${CMAKE_INSTALL_LIBDIR})