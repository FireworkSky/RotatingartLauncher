package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.activity.MainActivity;
import com.app.ralaunch.fragment.FragmentHelper;
import com.app.ralaunch.adapter.FileBrowserAdapter;
import com.app.ralaunch.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件浏览器Fragment
 * 
 * 提供文件系统浏览功能，用于选择游戏文件：
 * - 浏览文件夹和文件
 * - 上级目录导航
 * - 文件选择和确认
 * - 权限请求处理
 * - 当前路径显示
 * 
 * 支持选择 .zip 等游戏压缩包文件
 */
public class FileBrowserFragment extends BaseFragment implements FileBrowserAdapter.OnFileClickListener {

    // 模式常量
    public static final int MODE_SELECT_FILE = 0;  // 选择文件模式（默认）
    public static final int MODE_SELECT_ASSEMBLY = 1;  // 选择程序集模式（dll/exe）
    
    private int mode = MODE_SELECT_FILE;
    private OnFileSelectedListener fileSelectedListener;
    private OnAssemblySelectedListener assemblySelectedListener;
    private OnAddToGameListListener addToGameListListener;
    private OnBackListener backListener;

    // 界面控件
    private RecyclerView fileRecyclerView;
    private TextView currentPathText;
    private Button confirmButton;
    private LinearLayout emptyState;
    private TextView emptyText;
    private LinearLayout permissionDeniedState;
    private android.widget.EditText searchInput;
    private com.google.android.material.button.MaterialButton sortButton;

    // 文件相关
    private File currentDirectory;
    private File selectedFile;
    private List<String> allowedExtensions;
    private String fileType;

    // 文件列表
    private List<FileItem> fileList = new ArrayList<>();
    private List<FileItem> filteredFileList = new ArrayList<>();
    private FileBrowserAdapter fileAdapter;
    
    // 排序模式: 0=名称（默认）, 1=大小, 2=时间
    // 默认按文件名称排序（不区分大小写）
    private int sortMode = 0;
    
    // 权限请求监听器
    private OnPermissionRequestListener permissionRequestListener;

    // 权限请求监听器
    public interface OnPermissionRequestListener {
        void onPermissionRequest(MainActivity.PermissionCallback callback);
    }

    public interface OnFileSelectedListener {
        void onFileSelected(String filePath, String fileType);
    }
    
    public interface OnAssemblySelectedListener {
        void onAssemblySelected(String assemblyPath);
    }
    
    public interface OnAddToGameListListener {
        void onAddToGameList(String assemblyPath);
    }

    public interface OnBackListener {
        void onBack();
    }
    
    public void setMode(int mode) {
        this.mode = mode;
        if (mode == MODE_SELECT_ASSEMBLY) {
            // 程序集模式：只显示 dll/exe 文件
            setFileType("assembly", new String[]{".dll", ".exe"});
        }
    }

    public void setOnFileSelectedListener(OnFileSelectedListener listener) {
        this.fileSelectedListener = listener;
    }
    
    public void setOnAssemblySelectedListener(OnAssemblySelectedListener listener) {
        this.assemblySelectedListener = listener;
    }
    
    public void setOnAddToGameListListener(OnAddToGameListListener listener) {
        this.addToGameListListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }
    
    public void setOnPermissionRequestListener(OnPermissionRequestListener listener) {
        this.permissionRequestListener = listener;
    }

    public void setFileType(String fileType, String[] extensions) {
        this.fileType = fileType;
        this.allowedExtensions = Arrays.asList(extensions);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_browser, container, false);
        setupUI(view);

        // 检查权限并加载文件
        checkPermissionsAndLoadFiles();

