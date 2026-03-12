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

# FIXED: Detect correct SDL target name dynamically
if(NOT DEFINED SDL_TARGET)
    if(TARGET SDL2-shared)
        set(SDL_TARGET SDL2-shared)
    elseif(TARGET SDL3-shared)
        set(SDL_TARGET SDL3-shared)
    else()
        set(SDL_TARGET SDL2)
    endif()
endif()

# Link using the dynamic variable
target_link_libraries(fmod_SDL PRIVATE
    ${SDL_TARGET}
)

target_compile_options(fmod_SDL PRIVATE
    -O3 -Wall -pedantic
)
