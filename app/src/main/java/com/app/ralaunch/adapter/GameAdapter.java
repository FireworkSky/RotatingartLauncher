package com.app.ralaunch.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;

import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
    private List<GameItem> gameList;
    private OnGameClickListener gameClickListener;
    private OnGameDeleteListener gameDeleteListener;

    public interface OnGameClickListener {
        void onGameClick(GameItem game);
    }

    public interface OnGameDeleteListener {
        void onGameDelete(GameItem game, int position);
    }

    public GameAdapter(List<GameItem> gameList, OnGameClickListener clickListener, OnGameDeleteListener deleteListener) {
        this.gameList = gameList;
        this.gameClickListener = clickListener;
        this.gameDeleteListener = deleteListener;
    }

    @Override
    public GameViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.game_item, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(GameViewHolder holder, int position) {
        GameItem game = gameList.get(position);
        holder.gameName.setText(game.getGameName());
        holder.gameDescription.setText(game.getGameDescription());

        // 加载游戏图标 - 优先使用资源ID
        if (game.getIconResId() != 0) {
            holder.gameImage.setImageResource(game.getIconResId());
        }


        // 游戏点击事件
        holder.itemView.setOnClickListener(v -> {
            if (gameClickListener != null) {
                gameClickListener.onGameClick(game);
            }
        });

        // 删除按钮点击事件
        holder.deleteButton.setOnClickListener(v -> {
            if (gameDeleteListener != null) {
                gameDeleteListener.onGameDelete(game, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return gameList.size();
    }

    public static class GameViewHolder extends RecyclerView.ViewHolder {
        public ImageView gameImage;
        public TextView gameName;
        public TextView gameDescription;
        public ImageButton deleteButton;

        public GameViewHolder(View itemView) {
            super(itemView);
            gameImage = itemView.findViewById(R.id.gameImage);
            gameName = itemView.findViewById(R.id.gameName);
            gameDescription = itemView.findViewById(R.id.gameDescription);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }

    public void updateGameList(List<GameItem> newGameList) {
        this.gameList = newGameList;
        notifyDataSetChanged();
    }

    public void removeGame(int position) {
        if (position >= 0 && position < gameList.size()) {
            gameList.remove(position);
            notifyItemRemoved(position);

        }
    }
}