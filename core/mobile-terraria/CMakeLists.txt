include(FetchContent)

FetchContent_Declare(mobile_ogg
    URL https://downloads.xiph.org/releases/ogg/libogg-1.3.6.tar.gz
    URL_HASH SHA256=83e6704730683d004d20e21b8f7f55dcb3383cdf84c0daedf30bde175f774638
)
FetchContent_Declare(mobile_vorbis
    URL https://downloads.xiph.org/releases/vorbis/libvorbis-1.3.7.tar.gz
    URL_HASH SHA256=0e982409a9c3fc82ee06e08205b1355e5c6aa4c36bca58146ef399621b0ce5ab
)
foreach(dependency IN ITEMS mobile_ogg mobile_vorbis)
    FetchContent_GetProperties(${dependency})
    if(NOT ${dependency}_POPULATED)
        FetchContent_Populate(${dependency})
    endif()
endforeach()

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF)
set(INSTALL_DOCS OFF)
FetchContent_Declare(mobile_vgmstream
    GIT_REPOSITORY https://github.com/vgmstream/vgmstream.git
    GIT_TAG 24ffd6ce3e1f34fdebf9f5316d18ad2e162838ce
)
FetchContent_GetProperties(mobile_vgmstream)
if(NOT mobile_vgmstream_POPULATED)
    FetchContent_Populate(mobile_vgmstream)
endif()

set(VGM_SOURCE_DIR "${mobile_vgmstream_SOURCE_DIR}")
set(VGM_BINARY_DIR "${mobile_vgmstream_BINARY_DIR}")
add_subdirectory("${mobile_ogg_SOURCE_DIR}" "${VGM_BINARY_DIR}/dependencies/ogg" EXCLUDE_FROM_ALL)
# Vorbis's FindOgg expects these values even when the Ogg target already exists.
set(OGG_LIBRARY ogg)
set(OGG_INCLUDE_DIR "${mobile_ogg_SOURCE_DIR}/include")
add_subdirectory("${mobile_vorbis_SOURCE_DIR}" "${VGM_BINARY_DIR}/dependencies/vorbis" EXCLUDE_FROM_ALL)

set(USE_VORBIS ON)
set(VORBISFILE_INCLUDE_DIRS "${mobile_vorbis_SOURCE_DIR}/include"
    "${mobile_ogg_SOURCE_DIR}/include" "${VGM_BINARY_DIR}/dependencies/ogg/include")
include("${VGM_SOURCE_DIR}/cmake/vgmstream.cmake")
add_subdirectory("${VGM_SOURCE_DIR}/src" "${VGM_BINARY_DIR}/src" EXCLUDE_FROM_ALL)

FetchContent_Declare(mobile_lz4
    URL https://codeload.github.com/lz4/lz4/tar.gz/refs/tags/v1.10.0
    URL_HASH SHA256=537512904744b35e232912055ccf8ec66d768639ff3abe5788d90d792ec5f48b
)
FetchContent_GetProperties(mobile_lz4)
if(NOT mobile_lz4_POPULATED)
    FetchContent_Populate(mobile_lz4)
endif()

add_library(terraria_converter SHARED
    adpcm.cpp control_art.cpp converter.cpp fsb5.c jni.cpp lzx.cpp unity.cpp xnb.cpp
    "${mobile_lz4_SOURCE_DIR}/lib/lz4.c"
)
target_compile_features(terraria_converter PRIVATE cxx_std_20)
# Debug APKs also transcode the full soundtrack on the device.
foreach(target IN ITEMS terraria_converter libvgmstream vorbisfile vorbis ogg)
    target_compile_options(${target} PRIVATE "$<$<CONFIG:Debug>:-O2>")
endforeach()
set_target_properties(terraria_converter PROPERTIES C_VISIBILITY_PRESET hidden CXX_VISIBILITY_PRESET hidden)
target_include_directories(terraria_converter PRIVATE "${mobile_vgmstream_SOURCE_DIR}/src" "${mobile_lz4_SOURCE_DIR}/lib")
target_link_libraries(terraria_converter PRIVATE libvgmstream vorbisfile vorbis ogg m z)
target_link_options(terraria_converter PRIVATE "-Wl,--exclude-libs,ALL")
