package com.app.ralaunch.settings;

import android.view.View;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 开发者设置模块
 */
public class DeveloperSettingsModule implements SettingsModule {
    
    private Fragment fragment;
    private View rootView;
    private SettingsManager settingsManager;
    
    @Override
    public void setup(Fragment fragment, View rootView) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.settingsManager = SettingsManager.getInstance(fragment.requireContext());
        
        setupVerboseLogging();
        setupServerGC();
        setupConcurrentGC();
        setupTieredCompilation();
    }
    
    private void setupVerboseLogging() {
        MaterialSwitch switchVerboseLogging = rootView.findViewById(R.id.switchVerboseLogging);
        if (switchVerboseLogging != null) {
            switchVerboseLogging.setChecked(settingsManager.isVerboseLogging());
            switchVerboseLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setVerboseLogging(isChecked);
                String message = isChecked ?
                        fragment.getString(R.string.verbose_logging_enabled) :
                        fragment.getString(R.string.verbose_logging_disabled);
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void setupServerGC() {
        MaterialSwitch switchServerGC = rootView.findViewById(R.id.switchServerGC);
        if (switchServerGC != null) {
            switchServerGC.setChecked(settingsManager.isServerGC());
            switchServerGC.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setServerGC(isChecked);
                Toast.makeText(fragment.requireContext(), R.string.coreclr_settings_restart, Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void setupConcurrentGC() {
        MaterialSwitch switchConcurrentGC = rootView.findViewById(R.id.switchConcurrentGC);
        if (switchConcurrentGC != null) {
            switchConcurrentGC.setChecked(settingsManager.isConcurrentGC());
            switchConcurrentGC.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setConcurrentGC(isChecked);
                Toast.makeText(fragment.requireContext(), R.string.coreclr_settings_restart, Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void setupTieredCompilation() {
        MaterialSwitch switchTieredCompilation = rootView.findViewById(R.id.switchTieredCompilation);
        if (switchTieredCompilation != null) {
            switchTieredCompilation.setChecked(settingsManager.isTieredCompilation());
            switchTieredCompilation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setTieredCompilation(isChecked);
                Toast.makeText(fragment.requireContext(), R.string.coreclr_settings_restart, Toast.LENGTH_SHORT).show();
            });
        }
    }
}


