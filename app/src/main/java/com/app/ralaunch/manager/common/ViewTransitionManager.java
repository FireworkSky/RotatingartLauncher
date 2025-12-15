package com.app.ralaunch.manager.common;

import android.view.View;
import android.view.ViewGroup;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

public class ViewTransitionManager {
    
    public static void transitionViews(View hideView, View showView, Runnable onComplete) {
        if (hideView == null || showView == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        YoYo.with(Techniques.FadeOut).duration(200)
                .onEnd(animator -> {
                    hideView.setVisibility(View.GONE);
                    showView.setVisibility(View.VISIBLE);
                    YoYo.with(Techniques.FadeIn).duration(200).playOn(showView);
                    if (onComplete != null) onComplete.run();
                })
                .playOn(hideView);
    }
    
    public static void transitionViewsImmediate(View hideView, View showView) {
        if (hideView != null) hideView.setVisibility(View.GONE);
        if (showView != null) showView.setVisibility(View.VISIBLE);
    }
    
    public static void clearContainer(ViewGroup container) {
        if (container != null) container.removeAllViews();
    }
    
    public static void addViewToContainer(ViewGroup container, View view) {
        if (container != null && view != null) {
            container.addView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
}

