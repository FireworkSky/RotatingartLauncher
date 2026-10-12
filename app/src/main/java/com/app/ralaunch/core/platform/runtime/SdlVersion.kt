package com.app.ralaunch.core.platform.runtime

/**
 * SDL 版本选择。
 *
 * 启动器同时内置两套 SDL：
 *  - [SDL3]：走 Android 视频后端，需要 org.libsdl.app 的 Java 绑定层（SDLActivity）。
 *  - [SDL2]：走 Wayland 视频后端，不含 Java 绑定层，由应用内 Wayland 合成器
 *    （org.libsdl.app2）自己持有窗口与 Surface，因此可与 SDL3 的
 *    Java 绑定共存于同一 APK。
 *
 * 两套各自对应一个入口 Activity（[GameActivity][com.app.ralaunch.feature.game.ui.legacy.GameActivity]
 * 与 GameActivitySdl2），由 GameLaunchManager 依据本设置的值决定启动哪一个。
 */
object SdlVersion {
    const val SDL2 = "sdl2"
    const val SDL3 = "sdl3"

    /** 归一化：未知值按 SDL3 处理，保证旧设置值继续可用（向后兼容）。 */
    fun normalize(raw: String?): String =
        if (raw?.trim()?.lowercase() == SDL2) SDL2 else SDL3

    fun isSdl2(raw: String?): Boolean = normalize(raw) == SDL2

    /** 展示用名称 */
    fun displayName(raw: String?): String =
        if (isSdl2(raw)) "SDL2 · Wayland" else "SDL3 · Android"
}
