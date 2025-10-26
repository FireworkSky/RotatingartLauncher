// ControlEditorFragment.java
package com.app.ralaunch.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.app.ralaunch.view.ControlEditorView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ControlEditorFragment extends Fragment {

    private ControlLayout controlLayout;
    private ControlLayoutManager layoutManager;
    private ControlEditorView editorView;
    private Button btnSave, btnAddButton, btnAddJoystick, btnLoadDefault;
    private ImageButton backButton;
    private ImageButton settingsGear;

    private OnEditorBackListener backListener;

    public interface OnEditorBackListener {
        void onEditorBack();
    }
    public interface OnElementLongPressListener {
        void onElementLongPress(ControlElement element);
    }
    public void setOnEditorBackListener(OnEditorBackListener listener) {
        this.backListener = listener;
    }

    public void setControlLayout(ControlLayout layout) {
        this.controlLayout = layout;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control_editor, container, false);

        layoutManager = new ControlLayoutManager(requireContext());

        initUI(view);
        setupEditor();

        editorView.setOnElementLongPressListener(element -> {
            showElementPropertiesDialog(element);
        });

        return view;
    }

    private void initUI(View view) {
        editorView = view.findViewById(R.id.control_editor_view);
        backButton = view.findViewById(R.id.back_button);
        settingsGear = view.findViewById(R.id.settings_gear);

        backButton.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onEditorBack();
            }
        });

        settingsGear.setOnClickListener(v -> showSettingsMenu());

        setupEditor();
    }
    private void showSettingsMenu() {
        // 创建暗色主题的弹出菜单
        Context wrapper = new ContextThemeWrapper(requireContext(), R.style.PopupMenuDark);
        PopupMenu popupMenu = new PopupMenu(wrapper, settingsGear);
        popupMenu.getMenuInflater().inflate(R.menu.control_editor_menu, popupMenu.getMenu());

        // 设置菜单背景
        try {
            Field field = popupMenu.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popupMenu);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceShowIcon.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 设置菜单项点击监听
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_add_button) {
                addNewButton();
                return true;
            } else if (itemId == R.id.menu_add_joystick) {
                addNewJoystick();
                return true;
            } else if (itemId == R.id.menu_add_cross) {
                addNewCrossKey();
                return true;
            } else if (itemId == R.id.menu_load_default) {
                loadDefaultLayout();
                return true;
            } else if (itemId == R.id.menu_save_layout) {
                saveLayout();
                return true;
            } else if (itemId == R.id.menu_clear_all) {
                clearAllElements();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }
    private void addNewCrossKey() {
        ControlElement crossKey = new ControlElement(
                "cross_" + System.currentTimeMillis(),
                ControlElement.ElementType.CROSS_KEY,
                "方向键"
        );
        crossKey.setX(0.2f);
        crossKey.setY(0.6f);
        crossKey.setWidth(200);
        crossKey.setHeight(200);

        controlLayout.addElement(crossKey);
        editorView.setControlLayout(controlLayout);
        editorView.setSelectedElement(crossKey);

        showElementPropertiesDialog(crossKey);
    }

    private void clearAllElements() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogDark);
        builder.setTitle("清空所有控件")
                .setMessage("确定要清空所有控件吗？此操作不可撤销。")
                .setPositiveButton("清空", (dialog, which) -> {
                    controlLayout.getElements().clear();
                    editorView.setControlLayout(controlLayout);
                    editorView.invalidate();
                    Toast.makeText(getContext(), "已清空所有控件", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadDefaultLayout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogDark);
        builder.setTitle("加载默认布局")
                .setMessage("这将替换当前所有控件，确定要继续吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 创建默认布局
                    controlLayout.getElements().clear();

                    ControlElement crossKey = new ControlElement("cross", ControlElement.ElementType.CROSS_KEY, "方向键");
                    crossKey.setX(0.1f);
                    crossKey.setY(0.6f);
                    crossKey.setWidth(200);
                    crossKey.setHeight(200);
                    controlLayout.addElement(crossKey);

                    ControlElement jumpButton = new ControlElement("jump", ControlElement.ElementType.BUTTON, "跳跃");
                    jumpButton.setX(0.8f);
                    jumpButton.setY(0.6f);
                    jumpButton.setWidth(120);
                    jumpButton.setHeight(120);
                    jumpButton.setKeyCode(32);
                    controlLayout.addElement(jumpButton);

                    editorView.setControlLayout(controlLayout);
                    Toast.makeText(getContext(), "已加载默认布局", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private void setupEditor() {
        if (controlLayout != null) {
            editorView.setControlLayout(controlLayout);
        }
    }

    private void saveLayout() {
        if (controlLayout != null) {
            layoutManager.saveLayout(controlLayout);
            Toast.makeText(getContext(), "布局已保存", Toast.LENGTH_SHORT).show();
        }
    }

    private void addNewButton() {
        ControlElement newButton = new ControlElement(
                "button_" + System.currentTimeMillis(),
                ControlElement.ElementType.BUTTON,
                "新建按钮"
        );
        newButton.setX(0.5f);
        newButton.setY(0.5f);
        newButton.setWidth(100);
        newButton.setHeight(100);
        newButton.setKeyCode(32); // 默认空格键

        controlLayout.addElement(newButton);
        editorView.setControlLayout(controlLayout);
        editorView.setSelectedElement(newButton);

        showElementPropertiesDialog(newButton);
    }

    private void addNewJoystick() {
        ControlElement newJoystick = new ControlElement(
                "joystick_" + System.currentTimeMillis(),
                ControlElement.ElementType.JOYSTICK,
                "摇杆"
        );
        newJoystick.setX(0.2f);
        newJoystick.setY(0.7f);
        newJoystick.setWidth(150);
        newJoystick.setHeight(150);

        controlLayout.addElement(newJoystick);
        editorView.setControlLayout(controlLayout);
        editorView.setSelectedElement(newJoystick);

        showElementPropertiesDialog(newJoystick);
    }

    private void showElementPropertiesDialog(ControlElement element) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_element_properties, null);

        // 初始化对话框控件
        EditText editName = dialogView.findViewById(R.id.edit_element_name);
        EditText editWidth = dialogView.findViewById(R.id.edit_width);
        EditText editHeight = dialogView.findViewById(R.id.edit_height);
        Spinner spinnerKey1 = dialogView.findViewById(R.id.spinner_key1);
        Spinner spinnerKey2 = dialogView.findViewById(R.id.spinner_key2);
        Switch switchToggle = dialogView.findViewById(R.id.switch_toggle);
        Switch switchPassthrough = dialogView.findViewById(R.id.switch_passthrough);
        SeekBar seekOpacity = dialogView.findViewById(R.id.seek_opacity);
        TextView textOpacityValue = dialogView.findViewById(R.id.text_opacity_value);

        // 设置当前值
        editName.setText(element.getName());
        editWidth.setText(String.valueOf(element.getWidth()));
        editHeight.setText(String.valueOf(element.getHeight()));
        switchToggle.setChecked(element.isToggle());
        switchPassthrough.setChecked(element.isPassthrough());
        seekOpacity.setProgress((int)(element.getOpacity() * 100));
        textOpacityValue.setText((int)(element.getOpacity() * 100) + "%");

        // 设置不透明度变化监听
        seekOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textOpacityValue.setText(progress + "%");
                element.setOpacity(progress / 100.0f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 创建 AlertDialog 而不是 MaterialAlertDialogBuilder
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogDark);
        builder.setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    // 保存属性
                    element.setName(editName.getText().toString());
                    element.setWidth(Float.parseFloat(editWidth.getText().toString()));
                    element.setHeight(Float.parseFloat(editHeight.getText().toString()));
                    element.setToggle(switchToggle.isChecked());
                    element.setPassthrough(switchPassthrough.isChecked());

                    editorView.invalidate();
                    Toast.makeText(getContext(), "属性已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }


}