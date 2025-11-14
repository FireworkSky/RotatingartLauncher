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
import com.app.ralaunch.utils.GameExtractor;
import com.app.ralaunch.utils.IconExtractorHelper;
import com.app.ralib.extractors.GogShFileExtractor;

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
public class LocalImportFragment extends Fragment {

    private static final String TAG = "LocalImportFragment";

    private OnImportCompleteListener importCompleteListener;
    private OnBackListener backListener;

    // 界面控件
    private Button selectGameFileButton;
    private Button selectModLoaderButton;
    private Button startImportButton;
    private LinearLayout importProgressContainer;
    private com.app.ralib.ui.ModernProgressBar modernProgressBar;
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
        startImportButton = view.findViewById(R.id.startImportButton);
        importProgressContainer = view.findViewById(R.id.importProgressContainer);
        modernProgressBar = view.findViewById(R.id.modernProgressBar);
        gameFileText = view.findViewById(R.id.gameFileText);
        modLoaderFileText = view.findViewById(R.id.modLoaderFileText);

        // 设置按钮点击事件
        selectGameFileButton.setOnClickListener(v -> selectGameFile());
        selectModLoaderButton.setOnClickListener(v -> selectModLoaderFile());
        startImportButton.setOnClickListener(v -> startImport());

        // 初始状态
        updateImportButtonState();
    }

    private void selectGameFile() {
        openFileBrowser("game", new String[]{".sh"}, filePath -> {
            gameFilePath = filePath;
            File file = new File(gameFilePath);
            
            // 确保UI更新在主线程执行
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    gameFileText.setText("已选择: " + file.getName());
                    updateImportButtonState();
                });
            }
            
            // 在后台线程解析游戏信息
            new Thread(() -> {
                var gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(Paths.get(filePath));
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (gdzf != null) {
                            gameName = gdzf.id;
                            gameVersion = gdzf.version;
                            gameIconPath = null; // TODO: 从 gdzf 提取图标路径
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("检测到游戏: " + gameName + " " + gameVersion);
                            }
                            Log.d(TAG, "Game data zip file: " + gdzf);
                            Log.d(TAG, "Icon path: " + gameIconPath);
                        } else {
                            gameName = "未知游戏";
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("无法读取游戏信息，使用默认名称");
                            }
                        }
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

            // 确保UI更新在主线程执行
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    modLoaderFileText.setText("已选择: " + fileName);
                    updateImportButtonState();
                });
            }
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
            modernProgressBar.setStatusText("正在读取游戏信息...");

            new Thread(() -> {
                var gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(Paths.get(gameFilePath));
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (gdzf != null) {
                            gameName = gdzf.id;
                            gameVersion = gdzf.version;
                            gameIconPath = null;
                            Log.d(TAG, "Re-parsed game info: " + gameName + " " + gameVersion);
                            Log.d(TAG, "Re-parsed icon path: " + gameIconPath);

                            // 继续导入
                            continueImport();
                        } else {
                            modernProgressBar.setStatusText("无法读取游戏信息");
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
        // 确定目录名称：优先使用 ModLoader 名称
        String directoryBaseName = gameName; // 默认使用游戏名称
        
        // 如果有 ModLoader，尝试从文件名提取名称
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        if (hasModLoader && modLoaderBaseName != null && !modLoaderBaseName.isEmpty()) {
            directoryBaseName = modLoaderBaseName; // 使用 ModLoader 名称
            Log.i(TAG, "Using ModLoader name for directory: " + directoryBaseName);
        } else if (hasModLoader) {
            // 如果有 modLoaderFilePath 但没有 modLoaderBaseName，尝试从路径提取
            try {
                String modLoaderFileName = new File(modLoaderFilePath).getName();
                // 移除扩展名
                if (modLoaderFileName.endsWith(".zip")) {
                    directoryBaseName = modLoaderFileName.substring(0, modLoaderFileName.length() - 4);
                } else if (modLoaderFileName.contains(".")) {
                    directoryBaseName = modLoaderFileName.substring(0, modLoaderFileName.lastIndexOf('.'));
                } else {
                    directoryBaseName = modLoaderFileName;
                }
                Log.i(TAG, "Extracted ModLoader name from file: " + directoryBaseName);
            } catch (Exception e) {
                Log.w(TAG, "Failed to extract ModLoader name, using game name", e);
            }
        } else {
            Log.i(TAG, "No ModLoader, using game name for directory: " + directoryBaseName);
        }
        
        // 创建游戏目录
        gameDir = createGameDirectory(directoryBaseName);
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

        // 开始导入流程（hasModLoader 已在开头定义）
        if (hasModLoader) {
            // 有 ModLoader，使用完整导入逻辑
            GameExtractor.installCompleteGame(gameFilePath, modLoaderFilePath, outputPath,
                    new GameExtractor.ExtractionListener() {
                        @Override
                        public void onProgress(String message, int progress) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    modernProgressBar.setStatusText(message);
                                    modernProgressBar.setProgress(progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    modernProgressBar.setStatusText("导入完成！");
                                    modernProgressBar.setProgress(100);

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
                                        // 如果最终路径仍然是目录，尝试在其下递归查找 DLL（优先 SMAPI）
                                        File resolved = resolveExecutableFromDirectory(modLoaderDir);
                                        if (resolved != null) {
                                            finalGamePath = resolved.getAbsolutePath();
                                            Log.i(TAG, "Resolved executable from directory: " + finalGamePath);
                                        }
                                    }

                                    // 🔍 检测 SMAPI（星露谷物语模组加载器）
                                    String[] smapiPaths = GameExtractor.detectAndConfigureSMAPI(requireContext(), modLoaderDir);

                                    String gameBodyPath = null;
                                    if (smapiPaths != null) {
                                        // [OK] 检测到 SMAPI，使用检测到的路径
                                        finalGamePath = smapiPaths[0];  // SMAPI 启动器路径
                                        gameBodyPath = smapiPaths[1];   // 游戏本体路径（可能为 null）

                                        Log.d(TAG, "[OK] SMAPI 已自动配置:");
                                        Log.d(TAG, "  - SMAPI 启动器: " + finalGamePath);
                                        Log.d(TAG, "  - 游戏本体: " + gameBodyPath);
                                        Log.d(TAG, "  - 提示: 游戏将通过 SMAPI 启动，支持模组功能");
                                    } else {
                                        // 不是 SMAPI，使用常规逻辑查找游戏本体
                                        gameBodyPath = findGameBodyPath(gamePath);
                                        if (gameBodyPath != null) {
                                            Log.d(TAG, "Game body path: " + gameBodyPath);
                                        } else {
                                            Log.w(TAG, "Game body not found in: " + gamePath);
                                        }
                                    }

                                    Log.d(TAG, "Final game path: " + finalGamePath);
                                    Log.d(TAG, "GameExtractor returned gamePath: " + gamePath);
                                    Log.d(TAG, "GameExtractor returned modLoaderPath: " + modLoaderPath);

                                    var newGame = new GameItem();

                                    // 如果有模组加载器，提取模组加载器的信息
                                    String iconSourcePath;
                                    String displayName;
                                    if (modLoaderPath != null && !modLoaderPath.isEmpty()) {
                                        // 使用模组加载器的程序集
                                        iconSourcePath = finalGamePath;

                                        // 尝试从程序集文件名提取名称
                                        File modLoaderFile = new File(finalGamePath);
                                        if (modLoaderFile.exists() && modLoaderFile.isFile()) {
                                            String modLoaderName = modLoaderFile.getName().replace(".dll", "").replace(".exe", "");
                                            displayName = modLoaderName; // 直接使用 ModLoader 名称，不加括号
                                            Log.i(TAG, "Using ModLoader assembly: " + modLoaderName);
                                        } else {
                                            displayName = gameName; // 使用游戏名称
                                        }

                                        // 自动启用模组加载器
                                        newGame.setModLoaderEnabled(true);
                                        Log.i(TAG, "ModLoader detected, enabled automatically");
                                    } else {
                                        // 使用游戏本体
                                        iconSourcePath = (gameBodyPath != null) ? gameBodyPath : finalGamePath;
                                        displayName = gameName;
                                    }

                                    newGame.setGameName(displayName);
                                    try {
                                        newGame.setGameBasePath(gameDir.getCanonicalPath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    newGame.setGamePath(finalGamePath);
                                    newGame.setGameBodyPath(gameBodyPath);
                                    newGame.setEngineType(engineType);

                                    // 从正确的程序集中提取图标
                                    String extractedIconPath = extractIconFromExecutable(iconSourcePath, gameIconPath);
                                    newGame.setIconPath(extractedIconPath);

                                    // Bootstrapper 导入功能已移除

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
                                    modernProgressBar.setStatusText("导入失败: " + error);
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
                                    modernProgressBar.setStatusText(message);
                                    modernProgressBar.setProgress(progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    modernProgressBar.setStatusText("导入完成！");
                                    modernProgressBar.setProgress(100);

                                    // 🔍 检测是否为 SMAPI（星露谷物语模组加载器）
                                    File currentGameDir = new File(gamePath);
                                    String[] smapiPaths = GameExtractor.detectAndConfigureSMAPI(requireContext(), currentGameDir);

                                    String finalGamePath;
                                    String gameBodyPath = null;

                                    if (smapiPaths != null) {
                                        // [OK] 检测到 SMAPI
                                        finalGamePath = smapiPaths[0];  // SMAPI 启动器
                                        gameBodyPath = smapiPaths[1];   // 游戏本体

                                        Log.d(TAG, "[OK] SMAPI 已自动配置（纯游戏导入）:");
                                        Log.d(TAG, "  - SMAPI 启动器: " + finalGamePath);
                                        Log.d(TAG, "  - 游戏本体: " + gameBodyPath);
                                        Log.d(TAG, "  - 提示: 游戏将通过 SMAPI 启动，支持模组功能");
                                    } else {
                                        // 纯游戏，根据 gameinfo 中的游戏名称查找程序集
                                        finalGamePath = findGameBodyPath(gamePath);

                                        if (finalGamePath == null) {
                                            Log.w("LocalImportFragment", "Game executable not found, using directory path");
                                            finalGamePath = gamePath;
                                        }

                                        Log.d(TAG, "Pure game path: " + finalGamePath);
                                    }

                                    var newGame = new GameItem();

                                    // 检测是否有 SMAPI（模组加载器）
                                    String iconSourcePath;
                                    String displayName;
                                    if (smapiPaths != null) {
                                        // 检测到 SMAPI，使用 SMAPI 的信息
                                        iconSourcePath = finalGamePath;  // SMAPI 启动器
                                        displayName = "SMAPI"; // 直接使用 SMAPI 名称，不加括号

                                        // 自动启用模组加载器
                                        newGame.setModLoaderEnabled(true);
                                        Log.i(TAG, "SMAPI detected, enabled automatically");
                                    } else {
                                        // 纯游戏
                                        iconSourcePath = finalGamePath;
                                        displayName = gameName;
                                    }

                                    newGame.setGameName(displayName);
                                    try {
                                        newGame.setGameBasePath(currentGameDir.getCanonicalPath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    newGame.setGamePath(finalGamePath);

                                    // 如果检测到 SMAPI，设置游戏本体路径
                                    if (gameBodyPath != null) {
                                        newGame.setGameBodyPath(gameBodyPath);
                                        Log.d(TAG, "SMAPI game body path set: " + gameBodyPath);
                                    }

                                    newGame.setEngineType(engineType);

                                    // 从正确的程序集中提取图标
                                    String extractedIconPath = extractIconFromExecutable(iconSourcePath, gameIconPath);
                                    newGame.setIconPath(extractedIconPath);

                                    // Bootstrapper 导入功能已移除

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
                                    modernProgressBar.setStatusText("导入失败: " + error);
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

        Log.i(TAG, "Created game directory with name: " + dirName);
        return gameDir;
    }

    /**
     * 高清化小图标（使用双三次插值+锐化）
     *
     * @param iconPath 原始图标路径
     * @return 高清化后的图标路径，失败返回null
     */
    private String upscaleIcon(String iconPath) {
        try {
            // 读取原始图标
            android.graphics.Bitmap original = android.graphics.BitmapFactory.decodeFile(iconPath);
            if (original == null) {
                Log.e(TAG, "Failed to decode original icon");
                return null;
            }

            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();

            Log.i(TAG, String.format("Original icon size: %dx%d", originalWidth, originalHeight));

            // 目标尺寸：256x256（或原尺寸的8倍，取较小值）
            int targetSize = Math.min(256, Math.max(originalWidth, originalHeight) * 8);

            // 使用双三次插值放大
            android.graphics.Bitmap upscaled = android.graphics.Bitmap.createScaledBitmap(
                    original, targetSize, targetSize, true);

            // 应用锐化滤镜提升清晰度
            android.graphics.Bitmap sharpened = applySharpen(upscaled);

            // 保存高清化后的图标
            String upscaledPath = iconPath.replace(".png", "_upscaled.png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(upscaledPath);
            sharpened.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            out.close();

            // 清理
            original.recycle();
            upscaled.recycle();
            sharpened.recycle();

            Log.i(TAG, String.format("Icon upscaled from %dx%d to %dx%d",
                    originalWidth, originalHeight, targetSize, targetSize));

            return upscaledPath;

        } catch (Exception e) {
            Log.e(TAG, "Failed to upscale icon: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 应用锐化滤镜
     */
    private android.graphics.Bitmap applySharpen(android.graphics.Bitmap src) {
        // 锐化卷积核
        float[] sharpenKernel = {
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        };

        android.graphics.Bitmap result = android.graphics.Bitmap.createBitmap(
                src.getWidth(), src.getHeight(), src.getConfig());

        android.renderscript.RenderScript rs = null;
        try {
            rs = android.renderscript.RenderScript.create(getContext());
            android.renderscript.Allocation input = android.renderscript.Allocation.createFromBitmap(rs, src);
            android.renderscript.Allocation output = android.renderscript.Allocation.createFromBitmap(rs, result);

            android.renderscript.ScriptIntrinsicConvolve3x3 convolution =
                    android.renderscript.ScriptIntrinsicConvolve3x3.create(rs, android.renderscript.Element.U8_4(rs));

            convolution.setInput(input);
            convolution.setCoefficients(sharpenKernel);
            convolution.forEach(output);

            output.copyTo(result);

            input.destroy();
            output.destroy();
            convolution.destroy();

        } catch (Exception e) {
            Log.w(TAG, "Failed to apply sharpen filter, using original: " + e.getMessage());
            return src;
        } finally {
            if (rs != null) {
                rs.destroy();
            }
        }

        return result;
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
            Log.w(TAG, "EXE path is null or empty, using fallback icon");
            return fallbackIconPath;
        }

        File exeFile = new File(exePath);
        if (!exeFile.exists()) {
            Log.w(TAG, "EXE file not found: " + exePath + ", using fallback icon");
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
                Log.i(TAG, "Found Windows .exe file: " + winExe.getName());
                tryPath = winExe.getAbsolutePath();
            } else {
                Log.i(TAG, "No .exe file found, will try .dll (may have small icons)");
            }
        }

        try {
            Log.i(TAG, "Attempting to extract icon from: " + tryPath);

            // 使用IconExtractorHelper提取图标
            String extractedIconPath = IconExtractorHelper.extractGameIcon(getContext(), tryPath);

            if (extractedIconPath != null && new File(extractedIconPath).exists()) {
                // 检查提取的图标大小，如果太小则高清化
                File iconFile = new File(extractedIconPath);
                long fileSize = iconFile.length();

                // 如果图标文件小于5KB，可能是16x16或32x32的小图标，需要高清化
                if (fileSize < 5 * 1024) {
                    Log.w(TAG, String.format("Extracted icon is small (%d bytes), applying upscaling...", fileSize));

                    // 尝试高清化图标
                    String upscaledPath = upscaleIcon(extractedIconPath);
                    if (upscaledPath != null) {
                        Log.i(TAG, "[OK] Icon upscaled successfully: " + upscaledPath);
                        return upscaledPath;
                    } else if (fallbackIconPath != null) {
                        Log.w(TAG, "Upscaling failed, using fallback GOG icon");
                        return fallbackIconPath;
                    }
                }

                Log.i(TAG, "[OK] Successfully extracted icon to: " + extractedIconPath);
                return extractedIconPath;
            } else {
                Log.w(TAG, "Icon extraction returned null or file doesn't exist, using fallback");
                return fallbackIconPath;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract icon from executable: " + e.getMessage(), e);
            return fallbackIconPath;
        }
    }

    private File resolveExecutableFromDirectory(File baseDir) {
        if (baseDir == null || !baseDir.exists()) {
            return null;
        }
        // 优先查找 StardewModdingAPI.dll / .exe
        String[] preferredNames = {
                "StardewModdingAPI.dll",
                "StardewModdingAPI.exe",
                "Stardew Valley.dll",
                "Stardew Valley.exe",
                "tModLoader.dll",
                "ModLoader.dll"
        };
        for (String name : preferredNames) {
            File candidate = findFileRecursively(baseDir, name);
            if (candidate != null && candidate.isFile()) {
                return candidate;
            }
        }
        // 回退：查找任意 DLL
        File anyDll = findFirstFileRecursively(baseDir, name -> name.toLowerCase().endsWith(".dll") || name.toLowerCase().endsWith(".exe"));
        return anyDll;
    }

    private File findFileRecursively(File dir, String targetName) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File result = findFileRecursively(file, targetName);
                if (result != null) {
                    return result;
                }
            } else if (file.getName().equalsIgnoreCase(targetName)) {
                return file;
            }
        }
        return null;
    }

    private File findFirstFileRecursively(File dir, java.util.function.Predicate<String> predicate) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File result = findFirstFileRecursively(file, predicate);
                if (result != null) {
                    return result;
                }
            } else if (predicate.test(file.getName())) {
                return file;
            }
        }
        return null;
    }
}