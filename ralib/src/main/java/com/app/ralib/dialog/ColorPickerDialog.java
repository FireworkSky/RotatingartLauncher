package com.app.ralib.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.app.ralib.R;
import com.app.ralib.ui.ColorPickerView;

/**
 * Material Design 3 风格颜色���择器对话框
 * 支持 ARGB 颜色选择，包括透明度
 */
public class ColorPickerDialog extends DialogFragment {

    private ColorPickerView colorPicker;
    private android.view.View colorPreview; // 大块颜色预览
    private TextView tvTitle, tvHexDisplay, tvRgbDisplay;
    private android.widget.ImageButton btnCopyHex, btnCopyRgb;
    private com.google.android.material.button.MaterialButton btnConfirm, btnCancel;
    private android.widget.GridLayout gridPresets;

    private int currentColor = 0xFF9C7AE8; // 当前选择的颜色（实时变化）
    private int selectedColor = 0xFF9C7AE8; // 已选中的颜色（确定后的颜色）
    private int initialColor = 0xFF9C7AE8; // 初始颜色，用于取消时恢复
    private String dialogTitle = null; // 自定义标题
    private int backgroundOpacity = 100; // 背景透明度 (0-100)
    private OnColorSelectedListener listener;

    // 预设颜色 - 精选自 peiseka.cn
    private final int[] presetColors = {
        // 第一行：黑白灰 + 冷色系
        0xFF000000, // 黑
        0xFFFFFFFF, // 白
        0xFF393E46, // 深灰 (No.1)
        0xFF00ADB5, // 青 (No.1)
        0xFF355C7D, // 深蓝 (No.22)
        0xFF8C82FC, // 紫 (No.37)
        0xFF00B8A9, // 绿 (No.12)
        0xFF71C9CE, // 湖蓝 (No.7)
        
        // 第二行：暖色系
        0xFFFF2E63, // 亮红 (No.4)
        0xFFF6416C, // 红 (No.12)
        0xFFFC5185, // 粉 (No.5)
        0xFFF38181, // 浅粉 (No.3)
        0xFFFF9A00, // 橙 (No.21)
        0xFFFFD460, // 黄 (No.24)
        0xFFAA96DA, // 淡紫 (No.6)
        0xFFB83B5E  // 洋红 (No.2)
    };

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private OnColorChangedListener colorChangedListener;

    public ColorPickerDialog() {
    }

