package com.app.ralaunch.manager;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.app.ralaunch.R;
import com.app.ralaunch.utils.RuntimeManager;
import com.app.ralaunch.manager.common.AnimationHelper;
import java.util.List;

/**
 * 运行时选择器管理器
 * 负责管理 .NET 运行时版本选择
 */
public class RuntimeSelectorManager {
    private final Context context;
    private View runtimeSelectContainer;
    private View btnRuntimeSelector;
    private TextView tvCurrentRuntime;
    private OnVersionChangedListener onVersionChangedListener;
    
    public interface OnVersionChangedListener {
        void onVersionChanged(String version);
    }
    
    public RuntimeSelectorManager(Context context) {
        this.context = context;
    }
    
    /**
     * 初始化运行时选择器
     */
    public void initialize(View container, View button, TextView textView) {
        this.runtimeSelectContainer = container;
        this.btnRuntimeSelector = button;
        this.tvCurrentRuntime = textView;
        
        setupRuntimeSelector();
    }
    
    /**
     * 设置运行时版本选择器
     */
    private void setupRuntimeSelector() {
        if (btnRuntimeSelector == null || runtimeSelectContainer == null || tvCurrentRuntime == null) {
            return;
        }
        
        List<String> versions = RuntimeManager.listInstalledVersions(context);
        
        if (versions.isEmpty()) {
            runtimeSelectContainer.setVisibility(View.GONE);
            return;
        }
        
        runtimeSelectContainer.setVisibility(View.VISIBLE);
        
        // 显示当前版本
        String selectedVersion = RuntimeManager.getSelectedVersion(context);
        if (selectedVersion != null) {
            tvCurrentRuntime.setText(".NET " + selectedVersion);
        }
        
        // 设置点击事件
        btnRuntimeSelector.setOnClickListener(v -> {
            AnimationHelper.animateButtonPulse(v, () -> showRuntimeSelectorDialog());
        });
    }
    
    /**
     * 显示运行时选择对话框
     */
    private void showRuntimeSelectorDialog() {
        List<String> versions = RuntimeManager.listInstalledVersions(context);
        String currentVersion = RuntimeManager.getSelectedVersion(context);
        
        // 构建选项列表
        List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new java.util.ArrayList<>();
        for (String version : versions) {
            String description = getVersionDescription(version);
            options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
                version,
                context.getString(R.string.runtime_dotnet_version, version),
                description
            ));
        }
        
        // 创建并配置对话框
        com.app.ralib.dialog.OptionSelectorDialog dialog = new com.app.ralib.dialog.OptionSelectorDialog()
            .setTitle(context.getString(R.string.runtime_selector_title))
            .setIcon(R.drawable.ic_settings)
            .setOptions(options)
            .setCurrentValue(currentVersion)
            .setAutoCloseOnSelect(true)
            .setAutoCloseDelay(300)
            .setOnOptionSelectedListener(version -> {
                // 保存选择
                RuntimeManager.setSelectedVersion(context, version);
                
                // 更新显示的版本
                tvCurrentRuntime.setText(".NET " + version);
                
                // 通知监听器
                if (onVersionChangedListener != null) {
                    onVersionChangedListener.onVersionChanged(version);
                }
                
                // 添加更新动画
                AnimationHelper.animateScaleUpdate(tvCurrentRuntime);
            });

    }
    
    /**
     * 显示运行时选择对话框（需要 FragmentManager）
     */
    public void showRuntimeSelectorDialog(androidx.fragment.app.FragmentManager fragmentManager) {
        List<String> versions = RuntimeManager.listInstalledVersions(context);
        String currentVersion = RuntimeManager.getSelectedVersion(context);
        
        // 构建选项列表
        List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new java.util.ArrayList<>();
        for (String version : versions) {
            String description = getVersionDescription(version);
            options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
                version,
                context.getString(R.string.runtime_dotnet_version, version),
                description
            ));
        }
        
        // 创建并配置对话框
        com.app.ralib.dialog.OptionSelectorDialog dialog = new com.app.ralib.dialog.OptionSelectorDialog()
            .setTitle(context.getString(R.string.runtime_selector_title))
            .setIcon(R.drawable.ic_settings)
            .setOptions(options)
            .setCurrentValue(currentVersion)
            .setAutoCloseOnSelect(true)
            .setAutoCloseDelay(300)
            .setOnOptionSelectedListener(version -> {
                // 保存选择
                RuntimeManager.setSelectedVersion(context, version);
                
                // 更新显示的版本
                tvCurrentRuntime.setText(".NET " + version);
                
                // 通知监听器
                if (onVersionChangedListener != null) {
                    onVersionChangedListener.onVersionChanged(version);
                }
                
                // 添加更新动画
                AnimationHelper.animateScaleUpdate(tvCurrentRuntime);
            });
        
        dialog.show(fragmentManager, "RuntimeSelectorDialog");
    }
    
    /**
     * 获取运行时版本的描述
     */
    private String getVersionDescription(String version) {
        if (version.startsWith("10.")) {
            return context.getString(R.string.runtime_version_latest_recommended);
        } else if (version.startsWith("9.")) {
            return context.getString(R.string.runtime_version_stable_recommended);
        } else if (version.startsWith("8.")) {
            return context.getString(R.string.runtime_version_lts_desc);
        } else if (version.startsWith("7.")) {
            return context.getString(R.string.runtime_version_lts_desc);
        }
        return context.getString(R.string.runtime_version_stable);
    }
    
    /**
     * 设置版本改变监听器
     */
    public void setOnVersionChangedListener(OnVersionChangedListener listener) {
        this.onVersionChangedListener = listener;
    }
}

