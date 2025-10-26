cmake_minimum_required(VERSION 3.22.1)
project("main")

# 设置路径
set(SDL_SOURCE_DIR ${CMAKE_SOURCE_DIR}/SDL)
set(GL4ES_SOURCE_DIR ${CMAKE_SOURCE_DIR}/gl4es)
#set(LGOG_SOURCE_DIR ${CMAKE_SOURCE_DIR}/lgogdownloader)
# 根据当前编译的架构动态选择库路径
set(RUSTDOTNET_LIB_PATH ${CMAKE_SOURCE_DIR}/../jnilibs/${ANDROID_ABI}/librustcorehost.so)

# 添加SDL子项目
add_subdirectory(${SDL_SOURCE_DIR})

# 添加gl4es子项目
add_subdirectory(${GL4ES_SOURCE_DIR})

# 添加你的主程序源码
add_library(${CMAKE_PROJECT_NAME} SHARED
        SDL_android_main.c
        And64InlineHook.cpp

)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
        egl
        android
        log
        SDL2
        GL
       # lgogdownloader
        ${RUSTDOTNET_LIB_PATH}
)

# 包含 SDL 和 gl4es 头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include
        ${GL4ES_SOURCE_DIR}/include
        ${LGOG_SOURCE_DIR}/include
)