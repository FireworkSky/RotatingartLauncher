package com.app.ralaunch.activity;

import org.libsdl.app.SDLSurface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.views.ControlLayout;
import com.app.ralaunch.controls.bridges.SDLInputBridge;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.view.FPSDisplayView;
import com.app.ralaunch.utils.AppLogger;

/**
 * 负责虚拟按键和 FPS 叠层的初始化与显示控制
 */
public class GameVirtualControlsManager {

    private ControlLayout controlLayout;
    private SDLInputBridge inputBridge;
    private FPSDisplayView fpsDisplayView;
    private SettingsManager settingsManager;

    public void initialize(GameActivity activity, ViewGroup sdlLayout, SDLSurface sdlSurface, Runnable disableSDLTextInput) {
        try {
            settingsManager = SettingsManager.getInstance();
            inputBridge = new SDLInputBridge();

            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            SDLInputBridge.setScreenSize(metrics.widthPixels, metrics.heightPixels);

            controlLayout = new ControlLayout(activity);
            controlLayout.setInputBridge(inputBridge);
            controlLayout.loadLayoutFromPackManager();
            disableClippingRecursive(controlLayout);


            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );

            if (sdlLayout != null) {
                sdlLayout.addView(controlLayout, params);

                if (sdlSurface != null) {
                    controlLayout.setSDLSurface(sdlSurface);
                    // 设置虚拟控件管理器到 SDLSurface
                    sdlSurface.setVirtualControlsManager(this);
                }

                // 添加 FPS 显示视图
                fpsDisplayView = new FPSDisplayView(activity);
                fpsDisplayView.setInputBridge(inputBridge);
                sdlLayout.addView(fpsDisplayView, params);
                fpsDisplayView.start();
            }

            if (sdlLayout != null) {
                sdlLayout.postDelayed(disableSDLTextInput, 2000);
            }
        } catch (Exception e) {
            AppLogger.error("GameVirtualControls", "Failed to initialize virtual controls", e);
        }
    }

    public void onActivityResultReload() {
        if (controlLayout != null) {
            controlLayout.loadLayoutFromPackManager();
            disableClippingRecursive(controlLayout);
        }
    }

    public void toggle(GameActivity activity) {
        if (controlLayout != null) {
            boolean visible = !controlLayout.isControlsVisible();
            controlLayout.setControlsVisible(visible);
            Toast.makeText(activity, visible ? R.string.game_menu_controls_on : R.string.game_menu_controls_off,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void setVisible(boolean visible) {
        if (controlLayout != null) {
            controlLayout.setControlsVisible(visible);
        }
    }

    public ControlLayout getControlLayout() {
        return controlLayout;
    }

    public SDLInputBridge getInputBridge() {
        return inputBridge;
    }

    public void stop() {
        if (fpsDisplayView != null) {
            fpsDisplayView.stop();
        }
    }

    private void disableClippingRecursive(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            viewGroup.setClipChildren(false);
            viewGroup.setClipToPadding(false);
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                disableClippingRecursive(viewGroup.getChildAt(i));
            }
        }
        view.setClipToOutline(false);
        view.setClipBounds(null);
    }
}

