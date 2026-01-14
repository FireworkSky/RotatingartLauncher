package com.app.ralaunch.settings;

import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

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

        setupControlOpacitySettings();
        setupVibrationSettings();
        setupVirtualControllerVibrationSettings();
    }

    private void setupControlOpacitySettings() {
        Slider slider = rootView.findViewById(R.id.sliderControlOpacity);
        TextView tvOpacityValue = rootView.findViewById(R.id.tvControlOpacityValue);

        if (slider != null && tvOpacityValue != null) {
            float currentOpacity = settingsManager.getControlsOpacity();
            slider.setValue(currentOpacity * 100);
            updateOpacityDisplay(tvOpacityValue, currentOpacity);

            slider.addOnChangeListener((s, value, fromUser) -> {
                float opacity = value / 100f;
                updateOpacityDisplay(tvOpacityValue, opacity);

                if (fromUser) {
                    settingsManager.setControlsOpacity(opacity);
                }
            });
        }
    }

    private void updateOpacityDisplay(TextView textView, float opacity) {
        int percentage = Math.round(opacity * 100);
        textView.setText(percentage + "%");
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

    private void setupVirtualControllerVibrationSettings() {
        MaterialSwitch switchVirtualControllerVibration = rootView.findViewById(R.id.switchVirtualControllerVibration);
        MaterialCardView intensityCard = rootView.findViewById(R.id.virtualControllerVibrationIntensityCard);
        Slider slider = rootView.findViewById(R.id.sliderVirtualControllerVibrationIntensity);
        TextView tvIntensityValue = rootView.findViewById(R.id.tvVirtualControllerVibrationIntensityValue);

        // Setup switch
        if (switchVirtualControllerVibration != null) {
            boolean vibrationEnabled = settingsManager.isVirtualControllerVibrationEnabled();
            switchVirtualControllerVibration.setChecked(vibrationEnabled);
            switchVirtualControllerVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setVirtualControllerVibrationEnabled(isChecked);
                updateIntensityCardVisibility(intensityCard, isChecked);
            });

            // Update initial visibility
            updateIntensityCardVisibility(intensityCard, vibrationEnabled);
        }

        // Setup intensity slider
        if (slider != null && tvIntensityValue != null) {
            float currentIntensity = settingsManager.getVirtualControllerVibrationIntensity();
            slider.setValue(currentIntensity * 100);
            updateIntensityDisplay(tvIntensityValue, currentIntensity);

            slider.addOnChangeListener((s, value, fromUser) -> {
                float intensity = value / 100f;
                updateIntensityDisplay(tvIntensityValue, intensity);

                if (fromUser) {
                    settingsManager.setVirtualControllerVibrationIntensity(intensity);
                }
            });
        }
    }

    private void updateIntensityCardVisibility(MaterialCardView card, boolean isEnabled) {
        if (card != null) {
            card.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void updateIntensityDisplay(TextView textView, float intensity) {
        int percentage = Math.round(intensity * 100);
        textView.setText(percentage + "%");
    }
}


