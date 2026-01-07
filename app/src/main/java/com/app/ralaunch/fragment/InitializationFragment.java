package com.app.ralaunch.fragment;

import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.adapter.ComponentAdapter;
import com.app.ralaunch.model.ComponentItem;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.manager.PermissionManager;
import com.app.ralib.error.ErrorHandler;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 初始化Fragment - 处理法律声明和组件安装
 * 
 * 主要功能：
 * 1. 显示法律声明并获取用户同意
 * 2. 从assets解压必要的组件到应用目录
 * 3. 显示安装进度和状态
 */
public class InitializationFragment extends Fragment {

    private static final String TAG = "InitializationFragment";
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LEGAL_AGREED = "legal_agreed";
    private static final String KEY_PERMISSIONS_GRANTED = "permissions_granted";
    private static final String KEY_COMPONENTS_EXTRACTED = "components_extracted";
    
    // 界面状态枚举
    private enum State {
        LEGAL_AGREEMENT,      // 法律声明界面
        PERMISSION_REQUEST,   // 权限请求界面
        EXTRACTION            // 组件解压界面
    }
    
    private State currentState = State.LEGAL_AGREEMENT;
    
    // UI组件
    private android.widget.FrameLayout legalLayout;
    private LinearLayout permissionLayout;
    private android.widget.FrameLayout extractionLayout;
    private RecyclerView componentsRecyclerView;
    private Button btnAcceptLegal;
    private Button btnDeclineLegal;
    private Button btnRequestPermissions;
    private Button btnSkipPermissions;
    private Button btnStartExtraction;
    private ProgressBar overallProgressBar;
    private TextView overallProgressText;
    private TextView overallProgressPercent;
    private TextView permissionStatusText;
    
    // 数据和适配器
    private List<ComponentItem> components;
    private ComponentAdapter componentAdapter;
    
    // 权限管理
    private PermissionManager permissionManager;
    
    // 线程管理
    private ExecutorService executorService;
    private Handler mainHandler;
    private AtomicBoolean isExtracting = new AtomicBoolean(false);
    
    // 回调接口
    public interface OnInitializationCompleteListener {
        void onInitializationComplete();
    }
    
    private OnInitializationCompleteListener listener;
    
    public void setOnInitializationCompleteListener(OnInitializationCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化权限管理器
        if (getActivity() instanceof ComponentActivity) {
            permissionManager = new PermissionManager((ComponentActivity) getActivity());
            permissionManager.initialize();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_initialization, container, false);
        
        initViews(view);
        setupComponentsList();
        checkInitializationStatus();
        
        return view;
    }
    
    /**
     * 初始化视图组件
     */
    private void initViews(View view) {
        legalLayout = view.findViewById(R.id.legalLayout);
        permissionLayout = view.findViewById(R.id.permissionLayout);
        extractionLayout = view.findViewById(R.id.extractionLayout);
        componentsRecyclerView = view.findViewById(R.id.componentsRecyclerView);
        
        btnAcceptLegal = view.findViewById(R.id.btnAcceptLegal);
        btnDeclineLegal = view.findViewById(R.id.btnDeclineLegal);
        btnRequestPermissions = view.findViewById(R.id.btnRequestPermissions);
        btnSkipPermissions = view.findViewById(R.id.btnSkipPermissions);
        btnStartExtraction = view.findViewById(R.id.btnStartExtraction);
        
        // 这些视图在布局中已移除，设置为 null
        overallProgressBar = null; // view.findViewById(R.id.overallProgressBar);
        overallProgressText = null; // view.findViewById(R.id.overallProgressText);
        overallProgressPercent = null; // view.findViewById(R.id.overallProgressPercent);
        permissionStatusText = view.findViewById(R.id.permissionStatusText);
        
        // 官方下载链接
        TextView officialDownloadLink = view.findViewById(R.id.officialDownloadLink);
        if (officialDownloadLink != null) {
            officialDownloadLink.setOnClickListener(v -> openOfficialDownloadPage());
        }
        
        // 设置按钮点击监听
        btnAcceptLegal.setOnClickListener(v -> handleAcceptLegal());
        btnDeclineLegal.setOnClickListener(v -> handleDeclineLegal());
        btnRequestPermissions.setOnClickListener(v -> handleRequestPermissions());
        btnSkipPermissions.setOnClickListener(v -> handleSkipPermissions());
        btnStartExtraction.setOnClickListener(v -> handleStartExtraction());
    }
    
