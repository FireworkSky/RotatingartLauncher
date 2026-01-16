cmake_minimum_required(VERSION 3.10)
project(glibc_bridge VERSION 1.0.0 LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ============================================================================
# 构建选项
# ============================================================================

option(GLIBC_BRIDGE_DEBUG "启用调试功能" OFF)

# ============================================================================
# 编译器标志
# ============================================================================

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-unused-parameter -fPIC")
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -g -O0 -DDEBUG")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O2 -DNDEBUG")

if(GLIBC_BRIDGE_DEBUG)
    add_definitions(-DGLIBC_BRIDGE_DEBUG=1)
endif()

# ============================================================================
# 包含目录
# ============================================================================

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/compat
    ${CMAKE_CURRENT_SOURCE_DIR}/src/elf
    ${CMAKE_CURRENT_SOURCE_DIR}/src/core
)

# ============================================================================
# 源文件 - 核心模块 (core/)
# ============================================================================

set(GLIBC_BRIDGE_CORE_SOURCES
    src/core/core.c           # 主 API 实现
    src/core/loader.c         # ELF 解析和加载
    src/core/runner.c         # 栈设置、TLS 和执行
)

# ============================================================================
# 源文件 - ELF 和动态链接 (elf/)
# ============================================================================

set(GLIBC_BRIDGE_ELF_SOURCES
    src/elf/elf_loader.c      # ELF 共享库加载器
    src/elf/dynlink.c         # 动态链接主模块
    src/elf/reloc.c           # 重定位处理
    src/elf/resolver.c        # 符号解析
    src/elf/symbol_table.c    # 符号表（glibc->bionic 映射）
    src/elf/log.c             # 日志工具
)

# ============================================================================
# 源文件 - 兼容层 (compat/)
# ============================================================================

set(GLIBC_BRIDGE_COMPAT_SOURCES
    src/compat/tls.c          # TLS 兼容层
    src/compat/stdio.c        # stdio FILE 结构转换
    src/compat/root.c         # 身份和信号兼容
    src/compat/error_hook.c   # 错误钩子
)

# ============================================================================
# 源文件 - JNI 接口 (jni/)
# ============================================================================

set(GLIBC_BRIDGE_JNI_SOURCES
    src/jni/jni.c             # JNI 入口点
)

# ============================================================================
# 源文件 - 包装函数 (wrapper/)
# glibc 函数到 bionic 的包装实现
# ============================================================================

set(GLIBC_BRIDGE_WRAPPER_SOURCES
    src/wrappers/wrapper_path.c       # 路径翻译工具
    src/wrappers/wrapper_cxx.c        # C++ 运行时
    src/wrappers/wrapper_dl.c         # 动态链接 (dlopen/dlsym)
    src/wrappers/wrapper_fortify.c    # FORTIFY 安全函数
    src/wrappers/wrapper_fs.c         # 文件系统操作
    src/wrappers/wrapper_gettext.c    # 国际化（gettext）
    src/wrappers/wrapper_ipc.c        # IPC (共享内存/信号量/消息队列)
    src/wrappers/wrapper_locale.c     # 本地化
    src/wrappers/wrapper_math.c       # 数学函数
    src/wrappers/wrapper_misc.c       # 杂项函数
    src/wrappers/wrapper_mlock.c      # 内存锁定
    src/wrappers/wrapper_network.c    # 网络函数
    src/wrappers/wrapper_obstack.c    # obstack 内存池
    src/wrappers/wrapper_proc.c       # /proc 文件系统
    src/wrappers/wrapper_process.c    # 进程控制
    src/wrappers/wrapper_pthread.c    # pthread 扩展
    src/wrappers/wrapper_search.c     # 搜索函数 (tsearch/hsearch)
    src/wrappers/wrapper_signal.c     # 信号处理
    src/wrappers/wrapper_stat.c       # 文件状态和路径转换
    src/wrappers/wrapper_stdio.c      # stdio 扩展
    src/wrappers/wrapper_string.c     # 字符串函数
    src/wrappers/wrapper_sysinfo.c    # 系统信息（uname 等）
    src/wrappers/wrapper_thread.c     # 线程函数
    src/wrappers/wrapper_time.c       # 时间函数
    src/wrappers/wrapper_ucontext.c   # 上下文切换
    src/wrappers/wrapper_wchar.c      # 宽字符函数
)

# ============================================================================
# glibc-bridge 共享库
# ============================================================================

add_library(glibc_bridge SHARED
    ${GLIBC_BRIDGE_CORE_SOURCES}
    ${GLIBC_BRIDGE_ELF_SOURCES}
    ${GLIBC_BRIDGE_COMPAT_SOURCES}
    ${GLIBC_BRIDGE_JNI_SOURCES}
    ${GLIBC_BRIDGE_WRAPPER_SOURCES}
)

target_include_directories(glibc_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/compat
    ${CMAKE_CURRENT_SOURCE_DIR}/src/elf
    ${CMAKE_CURRENT_SOURCE_DIR}/src/core
    ${CMAKE_CURRENT_SOURCE_DIR}/src/wrappers
)

# Android 特定库
find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(glibc_bridge
    ${log-lib}
    ${dl-lib}
    m
)

set_target_properties(glibc_bridge PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    OUTPUT_NAME "glibc_bridge"
)

# ============================================================================
# 构建摘要
# ============================================================================

message(STATUS "")
message(STATUS "========================================")
message(STATUS "glibc-bridge 配置摘要")
message(STATUS "========================================")
message(STATUS "版本: ${PROJECT_VERSION}")
message(STATUS "构建类型: ${CMAKE_BUILD_TYPE}")
message(STATUS "C 编译器: ${CMAKE_C_COMPILER}")
message(STATUS "========================================")
message(STATUS "")
