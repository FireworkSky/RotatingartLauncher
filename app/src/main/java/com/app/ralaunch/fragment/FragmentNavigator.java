package com.app.ralaunch.fragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

/**
 * Fragment 导航工具类
 * 统一管理 Fragment 之间的导航，减少重复代码和耦合
 */
public class FragmentNavigator {

    private final FragmentManager fragmentManager;
    private final int containerId;

    public FragmentNavigator(@NonNull FragmentManager fragmentManager, int containerId) {
        this.fragmentManager = fragmentManager;
        this.containerId = containerId;
    }

    /**
     * 替换当前 Fragment
     */
    public void replace(@NonNull Fragment fragment, @Nullable String backStackName) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(containerId, fragment);
        if (backStackName != null) {
            transaction.addToBackStack(backStackName);
        }
        transaction.commit();
    }

    /**
     * 添加 Fragment（不替换）
     */
    public void add(@NonNull Fragment fragment, @Nullable String backStackName) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.add(containerId, fragment);
        if (backStackName != null) {
            transaction.addToBackStack(backStackName);
        }
        transaction.commit();
    }

    /**
     * 返回上一个 Fragment
     */
    public boolean goBack() {
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            return true;
        }
        return false;
    }

    /**
     * 返回并执行回调
     */
    public void goBack(@Nullable Runnable onBackComplete) {
        if (goBack() && onBackComplete != null) {
            onBackComplete.run();
        }
    }

    /**
     * 清除所有返回栈
     */
    public void clearBackStack() {
        int count = fragmentManager.getBackStackEntryCount();
        for (int i = 0; i < count; i++) {
            fragmentManager.popBackStack();
        }
    }

    /**
     * 获取返回栈数量
     */
    public int getBackStackCount() {
        return fragmentManager.getBackStackEntryCount();
    }
}



