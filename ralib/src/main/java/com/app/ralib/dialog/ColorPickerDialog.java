package com.app.ralib.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
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
    private View colorPreview;
    private EditText etRed, etGreen, etBlue, etHex;
    private android.widget.SeekBar alphaSeekBar;
    private TextView tvAlphaValue;
    private Button btnConfirm, btnCancel;

    private int currentColor = 0xFF4CAF50; // 默认绿色（不透明）
    private int initialColor = 0xFF4CAF50; // 初始颜色，用于取消时恢复
    private OnColorSelectedListener listener;

    private boolean isUpdatingInputs = false;

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public ColorPickerDialog() {
    }

    public static ColorPickerDialog newInstance(int initialColor) {
        ColorPickerDialog dialog = new ColorPickerDialog();
        Bundle args = new Bundle();
        args.putInt("color", initialColor);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置对话框样式为浮动对话框
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
        if (getArguments() != null) {
            currentColor = getArguments().getInt("color", 0xFF4CAF50);
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

        // 初始化视图
        colorPicker = view.findViewById(R.id.colorPicker);
        colorPreview = view.findViewById(R.id.colorPreview);
        etRed = view.findViewById(R.id.etRed);
        etGreen = view.findViewById(R.id.etGreen);
        etBlue = view.findViewById(R.id.etBlue);
        etHex = view.findViewById(R.id.etHex);
        alphaSeekBar = view.findViewById(R.id.alphaSeekBar);
        tvAlphaValue = view.findViewById(R.id.tvAlphaValue);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnCancel = view.findViewById(R.id.btnCancel);

        android.widget.ImageButton btnClose = view.findViewById(R.id.btnClose);

        // 设置初始颜色
        colorPicker.setColor(currentColor);
        updateColorPreview(currentColor);
        updateInputsFromColor(currentColor);

        // 颜色选择器监听 - 仅更新UI，不立即保存
        colorPicker.setOnColorChangedListener(color -> {
            currentColor = color;
            updateColorPreview(color);
            updateInputsFromColor(color);
        });

        // RGB 输入监听
        TextWatcher rgbWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingInputs) {
                    updateColorFromRGB();
                }
            }
        };

        etRed.addTextChangedListener(rgbWatcher);
        etGreen.addTextChangedListener(rgbWatcher);
        etBlue.addTextChangedListener(rgbWatcher);

        // HEX 输入监听
        etHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingInputs) {
                    updateColorFromHex();
                }
            }
        });

        // 透明度滑动条监听
        if (alphaSeekBar != null) {
            alphaSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    if (!isUpdatingInputs && fromUser) {
                        int alpha = progress;
                        int rgb = currentColor & 0x00FFFFFF;
                        currentColor = (alpha << 24) | rgb;

                        updateColorPreview(currentColor);
                        updateAlphaDisplay(alpha);

                        // 更新ColorPickerView的透明度
                        colorPicker.setColor(currentColor);
                    }
                }

                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
            });
        }

        // 预设颜色点击
        setupPresetColors(view);

        // 关闭按钮
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        // 确定按钮 - 保存并关闭
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onColorSelected(currentColor); // 保留透明度
                }
                dismiss();
            });
        }

        // 取消按钮 - 不保存，直接关闭
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dismiss());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            // 获取屏幕宽度
            android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int dialogWidth = (int) (displayMetrics.widthPixels * 0.85f); // 85% 屏幕宽度

            getDialog().getWindow().setLayout(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );

            // 使用透明背景让布局的圆角可见
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

            // 添加外边距
            getDialog().getWindow().getDecorView().setPadding(20, 20, 20, 20);
        }
    }

    private void setupPresetColors(View view) {
        // Pickr 风格的14个预设颜色（Material Design 调色板）
        int[] presetColors = {
            0xFFF44336, // Red
            0xFFE91E63, // Pink
            0xFF9C27B0, // Purple
            0xFF673AB7, // Deep Purple
            0xFF3F51B5, // Indigo
            0xFF2196F3, // Blue
            0xFF03A9F4, // Light Blue
            0xFF00BCD4, // Cyan
            0xFF009688, // Teal
            0xFF4CAF50, // Green
            0xFF8BC34A, // Light Green
            0xFFCDDC39, // Lime
            0xFFFFEB3B, // Yellow
            0xFFFF9800  // Orange
        };

        int[] presetIds = {
            R.id.presetColor1,  R.id.presetColor2,  R.id.presetColor3,  R.id.presetColor4,
            R.id.presetColor5,  R.id.presetColor6,  R.id.presetColor7,  R.id.presetColor8,
            R.id.presetColor9,  R.id.presetColor10, R.id.presetColor11, R.id.presetColor12,
            R.id.presetColor13, R.id.presetColor14
        };

        for (int i = 0; i < presetIds.length; i++) {
            final int color = presetColors[i];
            View preset = view.findViewById(presetIds[i]);
            if (preset != null) {
                // 设置预设颜色的背景
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);
                preset.setBackground(drawable);

                // 点击事件 - 仅更新UI，不立即保存
                preset.setOnClickListener(v -> {
                    currentColor = color;
                    colorPicker.setColor(color);
                    updateColorPreview(color);
                    updateInputsFromColor(color);
                });
            }
        }
    }

    private void updateColorPreview(int color) {
        // 更新小矩形颜色预览（保留透明度）
        if (colorPreview != null) {
            colorPreview.setBackgroundColor(color);
        }
    }

    private void updateAlphaDisplay(int alpha) {
        if (tvAlphaValue != null) {
            int percentage = (int) ((alpha / 255.0f) * 100);
            tvAlphaValue.setText(percentage + "%");
        }
    }

    private void updateInputsFromColor(int color) {
        isUpdatingInputs = true;

        int a = Color.alpha(color);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        etRed.setText(String.valueOf(r));
        etGreen.setText(String.valueOf(g));
        etBlue.setText(String.valueOf(b));

        // HEX 显示（#AARRGGBB 格式，包含透明度）
        etHex.setText(String.format("#%08X", color));

        // 更新透明度滑动条
        if (alphaSeekBar != null) {
            alphaSeekBar.setProgress(a);
        }
        updateAlphaDisplay(a);

        isUpdatingInputs = false;
    }

    private void updateColorFromRGB() {
        try {
            String rText = etRed.getText().toString();
            String gText = etGreen.getText().toString();
            String bText = etBlue.getText().toString();

            if (rText.isEmpty() || gText.isEmpty() || bText.isEmpty()) {
                return;
            }

            int r = Math.max(0, Math.min(255, Integer.parseInt(rText)));
            int g = Math.max(0, Math.min(255, Integer.parseInt(gText)));
            int b = Math.max(0, Math.min(255, Integer.parseInt(bText)));

            // 保留当前的透明度
            int alpha = Color.alpha(currentColor);
            int color = Color.argb(alpha, r, g, b);

            currentColor = color;
            colorPicker.setColor(color);
            updateColorPreview(color);

            // 同步 HEX 输入框
            isUpdatingInputs = true;
            etHex.setText(String.format("#%08X", color));
            isUpdatingInputs = false;

        } catch (NumberFormatException e) {
            // 忽略无效输入
        }
    }

    private void updateColorFromHex() {
        try {
            String hexText = etHex.getText().toString().trim();
            if (hexText.isEmpty()) {
                return;
            }

            if (!hexText.startsWith("#")) {
                hexText = "#" + hexText;
            }

            int color;
            if (hexText.length() == 7) {
                // #RRGGBB 格式，添加完全不透明的 Alpha
                color = Color.parseColor(hexText) | 0xFF000000;
            } else if (hexText.length() == 9) {
                // #AARRGGBB 格式
                color = (int) Long.parseLong(hexText.substring(1), 16);
            } else {
                return; // 无效格式
            }

            currentColor = color;
            colorPicker.setColor(color);
            updateColorPreview(color);

            // 同步 RGB 输入框和透明度滑动条
            isUpdatingInputs = true;
            int a = Color.alpha(color);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            etRed.setText(String.valueOf(r));
            etGreen.setText(String.valueOf(g));
            etBlue.setText(String.valueOf(b));
            if (alphaSeekBar != null) {
                alphaSeekBar.setProgress(a);
            }
            updateAlphaDisplay(a);
            isUpdatingInputs = false;

        } catch (IllegalArgumentException e) {
            // 忽略无效输入（包括 NumberFormatException）
        }
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }
}
