package com.app.ralaunch.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import com.app.ralaunch.model.GameConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏配置管理器
 * 
 * 从 assets 加载并管理游戏配置信息，提供：
 * - 从 games.json 读取游戏配置
 * - 按游戏ID或名称查询配置
 * - 获取可用游戏列表
 * 
 * 配置信息包括游戏的引擎类型、图标、描述等
 */
public class GameConfigManager {
    private static final String TAG = "GameConfigManager";
    private static final String CONFIG_FILE = "games.json";

    private Context context;
    private Map<String, GameConfig> gameConfigs;
    private List<GameConfig> availableGames;

    public GameConfigManager(Context context) {
        this.context = context;
        this.gameConfigs = new HashMap<>();
        this.availableGames = new ArrayList<>();
        loadGameConfigs();
    }

    private void loadGameConfigs() {
        try (InputStream inputStream = context.getAssets().open(CONFIG_FILE)) {
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);

            String json = new String(buffer, "UTF-8");
            Gson gson = new Gson();
            Type listType = new TypeToken<List<GameConfig>>(){}.getType();
            List<GameConfig> configs = gson.fromJson(json, listType);



            for (GameConfig config : configs) {
                gameConfigs.put(config.getId(), config);
                availableGames.add(config);
                Log.d(TAG, "Loaded game config: " + config.getName());
            }

            Log.d(TAG, "Successfully loaded " + gameConfigs.size() + " game configurations");

        } catch (IOException e) {
            Log.e(TAG, "Failed to load game configurations: " + e.getMessage());

        }
    }

    public List<GameConfig> getAvailableGames() {
        return availableGames;
    }

    public GameConfig getGameConfig(String gameId) {
        return gameConfigs.get(gameId);
    }

    public boolean hasGameConfig(String gameId) {
        return gameConfigs.containsKey(gameId);
    }

    public List<String> getSupportedExtensions(String gameId, String fileType) {
        GameConfig config = getGameConfig(gameId);
        if (config != null && config.getRequiredFiles() != null) {
            for (GameConfig.RequiredFile file : config.getRequiredFiles()) {
                if (file.getType().equals(fileType)) {
                    return file.getExtensions();
                }
            }
        }
        return new ArrayList<>();
    }

    public String getFileDescription(String gameId, String fileType) {
        GameConfig config = getGameConfig(gameId);
        if (config != null && config.getRequiredFiles() != null) {
            for (GameConfig.RequiredFile file : config.getRequiredFiles()) {
                if (file.getType().equals(fileType)) {
                    return file.getDescription();
                }
            }
        }
        return "游戏文件";
    }
}