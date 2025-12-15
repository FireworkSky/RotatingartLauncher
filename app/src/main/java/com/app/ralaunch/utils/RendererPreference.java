package com.app.ralaunch.utils;

import android.content.Context;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.renderer.RendererConfig;
import com.app.ralaunch.renderer.RendererLoader;

public final class RendererPreference {
    private static final String TAG = "RendererPreference";
    
    public static final String RENDERER_AUTO = "auto";

    @Deprecated
    public static final String RENDERER_OPENGLES3 = "opengles3";
    @Deprecated
    public static final String RENDERER_OPENGL_GL4ES = "opengl_gl4es";
    @Deprecated
    public static final String RENDERER_VULKAN = "vulkan";

    private RendererPreference() {}

    public static void setRenderer(Context context, String renderer) {
        if (renderer == null) return;
        SettingsManager.getInstance(context).setFnaRenderer(renderer);
    }

    public static String getRenderer(Context context) {
        SettingsManager manager = SettingsManager.getInstance(context);
        String raw = manager.getFnaRenderer();
        String normalized = normalizeRendererValue(raw);
        if (!normalized.equals(raw)) {
            manager.setFnaRenderer(normalized);
        }
        return normalized;
    }

    public static String getEffectiveRenderer(Context context) {
        String renderer = getRenderer(context);
        if (RENDERER_AUTO.equals(renderer)) {
            return RendererConfig.RENDERER_NATIVE_GLES;
        }
        return renderer;
    }

    public static String normalizeRendererValue(String value) {
        if (value == null || value.isEmpty()) {
            return RENDERER_AUTO;
        }

        switch (value) {
            case "opengl_native":
            case RENDERER_OPENGLES3:
                return RendererConfig.RENDERER_NATIVE_GLES;
            case RENDERER_OPENGL_GL4ES:
                return RendererConfig.RENDERER_GL4ES;
            case RENDERER_VULKAN:
                return RendererConfig.RENDERER_NATIVE_GLES;
            default:
                return value;
        }
    }

    public static void applyRendererEnvironment(Context context) {
        String preferredRenderer = getEffectiveRenderer(context);
        String rendererId = mapRendererToConfigId(preferredRenderer);

        boolean success = RendererLoader.loadRenderer(context, rendererId);

        if (success) {
         
            android.util.Log.i(TAG, "  当前渲染器: " + RendererLoader.getCurrentRenderer());
        } else {
            android.util.Log.e(TAG, "Failed to load renderer: " + rendererId);
        }

        setEnv("FNA3D_OPENGL_DRIVER", rendererId);
        android.util.Log.i(TAG, "FNA3D_OPENGL_DRIVER = " + rendererId);

        setEnv("FNA3D_FORCE_DRIVER", "OpenGL");

        if (RendererConfig.RENDERER_GL4ES.equals(rendererId) ||
            RendererConfig.RENDERER_ZINK.equals(rendererId)) {
            unsetEnv("FNA3D_OPENGL_FORCE_ES3");
            unsetEnv("FNA3D_OPENGL_FORCE_VER_MAJOR");
            unsetEnv("FNA3D_OPENGL_FORCE_VER_MINOR");

            if (RendererConfig.RENDERER_GL4ES.equals(rendererId)) {
                android.util.Log.i(TAG, "FNA3D configured for Desktop OpenGL 2.1 Compatibility Profile (renderer: gl4es)");
            } else if (RendererConfig.RENDERER_ZINK.equals(rendererId)) {
                android.util.Log.i(TAG, "FNA3D configured for Desktop OpenGL 4.6 (renderer: " + rendererId + ")");
                setEnv("FNA3D_MOJOSHADER_PROFILE", "glsles3");
                android.util.Log.i(TAG, "FNA3D_MOJOSHADER_PROFILE = glsles3 (forced for zink to avoid glspirv)");
            }
        } else {
            setEnv("FNA3D_OPENGL_FORCE_ES3", "1");
            setEnv("FNA3D_OPENGL_FORCE_VER_MAJOR", "3");
            setEnv("FNA3D_OPENGL_FORCE_VER_MINOR", "0");
            android.util.Log.i(TAG, "FNA3D configured for OpenGL ES 3.0 (renderer: " + rendererId + ")");
        }

        if (RendererConfig.RENDERER_ZINK.equals(rendererId) ||
            RendererConfig.RENDERER_ZINK_25.equals(rendererId) ||
            RendererConfig.RENDERER_ANGLE.equals(rendererId) ||
            RendererConfig.RENDERER_GL4ES_ANGLE.equals(rendererId)) {
            setEnv("FNA3D_OPENGL_USE_MAP_BUFFER_RANGE", "0");
            android.util.Log.i(TAG, "FNA3D_OPENGL_USE_MAP_BUFFER_RANGE = 0 (disabled for Vulkan-translated renderer: " + rendererId + ")");
        } else {
            android.util.Log.i(TAG, "FNA3D_OPENGL_USE_MAP_BUFFER_RANGE = enabled by default (can be disabled via env var)");
        }

        setEnv("FORCE_VSYNC", "true");
        System.setProperty("fna.renderer", preferredRenderer);
    }

    private static String mapRendererToConfigId(String rendererPreference) {
        String normalized = normalizeRendererValue(rendererPreference);
        if (RENDERER_AUTO.equals(normalized)) {
            return RendererConfig.RENDERER_NATIVE_GLES;
        }
        return normalized;
    }

    private static void setEnv(String key, String value) {
        try {
            android.system.Os.setenv(key, value, true);
        } catch (android.system.ErrnoException e) {
            android.util.Log.w(TAG, "无法设置环境变量 " + key + ": " + e.getMessage());
        }
    }

    private static void unsetEnv(String key) {
        try {
            android.system.Os.unsetenv(key);
        } catch (android.system.ErrnoException e) {
            android.util.Log.w(TAG, "无法清除环境变量 " + key + ": " + e.getMessage());
        }
    }
}

