package com.app.ralib.dialog;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralib.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用选择对话框
 * 可用于任何需要从列表中选择一项的场景
 */
public class OptionSelectorDialog extends DialogFragment {

    private RecyclerView rvOptions;
    private TextView tvTitle;
    private MaterialButton btnClose;
    
    private List<Option> options;
    private String currentValue;
    private String selectedValue;
    private OptionAdapter adapter;
    
    private OnOptionSelectedListener listener;
    
    // 配置参数
    private String dialogTitle = "选择选项";
    private int dialogIconRes = 0;
    private boolean autoCloseOnSelect = true;
    private int autoCloseDelay = 300;
    private int dialogStyleRes = 0;

    /**
     * 选项数据类
     */
    public static class Option {
        private String value;
        private String label;
        private String description;
        private int iconRes;
        
        public Option(String value, String label) {
            this(value, label, null, 0);
        }
        
        public Option(String value, String label, String description) {
            this(value, label, description, 0);
        }
        
        public Option(String value, String label, String description, int iconRes) {
            this.value = value;
            this.label = label;
            this.description = description;
            this.iconRes = iconRes;
        }
        
        public String getValue() { return value; }
        public String getLabel() { return label; }
        public String getDescription() { return description; }
        public int getIconRes() { return iconRes; }
    }

    /**
     * 选择监听器
     */
    public interface OnOptionSelectedListener {
        void onOptionSelected(String value);
    }

    public OptionSelectorDialog() {
        this.options = new ArrayList<>();
    }

    // Builder 模式配置
    public OptionSelectorDialog setTitle(String title) {
        this.dialogTitle = title;
        return this;
    }
    
    public OptionSelectorDialog setIcon(int iconRes) {
        // 图标功能已移除，保持 API 兼容性但不显示图标
        this.dialogIconRes = iconRes;
        return this;
    }
    
    public OptionSelectorDialog setOptions(List<Option> options) {
        this.options = options;
        return this;
    }
    
