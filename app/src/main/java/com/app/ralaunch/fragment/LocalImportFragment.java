package com.app.ralaunch.fragment;

import android.os.Bundle;
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
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.AssemblyChecker;
import com.app.ralaunch.utils.GameExtractor;
import com.app.ralaunch.utils.GamePathResolver;
import com.app.ralib.error.ErrorHandler;
import com.app.ralib.extractors.GogShFileExtractor;
import com.app.ralib.icon.IconExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * 本地导入Fragment
 *
 * 处理从本地导入游戏的完整流程：
 * - 选择游戏文件和 ModLoader 文件
 * - 解压游戏压缩包
 * - 提取游戏信息
 * - 显示导入进度
 * - 处理导入错误
 *
 * 使用 GameExtractor 执行实际的解压和导入操作
 */
public class LocalImportFragment extends BaseFragment {

    private static final String TAG = "LocalImportFragment";

    private OnImportCompleteListener importCompleteListener;
    private OnBackListener backListener;

    // 界面控件
    private Button selectGameFileButton;
    private Button selectModLoaderButton;
    private Button startImportButton;
    private LinearLayout importProgressContainer;
    private com.google.android.material.progressindicator.LinearProgressIndicator progressIndicator;
    private TextView statusText;
    private TextView progressText;
    private TextView detailText;
    private TextView progressInfoText;
    private TextView gameFileText;
    private TextView modLoaderFileText;

    // 文件路径
    private String gameFilePath;
    private String modLoaderFilePath;

    // 游戏信息 - 将从.sh文件中读取
    private String gameType = "game";  // 通用游戏类型
    private String gameName = null;  // 将从gameinfo读取
    private String gameVersion = null;  // 将从gameinfo读取
    private String gameIconPath = null;  // 将从gameinfo读取
    private String engineType = "FNA";
    public static File gameDir;
    
    // 防止重复导入的标志
    private boolean isImporting = false;

    public interface OnImportCompleteListener {
        void onImportComplete(String gameType, GameItem newGame);
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

        // 从 Bundle 中获取传递的参数
        Bundle args = getArguments();
        if (args != null) {
            gameFilePath = args.getString("gameFilePath");
            modLoaderFilePath = args.getString("modLoaderFilePath");
            gameName = args.getString("gameName");
            gameVersion = args.getString("gameVersion");
        }

        setupUI(view);

        // 如果已经有文件路径，自动开始导入
        if (gameFilePath != null && !gameFilePath.isEmpty()) {
            // 隐藏选择按钮区域，直接显示进度
            view.post(() -> {
                selectGameFileButton.setVisibility(View.GONE);
                selectModLoaderButton.setVisibility(View.GONE);
                startImportButton.setVisibility(View.GONE);
                gameFileText.setVisibility(View.GONE);
                modLoaderFileText.setVisibility(View.GONE);

                // 自动开始导入
                startImport();
            });
        }

        return view;
    }

    private void setupUI(View view) {
        // 新布局不需要返回按钮（通过 NavigationRail 导航）

        // 初始化控件
        selectGameFileButton = view.findViewById(R.id.selectGameFileButton);
        selectModLoaderButton = view.findViewById(R.id.selectModLoaderButton);
        startImportButton = view.findViewById(R.id.startImportButton);
        importProgressContainer = view.findViewById(R.id.importProgressContainer);
        progressIndicator = view.findViewById(R.id.progressIndicator);
        statusText = view.findViewById(R.id.statusText);
        progressText = view.findViewById(R.id.progressText);
        detailText = view.findViewById(R.id.detailText);
        progressInfoText = view.findViewById(R.id.progressInfoText);
        gameFileText = view.findViewById(R.id.gameFileText);
        modLoaderFileText = view.findViewById(R.id.modLoaderFileText);

        // 设置按钮点击事件
        selectGameFileButton.setOnClickListener(v -> selectGameFile());
        selectModLoaderButton.setOnClickListener(v -> selectModLoaderFile());
        startImportButton.setOnClickListener(v -> startImport());

        // 初始状态
        updateImportButtonState();
    }

