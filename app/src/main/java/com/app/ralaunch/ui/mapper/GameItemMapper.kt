package com.app.ralaunch.ui.mapper

import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.shared.ui.model.GameItemUi

/**
 * App 层 GameItem -> UI 模型转换函数
 * 
 * 将 Android 层的 GameItem 转换为跨平台的 GameItemUi
 * 使用 gameName + gamePath 的组合生成唯一 ID，避免仅使用 hashCode 导致的冲突
 */
fun GameItem.toUiModel(): GameItemUi = GameItemUi(
    id = generateUniqueId(),
    name = gameName,
    description = gameDescription,
    iconPath = iconPath,
    isShortcut = isShortcut,
    modLoaderEnabled = modLoaderEnabled
)

/**
 * 生成唯一 ID
 * 直接使用游戏名+路径的组合字符串，避免 hashCode 碰撞导致 LazyGrid key 重复崩溃
 */
private fun GameItem.generateUniqueId(): String {
    return "${gameName}_${gamePath}"
}

/**
 * 批量转换（自动去重）
 * 
 * Presenter 返回的列表可能包含重复条目（相同 gameName + gamePath），
 * 使用 distinctBy 去重，确保 LazyGrid key 不会重复导致崩溃。
 */
fun List<GameItem>.toUiModels(): List<GameItemUi> =
    distinctBy { "${it.gameName}_${it.gamePath}" }
        .map { it.toUiModel() }
