package com.app.ralaunch.shared.ui.model

import com.app.ralaunch.shared.domain.model.GameItem

/**
 * 游戏项 UI 数据模型 (跨平台)
 * 
 * 用于 UI 层展示，与领域模型解耦
 */
data class GameItemUi(
    val id: String,
    val displayedName: String,
    val displayedDescription: String? = null,
    val iconPathFull: String? = null,
    val isShortcut: Boolean = false,
    val modLoaderEnabled: Boolean = true
) {
    /**
     * @deprecated Use `id` instead. This alias will be removed in a future version.
     */
    @Deprecated("Use 'id' instead", ReplaceWith("id"))
    val storageBasePathRelative: String
        get() = id
}

/**
 * 领域模型 -> UI 模型转换
 */
fun GameItem.toUiModel(): GameItemUi = GameItemUi(
    id = id,
    displayedName = displayedName,
    displayedDescription = displayedDescription,
    iconPathFull = iconPathFull,  // Use absolute path for UI
    modLoaderEnabled = modLoaderEnabled
)

/**
 * 批量转换
 */
fun List<GameItem>.toUiModels(): List<GameItemUi> = map { it.toUiModel() }

/**
 * 将 UI 模型的可编辑字段应用到领域模型
 *
 * 由于 GameItem 包含 GameItemUi 不具备的字段（如 gameId、gameExePathRelative 等），
 * 此方法仅更新可编辑的 UI 字段，不会覆盖其他字段。
 *
 * @param uiModel 包含更新值的 UI 模型
 * @return 返回 this 以支持链式调用
 */
fun GameItem.applyFromUiModel(uiModel: GameItemUi): GameItem {
    require(this.id == uiModel.id) { "GameItem id mismatch: ${this.id} != ${uiModel.id}" }

    this.displayedName = uiModel.displayedName
    this.displayedDescription = uiModel.displayedDescription ?: ""
    this.modLoaderEnabled = uiModel.modLoaderEnabled
    // 未来可扩展更多可编辑字段...

    return this
}

