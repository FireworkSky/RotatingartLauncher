package com.app.ralaunch.feature.installer

import kotlinx.serialization.Serializable

/**
 * 游戏定义
 * 定义游戏的基本信息和启动配置
 */
@Serializable
data class GameDefinition(
    /** 显示名称 */
    val displayName: String,

    /** 启动目标（相对路径，如 "Celeste.exe"） */
    val launchTarget: String,

    /** 图标搜索模式（用于自动查找图标） */
    val iconPatterns: List<String>,

    /** 是否为模组加载器 */
    val isModLoader: Boolean = false,

    /** 游戏类型ID（用于唯一标识游戏类型） */
    val gameId: String
) {
    companion object {
        // Celeste 游戏定义
        val CELESTE = GameDefinition(
            displayName = "Celeste",
            launchTarget = "Celeste.exe",
            iconPatterns = listOf("Celeste"),
            isModLoader = false,
            gameId = "Celeste"
        )

        // Everest Mod Loader 定义
        val EVEREST = GameDefinition(
            displayName = "Celeste (Everest)",
            launchTarget = "Celeste.dll",
            iconPatterns = listOf("Celeste"),
            isModLoader = true,
            gameId = "Everest"
        )

        // Terraria 游戏定义
        val FEZ = GameDefinition(
            displayName = "FEZ",
            launchTarget = "FEZ.exe",
            iconPatterns = listOf("FEZ"),
            isModLoader = false,
            gameId = "FEZ"
        )

        val TERRARIA = GameDefinition(
            displayName = "Terraria",
            launchTarget = "Terraria.exe",
            iconPatterns = listOf("Terraria"),
            isModLoader = false,
            gameId = "Terraria"
        )

        // tModLoader 定义
        val TMODLOADER = GameDefinition(
            displayName = "Terraria (tModLoader)",
            launchTarget = "tModLoader.dll",
            iconPatterns = listOf("tModLoader", "Terraria"),
            isModLoader = true,
            gameId = "tModLoader"
        )

        // Stardew Valley 游戏定义
        val STARDEW_VALLEY = GameDefinition(
            displayName = "Stardew Valley",
            launchTarget = "Stardew Valley.exe",
            iconPatterns = listOf("Stardew Valley", "StardewValley"),
            isModLoader = false,
            gameId = "StardewValley"
        )

        // SMAPI Mod Loader 定义
        val SMAPI = GameDefinition(
            displayName = "Stardew Valley (SMAPI)",
            launchTarget = "StardewModdingAPI.dll",
            iconPatterns = listOf("Stardew Valley", "StardewValley", "SMAPI"),
            isModLoader = true,
            gameId = "SMAPI"
        )
    }
}