package com.app.ralaunch.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.app.ralaunch.adapter.ControlLayoutAdapter;
import com.app.ralaunch.utils.AppLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 控制布局管理Fragment
 *
 * 提供控制布局的管理界面（Material 3 风格）：
 * - 显示所有保存的控制布局
 * - 创建新的控制布局
 * - 编辑、重命名、复制布局
 * - 设置默认布局
 * - 导出和删除布局
 * - 跳转到布局编辑器
 *
 * 使用 ControlLayoutManager 管理布局数据
 */
public class ControlLayoutFragment extends Fragment implements ControlLayoutAdapter.OnLayoutClickListener {

    private static final int REQUEST_CODE_EDIT_LAYOUT = 1001;
    private static final int REQUEST_CODE_EXPORT_LAYOUT = 1002;
    private static final int REQUEST_CODE_IMPORT_LAYOUT = 1003;

    private ControlLayoutManager layoutManager;
    private List<ControlLayout> layouts;
    private ControlLayoutAdapter adapter;
    private RecyclerView recyclerView;
    private ExtendedFloatingActionButton fabAddLayout;
    private com.google.android.material.button.MaterialButton btnImportLayout;
    private com.google.android.material.button.MaterialButton btnImportPreset;
    private LinearLayout emptyState;
    private Toolbar toolbar;
    private com.google.android.material.button.MaterialButton btnLayoutSettings;
    private ControlLayoutSettingsDialog mSettingsDialog;

    private OnControlLayoutBackListener backListener;
    private ControlLayout mExportingLayout; // 保存要导出的布局

    public interface OnControlLayoutBackListener {
        void onControlLayoutBack();
    }

    public void setOnControlLayoutBackListener(OnControlLayoutBackListener listener) {
        this.backListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control_layout, container, false);

        layoutManager = new ControlLayoutManager(requireContext());
        layouts = layoutManager.getLayouts();

        initUI(view);
        setupRecyclerView();

