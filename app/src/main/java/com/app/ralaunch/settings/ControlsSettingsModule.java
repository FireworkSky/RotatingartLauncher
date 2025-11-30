package com.app.ralaunch.settings;

import android.view.View;
import android.widget.RadioGroup;
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
        setupMouseRightStickSettings();
        setupAttackModeSettings();
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
    
    private void setupMultitouchSettings() {
        MaterialSwitch switchMultitouch = rootView.findViewById(R.id.switchMultitouch);
        if (switchMultitouch != null) {
            boolean multitouchEnabled = settingsManager.isTouchMultitouchEnabled();
            switchMultitouch.setChecked(multitouchEnabled);
            switchMultitouch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setTouchMultitouchEnabled(isChecked);
            });
        }
    }
    
    private void setupMouseRightStickSettings() {
        MaterialSwitch switchMouseRightStick = rootView.findViewById(R.id.switchMouseRightStick);
        if (switchMouseRightStick != null) {
            boolean mouseRightStickEnabled = settingsManager.isMouseRightStickEnabled();
            switchMouseRightStick.setChecked(mouseRightStickEnabled);
            switchMouseRightStick.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setMouseRightStickEnabled(isChecked);
                // 更新攻击模式卡片可见性
                updateAttackModeCardVisibility(isChecked);
            });
        }
    }
    
    private void setupAttackModeSettings() {
        RadioGroup rgAttackMode = rootView.findViewById(R.id.rgAttackMode);
        View attackModeCard = rootView.findViewById(R.id.attackModeCard);
        
        if (rgAttackMode != null && attackModeCard != null) {
            // 初始化可见性（根据鼠标模式右摇杆是否启用）
            boolean mouseRightStickEnabled = settingsManager.isMouseRightStickEnabled();
            attackModeCard.setVisibility(mouseRightStickEnabled ? View.VISIBLE : View.GONE);
            
            // 初始化选中状态
            int attackMode = settingsManager.getMouseRightStickAttackMode();
            switch (attackMode) {
                case SettingsManager.ATTACK_MODE_HOLD:
                    rgAttackMode.check(R.id.rbAttackModeHold);
                    break;
                case SettingsManager.ATTACK_MODE_CLICK:
                    rgAttackMode.check(R.id.rbAttackModeClick);
                    break;
                case SettingsManager.ATTACK_MODE_CONTINUOUS:
                    rgAttackMode.check(R.id.rbAttackModeContinuous);
                    break;
            }
            
            // 监听选择变化
            rgAttackMode.setOnCheckedChangeListener((group, checkedId) -> {
                int mode = SettingsManager.ATTACK_MODE_HOLD;
                if (checkedId == R.id.rbAttackModeHold) {
                    mode = SettingsManager.ATTACK_MODE_HOLD;
                } else if (checkedId == R.id.rbAttackModeClick) {
                    mode = SettingsManager.ATTACK_MODE_CLICK;
                } else if (checkedId == R.id.rbAttackModeContinuous) {
                    mode = SettingsManager.ATTACK_MODE_CONTINUOUS;
                }
                settingsManager.setMouseRightStickAttackMode(mode);
            });
        }
    }
    
    private void updateAttackModeCardVisibility(boolean visible) {
        View attackModeCard = rootView.findViewById(R.id.attackModeCard);
        if (attackModeCard != null) {
            attackModeCard.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
