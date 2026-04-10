package com.app.ralaunch.core.platform.runtime.renderer

import android.content.Context
import android.util.Log
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import com.app.ralaunch.shared.core.platform.runtime.renderer.AndroidRendererRegistry
import com.app.ralaunch.shared.core.platform.runtime.renderer.RendererRegistry

object RendererEnvironmentConfigurator {
    private const val TAG = "RendererEnvironmentConfigurator"

    @JvmStatic
    fun getEffectiveRenderer(): String {
        return RendererRegistry.normalizeRendererId(SettingsAccess.fnaRenderer)
    }

    internal fun resolveRendererForLaunch(
        globalEffectiveRenderer: String,
        rendererOverride: String?,
        isOverrideCompatible: Boolean = true
    ): String {
        val rawOverride = rendererOverride?.trim()
        if (rawOverride.isNullOrEmpty()) return globalEffectiveRenderer
        if (!AndroidRendererRegistry.isKnownRendererId(rawOverride)) return globalEffectiveRenderer

        val normalizedOverride = RendererRegistry.normalizeRendererId(rawOverride)
        return when {
            AndroidRendererRegistry.getRendererInfo(normalizedOverride) == null -> globalEffectiveRenderer
            !isOverrideCompatible -> globalEffectiveRenderer
            else -> normalizedOverride
        }
    }

    @JvmStatic
    fun apply(context: Context?, rendererOverride: String? = null) {
        val globalRenderer = getEffectiveRenderer()
        val normalizedOverride = rendererOverride?.let { RendererRegistry.normalizeRendererId(it) }
        val overrideCompatible = normalizedOverride?.let { renderer ->
            context == null || AndroidRendererRegistry.isRendererCompatible(renderer)
        } ?: true

        val renderer = resolveRendererForLaunch(
            globalEffectiveRenderer = globalRenderer,
            rendererOverride = rendererOverride,
            isOverrideCompatible = overrideCompatible
        )

        if (rendererOverride != null) {
            val rawOverride = rendererOverride.trim()
            when {
                rawOverride.isEmpty() || !RendererRegistry.isKnownRendererId(rawOverride) ->
                    Log.w(TAG, "Renderer override is invalid: $rendererOverride, fallback to global: $globalRenderer")
                !overrideCompatible ->
                    Log.w(TAG, "Renderer override is incompatible on this device: $rawOverride, fallback to global: $globalRenderer")
                else ->
                    Log.i(TAG, "Using per-game renderer override: ${RendererRegistry.normalizeRendererId(rawOverride)}")
            }
        }

        loadRendererLibraries(context, renderer)
        applyFna3dEnvironment(renderer)

        Log.i(TAG, "Renderer environment applied successfully for: $renderer")
        Log.i(TAG, "Effective renderer after apply: ${RendererLoader.getCurrentRenderer()}")
    }

    private fun loadRendererLibraries(context: Context?, renderer: String) {
        if (context == null) return
        val success = RendererLoader.loadRenderer(context, renderer)

        if (success) {
            Log.i(TAG, "当前渲染器: ${RendererLoader.getCurrentRenderer()}")
        } else {
            Log.e(TAG, "Failed to load renderer: $renderer")
        }
    }

    private fun applyFna3dEnvironment(renderer: String) {
        val fna3dEnvVars = buildFna3dEnvVars(renderer)
        EnvVarsManager.quickSetEnvVars(fna3dEnvVars)
        logFna3dConfiguration(renderer, fna3dEnvVars)
    }

    private fun buildFna3dEnvVars(renderer: String): Map<String, String?> {
        val envVars = mutableMapOf<String, String?>()

        envVars["FNA3D_FORCE_DRIVER"] = "OpenGL"
        envVars["FNA3D_OPENGL_DRIVER"] = mapRendererToFnaDriver(renderer)
        envVars.putAll(getOpenGlVersionConfig(renderer))
        envVars.putAll(getQualityConfig())

        return envVars
    }

    private fun mapRendererToFnaDriver(renderer: String): String {
        return when (renderer) {
            RendererRegistry.ID_MOBILEGLUES -> "mobileglues"
            else -> "OpenGL"
        }
    }

