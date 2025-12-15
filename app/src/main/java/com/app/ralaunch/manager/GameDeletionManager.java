package com.app.ralaunch.manager;

import android.content.Context;
import androidx.fragment.app.FragmentManager;
import com.app.ralaunch.R;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralib.utils.FileUtils;
import java.io.File;
import java.nio.file.Paths;

/**
 * 游戏删除管理器
 * 负责处理游戏删除逻辑
 */
public class GameDeletionManager {
    private final Context context;
    
    public GameDeletionManager(Context context) {
        this.context = context;
    }
    
    /**
     * 显示删除确认对话框
     */
    public void showDeleteConfirmDialog(FragmentManager fragmentManager, GameItem game, 
                                       int position, OnDeleteConfirmedListener listener) {
        com.app.ralib.dialog.OptionSelectorDialog dialog = 
            new com.app.ralib.dialog.OptionSelectorDialog();
        
        // 创建选项列表
        java.util.List<com.app.ralib.dialog.OptionSelectorDialog.Option> options = new java.util.ArrayList<>();
        
        // 根据是否为快捷方式显示不同的提示
        String description;
        if (game.isShortcut()) {
            description = context.getString(R.string.delete_shortcut_message, game.getGameName());
        } else {
            description = context.getString(R.string.delete_game_message_full, game.getGameName());
        }
        
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "confirm", 
            context.getString(R.string.delete_confirm), 
            description
        ));
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "cancel", 
            context.getString(R.string.cancel), 
            ""
        ));
        
        dialog.setTitle(context.getString(R.string.delete_game_title))
              .setIcon(R.drawable.ic_close)
              .setOptions(options)
              .setCurrentValue("cancel")
              .setAutoCloseOnSelect(false);
        
        dialog.setOnOptionSelectedListener(optionValue -> {
            if ("confirm".equals(optionValue)) {
                // 检查是否为快捷方式
                boolean isShortcut = game.isShortcut();
                boolean filesDeleted = false;
                
                if (isShortcut) {
                    filesDeleted = false;
                } else {
                    // 游戏：删除游戏文件夹
                    filesDeleted = deleteGameFiles(game);
                }
                
                // 通知监听器
                if (listener != null) {
                    listener.onDeleteConfirmed(game, position, filesDeleted);
                }
                
                dialog.dismiss();
            } else {
                dialog.dismiss();
            }
        });
        
        dialog.show(fragmentManager, "DeleteGameDialog");
    }
    
    /**
     * 删除游戏文件夹
     */
    public boolean deleteGameFiles(GameItem game) {
        try {
            if (game.isShortcut()) {
                return false;
            }
            
            String gameBasePath = game.getGameBasePath();
            File gameDir = null;
            
            if (gameBasePath != null && !gameBasePath.isEmpty()) {
                gameDir = new File(gameBasePath);
            } else {
                String gamePath = game.getGamePath();
                if (gamePath == null || gamePath.isEmpty()) {
                    return false;
                }
                
                File gameFile = new File(gamePath);
                // 尝试找到 /games/ 目录下的第一级子目录作为游戏根目录
                File parent = gameFile.getParentFile();
                while (parent != null && !parent.getName().equals("games")) {
                    gameDir = parent;
                    parent = parent.getParentFile();
                }
                
                if (gameDir == null) {
                    gameDir = gameFile.getParentFile();
                }
            }
            
            if (gameDir == null || !gameDir.exists()) {
                return false;
            }
            
            String dirPath = gameDir.getAbsolutePath();
            if (!dirPath.contains("/files/games/") && !dirPath.contains("/files/imported_games/")) {
                return false;
            }
            
            boolean success = FileUtils.deleteDirectoryRecursively(Paths.get(gameDir.getAbsolutePath()));
            
            return success;
            
        } catch (Exception e) {
            AppLogger.error("GameDeletionManager", "删除游戏文件时发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    
    /**
     * 删除确认监听器
     */
    public interface OnDeleteConfirmedListener {
        void onDeleteConfirmed(GameItem game, int position, boolean filesDeleted);
    }
}

