package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.app.ralaunch.R;
import com.app.ralaunch.activity.MainActivity;
import com.app.ralaunch.utils.AppLogger;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;


public class GameImportFragment extends BaseFragment {
    private static final String TAG = "GameImportFragment";

    private OnImportStartListener importStartListener;
    private OnBackListener backListener;

    // 界面控件
    private MaterialCardView gameFileCard;
    private MaterialCardView modLoaderCard;
    private MaterialButton selectGameFileButton;
    private MaterialButton selectModLoaderButton;
    private MaterialButton startImportButton;
    private TextView gameFileText;
    private TextView modLoaderFileText;

    // 文件路径
    private String gameFilePath;
    private String modLoaderFilePath;

    // 游戏信息
    private String gameName = null;
    private String gameVersion = null;

    public interface OnImportStartListener {
        void onImportStart(String gameFilePath, String modLoaderFilePath, String gameName, String gameVersion);
    }

    public interface OnBackListener {
        void onBack();
    }

    public void setOnImportStartListener(OnImportStartListener listener) {
        this.importStartListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 使用现代化的MD3风格布局
        View view = inflater.inflate(R.layout.fragment_game_import, container, false);
        setupUI(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 确保视图创建后恢复文件状态
        restoreFileStates();
    }

    private void restoreFileStates() {
        // 恢复游戏文件状态
        if (gameFilePath != null && !gameFilePath.isEmpty() && gameFileText != null) {
            File file = new File(gameFilePath);
            gameFileText.setText(file.getName());
        }
        // 恢复 ModLoader 文件状态
        if (modLoaderFilePath != null && !modLoaderFilePath.isEmpty() && modLoaderFileText != null) {
            File file = new File(modLoaderFilePath);
            modLoaderFileText.setText(file.getName());
        }
        // 更新按钮状态
        updateStartButtonState();
    }

    private void setupUI(View view) {
        // 新布局不需要关闭按钮，直接设置UI

        gameFileCard = view.findViewById(R.id.gameFileCard);
        modLoaderCard = view.findViewById(R.id.modLoaderCard);
        selectGameFileButton = view.findViewById(R.id.selectGameFileButton);
        selectModLoaderButton = view.findViewById(R.id.selectModLoaderButton);
        startImportButton = view.findViewById(R.id.startImportButton);
        gameFileText = view.findViewById(R.id.gameFileText);
        modLoaderFileText = view.findViewById(R.id.modLoaderFileText);

        // 游戏文件选择
        View.OnClickListener gameFileClickListener = v -> {
            showFileBrowserForGameFile();
        };
        if (gameFileCard != null) {
            gameFileCard.setOnClickListener(gameFileClickListener);
        }
        if (selectGameFileButton != null) {
            selectGameFileButton.setOnClickListener(gameFileClickListener);
        }

        // ModLoader 文件选择
        View.OnClickListener modLoaderClickListener = v -> {
            showFileBrowserForModLoader();
        };
        if (modLoaderCard != null) {
            modLoaderCard.setOnClickListener(modLoaderClickListener);
        }
        if (selectModLoaderButton != null) {
            selectModLoaderButton.setOnClickListener(modLoaderClickListener);
        }

        // 开始导入按钮
        if (startImportButton != null) {
            startImportButton.setOnClickListener(v -> {
                if (gameFilePath != null && !gameFilePath.isEmpty()) {
                    if (importStartListener != null) {
                        importStartListener.onImportStart(gameFilePath, modLoaderFilePath, gameName, gameVersion);
                    }
                }
            });
        }

        // 恢复已选择的文件状态
        if (gameFilePath != null && !gameFilePath.isEmpty() && gameFileText != null) {
            File file = new File(gameFilePath);
            gameFileText.setText(file.getName());
        }
        if (modLoaderFilePath != null && !modLoaderFilePath.isEmpty() && modLoaderFileText != null) {
            File file = new File(modLoaderFilePath);
            modLoaderFileText.setText(file.getName());
        }
        
        // 初始状态
        updateStartButtonState();
    }

    private void showFileBrowserForGameFile() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
            fileBrowserFragment.setFileType("game", new String[]{".sh"});
            fileBrowserFragment.setOnFileSelectedListener((filePath, fileType) -> {
                // 先设置文件路径
                setGameFile(filePath);
                // 返回到导入页面
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                    // 使用 post 确保在视图恢复后更新
                    if (getView() != null) {
                        getView().post(() -> restoreFileStates());
                    }
                }
            });
            fileBrowserFragment.setOnBackListener(() -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
            });
            fileBrowserFragment.setOnPermissionRequestListener(activity::onPermissionRequest);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.importPage, fileBrowserFragment, "file_browser")
                    .addToBackStack("game_import")
                    .commit();
        }
    }

    private void showFileBrowserForModLoader() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
            fileBrowserFragment.setFileType("modloader", new String[]{".zip"});
            fileBrowserFragment.setOnFileSelectedListener((filePath, fileType) -> {
                // 先设置文件路径
                setModLoaderFile(filePath);
                // 返回到导入页面
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                    // 使用 post 确保在视图恢复后更新
                    if (getView() != null) {
                        getView().post(() -> restoreFileStates());
                    }
                }
            });
            fileBrowserFragment.setOnBackListener(() -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
            });
            fileBrowserFragment.setOnPermissionRequestListener(activity::onPermissionRequest);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.importPage, fileBrowserFragment, "file_browser")
                    .addToBackStack("game_import")
                    .commit();
        }
    }

    public void setGameFile(String filePath) {
        this.gameFilePath = filePath;
        if (gameFileText != null && filePath != null) {
            File file = new File(filePath);
            gameFileText.setText(file.getName());
            
            // 尝试从文件名提取游戏信息
            try {
                // 使用 GameInfoParser 提取游戏信息
                com.app.ralaunch.utils.GameInfoParser.GameInfo info = 
                    com.app.ralaunch.utils.GameInfoParser.extractGameInfo(filePath);
                if (info != null) {
                    gameName = info.name;
                    gameVersion = info.version;
                }
            } catch (Exception e) {
            }
        }
        updateStartButtonState();
    }

    public void setModLoaderFile(String filePath) {
        this.modLoaderFilePath = filePath;
        if (modLoaderFileText != null && filePath != null) {
            File file = new File(filePath);
            modLoaderFileText.setText(file.getName());
        }
        updateStartButtonState();
    }

    private void updateStartButtonState() {
        if (startImportButton != null) {
            boolean canStart = gameFilePath != null && !gameFilePath.isEmpty();
            startImportButton.setEnabled(canStart);
        }
    }
}

