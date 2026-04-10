package com.app.ralaunch.shared.core.platform.runtime.renderer

expect object RendererRegistry {
    fun normalizeRendererId(raw: String?): String
}
