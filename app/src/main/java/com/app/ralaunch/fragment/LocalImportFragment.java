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
import com.app.ralaunch.fragment.FragmentHelper;
import com.app.ralaunch.activity.MainActivity;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.AssemblyChecker;
import com.app.ralaunch.utils.GameExtractor;
import com.app.ralaunch.utils.GamePathResolver;
import com.app.ralaunch.utils.IconExtractorHelper;
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
            runOnUiThread(() -> {
                if (isFragmentValid()) {
                    gameFileText.setText("已选择: " + file.getName());
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
                            showToast("检测到游戏: " + gameName + " " + gameVersion);
                            AppLogger.debug(TAG, "Game data zip file: " + gdzf);
                            AppLogger.debug(TAG, "Icon path: " + gameIconPath);
                        } else {
                            gameName = "未知游戏";
                            showToast("无法读取游戏信息，使用默认名称");
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
                AppLogger.debug("LocalImportFragment", "ModLoader base name: " + modLoaderBaseName);
            }

            // 确保UI更新在主线程执行
            runOnUiThread(() -> {
                if (isFragmentValid()) {
                    modLoaderFileText.setText("已选择: " + fileName);
                    updateImportButtonState();
                }
            });
        });
    }

  

    private interface FileChosen { void onChosen(String path); }

    private void openFileBrowser(String type, String[] exts, FileChosen cb) {
        // 使用统一的文件浏览器工具
        FragmentHelper.openFileBrowser(
            this,
            type,
            exts,
            (filePath, fileType) -> cb.onChosen(filePath),
            null
        );
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
            showToast("请先选择游戏文件");
            return;
        }

        // 根据是否选择了 ModLoader 来确定游戏类型
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        gameType = hasModLoader ? "modloader" : "game"; // 纯游戏类型为 "game"

        // 显示进度容器
        importProgressContainer.setVisibility(View.VISIBLE);
        startImportButton.setEnabled(false);

        // 添加日志检查gameName的值
        AppLogger.debug(TAG, "startImport() - gameName: " + gameName);
        AppLogger.debug(TAG, "startImport() - gameVersion: " + gameVersion);
        AppLogger.debug(TAG, "startImport() - gameIconPath: " + gameIconPath);
        AppLogger.debug(TAG, "startImport() - gameFilePath: " + gameFilePath);
        AppLogger.debug(TAG, "startImport() - hasModLoader: " + hasModLoader);

        // 如果游戏信息丢失，重新解析
        if (gameName == null || gameVersion == null) {
            AppLogger.warn(TAG, "Game info lost, re-parsing...");
            modernProgressBar.setStatusText("正在读取游戏信息...");

            new Thread(() -> {
                var gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(Paths.get(gameFilePath));
                runOnUiThread(() -> {
                    if (isFragmentValid()) {
                        if (gdzf != null) {
                            gameName = gdzf.id;
                            gameVersion = gdzf.version;
                            gameIconPath = null;
                            AppLogger.debug(TAG, "Re-parsed game info: " + gameName + " " + gameVersion);
                            AppLogger.debug(TAG, "Re-parsed icon path: " + gameIconPath);

                            // 继续导入
                            continueImport();
                        } else {
                            modernProgressBar.setStatusText("无法读取游戏信息");
                            startImportButton.setEnabled(true);
                            showToast("无法读取游戏信息，导入失败");
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
            directoryBaseName = modLoaderBaseName; // 使用 ModLoader 名称
            AppLogger.info(TAG, "Using ModLoader name for directory: " + directoryBaseName);
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
                AppLogger.info(TAG, "Extracted ModLoader name from file: " + directoryBaseName);
            } catch (Exception e) {
                AppLogger.warn(TAG, "Failed to extract ModLoader name, using game name", e);
            }
        } else {
            AppLogger.info(TAG, "No ModLoader, using game name for directory: " + directoryBaseName);
        }
        
        // 创建游戏目录
        gameDir = createGameDirectory(directoryBaseName);
        String outputPath = gameDir.getAbsolutePath();
        AppLogger.debug(TAG, "Created game directory: " + outputPath);

        // 复制图标到游戏目录
        if (gameIconPath != null) {
            Path iconSrc = Paths.get(gameIconPath);
            if (Files.exists(iconSrc) && Files.isRegularFile(iconSrc)) {
                try {
                    Path iconDest = Paths.get(gameDir.getAbsolutePath(), "icon.png");
                    Files.copy(iconSrc, iconDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    // 更新图标路径为游戏目录中的路径
                    gameIconPath = iconDest.toAbsolutePath().toString();
                    AppLogger.debug(TAG, "Icon copied to: " + gameIconPath);
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

                                    AppLogger.info(TAG, "=== 导入完成回调 ===");
                                    AppLogger.info(TAG, "游戏路径: " + gamePath);
                                    AppLogger.info(TAG, "ModLoader 解压路径: " + modLoaderPath);
                                    AppLogger.info(TAG, "ModLoader 原始 ZIP 路径: " + modLoaderFilePath);

                                    // 根据模组zip文件名推断程序集名称
                                    File modLoaderDir = new File(modLoaderPath);
                                    File assemblyFile = null;

                                    if (modLoaderBaseName != null && !modLoaderBaseName.isEmpty()) {
                                        // 优先使用 zip 文件名推断的程序集名称，递归查找
                                        String expectedDllName = modLoaderBaseName + ".dll";
                                        assemblyFile = findFileRecursively(modLoaderDir, expectedDllName);

                                        if (assemblyFile != null && assemblyFile.exists()) {
                                            AppLogger.debug(TAG, "Found ModLoader assembly based on zip name: " + assemblyFile.getAbsolutePath());
                                        } else {
                                            AppLogger.warn(TAG, "Expected DLL not found: " + expectedDllName + ", searching for any .dll/.exe");
                                            // 如果找不到，尝试查找第一个 .dll 或 .exe 文件
                                            assemblyFile = findFirstFileRecursively(modLoaderDir, name ->
                                                name.toLowerCase().endsWith(".dll") || name.toLowerCase().endsWith(".exe")
                                            );
                                            if (assemblyFile != null) {
                                                AppLogger.info(TAG, "Found alternative assembly: " + assemblyFile.getAbsolutePath());
                                            }
                                        }
                                    } else {
                                        // 如果没有 baseName，直接查找第一个 .dll 或 .exe
                                        AppLogger.info(TAG, "No modLoaderBaseName, searching for any .dll/.exe");
                                        assemblyFile = findFirstFileRecursively(modLoaderDir, name ->
                                            name.toLowerCase().endsWith(".dll") || name.toLowerCase().endsWith(".exe")
                                        );
                                        if (assemblyFile != null) {
                                            AppLogger.info(TAG, "Found assembly: " + assemblyFile.getAbsolutePath());
                                        }
                                    }



                                    // 让 C# 自动搜索目录中有入口点的程序集
                                    boolean foundModLoaderEntry = false;
                                    if (modLoaderDir != null && modLoaderDir.exists()) {
                                        AppLogger.info(TAG, "让 C# 搜索目录中有入口点的程序集: " + modLoaderDir.getAbsolutePath());
                                        try {
                                            // 调用 C# AssemblyChecker，传入目录路径
                                            // C# 会自动搜索目录中的所有 .dll 和 .exe 文件
                                            AssemblyChecker.CheckResult checkResult =
                                                    AssemblyChecker.searchDirectoryForEntryPoint(getContext(), modLoaderDir.getAbsolutePath());

                                            if (checkResult != null && checkResult.hasEntryPoint && checkResult.exists) {
                                                // C# 已经找到有入口点的程序集
                                                AppLogger.info(TAG, "✓ 找到有入口点的程序集: " + checkResult.assemblyPath);
                                                assemblyFile = new File(checkResult.assemblyPath);
                                                foundModLoaderEntry = true;
                                            } else {
                                                AppLogger.warn(TAG, "✗ 未找到有入口点的程序集");
                                            }
                                        } catch (Exception e) {
                                            AppLogger.error(TAG, "C# 搜索程序集失败", e);
                                        }

                                        // 兜底逻辑：如果 C# 工具失败，尝试手动查找程序集
                                        if (!foundModLoaderEntry) {
                                            AppLogger.info(TAG, "C# 工具检测失败，尝试手动查找 ModLoader 程序集");

                                            // 优先查找与 zip 文件名匹配的 DLL
                                            if (modLoaderBaseName != null && !modLoaderBaseName.isEmpty()) {
                                                String expectedDllName = modLoaderBaseName + ".dll";
                                                assemblyFile = findFileRecursively(modLoaderDir, expectedDllName);

                                                if (assemblyFile != null && assemblyFile.exists()) {
                                                    AppLogger.info(TAG, "✓ 找到与 ZIP 名称匹配的程序集: " + assemblyFile.getName());
                                                    foundModLoaderEntry = true;
                                                }
                                            }

                                            // 如果没找到，查找常见的 ModLoader 程序集名称
                                            if (!foundModLoaderEntry) {
                                                String[] commonModLoaders = {
                                                    "tModLoader.dll", "Terraria.dll",
                                                    "StardewModdingAPI.dll", "SMAPI.dll",
                                                    "MelonLoader.dll", "BepInEx.dll"
                                                };

                                                for (String modLoaderName : commonModLoaders) {
                                                    assemblyFile = findFileRecursively(modLoaderDir, modLoaderName);
                                                    if (assemblyFile != null && assemblyFile.exists()) {
                                                        AppLogger.info(TAG, "✓ 找到常见 ModLoader 程序集: " + assemblyFile.getName());
                                                        foundModLoaderEntry = true;
                                                        break;
                                                    }
                                                }
                                            }

                                            // 最后兜底：查找任意 .dll 或 .exe 文件
                                            if (!foundModLoaderEntry) {
                                                assemblyFile = findFirstFileRecursively(modLoaderDir, name ->
                                                    name.toLowerCase().endsWith(".dll") || name.toLowerCase().endsWith(".exe")
                                                );

                                                if (assemblyFile != null && assemblyFile.exists()) {
                                                    AppLogger.info(TAG, "✓ 找到程序集文件: " + assemblyFile.getName());
                                                    foundModLoaderEntry = true;
                                                }
                                            }
                                        }
                                    }

                                    // 查找游戏本体路径
                                    String gameBodyPath = findGameBodyPath(gamePath);
                                    if (gameBodyPath != null) {
                                        AppLogger.debug(TAG, "Game body path: " + gameBodyPath);
                                    } else {
                                        AppLogger.warn(TAG, "Game body not found in: " + gamePath);
                                    }

                                    // 确定最终的游戏路径和图标路径
                                    String finalGamePath;
                                    String iconSourcePath;
                                    String displayName;

                                    if (foundModLoaderEntry && assemblyFile != null && assemblyFile.exists()) {
                                        // 找到了模组入口点，使用模组配置
                                        finalGamePath = assemblyFile.getAbsolutePath();
                                        iconSourcePath = finalGamePath;

                                        // 优先级：modLoaderBaseName > 程序集文件名 > 游戏名称
                                        if (modLoaderBaseName != null && !modLoaderBaseName.isEmpty()) {
                                            displayName = modLoaderBaseName;
                                            AppLogger.info(TAG, "Using ModLoader zip name: " + displayName);
                                        } else {
                                            String modLoaderName = assemblyFile.getName().replace(".dll", "").replace(".exe", "");
                                            displayName = modLoaderName;
                                            AppLogger.info(TAG, "Using ModLoader assembly name: " + modLoaderName);
                                        }
                                        AppLogger.info(TAG, "✓ 使用模组配置");
                                    } else {
                                        // 未找到 ModLoader 入口点，使用游戏本体配置
                                        AppLogger.warn(TAG, "⚠ 未找到模组程序集入口点，使用游戏本体配置");
                                        finalGamePath = (gameBodyPath != null) ? gameBodyPath : gamePath;
                                        iconSourcePath = finalGamePath;
                                        displayName = gameName;
                                        AppLogger.info(TAG, "✓ 使用游戏原本配置（作为纯游戏）");
                                    }

                                    AppLogger.debug(TAG, "Final game path: " + finalGamePath);
                                    AppLogger.debug(TAG, "Icon source path: " + iconSourcePath);
                                    AppLogger.debug(TAG, "Display name: " + displayName);

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
                                    if (foundModLoaderEntry) {
                                        AppLogger.info(TAG, "ModLoader enabled");
                                    } else {
                                        AppLogger.info(TAG, "ModLoader disabled (using game as pure game)");
                                    }

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
                                    modernProgressBar.setStatusText("导入失败: " + error);
                                    if (getActivity() != null) {
                                        // 使用 RALib 的错误弹窗代替普通 Toast
                                        ErrorHandler.showWarning("导入失败", error);
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


                                    String finalGamePath;
                                    String gameBodyPath = null;


                                        finalGamePath = findGameBodyPath(gamePath);

                                        if (finalGamePath == null) {
                                            AppLogger.warn("LocalImportFragment", "Game executable not found, using directory path");
                                            finalGamePath = gamePath;
                                        }

                                        AppLogger.debug(TAG, "Pure game path: " + finalGamePath);

                                    var newGame = new GameItem();
                                    String iconSourcePath;
                                    String displayName;

                                        iconSourcePath = finalGamePath;
                                        displayName = gameName;


                                    newGame.setGameName(displayName);
                                    try {
                                        newGame.setGameBasePath(new File(gamePath).getCanonicalPath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
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
                                    modernProgressBar.setStatusText("导入失败: " + error);
                                    if (getActivity() != null) {
                                        // 使用 RALib 的错误弹窗代替普通 Toast
                                        ErrorHandler.showWarning("导入失败", error);
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

        AppLogger.info(TAG, "Created game directory with name: " + dirName);
        return gameDir;
    }

    /**
     * 高清化小图标（使用双三次插值+锐化）
     * 现在使用 ralib 中的实现
     *
     * @param iconPath 原始图标路径
     * @return 高清化后的图标路径，失败返回null
     */
    private String upscaleIcon(String iconPath) {
        return IconExtractor.upscaleIcon(getContext(), iconPath);
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
            AppLogger.warn(TAG, "EXE path is null or empty, using fallback icon");
            return fallbackIconPath;
        }

        File exeFile = new File(exePath);
        if (!exeFile.exists()) {
            AppLogger.warn(TAG, "EXE file not found: " + exePath + ", using fallback icon");
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
                AppLogger.info(TAG, "Found Windows .exe file: " + winExe.getName());
                tryPath = winExe.getAbsolutePath();
            } else {
                AppLogger.info(TAG, "No .exe file found, will try .dll (may have small icons)");
            }
        }

        try {
            AppLogger.info(TAG, "Attempting to extract icon from: " + tryPath);

            // 使用IconExtractorHelper提取图标
            String extractedIconPath = IconExtractorHelper.extractGameIcon(getContext(), tryPath);

            if (extractedIconPath != null && new File(extractedIconPath).exists()) {
                // 检查提取的图标大小，如果太小则高清化
                File iconFile = new File(extractedIconPath);
                long fileSize = iconFile.length();

                // 如果图标文件小于5KB，可能是16x16或32x32的小图标，需要高清化
                if (fileSize < 5 * 1024) {
                    AppLogger.warn(TAG, String.format("Extracted icon is small (%d bytes), applying upscaling...", fileSize));

                    // 尝试高清化图标
                    String upscaledPath = upscaleIcon(extractedIconPath);
                    if (upscaledPath != null) {
                        AppLogger.info(TAG, "Icon upscaled successfully: " + upscaledPath);
                        return upscaledPath;
                    } else if (fallbackIconPath != null) {
                        AppLogger.warn(TAG, "Upscaling failed, using fallback GOG icon");
                        return fallbackIconPath;
                    }
                }

                AppLogger.info(TAG, "Successfully extracted icon to: " + extractedIconPath);
                return extractedIconPath;
            } else {
                AppLogger.warn(TAG, "Icon extraction returned null or file doesn't exist, using fallback");
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

}