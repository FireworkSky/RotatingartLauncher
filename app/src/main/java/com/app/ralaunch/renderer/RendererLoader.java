package com.app.ralaunch.renderer;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import com.app.ralaunch.utils.AppLogger;

import java.util.Map;

/**
 * 渲染器加载器 - 基于环境变量的简化实现
 *
 * 相比之前的 dlopen 方式，这个实现更简单、更可靠：
 * 1. 不需要手动 dlopen 库文件
 * 2. 不需要设置 LD_PRELOAD
 * 3. 只需要设置环境变量
 * 4. 让 SDL/FNA3D 自己根据环境变量选择渲染器
 */
public class RendererLoader {
    private static final String TAG = "RendererLoader";

    /**
     * 加载渲染器（通过设置环境变量）
     *
     * @param context    应用上下文
     * @param rendererId 渲染器 ID
     * @return 是否成功
     */
    public static boolean loadRenderer(Context context, String rendererId) {
        try {
            RendererConfig.RendererInfo renderer = RendererConfig.getRendererById(rendererId);
            if (renderer == null) {
                AppLogger.error(TAG, "Unknown renderer: " + rendererId);
                return false;
            }

            if (!RendererConfig.isRendererCompatible(context, rendererId)) {
                AppLogger.error(TAG, "Renderer is not compatible with this device");
                return false;
            }

            Map<String, String> envMap = RendererConfig.getRendererEnv(context, rendererId);
            for (Map.Entry<String, String> entry : envMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                try {
                    if (value != null) {
                        Os.setenv(key, value, true);
                    } else {
                        Os.unsetenv(key);
                    }
                } catch (ErrnoException e) {
                    AppLogger.error(TAG, "Failed to set " + key + ": " + e.getMessage());
                }
            }

            // 对于需要 preload 的渲染器，提前加载库文件并设置库路径
            if (renderer.needsPreload && renderer.eglLibrary != null) {
                try {
                    // 获取 EGL 库的完整路径
                    // 通过 FNA3D_OPENGL_LIBRARY 环境变量指定库路径
                    String eglLibPath = RendererConfig.getRendererLibraryPath(context, renderer.eglLibrary);
                    Os.setenv("FNA3D_OPENGL_LIBRARY", eglLibPath, true);
                    if (RendererConfig.RENDERER_ZINK.equals(rendererId)) {
                        Os.setenv("SDL_VIDEO_GL_DRIVER", eglLibPath, true);
                    }

                } catch (UnsatisfiedLinkError e) {
                    AppLogger.error(TAG, "Failed to preload renderer library: " + e.getMessage());
                } catch (ErrnoException e) {
                    AppLogger.error(TAG, "Failed to set FNA3D_OPENGL_LIBRARY: " + e.getMessage());
                }
            }

            // 设置 RALCORE_NATIVEDIR 环境变量（Turnip 加载需要）
            try {
                String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
                Os.setenv("RALCORE_NATIVEDIR", nativeLibDir, true);
            } catch (ErrnoException e) {
                AppLogger.error(TAG, "Failed to set RALCORE_NATIVEDIR: " + e.getMessage());
            }
            
            // 加载 Turnip Vulkan 驱动（如果启用且是 Adreno GPU）
            loadTurnipDriverIfNeeded(context);
            
            // 对于 zink 渲染器，先加载 Vulkan（必须在 OSMesa 初始化之前）
            if (RendererConfig.RENDERER_ZINK.equals(rendererId)) {
                try {
                    OSMRenderer.nativeLoadVulkan();
                } catch (Exception e) {
                    AppLogger.error(TAG, "Failed to initialize zink renderer: " + e.getMessage());
                }
            }

            return true;

        } catch (Exception e) {
            AppLogger.error(TAG, "Renderer loading failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取当前渲染器名称（从环境变量）
     */
    public static String getCurrentRenderer() {
        String ralcoreRenderer = Os.getenv("RALCORE_RENDERER");
        String ralcoreEgl = Os.getenv("RALCORE_EGL");

        if (ralcoreRenderer != null && !ralcoreRenderer.isEmpty()) {
            return ralcoreRenderer;
        } else if (ralcoreEgl != null && ralcoreEgl.contains("angle")) {
            return "angle";
        } else {
            return "native";
        }
    }

    /**
     * 清除渲染器环境变量
     */
    public static void clearRendererEnv() {
        try {
            Os.unsetenv("RALCORE_RENDERER");
            Os.unsetenv("RALCORE_EGL");
            Os.unsetenv("LIBGL_GLES");
            Os.unsetenv("LIBGL_ES");
            Os.unsetenv("LIBGL_MIPMAP");
            Os.unsetenv("LIBGL_NORMALIZE");
            Os.unsetenv("LIBGL_NOINTOVLHACK");
            Os.unsetenv("LIBGL_NOERROR");
            Os.unsetenv("GALLIUM_DRIVER");
            Os.unsetenv("MESA_LOADER_DRIVER_OVERRIDE");
            Os.unsetenv("MESA_GL_VERSION_OVERRIDE");
            Os.unsetenv("MESA_GLSL_VERSION_OVERRIDE");
        } catch (ErrnoException e) {
            AppLogger.error(TAG, "Failed to clear env: " + e.getMessage());
        }
    }
    
    /**
     * 加载 Turnip Vulkan 驱动（如果启用）
     */
    private static void loadTurnipDriverIfNeeded(Context context) {
        try {
            // 检查是否为 Adreno GPU
            com.app.ralaunch.utils.GLInfoUtils.GLInfo glInfo = 
                com.app.ralaunch.utils.GLInfoUtils.getGlInfo();
            if (!glInfo.isAdreno()) {
                return;
            }
            
            // 检查设置
            com.app.ralaunch.data.SettingsManager settingsManager = 
                com.app.ralaunch.data.SettingsManager.getInstance(context);
            boolean useTurnip = settingsManager.isVulkanDriverTurnip();
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to check Turnip driver settings: " + e.getMessage());
        }
    }
}
