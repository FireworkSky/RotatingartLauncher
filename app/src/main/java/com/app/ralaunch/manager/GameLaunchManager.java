package com.app.ralaunch.manager;

import android.content.Context;
import android.content.Intent;

import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.activity.GameActivity;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralib.patch.Patch;
import com.app.ralib.patch.PatchManager;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 游戏启动管理器
 * 负责处理游戏启动逻辑
 */
public class GameLaunchManager {
    private static final String TAG = "GameLaunchManager";
    
    private final Context context;
    
    public GameLaunchManager(Context context) {
        this.context = context;
    }
    
    /**
     * 启动游戏
     */
    public boolean launchGame(GameItem game) {
        // 验证程序集文件是否存在
        String assemblyPath = game.getGamePath();
        File assemblyFile = new File(assemblyPath);
        
        if (!assemblyFile.exists() || !assemblyFile.isFile()) {
            AppLogger.error(TAG, "Assembly file not found: " + assemblyPath);
            return false;
        }
        
        PatchManager patchManager = RaLaunchApplication.getPatchManager();
        // 使用 getApplicableAndEnabledPatches 来正确过滤只适用于该游戏的补丁
        // gameId 使用游戏名称，补丁的 targetGames 字段会匹配
        String gameId = game.getGameName();
        List<Patch> enabledPatches = patchManager.getApplicableAndEnabledPatches(gameId, assemblyFile.toPath());
        AppLogger.info(TAG, "Game: " + gameId + ", Applicable patches: " + enabledPatches.size());
        
        // 创建启动 Intent
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra("GAME_NAME", game.getGameName());
        intent.putExtra("ASSEMBLY_PATH", assemblyPath);
        intent.putExtra("GAME_ID", game.getGamePath());
        intent.putExtra("GAME_PATH", game.getGamePath());
        
        // 传递启用的补丁ID列表
        if (!enabledPatches.isEmpty()) {
            intent.putStringArrayListExtra(
                    "ENABLED_PATCH_IDS",
                    enabledPatches.stream()
                            .map(p -> p.manifest.id)
                            .collect(Collectors.toCollection(ArrayList::new)));
        }
        
        context.startActivity(intent);
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).overridePendingTransition(
                android.R.anim.fade_in, android.R.anim.fade_out);
        }
        
        return true;
    }
    
    /**
     * 启动选中的程序集文件
     */
    public boolean launchAssembly(File assemblyFile) {
        if (assemblyFile == null || !assemblyFile.exists()) {
            AppLogger.error(TAG, "Assembly file is null or does not exist");
            return false;
        }
        
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra("ASSEMBLY_PATH", assemblyFile.getAbsolutePath());
        intent.putExtra("GAME_NAME", assemblyFile.getName());
        
        context.startActivity(intent);
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).overridePendingTransition(
                android.R.anim.fade_in, android.R.anim.fade_out);
        }
        
        return true;
    }
}


