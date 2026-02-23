package com.app.ralaunch.core.common.console

import androidx.annotation.StringRes
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 命令控制台管理器（单例）
 *
 * 收集实时日志、管理控制台状态、处理命令。
 */
object ConsoleManager {

    private const val TAG = "ConsoleManager"
    private const val MAX_LOG_LINES = 500
    private const val MAX_DEBUG_LOG_LINES = 30

    /** 只收集包含这些关键词的 tag（不区分大小写） */
    private val ALLOWED_TAG_KEYWORDS = listOf(
        "dotnet", "corehost", "gamelauncher", "processlauncher",
        "serverlauncher", "serverlaunch", "mono", "console",
        "tmodloader", "terraria", "fna", "sdl",
    )

    /** 日志条目 */
    data class LogEntry(
        val id: Long,
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        val display: String get() = "[$timestamp] [$level/$tag] $message"
    }

    private var nextId = java.util.concurrent.atomic.AtomicLong(0)

    enum class LogLevel { V, D, I, W, E }

    // 全部日志
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // 最近日志（用于调试日志覆盖层）
    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs: StateFlow<List<LogEntry>> = _recentLogs.asStateFlow()

    // 控制台可见性
    private val _consoleVisible = MutableStateFlow(false)
    val consoleVisible: StateFlow<Boolean> = _consoleVisible.asStateFlow()

    // 调试日志可见性
    private val _debugLogVisible = MutableStateFlow(false)
    val debugLogVisible: StateFlow<Boolean> = _debugLogVisible.asStateFlow()

    private val logBuffer = CopyOnWriteArrayList<LogEntry>()
    private var logcatThread: Thread? = null
    private var isRunning = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * 开始收集日志
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        logcatThread = Thread({
            try {
                // 使用 tag 格式：X/Tag: message，不清除旧缓冲
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "tag", "-T", "100"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (isRunning) {
                    line = reader.readLine()
                    if (line != null && line.isNotBlank()) {
                        val entry = parseLine(line)
                        if (entry != null) {
                            addLog(entry)
                        }
                    }
                }
                process.destroy()
            } catch (e: Exception) {
                if (isRunning) {
                    AppLogger.error(TAG, "日志收集异常: ${e.message}")
                }
            }
        }, "ConsoleLogCollector").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 停止收集日志
     */
    fun stop() {
        isRunning = false
        logcatThread?.interrupt()
        logcatThread = null
    }

    /**
     * 手动添加一条日志
     */
    fun addLog(entry: LogEntry) {
        logBuffer.add(entry)
        // 裁剪缓冲
        while (logBuffer.size > MAX_LOG_LINES) {
            logBuffer.removeAt(0)
        }
        _logs.value = logBuffer.toList()
        _recentLogs.value = logBuffer.takeLast(MAX_DEBUG_LOG_LINES)

        // 智能提示：检测服务器输出并给出操作提示
        if (entry.tag != HINT_TAG) {
            checkAndShowHint(entry.message)
        }
    }

