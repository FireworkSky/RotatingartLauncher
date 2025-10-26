package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;

public class SelectGameFragment extends Fragment {

    private OnGameSelectedListener gameSelectedListener;
    private OnBackToMainListener backToMainListener;

    // 界面控件
    private CardView tmodloaderCard, stardewCard;
    private ImageView tmodloaderCheck, stardewCheck;
    private Button nextButton;

    // 选中的游戏类型
    private String selectedGameType = null;

    public interface OnGameSelectedListener {
        void onGameSelected(String gameType, String gameName, String engineType);
    }

    public interface OnBackToMainListener {
        void onBackToMain();
    }

    public void setOnGameSelectedListener(OnGameSelectedListener listener) {
        this.gameSelectedListener = listener;
    }

    public void setOnBackToMainListener(OnBackToMainListener listener) {
        this.backToMainListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select_game, container, false);
        setupUI(view);
        return view;
    }

    private void setupUI(View view) {
        // 返回按钮
        ImageButton backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (backToMainListener != null) {
                backToMainListener.onBackToMain();
            }
        });

        // 初始化控件
        tmodloaderCard = view.findViewById(R.id.tmodloaderCard);
        stardewCard = view.findViewById(R.id.stardewCard);
        tmodloaderCheck = view.findViewById(R.id.tmodloaderCheck);
        stardewCheck = view.findViewById(R.id.stardewCheck);
        nextButton = view.findViewById(R.id.nextButton);

        // 设置游戏选择监听
        tmodloaderCard.setOnClickListener(v -> selectGame("tmodloader"));
        stardewCard.setOnClickListener(v -> selectGame("stardew"));

        // 下一步按钮
        nextButton.setOnClickListener(v -> proceedToNextStep());
    }

    private void selectGame(String gameType) {
        // 重置所有选择状态
        resetSelection();

        // 设置新的选择状态
        selectedGameType = gameType;
        switch (gameType) {
            case "tmodloader":
                tmodloaderCheck.setVisibility(View.VISIBLE);
                tmodloaderCard.setCardBackgroundColor(getResources().getColor(R.color.selected_card_color));
                break;
            case "stardew":
                stardewCheck.setVisibility(View.VISIBLE);
                stardewCard.setCardBackgroundColor(getResources().getColor(R.color.selected_card_color));
                break;
        }

        // 启用下一步按钮
        nextButton.setEnabled(true);
        nextButton.setVisibility(View.VISIBLE);
    }

    private void resetSelection() {
        tmodloaderCheck.setVisibility(View.GONE);
        stardewCheck.setVisibility(View.GONE);

        tmodloaderCard.setCardBackgroundColor(getResources().getColor(R.color.card_background));
        stardewCard.setCardBackgroundColor(getResources().getColor(R.color.card_background));

        nextButton.setEnabled(false);
        nextButton.setVisibility(View.GONE);
    }

    private void proceedToNextStep() {
        if (selectedGameType != null && gameSelectedListener != null) {
            switch (selectedGameType) {
                case "tmodloader":
                    gameSelectedListener.onGameSelected("tmodloader", "tModLoader", "FNA");
                    break;
                case "stardew":
                    gameSelectedListener.onGameSelected("stardew", "Stardew Valley", "MonoGame");
                    break;
            }
        }
    }
}