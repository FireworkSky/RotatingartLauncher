package com.app.ralaunch.core.model

import com.app.ralaunch.jsonconfig.FlowJsonConfig
import kotlinx.serialization.Serializable

/**
 * 游戏列表索引
 *
 * 存储在 games/game_list.json，包含所有游戏的目录名称
 */
@FlowJsonConfig
@Serializable
data class GameList(
    /** 游戏目录名称列表 */
    val games: List<String> = emptyList()
)
