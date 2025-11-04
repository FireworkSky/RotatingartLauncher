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
import com.app.ralaunch.adapter.GameItem;
import com.app.ralaunch.game.Bootstrapper;
import com.app.ralaunch.game.BootstrapperManifest;
import com.app.ralaunch.utils.GameExtractor;
import com.app.ralaunch.utils.GameInfoParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

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
public class LocalImportFragment extends Fragment {

    private static final String TAG = "LocalImportFragment";

    private OnImportCompleteListener importCompleteListener;
    private OnBackListener backListener;

    // 界面控件
    private Button selectGameFileButton;
    private Button selectModLoaderButton;
    private Button selectBootstrapperButton;
    private Button startImportButton;
    private LinearLayout importProgressContainer;
    private ProgressBar importProgress;
    private TextView importStatus;
    private TextView gameFileText;
    private TextView modLoaderFileText;
    private TextView bootstrapperFileText;

    // 文件路径
    private String gameFilePath;
    private String modLoaderFilePath;
    private String bootstrapperFilePath;

    // 游戏信息 - 将从.sh文件中读取
    private String gameType = "modloader";
    private String gameName = null;  // 将从gameinfo读取
    private String gameVersion = null;  // 将从gameinfo读取
    private String gameIconPath = null;  // 将从gameinfo读取
    private String engineType = "FNA";
    public static File gameDir;

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
        selectModLoaderButton = view.findViewById(R.id.selectModLoaderButton);
        selectBootstrapperButton = view.findViewById(R.id.selectBootstrapperButton);
        startImportButton = view.findViewById(R.id.startImportButton);
        importProgressContainer = view.findViewById(R.id.importProgressContainer);
        importProgress = view.findViewById(R.id.importProgress);
        importStatus = view.findViewById(R.id.importStatus);
        gameFileText = view.findViewById(R.id.gameFileText);
        modLoaderFileText = view.findViewById(R.id.modLoaderFileText);
        bootstrapperFileText = view.findViewById(R.id.bootstrapperFileText);

        // 设置按钮点击事件
        selectGameFileButton.setOnClickListener(v -> selectGameFile());
        selectModLoaderButton.setOnClickListener(v -> selectModLoaderFile());
        selectBootstrapperButton.setOnClickListener(v -> selectBootstrapperFile());
        startImportButton.setOnClickListener(v -> startImport());

