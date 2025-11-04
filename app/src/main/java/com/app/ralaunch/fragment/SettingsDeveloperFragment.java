package com.app.ralaunch.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;
import com.app.ralaunch.activity.DebugActivity;
import com.app.ralaunch.utils.RuntimePreference;

import java.util.Locale;

/**
 * 设置Fragment
 *
 * 提供应用设置功能：
 * - 主题切换（浅色/深色/跟随系统）
 * - 语言切换（中文/英文）
 * - 设置持久化保存
 * - 实时应用设置更改
 *
 * 设置保存在 SharedPreferences 中
 */
public class SettingsDeveloperFragment extends Fragment {

    // 界面控件
    private SwitchCompat switchVerboseLogging;
    private SwitchCompat switchPerformanceMonitor;
    private Button debugButton;

    // 设置键值
    private static final String PREFS_NAME = "AppSettings";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_developer, container, false);
        setupUI(view);
        loadSettings();
        return view;
    }

    private void setupUI(View view) {

        // 初始化控件
        switchVerboseLogging = view.findViewById(R.id.switchVerboseLogging);
        switchPerformanceMonitor = view.findViewById(R.id.switchPerformanceMonitor);
        View verboseLoggingContainer = view.findViewById(R.id.verboseLoggingContainer);
        View performanceMonitorContainer = view.findViewById(R.id.performanceMonitorContainer);
        debugButton = view.findViewById(R.id.debugButton);

        // 详细日志开关监听
        switchVerboseLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RuntimePreference.setVerboseLogging(requireContext(), isChecked);
            String message = isChecked ?
                    "已启用详细日志，重启应用后生效" :
                    "已禁用详细日志";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // 点击整个容器时切换开关
        verboseLoggingContainer.setOnClickListener(v -> {
            switchVerboseLogging.setChecked(!switchVerboseLogging.isChecked());
        });

        // 性能监控开关监听
        switchPerformanceMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RuntimePreference.setPerformanceMonitorEnabled(requireContext(), isChecked);
            String message = isChecked ?
                    "已启用性能监控，进入游戏后生效" :
                    "已禁用性能监控";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // 点击整个容器时切换开关
        if (performanceMonitorContainer != null) {
            performanceMonitorContainer.setOnClickListener(v -> {
                switchPerformanceMonitor.setChecked(!switchPerformanceMonitor.isChecked());
            });
        }

        // 调试按钮监听
        debugButton.setOnClickListener(v -> {
            // 启动调试活动
            Intent intent = new Intent(getActivity(), DebugActivity.class);
            startActivity(intent);
        });
    }

    private void loadSettings() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 加载详细日志设置
        boolean verboseLogging = RuntimePreference.isVerboseLogging(requireContext());
        switchVerboseLogging.setChecked(verboseLogging);

        // 加载性能监控设置
        boolean performanceMonitor = RuntimePreference.isPerformanceMonitorEnabled(requireContext());
        switchPerformanceMonitor.setChecked(performanceMonitor);
    }
}