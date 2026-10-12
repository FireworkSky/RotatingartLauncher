package com.app.ralaunch.core.platform.runtime

/**
 * 运行时标识串（Runtime Spec）
 *
 * 用一个字符串同时表达「用哪个 .NET 运行时引擎」和「用哪个版本」，
 * 这样现有的运行时选择对话框只需沿用 `onSelect(version: String)` 单个回调
 * 就能把 CoreCLR 与 Mono 放在同一个列表里。
 *
 * 编码约定：
 * - CoreCLR（原有行为，保持向后兼容）：直接就是版本号，例如 `10.0.1`
 * - Mono：`mono:<版本>`，例如 `mono:4.5`
 */
object RuntimeSpec {

    const val ENGINE_DOTNET = "dotnet"
    const val ENGINE_MONO = "mono"

    private const val MONO_PREFIX = "$ENGINE_MONO:"

    /** Mono 运行时使用的版本目录名（对应 mono 的 mono/4.5 profile） */
    const val MONO_DEFAULT_VERSION = "4.5"

    data class Decoded(
        val engine: String,
        val version: String
    ) {
        val isMono: Boolean get() = engine == ENGINE_MONO
    }

    /** 把引擎与版本编码成运行时标识串 */
    fun encode(engine: String, version: String): String {
        val normalizedVersion = version.trim()
        return if (engine == ENGINE_MONO) "$MONO_PREFIX$normalizedVersion" else normalizedVersion
    }

    /** 编码一个 Mono 运行时标识串 */
    fun encodeMono(version: String = MONO_DEFAULT_VERSION): String = encode(ENGINE_MONO, version)

    /**
     * 解析运行时标识串。
     * 无法识别时按 CoreCLR 处理，保证旧设置值继续可用。
     */
    fun decode(spec: String?): Decoded {
        val raw = spec?.trim().orEmpty()
        if (raw.startsWith(MONO_PREFIX, ignoreCase = true)) {
            val version = raw.substring(MONO_PREFIX.length).trim().ifBlank { MONO_DEFAULT_VERSION }
            return Decoded(ENGINE_MONO, version)
        }
        return Decoded(ENGINE_DOTNET, raw)
    }

    /** 判断标识串是否指向 Mono */
    fun isMono(spec: String?): Boolean = decode(spec).isMono

    /** 展示用名称 */
    fun displayName(spec: String?): String {
        val decoded = decode(spec)
        return if (decoded.isMono) {
            "Mono ${decoded.version}"
        } else {
            decoded.version
        }
    }
}
