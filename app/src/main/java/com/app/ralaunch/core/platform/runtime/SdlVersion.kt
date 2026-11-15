package com.app.ralaunch.core.platform.runtime

import com.google.gson.JsonParser
import java.io.File

/**
 * SDL 版本选择。
 *
 * 启动器同时内置两套 SDL：
 *  - [SDL3]：走 Android 视频后端，需要 org.libsdl.app 的 Java 绑定层（SDLActivity）。
 *  - [SDL2]：走 Android 视频后端，使用包名改为 org.libsdl.app2 的 SDL2 Java 胶水层，
 *    因此可与 SDL3 的 Java 绑定共存于同一 APK。
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

    /** Older FNA keeps its SDL2 managed backend while sdl2-compat supplies the SDL3 runtime. */
    fun requiresSdl2Compat(assembly: File): Boolean {
        val fna = findFna(assembly) ?: return false
        val metadata = runCatching { fna.readText(Charsets.ISO_8859_1) }.getOrNull()
            ?: return false
        return metadata.startsWith("MZ") &&
            metadata.contains("\u0000SDL2_FNAPlatform\u0000") &&
            !metadata.contains("\u0000SDL3_FNAPlatform\u0000")
    }

    private fun findFna(assembly: File): File? {
        val directory = assembly.parentFile ?: return null
        directory.resolve("FNA.dll").takeIf { it.isFile }?.let { return it }
        val libraries = directory.resolve("Libraries/FNA")
        val depsFile = directory.resolve("${assembly.nameWithoutExtension}.deps.json")
        val version = runCatching {
            JsonParser.parseString(depsFile.readText()).asJsonObject
                .getAsJsonObject("libraries").keySet()
                .singleOrNull { it.startsWith("FNA/") }?.substringAfter('/')
        }.getOrNull()
        if (version != null) {
            return libraries.resolve("$version/FNA.dll").takeIf { it.isFile }
        }
        return libraries.listFiles()?.map { it.resolve("FNA.dll") }
            ?.filter { it.isFile }?.singleOrNull()
    }

    /** 展示用名称 */
    fun displayName(raw: String?): String =
        if (isSdl2(raw)) "SDL2" else "SDL3"
}
