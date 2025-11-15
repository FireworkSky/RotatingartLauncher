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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.adapter.ComponentAdapter;
import com.app.ralaunch.model.ComponentItem;
import com.app.ralaunch.utils.AppLogger;

import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    private static final String KEY_COMPONENTS_EXTRACTED = "components_extracted";
    
    // 界面状态枚举
    private enum State {
        LEGAL_AGREEMENT,    // 法律声明界面
        EXTRACTION          // 组件解压界面
    }
    
    private State currentState = State.LEGAL_AGREEMENT;
    
    // UI组件
    private LinearLayout legalLayout;
    private LinearLayout extractionLayout;
    private RecyclerView componentsRecyclerView;
    private Button btnAcceptLegal;
    private Button btnDeclineLegal;
    private Button btnStartExtraction;
    private ProgressBar overallProgressBar;
    private TextView overallProgressText;
    private TextView overallProgressPercent;
    
    // 数据和适配器
    private List<ComponentItem> components;
    private ComponentAdapter componentAdapter;
    
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
        extractionLayout = view.findViewById(R.id.extractionLayout);
        componentsRecyclerView = view.findViewById(R.id.componentsRecyclerView);
        
        btnAcceptLegal = view.findViewById(R.id.btnAcceptLegal);
        btnDeclineLegal = view.findViewById(R.id.btnDeclineLegal);
        btnStartExtraction = view.findViewById(R.id.btnStartExtraction);
        
        overallProgressBar = view.findViewById(R.id.overallProgressBar);
        overallProgressText = view.findViewById(R.id.overallProgressText);
        overallProgressPercent = view.findViewById(R.id.overallProgressPercent);
        
        // 设置按钮点击监听
        btnAcceptLegal.setOnClickListener(v -> handleAcceptLegal());
        btnDeclineLegal.setOnClickListener(v -> handleDeclineLegal());
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


        String zipFileName = "dotnet.zip";
        String componentName = "dotnet";
        String componentDesc = ".NET 7/8/9/10 运行时";

        componentList.add(new ComponentItem(
            componentName,
            componentDesc,
            zipFileName
        ));

        return componentList;
    }
    
    /**
     * 检查初始化状态
     */
    private void checkInitializationStatus() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        boolean legalAgreed = prefs.getBoolean(KEY_LEGAL_AGREED, false);
        boolean componentsExtracted = prefs.getBoolean(KEY_COMPONENTS_EXTRACTED, false);

        if (componentsExtracted) {
            // 已经完成初始化，直接回调
            completeInitialization();
        } else if (legalAgreed) {
            // 已同意协议但未解压，显示解压界面
            showExtractionState();
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
        extractionLayout.setVisibility(View.GONE);
        
        animateViewEntrance(legalLayout);

    }
    
    /**
     * 显示组件解压界面
     */
    private void showExtractionState() {
        currentState = State.EXTRACTION;
        legalLayout.setVisibility(View.GONE);
        extractionLayout.setVisibility(View.VISIBLE);
        
        // 重置组件状态
        resetComponentsState();
        
        // 重置进度
        updateOverallProgress(0, "准备安装...");
        
        animateViewEntrance(extractionLayout);

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
        
        showExtractionState();
        // 自动开始解压
        startExtraction();
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
            AppLogger.warn(TAG, "Extraction already in progress");
            return;
        }

        // 更新按钮状态
        btnStartExtraction.setEnabled(false);
        btnStartExtraction.setText("安装中...");
        
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

            deleteDirectory(outputDir);
        }
        outputDir.mkdirs();

        for (int i = 0; i < totalComponents; i++) {
            ComponentItem component = components.get(i);
            
            // 更新组件状态为开始解压
            updateComponentStatus(i, 10, "开始解压...");
            
            // 执行解压
            boolean success = extractComponent(component, i);
            
            if (success) {
                // 标记为已安装
                updateComponentStatus(i, 100, "解压完成");
                component.setInstalled(true);

            } else {
                throw new Exception("解压 " + component.getName() + " 失败");
            }
        }
    }
    
    /**
     * 解压单个组件
     */
    private boolean extractComponent(ComponentItem component, int componentIndex) {

        AssetManager assetManager = requireActivity().getAssets();
        File tempZipFile = null;
        
        try {
            // 1. 创建临时文件
            tempZipFile = new File(requireActivity().getCacheDir(), 
                                 "temp_" + component.getFileName());
            updateComponentStatus(componentIndex, 20, "准备文件...");
            
            // 2. 从assets复制到临时文件
            copyAssetToFile(assetManager, component.getFileName(), tempZipFile);
            updateComponentStatus(componentIndex, 30, "文件准备完成");

            // 3. 获取目标目录（仅支持 ARM64）
            String dirName = "dotnet";
            File outputDir = new File(requireActivity().getFilesDir(), dirName);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // 4. 解压文件到目标目录
            boolean success = extractZipFile(tempZipFile, outputDir, component, componentIndex);
            
            return success;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "Error extracting component: " + component.getName(), e);
            return false;
        } finally {
            // 清理临时文件
            if (tempZipFile != null && tempZipFile.exists()) {
                try {
                    java.nio.file.Files.deleteIfExists(tempZipFile.toPath());
                } catch (IOException e) {
                    AppLogger.warn(TAG, "Failed to delete temp file: " + tempZipFile.getAbsolutePath(), e);
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

        }
    }
    
    /**
     * 解压ZIP文件（优先使用SevenZipJBinding，失败则回退到ZipInputStream）
     */
    private boolean extractZipFile(File zipFile, File targetDir, 
                                    ComponentItem component, int componentIndex) {
        // 先尝试使用SevenZipJBinding
        boolean success = extractWithSevenZipJBinding(zipFile, targetDir, component, componentIndex);
        
        if (!success) {
            // 回退到标准ZipInputStream

            success = extractWithZipInputStream(zipFile, targetDir, component, componentIndex);
        }
        
        return success;
    }
    
    /**
     * 使用SevenZipJBinding解压
     */
    private boolean extractWithSevenZipJBinding(File zipFile, File targetDir,
                                                ComponentItem component, int componentIndex) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(zipFile, "r");
             RandomAccessFileInStream inStream = new RandomAccessFileInStream(randomAccessFile);
             IInArchive inArchive = SevenZip.openInArchive(null, inStream)) {

            int totalItems = inArchive.getNumberOfItems();

            // 创建解压回调
            ExtractCallback callback = new ExtractCallback(
                inArchive, targetDir, component, componentIndex, totalItems);
            
            // 执行解压
            inArchive.extract(null, false, callback);

            return true;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "SevenZipJBinding extraction failed", e);
            return false;
        }
    }
    
    /**
     * SevenZipJBinding解压回调
     */
    private class ExtractCallback implements IArchiveExtractCallback {
        private final IInArchive inArchive;
        private final File targetDir;
        private final ComponentItem component;
        private final int componentIndex;
        private final int totalItems;
        private int processedItems = 0;
        
        public ExtractCallback(IInArchive inArchive, File targetDir,
                              ComponentItem component, int componentIndex, int totalItems) {
            this.inArchive = inArchive;
            this.targetDir = targetDir;
            this.component = component;
            this.componentIndex = componentIndex;
            this.totalItems = totalItems;
        }
        
        @Override
        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) 
                throws SevenZipException {
            try {
                String filePath = inArchive.getStringProperty(index, PropID.PATH);
                Boolean isFolder = (Boolean) inArchive.getProperty(index, PropID.IS_FOLDER);
                
                // 跳过 zip 内部的顶层 "dotnet*/" 目录（避免双层目录）
                // 支持: dotnet/, dotnet-arm64/, dotnet-x64/ 等
                if (filePath.contains("/") || filePath.contains("\\")) {
                    int slashIndex = Math.max(filePath.indexOf('/'), filePath.indexOf('\\'));
                    String topLevel = filePath.substring(0, slashIndex);
                    if (topLevel.startsWith("dotnet")) {
                        filePath = filePath.substring(slashIndex + 1);
                        if (!filePath.isEmpty()) {

                        }
                    }
                }
                
                // 如果路径为空或只是分隔符，跳过
                if (filePath.isEmpty() || filePath.equals("/") || filePath.equals("\\")) {
                    return null;
                }
                
                if (Boolean.TRUE.equals(isFolder)) {
                    // 创建目录
                    File dir = new File(targetDir, filePath);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    return null;
                }
                
                // 处理文件
                File targetFile = new File(targetDir, filePath);
                
                // 安全检查
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();
                
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    throw new SevenZipException("ZIP条目在目标目录之外: " + filePath);
                }
                
                // 创建父目录
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                
                return new SequentialOutStream(targetFile);
                
            } catch (Exception e) {
                throw new SevenZipException("Error getting stream for index " + index, e);
            }
        }
        
        @Override
        public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
            // 准备操作
        }
        
        @Override
        public void setOperationResult(ExtractOperationResult extractOperationResult) 
                throws SevenZipException {
            processedItems++;
            
            // 更新进度
            if (totalItems > 0) {
                int progress = 30 + (int) ((processedItems * 60.0) / totalItems);
                String status = "解压中 (" + processedItems + "/" + totalItems + ")";
                updateComponentStatus(componentIndex, progress, status);
            }
            
            if (extractOperationResult != ExtractOperationResult.OK) {
                AppLogger.warn(TAG, "Extraction operation result: " + extractOperationResult);
            }
        }
        
        @Override
        public void setTotal(long total) throws SevenZipException {
            // 可以在这里更新基于字节的进度
        }
        
        @Override
        public void setCompleted(long complete) throws SevenZipException {
            // 可以在这里更新基于字节的进度
        }
    }
    
    /**
     * SevenZipJBinding输出流实现
     */
    private class SequentialOutStream implements ISequentialOutStream {
        private final FileOutputStream outputStream;
        
        public SequentialOutStream(File targetFile) throws FileNotFoundException {
            this.outputStream = new FileOutputStream(targetFile);
        }
        
        @Override
        public int write(byte[] data) throws SevenZipException {
            try {
                outputStream.write(data);
                return data.length;
            } catch (IOException e) {
                throw new SevenZipException("Error writing to output stream", e);
            }
        }
        
        public void close() throws IOException {
            outputStream.close();
        }
    }
    
    /**
     * 使用ZipInputStream解压（备用方法）
     */
    private boolean extractWithZipInputStream(File zipFile, File targetDir,
                                              ComponentItem component, int componentIndex) {
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            updateComponentStatus(componentIndex, 40, "使用备用方法解压...");
            
            // 统计总条目数
            int totalEntries = countZipEntries(zipFile);
            if (totalEntries == 0) totalEntries = 1;
            
            ZipEntry entry;
            int entriesProcessed = 0;

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                
                // 跳过 zip 内部的顶层 "dotnet*/" 目录（避免双层目录）
                // 支持: dotnet/, dotnet-arm64/, dotnet-x64/ 等
                if (fileName.contains("/") || fileName.contains("\\")) {
                    int slashIndex = Math.max(fileName.indexOf('/'), fileName.indexOf('\\'));
                    String topLevel = fileName.substring(0, slashIndex);
                    if (topLevel.startsWith("dotnet")) {
                        fileName = fileName.substring(slashIndex + 1);
                        if (!fileName.isEmpty()) {

                        }
                    }
                }
                
                // 如果路径为空或只是分隔符，跳过
                if (fileName.isEmpty() || fileName.equals("/") || fileName.equals("\\")) {
                    zis.closeEntry();
                    continue;
                }
                
                File outputFile = new File(targetDir, fileName);
                
                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    java.nio.file.Files.copy(zis, outputFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                
                zis.closeEntry();
                entriesProcessed++;
                
                // 更新进度
                int progress = 40 + (int) ((entriesProcessed * 50.0) / totalEntries);
                String status = "解压中 (" + entriesProcessed + "/" + totalEntries + ")";
                updateComponentStatus(componentIndex, progress, status);
            }
            
            return true;
            
        } catch (Exception e) {
            AppLogger.error(TAG, "ZipInputStream extraction failed", e);
            return false;
        }
    }
    
    /**
     * 统计ZIP文件中的条目数
     */
    private int countZipEntries(File zipFile) {
        int count = 0;
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {
            
            while (zis.getNextEntry() != null) {
                count++;
                zis.closeEntry();
            }
        } catch (IOException e) {
            AppLogger.error(TAG, "Error counting zip entries", e);
        }
        return count;
    }
    
    /**
     * 删除目录及其所有内容
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
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
        overallProgressBar.setProgress(progress);
        overallProgressText.setText(status);
        overallProgressPercent.setText(progress + "%");
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
        btnStartExtraction.setText("重试安装");
        
        String errorMessage = "解压失败: " + error.getMessage();
        Toast.makeText(requireActivity(), errorMessage, Toast.LENGTH_LONG).show();

        AppLogger.error(TAG, "Extraction error handled", error);
    }
    
    /**
     * 完成初始化
     */
    private void completeInitialization() {

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        prefs.edit().putBoolean(KEY_COMPONENTS_EXTRACTED, true).apply();
        
        // 更新UI显示成功状态
        updateOverallProgress(100, "安装完成");
        
        // 显示安装的运行时版本信息
        try {
            java.util.List<String> versions = com.app.ralaunch.utils.RuntimeManager.listInstalledVersions(requireActivity());
            if (!versions.isEmpty()) {
                String versionInfo = "已安装 .NET 运行时版本：" + String.join(", ", versions);
                Toast.makeText(requireActivity(), versionInfo, Toast.LENGTH_LONG).show();

            } else {
                Toast.makeText(requireActivity(), ".NET 环境安装成功！", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireActivity(), ".NET 环境安装成功！", Toast.LENGTH_SHORT).show();
            AppLogger.warn(TAG, "Failed to get installed versions", e);
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
