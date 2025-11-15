package com.app.ralaunch.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.app.ralaunch.R;
import com.app.ralaunch.activity.MainActivity;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.utils.GameExtractor;
import com.app.ralaunch.utils.IconExtractorHelper;
import com.app.ralib.error.ErrorHandler;
import com.app.ralib.extractors.GogShFileExtractor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.nio.file.Paths;

/**
 * 本地导入对话框 - Material Design 3风格
 *
 * 弹窗式游戏导入界面,包含两个文件选择卡片:
 * - 游戏本体文件(.sh)
 * - ModLoader文件(.zip,可选)
 */
public class LocalImportDialog extends DialogFragment {

    private static final String TAG = "LocalImportDialog";

    private OnFileSelectionListener fileSelectionListener;
    private OnImportStartListener importStartListener;

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

    public interface OnFileSelectionListener {
        void onSelectGameFile(LocalImportDialog dialog);
        void onSelectModLoaderFile(LocalImportDialog dialog);
    }

    public interface OnImportStartListener {
        void onImportStart(String gameFilePath, String modLoaderFilePath, String gameName, String gameVersion);
    }

    public void setOnFileSelectionListener(OnFileSelectionListener listener) {
        this.fileSelectionListener = listener;
    }

    public void setOnImportStartListener(OnImportStartListener listener) {
        this.importStartListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.AddGameDialogStyle);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_local_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUI(view);
    }

    private void setupUI(View view) {
        // 初始化控件
        view.findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());

        gameFileCard = view.findViewById(R.id.gameFileCard);
        modLoaderCard = view.findViewById(R.id.modLoaderCard);
        selectGameFileButton = view.findViewById(R.id.selectGameFileButton);
        selectModLoaderButton = view.findViewById(R.id.selectModLoaderButton);
        startImportButton = view.findViewById(R.id.startImportButton);
        gameFileText = view.findViewById(R.id.gameFileText);
        modLoaderFileText = view.findViewById(R.id.modLoaderFileText);

        // 游戏文件选择
        View.OnClickListener gameFileClickListener = v -> {
            if (fileSelectionListener != null) {
                // 隐藏Dialog以显示文件浏览器
                if (getDialog() != null) {
                    getDialog().hide();
                }
                fileSelectionListener.onSelectGameFile(this);
            }
        };
        gameFileCard.setOnClickListener(gameFileClickListener);
        selectGameFileButton.setOnClickListener(gameFileClickListener);

        // ModLoader文件选择
        View.OnClickListener modLoaderClickListener = v -> {
            if (fileSelectionListener != null) {
                // 隐藏Dialog以显示文件浏览器
                if (getDialog() != null) {
                    getDialog().hide();
                }
                fileSelectionListener.onSelectModLoaderFile(this);
            }
        };
        modLoaderCard.setOnClickListener(modLoaderClickListener);
        selectModLoaderButton.setOnClickListener(modLoaderClickListener);

        // 开始导入
        startImportButton.setOnClickListener(v -> {
            if (importStartListener != null) {
                importStartListener.onImportStart(gameFilePath, modLoaderFilePath, gameName, gameVersion);
            }
            dismiss();
        });

        updateImportButtonState();
    }

    /**
     * 设置已选择的游戏文件
     */
    public void setGameFile(String filePath) {
        this.gameFilePath = filePath;
        if (gameFileText != null && filePath != null) {
            File file = new File(filePath);
            gameFileText.setText("已选择: " + file.getName());

            // 在后台解析游戏信息
            new Thread(() -> {
                var gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(Paths.get(filePath));
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (gdzf != null) {
                            gameName = gdzf.id;
                            gameVersion = gdzf.version;
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("检测到游戏: " + gameName + " " + gameVersion);
                            }
                            AppLogger.debug(TAG, "Game detected: " + gameName + " " + gameVersion);
                        }
                        updateImportButtonState();
                    });
                }
            }).start();
        }
        updateImportButtonState();
    }

    /**
     * 设置已选择的ModLoader文件
     */
    public void setModLoaderFile(String filePath) {
        this.modLoaderFilePath = filePath;
        if (modLoaderFileText != null && filePath != null) {
            File file = new File(filePath);
            modLoaderFileText.setText("已选择: " + file.getName());
        } else if (modLoaderFileText != null) {
            modLoaderFileText.setText("模组加载器 (.zip)");
        }
        updateImportButtonState();
    }

    /**
     * 更新导入按钮状态
     */
    private void updateImportButtonState() {
        if (startImportButton != null) {
            boolean canImport = gameFilePath != null && !gameFilePath.isEmpty();
            startImportButton.setEnabled(canImport);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public String getGameFilePath() {
        return gameFilePath;
    }

    public String getModLoaderFilePath() {
        return modLoaderFilePath;
    }

    /**
     * 重新显示Dialog（在文件选择后）
     */
    public void showDialog() {
        if (getDialog() != null && !getDialog().isShowing()) {
            getDialog().show();
        }
    }
}