        return view;
    }

    private void initUI(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerView);
        fabAddLayout = view.findViewById(R.id.fabAddLayout);
        btnImportLayout = view.findViewById(R.id.btnImportLayout);
        btnImportPreset = view.findViewById(R.id.btnImportPreset);
        emptyState = view.findViewById(R.id.emptyState);
        btnLayoutSettings = view.findViewById(R.id.btn_layout_settings);

        toolbar.setNavigationOnClickListener(v -> {
            if (backListener != null) {
                backListener.onControlLayoutBack();
            }
        });

        fabAddLayout.setOnClickListener(v -> showAddLayoutDialog());
        
        // 导入布局按钮
        btnImportLayout.setOnClickListener(v -> importLayoutFromFile());
        
        // 导入预设按钮
        btnImportPreset.setOnClickListener(v -> showImportPresetDialog());

        // 初始化布局设置侧边弹窗
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        // 使用 CoordinatorLayout 作为父容器（fragment_control_layout 的根布局）
        ViewGroup rootView = (ViewGroup) view;
        mSettingsDialog = new ControlLayoutSettingsDialog(requireContext(), rootView, screenWidth);
        mSettingsDialog.setOnMenuItemClickListener(new ControlLayoutSettingsDialog.OnMenuItemClickListener() {
            @Override
            public void onImportLayout() {
                importLayoutFromFile();
            }

            @Override
            public void onImportPreset() {
                showImportPresetDialog();
            }
        });

        // 布局设置按钮
        btnLayoutSettings.setOnClickListener(v -> {
            if (mSettingsDialog != null) {
                mSettingsDialog.show();
            }
        });

        updateEmptyState();
    }

    private void setupRecyclerView() {
        adapter = new ControlLayoutAdapter(layouts, this);
        adapter.setDefaultLayoutId(layoutManager.getCurrentLayoutName());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void showAddLayoutDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_new_layout, null);
        EditText editText = dialogView.findViewById(R.id.layout_name_edit);

        // 设置默认名称
        String defaultName = getString(R.string.control_new_layout);

        // 如果名称已存在，添加数字后缀
        String finalName = defaultName;
        int counter = 1;
        while (layoutExists(finalName)) {
            counter++;
            finalName = defaultName + " " + counter;
        }

        editText.setText(finalName);
        editText.selectAll();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.control_create_layout))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.control_create), (dialog, which) -> {
                    String layoutName = editText.getText().toString().trim();
                    if (!layoutName.isEmpty()) {
                        createNewLayout(layoutName);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void createNewLayout(String name) {
        // 检查名称是否已存在
        for (ControlLayout layout : layouts) {
            if (layout.getName().equals(name)) {
                Toast.makeText(getContext(), getString(R.string.control_layout_name_exists), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        ControlLayout newLayout = new ControlLayout(name);
        layoutManager.addLayout(newLayout);
        layouts = layoutManager.getLayouts();
        adapter.updateLayouts(layouts);
        updateEmptyState();

        // 打开编辑界面
        openLayoutEditor(newLayout);
    }

    private void openLayoutEditor(ControlLayout layout) {
        // 使用新的编辑器Activity，传递布局名称
        Intent intent = new Intent(getActivity(), com.app.ralaunch.controls.editor.ControlEditorActivity.class);
        intent.putExtra("layout_name", layout.getName());
        startActivityForResult(intent, REQUEST_CODE_EDIT_LAYOUT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_EDIT_LAYOUT && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getBooleanExtra("return_to_main", false)) {
                // 编辑器请求返回主界面，关闭当前Fragment
                if (backListener != null) {
                    backListener.onControlLayoutBack();
                }
            }
        } else if (requestCode == REQUEST_CODE_EXPORT_LAYOUT && resultCode == android.app.Activity.RESULT_OK) {
            // 处理导出布局
            if (data != null && data.getData() != null && mExportingLayout != null) {
                exportLayoutToFile(data.getData(), mExportingLayout);
                mExportingLayout = null; // 清除引用
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_LAYOUT && resultCode == android.app.Activity.RESULT_OK) {
            // 处理导入布局
            if (data != null && data.getData() != null) {
                importLayoutFromUri(data.getData());
            }
        }
    }
    
    /**
     * 将布局导出到文件
     */
    private void exportLayoutToFile(Uri uri, ControlLayout layout) {
        try {
            // 获取屏幕尺寸用于坐标转换
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            
            // 将 ControlLayout 转换为 ControlConfig
            com.app.ralaunch.controls.ControlConfig config = new com.app.ralaunch.controls.ControlConfig();
            config.name = layout.getName();
            config.version = 1;
            config.controls = new java.util.ArrayList<>();
            
            // 将 ControlElement 转换为 ControlData
            for (ControlElement element : layout.getElements()) {
                com.app.ralaunch.controls.ControlData data = 
                    com.app.ralaunch.controls.ControlDataConverter.elementToData(element, screenWidth, screenHeight);
                if (data != null) {
                    config.controls.add(data);
                }
            }
            
            // 将 ControlConfig 转换为 JSON
            String json = config.toJson();
            
            // 写入文件
            OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(json.getBytes("UTF-8"));
                outputStream.close();
                Toast.makeText(getContext(), getString(R.string.control_export_success), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), getString(R.string.control_export_failed_write), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.control_export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 从编辑器返回时刷新列表
        if (layoutManager != null) {
            layouts = layoutManager.getLayouts();
            if (adapter != null) {
                adapter.updateLayouts(layouts);
            }
        }

    }

    private void updateEmptyState() {
        if (layouts.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLayoutClick(ControlLayout layout) {
        openLayoutEditor(layout);
    }

    @Override
    public void onLayoutEdit(ControlLayout layout) {
        openLayoutEditor(layout);
    }

    @Override
    public void onLayoutRename(ControlLayout layout) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_new_layout, null);
        EditText editText = dialogView.findViewById(R.id.layout_name_edit);
        editText.setText(layout.getName());
        editText.selectAll();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.control_rename_layout))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(layout.getName())) {
                        // 检查名称是否已存在
                        for (ControlLayout l : layouts) {
                            if (l.getName().equals(newName)) {
                                Toast.makeText(getContext(), getString(R.string.control_layout_name_exists), Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        layout.setName(newName);
                        layoutManager.saveLayout(layout);
                        layouts = layoutManager.getLayouts();
                        adapter.updateLayouts(layouts);
                        Toast.makeText(getContext(), getString(R.string.control_layout_renamed), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    @Override
    public void onLayoutDuplicate(ControlLayout layout) {
        String newName = layout.getName() + " " + getString(R.string.control_layout_copy_suffix);
        int counter = 1;
        while (layoutExists(newName)) {
            counter++;
            newName = layout.getName() + " " + getString(R.string.control_layout_copy_suffix_numbered, counter);
        }

        ControlLayout duplicate = new ControlLayout(newName);
        duplicate.setElements(new ArrayList<>(layout.getElements()));
        layoutManager.addLayout(duplicate);
        layouts = layoutManager.getLayouts();
        adapter.updateLayouts(layouts);
        updateEmptyState();
        Toast.makeText(getContext(), getString(R.string.control_layout_duplicated), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLayoutSetDefault(ControlLayout layout) {
        layoutManager.setCurrentLayout(layout.getName());
        adapter.setDefaultLayoutId(layout.getName());
        Toast.makeText(getContext(), getString(R.string.control_set_as_default), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLayoutExport(ControlLayout layout) {
        try {
            // 保存要导出的布局
            mExportingLayout = layout;
            
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, layout.getName() + ".json");
            startActivityForResult(intent, REQUEST_CODE_EXPORT_LAYOUT);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.control_export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLayoutDelete(ControlLayout layout) {
        String layoutName = layout.getName();
        
        // 检查是否是默认布局，给出警告但允许删除
        boolean isDefaultLayout = getString(R.string.control_layout_keyboard_mode).equals(layoutName) || 
                                  getString(R.string.control_layout_gamepad_mode).equals(layoutName);
        String message = isDefaultLayout 
            ? getString(R.string.control_delete_default_layout_confirm, layoutName)
            : getString(R.string.control_delete_layout_confirm, layoutName);
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.control_delete_layout))
                .setMessage(message)
                .setPositiveButton(getString(R.string.control_delete), (dialog, which) -> {
                    layoutManager.removeLayout(layoutName);
                    // 重新加载布局列表
                    layouts = layoutManager.getLayouts();
                    adapter.updateLayouts(layouts);
                    updateEmptyState();
                    Toast.makeText(getContext(), getString(R.string.control_layout_deleted), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private boolean layoutExists(String name) {
        for (ControlLayout layout : layouts) {
            if (layout.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 导入布局（从文件选择器）
     */
    private void importLayoutFromFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json"});
        startActivityForResult(intent, REQUEST_CODE_IMPORT_LAYOUT);
    }

    /**
     * 从URI导入布局
     */
    private void importLayoutFromUri(Uri uri) {
        try {
            // 读取文件内容
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(getContext(), getString(R.string.control_cannot_read_file), Toast.LENGTH_SHORT).show();
                return;
            }

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line).append("\n");
            }
            reader.close();
            inputStream.close();

            String json = jsonBuilder.toString();

            // 解析 JSON 配置（使用统一的 loadFromJson 方法，确保正确处理空字符串）
            com.app.ralaunch.controls.ControlConfig config = com.app.ralaunch.controls.ControlConfig.loadFromJson(json);

            if (config == null || config.controls == null || config.controls.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.control_layout_file_invalid), Toast.LENGTH_SHORT).show();
                return;
            }

            // 生成唯一的布局名称
            String defaultName = getString(R.string.control_layout_imported);
            String layoutName = config.name != null ? config.name : defaultName;
            int counter = 1;
            while (layoutExists(layoutName)) {
                counter++;
                layoutName = (config.name != null ? config.name : defaultName) + " " + counter;
            }

            // 获取屏幕尺寸用于坐标转换
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            // 创建新布局并添加控件
            ControlLayout newLayout = new ControlLayout(layoutName);
            for (com.app.ralaunch.controls.ControlData data : config.controls) {
                ControlElement element = com.app.ralaunch.controls.ControlDataConverter.dataToElement(data, screenWidth, screenHeight);
                if (element != null) {
                    newLayout.addElement(element);
                }
            }

            // 保存布局
            layoutManager.addLayout(newLayout);
            layouts = layoutManager.getLayouts();
            adapter.updateLayouts(layouts);
            updateEmptyState();

            Toast.makeText(getContext(), getString(R.string.control_import_success, layoutName), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.control_import_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示导入预设配置对话框
     */
    private void showImportPresetDialog() {
        String[] presetNames = {
            getString(R.string.control_layout_preset_keyboard),
            getString(R.string.control_layout_preset_gamepad)
        };
        String[] presetFiles = {"default_layout.json", "gamepad_layout.json"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.control_select_preset))
                .setItems(presetNames, (dialog, which) -> {
                    importPresetLayout(presetFiles[which], presetNames[which]);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    /**
     * 导入预设配置文件
     */
    private void importPresetLayout(String fileName, String presetName) {
        try {
            // 从 assets 读取配置文件
            java.io.InputStream is = requireContext().getAssets().open("controls/" + fileName);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");

            // 解析 JSON 配置 - 统一使用 ControlConfig
            Gson gson = new Gson();
            com.app.ralaunch.controls.ControlConfig config = gson.fromJson(json, com.app.ralaunch.controls.ControlConfig.class);

            if (config == null || config.controls == null || config.controls.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.control_preset_file_invalid), Toast.LENGTH_SHORT).show();
                return;
            }

            // 生成唯一的布局名称
            String layoutName = presetName;
            int counter = 1;
            while (layoutExists(layoutName)) {
                counter++;
                layoutName = presetName + " " + counter;
            }

            // 获取屏幕尺寸用于坐标转换
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            // 创建新布局并添加控件 - 统一使用 ControlDataConverter
            ControlLayout newLayout = new ControlLayout(layoutName);
            for (com.app.ralaunch.controls.ControlData data : config.controls) {
                ControlElement element = com.app.ralaunch.controls.ControlDataConverter.dataToElement(data, screenWidth, screenHeight);
                if (element != null) {
                    newLayout.addElement(element);
                }
            }

            // 保存布局
            layoutManager.addLayout(newLayout);
            layouts = layoutManager.getLayouts();
            adapter.updateLayouts(layouts);
            updateEmptyState();

            Toast.makeText(getContext(), getString(R.string.control_preset_imported, layoutName), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.control_import_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

}