    /**
     * 设置组件列表
     */
    private void setupComponentsList() {
        components = createComponentList();
        componentAdapter = new ComponentAdapter(components);
        componentsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        componentsRecyclerView.setAdapter(componentAdapter);
    }
    
    /**
     * 创建需要安装的组件列表（根据架构选择）
     */
    private List<ComponentItem> createComponentList() {
        List<ComponentItem> componentList = new ArrayList<>();

        // GL4ES - OpenGL ES 兼容层（预编译库，不需要解压）
        componentList.add(new ComponentItem(
            "GL4ES",
            getString(R.string.init_component_gl4es_desc),
            "gl4es.tar.xz",
            false  // 不需要解压
        ));

        // SDL - Simple DirectMedia Layer（预编译库，不需要解压）
        componentList.add(new ComponentItem(
            "SDL",
            getString(R.string.init_component_sdl_desc),
            "sdl.tar.xz",
            false  // 不需要解压
        ));

        // .NET Core Host（预编译库，不需要解压）
        componentList.add(new ComponentItem(
            "NETCoreclr",
            getString(R.string.init_component_netcorehost_desc),
            "netcorehost.tar.xz",
            false  // 不需要解压
        ));

        // .NET 运行时（需要解压）
        componentList.add(new ComponentItem(
            "dotnet",
            getString(R.string.init_component_dotnet_desc),
            "dotnet.tar.xz",
            true  // 需要解压
        ));

        // MonoMod 补丁框架（不在初始化时解压，按需解压）
        componentList.add(new ComponentItem(
            "MonoMod",
            getString(R.string.init_component_monomod_desc),
            "MonoMod.zip",
            false  // 不在初始化时解压
        ));

        // 游戏补丁集合（不在初始化时解压，按需解压）
        componentList.add(new ComponentItem(
            "patches",
            getString(R.string.init_component_patches_desc),
            "patches.zip",
            false  // 不在初始化时解压
        ));


        return componentList;
    }
    
    /**
     * 检查初始化状态
     */
    private void checkInitializationStatus() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        boolean legalAgreed = prefs.getBoolean(KEY_LEGAL_AGREED, false);
        boolean permissionsGranted = prefs.getBoolean(KEY_PERMISSIONS_GRANTED, false);
        boolean componentsExtracted = prefs.getBoolean(KEY_COMPONENTS_EXTRACTED, false);

