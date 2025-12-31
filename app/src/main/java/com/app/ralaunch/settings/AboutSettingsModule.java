package com.app.ralaunch.settings;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.sponsor.SponsorsActivity;

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
        // 设置赞助商墙按钮
        com.google.android.material.button.MaterialButton btnSponsors = 
            rootView.findViewById(com.app.ralaunch.R.id.btnSponsors);
        if (btnSponsors != null) {
            btnSponsors.setOnClickListener(v -> {
                openSponsorsPage();
            });
        }
        
        // 设置赞助商墙卡片点击
        View cardSponsors = rootView.findViewById(com.app.ralaunch.R.id.cardSponsors);
        if (cardSponsors != null) {
            cardSponsors.setOnClickListener(v -> {
                openSponsorsPage();
            });
        }
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
        
        // 设置 Future 版本贡献者 GitHub 按钮
        com.google.android.material.button.MaterialButton btnFutureContributorGithub = 
            rootView.findViewById(com.app.ralaunch.R.id.btnFutureContributorGithub);
        if (btnFutureContributorGithub != null) {
            btnFutureContributorGithub.setOnClickListener(v -> {
                // 打开 future 分支页面
                openUrl("https://github.com/FireworkSky/RotatingartLauncher/tree/future");
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
    
    private void openSponsorsPage() {
        try {
            Intent intent = new Intent(fragment.requireContext(), SponsorsActivity.class);
            fragment.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                fragment.requireContext(), 
                "无法打开赞助商页面", 
                Toast.LENGTH_SHORT
            ).show();
        }
    }
}




