// ControlLayoutFragment.java
package com.app.ralaunch.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.model.ControlElement;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.app.ralaunch.adapter.ControlLayoutAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ControlLayoutFragment extends Fragment implements ControlLayoutAdapter.OnLayoutClickListener {

    private ControlLayoutManager layoutManager;
    private List<ControlLayout> layouts;
    private ControlLayoutAdapter adapter;
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddLayout;
    private TextView emptyText;

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
        recyclerView = view.findViewById(R.id.control_layout_recycler_view);
        fabAddLayout = view.findViewById(R.id.fab_add_layout);
        emptyText = view.findViewById(R.id.empty_layout_text);

        ImageButton backButton = view.findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onControlLayoutBack();
            }
        });

        fabAddLayout.setOnClickListener(v -> showAddLayoutDialog());

        updateEmptyState();
    }

    private void setupRecyclerView() {
        adapter = new ControlLayoutAdapter(layouts, this);
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
        ControlEditorFragment editorFragment = new ControlEditorFragment();
        editorFragment.setControlLayout(layout);
        editorFragment.setOnEditorBackListener(() -> {
            // 返回时刷新列表
            layouts = layoutManager.getLayouts();
            adapter.updateLayouts(layouts);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, this)
                    .commit();
        });

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, editorFragment)
                .addToBackStack("editor")
                .commit();
    }

    private void updateEmptyState() {
        if (layouts.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLayoutClick(ControlLayout layout) {
        openLayoutEditor(layout);
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

    @Override
    public void onLayoutSetDefault(ControlLayout layout) {
        layoutManager.setCurrentLayout(layout.getName());
        adapter.updateLayouts(layouts);
        Toast.makeText(getContext(), "已设为默认布局: " + layout.getName(), Toast.LENGTH_SHORT).show();
    }
}