        if (componentsExtracted) {
            // 已经完成初始化，直接回调
            completeInitialization();
        } else if (legalAgreed && permissionsGranted) {
            // 已同意协议且已授权，显示解压界面
            showExtractionState();
        } else if (legalAgreed) {
            // 已同意协议但未授权，显示权限请求界面
            showPermissionState();
        } else {
            // 第一次进入，显示法律声明
            showLegalState();
        }
    }
    
    /**
     * 显示法律声明界面
     */
    private void showLegalState() {
        currentState = State.LEGAL_AGREEMENT;
        legalLayout.setVisibility(View.VISIBLE);
        permissionLayout.setVisibility(View.GONE);
        extractionLayout.setVisibility(View.GONE);
        
        animateViewEntrance(legalLayout);
    }
    
    /**
     * 显示权限请求界面
     */
    private void showPermissionState() {
        currentState = State.PERMISSION_REQUEST;
        legalLayout.setVisibility(View.GONE);
        permissionLayout.setVisibility(View.VISIBLE);
        extractionLayout.setVisibility(View.GONE);
        
        // 重置为初始未授权状态
        permissionStatusText.setVisibility(View.GONE);
        btnRequestPermissions.setText(getString(R.string.init_grant_permissions));
        btnRequestPermissions.setEnabled(true);
        btnSkipPermissions.setVisibility(View.VISIBLE);
        
        animateViewEntrance(permissionLayout);
    }
    
    /**
     * 显示组件解压界面
     */
    private void showExtractionState() {
        currentState = State.EXTRACTION;
        legalLayout.setVisibility(View.GONE);
        permissionLayout.setVisibility(View.GONE);
        extractionLayout.setVisibility(View.VISIBLE);
        
        // 重置组件状态
        resetComponentsState();
        
        // 重置进度
        updateOverallProgress(0, getString(R.string.init_click_to_start));
        
        animateViewEntrance(extractionLayout);
        
        // 等待用户点击按钮，不再自动开始
    }
    
    /**
     * 重置所有组件状态
     */
    private void resetComponentsState() {
        for (ComponentItem component : components) {
            component.setInstalled(false);
            component.setProgress(0);
        }
        componentAdapter.notifyDataSetChanged();
    }
    
    /**
     * 处理接受法律声明
     */
    private void handleAcceptLegal() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        prefs.edit().putBoolean(KEY_LEGAL_AGREED, true).apply();
        
        // 进入权限请求界面
        showPermissionState();
    }
    
    /**
     * 处理拒绝法律声明
     */
    private void handleDeclineLegal() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
    
    /**
     * 打开官方下载页面
     */
    private void openOfficialDownloadPage() {
        try {
            String officialUrl = "https://github.com/FireworkSky/RotatingartLauncher";
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(officialUrl));
            startActivity(intent);
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to open official download page", e);
            Toast.makeText(requireActivity(), getString(R.string.init_cannot_open_browser), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 处理请求权限
     */
    private void handleRequestPermissions() {
        if (permissionManager == null) {
            AppLogger.error(TAG, "PermissionManager is null");
            Toast.makeText(requireActivity(), getString(R.string.init_permission_manager_failed), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 如果已经有权限，直接进入下一步
        if (permissionManager.hasRequiredPermissions()) {
            SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
            prefs.edit().putBoolean(KEY_PERMISSIONS_GRANTED, true).apply();
            showExtractionState();
            return;
        }
        
        btnRequestPermissions.setEnabled(false);
        btnRequestPermissions.setText(getString(R.string.init_requesting_permissions));
        
        permissionManager.requestRequiredPermissions(new PermissionManager.PermissionCallback() {
            @Override
            public void onPermissionsGranted() {
                // 权限授予成功，直接进入下一步
                SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
                prefs.edit().putBoolean(KEY_PERMISSIONS_GRANTED, true).apply();
                
                Toast.makeText(requireActivity(), getString(R.string.init_permissions_granted), Toast.LENGTH_SHORT).show();
                showExtractionState();
            }
            
            @Override
            public void onPermissionsDenied() {
                // 权限被拒绝，恢复按钮状态
                btnRequestPermissions.setEnabled(true);
                btnRequestPermissions.setText(getString(R.string.init_grant_permissions));
                
                Toast.makeText(requireActivity(), 
                    getString(R.string.init_permissions_denied), 
                    Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * 处理跳过权限
     */
    private void handleSkipPermissions() {
        // 用户选择跳过权限，直接进入解压界面
        // 不保存权限已授予状态，下次启动时会再次询问
        new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.init_skip_permissions_title))
            .setMessage(getString(R.string.init_skip_permissions_message))
            .setPositiveButton(getString(R.string.init_skip_continue), (dialog, which) -> {
                showExtractionState();
            })
            .setNegativeButton(getString(R.string.init_skip_back), null)
            .show();
    }
    
    /**
     * 更新权限状态显示
     */
    private void updatePermissionStatus() {
        if (permissionManager == null || permissionStatusText == null) {
            return;
        }
        
        boolean hasPermissions = permissionManager.hasRequiredPermissions();
        
        if (hasPermissions) {
            permissionStatusText.setVisibility(View.VISIBLE);
            permissionStatusText.setText(getString(R.string.init_permissions_granted_check));
            btnRequestPermissions.setText(getString(R.string.init_continue));
            btnRequestPermissions.setEnabled(true);
            btnSkipPermissions.setVisibility(View.GONE);
        } else {
            permissionStatusText.setVisibility(View.GONE);
            btnRequestPermissions.setText(getString(R.string.init_grant_permissions));
            btnRequestPermissions.setEnabled(true);
            btnSkipPermissions.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 处理开始解压
     */
    private void handleStartExtraction() {
        if (!isExtracting.get()) {
            startExtraction();
        }
    }
    
    /**
     * 开始组件解压
     */
    private void startExtraction() {
        if (isExtracting.getAndSet(true)) {
            return;
        }

        // 更新按钮状态
        btnStartExtraction.setEnabled(false);
        btnStartExtraction.setText(getString(R.string.init_installing));
        
        // 在后台线程执行解压
        executorService.execute(() -> {
            try {
                extractAllComponents();

                // 在主线程完成初始化
                mainHandler.post(() -> {
                    completeInitialization();
                });
            } catch (Exception e) {
                AppLogger.error(TAG, "Extraction failed", e);
                
                // 在主线程恢复按钮状态并显示错误
                mainHandler.post(() -> {
                    handleExtractionError(e);
                });
            } finally {
                isExtracting.set(false);
            }
        });
    }
    
    /**
     * 解压所有组件（根据架构解压到对应目录）
     */
    private void extractAllComponents() throws Exception {

        int totalComponents = components.size();

        // 获取目标目录（仅支持 ARM64）
        String dirName = "dotnet";
        File outputDir = new File(requireActivity().getFilesDir(), dirName);

        // 在开始解压前先清理目标目录
        if (outputDir.exists()) {

            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(outputDir);
        }
        outputDir.mkdirs();

        for (int i = 0; i < totalComponents; i++) {
            ComponentItem component = components.get(i);
            
            // 更新组件状态为开始解压
            updateComponentStatus(i, 10, getString(R.string.init_start_extracting));
            
            // 执行解压
            boolean success = extractComponent(component, i);
            
            if (success) {
                // 标记为已安装
                updateComponentStatus(i, 100, getString(R.string.init_extraction_complete));
                component.setInstalled(true);

            } else {
                throw new Exception(getString(R.string.init_extraction_failed_component, component.getName()));
            }
        }
    }
    
    /**
     * 解压单个组件
     */
    private boolean extractComponent(ComponentItem component, int componentIndex) {
        if (!component.needsExtraction()) {
            updateComponentStatus(componentIndex, 100, getString(R.string.init_no_extraction_needed));
            return true;
        }

        AssetManager assetManager = requireActivity().getAssets();
        File tempArchiveFile = null;
        
        try {
            // 1. 创建临时文件
            tempArchiveFile = new File(requireActivity().getCacheDir(), 
                                 "temp_" + component.getFileName());
            updateComponentStatus(componentIndex, 20, getString(R.string.init_preparing_file));
            
            // 2. 从assets复制到临时文件
            copyAssetToFile(assetManager, component.getFileName(), tempArchiveFile);
            updateComponentStatus(componentIndex, 30, getString(R.string.init_file_ready));

            // 3. 根据组件名称确定目标目录
            String dirName = component.getName(); // 使用组件名称作为目录名
            File outputDir = new File(requireActivity().getFilesDir(), dirName);
            
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // 4. 解压文件到目标目录
            boolean success = extractArchiveFile(tempArchiveFile, outputDir, component, componentIndex);
            
            return success;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Error extracting component: " + component.getName(), e);
            return false;
        } finally {
            // 清理临时文件
            if (tempArchiveFile != null && tempArchiveFile.exists()) {
                try {
                    java.nio.file.Files.deleteIfExists(tempArchiveFile.toPath());
                } catch (IOException e) {
                }
            }
        }
    }
    
    /**
     * 从assets复制文件到临时文件
     */
    private void copyAssetToFile(AssetManager assetManager, String assetFileName, 
                                  File targetFile) throws IOException {
        
        try (InputStream inputStream = assetManager.open(assetFileName)) {
            java.nio.file.Files.copy(inputStream, targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (FileNotFoundException e) {
            AppLogger.error(TAG, "Asset file not found: " + assetFileName, e);
            // 尝试列出 assets 目录内容以便调试
            try {
                String[] files = assetManager.list("");
                AppLogger.error(TAG, "Available assets: " + java.util.Arrays.toString(files));
            } catch (Exception ex) {
                AppLogger.error(TAG, "Failed to list assets for debugging", ex);
            }
            throw e;
        }
    }
    
    /**
     * 解压归档文件（支持 tar.xz、tar.gz 和 tar 格式）
     */
    private boolean extractArchiveFile(File archiveFile, File targetDir, 
                                       ComponentItem component, int componentIndex) {
        String fileName = component.getFileName().toLowerCase();
        
        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            return extractTarGz(archiveFile, targetDir, component, componentIndex);
        } else if (fileName.endsWith(".tar.xz")) {
            return extractTarXz(archiveFile, targetDir, component, componentIndex);
        } else if (fileName.endsWith(".tar") && !fileName.endsWith(".tar.gz") && !fileName.endsWith(".tar.xz")) {
            return extractTar(archiveFile, targetDir, component, componentIndex);
        } else {
            AppLogger.error(TAG, "Unsupported archive format: " + fileName);
            return false;
        }
    }
    
    /**
     * 使用通用解压工具解压纯 tar 文件（无压缩）
     */
    private boolean extractTar(File archiveFile, File targetDir, 
                               ComponentItem component, int componentIndex) {
        try {
            updateComponentStatus(componentIndex, 40, getString(R.string.init_extracting));
            
            // 根据组件名称确定前缀
            String stripPrefix = null;
            
            // 使用 Apache Commons Compress 解压 tar 文件
            try (FileInputStream fis = new FileInputStream(archiveFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarIn = 
                     new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bis)) {
                
                org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
                int processedFiles = 0;
                
                while ((entry = tarIn.getNextTarEntry()) != null) {
                    String entryName = entry.getName();
                    
                    // 跳过根目录和空条目
                    if (entryName.isEmpty() || entryName.equals(".") || entryName.equals("..")) {
                        continue;
                    }
                    
                    // 移除前导的 ./
                    if (entryName.startsWith("./")) {
                        entryName = entryName.substring(2);
                    }
                    
                    // 移除指定的前缀
                    if (stripPrefix != null && !stripPrefix.isEmpty()) {
                        if (entryName.startsWith("./" + stripPrefix)) {
                            entryName = entryName.substring(2 + stripPrefix.length());
                        } else if (entryName.startsWith(stripPrefix)) {
                            entryName = entryName.substring(stripPrefix.length());
                        } else if (entryName.contains(stripPrefix)) {
                            int idx = entryName.indexOf(stripPrefix);
                            entryName = entryName.substring(idx + stripPrefix.length());
                        }
                    }
                    
                    if (entryName.isEmpty()) {
                        continue;
                    }
                    
                    File targetFile = new File(targetDir, entryName);
                    
                    // 安全检查
                    String canonicalDestPath = targetDir.getCanonicalPath();
                    String canonicalEntryPath = targetFile.getCanonicalPath();
                    if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator) && 
                        !canonicalEntryPath.equals(canonicalDestPath)) {
                        continue;
                    }
                    
                    if (entry.isDirectory()) {
                        if (!targetFile.exists()) {
                            targetFile.mkdirs();
                        }
                    } else {
                        File parent = targetFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        
                        try (FileOutputStream fos = new FileOutputStream(targetFile);
                             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = tarIn.read(buffer)) != -1) {
                                bos.write(buffer, 0, bytesRead);
                            }
                        }
                        
                        if ((entry.getMode() & 0100) != 0) {
                            targetFile.setExecutable(true, false);
                        }
                        targetFile.setReadable(true, false);
                    }
                    
                    processedFiles++;
                }
                
                updateComponentStatus(componentIndex, 100, getString(R.string.init_complete));
                return true;
            }
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to extract tar: " + archiveFile.getName(), e);
            updateComponentStatus(componentIndex, 0, getString(R.string.init_extraction_failed_status));
            return false;
        }
    }
    
    /**
     * 使用通用解压工具解压 tar.gz 文件
     */
    private boolean extractTarGz(File archiveFile, File targetDir, 
                                  ComponentItem component, int componentIndex) {
        try {
            updateComponentStatus(componentIndex, 40, getString(R.string.init_extracting));
            
            // 根据组件名称确定前缀
            String stripPrefix = null;
            
            // 使用通用解压工具
            int fileCount = com.app.ralaunch.utils.ArchiveExtractor.extractTarGz(
                archiveFile, targetDir, stripPrefix);
            
            updateComponentStatus(componentIndex, 100, getString(R.string.init_complete));
            return true;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to extract tar.gz: " + archiveFile.getName(), e);
            updateComponentStatus(componentIndex, 0, getString(R.string.init_extraction_failed_status));
            return false;
        }
    }
    
    /**
     * 使用 Apache Commons Compress 解压 tar.xz 文件
     */
    private boolean extractTarXz(File archiveFile, File targetDir, 
                                  ComponentItem component, int componentIndex) {
        try (FileInputStream fis = new FileInputStream(archiveFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             org.apache.commons.compress.compressors.xz.XZCompressorInputStream xzIn = 
                 new org.apache.commons.compress.compressors.xz.XZCompressorInputStream(bis);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarIn = 
                 new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(xzIn)) {
            
            updateComponentStatus(componentIndex, 40, getString(R.string.init_extracting));
            
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            int processedFiles = 0;
            int totalFiles = 0;
            
            // 先统计文件数量（粗略估计）
            long fileSize = archiveFile.length();
            int estimatedFiles = (int)(fileSize / (50 * 1024)); // 假设平均每个文件50KB
            
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (!tarIn.canReadEntryData(entry)) {
                    continue;
                }
                
                String entryName = entry.getName();
                
                // 跳过顶层 dotnet 目录
                if (entryName.contains("/") || entryName.contains("\\")) {
                    int slashIndex = Math.max(entryName.indexOf('/'), entryName.indexOf('\\'));
                    String topLevel = entryName.substring(0, slashIndex);
                    if (topLevel.startsWith("dotnet")) {
                        entryName = entryName.substring(slashIndex + 1);
                    }
                }
                
                if (entryName.isEmpty()) {
                    continue;
                }
                
                File targetFile = new File(targetDir, entryName);
                
                // 安全检查
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    continue;
                }
                
                if (entry.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                } else {
                    // 创建父目录
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    // 写入文件
                    try (FileOutputStream fos = new FileOutputStream(targetFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = tarIn.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    // 设置权限（如果是可执行文件）
                    if ((entry.getMode() & 0100) != 0) {
                        targetFile.setExecutable(true);
                    }
                }
                
                processedFiles++;
                if (processedFiles % 10 == 0) {
                    int progress = 40 + (int)((processedFiles * 50.0) / Math.max(estimatedFiles, processedFiles));
                    updateComponentStatus(componentIndex, Math.min(progress, 90), 
                        getString(R.string.init_extracting_files, processedFiles));
                }
            }
            
            updateComponentStatus(componentIndex, 100, getString(R.string.init_complete));
            return true;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to extract tar.xz: " + archiveFile.getName(), e);
            updateComponentStatus(componentIndex, 0, getString(R.string.init_extraction_failed_status));
            return false;
        }
    }
    
    /**
     * 删除目录及其所有内容
     */
    
    /**
     * 更新组件状态
     */
    private void updateComponentStatus(int componentIndex, int progress, String status) {
        mainHandler.post(() -> {
            ComponentItem component = components.get(componentIndex);
            component.setProgress(progress);
            componentAdapter.notifyItemChanged(componentIndex);
            
            // 更新总体进度
            int overallProgress = calculateOverallProgress();
            updateOverallProgress(overallProgress, status);

        });
    }
    
    /**
     * 更新总体进度
     */
    private void updateOverallProgress(int progress, String status) {
        if (overallProgressBar != null) {
            overallProgressBar.setProgress(progress);
        }
        if (overallProgressText != null) {
            overallProgressText.setText(status);
        }
        if (overallProgressPercent != null) {
            overallProgressPercent.setText(progress + "%");
        }
    }
    
    /**
     * 计算总体进度
     */
    private int calculateOverallProgress() {
        if (components.isEmpty()) return 0;
        
        int totalProgress = 0;
        for (ComponentItem component : components) {
            totalProgress += component.getProgress();
        }
        return totalProgress / components.size();
    }
    
    /**
     * 处理解压错误
     */
    private void handleExtractionError(Exception error) {
        btnStartExtraction.setEnabled(true);
        btnStartExtraction.setText(getString(R.string.init_retry_install));
        
        String errorMessage = getString(R.string.init_extraction_failed, error.getMessage());
        // 使用统一的错误弹窗
        ErrorHandler.handleError(errorMessage, error);

        AppLogger.error(TAG, "Extraction error handled", error);
    }
    
    /**
     * 完成初始化
     */
    private void completeInitialization() {

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        prefs.edit().putBoolean(KEY_COMPONENTS_EXTRACTED, true).apply();
        
        // 更新UI显示成功状态
        updateOverallProgress(100, getString(R.string.init_install_complete));
        
        // 显示安装的运行时版本信息
        try {
            java.util.List<String> versions = com.app.ralaunch.utils.RuntimeManager.listInstalledVersions(requireActivity());
            if (!versions.isEmpty()) {
                String versionInfo = getString(R.string.init_installed_runtime_versions, String.join(", ", versions));
                Toast.makeText(requireActivity(), versionInfo, Toast.LENGTH_LONG).show();

            } else {
                Toast.makeText(requireActivity(), getString(R.string.init_dotnet_install_success), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireActivity(), getString(R.string.init_dotnet_install_success), Toast.LENGTH_SHORT).show();
        }
        
        // 延迟后回调，给用户看到成功状态的时间
        mainHandler.postDelayed(() -> {
            if (listener != null) {
                listener.onInitializationComplete();
            }
        }, 2000); // 2秒后返回，给用户足够时间看到版本信息
    }
    
    /**
     * 视图入场动画
     */
    private void animateViewEntrance(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.95f);
        view.setScaleY(0.95f);
        
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 确保全屏显示
        if (getActivity() != null) {
            View decorView = getActivity().getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // 关闭线程池
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
