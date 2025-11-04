package com.app.ralaunch.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.app.ralaunch.R;
import com.app.ralaunch.utils.PageManager;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    private SettingsFragment.OnSettingsBackListener backListener;
    private PageManager pageManager;

    private ListView settingsCategoryListView;

    public interface OnSettingsBackListener {
        void onSettingsBack();
    }

    public void setOnSettingsBackListener(OnSettingsBackListener listener) {
        this.backListener = listener;
    }

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        setupUI(view);
        return view;
    }

    private void setupUI(View view) {
        pageManager = new PageManager(getChildFragmentManager(), R.id.settingsFragmentContainer);
        settingsCategoryListView = view.findViewById(R.id.settingsCategoryListView);

        // 工具栏返回按钮
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (backListener != null) {
                backListener.onSettingsBack();
            }
        });

        List<Map<String, Object>> categories = getCategories();

        SimpleAdapter adapter = new SimpleAdapter(
                this.getContext(),
                categories,
                R.layout.item_settings_category,
                new String[]{"icon", "category_name"},
                new int[]{R.id.icon, R.id.category_name}
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                // Ensure activated state reflects ListView checked state so the selector works even after recycling
                if (settingsCategoryListView != null) {
                    v.setActivated(settingsCategoryListView.isItemChecked(position));
                }
                return v;
            }
        };

        settingsCategoryListView.setAdapter(adapter);
        // Enable single choice so activated state works
        settingsCategoryListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        settingsCategoryListView.setOnItemClickListener((parent, v, position, id) -> {
            android.util.Log.d(TAG, "Category clicked: position=" + position + ", name=" + categories.get(position).get("category_name"));

            // Notify adapter to refresh views
            adapter.notifyDataSetChanged();

            Fragment selectedFragment = (Fragment) Objects.requireNonNull(categories.get(position).get("fragment"));
            pageManager.showPage(selectedFragment, (String) Objects.requireNonNull(categories.get(position).get("category_name")));
        });

        // 设置默认 fragment
        if (!categories.isEmpty()) {
            // set default checked item so initial appearance matches selection
            settingsCategoryListView.post(() -> {
                settingsCategoryListView.setItemChecked(0, true);
                adapter.notifyDataSetChanged();
            });
            pageManager.showPage(
                    (Fragment) Objects.requireNonNull(categories.get(0).get("fragment")),
                    (String) Objects.requireNonNull(categories.get(0).get("category_name")));
        }
    }

    private List<Map<String, Object>> getCategories() {
        List<Map<String, Object>> list = new ArrayList<>();

        // 第一项数据
        Map<String, Object> item1 = new HashMap<>();
        item1.put("icon", R.drawable.ic_settings); // 图片资源ID
        item1.put("category_name", "杂项");
        item1.put("fragment", new SettingsMiscFragment());
        list.add(item1);

        // 第二项数据
        Map<String, Object> item2 = new HashMap<>();
        item2.put("icon", R.drawable.ic_bug); // 图片资源ID
        item2.put("category_name", "开发者选项");
        item2.put("fragment", new SettingsDeveloperFragment());
        list.add(item2);

        // ... 可以继续添加更多数据
        return list;
    }
}