        return view;
    }

    private void setupUI(View view) {
        // 返回按钮已移除，通过 NavigationRail 导航

        // 设置页面标题
        TextView pageTitle = view.findViewById(R.id.pageTitle);
        pageTitle.setText(getPageTitle());

        // 初始化控件
        fileRecyclerView = view.findViewById(R.id.fileRecyclerView);
        currentPathText = view.findViewById(R.id.currentPathText);
        confirmButton = view.findViewById(R.id.confirmButton);
        emptyState = view.findViewById(R.id.emptyState);
        emptyText = view.findViewById(R.id.emptyText);
        permissionDeniedState = view.findViewById(R.id.permissionDeniedState);
        searchInput = view.findViewById(R.id.searchInput);
        sortButton = view.findViewById(R.id.sortButton);

        // 设置 RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        fileRecyclerView.setLayoutManager(layoutManager);
        fileAdapter = new FileBrowserAdapter(filteredFileList, this);
        fileRecyclerView.setAdapter(fileAdapter);

        // 优化 RecyclerView 性能
        fileRecyclerView.setHasFixedSize(true);
        fileRecyclerView.setItemViewCacheSize(20);
        fileRecyclerView.setDrawingCacheEnabled(true);
        fileRecyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        
        // 搜索框监听
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFiles(s.toString());
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        
        // 排序按钮监听
        if (sortButton != null) {
            sortButton.setOnClickListener(v -> showSortMenu());
        }

        // 确认按钮
        confirmButton.setOnClickListener(v -> {
            if (selectedFile != null) {
                if (mode == MODE_SELECT_ASSEMBLY && assemblySelectedListener != null) {
                    assemblySelectedListener.onAssemblySelected(selectedFile.getAbsolutePath());
                } else if (fileSelectedListener != null) {
                    fileSelectedListener.onFileSelected(selectedFile.getAbsolutePath(), fileType);
                }
            }
        });

        // 权限拒绝状态的授权按钮
        Button grantPermissionButton = view.findViewById(R.id.grantPermissionButton);
        grantPermissionButton.setOnClickListener(v -> {
            if (permissionRequestListener != null) {
                permissionRequestListener.onPermissionRequest(new MainActivity.PermissionCallback() {
                    @Override public void onPermissionsGranted() { loadInitialDirectory(); hidePermissionDeniedState(); }
                    @Override public void onPermissionsDenied() { showPermissionDeniedState(); }
                });
            } else {
                checkPermissionsAndLoadFiles();
            }
        });

        // 初始状态
        updateConfirmButton();
    }

    private String getPageTitle() {
        if (fileType == null) {
            return getString(R.string.filebrowser_select_file_generic);
        }
        switch (fileType) {
            case "game":
                return getString(R.string.filebrowser_select_game_file);
            case "modloader":
                return getString(R.string.filebrowser_select_modloader_file);
            default:
                return getString(R.string.filebrowser_select_file_generic);
        }
    }

    /**
     * 检查权限并加载文件
     * 注意：此方法只检查权限状态，不会主动请求权限
     * 如果没有权限，会显示权限拒绝状态，用户需要主动点击授权按钮
     */
    private void checkPermissionsAndLoadFiles() {
        MainActivity activity = FragmentHelper.getMainActivity(this);
        if (activity != null) {
            if (activity.hasRequiredPermissions()) {
                // 已有权限，加载文件
                loadInitialDirectory();
                hidePermissionDeniedState();
            } else {
                // 没有权限，显示权限拒绝状态
                // 不自动请求权限，避免重复授权提示
                // 用户需要点击"授予权限"按钮来请求权限
                showPermissionDeniedState();
            }
        }
    }

    private void showPermissionDeniedState() {
        permissionDeniedState.setVisibility(View.VISIBLE);
        fileRecyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void hidePermissionDeniedState() {
        permissionDeniedState.setVisibility(View.GONE);
        fileRecyclerView.setVisibility(View.VISIBLE);
    }

    private void loadInitialDirectory() {
        // 从下载目录开始
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            loadDirectory(downloadsDir);
        } else {
            // 如果下载目录不存在，从根目录开始
            loadDirectory(Environment.getExternalStorageDirectory());
        }
    }

    private void loadDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        currentDirectory = directory;
        currentPathText.setText(directory.getAbsolutePath());

        fileList.clear();

        // 始终添加上级目录项（如果不是根目录）
        if (directory.getParentFile() != null && !isRootDirectory(directory)) {
            fileList.add(new FileItem("..", directory.getParentFile().getAbsolutePath(), true, true));
        }

        // 获取目录下的文件和文件夹
        File[] files = directory.listFiles();
        if (files != null && files.length > 0) {
            // 先添加文件夹
            for (File file : files) {
                if (file.isDirectory() && !file.isHidden()) {
                    fileList.add(new FileItem(file.getName(), file.getAbsolutePath(), true, false));
                }
            }

            // 再添加文件（只显示允许的文件类型）
            for (File file : files) {
                if (file.isFile() && !file.isHidden() && isFileAllowed(file)) {
                    fileList.add(new FileItem(file.getName(), file.getAbsolutePath(), false, false));
                }
            }
        }

        // 排序文件列表
        sortFileList();
        
        // 应用搜索过滤
        filterFiles(searchInput != null ? searchInput.getText().toString() : "");
        
        // 更新空状态
        updateEmptyState();
    }

    private boolean isRootDirectory(File directory) {
        File externalStorage = Environment.getExternalStorageDirectory();
        File[] externalStorages = getContext().getExternalFilesDirs(null);

        for (File storage : externalStorages) {
            if (storage != null && directory.getAbsolutePath().equals(storage.getParentFile().getAbsolutePath())) {
                return true;
            }
        }

        return directory.getAbsolutePath().equals("/") ||
                directory.getAbsolutePath().equals(externalStorage.getAbsolutePath());
    }

    private boolean isFileAllowed(File file) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return true;
        }

        String fileName = file.getName().toLowerCase();
        for (String ext : allowedExtensions) {
            if (fileName.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void updateEmptyState() {
        boolean hasParentDirectory = false;
        for (FileItem item : fileList) {
            if (item.isParentDirectory()) {
                hasParentDirectory = true;
                break;
            }
        }

        if (fileList.isEmpty() || (fileList.size() == 1 && hasParentDirectory)) {
            emptyState.setVisibility(View.VISIBLE);
            fileRecyclerView.setVisibility(View.GONE);
            if (hasParentDirectory) {
                emptyText.setText(getString(R.string.filebrowser_folder_empty));
            } else {
                emptyText.setText(getString(R.string.filebrowser_directory_empty));
            }
        } else {
            emptyState.setVisibility(View.GONE);
            fileRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateConfirmButton() {
        // 检查 Fragment 是否已 attach，避免崩溃
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        if (selectedFile != null) {
            confirmButton.setEnabled(true);
            confirmButton.setBackgroundTintList(getResources().getColorStateList(R.color.button_secondary));
            confirmButton.setText(getString(R.string.filebrowser_confirm_select, selectedFile.getName()));
        } else {
            confirmButton.setEnabled(false);
            confirmButton.setBackgroundTintList(getResources().getColorStateList(R.color.button_disabled));
            confirmButton.setText(getString(R.string.filebrowser_select_file));
        }
    }

    @Override
    public void onFileClick(FileItem fileItem) {
        if (fileItem.isDirectory()) {
            if (fileItem.isParentDirectory()) {
                // 返回上级目录
                loadDirectory(new File(fileItem.getPath()));
            } else {
                // 进入子目录
                loadDirectory(new File(fileItem.getPath()));
            }
            selectedFile = null;
        } else {
            // 选择文件
            selectedFile = new File(fileItem.getPath());
            fileAdapter.setSelectedFile(selectedFile.getAbsolutePath());
        }
        updateConfirmButton();
    }

    @Override
    public void onFileLongClick(FileItem fileItem) {
        // 长按 DLL/EXE 文件时显示操作菜单
        if (!fileItem.isDirectory()) {
            String fileName = fileItem.getName().toLowerCase();
            if (fileName.endsWith(".dll") || fileName.endsWith(".exe")) {
                showFileActionMenu(fileItem);
            }
        }
    }
    
    /**
     * 显示文件操作菜单
     */
    private void showFileActionMenu(FileItem fileItem) {
        String filePath = fileItem.getPath();
        String fileName = fileItem.getName();
        
        // 创建选项菜单
        java.util.List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new java.util.ArrayList<>();
        
        // 添加到游戏列表选项（仅在非程序集选择模式下显示）
        if (mode != MODE_SELECT_ASSEMBLY && addToGameListListener != null) {
            // File browser add to game list option removed
        }
        
        // 选择此文件选项
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "select_file", 
            getString(R.string.filebrowser_select_this_file), 
            getString(R.string.filebrowser_select_this_file_desc)
        ));
        
        if (options.isEmpty()) {
            return;
        }
        
        // 显示选项对话框
        new com.app.ralib.dialog.OptionSelectorDialog()
            .setTitle(getString(R.string.filebrowser_file_action, fileName))
            .setIcon(R.drawable.ic_file)
            .setOptions(options)
            .setOnOptionSelectedListener(value -> {
                if ("add_to_game_list".equals(value) && addToGameListListener != null) {
                    addToGameListListener.onAddToGameList(filePath);
                    com.app.ralaunch.utils.AppLogger.info("FileBrowserFragment", "添加到游戏列表: " + filePath);
                } else if ("select_file".equals(value)) {
                    // 直接选择文件（相当于点击）
                    onFileClick(fileItem);
                }
            })
            .show(getParentFragmentManager(), "file_action_menu");
    }
    
    /**
     * 排序文件列表
     */
    private void sortFileList() {
        if (fileList.isEmpty()) {
            return;
        }
        
        // 保存上级目录项
        FileItem parentItem = null;
        if (!fileList.isEmpty() && fileList.get(0).isParentDirectory()) {
            parentItem = fileList.remove(0);
        }
        
        // 分离文件夹和文件
        List<FileItem> directories = new ArrayList<>();
        List<FileItem> files = new ArrayList<>();
        
        for (FileItem item : fileList) {
            if (item.isDirectory()) {
                directories.add(item);
            } else {
                files.add(item);
            }
        }
        
        // 根据排序模式排序
        java.util.Comparator<FileItem> comparator;
        switch (sortMode) {
            case 1: // 按大小排序
                comparator = (a, b) -> {
                    File fileA = new File(a.getPath());
                    File fileB = new File(b.getPath());
                    return Long.compare(fileB.length(), fileA.length());
                };
                break;
            case 2: // 按时间排序
                comparator = (a, b) -> {
                    File fileA = new File(a.getPath());
                    File fileB = new File(b.getPath());
                    return Long.compare(fileB.lastModified(), fileA.lastModified());
                };
                break;
            default: // 按名称排序（默认，不区分大小写）
                comparator = (a, b) -> a.getName().compareToIgnoreCase(b.getName());
                break;
        }
        
        java.util.Collections.sort(directories, comparator);
        java.util.Collections.sort(files, comparator);
        
        // 重新组合列表
        fileList.clear();
        if (parentItem != null) {
            fileList.add(parentItem);
        }
        fileList.addAll(directories);
        fileList.addAll(files);
    }
    
    /**
     * 过滤文件
     */
    private void filterFiles(String query) {
        filteredFileList.clear();
        
        if (query == null || query.trim().isEmpty()) {
            filteredFileList.addAll(fileList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (FileItem item : fileList) {
                if (item.getName().toLowerCase().contains(lowerQuery)) {
                    filteredFileList.add(item);
                }
            }
        }
        
        fileAdapter.notifyDataSetChanged();
        updateEmptyState();
    }
    
    /**
     * 显示排序菜单
     */
    private void showSortMenu() {
        java.util.List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = java.util.Arrays.asList(
            new com.app.ralib.dialog.OptionSelectorDialog.Option("0", getString(R.string.filebrowser_sort_by_name), getString(R.string.filebrowser_sort_by_name_desc)),
            new com.app.ralib.dialog.OptionSelectorDialog.Option("1", getString(R.string.filebrowser_sort_by_size), getString(R.string.filebrowser_sort_by_size_desc)),
            new com.app.ralib.dialog.OptionSelectorDialog.Option("2", getString(R.string.filebrowser_sort_by_time), getString(R.string.filebrowser_sort_by_time_desc))
        );
        
        new com.app.ralib.dialog.OptionSelectorDialog()
            .setTitle(getString(R.string.filebrowser_sort_by))
            .setIcon(R.drawable.ic_sort)
            .setOptions(options)
            .setCurrentValue(String.valueOf(sortMode))
            .setOnOptionSelectedListener(value -> {
                sortMode = Integer.parseInt(value);
                sortFileList();
                filterFiles(searchInput.getText().toString());
                
                String sortName = sortMode == 0 ? getString(R.string.filebrowser_sort_name) : sortMode == 1 ? getString(R.string.filebrowser_sort_size) : getString(R.string.filebrowser_sort_time);
                showToast(getString(R.string.filebrowser_sorted_by, sortName));
            })
            .show(getParentFragmentManager(), "sort_options");
    }
}