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
import com.app.ralaunch.adapter.FileBrowserAdapter;
import com.app.ralaunch.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileBrowserFragment extends Fragment implements FileBrowserAdapter.OnFileClickListener {

    private OnFileSelectedListener fileSelectedListener;
    private OnBackListener backListener;

    // 界面控件
    private RecyclerView fileRecyclerView;
    private TextView currentPathText;
    private Button confirmButton;
    private LinearLayout emptyState;
    private TextView emptyText;
    private LinearLayout permissionDeniedState;

    // 文件相关
    private File currentDirectory;
    private File selectedFile;
    private List<String> allowedExtensions;
    private String fileType;

    // 文件列表
    private List<FileItem> fileList = new ArrayList<>();
    private FileBrowserAdapter fileAdapter;

    // 权限请求监听器
    public interface OnPermissionRequestListener {
        void onPermissionRequest(MainActivity.PermissionCallback callback);
    }

    public interface OnFileSelectedListener {
        void onFileSelected(String filePath, String fileType);
    }

    public interface OnBackListener {
        void onBack();
    }

    public void setOnFileSelectedListener(OnFileSelectedListener listener) {
        this.fileSelectedListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
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
        // 返回按钮
        ImageButton backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onBack();
            }
        });

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

        // 设置 RecyclerView
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        fileAdapter = new FileBrowserAdapter(fileList, this);
        fileRecyclerView.setAdapter(fileAdapter);

        // 确认按钮
        confirmButton.setOnClickListener(v -> {
            if (selectedFile != null && fileSelectedListener != null) {
                fileSelectedListener.onFileSelected(selectedFile.getAbsolutePath(), fileType);
            }
        });

        // 权限拒绝状态的授权按钮
        Button grantPermissionButton = view.findViewById(R.id.grantPermissionButton);
        grantPermissionButton.setOnClickListener(v -> checkPermissionsAndLoadFiles());

        // 初始状态
        updateConfirmButton();
    }

    private String getPageTitle() {
        switch (fileType) {
            case "game":
                return "选择游戏文件";
            case "tmodloader":
                return "选择 tModLoader 文件";
            default:
                return "选择文件";
        }
    }

    /**
     * 检查权限并加载文件
     */
    private void checkPermissionsAndLoadFiles() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();

            if (activity.hasRequiredPermissions()) {
                // 已有权限，加载文件
                loadInitialDirectory();
                hidePermissionDeniedState();
            } else {
                activity.requestRequiredPermissions(new MainActivity.PermissionCallback() {
                    @Override
                    public void onPermissionsGranted() {
                        checkPermissionsAndLoadFiles();
                    }

                    @Override
                    public void onPermissionsDenied() {

                    }
                });
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

        // 更新空状态
        updateEmptyState();

        fileAdapter.notifyDataSetChanged();
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
                emptyText.setText("当前文件夹为空");
            } else {
                emptyText.setText("当前目录为空");
            }
        } else {
            emptyState.setVisibility(View.GONE);
            fileRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateConfirmButton() {
        if (selectedFile != null) {
            confirmButton.setEnabled(true);
            confirmButton.setBackgroundTintList(getResources().getColorStateList(R.color.button_secondary));
            confirmButton.setText("确认选择: " + selectedFile.getName());
        } else {
            confirmButton.setEnabled(false);
            confirmButton.setBackgroundTintList(getResources().getColorStateList(R.color.button_disabled));
            confirmButton.setText("选择文件");
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
        // 长按显示文件信息（可选功能）
    }
}