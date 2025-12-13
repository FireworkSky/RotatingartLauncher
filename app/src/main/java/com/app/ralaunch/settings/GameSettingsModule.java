package com.app.ralaunch.settings;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.dialog.PatchManagementDialog;
import com.app.ralaunch.renderer.RendererConfig;
import com.app.ralaunch.utils.RuntimePreference;
import com.app.ralib.dialog.OptionSelectorDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏设置模块
 */
public class GameSettingsModule implements SettingsModule {
    
    private Fragment fragment;
    private View rootView;
    private SettingsManager settingsManager;
    
    @Override
    public void setup(Fragment fragment, View rootView) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.settingsManager = SettingsManager.getInstance(fragment.requireContext());
        
        setupRendererSettings();
        setupVulkanDriverSettings();
        setupPatchManagement();
    }
    
    private void setupRendererSettings() {
        MaterialCardView rendererCard = rootView.findViewById(R.id.rendererCard);
        TextView tvRendererValue = rootView.findViewById(R.id.tvRendererValue);

        if (rendererCard != null && tvRendererValue != null) {
            updateRendererDisplay(tvRendererValue);

            rendererCard.setOnClickListener(v -> {
                List<RendererConfig.RendererInfo> compatibleRenderers =
                    RendererConfig.getCompatibleRenderers(fragment.requireContext());

                List<OptionSelectorDialog.Option> options = new ArrayList<>();

                // 移除自动选择选项，直接显示所有可用渲染器
                for (RendererConfig.RendererInfo renderer : compatibleRenderers) {
                    String stringId = "renderer_" + renderer.id;
                    String descId = "renderer_" + renderer.id + "_desc";

                    int nameResId = fragment.getResources().getIdentifier(stringId, "string", fragment.requireContext().getPackageName());
                    int descResId = fragment.getResources().getIdentifier(descId, "string", fragment.requireContext().getPackageName());

                    if (nameResId != 0 && descResId != 0) {
                        options.add(new OptionSelectorDialog.Option(
                            renderer.id,
                            fragment.getString(nameResId),
                            fragment.getString(descResId)
                        ));
                    } else {
                        options.add(new OptionSelectorDialog.Option(
                            renderer.id,
                            renderer.displayName,
                            renderer.description
                        ));
                    }
                }

                new OptionSelectorDialog()
                    .setTitle(fragment.getString(R.string.renderer_title))
                    .setIcon(R.drawable.ic_game)
                    .setOptions(options)
                    .setCurrentValue(RuntimePreference.normalizeRendererValue(settingsManager.getFnaRenderer()))
                    .setOnOptionSelectedListener(value -> {
                        settingsManager.setFnaRenderer(RuntimePreference.normalizeRendererValue(value));
                        updateRendererDisplay(tvRendererValue);
                        Toast.makeText(fragment.requireContext(), fragment.getString(R.string.renderer_changed), Toast.LENGTH_SHORT).show();
                    })
                    .show(fragment.getParentFragmentManager(), "renderer");
            });
        }
    }
    
    private void setupVulkanDriverSettings() {
        // 检查是否为 Adreno GPU
        com.app.ralaunch.utils.GLInfoUtils.GLInfo glInfo = com.app.ralaunch.utils.GLInfoUtils.getGlInfo();
        boolean isAdreno = glInfo.isAdreno();
        
        MaterialCardView turnipDriverCard = rootView.findViewById(R.id.turnipDriverCard);
        if (turnipDriverCard != null) {
            // 只在 Adreno GPU 上显示 Turnip 驱动选项
            if (isAdreno) {
                turnipDriverCard.setVisibility(View.VISIBLE);
                
                MaterialSwitch switchTurnip = rootView.findViewById(R.id.switchTurnipDriver);
                if (switchTurnip != null) {
                    boolean turnipEnabled = settingsManager.isVulkanDriverTurnip();
                    switchTurnip.setChecked(turnipEnabled);
                    
                    switchTurnip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        settingsManager.setVulkanDriverTurnip(isChecked);
                        Toast.makeText(fragment.requireContext(), 
                            isChecked ? "已启用 Turnip 驱动" : "已禁用 Turnip 驱动（使用系统驱动）", 
                            Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                turnipDriverCard.setVisibility(View.GONE);
            }
        }
    }
    
    private void setupPatchManagement() {
        MaterialCardView patchManagementCard = rootView.findViewById(R.id.patchManagementCard);
        if (patchManagementCard != null) {
            patchManagementCard.setOnClickListener(v -> {
                new PatchManagementDialog(fragment.requireContext()).show();
            });
        }
    }
    
    private void updateRendererDisplay(TextView textView) {
        String renderer = RuntimePreference.normalizeRendererValue(settingsManager.getFnaRenderer());
        String display;

        // 自动选择
        if (RuntimePreference.RENDERER_AUTO.equals(renderer)) {
            display = fragment.getString(R.string.renderer_auto);
        } else {
            // 尝试从字符串资源获取
            String stringId = "renderer_" + renderer;
            int resId = fragment.getResources().getIdentifier(stringId, "string", fragment.requireContext().getPackageName());

            if (resId != 0) {
                display = fragment.getString(resId);
            } else {
                // 如果找不到字符串资源，使用渲染器 displayName
                RendererConfig.RendererInfo rendererInfo =
                    RendererConfig.getRendererById(renderer);
                if (rendererInfo != null) {
                    display = rendererInfo.displayName;
                } else {
                    // 最后回退：直接显示 ID
                    display = renderer;
                }
            }
        }

        textView.setText(display);
    }
}