    /**
     * 更新进度显示（高级格式）
     */
    private void updateProgress(String message, int progress) {
        if (progressIndicator != null) {
            progressIndicator.setProgress(progress);
        }
        if (statusText != null) {
            statusText.setText(message);
        }
        if (progressText != null) {
            // 显示带小数点的百分比（例如：45.7%）
            // 如果进度是整数，显示整数；否则显示一位小数
            String progressStr;
            if (progress % 10 == 0) {
                progressStr = String.format("%d%%", progress);
            } else {
                progressStr = String.format("%.1f%%", (float) progress);
            }
            progressText.setText(progressStr);
        }
        if (progressInfoText != null) {
            progressInfoText.setText(message);
        }
        // 更新详细信息（显示进度百分比，格式：45.7%）
        if (detailText != null) {
            String detail;
            if (progress == 100) {
                detail = getString(R.string.init_complete);
            } else if (progress == 0) {
                detail = getString(R.string.init_start_extracting);
            } else {
                detail = String.format("%.1f%%", (float) progress);
            }
            detailText.setText(detail);
        }
    }

    /**
     * 更新详细信息（可选）
     */
    private void updateDetail(String detail) {
        if (detailText != null) {
            detailText.setText(detail);
        }
    }

    private void selectGameFile() {
        openFileBrowser("game", new String[]{".sh"}, filePath -> {
            gameFilePath = filePath;
            File file = new File(gameFilePath);
            
            // 确保UI更新在主线程执行
            runOnUiThread(() -> {
                if (isFragmentValid()) {
                    gameFileText.setText(getString(R.string.import_file_selected, file.getName()));
                    updateImportButtonState();
                }
            });
            
            // 在后台线程解析游戏信息
            new Thread(() -> {
                var gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(Paths.get(filePath));
                runOnUiThread(() -> {
                    if (isFragmentValid()) {
                        if (gdzf != null) {
                            gameName = gdzf.id;
                            gameVersion = gdzf.version;
                            gameIconPath = null; // TODO: 从 gdzf 提取图标路径
                            showToast(getString(R.string.import_game_detected, gameName, gameVersion));
                        } else {
                            gameName = getString(R.string.import_unknown_game);
                            showToast(getString(R.string.import_cannot_read_info));
                        }
                    }
                });
            }).start();
        });
    }

    // 保存模组加载器的文件名（不含扩展名），用于推断程序集名称
    private String modLoaderBaseName = null;

    private void selectModLoaderFile() {
        openFileBrowser("modloader", new String[]{".zip"}, filePath -> {
            modLoaderFilePath = filePath;
            File file = new File(modLoaderFilePath);

            // 提取文件名（不含扩展名）
            String fileName = file.getName();
            if (fileName.toLowerCase().endsWith(".zip")) {
                modLoaderBaseName = fileName.substring(0, fileName.length() - 4);
            }

            // 确保UI更新在主线程执行
            runOnUiThread(() -> {
                if (isFragmentValid()) {
                    modLoaderFileText.setText(getString(R.string.import_file_selected, fileName));
                    updateImportButtonState();
                }
            });
        });
    }

  

    private interface FileChosen { void onChosen(String path); }

    private void openFileBrowser(String type, String[] exts, FileChosen cb) {
        // 直接打开文件浏览器
        MainActivity mainActivity = getMainActivity();
        if (mainActivity == null) {
            return;
        }

        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        if (type != null && exts != null) {
            fileBrowserFragment.setFileType(type, exts);
        }

        fileBrowserFragment.setOnFileSelectedListener((filePath, fileType) -> {
            cb.onChosen(filePath);
            mainActivity.onFragmentBack();
        });

        fileBrowserFragment.setOnBackListener(mainActivity::onFragmentBack);
        mainActivity.getFragmentNavigator().showFragment(fileBrowserFragment, "file_browser");
    }
    
    private MainActivity getMainActivity() {
        if (isAdded() && getActivity() instanceof MainActivity) {
            return (MainActivity) getActivity();
        }
        return null;
    }

    private void updateImportButtonState() {
        boolean hasGameFile = gameFilePath != null && !gameFilePath.isEmpty();

        // 只需要游戏文件即可导入，ModLoader 是可选的
        startImportButton.setEnabled(hasGameFile);

        if (hasGameFile) {
            startImportButton.setAlpha(1.0f);
        } else {
            startImportButton.setAlpha(0.5f);
        }
    }

