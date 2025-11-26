package com.app.ralaunch.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.data.GameDataManager;
import com.app.ralib.patch.Patch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

/**
 * Material Design 3 风格的补丁管理对话框
 * 左侧游戏列表,右侧补丁列表
 */
public class PatchManagementDialog {

    private final Context context;
    private final GameDataManager gameDataManager;

    private RecyclerView recyclerViewGames;
    private RecyclerView recyclerViewPatches;
    private TextView tvNoGames;
    private TextView tvNoGameSelected;
    private GameItem selectedGame;
    private GameAdapter gameAdapter;
    private PatchAdapter patchAdapter;

    public PatchManagementDialog(Context context) {
        this.context = context;
        this.gameDataManager = new GameDataManager(context);
    }

    public void show() {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_patch_management, null);

        recyclerViewGames = dialogView.findViewById(R.id.recyclerViewGames);
        recyclerViewPatches = dialogView.findViewById(R.id.recyclerViewPatches);
        tvNoGames = dialogView.findViewById(R.id.tvNoGames);
        tvNoGameSelected = dialogView.findViewById(R.id.tvNoGameSelected);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnClose);

        List<GameItem> games = gameDataManager.loadGameList();

        if (games.isEmpty()) {
            recyclerViewGames.setVisibility(View.GONE);
            tvNoGames.setVisibility(View.VISIBLE);
        } else {
            recyclerViewGames.setVisibility(View.VISIBLE);
            tvNoGames.setVisibility(View.GONE);
            recyclerViewGames.setLayoutManager(new LinearLayoutManager(context));
            gameAdapter = new GameAdapter(games);
            recyclerViewGames.setAdapter(gameAdapter);
        }

        // 初始化补丁列表 (空)
        recyclerViewPatches.setLayoutManager(new LinearLayoutManager(context));
        patchAdapter = new PatchAdapter();
        recyclerViewPatches.setAdapter(patchAdapter);

        Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // 设置对话框窗口大小为屏幕的95%宽度和95%高度
        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            android.util.DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            params.width = (int) (displayMetrics.widthPixels * 0.95);
            params.height = (int) (displayMetrics.heightPixels * 0.95);
            dialog.getWindow().setAttributes(params);
        }
    }

    /**
     * 当选择游戏时,更新右侧补丁列表
     */
    private void onGameSelected(GameItem game) {
        selectedGame = game;

        // 获取适用于该游戏的补丁
        List<Patch> patches = RaLaunchApplication.getPatchManager().getApplicablePatches(game.getGameName());

        if (patches.isEmpty()) {
            // 没有可用补丁
            recyclerViewPatches.setVisibility(View.GONE);
            tvNoGameSelected.setVisibility(View.VISIBLE);
            tvNoGameSelected.setText(R.string.no_patches_for_game);
        } else {
            // 显示补丁列表
            recyclerViewPatches.setVisibility(View.VISIBLE);
            tvNoGameSelected.setVisibility(View.GONE);
            patchAdapter.setPatches(patches);
        }
    }

    /**
     * 游戏列表适配器
     */
    private class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {

        private final List<GameItem> games;
        private int selectedPosition = -1;

        public GameAdapter(List<GameItem> games) {
            this.games = games;
        }

        @NonNull
        @Override
        public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_game_selectable, parent, false);
            return new GameViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
            GameItem game = games.get(position);
            holder.bind(game, position);
        }

        @Override
        public int getItemCount() {
            return games.size();
        }

        class GameViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView cardGame;
            private final ImageView ivGameIcon;
            private final TextView tvGameName;

            public GameViewHolder(@NonNull View itemView) {
                super(itemView);
                cardGame = (MaterialCardView) itemView;
                ivGameIcon = itemView.findViewById(R.id.ivGameIcon);
                tvGameName = itemView.findViewById(R.id.tvGameName);
            }

            public void bind(GameItem game, int position) {
                // 设置游戏名称
                tvGameName.setText(game.getGameName());

                // 设置游戏图标
                if (game.getIconPath() != null && !game.getIconPath().isEmpty()) {
                    File iconFile = new File(game.getIconPath());
                    if (iconFile.exists()) {
                        ivGameIcon.setImageBitmap(BitmapFactory.decodeFile(game.getIconPath()));
                    } else {
                        ivGameIcon.setImageResource(R.drawable.ic_launcher_foreground);
                    }
                } else if (game.getIconResId() != 0) {
                    ivGameIcon.setImageResource(game.getIconResId());
                } else {
                    ivGameIcon.setImageResource(R.drawable.ic_launcher_foreground);
                }

                // 设置选中状态
                if (position == selectedPosition) {
                    cardGame.setStrokeColor(context.getColor(R.color.accent_primary));
                    cardGame.setCardBackgroundColor(context.getColor(R.color.selected_item_background));
                } else {
                    cardGame.setStrokeColor(context.getColor(android.R.color.transparent));
                    cardGame.setCardBackgroundColor(context.getColor(R.color.background_card));
                }

                // 点击选择游戏
                itemView.setOnClickListener(v -> {
                    int oldPosition = selectedPosition;
                    selectedPosition = position;
                    notifyItemChanged(oldPosition);
                    notifyItemChanged(selectedPosition);
                    onGameSelected(game);
                });
            }
        }
    }

    /**
     * 补丁列表适配器
     */
    private class PatchAdapter extends RecyclerView.Adapter<PatchAdapter.PatchViewHolder> {

        private List<Patch> patches;

        public PatchAdapter() {
            this.patches = List.of(); // 空列表
        }

        public void setPatches(List<Patch> patches) {
            this.patches = patches;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_patch, parent, false);
            return new PatchViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PatchViewHolder holder, int position) {
            Patch patch = patches.get(position);
            holder.bind(patch);
        }

        @Override
        public int getItemCount() {
            return patches.size();
        }

        class PatchViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvPatchName;
            private final TextView tvPatchDescription;
            private final MaterialSwitch switchPatch;

            public PatchViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPatchName = itemView.findViewById(R.id.tvPatchName);
                tvPatchDescription = itemView.findViewById(R.id.tvPatchDescription);
                switchPatch = itemView.findViewById(R.id.switchPatch);
            }

            public void bind(Patch patch) {
                // 设置补丁信息
                tvPatchName.setText(patch.manifest.name);
                tvPatchDescription.setText(patch.manifest.description);

                if (selectedGame == null) {
                    switchPatch.setEnabled(false);
                    switchPatch.setChecked(false);
                    return;
                }

                // 获取当前补丁状态
                var gameAsmPath = Paths.get(selectedGame.getGamePath());
                boolean isEnabled = RaLaunchApplication.getPatchManager().isPatchEnabled(gameAsmPath, patch.manifest.id);

                switchPatch.setEnabled(true);
                switchPatch.setOnCheckedChangeListener(null); // 先移除监听器避免触发
                switchPatch.setChecked(isEnabled);

                // 设置监听器
                switchPatch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    RaLaunchApplication.getPatchManager().setPatchEnabled(gameAsmPath, patch.manifest.id, isChecked);
                    Toast.makeText(context,
                        selectedGame.getGameName() + " - " + patch.manifest.name + ": " +
                        (isChecked ? context.getString(R.string.patch_enabled) : context.getString(R.string.patch_disabled)),
                        Toast.LENGTH_SHORT).show();
                });
            }
        }
    }
}