    private fun getQualityConfig(): Map<String, String?> {
        val settings = SettingsAccess
        val envVars = mutableMapOf<String, String?>()

        val qualityLevel = settings.fnaQualityLevel
        when (qualityLevel) {
            1 -> {
                envVars["FNA3D_TEXTURE_LOD_BIAS"] = "1.0"
                envVars["FNA3D_MAX_ANISOTROPY"] = "2"
                envVars["FNA3D_RENDER_SCALE"] = "0.85"
            }
            2 -> {
                envVars["FNA3D_TEXTURE_LOD_BIAS"] = "2.0"
                envVars["FNA3D_MAX_ANISOTROPY"] = "1"
                envVars["FNA3D_RENDER_SCALE"] = "0.7"
                envVars["FNA3D_SHADER_LOW_PRECISION"] = "1"
            }
            else -> {
                val lodBias = settings.fnaTextureLodBias
                val maxAnisotropy = settings.fnaMaxAnisotropy
                val renderScale = settings.fnaRenderScale
                val shaderLowPrecision = settings.isFnaShaderLowPrecision

                if (lodBias > 0f) {
                    envVars["FNA3D_TEXTURE_LOD_BIAS"] = lodBias.toString()
                }
                if (maxAnisotropy < 16) {
                    envVars["FNA3D_MAX_ANISOTROPY"] = maxAnisotropy.toString()
                }
                if (renderScale < 1.0f) {
                    envVars["FNA3D_RENDER_SCALE"] = renderScale.toString()
                }
                if (shaderLowPrecision) {
                    envVars["FNA3D_SHADER_LOW_PRECISION"] = "1"
                }
            }
        }

        val targetFps = settings.fnaTargetFps
        if (targetFps > 0) {
            envVars["FNA3D_TARGET_FPS"] = targetFps.toString()
        }

        return envVars
    }

    private fun getOpenGlVersionConfig(renderer: String): Map<String, String?> {
        return when (renderer) {
            AndroidRendererRegistry.ID_GL4ES,
            AndroidRendererRegistry.ID_ZINK -> {
                buildMap {
                    put("FNA3D_OPENGL_FORCE_ES3", null)
                    put("FNA3D_OPENGL_FORCE_VER_MAJOR", null)
                    put("FNA3D_OPENGL_FORCE_VER_MINOR", null)
                }
            }
            else -> {
                mapOf(
                    "FNA3D_OPENGL_FORCE_ES3" to "1",
                    "FNA3D_OPENGL_FORCE_VER_MAJOR" to "3",
                    "FNA3D_OPENGL_FORCE_VER_MINOR" to "0"
                )
            }
        }
    }

    private fun getMapBufferRangeValue(renderer: String): String? {
        val vulkanTranslatedRenderers = setOf(
            AndroidRendererRegistry.ID_ANGLE,
            AndroidRendererRegistry.ID_GL4ES_ANGLE,
            AndroidRendererRegistry.ID_ZINK
        )

        return when {
            renderer in vulkanTranslatedRenderers -> "0"
            else -> {
                val settings = SettingsAccess
                if (settings.isFnaEnableMapBufferRangeOptimization) null else "0"
            }
        }
    }

    private fun logFna3dConfiguration(renderer: String, envVars: Map<String, String?>) {
        Log.i(TAG, "=== FNA3D Configuration ===")
        Log.i(TAG, "Renderer ID: $renderer")
        Log.i(TAG, "FNA3D_OPENGL_DRIVER = ${envVars["FNA3D_OPENGL_DRIVER"]}")
        Log.i(TAG, "FNA3D_FORCE_DRIVER = ${envVars["FNA3D_FORCE_DRIVER"]}")

        when (renderer) {
            AndroidRendererRegistry.ID_GL4ES ->
                Log.i(TAG, "OpenGL Profile: Desktop OpenGL 2.1 Compatibility Profile")
            AndroidRendererRegistry.ID_ZINK ->
                Log.i(TAG, "OpenGL Profile: Desktop OpenGL 4.3 (Mesa Zink over Vulkan)")
            RendererRegistry.ID_ANGLE,
            RendererRegistry.ID_GL4ES_ANGLE,
            RendererRegistry.ID_NATIVE,
            RendererRegistry.ID_MOBILEGLUES ->
                Log.i(TAG, "OpenGL Profile: OpenGL ES 3.0")
            else ->
                Log.i(TAG, "OpenGL Profile: OpenGL ES")
        }

        val mapBufferRange = envVars["FNA3D_OPENGL_USE_MAP_BUFFER_RANGE"]
        when {
            mapBufferRange == "0" && renderer in setOf(RendererRegistry.ID_ANGLE, RendererRegistry.ID_GL4ES_ANGLE) ->
                Log.i(TAG, "Map Buffer Range: Disabled (translated renderer)")
            mapBufferRange == "0" ->
                Log.i(TAG, "Map Buffer Range: Disabled (via settings)")
            else ->
                Log.i(TAG, "Map Buffer Range: Enabled by default")
        }

        Log.i(TAG, "VSync: Controlled by runtime/settings")
        Log.i(TAG, "===========================")
    }
}
