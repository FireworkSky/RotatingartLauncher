package com.app.ralaunch.gog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

/**
 * GOG 游戏列表适配器
 */
public class GogGameAdapter extends RecyclerView.Adapter<GogGameAdapter.GameViewHolder> {

    private List<GogApiClient.GogGame> games;
    private final OnGameClickListener listener;

    public interface OnGameClickListener {
        void onGameClick(GogApiClient.GogGame game);
    }

    public GogGameAdapter(List<GogApiClient.GogGame> games, OnGameClickListener listener) {
        this.games = games;
        this.listener = listener;
    }

    /**
     * 更新游戏列表
     */
    public void updateGames(List<GogApiClient.GogGame> newGames) {
        this.games = newGames;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gog_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        GogApiClient.GogGame game = games.get(position);
        holder.bind(game, listener);
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView gameCard;
        private final ShapeableImageView gameImage;
        private final TextView gameTitle;
        private final TextView gameStatus;
        private final MaterialButton btnDownload;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameCard = itemView.findViewById(R.id.gameCard);
            gameImage = itemView.findViewById(R.id.gameImage);
            gameTitle = itemView.findViewById(R.id.gameTitle);
            gameStatus = itemView.findViewById(R.id.gameStatus);
            btnDownload = itemView.findViewById(R.id.btnDownload);
        }

        public void bind(GogApiClient.GogGame game, OnGameClickListener listener) {
            gameTitle.setText(game.title);
            gameStatus.setText("可下载");

            // 加载游戏图标
            if (game.image != null && !game.image.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(game.image)
                        .placeholder(R.drawable.ic_games)
                        .error(R.drawable.ic_games)
                        .into(gameImage);
            } else {
                gameImage.setImageResource(R.drawable.ic_games);
            }

            // 卡片点击
            gameCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGameClick(game);
                }
            });

            // 下载按钮点击
            btnDownload.setOnClickListener(v -> {
                // TODO: 实现下载功能
                if (listener != null) {
                    listener.onGameClick(game);
                }
            });
        }
    }
}
