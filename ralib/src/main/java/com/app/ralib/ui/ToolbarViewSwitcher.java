package com.app.ralib.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import com.app.ralib.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具栏视图切换器 - Material Design 3 风格
 * 类似左侧导航栏的设计，在顶部工具栏显示多个图标按钮
 * 支持选中状态高亮显示和切换动画
 */
public class ToolbarViewSwitcher extends LinearLayout {
    
    private List<ViewItem> viewItems = new ArrayList<>();
    private int selectedIndex = 0;
    private OnViewSelectedListener listener;
    
    public static class ViewItem {
        private Drawable icon;
        private String label;
        private int iconResId;
        
        public ViewItem(int iconResId, String label) {
            this.iconResId = iconResId;
            this.label = label;
        }
        
        public ViewItem(Drawable icon, String label) {
            this.icon = icon;
            this.label = label;
        }
        
        public Drawable getIcon(Context context) {
            if (icon != null) {
                return icon;
            }
            if (iconResId != 0 && context != null) {
                return context.getDrawable(iconResId);
            }
            return null;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
    public interface OnViewSelectedListener {
        void onViewSelected(int index, ViewItem item);
    }
    
    public ToolbarViewSwitcher(Context context) {
        super(context);
        init(context);
    }
    
    public ToolbarViewSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public ToolbarViewSwitcher(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setLayoutParams(new LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }
    
    /**
     * 添加视图项
     */
    public void addViewItem(int iconResId, String label) {
        viewItems.add(new ViewItem(iconResId, label));
        refreshViews();
    }
    
    /**
     * 添加视图项（使用Drawable）
     */
    public void addViewItem(Drawable icon, String label) {
        viewItems.add(new ViewItem(icon, label));
        refreshViews();
    }
    
    /**
     * 设置视图项列表
     */
    public void setViewItems(List<ViewItem> items) {
        if (items != null) {
            viewItems.clear();
            viewItems.addAll(items);
            refreshViews();
        }
    }
    
    /**
     * 选择指定索引的视图
     */
    public void selectView(int index) {
        if (index < 0 || index >= viewItems.size()) {
            return;
        }
        
        int previousIndex = selectedIndex;
        selectedIndex = index;
        
        updateSelection(previousIndex, selectedIndex);
        
        if (listener != null) {
            listener.onViewSelected(selectedIndex, viewItems.get(selectedIndex));
        }
    }
    
    /**
     * 获取当前选中的索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }
    
    /**
     * 获取当前选中的视图项
     */
    public ViewItem getSelectedViewItem() {
        if (viewItems.isEmpty() || selectedIndex < 0 || selectedIndex >= viewItems.size()) {
            return null;
        }
        return viewItems.get(selectedIndex);
    }
    
    /**
     * 刷新视图
     */
    private void refreshViews() {
        removeAllViews();
        
        for (int i = 0; i < viewItems.size(); i++) {
            ViewItem item = viewItems.get(i);
            MaterialButton button = createButton(item, i);
            addView(button);
        }
        
        // 更新选中状态
        updateSelection(-1, selectedIndex);
    }
    
    /**
     * 创建按钮
     */
    private MaterialButton createButton(ViewItem item, int index) {
        // 使用MaterialButton的IconButton样式
        MaterialButton button = new MaterialButton(getContext());
        
        // 设置图标
        Drawable icon = item.getIcon(getContext());
        if (icon != null) {
            button.setIcon(icon);
            button.setIconSize((int) (24 * getResources().getDisplayMetrics().density));
        }
        
        // 设置内容描述
        if (item.getLabel() != null && !item.getLabel().isEmpty()) {
            button.setContentDescription(item.getLabel());
        }
        
        // 设置图标着色（默认灰色）
        button.setIconTint(android.content.res.ColorStateList.valueOf(
            getContext().getColor(android.R.color.darker_gray)
        ));
        
        // 设置布局参数
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            (int) (48 * getResources().getDisplayMetrics().density),
            (int) (48 * getResources().getDisplayMetrics().density)
        );
        params.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
        button.setLayoutParams(params);
        
        // 设置点击事件
        button.setOnClickListener(v -> selectView(index));
        
        return button;
    }
    
    /**
     * 更新选中状态
     */
    private void updateSelection(int previousIndex, int newIndex) {
        // 取消之前的选中状态
        if (previousIndex >= 0 && previousIndex < getChildCount()) {
            View previousView = getChildAt(previousIndex);
            if (previousView instanceof MaterialButton) {
                MaterialButton previousButton = (MaterialButton) previousView;
                previousButton.setSelected(false);
                // 移除选中状态的背景（恢复默认）
                previousButton.setBackgroundTintList(null);
                // 恢复图标颜色为默认
                previousButton.setIconTint(android.content.res.ColorStateList.valueOf(
                    getContext().getColor(android.R.color.darker_gray)
                ));
            }
        }
        
        // 设置新的选中状态
        if (newIndex >= 0 && newIndex < getChildCount()) {
            View newView = getChildAt(newIndex);
            if (newView instanceof MaterialButton) {
                MaterialButton newButton = (MaterialButton) newView;
                newButton.setSelected(true);
                // 设置选中状态的背景色（主题色）
                int primaryColor = android.graphics.Color.parseColor("#9C7AE8"); // 使用主题色
                newButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(primaryColor)
                );
                // 选中时图标颜色为白色
                newButton.setIconTint(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.WHITE
                ));
                
                // 添加选中动画
                animateSelection(newButton);
            }
        }
    }
    
    /**
     * 选中动画
     */
    private void animateSelection(View view) {
        // 缩放动画
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .withEndAction(() -> {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start();
            })
            .start();
    }
    
    /**
     * 设置视图选择监听器
     */
    public void setOnViewSelectedListener(OnViewSelectedListener listener) {
        this.listener = listener;
    }
}

