package com.app.ralaunch.manager.common;

import android.view.View;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

public class AnimationHelper {
    
    public static void animateButtonClick(View button, Runnable action) {
        if (button == null) {
            if (action != null) action.run();
            return;
        }
        YoYo.with(Techniques.BounceIn).duration(700).playOn(button);
        if (action != null) action.run();
    }
    
    public static void animateButtonPulse(View button, Runnable action) {
        if (button == null) {
            if (action != null) action.run();
            return;
        }
        YoYo.with(Techniques.Pulse).duration(200).playOn(button);
        if (action != null) action.run();
    }
    
    public static void animateRefresh(View view) {
        if (view != null) {
            YoYo.with(Techniques.Flash).duration(600).playOn(view);
        }
    }
    
    public static void animateSelection(View view) {
        if (view != null) {
            YoYo.with(Techniques.Tada).duration(800).playOn(view);
        }
    }
    
    public static void animateSlideInRight(View view) {
        if (view != null) {
            YoYo.with(Techniques.SlideInRight).duration(300).playOn(view);
        }
    }
    
    public static void animateFadeOut(View view, Runnable onEnd) {
        if (view == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        YoYo.with(Techniques.FadeOut).duration(200)
                .onEnd(animator -> {
                    if (onEnd != null) onEnd.run();
                })
                .playOn(view);
    }
    
    public static void animateFadeIn(View view) {
        if (view != null) {
            YoYo.with(Techniques.FadeIn).duration(200).playOn(view);
        }
    }
    
    public static void animateScaleUpdate(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(1.2f).scaleY(1.2f).setDuration(150)
                .withEndAction(() -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                })
                .start();
    }
    
    public static void setClickListenerWithBounce(View button, View.OnClickListener listener) {
        if (button == null) return;
        button.setOnClickListener(v -> {
            YoYo.with(Techniques.BounceIn).duration(700).playOn(button);
            if (listener != null) listener.onClick(v);
        });
    }
    
    public static void setClickListenerWithPulse(View button, View.OnClickListener listener) {
        if (button == null) return;
        button.setOnClickListener(v -> {
            YoYo.with(Techniques.Pulse).duration(200).playOn(v);
            if (listener != null) listener.onClick(v);
        });
    }
    
    public static void setClickListener(View button, View.OnClickListener listener) {
        if (button != null && listener != null) {
            button.setOnClickListener(listener);
        }
    }
}

