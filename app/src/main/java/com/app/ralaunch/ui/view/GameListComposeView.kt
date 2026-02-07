package com.app.ralaunch.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.*
import androidx.compose.ui.platform.AbstractComposeView
import coil.compose.AsyncImage
import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.shared.ui.components.game.GameListContent
import com.app.ralaunch.shared.ui.model.GameItemUi
import com.app.ralaunch.shared.ui.theme.RaLaunchTheme
import java.io.File

/**
 * 游戏列表 ComposeView
 * 
 * 可以直接嵌入到 XML 布局中使用
 */
class GameListComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    // 游戏列表
    var games: List<GameItem> by mutableStateOf(emptyList())

    // 选中的游戏
    var selectedGame: GameItem? by mutableStateOf(null)

    // 加载状态
    var isLoading: Boolean by mutableStateOf(false)

    // 回调
    var onGameClick: ((GameItem) -> Unit)? = null
    var onGameLongClick: ((GameItem) -> Unit)? = null
    var onLaunchClick: (() -> Unit)? = null
    var onDeleteClick: (() -> Unit)? = null

    @Composable
    override fun Content() {
        RaLaunchTheme {
            val gamesUi = remember(games) {
                games.map { it.toUi() }
            }
            val selectedGameUi = remember(selectedGame) {
                selectedGame?.toUi()
            }

            GameListContent(
                games = gamesUi,
                selectedGame = selectedGameUi,
                onGameClick = { gameUi ->
                    val game = games.find { it.id == gameUi.id }
                    if (game != null) {
                        selectedGame = game
                        onGameClick?.invoke(game)
                    }
                },
                onGameLongClick = { gameUi ->
                    val game = games.find { it.id == gameUi.id }
                    game?.let { onGameLongClick?.invoke(it) }
                },
                onLaunchClick = { onLaunchClick?.invoke() },
                onDeleteClick = { onDeleteClick?.invoke() },
                isLoading = isLoading,
                iconLoader = { iconPath, modifier ->
                    if (iconPath != null) {
                        AsyncImage(
                            model = File(iconPath),
                            contentDescription = null,
                            modifier = modifier
                        )
                    }
                }
            )
        }
    }

    /**
     * 设置游戏列表
     */
    fun setGameList(gameList: List<GameItem>) {
        games = gameList
    }

    /**
     * 设置选中的游戏
     */
    fun setSelectedGameItem(game: GameItem?) {
        selectedGame = game
    }

    /**
     * 设置加载状态
     */
    fun setLoadingState(loading: Boolean) {
        isLoading = loading
    }

    /**
     * GameItem 转换为 UI 模型
     */
    private fun GameItem.toUi(): GameItemUi {
        return GameItemUi(
            id = this.id,
            name = this.gameName,
            description = this.gameDescription,
            iconPath = this.iconPath,
            isShortcut = this.isShortcut
        )
    }

    // GameItem 扩展属性 - 使用名称+路径组合，避免 hashCode 碰撞
    private val GameItem.id: String
        get() = "${gameName}_${gamePath}"
}
