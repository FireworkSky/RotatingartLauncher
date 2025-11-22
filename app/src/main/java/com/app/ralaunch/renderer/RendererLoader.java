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
 *
 * 参考 FoldCraftLauncher 的实现方式
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
        AppLogger.info(TAG, "================================================");
        AppLogger.info(TAG, "  Renderer Loader (Environment Variable Mode)");
        AppLogger.info(TAG, "  Renderer ID: " + rendererId);
        AppLogger.info(TAG, "================================================");

        try {
            // 获取渲染器信息
            RendererConfig.RendererInfo renderer = RendererConfig.getRendererById(rendererId);
            if (renderer == null) {
                AppLogger.error(TAG, "Unknown renderer: " + rendererId);
                return false;
            }

            AppLogger.info(TAG, "Renderer: " + renderer.displayName);
            AppLogger.info(TAG, "Description: " + renderer.description);

            // 检查兼容性
            if (!RendererConfig.isRendererCompatible(context, rendererId)) {
                AppLogger.error(TAG, "Renderer is not compatible with this device");
                return false;
            }

            // 获取并设置环境变量
            Map<String, String> envMap = RendererConfig.getRendererEnv(context, rendererId);

            if (envMap.isEmpty()) {
                AppLogger.info(TAG, "Using native renderer (no environment variables needed)");
            } else {
                AppLogger.info(TAG, "Setting environment variables:");
                for (Map.Entry<String, String> entry : envMap.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    try {
                        Os.setenv(key, value, true);
                        AppLogger.info(TAG, "  " + key + " = " + value);
                    } catch (ErrnoException e) {
                        AppLogger.error(TAG, "Failed to set " + key + ": " + e.getMessage());
                    }
                }
            }

            // 对于需要 preload 的渲染器，提前加载库文件并设置库路径
            if (renderer.needsPreload && renderer.eglLibrary != null) {
                try {
                    // 获取 EGL 库的完整路径
                    // 参考 PojavLauncher 的方法,通过 FNA3D_OPENGL_LIBRARY 环境变量指定库路径
                    String eglLibPath = RendererConfig.getRendererLibraryPath(context, renderer.eglLibrary);
                    AppLogger.info(TAG, "EGL Library Path: " + eglLibPath);

                    // 设置 FNA3D_OPENGL_LIBRARY 环境变量
                    // SDL 的 Android_GLES_LoadLibrary 会读取这个变量来加载自定义 EGL 库
                    Os.setenv("FNA3D_OPENGL_LIBRARY", eglLibPath, true);
                    AppLogger.info(TAG, "✓ FNA3D_OPENGL_LIBRARY = " + eglLibPath);

                    // NOTE: Java 层不预加载渲染器库，让 C 层的 SDL_Android_LoadEGL 负责加载
                    // 原因：C 层需要使用 RTLD_GLOBAL 标志加载 ANGLE 以使符号全局可见
                    // Java 的 System.loadLibrary 使用 RTLD_LOCAL，导致 dlsym 无法找到 GL 函数
                    AppLogger.info(TAG, "Renderer library will be loaded by native code (SDL_Android_LoadEGL)");

                } catch (UnsatisfiedLinkError e) {
                    AppLogger.error(TAG, "Failed to preload renderer library: " + e.getMessage());
                } catch (ErrnoException e) {
                    AppLogger.error(TAG, "Failed to set FNA3D_OPENGL_LIBRARY: " + e.getMessage());
                }
            }

            AppLogger.info(TAG, "✅ Renderer configuration completed");
            AppLogger.info(TAG, "================================================");
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
            AppLogger.info(TAG, "Renderer environment variables cleared");
        } catch (ErrnoException e) {
            AppLogger.error(TAG, "Failed to clear env: " + e.getMessage());
        }
    }
}
