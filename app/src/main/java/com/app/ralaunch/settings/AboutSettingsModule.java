package com.app.ralaunch.settings;

import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import androidx.fragment.app.Fragment;

/**
 * 关于设置模块
 */
public class AboutSettingsModule implements SettingsModule {
    
    private Fragment fragment;
    private View rootView;

    @Override
    public void setup(Fragment fragment, View rootView) {
        this.fragment = fragment;
        this.rootView = rootView;
        
        setupButtons();
    }
    
    private void setupButtons() {
        // 设置爱发电按钮
        com.google.android.material.button.MaterialButton btnAfdian = 
            rootView.findViewById(com.app.ralaunch.R.id.btnAfdian);
        if (btnAfdian != null) {
            btnAfdian.setOnClickListener(v -> {
                openUrl("https://afdian.com/a/RotatingartLauncher");
            });
        }
        
        // 设置 Patreon 按钮
        com.google.android.material.button.MaterialButton btnPatreon = 
            rootView.findViewById(com.app.ralaunch.R.id.btnPatreon);
        if (btnPatreon != null) {
            btnPatreon.setOnClickListener(v -> {
                openUrl("https://www.patreon.com/c/RotatingArtLauncher");
            });
        }
        
        // 设置 Discord 按钮
        com.google.android.material.button.MaterialButton btnDiscord = 
            rootView.findViewById(com.app.ralaunch.R.id.btnDiscord);
        if (btnDiscord != null) {
            btnDiscord.setOnClickListener(v -> {
                openUrl("https://discord.gg/cVkrRdffGp");
            });
        }
        
        // 设置 QQ 群按钮
        com.google.android.material.button.MaterialButton btnQQGroup = 
            rootView.findViewById(com.app.ralaunch.R.id.btnQQGroup);
        if (btnQQGroup != null) {
            btnQQGroup.setOnClickListener(v -> {
                // QQ 群一键加群链接
                openUrl("https://qm.qq.com/q/BWiPSj6wWQ");
            });
        }
        
        // 设置作者 GitHub 按钮
        com.google.android.material.button.MaterialButton btnAuthorGithub = 
            rootView.findViewById(com.app.ralaunch.R.id.btnAuthorGithub);
        if (btnAuthorGithub != null) {
            btnAuthorGithub.setOnClickListener(v -> {
                openGitHubProfile("FireworkSky");
            });
        }
        
        // 设置开发者 GitHub 按钮
        com.google.android.material.button.MaterialButton btnContributorGithub = 
            rootView.findViewById(com.app.ralaunch.R.id.btnContributorGithub);
        if (btnContributorGithub != null) {
            btnContributorGithub.setOnClickListener(v -> {
                openGitHubProfile("LaoSparrow");
            });
        }
    }
    
    private void openGitHubProfile(String username) {
        openUrl("https://github.com/" + username);
    }
    
    private void openUrl(String url) {
        try {
            android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_VIEW, 
                Uri.parse(url)
            );
            fragment.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                fragment.requireContext(), 
                "无法打开浏览器", 
                Toast.LENGTH_SHORT
            ).show();
        }
    }
}