        // 初始状态
        updateImportButtonState();
    }

    private void selectGameFile() {
        openFileBrowser("game", new String[]{".sh"}, filePath -> {
            gameFilePath = filePath;
            File file = new File(gameFilePath);
            gameFileText.setText("已选择: " + file.getName());
            new Thread(() -> {
                GameInfoParser.GameInfo gameInfo = GameInfoParser.extractGameInfo(filePath);
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (gameInfo != null) {
                            gameName = gameInfo.name;
                            gameVersion = gameInfo.version;
                            gameIconPath = gameInfo.iconPath;
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("检测到游戏: " + gameName + " " + gameVersion);
                            }
                            Log.d(TAG, "Game info: " + gameInfo);
                            Log.d(TAG, "Icon path: " + gameIconPath);
                        } else {
                            gameName = "未知游戏";
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("无法读取游戏信息，使用默认名称");
                            }
                        }
                        updateImportButtonState();
                    });
                }
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
                Log.d("LocalImportFragment", "ModLoader base name: " + modLoaderBaseName);
            }

            modLoaderFileText.setText("已选择: " + fileName);
            updateImportButtonState();
        });
    }

    private void selectBootstrapperFile() {
        openFileBrowser("bootstrapper", new String[]{".zip"}, filePath -> {
            bootstrapperFilePath = filePath;
            File file = new File(bootstrapperFilePath);
            bootstrapperFileText.setText("已选择: " + file.getName());
            updateImportButtonState();
        });
    }

    private interface FileChosen { void onChosen(String path); }

    private void openFileBrowser(String type, String[] exts, FileChosen cb) {
        FileBrowserFragment f = new FileBrowserFragment();
        f.setFileType(type, exts);
        f.setOnFileSelectedListener((filePath, fileType) -> {
            cb.onChosen(filePath);
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        f.setOnBackListener(() -> requireActivity().getSupportFragmentManager().popBackStack());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, f)
                .addToBackStack("file_browser")
                .commit();
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
            ((MainActivity) getActivity()).showToast("请先选择游戏文件");
            return;
        }

        // 根据是否选择了 ModLoader 来确定游戏类型
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        gameType = hasModLoader ? "modloader" : "game"; // 纯游戏类型为 "game"

        // 显示进度容器
        importProgressContainer.setVisibility(View.VISIBLE);
        startImportButton.setEnabled(false);

        // 添加日志检查gameName的值
        Log.d(TAG, "startImport() - gameName: " + gameName);
        Log.d(TAG, "startImport() - gameVersion: " + gameVersion);
        Log.d(TAG, "startImport() - gameIconPath: " + gameIconPath);
        Log.d(TAG, "startImport() - gameFilePath: " + gameFilePath);
        Log.d(TAG, "startImport() - hasModLoader: " + hasModLoader);

        // 如果游戏信息丢失，重新解析
        if (gameName == null || gameVersion == null) {
            Log.w(TAG, "Game info lost, re-parsing...");
            importStatus.setText("正在读取游戏信息...");
            
            new Thread(() -> {
                GameInfoParser.GameInfo gameInfo = GameInfoParser.extractGameInfo(gameFilePath);
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (gameInfo != null) {
                            gameName = gameInfo.name;
                            gameVersion = gameInfo.version;
                            gameIconPath = gameInfo.iconPath;
                            Log.d(TAG, "Re-parsed game info: " + gameName + " " + gameVersion);
                            Log.d(TAG, "Re-parsed icon path: " + gameIconPath);

                            // 继续导入
                            continueImport();
                        } else {
                            importStatus.setText("无法读取游戏信息");
                            startImportButton.setEnabled(true);
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("无法读取游戏信息，导入失败");
                            }
                        }
                    });
                }
            }).start();
            return;
        }
        
        continueImport();
    }
    
    private void continueImport() {
        // 创建游戏目录
        gameDir = createGameDirectory();
        String outputPath = gameDir.getAbsolutePath();
        Log.d(TAG, "Created game directory: " + outputPath);

        // 复制图标到游戏目录
        if (gameIconPath != null) {
            Path iconSrc = Paths.get(gameIconPath);
            if (Files.exists(iconSrc) && Files.isRegularFile(iconSrc)) {
                try {
                    Path iconDest = Paths.get(gameDir.getAbsolutePath(), "icon.png");
                    Files.copy(iconSrc, iconDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    // 更新图标路径为游戏目录中的路径
                    gameIconPath = iconDest.toAbsolutePath().toString();
                    Log.d(TAG, "Icon copied to: " + gameIconPath);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy icon", e);
                }
            }
        }

        // 检查是否有 ModLoader
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        
        if (hasModLoader) {
            // 有 ModLoader，使用完整导入逻辑
            GameExtractor.installCompleteGame(gameFilePath, modLoaderFilePath, outputPath,
                    new GameExtractor.ExtractionListener() {
                        @Override
                        public void onProgress(String message, int progress) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText(message);
                                    importProgress.setProgress(progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText("导入完成！");
                                    importProgress.setProgress(100);

                                    // 根据模组zip文件名推断程序集名称
                                    File modLoaderDir = new File(modLoaderPath);
                                    File assemblyFile = null;

                                    if (modLoaderBaseName != null && !modLoaderBaseName.isEmpty()) {
                                        // 优先使用 zip 文件名推断的程序集名称
                                        String expectedDllName = modLoaderBaseName + ".dll";
                                        File expectedDll = new File(modLoaderDir, expectedDllName);

                                        if (expectedDll.exists()) {
                                            assemblyFile = expectedDll;
                                            Log.d(TAG, "Found ModLoader assembly based on zip name: " + expectedDllName);
                                        } else {
                                            Log.w(TAG, "Expected DLL not found: " + expectedDllName);
                                        }
                                    }

                                    // 如果基于zip名称没找到，尝试常见名称
                                    if (assemblyFile == null || !assemblyFile.exists()) {
                                        String[] possibleNames = {
                                            "tModLoader.dll",
                                            "ModLoader.dll",
                                            "Terraria.dll"
                                        };

                                        for (String name : possibleNames) {
                                            File candidate = new File(modLoaderDir, name);
                                            if (candidate.exists()) {
                                                assemblyFile = candidate;
                                                Log.d(TAG, "Found ModLoader assembly by fallback: " + name);
                                                break;
                                            }
                                        }
                                    }

                                    // 最后尝试查找目录中第一个 .dll 文件
                                    if (assemblyFile == null || !assemblyFile.exists()) {
                                        File[] dllFiles = modLoaderDir.listFiles((dir, name) -> name.endsWith(".dll"));
                                        if (dllFiles != null && dllFiles.length > 0) {
                                            assemblyFile = dllFiles[0];
                                            Log.d(TAG, "Using first DLL found: " + assemblyFile.getName());
                                        }
                                    }

                                    String finalGamePath = (assemblyFile != null && assemblyFile.exists())
                                        ? assemblyFile.getAbsolutePath()
                                        : modLoaderPath;

                                    if (assemblyFile == null || !assemblyFile.exists()) {
                                        Log.w(TAG, "No valid ModLoader DLL found, using directory path: " + modLoaderPath);
                                    }
                                
                                    // 保存游戏本体路径（根据gameinfo的游戏名称查找）
                                    String gameBodyPath = findGameBodyPath(gamePath);
                                    if (gameBodyPath != null) {
                                        Log.d(TAG, "Game body path: " + gameBodyPath);
                                    } else {
                                        Log.w(TAG, "Game body not found in: " + gamePath);
                                    }

                                    Log.d(TAG, "Final game path: " + finalGamePath);
                                    Log.d(TAG, "GameExtractor returned gamePath: " + gamePath);
                                    Log.d(TAG, "GameExtractor returned modLoaderPath: " + modLoaderPath);

                                    var newGame = new GameItem();
                                    newGame.setGameName(gameName);
                                    try {
                                        newGame.setGameBasePath(gameDir.getCanonicalPath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    newGame.setGamePath(finalGamePath);
                                    newGame.setGameBodyPath(gameBodyPath);
                                    newGame.setEngineType(engineType);
                                    newGame.setIconPath(gameIconPath);

                                    tryToImportBootstrapper(newGame);

                                    // 导入完成，返回结果
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
                                    importStatus.setText("导入失败: " + error);
                                    if (getActivity() != null) {
                                        ((MainActivity) getActivity()).showToast("导入失败: " + error);
                                    }
                                    startImportButton.setEnabled(true);
                                });
                            }
                        }
                    });
        } else {
            // 没有 ModLoader，只安装纯游戏
            GameExtractor.installGameOnly(gameFilePath, outputPath,
                    new GameExtractor.ExtractionListener() {
                        @Override
                        public void onProgress(String message, int progress) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText(message);
                                    importProgress.setProgress(progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText("导入完成！");
                                    importProgress.setProgress(100);

                                    // 纯游戏，根据 gameinfo 中的游戏名称查找程序集
                                    String finalGamePath = findGameBodyPath(gamePath);

                                    if (finalGamePath == null) {
                                        Log.w("LocalImportFragment", "Game executable not found, using directory path");
                                        finalGamePath = gamePath;
                                    }

                                    Log.d(TAG, "Pure game path: " + finalGamePath);

                                    var newGame = new GameItem();
                                    newGame.setGameName(gameName);
                                    try {
                                        newGame.setGameBasePath(gameDir.getCanonicalPath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    newGame.setGamePath(finalGamePath);
                                    newGame.setEngineType(engineType);
                                    newGame.setIconPath(gameIconPath);

                                    tryToImportBootstrapper(newGame);

                                    // 导入完成，返回结果（纯游戏没有 gameBodyPath）
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
                                    importStatus.setText("导入失败: " + error);
                                    if (getActivity() != null) {
                                        ((MainActivity) getActivity()).showToast("导入失败: " + error);
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
        if (gamePath == null || gameName == null) {
            return null;
        }

        File gameDir = new File(gamePath);
        if (!gameDir.exists() || !gameDir.isDirectory()) {
            return null;
        }

        // 尝试的文件扩展名
        String[] extensions = {".exe", ".dll"};

        // 1. 优先使用游戏名称精确匹配
        for (String ext : extensions) {
            File gameFile = new File(gameDir, gameName + ext);
            if (gameFile.exists()) {
                Log.d("LocalImportFragment", "Found game body by exact name: " + gameFile.getName());
                return gameFile.getAbsolutePath();
            }
        }

        // 2. 尝试游戏名称的常见变体（去除空格、转小写等）
        String normalizedName = gameName.replaceAll("\\s+", ""); // 去除所有空格
        for (String ext : extensions) {
            File gameFile = new File(gameDir, normalizedName + ext);
            if (gameFile.exists()) {
                Log.d("LocalImportFragment", "Found game body by normalized name: " + gameFile.getName());
                return gameFile.getAbsolutePath();
            }
        }

        // 3. 尝试常见的游戏本体名称
        String[] commonNames = {
            "Terraria",      // Terraria
            "Stardew Valley", // Stardew Valley
            "Game",          // 通用名称
            gameName         // 原始游戏名称
        };

        for (String name : commonNames) {
            for (String ext : extensions) {
                File gameFile = new File(gameDir, name + ext);
                if (gameFile.exists()) {
                    Log.d("LocalImportFragment", "Found game body by common name: " + gameFile.getName());
                    return gameFile.getAbsolutePath();
                }
            }
        }

        // 4. 查找目录中第一个 .exe 文件
        File[] exeFiles = gameDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".exe"));
        if (exeFiles != null && exeFiles.length > 0) {
            Log.d("LocalImportFragment", "Found game body by first .exe: " + exeFiles[0].getName());
            return exeFiles[0].getAbsolutePath();
        }

        // 5. 查找目录中第一个 .dll 文件
        File[] dllFiles = gameDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".dll"));
        if (dllFiles != null && dllFiles.length > 0) {
            Log.d("LocalImportFragment", "Found game body by first .dll: " + dllFiles[0].getName());
            return dllFiles[0].getAbsolutePath();
        }

        Log.w("LocalImportFragment", "Could not find game body in: " + gamePath);
        return null;
    }

    private boolean tryToImportBootstrapper(GameItem newGame) {
        try {
            boolean hasBootstrapper = bootstrapperFilePath != null && !bootstrapperFilePath.isEmpty();
            if (hasBootstrapper && Files.exists(Paths.get(bootstrapperFilePath)) && Files.isRegularFile(Paths.get(bootstrapperFilePath))) {
                Log.d(TAG, "Bootstrapper selected: " + bootstrapperFilePath);
                // 处理 Bootstrapper 的逻辑（如果需要）
                // 目前假设 Bootstrapper 不影响导入流程
                var manifest = BootstrapperManifest.FromZip(bootstrapperFilePath);
                if (manifest == null) {
                    Log.e(TAG, "Failed to read bootstrapper manifest");
                    return false;
                }

                if (!Bootstrapper.ExtractBootstrapper(bootstrapperFilePath, newGame.getGameBasePath())) {
                    Log.e(TAG, "Failed to extract bootstrapper");
                    return false;
                }

                newGame.setBootstrapperPresent(true);
                newGame.setBootstrapperBasePath(manifest.getExtractDirectory());
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract bootstrapper", e);
        }
        return false;
    }

    private File createGameDirectory() {
        File externalDir = MainActivity.mainActivity.getExternalFilesDir(null);

        // 创建更结构化的目录
        File gamesDir = new File(externalDir, "games");
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }

        // 使用游戏名称和版本号（如果有）
        String dirName = gameName != null ? gameName : "Unknown";
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
}
