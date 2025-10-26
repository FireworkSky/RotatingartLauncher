package com.app.ralaunch.fragment;

import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class InitializationFragment extends Fragment {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LEGAL_AGREED = "legal_agreed";
    private static final String KEY_COMPONENTS_EXTRACTED = "components_extracted";
    private static final String TAG = "InitializationFragment";

    // 界面状态
    private static final int STATE_LEGAL = 0;
    private static final int STATE_EXTRACTION = 1;

    private int currentState = STATE_LEGAL;

    // 界面控件
    private LinearLayout legalLayout;
    private LinearLayout extractionLayout;
    private RecyclerView componentsRecyclerView;
    private Button primaryButtonLegal;
    private Button secondaryButtonLegal;
    private Button primaryButtonExtraction;
    private ProgressBar overallProgressBar;
    private TextView overallProgressText;

    // 数据
    private List<ComponentItem> components;
    private ComponentAdapter componentAdapter;

    // 回调接口
    public interface OnInitializationCompleteListener {
        void onInitializationComplete();
    }

    private OnInitializationCompleteListener listener;

    public void setOnInitializationCompleteListener(OnInitializationCompleteListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_initialization, container, false);
        initViews(view);
        setupComponentsList();

        // 检查当前应该显示哪个状态
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        boolean legalAgreed = prefs.getBoolean(KEY_LEGAL_AGREED, false);
        boolean componentsExtracted = prefs.getBoolean(KEY_COMPONENTS_EXTRACTED, false);

        Log.d(TAG, "legalAgreed: " + legalAgreed + ", componentsExtracted: " + componentsExtracted);

        if (componentsExtracted) {
            // 如果已经完成初始化，直接回调
            if (listener != null) {
                listener.onInitializationComplete();
            }
        } else if (legalAgreed) {
            // 如果已经同意协议但未解压，直接显示解压界面并开始解压
            showExtractionState();
            startComponentsExtraction();
        } else {
            // 第一次进入，显示法律声明
            showLegalState();
        }

        return view;
    }

    private void initViews(View view) {
        legalLayout = view.findViewById(R.id.legalLayout);
        extractionLayout = view.findViewById(R.id.extractionLayout);
        componentsRecyclerView = view.findViewById(R.id.componentsRecyclerView);

        // 分别获取不同布局的按钮
        primaryButtonLegal = view.findViewById(R.id.primaryButtonLegal);
        secondaryButtonLegal = view.findViewById(R.id.secondaryButtonLegal);
        primaryButtonExtraction = view.findViewById(R.id.primaryButtonExtraction);

        overallProgressBar = view.findViewById(R.id.overallProgressBar);
        overallProgressText = view.findViewById(R.id.overallProgressText);

        // 设置按钮点击监听
        primaryButtonLegal.setOnClickListener(v -> handlePrimaryButtonClick());
        secondaryButtonLegal.setOnClickListener(v -> handleSecondaryButtonClick());
        primaryButtonExtraction.setOnClickListener(v -> handlePrimaryButtonClick());

        Log.d(TAG, "Views initialized");
    }

    private void setupComponentsList() {
        components = createComponentList();
        componentAdapter = new ComponentAdapter(components);
        componentsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        componentsRecyclerView.setAdapter(componentAdapter);
    }

    private List<ComponentItem> createComponentList() {
        List<ComponentItem> components = new ArrayList<>();
        components.add(new ComponentItem("dotnet8", "dotnet环境", "dotnet.zip"));
        return components;
    }

    private void showLegalState() {
        currentState = STATE_LEGAL;
        legalLayout.setVisibility(View.VISIBLE);
        extractionLayout.setVisibility(View.GONE);

        // 设置法律声明页面的按钮
        primaryButtonLegal.setText("接受并继续");
        secondaryButtonLegal.setText("退出应用");
        secondaryButtonLegal.setVisibility(View.VISIBLE);

        Log.d(TAG, "Showing legal state");

        // 添加动画效果
        animateViewEntrance(legalLayout);
    }

    private void showExtractionState() {
        currentState = STATE_EXTRACTION;
        legalLayout.setVisibility(View.GONE);
        extractionLayout.setVisibility(View.VISIBLE);

        // 设置解压页面的按钮
        primaryButtonExtraction.setText("同意并安装");
        primaryButtonExtraction.setEnabled(true);

        // 重置组件状态
        for (ComponentItem component : components) {
            component.setInstalled(false);
            component.setProgress(0);
        }
        componentAdapter.notifyDataSetChanged();

        // 重置进度
        overallProgressBar.setProgress(0);
        overallProgressText.setText("准备安装...");

        Log.d(TAG, "Showing extraction state");

        animateViewEntrance(extractionLayout);
    }

    private void handlePrimaryButtonClick() {
        Log.d(TAG, "Primary button clicked, current state: " + currentState);
        switch (currentState) {
            case STATE_LEGAL:
                acceptLegalAgreement();
                break;
            case STATE_EXTRACTION:
                startComponentsExtraction();
                break;
        }
    }

    private void handleSecondaryButtonClick() {
        Log.d(TAG, "Secondary button clicked");
        if (currentState == STATE_LEGAL) {
            // 退出应用
            requireActivity().finish();
        }
    }

    private void acceptLegalAgreement() {
        Log.d(TAG, "Accepting legal agreement");
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        prefs.edit().putBoolean(KEY_LEGAL_AGREED, true).apply();
        showExtractionState();
        // 同意协议后自动开始解压
        startComponentsExtraction();
    }

    private void startComponentsExtraction() {
        Log.d(TAG, "Starting components extraction");
        primaryButtonExtraction.setEnabled(false);
        primaryButtonExtraction.setText("安装中...");

        new Thread(() -> {
            try {
                extractComponents();

                // 在主线程中完成初始化
                new Handler(Looper.getMainLooper()).post(() -> {
                    completeInitialization();
                });
            } catch (Exception e) {
                Log.e(TAG, "Extraction failed", e);
                // 恢复按钮状态
                new Handler(Looper.getMainLooper()).post(() -> {
                    primaryButtonExtraction.setEnabled(true);
                    primaryButtonExtraction.setText("重试安装");
                    Toast.makeText(getActivity(), "解压失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 从assets解压zip文件到应用私有目录
     */
    private void extractComponents() throws Exception {
        Log.d(TAG, "Extracting components");
        int totalComponents = components.size();

        for (int i = 0; i < totalComponents; i++) {
            final int componentIndex = i;
            ComponentItem component = components.get(i);

            // 更新进度为开始解压
            updateComponentProgress(component, componentIndex, 10, "开始解压...");

            // 实际解压操作
            boolean success = extractZipFromAssets(component.getFileName(), component, componentIndex);

            if (success) {
                // 标记为已安装
                updateComponentProgress(component, componentIndex, 100, "解压完成");
                component.setInstalled(true);
                Log.d(TAG, "Component extraction successful: " + component.getName());
            } else {
                throw new Exception("解压 " + component.getName() + " 失败");
            }
        }
    }

    /**
     * 使用SevenZipJBinding从assets解压zip文件
     */
    private boolean extractZipFromAssets(String zipFileName, ComponentItem component, int componentIndex) {
        Log.d(TAG, "Extracting from assets using SevenZipJBinding: " + zipFileName);
        AssetManager assetManager = requireActivity().getAssets();

        File tempZipFile = null;
        try {
            // 1. 将assets中的zip文件复制到临时文件
            tempZipFile = new File(requireActivity().getCacheDir(), "temp_" + zipFileName);
            updateComponentProgress(component, componentIndex, 20, "准备解压文件...");

            // 复制assets文件到临时文件
            try (InputStream inputStream = assetManager.open(zipFileName);
                 FileOutputStream outputStream = new FileOutputStream(tempZipFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            updateComponentProgress(component, componentIndex, 30, "文件准备完成");

            // 2. 创建目标目录
            File outputDir = new File(requireActivity().getFilesDir(), "dotnet");
            if (outputDir.exists()) {
                deleteDirectory(outputDir);
            }
            outputDir.mkdirs();

            // 3. 使用SevenZipJBinding解压
            return extractWithSevenZipJBinding(tempZipFile, outputDir, component, componentIndex);

        } catch (Exception e) {
            Log.e(TAG, "SevenZipJBinding extraction error", e);
            return false;
        } finally {
            // 清理临时文件
            if (tempZipFile != null && tempZipFile.exists()) {
                tempZipFile.delete();
            }
        }
    }

    /**
     * 使用SevenZipJBinding解压文件
     */
    private boolean extractWithSevenZipJBinding(File zipFile, File targetDir,
                                                ComponentItem component, int componentIndex) {
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(zipFile, "r");
            RandomAccessFileInStream inStream = new RandomAccessFileInStream(randomAccessFile);

            try {
                IInArchive inArchive = SevenZip.openInArchive(null, inStream);

                try {
                    int totalItems = inArchive.getNumberOfItems();
                    Log.d(TAG, "Archive contains " + totalItems + " items");

                    // 创建自定义回调
                    AssetsExtractCallback callback = new AssetsExtractCallback(
                            inArchive, targetDir, component, componentIndex, totalItems);

                    // 提取所有文件
                    inArchive.extract(null, false, callback);

                    Log.d(TAG, "SevenZipJBinding extraction completed successfully");
                    return true;

                } finally {
                    inArchive.close();
                }
            } finally {
                inStream.close();
                randomAccessFile.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "SevenZipJBinding extraction failed", e);
            // 如果SevenZipJBinding解压失败，回退到原来的ZipInputStream方法
            Log.d(TAG, "Falling back to standard ZipInputStream extraction");
            return extractWithZipInputStream(zipFile, targetDir, component, componentIndex);
        }
    }

    /**
     * SevenZipJBinding 解压回调类
     */
    private class AssetsExtractCallback implements IArchiveExtractCallback {
        private final IInArchive inArchive;
        private final File targetDir;
        private final ComponentItem component;
        private final int componentIndex;
        private final int totalItems;
        private int processedItems = 0;

        public AssetsExtractCallback(IInArchive inArchive, File targetDir,
                                     ComponentItem component, int componentIndex, int totalItems) {
            this.inArchive = inArchive;
            this.targetDir = targetDir;
            this.component = component;
            this.componentIndex = componentIndex;
            this.totalItems = totalItems;
        }

        @Override
        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
            try {
                // 获取文件信息
                String filePath = inArchive.getStringProperty(index, PropID.PATH);
                boolean isFolder = (Boolean) inArchive.getProperty(index, PropID.IS_FOLDER);

                Log.d(TAG, "Processing item: " + filePath + " (isFolder: " + isFolder + ")");

                // 跳过文件夹，我们会在需要时创建
                if (isFolder) {
                    // 创建目录
                    File dir = new File(targetDir, filePath);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    return null;
                }

                // 处理文件
                File targetFile = new File(targetDir, filePath);

                // 安全检查：确保目标文件在目标目录内
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalEntryPath = targetFile.getCanonicalPath();

                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    throw new SevenZipException("ZIP条目在目标目录之外: " + filePath);
                }

                // 创建父目录
                File parent = targetFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                // 返回输出流
                return new AssetsSequentialOutStream(targetFile);

            } catch (Exception e) {
                throw new SevenZipException("Error getting stream for index " + index, e);
            }
        }

        @Override
        public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
            // 准备操作
        }

        @Override
        public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
            processedItems++;

            // 更新进度
            if (totalItems > 0) {
                int progress = 30 + (int) ((processedItems * 60.0) / totalItems);
                updateComponentProgress(component, componentIndex, progress,
                        "解压中 (" + processedItems + "/" + totalItems + ")");
            }

            if (extractOperationResult != ExtractOperationResult.OK) {
                Log.w(TAG, "Extraction operation result: " + extractOperationResult);
            }
        }

        @Override
        public void setTotal(long total) throws SevenZipException {
            Log.d(TAG, "Total bytes to extract: " + total);
        }

        @Override
        public void setCompleted(long complete) throws SevenZipException {
            // 可以在这里更新基于字节的进度
        }
    }

    /**
     * SevenZipJBinding 输出流实现
     */
    private class AssetsSequentialOutStream implements ISequentialOutStream {
        private final FileOutputStream outputStream;

        public AssetsSequentialOutStream(File targetFile) throws FileNotFoundException {
            this.outputStream = new FileOutputStream(targetFile);
        }

        @Override
        public int write(byte[] data) throws SevenZipException {
            try {
                outputStream.write(data);
                return data.length;
            } catch (IOException e) {
                throw new SevenZipException("Error writing to output stream", e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }

    /**
     * 回退方法：使用标准的ZipInputStream解压
     */
    private boolean extractWithZipInputStream(File zipFile, File targetDir,
                                              ComponentItem component, int componentIndex) {
        try {
            updateComponentProgress(component, componentIndex, 40, "使用备用方法解压...");

            FileInputStream fis = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(fis);

            // 统计总条目数
            int totalEntries = countZipEntriesFromFile(zipFile);
            if (totalEntries == 0) totalEntries = 1;

            ZipEntry entry;
            int entriesProcessed = 0;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                File outputFile = new File(targetDir, fileName);

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }

                zis.closeEntry();
                entriesProcessed++;

                // 更新进度
                int progress = 40 + (int) ((entriesProcessed * 50.0) / totalEntries);
                updateComponentProgress(component, componentIndex, progress,
                        "解压中 (" + entriesProcessed + "/" + totalEntries + ")");
            }

            zis.close();
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Fallback extraction failed", e);
            return false;
        }
    }

    /**
     * 从文件统计zip条目数量
     */
    private int countZipEntriesFromFile(File zipFile) {
        int count = 0;
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            while (zis.getNextEntry() != null) {
                count++;
                zis.closeEntry();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error counting zip entries from file", e);
        }
        return count;
    }

    /**
     * 删除目录及其内容
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
     * 更新组件进度
     */
    private void updateComponentProgress(ComponentItem component, int componentIndex, int progress, String status) {
        new Handler(Looper.getMainLooper()).post(() -> {
            component.setProgress(progress);
            componentAdapter.notifyItemChanged(componentIndex);

            // 更新总体进度
            int overallProgress = calculateOverallProgress();
            overallProgressBar.setProgress(overallProgress);
            overallProgressText.setText(String.format("%s (%d%%)", status, overallProgress));

            Log.d(TAG, "updateComponentProgress: progress=" + progress + ", overallProgress=" + overallProgress + ", status=" + status);
        });
    }

    private int calculateOverallProgress() {
        if (components.isEmpty()) return 0;

        int totalProgress = 0;
        for (ComponentItem component : components) {
            totalProgress += component.getProgress();
        }
        int overall = totalProgress / components.size();
        Log.d(TAG, "calculateOverallProgress: totalProgress=" + totalProgress + ", components.size()=" + components.size() + ", overall=" + overall);
        return overall;
    }

    private void completeInitialization() {
        Log.d(TAG, "Initialization complete");
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        prefs.edit().putBoolean(KEY_COMPONENTS_EXTRACTED, true).apply();

        // 显示成功消息
        Toast.makeText(getActivity(), "dotnet环境安装成功！", Toast.LENGTH_LONG).show();

        // 延迟一段时间后自动返回主界面
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (listener != null) {
                listener.onInitializationComplete();
            }
        }, 1500); // 1.5秒后返回
    }

    private void animateViewEntrance(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);

        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 确保全屏显示
        if (getActivity() != null) {
            getActivity().getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }
}