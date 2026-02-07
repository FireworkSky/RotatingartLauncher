cmake_minimum_required(VERSION 3.15)
project(dotnethost CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(WIN32)
    add_definitions(-D_WINDOWS)
endif()

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

set(DOTNETHOST_SOURCES
    src/nethost.cpp
    src/nethost_stub.cpp
    src/hostfxr.cpp
    src/error.cpp
    src/pdcstring.cpp
    src/context.cpp
)

set(DOTNETHOST_HEADERS
    include/dotnethost/nethost.hpp
    include/dotnethost/hostfxr.hpp
    include/dotnethost/error.hpp
    include/dotnethost/pdcstring.hpp
    include/dotnethost/context.hpp
    include/dotnethost/bindings.hpp
)

add_library(dotnethost SHARED ${DOTNETHOST_SOURCES} ${DOTNETHOST_HEADERS})

target_include_directories(dotnethost
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

if(WIN32)
    target_link_libraries(dotnethost PRIVATE)
elseif(ANDROID)
    find_library(CXX_SHARED_LIB c++_shared REQUIRED)
    target_link_libraries(dotnethost PRIVATE dl log ${CXX_SHARED_LIB})
else()
    target_link_libraries(dotnethost PRIVATE dl)
endif()

if(MSVC)
    target_compile_options(dotnethost PRIVATE /W4)
else()
    target_compile_options(dotnethost PRIVATE -Wall -Wextra -Wpedantic)
endif()

install(TARGETS dotnethost
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_LIBDIR}
)

install(DIRECTORY include/dotnethost DESTINATION include)
