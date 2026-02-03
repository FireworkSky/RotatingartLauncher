# Main library CMakeLists.txt
# FNA3D .NET 游戏启动器 - 主 JNI 桥接库

set(MAIN_SOURCES
        sdl_entry.c
        jni_bridge.c
        app_logger.c
        app_logger_jni.c
        renderer_loader.c
        thread_affinity_manager.cpp
        touch_bridge.c
        virtual_mouse_sdl.c
        sdl_text_bridge.c
        And64InlineHook/And64InlineHook.cpp
        shared_envvars.cpp
        dotnet/dotnet_launcher.cpp
        dotnet/corehost_trace_redirect.cpp
        utils/native_methods.cpp
        core/game_launcher.cpp
)

add_library(${CMAKE_PROJECT_NAME} SHARED ${MAIN_SOURCES})

# 链接库 - FNA3D .NET 游戏所需
target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        SDL2
        dl
        FAudio-shared
        FNA3D
        mojoshader
        netcorehost
        linkernsbypass
        c++_shared
        ${CXX_SHARED_LIB}
)

# 包含头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include
        ${FNA3D_SOURCE_DIR}/include
        ${NETCOREHOST_SOURCE_DIR}/include
        ${CMAKE_SOURCE_DIR}/liblinkernsbypass
        ${CMAKE_CURRENT_SOURCE_DIR}
)
