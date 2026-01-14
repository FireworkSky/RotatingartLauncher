package com.app.ralaunch.manager;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;

import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.data.SettingsManager;

public class VibrationManager {
    public static final String TAG = "VibrationManager";

    @Nullable
    public Vibrator vibrator;

    public VibrationManager(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void vibrateClick() {
        if (vibrator != null && vibrator.hasVibrator() && SettingsManager.getInstance().getVibrationEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            }
            else {
                vibrator.vibrate(50);
            }
        }
    }

    public void vibrateHeavyClick() {
        if (vibrator != null && vibrator.hasVibrator() && SettingsManager.getInstance().getVibrationEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            }
            else {
                vibrator.vibrate(100);
            }
        }
    }

    public void vibrateTick() {
        if (vibrator != null && vibrator.hasVibrator() && SettingsManager.getInstance().getVibrationEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            }
            else {
                vibrator.vibrate(20);
            }
        }
    }

    public void vibrateOneShot(long milliseconds, int amplitude) {
        if (vibrator != null && vibrator.hasVibrator() && SettingsManager.getInstance().getVibrationEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, amplitude));
            }
            else {
                vibrator.vibrate(milliseconds);
            }
        }
    }
}
