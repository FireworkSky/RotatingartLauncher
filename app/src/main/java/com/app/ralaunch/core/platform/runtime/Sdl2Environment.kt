package com.app.ralaunch.core.platform.runtime

/**
 * SDL2 路径的运行环境变量（Android 视频后端）
 *
 * SDL2 与 SDL3 各自带一套 Java 胶水层，本工程把它们放在不同包名下互不冲突：
 *  - SDL3：org.libsdl.app.SDLActivity
 *  - SDL2：org.libsdl.app2.SDLActivity（官方胶水层，包名整体 +2）
 * 因此 SDL2 走的是 SDL 官方的 Android 视频后端 + 系统 EGL/GLES（厂商 GPU 加速），
 * 不再需要内嵌 Wayland 合成器与软件光栅 Mesa。
 *
 * 这里只做两件事：
 *  1) 让 FNA3D 走 OpenGL/GLES3（SDL_GPU 分支需要 SDL3，SDL2 下不可用）；
 *  2) 清掉可能残留的 Vulkan / SDL_GPU / Mesa / Wayland 相关变量，
 *     避免 SDL2 误用改名后的 Mesa 库或不存在的视频后端。
 *
 * 这些变量需在渲染器配置之后应用（见 [RendererEnvironmentConfigurator]），
 * 否则会被「Vulkan」档位的配置覆盖。
 */
object Sdl2Environment {

    const val TAG = "Sdl2Environment"

    /** 应用 SDL2 图形环境变量 */
    @JvmStatic
    fun apply() {
        EnvVarsManager.quickSetEnvVars(buildEnvVars())
    }

    /**
     * 构造 SDL2 环境变量表。
     *
     * null 表示清除该变量（避免全局渲染器设置残留）。
     */
    @JvmStatic
    fun buildEnvVars(): Map<String, String?> {
        val envVars = mutableMapOf<String, String?>()

        // FNA3D：SDL2 下只能用 OpenGL 驱动；Android/EGL 只提供 GLES，因此强制 ES3 上下文
        envVars["FNA3D_FORCE_DRIVER"] = "OpenGL"
        envVars["FNA3D_OPENGL_FORCE_ES3"] = "1"
        envVars["FNA3D_MOJOSHADER_PROFILE"] = "glsles3"

        // 清除 Vulkan / SDL_GPU 档位残留
        envVars["SDL_GPU_DRIVER"] = null
        envVars["FNA3D_OPENGL_DRIVER"] = null
        envVars["FNA3D_OPENGL_LIBRARY"] = null
        envVars["RALCORE_EGL"] = null
        envVars["LIBGL_GLES"] = null
        envVars["VK_ICD_FILENAMES"] = null

        // 视频后端交回 SDL2 自己的 Android 驱动（系统 libEGL/libGLESv2，走厂商 GPU）
        envVars["SDL_VIDEODRIVER"] = null
        envVars["SDL_VIDEO_EGL_DRIVER"] = null
        envVars["SDL_VIDEO_GL_DRIVER"] = null

        // 音频必须显式用 AAudio：SDL2 的 OpenSL ES 驱动把
        // impl.OnlyHasDefaultOutputDevice 设为 TRUE，SDL_OpenAudioDevice 会拒绝第二次打开默认设备
        // （"Audio device already open"）。tModLoader 会开两个：FNA 自身 + LegacyAudioSystem 的
        // FAudio AudioEngine(FACT)，于是引擎初始化直接崩。AAudio 驱动该标志为 FALSE，可以多开。
        envVars["SDL_AUDIODRIVER"] = "aaudio"

        // 清除 Wayland 合成器 / Mesa 软光栅专用变量（SDL2 路径不再使用）
        envVars["WAYLAND_DISPLAY"] = null
        envVars["XDG_RUNTIME_DIR"] = null
        envVars["LIBGL_DRIVERS_PATH"] = null
        envVars["LIBGL_ALWAYS_SOFTWARE"] = null
        envVars["GALLIUM_DRIVER"] = null
        envVars["MESA_LOADER_DRIVER_OVERRIDE"] = null
        envVars["MESA_DEBUG"] = null
        envVars["MESA_LOG"] = null
        envVars["LIBGL_DEBUG"] = null
        envVars["EGL_LOG_LEVEL"] = null
        envVars["MESA_GL_VERSION_OVERRIDE"] = null
        envVars["MESA_GLSL_VERSION_OVERRIDE"] = null
        envVars["MESA_NO_ERROR"] = null
        envVars["ZINK_DESCRIPTORS"] = null
        envVars["TU_DEBUG"] = null

        return envVars
    }
}
