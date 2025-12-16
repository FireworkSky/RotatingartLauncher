package com.app.ralaunch.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.data.SettingsManager;
import com.app.ralaunch.R;
import com.app.ralaunch.adapter.FileBrowserAdapter;
import com.app.ralaunch.adapter.ModManagerFileAdapter;
import com.app.ralaunch.model.FileItem;
import com.app.ralaunch.utils.AppLogger;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import com.app.ralib.utils.StreamUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 游戏文件浏览器 Fragment
 * 
 * 提供游戏文件管理功能：
 * - 浏览 /storage/emulated/0/RALauncher 目录
 * - 移动/复制文件夹和文件
 * - 删除文件/文件夹
 * - 创建新文件夹
 * 
 * 使用 Material Design 3 风格，横屏布局
 */
public class ModManagerFragment extends BaseFragment implements FileBrowserAdapter.OnFileClickListener {
    
    private static final String TAG = "ModManagerFragment";
    private static final String DEFAULT_PATH = "/storage/emulated/0/RALauncher";
    private static final int REQUEST_CODE_PICK_DIR = 1002;
    private static final String KEY_VIEW_MODE = "game_file_browser_view_mode"; // "grid" or "list"
    private static final String VIEW_MODE_GRID = "grid";
    private static final String VIEW_MODE_LIST = "list";
    
    // 界面控件
    private RecyclerView fileRecyclerView;
    private MaterialButton btnNewFolder;
    private MaterialButton btnRefresh;
    private MaterialButton btnToggleView;
    private MaterialButton btnHome;
    private MaterialButton btnPaste;
    private MaterialButton btnDelete;
    private MaterialButton btnCopy;
    private MaterialButton btnMove;
    private com.google.android.material.textfield.TextInputLayout pathInputLayout;
    private com.google.android.material.textfield.TextInputEditText pathEditText;
    private View emptyState;
    private TextView emptyText;
    
    // 文件相关
    private File currentDirectory;
    private List<FileItem> fileList = new ArrayList<>();
    private ModManagerFileAdapter fileAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private SettingsManager settingsManager;
    
    // 视图模式
    private String currentViewMode = VIEW_MODE_GRID; // "grid" or "list"
    
    // 操作相关
    private FileItem selectedItem;
    private File copiedFile; // 复制的文件
    private File movedFile;  // 移动的文件
    private boolean isCopyMode = false; // true=复制模式, false=移动模式
    
