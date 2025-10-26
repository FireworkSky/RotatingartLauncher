package com.app.ralaunch.model;

import com.app.ralaunch.R;

public class FileItem {
    private String name;
    private String path;
    private boolean isDirectory;
    private boolean isParentDirectory;

    public FileItem(String name, String path, boolean isDirectory, boolean isParentDirectory) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
        this.isParentDirectory = isParentDirectory;
    }

    // Getters
    public String getName() { return name; }
    public String getPath() { return path; }
    public boolean isDirectory() { return isDirectory; }
    public boolean isParentDirectory() { return isParentDirectory; }

    // 获取文件图标资源ID
    public int getIconResId() {
        if (isParentDirectory) {
            return R.drawable.ic_folder_up;
        } else if (isDirectory) {
            return R.drawable.ic_folder;
        } else {
            // 根据文件扩展名返回不同图标
            if (name.toLowerCase().endsWith(".sh")) {
                return R.drawable.ic_script;
            } else if (name.toLowerCase().endsWith(".zip")) {
                return R.drawable.ic_zip;
            } else {
                return R.drawable.ic_file;
            }
        }
    }
}