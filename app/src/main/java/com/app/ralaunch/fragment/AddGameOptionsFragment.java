package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;

public class AddGameOptionsFragment extends Fragment {

    private OnGameSourceSelectedListener gameSourceSelectedListener;
    private OnBackListener backListener;

    // 界面控件
    private Button downloadGameButton;
    private Button localImportButton;
    private androidx.cardview.widget.CardView downloadGameCard;
    private androidx.cardview.widget.CardView localImportCard;

    public interface OnGameSourceSelectedListener {
        void onGameSourceSelected(String sourceType);
    }

    public interface OnBackListener {
        void onBack();
    }

    public void setOnGameSourceSelectedListener(OnGameSourceSelectedListener listener) {
        this.gameSourceSelectedListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_game_options, container, false);
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
        downloadGameButton = view.findViewById(R.id.downloadGameButton);
        localImportButton = view.findViewById(R.id.localImportButton);
        downloadGameCard = view.findViewById(R.id.downloadGameCard);
        localImportCard = view.findViewById(R.id.localImportCard);

        // 下载按钮 - 通知选择下载选项
        downloadGameButton.setOnClickListener(v -> selectOption("download"));
        
        // 下载卡片也可以点击
        downloadGameCard.setOnClickListener(v -> selectOption("download"));

        // 本地导入按钮 - 通知选择导入选项
        localImportButton.setOnClickListener(v -> selectOption("local"));
        
        // 本地导入卡片也可以点击
        localImportCard.setOnClickListener(v -> selectOption("local"));
    }

    private void selectOption(String sourceType) {
        // 通知游戏来源选择完成
        // sourceType: "download" 或 "local"
        if (gameSourceSelectedListener != null) {
            gameSourceSelectedListener.onGameSourceSelected(sourceType);
        }
    }
}
