package com.app.ralib.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralib.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 现代化游戏文件浏览器组件
 * 专门用于浏览游戏目录中的 DLL 和 EXE 文件
 */
public class GameFileBrowser extends LinearLayout {
    private static final String TAG = "GameFileBrowser";
    
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private File currentDirectory;
    private OnFileSelectedListener listener;
    
    public interface OnFileSelectedListener {
        void onFileSelected(File file);
    }
    
    public GameFileBrowser(Context context) {
        super(context);
        init(context);
    }
    
    public GameFileBrowser(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public GameFileBrowser(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.game_file_browser, this, true);
        
        recyclerView = findViewById(R.id.fileRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);
    }
    
    /**
     * 设置起始目录
     */
    public void setDirectory(File directory) {
        if (directory != null && directory.exists() && directory.isDirectory()) {
            this.currentDirectory = directory;
            loadFiles(directory);
        }
    }
    
    /**
     * 设置起始目录路径
     */
    public void setDirectory(String path) {
        if (path != null && !path.isEmpty()) {
            setDirectory(new File(path));
        }
    }
    
    /**
     * 设置文件选择监听器
     */
    public void setOnFileSelectedListener(OnFileSelectedListener listener) {
        this.listener = listener;
    }
    
    /**
     * 加载目录文件
     */
    private void loadFiles(File directory) {
        List<FileItem> items = new ArrayList<>();
        
        // 添加"返回上级"选项（如果不是根目录）
        File parent = directory.getParentFile();
        if (parent != null && parent.exists()) {
            items.add(new FileItem(parent, true, true)); // 标记为返回上级
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            // 先添加文件夹
            List<File> directories = new ArrayList<>();
            List<File> exeFiles = new ArrayList<>();
            
            for (File file : files) {
                if (file.isDirectory()) {
                    directories.add(file);
                } else if (isExecutableFile(file)) {
                    exeFiles.add(file);
                }
            }
            
            // 排序
            Collections.sort(directories, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            Collections.sort(exeFiles, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            
            // 添加到列表
            for (File dir : directories) {
                items.add(new FileItem(dir, true, false));
            }
            for (File file : exeFiles) {
                items.add(new FileItem(file, false, false));
            }
        }
        
        adapter.setItems(items);
    }
    
    /**
     * 检查是否是可执行文件
     */
    private boolean isExecutableFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".dll") || name.endsWith(".exe");
    }
    
    /**
     * 文件项数据类
     */
    private static class FileItem {
        final File file;
        final boolean isDirectory;
        final boolean isParent; // 是否是"返回上级"项
        
        FileItem(File file, boolean isDirectory) {
            this(file, isDirectory, false);
        }
        
        FileItem(File file, boolean isDirectory, boolean isParent) {
            this.file = file;
            this.isDirectory = isDirectory;
            this.isParent = isParent;
        }
    }
    
    /**
     * 文件适配器
     */
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<FileItem> items = new ArrayList<>();
        
        void setItems(List<FileItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_game_file, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileItem item = items.get(position);
            holder.bind(item);
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView iconView;
            private final TextView nameView;
            private final ImageView arrowView;
            
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.fileIcon);
                nameView = itemView.findViewById(R.id.fileName);
                arrowView = itemView.findViewById(R.id.fileArrow);
            }
            
            void bind(FileItem item) {
                if (item.isParent) {
                    // 返回上级目录项 - 使用向上箭头
                    nameView.setText("返回上级");
                    iconView.setImageResource(R.drawable.ralib_ic_arrow_up);
                    arrowView.setVisibility(GONE);
                    itemView.setOnClickListener(v -> {
                        currentDirectory = item.file;
                        loadFiles(item.file);
                    });
                } else if (item.isDirectory) {
                    // 普通目录
                    nameView.setText(item.file.getName());
                    iconView.setImageResource(R.drawable.ralib_ic_folder);
                    arrowView.setVisibility(VISIBLE);
                    itemView.setOnClickListener(v -> {
                        currentDirectory = item.file;
                        loadFiles(item.file);
                    });
                } else {
                    // 文件
                    nameView.setText(item.file.getName());
                    iconView.setImageResource(R.drawable.ralib_ic_file);
                    arrowView.setVisibility(GONE);
                    itemView.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onFileSelected(item.file);
                        }
                    });
                }
            }
        }
    }
}

