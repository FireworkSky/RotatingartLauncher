package com.app.ralaunch.ui.main

import android.content.Context
import android.content.SharedPreferences
import com.app.ralaunch.shared.AppConstants
import com.app.ralaunch.R
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.ui.model.GameItemUi
import com.app.ralaunch.shared.ui.model.applyFromUiModel
import com.app.ralaunch.data.repository.GameRepository
import com.app.ralaunch.manager.GameLaunchManager
import com.app.ralaunch.manager.GameDeletionManager
import com.app.ralaunch.ui.base.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.java.KoinJavaComponent.get

/**
 * 主界面 Presenter
 * 处理所有业务逻辑，包括游戏列表、导航、初始化
 */
class MainPresenter(
    private val context: Context
) : BasePresenter<MainContract.View>(), MainContract.Presenter {

    /**
     * 导航页面枚举（替代 R.id.nav_* 资源引用）
     */
    enum class NavPage(val id: Int) {
        GAME(0),
        CONTROL(1),
        DOWNLOAD(2),
        ADD_GAME(3),
        SETTINGS(4)
    }

    // 通过 Koin 获取 GameRepository
    private val gameRepository: GameRepository = get(GameRepository::class.java)
    private val gameLaunchManager: GameLaunchManager = GameLaunchManager(context)
    private val gameDeletionManager: GameDeletionManager = GameDeletionManager(context)
    
    private val presenterScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private var gameList: MutableList<GameItem> = mutableListOf()
    private var selectedGame: GameItem? = null
    private var currentPage: NavPage = NavPage.GAME

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== 生命周期 ====================

    override fun onCreate() {
        loadGameList()
    }

    override fun onResume() {
        // 可添加恢复逻辑
    }

    override fun onPause() {
        // 可添加暂停逻辑
    }

    override fun onDestroy() {
        presenterScope.cancel()
        detach()
    }

    // ==================== 游戏列表 ====================

    override fun loadGameList() {
        // 同步加载：数据已在 Repository 初始化时读入内存，此处直接读取几乎无开销
        // 避免异步加载导致 Compose 首帧无数据，出现空白闪烁
        val games = gameRepository.loadGameList()
        gameList = games.toMutableList()
        withView { showGameList(gameList) }
    }

    override fun selectGame(game: GameItem) {
        selectedGame = game
        withView {
            showSelectedGame(game)
            showLaunchButton()
        }
    }

    override fun deleteGame(game: GameItem, position: Int) {
        if (selectedGame == game) {
            selectedGame = null
            withView { 
                showNoGameSelected()
                hideLaunchButton()
            }
        }
        
        if (position in gameList.indices) {
            gameList.removeAt(position)
            gameRepository.removeGame(position)
            withView { refreshGameList() }
        }
    }

    override fun launchSelectedGame() {
        android.util.Log.d("MainPresenter", "launchSelectedGame called, selectedGame=$selectedGame, isViewAttached=$isViewAttached")
        val game = selectedGame
        if (game != null) {
            android.util.Log.d("MainPresenter", "Launching game: ${game.displayedName}")
            withView { launchGame(game) }
        } else {
            android.util.Log.w("MainPresenter", "selectedGame is null!")
            withView { showToast(context.getString(R.string.main_select_game_first)) }
        }
    }

    override fun addGame(game: GameItem) {
        gameList.add(0, game)
        gameRepository.addGame(game)
        withView {
            refreshGameList()
            showToast(context.getString(R.string.game_added_success))
            showGamePage()
        }
    }

    fun getSelectedGame(): GameItem? = selectedGame

    fun getGameList(): List<GameItem> = gameList

    /**
     * 更新游戏信息
     *
     * 接收更新后的 GameItemUi，将修改应用到对应的 GameItem 并持久化。
     * 使用 applyFromUiModel 扩展函数，自动应用所有可编辑字段。
     */
    fun updateGame(updatedGameUi: GameItemUi) {
        val index = gameList.indexOfFirst { it.id == updatedGameUi.id }
        if (index >= 0) {
            val game = gameList[index]

            // 使用扩展函数应用所有可编辑字段
            game.applyFromUiModel(updatedGameUi)

            gameRepository.updateGame(index, game)

            // 如果是当前选中的游戏，更新选中状态
            if (selectedGame?.id == updatedGameUi.id) {
                selectedGame = game
                withView { showSelectedGame(game) }
            }

            withView { refreshGameList() }
        }
    }

    // ==================== 导航 ====================

    override fun onNavigationSelected(itemId: Int): Boolean {
        val page = NavPage.entries.find { it.id == itemId } ?: return false
        if (currentPage == page) return true
        currentPage = page
        
        withView {
            when (page) {
                NavPage.GAME -> showGamePage()
                NavPage.SETTINGS -> showSettingsPage()
                NavPage.CONTROL -> showControlPage()
                NavPage.DOWNLOAD -> showDownloadPage()
                NavPage.ADD_GAME -> showImportPage()
            }
        }
        return true
    }

    override fun onBackPressed(): Boolean {
        if (currentPage != NavPage.GAME) {
            currentPage = NavPage.GAME
            withView { showGamePage() }
            return true
        }
        return false
    }

    // ==================== 导入 ====================

    override fun onGameImportComplete(gameType: String, game: GameItem) {
        addGame(game)
    }

    // ==================== 工具方法 ====================

    fun getGameDeletionManager(): GameDeletionManager = gameDeletionManager

    fun getGameLaunchManager(): GameLaunchManager = gameLaunchManager
}
