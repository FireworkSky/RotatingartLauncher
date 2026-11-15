# SDL2 ABI for older FNA games, backed by the same SDL3 as the Activity/FNA3D.
# 2.32.60 requires SDL >= 3.2.12; keep the archive pinned and verified.
include(FetchContent)
set(SDL2COMPAT_TESTS OFF CACHE BOOL "" FORCE)
set(SDL2COMPAT_INSTALL OFF CACHE BOOL "" FORCE)
set(SDL2COMPAT_X11 OFF CACHE BOOL "" FORCE)
FetchContent_Declare(sdl2_compat
    URL https://codeload.github.com/libsdl-org/sdl2-compat/zip/9831c2e1fcf0a115a351339dfa29d7f32e202f36
    URL_HASH SHA256=7400ef9e08cc705891f7966b68b5759c4b74860e6a1a3dc389ac30579bd07a54
)
FetchContent_MakeAvailable(sdl2_compat)

# The real SDL2 remains available for the SDL2 Activity. In SDL3 processes,
# preload this library before FNA resolves libSDL2.so (bionic matches SONAME).
set_target_properties(SDL2 PROPERTIES OUTPUT_NAME "SDL2_compat" NO_SONAME TRUE)
target_link_options(SDL2 PRIVATE "-Wl,-soname,libSDL2.so")
