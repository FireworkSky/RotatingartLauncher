cmake_minimum_required(VERSION 3.22.1)
project(fmod_sdl C)

# Build the FMOD_SDL library
add_library(fmod_SDL SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/../FMOD_SDL/FMOD_SDL.c
)
target_include_directories(fmod_SDL PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../FMOD_SDL
)
target_link_libraries(fmod_SDL PRIVATE
    SDL2
)
target_compile_options(fmod_SDL PRIVATE
    -O3 -Wall -pedantic
)
