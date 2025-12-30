package com.app.ralaunch.settings;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.utils.LogcatReader;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 开发者设置模块
 */
public class DeveloperSettingsModule implements SettingsModule {
    
    private static final long MAX_LOG_FILE_SIZE = 2 * 1024 * 1024; // 2MB

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
        setupExportShareLog();
        setupThreadAffinityToBigCore();
        setupKillLauncherUI();
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

    // Urhhh, ik this method is huge... but who cares?
    private void setupExportShareLog() {
        MaterialButton btnExportShareLog = rootView.findViewById(R.id.btnExportShareLog);
        if (btnExportShareLog != null) {
            btnExportShareLog.setOnClickListener(v -> {
                // 检查日志系统是否已启用
                if (!settingsManager.isLogSystemEnabled()) {
                    // 显示警告对话框
                    new MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle(R.string.log_system_disabled_title)
                        .setMessage(R.string.log_system_disabled_message)
                        .setPositiveButton(R.string.confirm, null)
                        .show();
                    return;
                }

                // 显示进度提示
                Toast.makeText(fragment.requireContext(), R.string.exporting_log, Toast.LENGTH_SHORT).show();

                // 在后台线程执行日志导出
                new Thread(() -> {
                    try {
                        LogcatReader logcatReader = LogcatReader.getInstance();
                        File originalLogFile = logcatReader.getLogFile();

                        if (originalLogFile == null || !originalLogFile.exists()) {
                            throw new Exception("Log file not found");
                        }

                        // 检查文件大小
                        long fileSize = originalLogFile.length();
                        File fileToShare = originalLogFile;

                        // 如果文件大于 2MB，tail 到 2MB
                        if (fileSize > MAX_LOG_FILE_SIZE) {
                            File cacheDir = fragment.requireContext().getExternalCacheDir();
                            if (cacheDir == null) {
                                cacheDir = fragment.requireContext().getCacheDir();
                            }

                            // 使用原始文件名加 "tailed" 后缀
                            String originalName = originalLogFile.getName();
                            String tailedFileName;
                            int dotIndex = originalName.lastIndexOf('.');
                            if (dotIndex > 0) {
                                // 有扩展名: "file.log" -> "file_tailed.log"
                                tailedFileName = originalName.substring(0, dotIndex) + "_tailed" + originalName.substring(dotIndex);
                            } else {
                                // 无扩展名: "file" -> "file_tailed"
                                tailedFileName = originalName + "_tailed";
                            }

                            File tailedLogFile = new File(cacheDir, tailedFileName);
                            tailLogFile(originalLogFile, tailedLogFile, MAX_LOG_FILE_SIZE);
                            fileToShare = tailedLogFile;
                        }

                        // 使用 FileProvider 获取 URI
                        final File finalFileToShare = fileToShare;
                        Uri logUri = FileProvider.getUriForFile(
                            fragment.requireContext(),
                            fragment.requireContext().getPackageName() + ".fileprovider",
                            finalFileToShare
                        );

                        // 在主线程创建并启动分享 Intent
                        fragment.requireActivity().runOnUiThread(() -> {
                            try {
                                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                shareIntent.setType("text/plain");
                                shareIntent.putExtra(Intent.EXTRA_STREAM, logUri);
                                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "RaLaunch Log File");
                                shareIntent.putExtra(Intent.EXTRA_TEXT, "RaLaunch log file: " + finalFileToShare.getName());
                                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                Intent chooser = Intent.createChooser(shareIntent, fragment.getString(R.string.export_share_log));
                                fragment.startActivity(chooser);

                                Toast.makeText(fragment.requireContext(),
                                    R.string.log_exported,
                                    Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(fragment.requireContext(),
                                    fragment.getString(R.string.log_export_failed, e.getMessage()),
                                    Toast.LENGTH_LONG).show();
                            }
                        });

                    } catch (Exception e) {
                        // 在主线程显示错误消息
                        fragment.requireActivity().runOnUiThread(() ->
                            Toast.makeText(fragment.requireContext(),
                                fragment.getString(R.string.log_export_failed, e.getMessage()),
                                Toast.LENGTH_LONG).show()
                        );
                    }
                }, "ExportShareLog").start();
            });
        }
    }

    /**
     * Tail a log file to a specified size
     *
     * @param sourceFile The source log file
     * @param destFile The destination file for the tailed log
     * @param maxSize Maximum size in bytes
     * @throws IOException if an I/O error occurs
     */
    private void tailLogFile(File sourceFile, File destFile, long maxSize) throws IOException {
        long fileSize = sourceFile.length();

        // Calculate the starting position (from the end)
        long startPosition = fileSize - maxSize;
        if (startPosition < 0) {
            startPosition = 0;
        }

        try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r");
             FileOutputStream fos = new FileOutputStream(destFile)) {

            // Seek to the calculated position
            raf.seek(startPosition);

            // If we're not at the beginning, skip to the next line break to avoid partial lines
            if (startPosition > 0) {
                raf.readLine(); // Skip the potentially partial first line
            }

            // Read and write the rest
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = raf.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
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

    private void setupKillLauncherUI() {
        MaterialSwitch switchKillLauncherUI = rootView.findViewById(R.id.switchKillLauncherUI);
        if (switchKillLauncherUI != null) {
            switchKillLauncherUI.setChecked(settingsManager.isKillLauncherUIAfterLaunch());
            switchKillLauncherUI.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsManager.setKillLauncherUIAfterLaunch(isChecked);
                String message = isChecked ?
                        fragment.getString(R.string.kill_launcher_ui_enabled) :
                        fragment.getString(R.string.kill_launcher_ui_disabled);
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