    /**
     * 添加一条控制台消息
     */
    fun addMessage(message: String, level: LogLevel = LogLevel.I) {
        addLog(LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = "Console",
            message = message
        ))
    }

    // ==================== 智能提示 ====================

    private val HINT_TAG: String
        get() = getLocalizedString(R.string.console_hint_tag)

    /** 避免同一提示短时间内重复显示 */
    private var lastHintKey = ""
    private var lastHintTime = 0L
    private const val HINT_COOLDOWN_MS = 5000L

    private fun addHint(key: String, message: String) {
        val now = System.currentTimeMillis()
        if (key == lastHintKey && now - lastHintTime < HINT_COOLDOWN_MS) return
        lastHintKey = key
        lastHintTime = now
        addLog(LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = LogLevel.W,
            tag = HINT_TAG,
            message = message
        ))
    }

    /**
     * 检测服务器输出的关键内容，自动添加操作提示
     */
    private fun checkAndShowHint(msg: String) {
        val m = msg.trim().lowercase()
        when {
            // 世界选择菜单
            m.contains("new world") && m.contains("n") ->
                addHint("world_select", getLocalizedString(
                    R.string.console_hint_world_select
                ))

            // 端口输入
            m.contains("server port") || (m.contains("port") && m.contains("7777")) ->
                addHint("port", getLocalizedString(
                    R.string.console_hint_port
                ))

            // 最大玩家数
            m.contains("max player") || m.contains("maxplayers") ->
                addHint("maxplayers", getLocalizedString(
                    R.string.console_hint_max_players
                ))

            // 密码
            m.contains("server password") ->
                addHint("password", getLocalizedString(
                    R.string.console_hint_password
                ))

            // 服务器启动成功
            m.contains("listening on port") || m.contains("server started") -> {
                // 尝试提取端口号
                val portMatch = Regex("""port\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(msg)
                val port = portMatch?.groupValues?.get(1) ?: "7777"
                addHint("server_ready",
                    getLocalizedString(
                        R.string.console_hint_server_ready,
                        port
                    )
                )
            }

            // 自动转发
            m.contains("auto-forwarding port") || m.contains("upnp") ->
                addHint("upnp", getLocalizedString(
                    R.string.console_hint_upnp
                ))

            // Mods 加载
            m.contains("loading mods") || m.contains("loading mod") ->
                addHint("mods_loading", getLocalizedString(
                    R.string.console_hint_mods_loading
                ))

            // 世界生成中
            m.contains("generating world") || m.contains("world generation") ->
                addHint("worldgen", getLocalizedString(
                    R.string.console_hint_world_generating
                ))

            // 世界保存
            m.contains("saving world") ->
                addHint("saving", getLocalizedString(
                    R.string.console_hint_saving_world
                ))
        }
    }

    private fun getLocalizedString(
        @StringRes resId: Int,
        vararg formatArgs: Any
    ): String {
        val appContext = RaLaunchApp.getAppContext()
        return runCatching {
            val localizedContext = LocaleManager.applyLanguage(appContext) ?: appContext
            if (formatArgs.isEmpty()) localizedContext.getString(resId)
            else localizedContext.getString(resId, *formatArgs)
        }.getOrElse {
            if (formatArgs.isEmpty()) appContext.getString(resId)
            else appContext.getString(resId, *formatArgs)
        }
    }

    fun toggleConsole() {
        _consoleVisible.value = !_consoleVisible.value
    }

    fun setConsoleVisible(visible: Boolean) {
        _consoleVisible.value = visible
    }

    fun toggleDebugLog() {
        _debugLogVisible.value = !_debugLogVisible.value
    }

    fun setDebugLogVisible(visible: Boolean) {
        _debugLogVisible.value = visible
    }

    fun clearLogs() {
        logBuffer.clear()
        _logs.value = emptyList()
        _recentLogs.value = emptyList()
    }

    // 预编译正则：tag 格式 "X/Tag: message" 或 brief 格式 "X/Tag( PID): message"
    private val tagRegex = Regex("""^([VDIWEFS])/(.+?):\s*(.*)$""")
    private val briefRegex = Regex("""^([VDIWEFS])/(.+?)\(\s*\d+\):\s*(.*)$""")

    /**
     * 解析 logcat 行（仅保留 DOTNET 相关 tag）
     */
    private fun parseLine(line: String): LogEntry? {
        val match = tagRegex.find(line) ?: briefRegex.find(line) ?: return null
        val levelChar = match.groupValues[1]
        val tag = match.groupValues[2].trim()
        val msg = match.groupValues[3]

        // 过滤：只保留 DOTNET 相关的 tag
        val tagLower = tag.lowercase()
        if (ALLOWED_TAG_KEYWORDS.none { tagLower.contains(it) }) return null

        val level = when (levelChar) {
            "V" -> LogLevel.V
            "D" -> LogLevel.D
            "I" -> LogLevel.I
            "W" -> LogLevel.W
            "E", "F", "S" -> LogLevel.E
            else -> LogLevel.I
        }
        return LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = msg
        )
    }
}
