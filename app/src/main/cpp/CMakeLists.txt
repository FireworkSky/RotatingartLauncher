cmake_minimum_required(VERSION 3.22.1)
project("main")

# 设置路径
set(SDL_SOURCE_DIR ${CMAKE_SOURCE_DIR}/SDL)
set(FNA3D_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FNA3D)
set(FAUDIO_SOURCE_DIR ${CMAKE_SOURCE_DIR}/FAudio)


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

# 设置 SDL2 变量供 FAudio 使用
set(SDL2_INCLUDE_DIRS ${SDL_SOURCE_DIR}/include)
set(SDL2_LIBRARIES SDL2)

add_subdirectory(${FAUDIO_SOURCE_DIR})

# 添加 netcorehost 子项目（.NET Core Hosting）
set(NETCOREHOST_SOURCE_DIR ${CMAKE_SOURCE_DIR}/netcorehost)
add_subdirectory(${NETCOREHOST_SOURCE_DIR})

# 添加 liblinkernsbypass 子项目（用于绕过 Android 命名空间限制）
add_subdirectory(${CMAKE_SOURCE_DIR}/liblinkernsbypass)
add_subdirectory(${CMAKE_SOURCE_DIR}/linkerhook)

# 添加 FNA3D 子项目
# 配置 FNA3D 使用 SDL2 而不是 SDL3
set(BUILD_SDL3 OFF CACHE BOOL "Build against SDL 2.0" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared library" FORCE)
set(TRACING_SUPPORT OFF CACHE BOOL "Disable tracing" FORCE)
# 启用 DXVK Native 支持 - 允许 FNA3D 使用 D3D11 -> DXVK -> Vulkan 渲染路径
set(BUILD_DXVK_NATIVE ON CACHE BOOL "Enable DXVK Native for D3D11 support" FORCE)
# 设置 DXVK Native 头文件路径 (需要包含 directx/ 和 windows/ 子目录)
set(DXVK_NATIVE_INCLUDE_DIRS
    ${CMAKE_SOURCE_DIR}/dxvk/include/native/directx
    ${CMAKE_SOURCE_DIR}/dxvk/include/native/windows
    ${CMAKE_SOURCE_DIR}/dxvk/include/native/wsi
    ${CMAKE_SOURCE_DIR}/dxvk/include/native
)
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
# 设置 C++ 版本
set(CMAKE_CXX_STANDARD 20)
# 添加你的主程序源码
add_library(${CMAKE_PROJECT_NAME} SHARED
        sdl_entry.c
        netcorehost_launcher.cpp
        corehost_trace_redirect.cpp
        jni_bridge.c
        app_logger.c
        app_logger_jni.c
        renderer_loader.c
        thread_affinity_manager.cpp
        osmesa/osmesa_loader.c
        osmesa/osm_bridge.c
        osmesa/osm_renderer.c
        osmesa/osm_renderer_jni.c
        osmesa/vulkan_loader.c
        vulkan_turnip_loader.c
        dxvk_loader.c
        turnip_loader_nsbypass.cpp
        touch_bridge.c
        virtual_mouse_sdl.c
        And64InlineHook/And64InlineHook.cpp

)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        # GL 现在是共享库，运行时动态加载，不再静态链接
        SDL2            # SDL2使用EGL后端
        dl              # 用于dlopen/dlsym（动态加载EGL库）
        FAudio-shared   # FAudio音频库
        FNA3D           # FNA3D图形库
        mojoshader      # MojoShader着色器库（FNA3D需要）
        netcorehost     # .NET Core Hosting库
        linkernsbypass  # Android命名空间绕过库
        c++_shared
        ${CXX_SHARED_LIB}
        # lgogdownloader
)

# 包含 SDL、FNA3D、netcorehost 头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include
        ${FNA3D_SOURCE_DIR}/include
        ${NETCOREHOST_SOURCE_DIR}/include
        ${LGOG_SOURCE_DIR}/include
        ${CMAKE_SOURCE_DIR}/liblinkernsbypass
        ${CMAKE_SOURCE_DIR}  # For GL/osmesa.h and other local headers
)

install(TARGETS ${CMAKE_PROJECT_NAME} SDL2 FAudio-shared FNA3D netcorehost DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(TARGETS GL_gl4es EGL_gl4es DESTINATION ${CMAKE_INSTALL_LIBDIR})