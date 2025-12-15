package com.app.ralaunch.renderer;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.app.ralaunch.utils.AppLogger;
import org.libsdl.app.SDLSurface;

/**
 * OSMesa-aware SDL Surface
 * 
 * Extends SDLSurface to initialize OSMesa renderer when Surface is created
 */
public class OSMSurface extends SDLSurface {
    private static final String TAG = "OSMSurface";

    public OSMSurface(Context context) {
        super(context);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
        
        // Initialize OSMesa if zink renderer is selected
        initOSMRendererIfNeeded();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        super.surfaceChanged(holder, format, width, height);
        
        // Update OSMesa window when surface changes
        updateOSMRendererWindow();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // OSMesa cleanup is handled in GameActivity.onDestroy()
        super.surfaceDestroyed(holder);
    }

    /**
     * Initialize OSMesa renderer if zink is selected
     */
    private void initOSMRendererIfNeeded() {
        try {
            String currentRenderer = RendererLoader.getCurrentRenderer();
            boolean isZink = RendererConfig.RENDERER_ZINK.equals(currentRenderer) || 
                            "vulkan_zink".equals(currentRenderer);
            
            if (isZink) {
                if (!OSMRenderer.isAvailable()) {
                    return;
                }

                if (OSMRenderer.isInitialized()) {
                    return;
                }

                Surface surface = getNativeSurface();
                if (surface == null) {
                    return;
                }

                if (!OSMRenderer.init(surface)) {
                    AppLogger.error(TAG, "Failed to initialize OSMesa renderer");
                }
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to initialize OSMesa renderer: " + e.getMessage(), e);
        }
    }

    /**
     * Update OSMesa window when surface changes
     */
    private void updateOSMRendererWindow() {
        try {
            if (OSMRenderer.isInitialized()) {
                Surface surface = getNativeSurface();
                if (surface != null) {
                    OSMRenderer.setWindow(surface);
                }
            }
        } catch (Exception e) {
        }
    }
}