    private void startImport() {
        if (gameFilePath == null) {
            showToast(getString(R.string.import_select_game_first));
            return;
        }
        
        if (isImporting) {
            return;
        }
        isImporting = true;

        // 根据是否选择了 ModLoader 来确定游戏类型
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        gameType = hasModLoader ? "modloader" : "game"; // 纯游戏类型为 "game"

        // 显示进度容器
        importProgressContainer.setVisibility(View.VISIBLE);
        startImportButton.setEnabled(false);

        if (gameName == null || gameVersion == null) {
            updateProgress(getString(R.string.import_reading_info), 0);

            new Thread(() -> {
                var gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(Paths.get(gameFilePath));
                runOnUiThread(() -> {
                    if (isFragmentValid()) {
                        if (gdzf != null) {
                            gameName = gdzf.id;
                            gameVersion = gdzf.version;
                            gameIconPath = null;

                            // 继续导入
                            continueImport();
                        } else {
                            updateProgress(getString(R.string.import_cannot_read_info_failed), 0);
                            startImportButton.setEnabled(true);
                            showToast(getString(R.string.import_cannot_read_info_failed));
                        }
                    }
                });
            }).start();
            return;
        }

        continueImport();
    }

    private void continueImport() {
        // 确定目录名称：优先使用 ModLoader 名称
        String directoryBaseName = gameName; // 默认使用游戏名称
        
        // 如果有 ModLoader，尝试从文件名提取名称
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        if (hasModLoader && modLoaderBaseName != null && !modLoaderBaseName.isEmpty()) {
            directoryBaseName = modLoaderBaseName;
        } else if (hasModLoader) {
            try {
                String modLoaderFileName = new File(modLoaderFilePath).getName();
                if (modLoaderFileName.endsWith(".zip")) {
                    directoryBaseName = modLoaderFileName.substring(0, modLoaderFileName.length() - 4);
                } else if (modLoaderFileName.contains(".")) {
                    directoryBaseName = modLoaderFileName.substring(0, modLoaderFileName.lastIndexOf('.'));
                } else {
                    directoryBaseName = modLoaderFileName;
                }
            } catch (Exception e) {
            }
        }
        
        // 创建游戏目录
        gameDir = createGameDirectory(directoryBaseName);
        String outputPath = gameDir.getAbsolutePath();

        // 复制图标到游戏目录
        if (gameIconPath != null) {
            Path iconSrc = Paths.get(gameIconPath);
            if (Files.exists(iconSrc) && Files.isRegularFile(iconSrc)) {
                try {
                    Path iconDest = Paths.get(gameDir.getAbsolutePath(), "icon.png");
                    Files.copy(iconSrc, iconDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    // 更新图标路径为游戏目录中的路径
                    gameIconPath = iconDest.toAbsolutePath().toString();
                } catch (Exception e) {
                    AppLogger.error(TAG, "Failed to copy icon", e);
                }
            }
        }

        // 开始导入流程（hasModLoader 已在开头定义）
        if (hasModLoader) {
            // 有 ModLoader，使用完整导入逻辑
            GameExtractor.installCompleteGame(gameFilePath, modLoaderFilePath, outputPath,
                    new GameExtractor.ExtractionListener() {
                        @Override
                        public void onProgress(String message, int progress) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    updateProgress(message, progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    isImporting = false; // 重置导入标志
                                    updateProgress(getString(R.string.import_complete_exclamation), 100);

                                    File modLoaderDir = new File(modLoaderPath);
                                    File assemblyFile = null;
                                    boolean foundModLoaderEntry = false;

                                    if (modLoaderDir != null && modLoaderDir.exists()) {
                                        try {
                                            AssemblyChecker.CheckResult checkResult =
                                                    AssemblyChecker.searchDirectoryForAssemblyWithIcon(getContext(), modLoaderDir.getAbsolutePath());

                                            if (checkResult != null && checkResult.exists) {
                                                assemblyFile = new File(checkResult.assemblyPath);
                                                foundModLoaderEntry = true;
                                            }
                                        } catch (Exception e) {
                                            AppLogger.error(TAG, "搜索程序集失败", e);
                                        }
                                    }

                                    String gameBodyPath = findGameBodyPath(gamePath);
                                    String finalGamePath;
                                    String iconSourcePath;
                                    String displayName;

                                    if (foundModLoaderEntry && assemblyFile != null && assemblyFile.exists()) {
                                        finalGamePath = assemblyFile.getAbsolutePath();
                                        iconSourcePath = finalGamePath;

                                        if (modLoaderBaseName != null && !modLoaderBaseName.isEmpty()) {
                                            displayName = modLoaderBaseName;
                                        } else {
                                            String modLoaderName = assemblyFile.getName().replace(".dll", "").replace(".exe", "");
                                            displayName = modLoaderName;
                                        }
                                    } else {
                                        finalGamePath = (gameBodyPath != null) ? gameBodyPath : gamePath;
                                        iconSourcePath = finalGamePath;
                                        displayName = gameName;
                                    }

                                    // 创建 GameItem
                                    var newGame = new GameItem();
                                    newGame.setGameName(displayName);
                                    try {
                                        newGame.setGameBasePath(gameDir.getCanonicalPath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    newGame.setGamePath(finalGamePath);
                                    newGame.setGameBodyPath(gameBodyPath);
                                    newGame.setEngineType(engineType);

                                    // 设置 ModLoader 状态
                                    newGame.setModLoaderEnabled(foundModLoaderEntry);

                                    // 提取图标
                                    String extractedIconPath = extractIconFromExecutable(iconSourcePath, gameIconPath);
                                    newGame.setIconPath(extractedIconPath);

                                    if (importCompleteListener != null) {
                                        importCompleteListener.onImportComplete(gameType, newGame);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    isImporting = false; // 重置导入标志
                                    updateProgress(getString(R.string.import_failed_colon, error), 0);
                                    if (getActivity() != null) {
                                        // 使用 RALib 的错误弹窗代替普通 Toast
                                        ErrorHandler.showWarning(getString(R.string.import_error, ""), error);
                                    }
                                    startImportButton.setEnabled(true);
                                });
                            }
                        }
                    });
        } else {
            GameExtractor.installGameOnly(gameFilePath, outputPath,
                    new GameExtractor.ExtractionListener() {
                        @Override
                        public void onProgress(String message, int progress) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    updateProgress(message, progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    isImporting = false; // 重置导入标志
                                    updateProgress(getString(R.string.import_complete_exclamation), 100);


                                    String finalGamePath;
                                    String gameBodyPath = null;


                                        finalGamePath = findGameBodyPath(gamePath);

                                        if (finalGamePath == null) {
                                            finalGamePath = gamePath;
                                        }

                                    var newGame = new GameItem();
                                    String iconSourcePath;
                                    String displayName;

                                        iconSourcePath = finalGamePath;
                                        displayName = gameName;


                                    newGame.setGameName(displayName);
                                    try {
                                        newGame.setGameBasePath(new File(gamePath).getCanonicalPath());
                                    } catch (IOException e) {
                                        // 使用错误处理器显示错误，而不是抛出运行时异常
                                        ErrorHandler.handleError(getString(R.string.import_error_game_import_failed), e);
                                        return; // 错误已处理，退出导入流程
                                    }
                                    newGame.setGamePath(finalGamePath);



                                    newGame.setEngineType(engineType);

                                    // 从正确的程序集中提取图标
                                    String extractedIconPath = extractIconFromExecutable(iconSourcePath, gameIconPath);
                                    newGame.setIconPath(extractedIconPath);


                                    if (importCompleteListener != null) {
                                        importCompleteListener.onImportComplete(gameType, newGame);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    isImporting = false; // 重置导入标志
                                    updateProgress(getString(R.string.import_failed_colon, error), 0);
                                    if (getActivity() != null) {
                                        // 使用 RALib 的错误弹窗代替普通 Toast
                                        ErrorHandler.showWarning(getString(R.string.import_error, ""), error);
                                    }
                                    startImportButton.setEnabled(true);
                                });
                            }
                        }
                    });
        }
    }

    /**
     * 根据 gameinfo 中的游戏名称查找游戏本体路径
     * 支持 .exe 和 .dll 两种格式
     *
     * @param gamePath 游戏目录路径
     * @return 游戏本体的完整路径，如果找不到则返回 null
     */
    private String findGameBodyPath(String gamePath) {
        return GamePathResolver.findGameBodyPath(gamePath);
    }

    // tryToImportBootstrapper 方法已移除

    private File createGameDirectory(String baseName) {
        File externalDir = MainActivity.mainActivity.getExternalFilesDir(null);

        // 创建更结构化的目录
        File gamesDir = new File(externalDir, "games");
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }

        // 使用传入的基础名称（可能是游戏名称或 ModLoader 名称）和版本号（如果有）
        String dirName = baseName != null ? baseName : (gameName != null ? gameName : "Unknown");
        if (gameVersion != null) {
            dirName += "_" + gameVersion;
        }
        dirName += "_" + System.currentTimeMillis();

        File gameDir = new File(gamesDir, dirName);
        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }

        return gameDir;
    }

    /**
     * 尝试从游戏程序集中提取图标
     *
     * @param exePath 游戏可执行文件路径（.exe或.dll）
     * @param fallbackIconPath 回退的图标路径（GOG的icon.png）
     * @return 提取的图标路径，如果提取失败则返回fallbackIconPath
     */
    private String extractIconFromExecutable(String exePath, String fallbackIconPath) {
        if (exePath == null || exePath.isEmpty()) {
            return fallbackIconPath;
        }

        File exeFile = new File(exePath);
        if (!exeFile.exists()) {
            return fallbackIconPath;
        }

        // 如果是 .dll 文件，尝试查找 .exe 文件（只支持Windows PE格式）
        String tryPath = exePath;
        if (exePath.toLowerCase().endsWith(".dll")) {
            File gameDir = exeFile.getParentFile();
            String baseName = exeFile.getName().substring(0, exeFile.getName().length() - 4);

            // 尝试 .exe (Windows)
            File winExe = new File(gameDir, baseName + ".exe");
            if (winExe.exists()) {
                tryPath = winExe.getAbsolutePath();
            }
        }

        try {
            // 直接使用 IconExtractor 提取图标
            File gameFile = new File(tryPath);
            if (!gameFile.exists()) {
                return fallbackIconPath;
            }
            
            // 生成输出路径：在游戏文件旁边创建 xxx_icon.png
            String nameWithoutExt = gameFile.getName().replaceAll("\\.[^.]+$", "");
            String iconPath = gameFile.getParent() + File.separator + nameWithoutExt + "_icon.png";
            
            boolean success = IconExtractor.extractIconToPng(tryPath, iconPath);
            String extractedIconPath = null;
            
            if (success) {
                File iconFile = new File(iconPath);
                if (iconFile.exists() && iconFile.length() > 0) {
                    extractedIconPath = iconPath;
                }
            }
            
            if (extractedIconPath != null && new File(extractedIconPath).exists()) {
                // 检查提取的图标大小，如果太小则高清化
                File iconFile = new File(extractedIconPath);
                long fileSize = iconFile.length();

                if (fileSize < 5 * 1024) {
                    String upscaledPath = IconExtractor.upscaleIcon(getContext(), extractedIconPath);
                    if (upscaledPath != null) {
                        return upscaledPath;
                    } else if (fallbackIconPath != null) {
                        return fallbackIconPath;
                    }
                }

                return extractedIconPath;
            } else {
                return fallbackIconPath;
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to extract icon from executable: " + e.getMessage(), e);
            return fallbackIconPath;
        }
    }


    private File findFileRecursively(File dir, String targetName) {
        return GamePathResolver.findFileRecursively(dir, targetName);
    }

    private File findFirstFileRecursively(File dir, java.util.function.Predicate<String> predicate) {
        return GamePathResolver.findFirstFileRecursively(dir, predicate);
    }

    /**
     * 递归查找第一个有图标的 DLL/EXE 文件（递归所有子目录，无深度限制）
     */
    private File findFirstFileWithIconRecursively(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }


        // 先搜索当前目录
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            
            String fileName = file.getName().toLowerCase();
            // 只检查 DLL 和 EXE 文件
            if (!fileName.endsWith(".dll") && !fileName.endsWith(".exe")) {
                continue;
            }
            
            // 跳过系统 DLL
            if (fileName.startsWith("api-ms-win-") || 
                fileName.equals("kernel32.dll") ||
                fileName.equals("msvcrt.dll")) {
                continue;
            }
            
            // 检查是否有图标
            boolean hasIcon = com.app.ralib.icon.IconExtractor.hasIcon(file.getAbsolutePath());
            if (hasIcon) {
                return file;
            }
        }

        // 递归搜索所有子目录（无深度限制）
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFirstFileWithIconRecursively(file);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * 递归查找第一个 DLL/EXE 文件（递归所有子目录，无深度限制，跳过系统 DLL）
     */
    private File findFirstAssemblyFileRecursively(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }


        // 先搜索当前目录
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            
            String fileName = file.getName().toLowerCase();
            // 只检查 DLL 和 EXE 文件
            if (!fileName.endsWith(".dll") && !fileName.endsWith(".exe")) {
                continue;
            }
            
            // 跳过系统 DLL
            if (fileName.startsWith("api-ms-win-") || 
                fileName.equals("kernel32.dll") ||
                fileName.equals("msvcrt.dll") ||
                fileName.contains("system.") ||  // 跳过 System.*.dll
                fileName.contains("microsoft.")) { // 跳过 Microsoft.*.dll
                continue;
            }
            
            return file;
        }

        // 递归搜索所有子目录（无深度限制）
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFirstAssemblyFileRecursively(file);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

}