package com.app.ralaunch.fragment;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * Fragment 基类
 * 提供通用的 Fragment 操作和工具方法，减少代码重复
 */
public abstract class BaseFragment extends Fragment {

    /**
     * 安全地获取 Activity（已检查 isAdded 和 getActivity）
     */
    @Nullable
    protected Activity getSafeActivity() {
        if (isAdded() && getActivity() != null) {
            return getActivity();
        }
        return null;
    }

    /**
     * 安全地获取 Context（已检查 isAdded 和 getContext）
     */
    @Nullable
    protected Context getSafeContext() {
        if (isAdded() && getContext() != null) {
            return getContext();
        }
        return null;
    }

    /**
     * 安全地获取 FragmentManager
     */
    @Nullable
    protected FragmentManager getSafeFragmentManager() {
        Activity activity = getSafeActivity();
        if (activity instanceof androidx.fragment.app.FragmentActivity) {
            return ((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager();
        }
        return null;
    }

    /**
     * 在主线程执行操作
     */
    protected void runOnUiThread(@NonNull Runnable action) {
        Activity activity = getSafeActivity();
        if (activity != null) {
            activity.runOnUiThread(action);
        }
    }

    /**
     * 显示 Toast 消息
     */
    protected void showToast(@NonNull String message) {
        Context context = getSafeContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示长 Toast 消息
     */
    protected void showLongToast(@NonNull String message) {
        Context context = getSafeContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 检查 Fragment 是否仍然附加到 Activity
     */
    protected boolean isFragmentValid() {
        return isAdded() && getActivity() != null && !isDetached();
    }
}