    public static ColorPickerDialog newInstance(int initialColor) {
        ColorPickerDialog dialog = new ColorPickerDialog();
        Bundle args = new Bundle();
        args.putInt("color", initialColor);
        dialog.setArguments(args);
        dialog.initialColor = initialColor;
        dialog.currentColor = initialColor;
        dialog.selectedColor = initialColor;
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置对话框样式为无标题栏，使用透明背景
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog);
        if (getArguments() != null) {
            currentColor = getArguments().getInt("color", 0xFF9C7AE8);
            initialColor = currentColor; // 保存初始颜色
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.ralib_dialog_color_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 启用硬件加速，确保 Material Design 的触摸反馈和点击事件正常工作
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 颜色选择器对话框始终保持完全不透明
        view.setAlpha(1.0f);

        // 初始化视图
        tvTitle = view.findViewById(R.id.tvTitle);
        colorPicker = view.findViewById(R.id.colorPicker);
        colorPreview = view.findViewById(R.id.colorPreview);
        tvHexDisplay = view.findViewById(R.id.tvHexDisplay);
        tvRgbDisplay = view.findViewById(R.id.tvRgbDisplay);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnCancel = view.findViewById(R.id.btnCancel);
        gridPresets = view.findViewById(R.id.gridPresets);
        android.widget.ImageButton btnClose = view.findViewById(R.id.btnClose);
        
        // 初始化预设颜色
        if (gridPresets != null) {
            initPresetColors(view.getContext());
        }
        
      
        if (dialogTitle != null && tvTitle != null) {
            tvTitle.setText(dialogTitle);
        }
        
        // 确保按钮可见
        if (btnConfirm != null) {
            btnConfirm.setVisibility(View.VISIBLE);
        }
        if (btnCancel != null) {
            btnCancel.setVisibility(View.VISIBLE);
        }

        // 先设置监听器，再设置颜色，确保颜色变化时能正确回调
        colorPicker.setOnColorChangedListener(color -> {
            // 移除透明度，主题色始终完全不透明
            int rgbColor = (color & 0x00FFFFFF) | 0xFF000000;
            // 强制更新，即使颜色值相同也要更新（因为色相可能改变了）
            currentColor = rgbColor;
            updateColorPreview(currentColor); // 更新预览
            updateInputsFromColor(currentColor);
            
            // 通知外部监听器颜色已变化
            if (colorChangedListener != null) {
                colorChangedListener.onColorChanged(currentColor);
            }
        });

        // 设置初始颜色
        selectedColor = initialColor; // 保存的颜色（当前颜色）
        currentColor = initialColor; // 正在选择的颜色（选中颜色）
        colorPicker.setColor(currentColor);
        updateColorPreview(currentColor); // 更新预览
        updateInputsFromColor(currentColor);



        // 复制按钮
        btnCopyHex = view.findViewById(R.id.btnCopyHex);
        btnCopyRgb = view.findViewById(R.id.btnCopyRgb);
        
        if (btnCopyHex != null) {
            btnCopyHex.setOnClickListener(v -> copyHexToClipboard());
        }
        
        if (btnCopyRgb != null) {
            btnCopyRgb.setOnClickListener(v -> copyRgbToClipboard());
        }

        // 关闭按钮
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        // 确定按钮 - 保存并关闭
        if (btnConfirm != null) {
            btnConfirm.setVisibility(View.VISIBLE);
            btnConfirm.setOnClickListener(v -> {
                // 更新保存的颜色为当前选择的颜色
                selectedColor = currentColor;
                
                if (listener != null) {
                    listener.onColorSelected(currentColor);
                }
                dismiss();
            });
        }

        // 取消按钮 - 恢复初始颜色并关闭
        if (btnCancel != null) {
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setOnClickListener(v -> {
                // 恢复初始颜色（如果颜色改变了）
                if (currentColor != initialColor && colorChangedListener != null) {
                    colorChangedListener.onColorChanged(initialColor);
                }
                dismiss();
            });
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            // 获取屏幕尺寸
            android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;
            
            // 左右分栏布局需要更宽的空间
            boolean isLandscape = screenWidth > screenHeight;
            
            int dialogWidth = isLandscape ? 
                (int) (screenWidth * 0.7f) : // 横屏：70% 屏幕宽度
                (int) (screenWidth * 0.95f);    // 竖屏：95% 屏幕宽度
            
            // 限制最大宽度（左右分栏需要较宽，增加最大宽度限制）
            int maxWidth = (int) (600 * displayMetrics.density); 
            dialogWidth = Math.min(dialogWidth, maxWidth);
            
            // 使用 WRAP_CONTENT 让内容自适应
            getDialog().getWindow().setLayout(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            
            // 获取根视图并测量实际内容高度
            View rootView = getView();
            if (rootView != null) {
                // 强制布局测量
                rootView.measure(
                    View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                
                int measuredHeight = rootView.getMeasuredHeight();
                int maxHeight = isLandscape ?
                    (int) (screenHeight * 0.95f) : // 横屏：70% 屏幕高度
                    (int) (screenHeight * 0.8f);   // 竖屏：75% 屏幕高度
                
                // 如果内容高度超过最大高度，使用最大高度；否则使用实际高度
                int finalHeight = Math.min(measuredHeight, maxHeight);
                
                // 设置弹窗高度
                getDialog().getWindow().setLayout(
                    dialogWidth,
                    finalHeight
                );
            }

            // 使用透明背景让布局的圆角可见
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }


    private void copyHexToClipboard() {
        if (tvHexDisplay != null && tvHexDisplay.getText() != null) {
            String hexValue = tvHexDisplay.getText().toString();
            if (!hexValue.isEmpty()) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                    requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("HEX Color", hexValue);
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(requireContext(), "已复制: " + hexValue, 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void copyRgbToClipboard() {
        if (tvRgbDisplay != null && tvRgbDisplay.getText() != null) {
            String rgbValue = tvRgbDisplay.getText().toString();
            if (!rgbValue.isEmpty()) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                    requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("RGB Color", "RGB(" + rgbValue + ")");
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(requireContext(), "已复制: RGB(" + rgbValue + ")", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initPresetColors(android.content.Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = (int) (32 * density);
        int margin = (int) (4 * density);
        
        for (int color : presetColors) {
            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(context);
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(margin, margin, margin, margin);
            btn.setLayoutParams(params);
            
            // 设置圆角矩形背景
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(8 * density); // 8dp 圆角
            drawable.setColor(color);
            // 添加淡淡的描边以区分白色背景
            if (color == 0xFFFFFFFF) {
                drawable.setStroke((int) (1 * density), 0xFFE0E0E0);
            }
            
            // 清除默认的 tint，否则背景色会被覆盖
            btn.setBackgroundTintList(null);
            btn.setBackground(drawable);
            // 移除默认阴影和内边距
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setStateListAnimator(null);
            
            btn.setOnClickListener(v -> {
                currentColor = color;
                colorPicker.setColor(currentColor);
                updateColorPreview(currentColor);
                updateInputsFromColor(currentColor);
            });
            
            gridPresets.addView(btn);
        }
    }

    private void updateColorPreview(int color) {
        // 更新预览颜色块（实时变化）
        int opaqueColor = color | 0xFF000000;
        if (colorPreview != null) {
            if (colorPreview instanceof com.google.android.material.card.MaterialCardView) {
                ((com.google.android.material.card.MaterialCardView) colorPreview).setCardBackgroundColor(opaqueColor);
            } else {
                colorPreview.setBackgroundColor(opaqueColor);
            }
        }
    }
    
    // 移除了 updateSelectedColorBlock，因为新布局不再区分当前和选中，直接显示一个大的预览块


    private void updateInputsFromColor(int color) {
        // 更新HEX显示
        if (tvHexDisplay != null) {
            int rgbColor = color & 0x00FFFFFF;
            tvHexDisplay.setText(String.format("#%06X", rgbColor));
        }

        // 更新RGB显示
        if (tvRgbDisplay != null) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            tvRgbDisplay.setText(String.format("%d, %d, %d", r, g, b));
        }
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.colorChangedListener = listener;
    }

    /**
     * 设置对话框标题
     * @param title 标题文本
     */
    public void setTitle(String title) {
        this.dialogTitle = title;
        // 如果视图已经创建，立即更新标题
        if (tvTitle != null && title != null) {
            tvTitle.setText(title);
        }
    }


}
