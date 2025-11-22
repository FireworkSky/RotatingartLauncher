/*
  Simple DirectMedia Layer
  Copyright (C) 1997-2024 Sam Lantinga <slouken@libsdl.org>

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.
*/

/**
 * @file SDL_androidrenderer.h
 *
 * Android Dynamic Renderer Loading
 *
 * 参考 lwjgl3 的 SharedLibrary 和 PojavLauncher 的动态加载机制
 * 实现运行时动态切换渲染器，无需重新编译
 */

#ifndef SDL_androidrenderer_h_
#define SDL_androidrenderer_h_

#include "../../SDL_internal.h"
#include "../SDL_sysvideo.h"

/**
 * 渲染器后端信息
 */
typedef struct {
    const char *name;           /* 渲染器名称 (native, gl4es, angle, etc.) */
    const char *egl_library;    /* EGL 库路径 (NULL = 系统默认) */
    const char *gles_library;   /* GLES 库路径 (NULL = 系统默认) */
    SDL_bool need_preload;      /* 是否需要通过 LD_PRELOAD 加载 */
} SDL_RendererBackend;

/**
 * 动态加载渲染器
 *
 * 通过 dlopen + LD_PRELOAD 机制加载指定的渲染器库
 *
 * @param renderer_name 渲染器名称 (从环境变量 SDL_RENDERER 或 FNA3D_OPENGL_DRIVER 读取)
 * @return SDL_TRUE 成功, SDL_FALSE 失败
 */
extern SDL_bool Android_LoadRenderer(const char *renderer_name);

/**
 * 设置 OpenGL 函数指针到 video device
 *
 * 根据加载的渲染器设置对应的 GL 函数指针
 * 如果使用 LD_PRELOAD，所有渲染器都提供标准 EGL 接口，直接使用 SDL_EGL 函数
 *
 * @param device SDL video device
 * @return SDL_TRUE 成功, SDL_FALSE 失败
 */
extern SDL_bool Android_SetupGLFunctions(SDL_VideoDevice *device);

/**
 * 获取当前加载的渲染器名称
 */
extern const char* Android_GetCurrentRenderer(void);

/**
 * 获取当前渲染器的EGL库路径
 */
extern const char* Android_GetCurrentRendererLibPath(void);

#endif /* SDL_androidrenderer_h_ */
