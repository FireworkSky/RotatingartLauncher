package com.app.ralaunch.manager.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.GameItem;
import com.app.ralib.icon.IconExtractor;
import com.app.ralaunch.utils.AppLogger;
import java.io.File;

/**
 * 图标加载器
 */
public class IconLoader {
    
    /**
     * 加载游戏图标到 ImageView
     * 优先使用自定义图标路径，否则使用资源ID，最后使用默认图标
     */
    public static void loadGameIcon(Context context, ImageView imageView, GameItem game) {
        if (imageView == null || game == null) {
            return;
        }
        
        // 优先使用自定义图标路径
        if (game.getIconPath() != null && !game.getIconPath().isEmpty()) {
            File iconFile = new File(game.getIconPath());
            if (iconFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(game.getIconPath());
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            }
        }
        
        // 使用资源ID
        if (game.getIconResId() != 0) {
            imageView.setImageResource(game.getIconResId());
            return;
        }
        
        // 使用默认图标
        imageView.setImageResource(R.drawable.ic_game_default);
    }
    
    /**
     * 异步加载文件图标
     * 从文件路径提取图标并显示
     */
    public static void loadFileIconAsync(Context context, ImageView imageView, String filePath) {
        if (imageView == null || filePath == null) {
            return;
        }
        
        // 先设置默认图标
        imageView.setImageResource(R.drawable.ic_game_default);
        
        // 异步提取图标
        new Thread(() -> {
            try {
                java.io.File gameFile = new java.io.File(filePath);
                if (!gameFile.exists()) {
                    return;
                }
                
                // 生成输出路径：在游戏文件旁边创建 xxx_icon.png
                String nameWithoutExt = gameFile.getName().replaceAll("\\.[^.]+$", "");
                String iconPath = gameFile.getParent() + java.io.File.separator + nameWithoutExt + "_icon.png";
                
                boolean success = IconExtractor.extractIconToPng(filePath, iconPath);
                if (success) {
                    java.io.File iconFile = new java.io.File(iconPath);
                    if (iconFile.exists() && iconFile.length() > 0) {
                        Bitmap bitmap = BitmapFactory.decodeFile(iconPath);
                        if (bitmap != null && context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                imageView.setImageBitmap(bitmap);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                AppLogger.error("IconLoader", "Exception during icon extraction: " + e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * 验证并设置游戏默认图标
     * 如果游戏没有图标，设置默认图标
     */
    public static void ensureGameHasDefaultIcon(GameItem game) {
        if (game == null) {
            return;
        }
        
        // 检查自定义图标路径是否有效
        if (game.getIconPath() == null || !new File(game.getIconPath()).exists()) {
            game.setIconPath(null);
        }
        
        // 如果没有图标路径且没有设置图标资源ID，使用默认图标
        if ((game.getIconPath() == null || game.getIconPath().isEmpty()) && game.getIconResId() == 0) {
            game.setIconResId(R.drawable.ic_game_default);
        }
    }
}

