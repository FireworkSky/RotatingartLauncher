package com.app.ralaunch.utils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class PageManager {
    private FragmentManager fragmentManager;
    private int containerId;
    private OnPageChangeListener pageChangeListener;

    public PageManager(FragmentManager fragmentManager, int containerId) {
        this.fragmentManager = fragmentManager;
        this.containerId = containerId;
    }

    public interface OnPageChangeListener {
        void onPageChanged(int currentPage, int totalPages);
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        this.pageChangeListener = listener;
    }

    public void showPage(Fragment fragment, String tag) {
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

    public void goBack() {
        if (fragmentManager.getBackStackEntryCount() > 1) {
            fragmentManager.popBackStack();
        }
    }

    public int getBackStackCount() {
        return fragmentManager.getBackStackEntryCount();
    }

    public void clearBackStack() {
        while (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStackImmediate();
        }
    }
}