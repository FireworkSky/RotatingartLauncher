package com.app.ralaunch.settings;

import android.view.View;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.data.SettingsManager;
import com.google.android.material.button.MaterialButton;
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

        setupEnableLogSystem();
        setupVerboseLogging();
        setupThreadAffinityToBigCore();
        setupServerGC();
        setupConcurrentGC();
        setupTieredCompilation();
        setupFnaMapBufferRangeOptimization();
        setupForceReinstallPatches();
    }
    
    private void setupEnableLogSystem() {
        MaterialSwitch switchEnableLogSystem = rootView.findViewById(R.id.switchEnableLogSystem);
        if (switchEnableLogSystem != null) {
            switchEnableLogSystem.setChecked(settingsManager.isLogSystemEnabled());
            switchEnableLogSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setLogSystemEnabled(isChecked);
                String message = isChecked ?
                        fragment.getString(R.string.enable_log_system_enabled) :
                        fragment.getString(R.string.enable_log_system_disabled);
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
            });
        }
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

    private void setupThreadAffinityToBigCore() {
        MaterialSwitch switchThreadAffinityToBigCore = rootView.findViewById(R.id.switchThreadAffinityToBigCore);
        if (switchThreadAffinityToBigCore != null) {
            switchThreadAffinityToBigCore.setChecked(settingsManager.getSetThreadAffinityToBigCoreEnabled());
            switchThreadAffinityToBigCore.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setSetThreadAffinityToBigCoreEnabled(isChecked);
                String message = isChecked ?
                        fragment.getString(R.string.thread_affinity_big_core_enabled) :
                        fragment.getString(R.string.thread_affinity_big_core_disabled);
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
    
    private void setupFnaMapBufferRangeOptimization() {
        MaterialSwitch switchFnaMapBufferRangeOpt = rootView.findViewById(R.id.switchFnaMapBufferRangeOpt);
        if (switchFnaMapBufferRangeOpt != null) {
            switchFnaMapBufferRangeOpt.setChecked(settingsManager.isFnaEnableMapBufferRangeOptimization());
            switchFnaMapBufferRangeOpt.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setFnaEnableMapBufferRangeOptimization(isChecked);
                String message = isChecked ?
                        fragment.getString(R.string.fna_map_buffer_range_opt_enabled) :
                        fragment.getString(R.string.fna_map_buffer_range_opt_disabled);
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupForceReinstallPatches() {
        MaterialButton btnForceReinstallPatches = rootView.findViewById(R.id.btnForceReinstallPatches);
        if (btnForceReinstallPatches != null) {
            btnForceReinstallPatches.setOnClickListener(v -> {
                // 显示进度提示
                Toast.makeText(fragment.requireContext(), R.string.reinstalling_patches, Toast.LENGTH_SHORT).show();

                // 在后台线程执行补丁重装
                new Thread(() -> {
                    try {
                        com.app.ralib.patch.PatchManager patchManager = RaLaunchApplication.getPatchManager();

                        if (patchManager != null) {
                            // 强制重新安装内置补丁
                            com.app.ralib.patch.PatchManager.installBuiltInPatches(patchManager, true);

                            // 在主线程显示成功消息
                            fragment.requireActivity().runOnUiThread(() ->
                                Toast.makeText(fragment.requireContext(),
                                    R.string.patches_reinstalled,
                                    Toast.LENGTH_LONG).show()
                            );
                        }
                        else {
                            throw new Exception("PatchManager is not initialized");
                        }
                    } catch (Exception e) {
                        // 在主线程显示错误消息
                        fragment.requireActivity().runOnUiThread(() ->
                            Toast.makeText(fragment.requireContext(),
                                fragment.getString(R.string.patches_reinstall_failed, e.getMessage()),
                                Toast.LENGTH_LONG).show()
                        );
                    }
                }, "ForceReinstallPatches").start();
            });
        }
    }

}