    // 排序模式: 0=名称, 1=大小, 2=时间
    private int sortMode = 0;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mod_manager, container, false);
        initViews(view);
        setupRecyclerView();
        setupClickListeners();
        checkPermissionsAndLoadFiles();
        return view;
    }
    
    private void initViews(View view) {
        fileRecyclerView = view.findViewById(R.id.fileRecyclerView);
        btnNewFolder = view.findViewById(R.id.btnNewFolder);
        btnRefresh = view.findViewById(R.id.btnRefresh);
        btnToggleView = view.findViewById(R.id.btnToggleView);
        btnHome = view.findViewById(R.id.btnHome);
        btnPaste = view.findViewById(R.id.btnPaste);
        btnDelete = view.findViewById(R.id.btnDelete);
        btnCopy = view.findViewById(R.id.btnCopy);
        btnMove = view.findViewById(R.id.btnMove);
        pathInputLayout = view.findViewById(R.id.pathInputLayout);
        pathEditText = view.findViewById(R.id.pathEditText);
        emptyState = view.findViewById(R.id.emptyState);
        emptyText = view.findViewById(R.id.emptyText);
        
        // 初始化设置管理器
        settingsManager = SettingsManager.getInstance(requireContext());
        
        // 加载保存的视图模式
        currentViewMode = settingsManager.getString(KEY_VIEW_MODE, VIEW_MODE_GRID);
        
        // 初始状态：粘贴按钮禁用
        btnPaste.setEnabled(false);
    }
    
    private void setupRecyclerView() {
        // 创建自定义适配器
        fileAdapter = new ModManagerFileAdapter(fileList, this, currentViewMode);
        
        // 根据视图模式设置布局管理器
        if (VIEW_MODE_GRID.equals(currentViewMode)) {
            int spanCount = calculateSpanCount();
            layoutManager = new GridLayoutManager(requireContext(), spanCount);
        } else {
            layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        }
        
        fileRecyclerView.setLayoutManager(layoutManager);
        fileRecyclerView.setAdapter(fileAdapter);
        updateViewToggleButton();
    }
    
    private int calculateSpanCount() {
        // 根据屏幕宽度计算合适的列数
        // 每个项目大约需要 140dp 宽度（包括边距和padding），确保文件名完整显示
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float screenWidthDp = metrics.widthPixels / metrics.density;
        // 左侧工具栏占35%，右侧占65%，减去各种边距和padding
        float availableWidth = (screenWidthDp * 0.65f) - 16 - 16 - 16 - 24; // 减去各种边距和padding
        int spanCount = (int) (availableWidth / 140); // 每个项目约140dp，确保有足够空间
        return Math.max(3, Math.min(spanCount, 5)); // 最少3列，最多5列，确保每个项目有足够宽度
    }
    
    /**
     * 切换视图模式（网格/列表）
     */
    private void toggleViewMode() {
        // 切换视图模式
        if (VIEW_MODE_GRID.equals(currentViewMode)) {
            currentViewMode = VIEW_MODE_LIST;
        } else {
            currentViewMode = VIEW_MODE_GRID;
        }
        
        // 保存设置
        settingsManager.putString(KEY_VIEW_MODE, currentViewMode);
        
        // 更新适配器的视图模式
        fileAdapter.setViewMode(currentViewMode);
        
        // 切换布局管理器
        if (VIEW_MODE_GRID.equals(currentViewMode)) {
            int spanCount = calculateSpanCount();
            layoutManager = new GridLayoutManager(requireContext(), spanCount);
        } else {
            layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        }
        
        fileRecyclerView.setLayoutManager(layoutManager);
        fileAdapter.notifyDataSetChanged();
        
        // 更新按钮
        updateViewToggleButton();
        
        // 显示提示
        String message = VIEW_MODE_GRID.equals(currentViewMode) 
            ? getString(R.string.mod_manager_view_mode_grid)
            : getString(R.string.mod_manager_view_mode_list);
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show();
    }
    
    /**
     * 更新视图切换按钮的文本和图标
     */
    private void updateViewToggleButton() {
        if (VIEW_MODE_GRID.equals(currentViewMode)) {
            btnToggleView.setText(getString(R.string.mod_manager_list_view));
            btnToggleView.setIconResource(R.drawable.ic_list_view);
        } else {
            btnToggleView.setText(getString(R.string.mod_manager_grid_view));
            btnToggleView.setIconResource(R.drawable.ic_grid_view);
        }
    }
    
    private void setupClickListeners() {
        btnNewFolder.setOnClickListener(v -> createNewFolder());
        btnRefresh.setOnClickListener(v -> refreshFileList());
        btnToggleView.setOnClickListener(v -> toggleViewMode());
        btnHome.setOnClickListener(v -> navigateToHome());
        btnPaste.setOnClickListener(v -> pasteFile());
        btnDelete.setOnClickListener(v -> deleteSelected());
        btnCopy.setOnClickListener(v -> copySelected());
        btnMove.setOnClickListener(v -> moveSelected());
        
        // 路径输入框：按回车键导航
        pathEditText.setOnEditorActionListener((v, actionId, event) -> {
            String path = pathEditText.getText().toString().trim();
            if (!path.isEmpty()) {
                navigateToPath(path);
            }
            return true;
        });
    }
    
    private void checkPermissionsAndLoadFiles() {
        if (hasStoragePermission()) {
            loadInitialDirectory();
        } else {
            requestStoragePermission();
        }
    }
    
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", requireContext().getPackageName())));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 100);
        }
    }
    
    private void loadInitialDirectory() {
        File defaultDir = new File(DEFAULT_PATH);
        if (!defaultDir.exists()) {
            if (!defaultDir.mkdirs()) {
                defaultDir = Environment.getExternalStorageDirectory();
            }
        }
        navigateToDirectory(defaultDir);
    }
    
    /**
     * 导航到主目录
     */
    private void navigateToHome() {
        navigateToPath(DEFAULT_PATH);
    }
    
    private void navigateToPath(String path) {
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            navigateToDirectory(dir);
        } else {
            showError(getString(R.string.mod_manager_invalid_path));
        }
    }
    
    private void navigateToDirectory(File directory) {
        currentDirectory = directory;
        pathEditText.setText(directory.getAbsolutePath());
        loadFileList();
    }
    
    private void loadFileList() {
        fileList.clear();
        
        if (currentDirectory == null || !currentDirectory.exists()) {
            showEmptyState(true);
            fileAdapter.notifyDataSetChanged();
            return;
        }
        
        // 添加上级目录
        File parent = currentDirectory.getParentFile();
        if (parent != null && parent.exists()) {
            fileList.add(new FileItem("..", parent.getAbsolutePath(), true, true));
        }
        
        // 添加文件和文件夹
        File[] files = currentDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                fileList.add(new FileItem(
                    file.getName(),
                    file.getAbsolutePath(),
                    file.isDirectory(),
                    false
                ));
            }
        }
        
        // 排序
        sortFileList();
        
        showEmptyState(fileList.isEmpty());
        fileAdapter.notifyDataSetChanged();
    }
    
    private void sortFileList() {
        Collections.sort(fileList, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                // 上级目录始终在最前面
                if (a.getName().equals("..")) return -1;
                if (b.getName().equals("..")) return 1;
                
                // 文件夹优先
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                
                // 根据排序模式排序
                switch (sortMode) {
                    case 1: // 按大小（需要从文件系统获取）
                        File fileA = new File(a.getPath());
                        File fileB = new File(b.getPath());
                        return Long.compare(fileB.length(), fileA.length());
                    case 2: // 按时间（需要从文件系统获取）
                        File fileA2 = new File(a.getPath());
                        File fileB2 = new File(b.getPath());
                        return Long.compare(fileB2.lastModified(), fileA2.lastModified());
                    default: // 按名称
                        return a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
                }
            }
        });
    }
    
    private void showEmptyState(boolean show) {
        if (emptyState != null) {
            emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (fileRecyclerView != null) {
            fileRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }
    
    @Override
    public void onFileClick(FileItem fileItem) {
        if (fileItem.isDirectory()) {
            // 进入子目录
            navigateToDirectory(new File(fileItem.getPath()));
        } else {
            // 选中文件
            selectedItem = fileItem;
            showSnackbar(getString(R.string.mod_manager_file_selected, fileItem.getName()));
        }
    }
    
    @Override
    public void onFileLongClick(FileItem fileItem) {
        selectedItem = fileItem;
        showFileActionMenu(fileItem);
    }
    
    private void showFileActionMenu(FileItem fileItem) {
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.mod_manager_copy));
        options.add(getString(R.string.mod_manager_move));
        options.add(getString(R.string.mod_manager_delete));
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(fileItem.getName())
            .setItems(options.toArray(new String[0]), (dialog, which) -> {
                switch (which) {
                    case 0: copySelected(); break;
                    case 1: moveSelected(); break;
                    case 2: deleteSelected(); break;
                }
            })
            .show();
    }
    
    private void createNewFolder() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(getString(R.string.mod_manager_enter_folder_name));
        input.setSingleLine(true);
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mod_manager_new_folder))
            .setView(input)
            .setPositiveButton(getString(R.string.mod_manager_create), (dialog, which) -> {
                String folderName = input.getText().toString().trim();
                if (folderName.isEmpty()) {
                    showError(getString(R.string.mod_manager_enter_folder_name));
                    return;
                }
                File newFolder = new File(currentDirectory, folderName);
                if (newFolder.mkdirs()) {
                    refreshFileList();
                    showSnackbar(getString(R.string.mod_manager_folder_created));
                } else {
                    showError(getString(R.string.mod_manager_folder_create_failed));
                }
            })
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show();
    }
    
    private void refreshFileList() {
        loadFileList();
        showSnackbar(getString(R.string.mod_manager_refreshed));
    }
    
    private void copySelected() {
        if (selectedItem == null) {
            showError(getString(R.string.mod_manager_no_selection));
            return;
        }
        copiedFile = new File(selectedItem.getPath());
        isCopyMode = true;
        btnPaste.setEnabled(true);
        showSnackbar(getString(R.string.mod_manager_copied, selectedItem.getName()));
    }
    
    private void moveSelected() {
        if (selectedItem == null) {
            showError(getString(R.string.mod_manager_no_selection));
            return;
        }
        movedFile = new File(selectedItem.getPath());
        isCopyMode = false;
        btnPaste.setEnabled(true);
        showSnackbar(getString(R.string.mod_manager_moved, selectedItem.getName()));
    }
    
    private void pasteFile() {
        File sourceFile = isCopyMode ? copiedFile : movedFile;
        if (sourceFile == null || !sourceFile.exists()) {
            showError(getString(R.string.mod_manager_no_file_to_paste));
            btnPaste.setEnabled(false);
            return;
        }
        
        File destFile = new File(currentDirectory, sourceFile.getName());
        if (destFile.exists()) {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.mod_manager_overwrite_title))
                .setMessage(getString(R.string.mod_manager_overwrite_message, destFile.getName()))
                .setPositiveButton(getString(R.string.mod_manager_overwrite), (dialog, which) -> {
                    performPaste(sourceFile, destFile);
                })
                .setNegativeButton(getString(android.R.string.cancel), null)
                .show();
        } else {
            performPaste(sourceFile, destFile);
        }
    }
    
    private void performPaste(File source, File dest) {
        try {
            if (isCopyMode) {
                copyFile(source, dest);
                showSnackbar(getString(R.string.mod_manager_copied_success, dest.getName()));
            } else {
                moveFile(source, dest);
                showSnackbar(getString(R.string.mod_manager_moved_success, dest.getName()));
                movedFile = null;
            }
            btnPaste.setEnabled(false);
            refreshFileList();
        } catch (IOException e) {
            AppLogger.error(TAG, "Failed to paste file", e);
            showError(getString(R.string.mod_manager_paste_failed, e.getMessage()));
        }
    }
    
    private void copyFile(File source, File dest) throws IOException {
        if (source.isDirectory()) {
            copyDirectory(source, dest);
        } else {
            // 如果目标文件已存在，先删除它以确保完全覆盖
            if (dest.exists()) {
                if (!dest.delete()) {
                    throw new IOException("Failed to delete existing file: " + dest.getAbsolutePath());
                }
            }
            
            // 确保目标文件的父目录存在
            File parentDir = dest.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 使用 StreamUtils 进行文件复制，更可靠
            try (FileInputStream fis = new FileInputStream(source);
                 FileOutputStream fos = new FileOutputStream(dest)) {
                StreamUtils.transferTo(fis, fos);
                // 确保数据写入磁盘
                try {
                    fos.getFD().sync();
                } catch (Exception e) {
                }
            }
        }
    }
    
    private void copyDirectory(File sourceDir, File destDir) throws IOException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(destDir, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    copyFile(file, destFile);
                }
            }
        }
    }
    
    private void moveFile(File source, File dest) throws IOException {
        if (source.renameTo(dest)) {
            return;
        }
        // 如果重命名失败，尝试复制后删除
        copyFile(source, dest);
        deleteFile(source);
    }
    
    private void deleteSelected() {
        if (selectedItem == null) {
            showError(getString(R.string.mod_manager_no_selection));
            return;
        }
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mod_manager_delete_title))
            .setMessage(getString(R.string.mod_manager_delete_message, selectedItem.getName()))
            .setPositiveButton(getString(R.string.mod_manager_delete), (dialog, which) -> {
                File file = new File(selectedItem.getPath());
                if (deleteFile(file)) {
                    showSnackbar(getString(R.string.mod_manager_deleted, selectedItem.getName()));
                    refreshFileList();
                } else {
                    showError(getString(R.string.mod_manager_delete_failed));
                }
                selectedItem = null;
            })
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show();
    }
    
    private boolean deleteFile(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteFile(child);
                }
            }
        }
        return file.delete();
    }
    
    
    private void showSnackbar(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
        }
    }
    
    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}






