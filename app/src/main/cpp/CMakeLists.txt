cmake_minimum_required(VERSION 3.22.1)
project("main")

# 设置路径
set(SDL_SOURCE_DIR ${CMAKE_SOURCE_DIR}/SDL)
set(FNA3D_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FNA3D)
set(FAUDIO_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FAudio)
set(THEORAFILE_SOURCE_DIR ${CMAKE_SOURCE_DIR}/Theorafile)
#set(LGOG_SOURCE_DIR ${CMAKE_SOURCE_DIR}/lgogdownloader)
# 移除 rustcorehost，现在直接使用 CoreCLR API


# ===== 双渲染器系统 =====
# gl4es: 预编译的 libgl4es.so 放在 jniLibs 目录，运行时动态加载
# angle: 预编译的 libEGL_angle.so/libGLESv2_angle.so 放在 jniLibs 目录
# native: 使用系统原生 EGL/GLES


# 添加 gl4es 子项目
set(ANDROID ON CACHE BOOL "Targeting an Android device" FORCE)
set(USE_ANDROID_LOG ON CACHE BOOL "Use Android log instead of stdio" FORCE)
set(EGL_WRAPPER ON CACHE BOOL "Build EGL wrapper" FORCE)
set(NOX11 ON CACHE BOOL "Not using X11" FORCE)
set(STATICLIB OFF CACHE BOOL "Build shared library" FORCE)
set(GL4ES_SOURCE_DIR ${CMAKE_SOURCE_DIR}/gl4es)

# 找到 Android log 库供 gl4es 使用
find_library(log-lib log)

add_subdirectory(${GL4ES_SOURCE_DIR})


# 配置SDL：同时支持原生GLES和gl4es两种渲染器
set(SDL_VIDEO_OPENGL ON CACHE BOOL "Enable OpenGL support (for gl4es)" FORCE)
set(SDL_VIDEO_OPENGL_ES ON CACHE BOOL "Enable OpenGL ES (native renderer)" FORCE)
set(SDL_VIDEO_OPENGL_EGL ON CACHE BOOL "Enable EGL" FORCE)
set(SDL_OPENGLES ON CACHE BOOL "Enable OpenGL ES" FORCE)

# 添加gl4es编译定义，让SDL知道我们支持gl4es渲染器
add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)

find_library(CXX_SHARED_LIB c++_shared REQUIRED)
message(STATUS "Using libc++ shared: ${CXX_SHARED_LIB}")
set(CMAKE_ANDROID_STL_TYPE c++_shared)

# 添加SDL子项目
add_subdirectory(${SDL_SOURCE_DIR})

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
        netcorehost_manager.cpp
        netcorehost_manager_jni.cpp
        corehost_trace_redirect.cpp
        And64InlineHook/And64InlineHook.cpp
        framework_utils.cpp
        jni_bridge.c
        error_handler.c
        console_bridge.c
        app_logger.c
        app_logger_jni.c
        renderer_loader.c

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

# 包含 SDL、FNA3D 和 netcorehost 头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include

        ${FNA3D_SOURCE_DIR}/include
        ${NETCOREHOST_SOURCE_DIR}/include
        ${LGOG_SOURCE_DIR}/include
)

# 安装共享库到APK的lib目录
# 包括：libmain.so, SDL2, FAudio, FNA3D, netcorehost, mojoshader, theorafile
# gl4es: GL_gl4es 和 EGL_gl4es 也会被打包到 APK
install(TARGETS ${CMAKE_PROJECT_NAME} SDL2 FAudio-shared FNA3D netcorehost DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(TARGETS mojoshader theorafile DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(TARGETS GL_gl4es EGL_gl4es DESTINATION ${CMAKE_INSTALL_LIBDIR})
