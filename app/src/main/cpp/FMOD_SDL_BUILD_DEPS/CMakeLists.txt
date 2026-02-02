cmake_minimum_required(VERSION 3.22.1)
project(fmod_sdl C)

# 获取 USE_SDL3 配置（从父 CMakeLists.txt 传入）
if(NOT DEFINED USE_SDL3)
    set(USE_SDL3 OFF)
endif()

# Build the FMOD stub library (static, only needed at link time)
add_library(fmod_stub STATIC
    fmod_stub.c
)
target_include_directories(fmod_stub PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Build the FMOD_SDL library
add_library(fmod_SDL SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/../FMOD_SDL/FMOD_SDL.c
)
target_include_directories(fmod_SDL PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../FMOD_SDL
)

# 根据 SDL 版本链接不同的库
if(USE_SDL3)
    message(STATUS "FMOD_SDL: Using SDL3")
    target_link_libraries(fmod_SDL PRIVATE
        SDL3-shared
        fmod_stub
    )
    target_compile_definitions(fmod_SDL PRIVATE USE_SDL3)
else()
    message(STATUS "FMOD_SDL: Using SDL2")
    target_link_libraries(fmod_SDL PRIVATE
        SDL2
        fmod_stub
    )
endif()

target_compile_options(fmod_SDL PRIVATE
    -O3 -Wall -pedantic
)
