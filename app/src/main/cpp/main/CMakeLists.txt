# Main library CMakeLists.txt

# 添加主程序源码
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
        ${CMAKE_CURRENT_SOURCE_DIR}  # For local main headers
)
