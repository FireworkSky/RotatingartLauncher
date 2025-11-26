package com.app.ralaunch.controls;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

import com.app.ralaunch.R;

/**
 * Xbox 手柄按钮纹理管理器
 * 管理 Xbox 手柄按钮的图标资源
 */
public class XboxButtonTextureManager {

    /**
     * 获取 Xbox 按钮图标资源 ID
     * @param keycode 按键码
     * @return 资源 ID，如果不存在则返回 0
     */
    public static int getXboxButtonIconResId(int keycode) {
        switch (keycode) {
            case ControlData.XBOX_BUTTON_A:
                return R.drawable.ico_my_handle_btn_a;
            case ControlData.XBOX_BUTTON_B:
                return R.drawable.ico_my_handle_btn_b;
            case ControlData.XBOX_BUTTON_X:
                return R.drawable.ico_my_handle_btn_x;
            case ControlData.XBOX_BUTTON_Y:
                return R.drawable.ico_my_handle_btn_y;
            default:
                return 0;
        }
    }

    /**
     * 获取 Xbox 按钮图标 Drawable
     * @param context 上下文
     * @param keycode 按键码
     * @return Drawable，如果不存在则返回 null
     */
    public static Drawable getXboxButtonIcon(Context context, int keycode) {
        int resId = getXboxButtonIconResId(keycode);
        if (resId != 0) {
            return ContextCompat.getDrawable(context, resId);
        }
        return null;
    }

    /**
     * 判断是否为 Xbox 按钮
     * @param keycode 按键码
     * @return 是否为 Xbox 按钮
     */
    public static boolean isXboxButton(int keycode) {
        return keycode >= ControlData.XBOX_BUTTON_A && 
               keycode <= ControlData.XBOX_BUTTON_DPAD_RIGHT;
    }
}

