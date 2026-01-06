package com.app.ralaunch.renderer;

import android.content.Context;
import android.system.Os;
import com.app.ralaunch.core.EnvVarsManager;
import com.app.ralaunch.utils.AppLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
            EnvVarsManager.INSTANCE.quickSetEnvVars(envMap);

            // 对于需要 preload 的渲染器，提前加载库文件并设置库路径
            if (renderer.needsPreload && renderer.eglLibrary != null) {
                try {
                    // 获取 EGL 库的完整路径
                    // 通过 FNA3D_OPENGL_LIBRARY 环境变量指定库路径
                    String eglLibPath = RendererConfig.getRendererLibraryPath(context, renderer.eglLibrary);
                    EnvVarsManager.INSTANCE.quickSetEnvVar("FNA3D_OPENGL_LIBRARY", eglLibPath);
                    if (RendererConfig.RENDERER_ZINK.equals(rendererId)) {
                        EnvVarsManager.INSTANCE.quickSetEnvVar("SDL_VIDEO_GL_DRIVER", eglLibPath);
                    }

                } catch (UnsatisfiedLinkError e) {
                    AppLogger.error(TAG, "Failed to preload renderer library: " + e.getMessage());
                }
            }

            // 设置 RALCORE_NATIVEDIR 环境变量（Turnip 加载需要）
            String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
            EnvVarsManager.INSTANCE.quickSetEnvVar("RALCORE_NATIVEDIR", nativeLibDir);

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
            
            // 对于 DXVK 渲染器，加载 DXVK 和 vkd3d 库
            if (RendererConfig.RENDERER_DXVK.equals(rendererId)) {
                try {
                    // 创建 DXVK 配置文件
                    createDxvkConfig(context);
                    
                    // 使用 TurnipLoader 在命名空间中加载 Turnip + libvulkan.so
                    // 这样 libvulkan.so 会在同一命名空间中找到 Turnip，而不是系统驱动
                    String cacheDir = context.getCacheDir().getAbsolutePath();
                    boolean turnipLoaded = TurnipLoader.loadTurnip(nativeLibDir, cacheDir);
                    if (!turnipLoaded) {
                        AppLogger.warn(TAG, "Failed to load Turnip+Vulkan via nsbypass, DXVK will use system driver");
                    } else {
                        AppLogger.info(TAG, "Turnip+Vulkan loaded successfully, DXVK will use Turnip driver");
                    }
                    
                    // 加载 vkd3d 库（用于着色器编译）
                    System.loadLibrary("vkd3d");
                    System.loadLibrary("vkd3d-shader");
                    System.loadLibrary("vkd3d-utils");
                    AppLogger.info(TAG, "vkd3d libraries loaded successfully");
                    
                    // 加载 DXVK 库（DXGI 必须先于 D3D11 加载）
                    System.loadLibrary("dxvk_dxgi");
                    System.loadLibrary("dxvk_d3d11");
                    AppLogger.info(TAG, "DXVK libraries loaded successfully");
                } catch (UnsatisfiedLinkError e) {
                    AppLogger.error(TAG, "Failed to load DXVK/vkd3d libraries: " + e.getMessage());
                    return false;
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
        EnvVarsManager.INSTANCE.quickSetEnvVar("RALCORE_RENDERER", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("RALCORE_EGL", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("LIBGL_GLES", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("LIBGL_ES", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("LIBGL_MIPMAP", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("LIBGL_NORMALIZE", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("LIBGL_NOINTOVLHACK", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("LIBGL_NOERROR", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("GALLIUM_DRIVER", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("MESA_LOADER_DRIVER_OVERRIDE", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("MESA_GL_VERSION_OVERRIDE", null);
        EnvVarsManager.INSTANCE.quickSetEnvVar("MESA_GLSL_VERSION_OVERRIDE", null);
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

    /**
     * 创建 DXVK 配置文件
     * 这可以避免 DXVK 配置系统初始化问题
     */
    private static void createDxvkConfig(Context context) {
        try {
            // 在应用目录创建 dxvk.conf 文件
            File configDir = context.getFilesDir();
            File dxvkConf = new File(configDir, "dxvk.conf");
            
            // 写入基本配置
            FileWriter writer = new FileWriter(dxvkConf);
            writer.write("# DXVK Configuration for Android\n");
            writer.write("# Auto-generated by RALauncher\n\n");
            writer.write("# Use SDL2 for window system integration\n");
            writer.write("dxvk.wsi = \"SDL2\"\n\n");
            writer.write("# Disable shader cache temporarily for debugging\n");
            writer.write("dxvk.enableStateCache = False\n\n");
            writer.write("# Disable async shader compilation to avoid issues\n");
            writer.write("dxvk.numCompilerThreads = 1\n\n");
            writer.write("# Debug log level\n");
            writer.write("dxvk.logLevel = \"debug\"\n");
            writer.close();
            
            // 设置 DXVK_CONFIG_FILE 环境变量指向配置文件
            EnvVarsManager.INSTANCE.quickSetEnvVar("DXVK_CONFIG_FILE", dxvkConf.getAbsolutePath());
            AppLogger.info(TAG, "DXVK config created at: " + dxvkConf.getAbsolutePath());
            
            // 设置 DXVK 日志路径
            EnvVarsManager.INSTANCE.quickSetEnvVar("DXVK_LOG_PATH", configDir.getAbsolutePath());
            AppLogger.info(TAG, "DXVK log path set to: " + configDir.getAbsolutePath());
            
            // NOTE: VK_ICD_FILENAMES 不再设置，因为我们通过 TurnipLoader 预加载 Turnip
            // 并设置 TURNIP_HANDLE 环境变量供 DXVK 使用
            AppLogger.info(TAG, "DXVK will use pre-loaded Turnip via TURNIP_HANDLE");
            
        } catch (IOException e) {
            AppLogger.error(TAG, "Failed to create DXVK config: " + e.getMessage());
        }
    }
}
