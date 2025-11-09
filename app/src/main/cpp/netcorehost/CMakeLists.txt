cmake_minimum_required(VERSION 3.15)
project(netcorehost CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 平台检测
if(WIN32)
    add_definitions(-D_WINDOWS)
endif()

# 包含目录
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# 源文件
set(NETCOREHOST_SOURCES
    src/nethost.cpp
    src/hostfxr.cpp
    src/error.cpp
    src/pdcstring.cpp
    src/context.cpp
    src/delegate_loader.cpp
)

# 头文件
set(NETCOREHOST_HEADERS
    include/netcorehost/nethost.hpp
    include/netcorehost/hostfxr.hpp
    include/netcorehost/error.hpp
    include/netcorehost/pdcstring.hpp
    include/netcorehost/context.hpp
    include/netcorehost/delegate_loader.hpp
    include/netcorehost/bindings.hpp
)

# 创建静态库
add_library(netcorehost STATIC ${NETCOREHOST_SOURCES} ${NETCOREHOST_HEADERS})

# 设置目标属性
target_include_directories(netcorehost
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

# Windows 平台需要链接的库
if(WIN32)
    # Windows不需要额外链接库
    target_link_libraries(netcorehost PRIVATE)
elseif(ANDROID)
    # Android平台：链接dl库和.NET的libnethost.a
    # libnethost.a位于jniLibs目录中，根据ABI选择
    set(NETHOST_LIB_PATH ${CMAKE_CURRENT_SOURCE_DIR}/native/${ANDROID_ABI}/libnethost.a)
    target_link_libraries(netcorehost PRIVATE dl ${NETHOST_LIB_PATH})
else()
    # Unix/Linux/macOS需要链接dl库用于动态加载
    target_link_libraries(netcorehost PRIVATE dl)
endif()

# 设置编译选项
if(MSVC)
    target_compile_options(netcorehost PRIVATE /W4)
else()
    target_compile_options(netcorehost PRIVATE -Wall -Wextra -Wpedantic)
endif()

# 安装规则（可选）
install(TARGETS netcorehost
    EXPORT netcorehostTargets
    ARCHIVE DESTINATION lib
    LIBRARY DESTINATION lib
    RUNTIME DESTINATION bin
)

install(DIRECTORY include/netcorehost
    DESTINATION include
)

