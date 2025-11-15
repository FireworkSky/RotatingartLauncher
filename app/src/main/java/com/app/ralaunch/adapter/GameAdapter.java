package com.app.ralaunch.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.app.ralaunch.model.GameItem;

import java.util.List;

/**
 * 游戏列表适配器
 * 
 * 用于主界面显示游戏列表，提供：
 * - 游戏卡片显示（图标、名称、路径）
 * - 游戏点击事件处理
 * - 游戏删除功能
 * - 动态更新游戏列表
 * 
 * 支持点击和删除回调接口
 */
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

        // 加载游戏图标 - 优先使用自定义图标路径，否则使用资源ID
        if (game.getIconPath() != null && !game.getIconPath().isEmpty()) {
            // 从文件加载图标
            java.io.File iconFile = new java.io.File(game.getIconPath());
            if (iconFile.exists()) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(game.getIconPath());
                if (bitmap != null) {
                    holder.gameImage.setImageBitmap(bitmap);
                } else {
                    // 如果加载失败，使用资源ID或默认图标
                    if (game.getIconResId() != 0) {
                        holder.gameImage.setImageResource(game.getIconResId());
                    } else {
                        holder.gameImage.setImageResource(com.app.ralaunch.R.drawable.ic_game_default);
                    }
                }
            } else {
                // 文件不存在，使用资源ID或默认图标
                if (game.getIconResId() != 0) {
                    holder.gameImage.setImageResource(game.getIconResId());
                } else {
                    holder.gameImage.setImageResource(com.app.ralaunch.R.drawable.ic_game_default);
                }
            }
        } else if (game.getIconResId() != 0) {
            holder.gameImage.setImageResource(game.getIconResId());
        } else {
            // 没有任何图标信息，使用默认图标
            holder.gameImage.setImageResource(com.app.ralaunch.R.drawable.ic_game_default);
        }

        // 添加进入动画
        setEnterAnimation(holder.itemView, position);

        // 游戏点击事件（添加点击动画）
        holder.itemView.setOnClickListener(v -> {
            animateClick(v, () -> {
                if (gameClickListener != null) {
                    gameClickListener.onGameClick(game);
                }
            });
        });

        // 删除按钮点击事件（添加点击动画）
        holder.deleteButton.setOnClickListener(v -> {
            animateClick(v, () -> {
                if (gameDeleteListener != null) {
                    gameDeleteListener.onGameDelete(game, position);
                }
            });
        });
    }

    /**
     * 卡片进入动画 - 从下方滑入并淡入
     */
    private void setEnterAnimation(View view, int position) {
        // 只对新出现的卡片添加动画
        if (view.getAlpha() == 0f) {
            view.setAlpha(0f);
            view.setTranslationY(100f);
            
            // 错开动画时间，创造波浪效果
            long delay = position * 50L;
            
            ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            alphaAnim.setDuration(400);
            alphaAnim.setInterpolator(new DecelerateInterpolator());
            
            ObjectAnimator translateAnim = ObjectAnimator.ofFloat(view, "translationY", 100f, 0f);
            translateAnim.setDuration(500);
            translateAnim.setInterpolator(new OvershootInterpolator(0.8f));
            
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(alphaAnim, translateAnim);
            animatorSet.setStartDelay(delay);
            animatorSet.start();
        } else {
            // 确保已显示的卡片状态正确
            view.setAlpha(1f);
            view.setTranslationY(0f);
        }
    }

    /**
     * 点击动画 - 缩放反馈
     */
    private void animateClick(View view, Runnable action) {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f);
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);
        
        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.play(scaleDownX).with(scaleDownY);
        
        scaleDown.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f);
                ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f);
                scaleUpX.setDuration(100);
                scaleUpY.setDuration(100);
                
                AnimatorSet scaleUp = new AnimatorSet();
                scaleUp.play(scaleUpX).with(scaleUpY);
                scaleUp.start();
                
                // 在动画中间执行回调
                if (action != null) {
                    action.run();
                }
            }
        });
        
        scaleDown.start();
    }

    @Override
    public int getItemCount() {
        return gameList.size();
    }

    public static class GameViewHolder extends RecyclerView.ViewHolder {
        public ImageView gameImage;
        public TextView gameName;
        public TextView gameDescription;
        public View deleteButton;  // 改为View以支持不同类型的删除按钮

        public GameViewHolder(View itemView) {
            super(itemView);
            gameImage = itemView.findViewById(R.id.gameImage);
            gameName = itemView.findViewById(R.id.gameName);
            gameDescription = itemView.findViewById(R.id.gameDescription);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }

    /**
     * 删除游戏（带动画）
     */
    public void removeGame(int position) {
        if (position >= 0 && position < gameList.size()) {
            gameList.remove(position);
            notifyItemRemoved(position);
            // 更新后续项的位置
            notifyItemRangeChanged(position, gameList.size());
        }
    }

    /**
     * 更新游戏列表（重置动画状态）
     */
    public void updateGameList(List<GameItem> newGameList) {
        this.gameList = newGameList;
        notifyDataSetChanged();
    }
}