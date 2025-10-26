# gl4es/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)

project(GL LANGUAGES C)

# 设置gl4es源文件
set(GL4ES_SOURCES
        src/gl/arbconverter.c
        src/gl/arbgenerator.c
        src/gl/arbhelper.c
        src/gl/arbparser.c
        src/gl/array.c
        src/gl/blend.c
        src/gl/blit.c
        src/gl/buffers.c
        src/gl/build_info.c
        src/gl/debug.c
        src/gl/decompress.c
        src/gl/depth.c
        src/gl/directstate.c
        src/gl/drawing.c
        src/gl/enable.c
        src/gl/envvars.c
        src/gl/eval.c
        src/gl/face.c
        src/gl/fog.c
        src/gl/fpe.c
        src/gl/fpe_cache.c
        src/gl/fpe_shader.c
        src/gl/framebuffers.c
        src/gl/gl_lookup.c
        src/gl/getter.c
        src/gl/gl4es.c
        src/gl/glstate.c
        src/gl/hint.c
        src/gl/init.c
        src/gl/light.c
        src/gl/line.c
        src/gl/list.c
        src/gl/listdraw.c
        src/gl/listrl.c
        src/gl/loader.c
        src/gl/logs.c
        src/gl/matrix.c
        src/gl/matvec.c
        src/gl/oldprogram.c
        src/gl/pixel.c
        src/gl/planes.c
        src/gl/pointsprite.c
        src/gl/preproc.c
        src/gl/program.c
        src/gl/queries.c
        src/gl/raster.c
        src/gl/render.c
        src/gl/samplers.c
        src/gl/shader.c
        src/gl/shaderconv.c
        src/gl/shader_hacks.c
        src/gl/stack.c
        src/gl/stencil.c
        src/gl/string_utils.c
        src/gl/stubs.c
        src/gl/texenv.c
        src/gl/texgen.c
        src/gl/texture.c
        src/gl/texture_compressed.c
        src/gl/texture_params.c
        src/gl/texture_read.c
        src/gl/texture_3d.c
        src/gl/uniform.c
        src/gl/vertexattrib.c
        src/gl/wrap/gl4eswraps.c
        src/gl/wrap/gles.c
        src/gl/wrap/glstub.c
        src/gl/math/matheval.c
        src/glx/hardext.c
        src/glx/glx.c
        src/glx/lookup.c
        src/glx/gbm.c
        src/glx/streaming.c
)

# 创建gl4es共享库
add_library(GL SHARED ${GL4ES_SOURCES})

# 设置编译选项
target_compile_definitions(GL PRIVATE
        NOX11
        NO_GBM
        DEFAULT_ES=2
)

target_compile_options(GL PRIVATE
        -g
        -std=gnu99
        -funwind-tables
        -O3
        -Wno-typedef-redefinition
        -Wno-dangling-else
)

# 添加包含目录
target_include_directories(GL PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# 链接库
target_link_libraries(GL
        log
        android
        EGL
)

# 设置动态库版本
set_target_properties(GL PROPERTIES
        VERSION 1.0.0
        SOVERSION 1
)