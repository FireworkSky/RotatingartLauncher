package com.app.ralaunch.data;

import android.content.Context;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * 游戏数据管理器
 * 
 * 管理用户添加的游戏列表，提供：
 * - 从 SharedPreferences 加载和保存游戏列表
 * - 添加、删除和更新游戏项
 * - 路径有效性验证
 * 
 * 游戏数据持久化存储，应用重启后保留
 */
public class GameDataManager {
    private static final String TAG = "GameDataManager";
    private static final String PREF_NAME = "game_launcher_prefs";
    private static final String KEY_GAME_LIST = "game_list";

    private Context context;

    public GameDataManager(Context context) {
        this.context = context;
    }

    // 获取游戏安装基础目录
    public File getGamesBaseDirectory() {
        File externalDir = context.getExternalFilesDir(null);
        File gamesDir = new File(externalDir, "games");
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }
        return gamesDir;
    }

    // 为特定游戏创建安装目录
    public File createGameDirectory(String gameId, String gameName) {
        File baseDir = getGamesBaseDirectory();
        String dirName = gameId + "_" + System.currentTimeMillis();
        File gameDir = new File(baseDir, dirName);
        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }
        return gameDir;
    }

    // 获取游戏程序集路径
    public String getGameAssemblyPath(String gameId, File gameDir) {
        // 默认返回 Game.dll，如果需要特定程序集名称，可以从 GameItem 中获取
        return new File(gameDir, "Game.dll").getAbsolutePath();
    }

    // 获取游戏工作目录
    public String getGameWorkingDirectory(String gameId, File gameDir) {
        // 默认返回游戏目录本身
        return gameDir.getAbsolutePath();
    }

    // 保存和加载游戏列表
    public void saveGameList(List<GameItem> gameList) {
        try {
            Gson gson = new Gson();
            String gameListJson = gson.toJson(gameList);
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_GAME_LIST, gameListJson)
                    .apply();

        } catch (Exception e) {
            AppLogger.error(TAG, "保存游戏列表时发生错误: " + e.getMessage());
        }
    }

    public List<GameItem> loadGameList() {
        try {
            String gameListJson = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_GAME_LIST, null);
            if (gameListJson != null && !gameListJson.isEmpty()) {
                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<GameItem>>(){}.getType();
                List<GameItem> result = gson.fromJson(gameListJson, listType);

                return result != null ? result : new ArrayList<>();
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "加载游戏列表时发生错误: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public void addGame(GameItem game) {
        List<GameItem> gameList = loadGameList();
        gameList.add(0, game);
        saveGameList(gameList);
    }

    public void removeGame(int position) {
        List<GameItem> gameList = loadGameList();
        if (position >= 0 && position < gameList.size()) {
            gameList.remove(position);
            saveGameList(gameList);
        }
    }

    public void updateGameList(List<GameItem> gameList) {
        saveGameList(gameList);
    }
}