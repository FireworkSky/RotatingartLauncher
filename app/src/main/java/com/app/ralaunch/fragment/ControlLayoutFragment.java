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

    private ControlLayoutManager layoutManager;
    private List<ControlLayout> layouts;
    private ControlLayoutAdapter adapter;
    private RecyclerView recyclerView;
    private ExtendedFloatingActionButton fabAddLayout;
    private LinearLayout emptyState;
    private Toolbar toolbar;

    private OnControlLayoutBackListener backListener;

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
        emptyState = view.findViewById(R.id.emptyState);

        toolbar.setNavigationOnClickListener(v -> {
            if (backListener != null) {
                backListener.onControlLayoutBack();
            }
        });

        fabAddLayout.setOnClickListener(v -> showAddLayoutDialog());

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

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("新建控制布局")
                .setView(dialogView)
                .setPositiveButton("创建", (dialog, which) -> {
                    String layoutName = editText.getText().toString().trim();
                    if (!layoutName.isEmpty()) {
                        createNewLayout(layoutName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createNewLayout(String name) {
        // 检查名称是否已存在
        for (ControlLayout layout : layouts) {
            if (layout.getName().equals(name)) {
                Toast.makeText(getContext(), "布局名称已存在", Toast.LENGTH_SHORT).show();
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
        // 使用新的编辑器Activity
        Intent intent = new Intent(getActivity(), com.app.ralaunch.controls.editor.ControlEditorActivity.class);
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
                .setTitle("重命名布局")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(layout.getName())) {
                        // 检查名称是否已存在
                        for (ControlLayout l : layouts) {
                            if (l.getName().equals(newName)) {
                                Toast.makeText(getContext(), "布局名称已存在", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        layout.setName(newName);
                        layoutManager.saveLayout(layout);
                        layouts = layoutManager.getLayouts();
                        adapter.updateLayouts(layouts);
                        Toast.makeText(getContext(), "布局已重命名", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onLayoutDuplicate(ControlLayout layout) {
        String newName = layout.getName() + " (副本)";
        int counter = 1;
        while (layoutExists(newName)) {
            counter++;
            newName = layout.getName() + " (副本 " + counter + ")";
        }

        ControlLayout duplicate = new ControlLayout(newName);
        duplicate.setElements(new ArrayList<>(layout.getElements()));
        layoutManager.addLayout(duplicate);
        layouts = layoutManager.getLayouts();
        adapter.updateLayouts(layouts);
        updateEmptyState();
        Toast.makeText(getContext(), "布局已复制", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLayoutSetDefault(ControlLayout layout) {
        layoutManager.setCurrentLayout(layout.getName());
        adapter.setDefaultLayoutId(layout.getName());
        Toast.makeText(getContext(), "已设为默认布局", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLayoutExport(ControlLayout layout) {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, layout.getName() + ".json");
            startActivityForResult(intent, REQUEST_CODE_EXPORT_LAYOUT);
        } catch (Exception e) {
            Toast.makeText(getContext(), "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLayoutDelete(ControlLayout layout) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除布局")
                .setMessage("确定要删除布局 \"" + layout.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    layoutManager.removeLayout(layout.getName());
                    layouts = layoutManager.getLayouts();
                    adapter.updateLayouts(layouts);
                    updateEmptyState();
                    Toast.makeText(getContext(), "布局已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
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
}