package com.app.ralaunch.manager;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.adapter.GameAdapter;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.manager.common.AnimationHelper;
import com.app.ralaunch.manager.common.IconLoader;
import com.app.ralaunch.manager.common.SelectionViewManager;
import java.io.File;
import java.util.List;

/**
 * 游戏列表管理器
 * 负责管理游戏列表的显示、选择和刷新
 */
public class GameListManager {
    private final Context context;
    private RecyclerView gameRecyclerView;
    private GameAdapter gameAdapter;
    private List<GameItem> gameList;
    private GameItem selectedGame;
    
    // UI 控件
    private SelectionViewManager selectionViewManager;
    
    // 回调接口
    private OnGameSelectedListener onGameSelectedListener;
    
    public interface OnGameSelectedListener {
        void onGameSelected(GameItem game);
    }
    
    public GameListManager(Context context) {
        this.context = context;
    }
    
    /**
     * 初始化游戏列表
     */
    public void initialize(RecyclerView recyclerView, View gameInfoView, 
                          ImageView gameImage, TextView gameName, 
                          TextView gameDescription, CardView emptyText) {
        this.gameRecyclerView = recyclerView;
        this.selectionViewManager = new SelectionViewManager(
            gameInfoView, gameImage, gameName, gameDescription, emptyText);
        
        // 加载游戏列表
        gameList = RaLaunchApplication.getGameDataManager().loadGameList();
        
        // 创建适配器
        gameAdapter = new GameAdapter(gameList, 
            game -> {
                showSelectedGame(game);
                if (onGameSelectedListener != null) {
                    onGameSelectedListener.onGameSelected(game);
                }
            },
            (game, position) -> {
                // 删除回调由外部处理
            }
        );
        
        gameRecyclerView.setAdapter(gameAdapter);
    }
    
    /**
     * 设置游戏选择监听器
     */
    public void setOnGameSelectedListener(OnGameSelectedListener listener) {
        this.onGameSelectedListener = listener;
    }
    
    /**
     * 设置游戏删除监听器
     */
    public void setOnGameDeleteListener(GameAdapter.OnGameDeleteListener listener) {
        if (gameAdapter != null) {
            // 需要重新创建适配器以设置删除监听器
            gameAdapter = new GameAdapter(gameList,
                game -> {
                    showSelectedGame(game);
                    if (onGameSelectedListener != null) {
                        onGameSelectedListener.onGameSelected(game);
                    }
                },
                listener
            );
            gameRecyclerView.setAdapter(gameAdapter);
        }
    }

    /**
     * 刷新游戏列表
     */
    public void refreshGameList() {
        AnimationHelper.animateRefresh(gameRecyclerView);
        
        // 重新加载游戏列表
        gameList = RaLaunchApplication.getGameDataManager().loadGameList();
        if (gameAdapter != null) {
            gameAdapter.updateGameList(gameList);
        }
    }
    
    /**
     * 显示选中的游戏
     */
    public void showSelectedGame(GameItem game) {
        selectedGame = game;
        
        if (selectionViewManager != null) {
            selectionViewManager.showSelection(game.getGameName(), game.getGameDescription());
            
            // 加载游戏图标
            IconLoader.loadGameIcon(context, selectionViewManager.getImageView(), game);
            
            // 添加选中动画
            AnimationHelper.animateSelection(selectionViewManager.getImageView());
        }
    }
    
    /**
     * 显示未选择游戏状态
     */
    public void showNoGameSelected() {
        selectedGame = null;
        
        if (selectionViewManager != null) {
            selectionViewManager.showEmpty();
        }
    }
    
    /**
     * 添加游戏到列表
     */
    public void addGame(GameItem newGame) {
        // 验证游戏文件是否存在
        File gameFile = new File(newGame.getGamePath());
        if (!gameFile.exists()) {
            // 警告由外部处理
        }
        
        // 确保游戏有默认图标
        IconLoader.ensureGameHasDefaultIcon(newGame);
        
        gameList.add(0, newGame);
        if (gameAdapter != null) {
            gameAdapter.updateGameList(gameList);
        }
        RaLaunchApplication.getGameDataManager().addGame(newGame);
    }
    
    /**
     * 从列表中移除游戏
     */
    public void removeGame(int position) {
        if (gameAdapter != null) {
            gameAdapter.removeGame(position);
        }
        RaLaunchApplication.getGameDataManager().removeGame(position);
        
        // 如果删除的是当前选中的游戏，清除选择
        if (selectedGame != null && position < gameList.size() && gameList.get(position) == selectedGame) {
            showNoGameSelected();
        }
    }
    
    /**
     * 获取当前选中的游戏
     */
    public GameItem getSelectedGame() {
        return selectedGame;
    }
    
    /**
     * 获取游戏列表
     */
    public List<GameItem> getGameList() {
        return gameList;
    }
    
    /**
     * 更新游戏列表
     */
    public void updateGameList(List<GameItem> newGameList) {
        this.gameList = newGameList;
        if (gameAdapter != null) {
            gameAdapter.updateGameList(gameList);
        }
    }
}

