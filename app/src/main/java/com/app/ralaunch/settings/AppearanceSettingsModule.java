package com.app.ralaunch.settings;

import android.app.Activity;
import android.net.Uri;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.fragment.BaseFragment;
import com.app.ralaunch.manager.ThemeManager;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.LocaleManager;
import com.app.ralaunch.utils.ThemeColorUpdater;
import com.app.ralib.dialog.ColorPickerDialog;
import com.app.ralib.dialog.OptionSelectorDialog;
import com.app.ralaunch.activity.MainActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.Arrays;
import java.util.List;

/**
 * 外观设置模块
 */
public class AppearanceSettingsModule implements SettingsModule {
    
    private Fragment fragment;
    private View rootView;
    private SettingsManager settingsManager;
    
    // ActivityResultLauncher 需要从 Fragment 获取
    private androidx.activity.result.ActivityResultLauncher<String> imagePickerLauncher;
    private androidx.activity.result.ActivityResultLauncher<String> videoPickerLauncher;
    
    @Override
    public void setup(Fragment fragment, View rootView) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.settingsManager = SettingsManager.getInstance(fragment.requireContext());
        
        if (fragment instanceof BaseFragment) {
            BaseFragment baseFragment = (BaseFragment) fragment;
            imagePickerLauncher = baseFragment.registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                this::handleImageSelection
            );
            videoPickerLauncher = baseFragment.registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                this::handleVideoSelection
            );
        }
        
        setupThemeSettings();
        setupThemeColorSettings();
        setupBackgroundSettings();
        setupLanguageSettings();
        setupAuthorLink();
    }
    
    private void setupThemeSettings() {
        MaterialCardView themeCard = rootView.findViewById(R.id.themeCard);
        TextView tvThemeValue = rootView.findViewById(R.id.tvThemeValue);
        
        if (themeCard != null && tvThemeValue != null) {
            updateThemeDisplay(tvThemeValue);
            
            themeCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option("0",
                        fragment.getString(R.string.theme_system),
                        fragment.getString(R.string.settings_theme_desc_auto)),
                    new OptionSelectorDialog.Option("1",
                        fragment.getString(R.string.theme_dark),
                        fragment.getString(R.string.settings_theme_desc_dark)),
                    new OptionSelectorDialog.Option("2",
                        fragment.getString(R.string.theme_light),
                        fragment.getString(R.string.settings_theme_desc_light))
                );

                OptionSelectorDialog dialog = new OptionSelectorDialog()
                    .setTitle(fragment.getString(R.string.theme_settings))
                    .setIcon(R.drawable.ic_settings)
                    .setOptions(options)
                    .setCurrentValue(String.valueOf(settingsManager.getThemeMode()))
                    .setAutoCloseOnSelect(true)
                    .setOnOptionSelectedListener(value -> {
                        int themeMode = Integer.parseInt(value);
                        int oldThemeMode = settingsManager.getThemeMode();

                        if (themeMode != oldThemeMode) {
                            settingsManager.setThemeMode(themeMode);
                            
                            android.content.SharedPreferences prefs = fragment.requireContext()
                                .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
                            prefs.edit().putBoolean("restore_settings_after_recreate", true).apply();

                            new android.os.Handler().postDelayed(() -> {
                                if (fragment.isAdded() && fragment.getActivity() != null) {
                                    androidx.fragment.app.FragmentManager fm = fragment.getParentFragmentManager();
                                    for (androidx.fragment.app.Fragment f : fm.getFragments()) {
                                        if (f instanceof androidx.fragment.app.DialogFragment) {
                                            ((androidx.fragment.app.DialogFragment) f).dismissAllowingStateLoss();
                                        }
                                    }

                                    new android.os.Handler().postDelayed(() -> {
                                        if (fragment.isAdded() && fragment.getActivity() != null) {
                                            fragment.requireActivity().recreate();
                                        }
                                    }, 50);
                                }
                            }, 300);
                        }
                    });
                dialog.show(fragment.getParentFragmentManager(), "theme_dialog");
            });
        }
    }
    
    private void setupThemeColorSettings() {
        MaterialCardView themeColorCard = rootView.findViewById(R.id.themeColorCard);

        if (themeColorCard != null) {
            themeColorCard.setOnClickListener(v -> {
                int currentColor = settingsManager.getThemeColor();
                ColorPickerDialog dialog = ColorPickerDialog.newInstance(currentColor);

                dialog.setOnColorSelectedListener(color -> {
                    int oldColor = settingsManager.getThemeColor();
                    settingsManager.setThemeColor(color);
                    
                    if (color != oldColor) {
                        Activity activity = fragment.getActivity();
                        if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                            ThemeColorUpdater colorUpdater = 
                                new ThemeColorUpdater((androidx.appcompat.app.AppCompatActivity) activity);
                            colorUpdater.applyThemeColorDynamically();
                        }
                    }
                });

                dialog.show(fragment.getParentFragmentManager(), "color_picker_dialog");
            });
        }
    }
    
    private void setupBackgroundSettings() {
        // 背景图片设置
        MaterialCardView backgroundImageCard = rootView.findViewById(R.id.backgroundImageCard);
        TextView tvBackgroundImageValue = rootView.findViewById(R.id.tvBackgroundImageValue);

        if (backgroundImageCard != null && tvBackgroundImageValue != null) {
            updateBackgroundImageDisplay(tvBackgroundImageValue);

            backgroundImageCard.setOnClickListener(v -> {
                try {
                    if (fragment.isAdded() && fragment.getContext() != null && imagePickerLauncher != null) {
                        imagePickerLauncher.launch("image/*");
                    }
                } catch (Exception e) {
                    AppLogger.error("AppearanceSettingsModule", "启动图片选择器失败: " + e.getMessage(), e);
                    if (fragment.isAdded() && fragment.getContext() != null) {
                        Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_cannot_open_image_picker), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // 背景视频设置
        MaterialCardView backgroundVideoCard = rootView.findViewById(R.id.backgroundVideoCard);
        TextView tvBackgroundVideoValue = rootView.findViewById(R.id.tvBackgroundVideoValue);

        if (backgroundVideoCard != null && tvBackgroundVideoValue != null) {
            updateBackgroundVideoDisplay(tvBackgroundVideoValue);

            backgroundVideoCard.setOnClickListener(v -> {
                try {
                    if (fragment.isAdded() && fragment.getContext() != null && videoPickerLauncher != null) {
                        videoPickerLauncher.launch("video/*");
                    }
                } catch (Exception e) {
                    AppLogger.error("AppearanceSettingsModule", "启动视频选择器失败: " + e.getMessage(), e);
                    if (fragment.isAdded() && fragment.getContext() != null) {
                        Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_cannot_open_video_picker), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // 恢复默认背景设置
        MaterialCardView restoreBackgroundCard = rootView.findViewById(R.id.restoreBackgroundCard);
        if (restoreBackgroundCard != null) {
            restoreBackgroundCard.setOnClickListener(v -> {
                settingsManager.setBackgroundType("default");
                settingsManager.setBackgroundImagePath("");
                settingsManager.setBackgroundVideoPath("");
                
                TextView tvBgImage = rootView.findViewById(R.id.tvBackgroundImageValue);
                TextView tvBgVideo = rootView.findViewById(R.id.tvBackgroundVideoValue);
                if (tvBgImage != null) updateBackgroundImageDisplay(tvBgImage);
                if (tvBgVideo != null) updateBackgroundVideoDisplay(tvBgVideo);
                
                Activity activity = fragment.getActivity();
                if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                    ThemeManager themeManager = 
                        new ThemeManager((androidx.appcompat.app.AppCompatActivity) activity);
                    themeManager.applyBackgroundFromSettings();
                    
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).updateVideoBackground();
                    }
                    
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_background_restored), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    private void setupLanguageSettings() {
        MaterialCardView languageCard = rootView.findViewById(R.id.languageCard);
        TextView tvLanguageValue = rootView.findViewById(R.id.tvLanguageValue);

        if (languageCard != null && tvLanguageValue != null) {
            updateLanguageDisplay(tvLanguageValue);

            languageCard.setOnClickListener(v -> {
                List<OptionSelectorDialog.Option> options = Arrays.asList(
                    new OptionSelectorDialog.Option(LocaleManager.LANGUAGE_AUTO,
                        fragment.getString(R.string.language_system),
                        fragment.getString(R.string.settings_language_desc_auto)),
                    new OptionSelectorDialog.Option(LocaleManager.LANGUAGE_EN,
                        fragment.getString(R.string.language_english),
                        fragment.getString(R.string.settings_language_desc_en)),
                    new OptionSelectorDialog.Option(LocaleManager.LANGUAGE_ZH,
                        fragment.getString(R.string.language_chinese),
                        fragment.getString(R.string.settings_language_desc_zh))
                );

                String currentLanguage = LocaleManager.getLanguage(fragment.requireContext());

                OptionSelectorDialog dialog = new OptionSelectorDialog()
                    .setTitle(fragment.getString(R.string.language_settings))
                    .setIcon(R.drawable.ic_language)
                    .setOptions(options)
                    .setCurrentValue(currentLanguage)
                    .setAutoCloseOnSelect(false)
                    .setOnOptionSelectedListener(value -> {
                        String oldLanguage = LocaleManager.getLanguage(fragment.requireContext());

                        androidx.fragment.app.FragmentManager fm = fragment.getParentFragmentManager();
                        for (androidx.fragment.app.Fragment f : fm.getFragments()) {
                            if (f instanceof androidx.fragment.app.DialogFragment) {
                                ((androidx.fragment.app.DialogFragment) f).dismissAllowingStateLoss();
                            }
                        }

                        if (!value.equals(oldLanguage)) {
                            LocaleManager.setLanguage(fragment.requireContext(), value);
                            
                            android.content.SharedPreferences prefs = fragment.requireContext()
                                .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
                            prefs.edit().putBoolean("restore_settings_after_recreate", true).apply();

                            new android.os.Handler().postDelayed(() -> {
                                if (fragment.isAdded() && fragment.getActivity() != null) {
                                    fragment.requireActivity().recreate();
                                }
                            }, 100);
                        }
                    });
                dialog.show(fragment.getParentFragmentManager(), "language_dialog");
            });
        }
    }
    
    private void setupAuthorLink() {
        // 设置项目作者 GitHub 按钮
        com.google.android.material.button.MaterialButton btnAuthorGithub = 
            rootView.findViewById(R.id.btnAuthorGithub);
        if (btnAuthorGithub != null) {
            btnAuthorGithub.setOnClickListener(v -> {
                openGitHubProfile("FireworkSky");
            });
        }
        
        // 设置开发者 GitHub 按钮
        com.google.android.material.button.MaterialButton btnContributorGithub = 
            rootView.findViewById(R.id.btnContributorGithub);
        if (btnContributorGithub != null) {
            btnContributorGithub.setOnClickListener(v -> {
                openGitHubProfile("LaoSparrow");
            });
        }
    }
    
    private void openGitHubProfile(String username) {
        try {
            android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_VIEW, 
                Uri.parse("https://github.com/" + username)
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

    private void updateThemeDisplay(TextView textView) {
        int theme = settingsManager.getThemeMode();
        String display;
        switch (theme) {
            case 0:
                display = fragment.getString(R.string.theme_system);
                break;
            case 1:
                display = fragment.getString(R.string.theme_dark);
                break;
            case 2:
                display = fragment.getString(R.string.theme_light);
                break;
            default:
                display = fragment.getString(R.string.theme_system);
                break;
        }
        textView.setText(display);
    }
    
    private void updateLanguageDisplay(TextView textView) {
        String language = LocaleManager.getLanguage(fragment.requireContext());
        String displayName = LocaleManager.getLanguageDisplayName(language);
        textView.setText(displayName);
    }

    private void updateBackgroundImageDisplay(TextView textView) {
        if (textView == null) return;
        String imagePath = settingsManager.getBackgroundImagePath();
        if (imagePath != null && !imagePath.isEmpty()) {
            java.io.File file = new java.io.File(imagePath);
            String fileName = file.getName();
            if (fileName.length() > 20) {
                fileName = fileName.substring(0, 17) + "...";
            }
            textView.setText(fileName);
        } else {
            textView.setText(fragment.getString(R.string.appearance_not_set));
        }
    }

    private void updateBackgroundVideoDisplay(TextView textView) {
        if (textView == null) return;
        String videoPath = settingsManager.getBackgroundVideoPath();
        if (videoPath != null && !videoPath.isEmpty()) {
            java.io.File file = new java.io.File(videoPath);
            String fileName = file.getName();
            if (fileName.length() > 20) {
                fileName = fileName.substring(0, 17) + "...";
            }
            textView.setText(fileName);
        } else {
            textView.setText(fragment.getString(R.string.appearance_not_set));
        }
    }
    
    private void handleImageSelection(Uri uri) {
        if (uri == null) {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_no_file_selected), Toast.LENGTH_SHORT).show();
            return;
        }

        if (rootView == null) {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_ui_not_initialized), Toast.LENGTH_SHORT).show();
            return;
        }

        String imagePath = getPathFromUri(uri);
        if (imagePath != null && !imagePath.isEmpty()) {
            java.io.File imageFile = new java.io.File(imagePath);
            if (!imageFile.exists()) {
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_file_not_exist, imagePath), Toast.LENGTH_LONG).show();
                AppLogger.error("AppearanceSettingsModule", "图片文件不存在: " + imagePath);
                return;
            }
            
            settingsManager.setBackgroundImagePath(imagePath);
            settingsManager.setBackgroundType("image");
            settingsManager.setBackgroundVideoPath("");
            
            TextView tvBackgroundImageValue = rootView.findViewById(R.id.tvBackgroundImageValue);
            TextView tvBackgroundVideoValue = rootView.findViewById(R.id.tvBackgroundVideoValue);
            if (tvBackgroundImageValue != null) updateBackgroundImageDisplay(tvBackgroundImageValue);
            if (tvBackgroundVideoValue != null) updateBackgroundVideoDisplay(tvBackgroundVideoValue);
            
            Activity activity = fragment.getActivity();
            if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                ThemeManager themeManager = 
                    new ThemeManager((androidx.appcompat.app.AppCompatActivity) activity);
                themeManager.applyBackgroundFromSettings();
                
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).updateVideoBackground();
                }
                
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_background_image_set), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_cannot_get_file_path), Toast.LENGTH_LONG).show();
            AppLogger.error("AppearanceSettingsModule", "无法从 URI 获取图片路径: " + uri.toString());
        }
    }

    private void handleVideoSelection(Uri uri) {
        if (uri == null) {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_no_file_selected), Toast.LENGTH_SHORT).show();
            return;
        }

        if (rootView == null) {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_ui_not_initialized), Toast.LENGTH_SHORT).show();
            return;
        }

        String videoPath = getPathFromUri(uri);
        if (videoPath != null && !videoPath.isEmpty()) {
            java.io.File videoFile = new java.io.File(videoPath);
            if (!videoFile.exists()) {
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_file_not_exist, videoPath), Toast.LENGTH_LONG).show();
                AppLogger.error("AppearanceSettingsModule", "视频文件不存在: " + videoPath);
                return;
            }
            
            settingsManager.setBackgroundVideoPath(videoPath);
            settingsManager.setBackgroundType("video");
            settingsManager.setBackgroundImagePath("");
            
            TextView tvBackgroundImageValue = rootView.findViewById(R.id.tvBackgroundImageValue);
            TextView tvBackgroundVideoValue = rootView.findViewById(R.id.tvBackgroundVideoValue);
            if (tvBackgroundVideoValue != null) updateBackgroundVideoDisplay(tvBackgroundVideoValue);
            if (tvBackgroundImageValue != null) updateBackgroundImageDisplay(tvBackgroundImageValue);
            
            Activity activity = fragment.getActivity();
            if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                ThemeManager themeManager = 
                    new ThemeManager((androidx.appcompat.app.AppCompatActivity) activity);
                themeManager.applyBackgroundFromSettings();
                
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).updateVideoBackground();
                }
                
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_background_video_set), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.appearance_cannot_get_file_path), Toast.LENGTH_LONG).show();
            AppLogger.error("AppearanceSettingsModule", "无法从 URI 获取视频路径: " + uri.toString());
        }
    }
    
    private String getPathFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }

        String path = null;
        
        try {
            if (android.content.ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                path = uri.getPath();
            } 
            else if (android.content.ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        try {
                            android.content.ContentResolver resolver = fragment.requireContext().getContentResolver();
                            int takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            resolver.takePersistableUriPermission(uri, takeFlags);
                        } catch (Exception e) {
                            AppLogger.debug("AppearanceSettingsModule", "无法获取持久化权限: " + e.getMessage());
                        }
                    }
                    
                    String[] projection = {android.provider.MediaStore.MediaColumns.DATA};
                    android.database.Cursor cursor = null;
                    
                    try {
                        cursor = fragment.requireContext().getContentResolver().query(uri, projection, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA);
                            if (columnIndex >= 0) {
                                path = cursor.getString(columnIndex);
                            }
                        }
                    } catch (Exception e) {
                        AppLogger.error("AppearanceSettingsModule", "查询 MediaStore 失败: " + e.getMessage());
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    
                    if (path == null || path.isEmpty()) {
                        try {
                            android.content.ContentResolver resolver = fragment.requireContext().getContentResolver();
                            java.io.InputStream inputStream = resolver.openInputStream(uri);
                            if (inputStream != null) {
                                String extension = "";
                                String mimeType = resolver.getType(uri);
                                if (mimeType != null) {
                                    if (mimeType.startsWith("image/")) {
                                        if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
                                            extension = ".jpg";
                                        } else if (mimeType.contains("png")) {
                                            extension = ".png";
                                        } else if (mimeType.contains("gif")) {
                                            extension = ".gif";
                                        } else if (mimeType.contains("webp")) {
                                            extension = ".webp";
                                        }
                                    } else if (mimeType.startsWith("video/")) {
                                        if (mimeType.contains("mp4")) {
                                            extension = ".mp4";
                                        } else if (mimeType.contains("avi")) {
                                            extension = ".avi";
                                        } else if (mimeType.contains("mkv")) {
                                            extension = ".mkv";
                                        }
                                    }
                                }
                                
                                java.io.File tempFile = new java.io.File(
                                    fragment.requireContext().getCacheDir(), 
                                    "background_" + System.currentTimeMillis() + extension
                                );
                                
                                java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                                outputStream.close();
                                inputStream.close();
                                
                                path = tempFile.getAbsolutePath();
                                AppLogger.debug("AppearanceSettingsModule", "文件已复制到: " + path);
                            }
                        } catch (Exception e) {
                            AppLogger.error("AppearanceSettingsModule", "无法从 URI 复制文件: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    AppLogger.error("AppearanceSettingsModule", "查询文件路径失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            AppLogger.error("AppearanceSettingsModule", "处理 URI 失败: " + e.getMessage());
        }

        return path;
    }
}


