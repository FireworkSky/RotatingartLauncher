package com.app.ralaunch.fragment;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.app.ralaunch.R;
import com.app.ralaunch.activity.MainActivity;

/**
 * Fragment 辅助工具类
 * 提供 Fragment 相关的通用操作，减少代码重复
 */
public class FragmentHelper {

    /**
     * 检查 Activity 是否为 MainActivity
     */
    public static boolean isMainActivity(@Nullable Activity activity) {
        return activity instanceof MainActivity;
    }

    /**
     * 安全地获取 MainActivity
     */
    @Nullable
    public static MainActivity getMainActivity(@Nullable Activity activity) {
        if (activity instanceof MainActivity) {
            return (MainActivity) activity;
        }
        return null;
    }

    /**
     * 从 Fragment 获取 MainActivity
     */
    @Nullable
    public static MainActivity getMainActivity(@NonNull Fragment fragment) {
        if (fragment.isAdded() && fragment.getActivity() instanceof MainActivity) {
            return (MainActivity) fragment.getActivity();
        }
        return null;
    }

    /**
     * 打开文件浏览器 Fragment
     */
    public static void openFileBrowser(
            @NonNull Fragment fragment,
            @Nullable String fileType,
            @Nullable String[] extensions,
            @NonNull FileBrowserFragment.OnFileSelectedListener onFileSelected,
            @Nullable Runnable onBack) {

        MainActivity mainActivity = getMainActivity(fragment);
        if (mainActivity == null) {
            return;
        }

        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        if (fileType != null && extensions != null) {
            fileBrowserFragment.setFileType(fileType, extensions);
        }

        fileBrowserFragment.setOnFileSelectedListener((filePath, type) -> {
            onFileSelected.onFileSelected(filePath, type);
            FragmentNavigator navigator = new FragmentNavigator(
                mainActivity.getSupportFragmentManager(),
                R.id.fragmentContainer
            );
            navigator.goBack();
        });

        if (onBack != null) {
            fileBrowserFragment.setOnBackListener(() -> {
                onBack.run();
                FragmentNavigator navigator = new FragmentNavigator(
                    mainActivity.getSupportFragmentManager(),
                    R.id.fragmentContainer
                );
                navigator.goBack();
            });
        } else {
            fileBrowserFragment.setOnBackListener(() -> {
                FragmentNavigator navigator = new FragmentNavigator(
                    mainActivity.getSupportFragmentManager(),
                    R.id.fragmentContainer
                );
                navigator.goBack();
            });
        }

        FragmentNavigator navigator = new FragmentNavigator(
            mainActivity.getSupportFragmentManager(),
            R.id.fragmentContainer
        );
        navigator.replace(fileBrowserFragment, "file_browser");
    }

    /**
     * 打开文件浏览器选择图片
     */
    public static void openImageFileBrowser(
            @NonNull Fragment fragment,
            @NonNull FileBrowserFragment.OnFileSelectedListener onFileSelected) {
        openFileBrowser(
            fragment,
            "image",
            new String[]{".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"},
            onFileSelected,
            null
        );
    }

    /**
     * 打开文件浏览器选择视频
     */
    public static void openVideoFileBrowser(
            @NonNull Fragment fragment,
            @NonNull FileBrowserFragment.OnFileSelectedListener onFileSelected) {
        openFileBrowser(
            fragment,
            "video",
            new String[]{".mp4", ".avi", ".mkv", ".mov", ".webm", ".3gp"},
            onFileSelected,
            null
        );
    }

    /**
     * 打开文件浏览器选择程序集文件
     */
    public static void openAssemblyFileBrowser(
            @NonNull Fragment fragment,
            @NonNull FileBrowserFragment.OnAssemblySelectedListener onAssemblySelected) {
        MainActivity mainActivity = getMainActivity(fragment);
        if (mainActivity == null) {
            return;
        }

        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        fileBrowserFragment.setMode(FileBrowserFragment.MODE_SELECT_ASSEMBLY);
        fileBrowserFragment.setOnAssemblySelectedListener(assemblyPath -> {
            onAssemblySelected.onAssemblySelected(assemblyPath);
            FragmentNavigator navigator = new FragmentNavigator(
                mainActivity.getSupportFragmentManager(),
                R.id.fragmentContainer
            );
            navigator.goBack();
        });

        fileBrowserFragment.setOnBackListener(() -> {
            FragmentNavigator navigator = new FragmentNavigator(
                mainActivity.getSupportFragmentManager(),
                R.id.fragmentContainer
            );
            navigator.goBack();
        });

        FragmentNavigator navigator = new FragmentNavigator(
            mainActivity.getSupportFragmentManager(),
            R.id.fragmentContainer
        );
        navigator.replace(fileBrowserFragment, "assembly_browser");
    }
}


