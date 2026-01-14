package com.app.ralaunch.settings;

import android.view.View;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 控制设置模块
 * 多点触控和右摇杆鼠标模式默认开启，相关设置已移至控件编辑器
 */
public class ControlsSettingsModule implements SettingsModule {
    
    private Fragment fragment;
    private View rootView;
    private SettingsManager settingsManager;
    
    @Override
    public void setup(Fragment fragment, View rootView) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.settingsManager = SettingsManager.getInstance();

        setupVibrationSettings();
    }
    
    private void setupVibrationSettings() {
        MaterialSwitch switchVibration = rootView.findViewById(R.id.switchVibration);
        if (switchVibration != null) {
            boolean vibrationEnabled = settingsManager.getVibrationEnabled();
            switchVibration.setChecked(vibrationEnabled);
            switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setVibrationEnabled(isChecked);
            });
        }
    }
}
