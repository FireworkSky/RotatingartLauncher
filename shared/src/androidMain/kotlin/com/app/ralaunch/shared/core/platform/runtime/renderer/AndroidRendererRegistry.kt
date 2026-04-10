package com.app.ralaunch.shared.core.platform.runtime.renderer

object AndroidRendererRegistry {
    val ID_NATIVE: String = RendererRegistry.ID_NATIVE
    val ID_GL4ES: String = RendererRegistry.ID_GL4ES
    val ID_GL4ES_ANGLE: String = RendererRegistry.ID_GL4ES_ANGLE
    val ID_MOBILEGLUES: String = RendererRegistry.ID_MOBILEGLUES
    val ID_ANGLE: String = RendererRegistry.ID_ANGLE
    val ID_ZINK: String = RendererRegistry.ID_ZINK

    fun getRendererInfo(rendererId: String): RendererInfo? = RendererRegistry.getRendererInfo(rendererId)

    fun getRendererDisplayName(rendererId: String): String =
        RendererRegistry.getRendererDisplayName(rendererId)

    fun getRendererDescription(rendererId: String): String =
        RendererRegistry.getRendererDescription(rendererId)

    fun getCompatibleRenderers(): List<RendererInfo> = RendererRegistry.getCompatibleRenderers()

    fun isKnownRendererId(id: String): Boolean = RendererRegistry.isKnownRendererId(id)

    fun isRendererCompatible(rendererId: String): Boolean = RendererRegistry.isRendererCompatible(rendererId)

    fun getRendererLibraryPath(libraryName: String?): String? =
        RendererRegistry.getRendererLibraryPath(libraryName)

    fun buildRendererEnv(rendererId: String): Map<String, String?> =
        RendererRegistry.buildRendererEnv(rendererId)
}
