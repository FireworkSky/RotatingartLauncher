package com.app.ralaunch.feature.steam.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.feature.steam.data.SteamBranch
import com.app.ralaunch.feature.steam.data.SteamCatalog
import com.app.ralaunch.feature.steam.data.SteamCli
import com.app.ralaunch.feature.steam.data.SteamGame
import com.app.ralaunch.feature.steam.data.SteamInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.java.KoinJavaComponent
import java.io.File

/** 创意工坊列表项（从 CLI 的 workshop 事件解析） */
data class WorkshopEntry(
    val publishedFileId: Long,
    val title: String,
    val description: String,
    val fileSize: Long,
    val subscriptions: Int,
    val tags: List<String>,
    val previewUrl: String,
)

data class SteamUiState(
    val loggedIn: Boolean = false,
    val accountName: String = "",
    val accountInput: String = "",
    val passwordInput: String = "",
    val guardInput: String = "",
    val needGuard: Boolean = false,
    val busy: Boolean = false,
    val status: String = "",
    val progress: Float? = null,
    val currentFile: String? = null,
    val installed: Map<Int, String> = emptyMap(),
    val workshopQuery: String = "",
    val workshopBusy: Boolean = false,
    val workshopItems: List<WorkshopEntry> = emptyList(),
    /** 每个 app 当前选中的分支（分支 id） */
    val selectedBranches: Map<Int, String> = emptyMap(),
    /** 各 app 的真实分支清单（PICS 现查；受密码保护的分支要靠 beta 密码才会出现） */
    val branches: Map<Int, List<SteamBranch>> = emptyMap(),
    /** 已通过 Steam 校验的 beta 密码（下载受保护分支时作为 branch password 发回 Steam） */
    val betaCode: String = "",
    val secretInput: String = "",
    val message: String? = null,
)

class SteamViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val repository: IGameRepositoryServiceV3 =
        KoinJavaComponent.get(IGameRepositoryServiceV3::class.java)

    private val _state = MutableStateFlow(SteamUiState())
    val state: StateFlow<SteamUiState> = _state.asStateFlow()

    val games: List<SteamGame> = SteamCatalog.all

    init {
        refreshLoginState()
        val prefs = appContext.getSharedPreferences("steam", Context.MODE_PRIVATE)
        val savedCode = prefs.getString("beta_code", "").orEmpty()
        _state.update {
            it.copy(
                selectedBranches = games.associate { game -> game.appId to game.branch },
                betaCode = savedCode,
            )
        }
        viewModelScope.launch { loadBranches() }
        applyCachedBetaBranches()
    }

    /** 从 Steam 现查分支清单（不带 beta 密码：隐藏分支走缓存，避免 CheckAppBetaPassword 被限流） */
    fun loadBranches() {
        viewModelScope.launch {
            val result = mutableMapOf<Int, List<SteamBranch>>()
            games.forEach { game ->
                val list = runCatching { SteamCli.listBranches(appContext, game.appId) }
                    .getOrElse { emptyList() }
                if (list.isNotEmpty()) result[game.appId] = list
            }
            if (result.isNotEmpty()) {
                _state.update { it.copy(branches = result + it.branches) }
            }
        }
    }

    /** 上次解锁成功的隐藏分支（缓存），避免每次进页面都去请求 Steam */
    private fun applyCachedBetaBranches() {
        val prefs = appContext.getSharedPreferences("steam", Context.MODE_PRIVATE)
        val cached = prefs.getStringSet("beta_branches", emptySet()).orEmpty()
        if (cached.isEmpty()) return
        _state.update { current ->
            val merged = current.branches.toMutableMap()
            val tml = SteamCatalog.TMODLOADER_APP_ID
            val list = cached.map { SteamBranch(it, SteamCatalog.branchLabel(it), gated = true) }
            merged[tml] = (merged[tml].orEmpty() + list).distinctBy { it.id }
            current.copy(branches = merged)
        }
    }

    fun onBranchSelect(appId: Int, branchId: String) = _state.update {
        it.copy(selectedBranches = it.selectedBranches + (appId to branchId))
    }

    fun onSecretInput(value: String) = _state.update { it.copy(secretInput = value) }

    /**
     * 秘密代码 = Steam 的 beta 密码。
     * 交给 Steam 校验（IClientApps.CheckAppBetaPassword），校验通过才会返回受保护的分支名
     * （tModLoader 的 1.4.5-dev），失败时 Steam 什么都不给 —— 所以不需要本地维护密码表。
     */
    fun unlockSecret() {
        val code = _state.value.secretInput.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            // CheckAppBetaPassword 会被 Steam 限流：只查有隐藏分支的 app，且每次只发一条请求
            _state.update { it.copy(busy = true, status = "向 Steam 校验秘密代码...") }
            val appId = SteamCatalog.TMODLOADER_APP_ID
            val list = runCatching { SteamCli.listBranches(appContext, appId, code) }
                .getOrElse { emptyList() }
            if (list.isNotEmpty()) {
                appContext.getSharedPreferences("steam", Context.MODE_PRIVATE).edit()
                    .putStringSet("beta_branches", list.map { it.id }.toSet()).apply()
            }
            val unlocked = if (list.isEmpty()) emptyMap() else mapOf(appId to list)
            _state.update { current ->
                if (unlocked.isEmpty()) {
                    current.copy(busy = false, status = "", message = "秘密代码无效（Steam 没有返回任何分支）")
                } else {
                    val merged = current.branches.toMutableMap()
                    unlocked.forEach { (appId, list) ->
                        merged[appId] = (merged[appId].orEmpty() + list).distinctBy { it.id }
                    }
                    appContext.getSharedPreferences("steam", Context.MODE_PRIVATE).edit()
                        .putString("beta_code", code).apply()
                    current.copy(
                        busy = false,
                        status = "",
                        betaCode = code,
                        branches = merged,
                        secretInput = "",
                        message = "已解锁：" + unlocked.values.flatten().joinToString { it.label },
                    )
                }
            }
        }
    }

    private fun refreshLoginState() {
        val account = appContext.getSharedPreferences("steam", Context.MODE_PRIVATE)
            .getString("account", "").orEmpty()
        _state.update {
            it.copy(loggedIn = SteamCli.hasCredentials(appContext), accountName = account)
        }
    }

    fun onAccountInput(value: String) = _state.update { it.copy(accountInput = value) }
    fun onPasswordInput(value: String) = _state.update { it.copy(passwordInput = value) }
    fun onGuardInput(value: String) = _state.update { it.copy(guardInput = value) }
    fun onWorkshopQuery(value: String) = _state.update { it.copy(workshopQuery = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun login() {
        val current = _state.value
        if (current.busy) return
        if (current.accountInput.isBlank() || current.passwordInput.isBlank()) {
            _state.update { it.copy(message = "请填写账号和密码") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "登录 Steam...", progress = null) }
            val code = SteamCli.run(
                appContext,
                listOf(
                    "--game-root", File(appContext.filesDir, "steam").absolutePath,
                    "--account", current.accountInput.trim(),
                    "--password", current.passwordInput,
                    "--credentials", SteamCli.credentialsFile(appContext).absolutePath,
                    "--login-only",
                ),
            ) { event -> handleCliEvent(event) }

            if (SteamCli.hasCredentials(appContext)) {
                appContext.getSharedPreferences("steam", Context.MODE_PRIVATE).edit()
                    .putString("account", current.accountInput.trim()).apply()
            }
            refreshLoginState()
            _state.update {
                it.copy(
                    busy = false,
                    needGuard = false,
                    status = if (code == 0) "已登录" else "登录失败（退出码 $code）",
                    message = if (code == 0) null else "登录失败，请检查账号/密码/验证码",
                    passwordInput = "",
                )
            }
        }
    }

    fun submitGuard() {
        val code = _state.value.guardInput.trim()
        if (code.isBlank()) return
        SteamCli.submitGuard(appContext, code)
        _state.update { it.copy(guardInput = "", needGuard = false, status = "已提交验证码") }
    }

    fun logout() {
        SteamCli.forgetCredentials(appContext)
        appContext.getSharedPreferences("steam", Context.MODE_PRIVATE).edit().remove("account").apply()
        refreshLoginState()
        _state.update { it.copy(status = "已退出 Steam") }
    }

    fun install(game: SteamGame) {
        val branch = _state.value.selectedBranches[game.appId] ?: game.branch
        if (_state.value.busy) return
        if (!SteamCli.hasCredentials(appContext)) {
            _state.update { it.copy(message = "请先登录 Steam 账号") }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(busy = true, status = "准备下载 ${game.name}...", progress = 0f, currentFile = null)
            }
            try {
                // 受 beta 密码保护的分支（1.4.5-dev）下载时必须把密码发回 Steam
                val gated = _state.value.branches[game.appId]
                    ?.firstOrNull { it.id == branch }?.gated == true
                SteamInstaller.install(
                    context = appContext,
                    repository = repository,
                    game = game,
                    branch = branch,
                    betaCode = if (gated) _state.value.betaCode.ifBlank { null } else null,
                    onStatus = { message -> _state.update { it.copy(status = message) } },
                    onProgress = { percent, file ->
                        _state.update { it.copy(progress = percent, currentFile = file) }
                    },
                )
                val label = "已安装"
                _state.update {
                    it.copy(
                        busy = false,
                        progress = null,
                        status = "${game.name} $label",
                        installed = it.installed + (game.appId to label),
                    )
                }
            } catch (e: Exception) {
                AppLog.e("SteamViewModel", "安装失败: ${e.message}", e)
                _state.update {
                    it.copy(
                        busy = false,
                        progress = null,
                        status = "安装失败",
                        message = e.message?.lineSequence()?.firstOrNull() ?: "未知错误",
                    )
                }
            }
        }
    }

    fun searchWorkshop() {
        if (_state.value.workshopBusy) return
        viewModelScope.launch {
            _state.update { it.copy(workshopBusy = true, status = "查询创意工坊...") }
            val query = _state.value.workshopQuery.trim()
            val args = buildList {
                add("--workshop-list")
                add("--count"); add("30")
                if (query.isNotEmpty()) { add("--query"); add(query) }
            }
            SteamCli.run(appContext, args) { event -> handleWorkshopEvent(event) }
            _state.update { it.copy(workshopBusy = false, status = "") }
        }
    }

    fun downloadWorkshop(item: WorkshopEntry) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(busy = true, status = "下载 ${item.title}...", progress = 0f, currentFile = null)
            }
            try {
                val modsDir = modsDirectory()
                modsDir.mkdirs()
                val code = SteamCli.run(
                    appContext,
                    listOf(
                        "--workshop-download", item.publishedFileId.toString(),
                        "--workshop-out", File(appContext.filesDir, "steam/workshop").absolutePath,
                        "--mods-dir", modsDir.absolutePath,
                    ),
                ) { event -> handleCliEvent(event) }
                _state.update {
                    it.copy(
                        busy = false,
                        progress = null,
                        status = if (code == 0) "${item.title} 已放入 Mods" else "工坊下载失败（$code）",
                        message = if (code == 0) null else "工坊下载失败（退出码 $code）",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message) }
            }
        }
    }

    /** tModLoader 的 Mods 目录（RAL 约定的外部存储路径），拿不到就退回应用私有目录 */
    private suspend fun modsDirectory(): File = withContext(Dispatchers.IO) {
        val external = File("/storage/emulated/0/RALauncher/Terraria/ModLoader/Mods")
        if (external.exists() || external.parentFile?.exists() == true) external
        else File(appContext.filesDir, "steam/Mods")
    }

    private fun handleCliEvent(event: JSONObject) {
        when (event.optString("type")) {
            "status" -> _state.update { it.copy(status = event.optString("message")) }
            "progress" -> _state.update {
                it.copy(
                    progress = event.optDouble("percent", 0.0).toFloat().coerceIn(0f, 100f),
                    currentFile = event.optString("current").takeIf { c -> c.isNotBlank() },
                )
            }
            "need-guard" -> _state.update { it.copy(needGuard = true, status = "请输入 Steam Guard 验证码") }
            "error" -> _state.update {
                it.copy(
                    busy = false,
                    status = "出错了",
                    message = event.optString("message").lineSequence().firstOrNull(),
                )
            }
            "done" -> _state.update { it.copy(status = "完成") }
        }
    }

    private fun handleWorkshopEvent(event: JSONObject) {
        if (event.optString("type") != "workshop") return
        val array = event.optJSONArray("items") ?: return
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val tagsArray = item.optJSONArray("tags")
                val tags = buildList {
                    if (tagsArray != null) for (t in 0 until tagsArray.length()) add(tagsArray.optString(t))
                }
                add(
                    WorkshopEntry(
                        publishedFileId = item.optLong("publishedFileId"),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        fileSize = item.optLong("fileSize"),
                        subscriptions = item.optInt("subscriptions"),
                        tags = tags,
                        previewUrl = item.optString("previewUrl"),
                    )
                )
            }
        }
        _state.update { it.copy(workshopItems = items, status = "共 ${items.size} 个物品") }
    }
}
