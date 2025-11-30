package com.app.ralaunch.manager;

import android.content.Context;
import androidx.fragment.app.FragmentManager;
import com.app.ralaunch.R;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import java.io.File;

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
            description = "确定要从列表中移除 " + game.getGameName() + " 吗？\n\n注意：这只是移除快捷方式，不会删除实际文件";
        } else {
            description = "确定要删除 " + game.getGameName() + " 吗？\n\n注意：这将同时删除游戏文件";
        }
        
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "confirm", 
            "删除", 
            description
        ));
        options.add(new com.app.ralib.dialog.OptionSelectorDialog.Option(
            "cancel", 
            "取消", 
            ""
        ));
        
        dialog.setTitle("删除游戏")
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
                    // 快捷方式：只从列表删除，不删除实际文件
                    AppLogger.info("GameDeletionManager", "删除快捷方式: " + game.getGameName());
                    filesDeleted = false; // 标记为未删除文件，因为快捷方式不应该删除原始文件
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
            // 如果是快捷方式，不应该删除文件
            if (game.isShortcut()) {
                AppLogger.info("GameDeletionManager", "跳过快捷方式的文件删除: " + game.getGameName());
                return false;
            }
            
            // 优先使用 gameBasePath（游戏根目录）
            String gameBasePath = game.getGameBasePath();
            File gameDir = null;
            
            if (gameBasePath != null && !gameBasePath.isEmpty()) {
                gameDir = new File(gameBasePath);
                AppLogger.info("GameDeletionManager", "使用游戏根目录: " + gameBasePath);
            } else {
                // 如果没有 gameBasePath，尝试从 gamePath 推断
                String gamePath = game.getGamePath();
                if (gamePath == null || gamePath.isEmpty()) {
                    AppLogger.warn("GameDeletionManager", "游戏路径为空，无法删除文件");
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
                
                AppLogger.info("GameDeletionManager", "从游戏路径推断根目录: " + (gameDir != null ? gameDir.getAbsolutePath() : "null"));
            }
            
            if (gameDir == null || !gameDir.exists()) {
                AppLogger.warn("GameDeletionManager", "游戏目录不存在: " + (gameDir != null ? gameDir.getAbsolutePath() : "null"));
                return false;
            }
            
            // 确认这是一个游戏目录（在 files/games/ 下）
            String dirPath = gameDir.getAbsolutePath();
            if (!dirPath.contains("/files/games/") && !dirPath.contains("/files/imported_games/")) {
                AppLogger.warn("GameDeletionManager", "路径不在游戏目录中，跳过删除: " + dirPath);
                return false;
            }
            
            AppLogger.info("GameDeletionManager", "准备删除游戏目录: " + gameDir.getAbsolutePath());
            
            // 递归删除目录
            boolean success = deleteDirectory(gameDir);
            
            if (success) {
                AppLogger.info("GameDeletionManager", "游戏目录删除成功: " + gameDir.getName());
            } else {
                AppLogger.warn("GameDeletionManager", "删除游戏目录失败: " + gameDir.getName());
            }
            
            return success;
            
        } catch (Exception e) {
            AppLogger.error("GameDeletionManager", "删除游戏文件时发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 递归删除目录及其内容
     */
    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return false;
        }
        
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    boolean success = deleteDirectory(child);
                    if (!success) {
                        AppLogger.warn("GameDeletionManager", "无法删除: " + child.getAbsolutePath());
                    }
                }
            }
        }
        
        // 删除文件或空目录
        boolean deleted = dir.delete();
        return deleted;
    }
    
    /**
     * 删除确认监听器
     */
    public interface OnDeleteConfirmedListener {
        void onDeleteConfirmed(GameItem game, int position, boolean filesDeleted);
    }
}

