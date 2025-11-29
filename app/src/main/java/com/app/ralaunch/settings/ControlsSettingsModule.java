package com.app.ralaunch.settings;

import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 控制设置模块
 */
public class ControlsSettingsModule implements SettingsModule {
    
    private Fragment fragment;
    private View rootView;
    private SettingsManager settingsManager;
    
    @Override
    public void setup(Fragment fragment, View rootView) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.settingsManager = SettingsManager.getInstance(fragment.requireContext());
        
        setupVibrationSettings();
        setupMultitouchSettings();
    }
    
    private void setupVibrationSettings() {
        MaterialSwitch switchVibration = rootView.findViewById(R.id.switchVibration);
        if (switchVibration != null) {
            boolean vibrationEnabled = settingsManager.getVibrationEnabled();
            switchVibration.setChecked(vibrationEnabled);
            switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // 保存振动反馈设置
                 settingsManager.setVibrationEnabled(isChecked);
            });
        }
    }
    
    private void setupMultitouchSettings() {
        MaterialSwitch switchMultitouch = rootView.findViewById(R.id.switchMultitouch);
        if (switchMultitouch != null) {
            boolean multitouchEnabled = settingsManager.isTouchMultitouchEnabled();
            switchMultitouch.setChecked(multitouchEnabled);
            switchMultitouch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // 保存多点触控设置
                settingsManager.setTouchMultitouchEnabled(isChecked);
            });
        }
    }
}


