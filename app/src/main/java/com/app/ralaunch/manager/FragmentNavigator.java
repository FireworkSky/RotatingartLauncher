package com.app.ralaunch.manager;

import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.app.ralaunch.utils.AppLogger;

/**
 * Fragment 导航管理器
 */
public class FragmentNavigator {
    private final FragmentManager fragmentManager;
    private final View mainLayout;
    private final View fragmentContainer;
    private final int containerId;
    
    public FragmentNavigator(FragmentManager fragmentManager, int fragmentContainerId, View mainLayout) {
        this.fragmentManager = fragmentManager;
        this.mainLayout = mainLayout;
        this.containerId = fragmentContainerId;
        this.fragmentContainer = mainLayout.getRootView().findViewById(fragmentContainerId);
    }
    
    /**
     * 获取 FragmentManager（用于对话框等需要直接访问的场景）
     */
    public FragmentManager getFragmentManager() {
        return fragmentManager;
    }
    
    /**
     * 显示 Fragment
     */
    public void showFragment(Fragment fragment, String tag) {
        mainLayout.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );
        transaction.replace(containerId, fragment, tag);
        transaction.addToBackStack(tag);
        transaction.commit();
    }
    
    /**
     * 隐藏 Fragment
     */
    public void hideFragment() {
        mainLayout.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
        
        Fragment fragment = fragmentManager.findFragmentById(containerId);
        if (fragment != null) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.remove(fragment);
            transaction.commit();
        }
    }
    
    /**
     * 隐藏指定标签的 Fragment
     */
    public void hideFragment(String tag) {
        mainLayout.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
        
        Fragment fragment = fragmentManager.findFragmentById(containerId);
        if (fragment != null) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.remove(fragment);
            transaction.commitNow();
        }
        
        try {
            fragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    /**
     * 返回上一页
     */
    public void goBack() {
        if (fragmentManager.getBackStackEntryCount() > 1) {
            fragmentManager.popBackStack();
        }
        if (fragmentManager.getBackStackEntryCount() <= 1) {
            hideFragment();
        }
    }
    
    /**
     * 获取回退栈数量
     */
    public int getBackStackCount() {
        return fragmentManager.getBackStackEntryCount();
    }
    
    /**
     * 检查主布局是否可见
     */
    public boolean isMainLayoutVisible() {
        return mainLayout.getVisibility() == View.VISIBLE;
    }
    
    /**
     * 显示 Fragment（使用 PageManager，带回退栈）
     */
    public void showPage(Fragment fragment, String tag) {
        showFragment(fragment, tag);
    }
    
    /**
     * 隐藏 Fragment 并清理容器（用于特殊场景如 ControlLayoutFragment）
     */
    public void hideFragmentWithCleanup(String tag) {
        Fragment fragment = fragmentManager.findFragmentById(containerId);
        if (fragment != null) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.remove(fragment);
            transaction.commitNow();
        }
        
        try {
            fragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch (Exception e) {
        }
        
        if (fragmentContainer != null) {
            fragmentContainer.setBackground(null);
            fragmentContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            fragmentContainer.setVisibility(View.GONE);
        }
        
        mainLayout.setVisibility(View.VISIBLE);
        mainLayout.bringToFront();
        mainLayout.requestLayout();
        mainLayout.invalidate();
        
        View rootView = mainLayout.getRootView();
        if (rootView != null) {
            rootView.requestLayout();
            rootView.postInvalidate();
        }
    }
}

