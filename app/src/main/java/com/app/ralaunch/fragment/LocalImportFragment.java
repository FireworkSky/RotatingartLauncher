package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.activity.MainActivity;
import com.app.ralaunch.utils.GameExtractor;

import java.io.File;

public class LocalImportFragment extends Fragment {

    private OnImportCompleteListener importCompleteListener;
    private OnBackListener backListener;

    // 界面控件
    private Button selectGameFileButton;
    private Button selectTmodloaderButton;
    private Button startImportButton;
    private LinearLayout importProgressContainer;
    private ProgressBar importProgress;
    private TextView importStatus;
    private TextView gameFileText;
    private TextView tmodloaderFileText;

    // 文件路径
    private String gameFilePath;
    private String tmodloaderFilePath;

    // 游戏信息 - 固定为tmodloader
    private String gameType = "tmodloader";
    private String gameName = "tModLoader";
    private String engineType = "FNA";
    public static File gameDir;

    public interface OnImportCompleteListener {
        void onImportComplete(String gameType, String gameName, String gamePath, String engineType);
    }

    public interface OnBackListener {
        void onBack();
    }

    public void setOnImportCompleteListener(OnImportCompleteListener listener) {
        this.importCompleteListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_import, container, false);
        setupUI(view);
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

        // 初始化控件
        selectGameFileButton = view.findViewById(R.id.selectGameFileButton);
        selectTmodloaderButton = view.findViewById(R.id.selectTmodloaderButton);
        startImportButton = view.findViewById(R.id.startImportButton);
        importProgressContainer = view.findViewById(R.id.importProgressContainer);
        importProgress = view.findViewById(R.id.importProgress);
        importStatus = view.findViewById(R.id.importStatus);
        gameFileText = view.findViewById(R.id.gameFileText);
        tmodloaderFileText = view.findViewById(R.id.tmodloaderFileText);

        // 设置按钮点击事件
        selectGameFileButton.setOnClickListener(v -> selectGameFile());
        selectTmodloaderButton.setOnClickListener(v -> selectTmodloaderFile());
        startImportButton.setOnClickListener(v -> startImport());

        // 初始状态
        updateImportButtonState();
    }

    private void selectGameFile() {
        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        fileBrowserFragment.setFileType("game", new String[]{".sh"});
        fileBrowserFragment.setOnFileSelectedListener((filePath, fileType) -> {
            gameFilePath = filePath;
            File file = new File(gameFilePath);
            gameFileText.setText("已选择: " + file.getName());
            updateImportButtonState();

            // 返回到本地导入页面
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        fileBrowserFragment.setOnBackListener(() -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fileBrowserFragment)
                .addToBackStack("file_browser")
                .commit();
    }

    private void selectTmodloaderFile() {
        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        fileBrowserFragment.setFileType("tmodloader", new String[]{".zip"});
        fileBrowserFragment.setOnFileSelectedListener((filePath, fileType) -> {
            tmodloaderFilePath = filePath;
            File file = new File(tmodloaderFilePath);
            tmodloaderFileText.setText("已选择: " + file.getName());
            updateImportButtonState();

            // 返回到本地导入页面
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        fileBrowserFragment.setOnBackListener(() -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fileBrowserFragment)
                .addToBackStack("file_browser")
                .commit();
    }

    private void updateImportButtonState() {
        boolean hasGameFile = gameFilePath != null && !gameFilePath.isEmpty();
        boolean hasTmodloaderFile = tmodloaderFilePath != null && !tmodloaderFilePath.isEmpty();

        startImportButton.setEnabled(hasGameFile && hasTmodloaderFile);
        
        if (hasGameFile && hasTmodloaderFile) {
            startImportButton.setAlpha(1.0f);
        } else {
            startImportButton.setAlpha(0.5f);
        }
    }

    private void startImport() {
        if (gameFilePath == null || tmodloaderFilePath == null) {
            ((MainActivity) getActivity()).showToast("请先选择所有必需的文件");
            return;
        }

        // 显示进度容器
        importProgressContainer.setVisibility(View.VISIBLE);
        startImportButton.setEnabled(false);

        // 创建游戏目录
        gameDir = createGameDirectory();
        String outputPath = gameDir.getAbsolutePath();

        // 使用新的解压逻辑
        GameExtractor.installCompleteGame(gameFilePath, tmodloaderFilePath, outputPath,

                new GameExtractor.ExtractionListener() {
                    @Override
                    public void onProgress(String message, int progress) {
                        requireActivity().runOnUiThread(() -> {
                            importStatus.setText(message);
                            importProgress.setProgress(progress);
                        });
                    }

                    // 在 onComplete 回调中，确保返回正确的程序集路径
                    @Override
                    public void onComplete(String gamePath, String tmodloaderPath) {
                        requireActivity().runOnUiThread(() -> {
                            importStatus.setText("导入完成！");
                            importProgress.setProgress(100);

                            // 构建正确的程序集路径
                            String finalGamePath;
                            if ("tmodloader".equals(gameType)) {
                                // 对于 tmodloader，构建 tModLoader.dll 的完整路径
                                File tmodloaderDir = new File(tmodloaderPath);
                                File assemblyFile = new File(tmodloaderDir, "tModLoader.dll");
                                finalGamePath = assemblyFile.getAbsolutePath();

                                // 验证程序集文件是否存在
                                if (!assemblyFile.exists()) {
                                    Log.w("LocalImportFragment", "tModLoader.dll not found at: " + finalGamePath);
                                    // 如果找不到，回退到目录路径
                                    finalGamePath = tmodloaderPath;
                                }
                            } else {
                                // 对于其他游戏，使用游戏路径
                                finalGamePath = gamePath;
                            }

                            Log.d("LocalImportFragment", "Final game path: " + finalGamePath);
                            Log.d("LocalImportFragment", "GameExtractor returned gamePath: " + gamePath);
                            Log.d("LocalImportFragment", "GameExtractor returned tmodloaderPath: " + tmodloaderPath);

                            // 导入完成，返回结果
                            if (importCompleteListener != null) {
                                importCompleteListener.onImportComplete(gameType, gameName, finalGamePath, engineType);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        requireActivity().runOnUiThread(() -> {
                            importStatus.setText("导入失败: " + error);
                            ((MainActivity) getActivity()).showToast("导入失败: " + error);
                            startImportButton.setEnabled(true);
                        });
                    }
                });
    }

    private File createGameDirectory() {
        File externalDir = MainActivity.mainActivity.getExternalFilesDir(null);

        // 创建更结构化的目录
        File gamesDir = new File(externalDir, "games");
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }

        File gameDir = new File(gamesDir, gameName + "_" + System.currentTimeMillis());
        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }

        return gameDir;
    }
}