    public OptionSelectorDialog setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
        this.selectedValue = currentValue;
        return this;
    }
    
    public OptionSelectorDialog setAutoCloseOnSelect(boolean autoClose) {
        this.autoCloseOnSelect = autoClose;
        return this;
    }
    
    public OptionSelectorDialog setAutoCloseDelay(int delayMs) {
        this.autoCloseDelay = delayMs;
        return this;
    }
    
    public OptionSelectorDialog setDialogStyle(int styleRes) {
        this.dialogStyleRes = styleRes;
        return this;
    }
    
    public OptionSelectorDialog setOnOptionSelectedListener(OnOptionSelectedListener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 如果是状态恢复但没有options数据，直接关闭
        if (savedInstanceState != null && (options == null || options.isEmpty())) {
            dismissAllowingStateLoss();
            return;
        }
        
        // 使用外部提供的样式，或默认样式
        if (dialogStyleRes != 0) {
            setStyle(DialogFragment.STYLE_NORMAL, dialogStyleRes);
        }
        
        // 设置对话框可以通过点击外部关闭
        setCancelable(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            
            // 设置窗口背景为透明，以显示 MaterialCardView 的圆角
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            window.setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.8),
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_option_selector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 如果选项列表为空（可能是状态恢复），直接关闭对话框
        if (options == null || options.isEmpty()) {
            dismiss();
            return;
        }

        // 初始化视图
        rvOptions = view.findViewById(R.id.rvOptions);
        tvTitle = view.findViewById(R.id.tvTitle);
        btnClose = view.findViewById(R.id.btnClose);

        // 设置标题（图标已从布局中移除，保持极简设计）
        tvTitle.setText(dialogTitle);

        // 初始化 RecyclerView
        rvOptions.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OptionAdapter();
        rvOptions.setAdapter(adapter);

        // 关闭按钮 - 使用 dismissAllowingStateLoss 确保总是可以关闭
        btnClose.setOnClickListener(v -> {
            if (isAdded() && getDialog() != null) {
                dismissAllowingStateLoss();
            }
        });

        // 添加进入动画
        animateEnter(view);
    }

    private void onOptionItemClicked(String value) {
        selectedValue = value;
        adapter.setSelectedValue(value);
        
        // 通知监听器
        if (listener != null) {
            listener.onOptionSelected(value);
        }
        
        // 自动关闭
        if (autoCloseOnSelect) {
            new Handler().postDelayed(() -> {
                if (isAdded() && getDialog() != null && getDialog().isShowing()) {
                    dismiss();
                }
            }, autoCloseDelay);
        }
    }

    private void animateEnter(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    /**
     * RecyclerView 适配器
     */
    private class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.ViewHolder> {
        
        private String selectedValue;
        
        public void setSelectedValue(String value) {
            this.selectedValue = value;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_option_selector, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Option option = options.get(position);
            holder.bind(option);
        }

        @Override
        public int getItemCount() {
            return options.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardOption;
            ImageView ivOptionIcon;
            TextView tvOptionLabel;
            TextView tvOptionDescription;
            ImageView ivCheckmark;

            ViewHolder(View itemView) {
                super(itemView);
                cardOption = (MaterialCardView) itemView;
                ivOptionIcon = itemView.findViewById(R.id.ivOptionIcon);
                tvOptionLabel = itemView.findViewById(R.id.tvOptionLabel);
                tvOptionDescription = itemView.findViewById(R.id.tvOptionDescription);
                ivCheckmark = itemView.findViewById(R.id.ivCheckmark);
            }

            void bind(Option option) {
                tvOptionLabel.setText(option.getLabel());
                
                // 设置描述
                if (option.getDescription() != null && !option.getDescription().isEmpty()) {
                    tvOptionDescription.setVisibility(View.VISIBLE);
                    tvOptionDescription.setText(option.getDescription());
                } else {
                    tvOptionDescription.setVisibility(View.GONE);
                }
                
                // 设置图标
                if (option.getIconRes() > 0) {
                    ivOptionIcon.setVisibility(View.VISIBLE);
                    ivOptionIcon.setImageResource(option.getIconRes());
                } else {
                    ivOptionIcon.setVisibility(View.GONE);
                }
                
                // 设置选中状态
                boolean isSelected = option.getValue().equals(selectedValue);
                ivCheckmark.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                
                // 选中状态：使用主色调容器背景和边框，未选中：使用表面色和边框
                android.util.TypedValue typedValue = new android.util.TypedValue();
                android.content.res.Resources.Theme theme = cardOption.getContext().getTheme();
                
                if (isSelected) {
                    // 选中：使用主色调容器背景 + 主色调边框
                    if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)) {
                        int primaryContainerColor = typedValue.data;
                        cardOption.setCardBackgroundColor(primaryContainerColor);
                    }
                    
                    if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
                        int primaryColor = typedValue.data;
                        cardOption.setStrokeColor(primaryColor);
                        cardOption.setStrokeWidth((int) (2 * cardOption.getResources().getDisplayMetrics().density)); // 2dp
                    }
                    
                    // 获取 onPrimaryContainer 颜色
                    typedValue = new android.util.TypedValue();
                    if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)) {
                        int onPrimaryContainerColor = typedValue.data;
                        tvOptionLabel.setTextColor(onPrimaryContainerColor);
                    }
                } else {
                    // 未选中：使用表面色背景 + 轮廓边框
                    if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                        int surfaceColor = typedValue.data;
                        cardOption.setCardBackgroundColor(surfaceColor);
                    }
                    
                    if (theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)) {
                        int outlineColor = typedValue.data;
                        cardOption.setStrokeColor(outlineColor);
                        cardOption.setStrokeWidth((int) (1 * cardOption.getResources().getDisplayMetrics().density)); // 1dp
                    }
                    
                    // 获取 onSurface 颜色
                    typedValue = new android.util.TypedValue();
                    if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                        int onSurfaceColor = typedValue.data;
                        tvOptionLabel.setTextColor(onSurfaceColor);
                    }
                }

                // 点击事件
                itemView.setOnClickListener(v -> {
                    // 点击动画
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction(() -> {
                            v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start();
                        })
                        .start();
                    
                    onOptionItemClicked(option.getValue());
                });
            }
        }
    }
}

