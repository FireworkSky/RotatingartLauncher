package com.app.ralaunch.feature.main.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.R
import com.app.ralaunch.feature.gog.data.api.GogAuthClient
import com.app.ralaunch.feature.gog.data.api.GogWebsiteApi
import com.app.ralaunch.feature.gog.data.GogDownloader
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager.ModLoaderVersion
import com.app.ralaunch.feature.gog.data.model.GogGameFile
import com.app.ralaunch.shared.core.model.ui.GogGameUi
import com.app.ralaunch.shared.core.model.ui.GogUiState
import com.app.ralaunch.feature.gog.ui.GogScreen
import com.app.ralaunch.feature.gog.ui.components.DownloadStatus
import com.app.ralaunch.feature.gog.ui.components.GogDownloadDialog
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.ErrorHandler
import kotlinx.coroutines.*
import java.io.File

/**
 * 下载页面 Wrapper
 * 直接显示 GOG 登录和下载页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreenWrapper(
    onBack: () -> Unit,
    onNavigateToImport: (gamePath: String?, modLoaderPath: String?, gameName: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    GogTabContent(
        onBack = onBack,
        onNavigateToImport = onNavigateToImport,
        modifier = modifier.fillMaxSize()
    )
}

/**
 * 下载选项卡片（保留用于未来扩展）
 */
@Composable
private fun DownloadOptionCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(gradientColors)
                )
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 图标
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(80.dp),
                    tint = Color.White
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 标题
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 副标题
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 描述
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 进入按钮
                FilledTonalButton(
                    onClick = onClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.download_enter), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * GOG Tab 内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GogTabContent(
    onBack: () -> Unit,
    onNavigateToImport: (gamePath: String?, modLoaderPath: String?, gameName: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // API 客户端
    val authClient = remember { GogAuthClient(context) }
    val websiteApi = remember { GogWebsiteApi(authClient) }
    val downloader = remember { GogDownloader(authClient) }
    val modLoaderConfigManager = remember { ModLoaderConfigManager(context) }
    
    var uiState by remember { mutableStateOf(GogUiState()) }
    
    // 下载对话框状态
    var showDownloadDialog by remember { mutableStateOf(false) }
    var currentGame by remember { mutableStateOf<GogGameUi?>(null) }
    var gameFiles by remember { mutableStateOf<List<GogGameFile>>(emptyList()) }
    var currentModLoaderRule by remember { mutableStateOf<ModLoaderConfigManager.ModLoaderRule?>(null) }
    var downloadStatus by remember { mutableStateOf<DownloadStatus>(DownloadStatus.Idle) }
    
    // 选中的版本
    var selectedGameFile by remember { mutableStateOf<GogGameFile?>(null) }
    var selectedModLoaderVersion by remember { mutableStateOf<ModLoaderVersion?>(null) }
    
    // 下载结果路径
    var downloadedGamePath by remember { mutableStateOf<String?>(null) }
    var downloadedModLoaderPath by remember { mutableStateOf<String?>(null) }
    
    // 检查登录状态
    LaunchedEffect(Unit) {
        if (authClient.isLoggedIn()) {
            uiState = uiState.copy(isLoggedIn = true)
            loadUserInfoAndGames(
                authClient = authClient,
                websiteApi = websiteApi,
                scope = scope,
                context = context,
                onStateChange = { uiState = it(uiState) }
            )
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        GogScreen(
        uiState = uiState,
        onWebLogin = { authCode ->
            handleWebLogin(
                authCode = authCode,
                authClient = authClient,
                websiteApi = websiteApi,
                        scope = scope,
                        context = context,
                        onStateChange = { uiState = it(uiState) }
            )
        },
        onLogout = {
            authClient.logout()
            uiState = GogUiState()
            Toast.makeText(context, context.getString(R.string.gog_logged_out), Toast.LENGTH_SHORT).show()
        },
        onVisitGog = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gog.com")))
        },
        onSearchQueryChange = { query ->
            uiState = uiState.copy(searchQuery = query)
            val filtered = if (query.isBlank()) {
                uiState.games
            } else {
                uiState.games.filter { it.title.contains(query, ignoreCase = true) }
            }
            uiState = uiState.copy(filteredGames = filtered)
        },
        onGameClick = { game ->
            // 获取游戏详情并显示下载对话框
            handleGameClick(
                game = game,
                websiteApi = websiteApi,
                modLoaderConfigManager = modLoaderConfigManager,
                scope = scope,
                context = context,
                onStateChange = { uiState = it(uiState) },
                onShowDownloadDialog = { files, rule ->
                    currentGame = game
                    gameFiles = files
                    currentModLoaderRule = rule
                    downloadStatus = DownloadStatus.Idle
                    selectedGameFile = null
                    selectedModLoaderVersion = rule?.versions?.firstOrNull { it.stable }
                        ?: rule?.versions?.firstOrNull()
                    downloadedGamePath = null
                    downloadedModLoaderPath = null
                    showDownloadDialog = true
                }
            )
        },
        onLoginError = { error ->
            Toast.makeText(context, context.getString(R.string.gog_login_error, error), Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxSize()
    )
    }
    
    // 下载对话框
    if (showDownloadDialog && currentGame != null) {
        GogDownloadDialog(
            gameName = currentGame!!.title,
            gameFiles = gameFiles,
            modLoaderRule = currentModLoaderRule,
            downloadStatus = downloadStatus,
            onSelectGameVersion = { selectedGameFile = it },
            onSelectModLoaderVersion = { selectedModLoaderVersion = it },
            onStartDownload = {
                // 开始下载
                startDownload(
                    gameFile = selectedGameFile,
                    modLoaderVersion = selectedModLoaderVersion,
                    downloader = downloader,
                    scope = scope,
                    context = context,
                    onStatusChange = { downloadStatus = it },
                    onDownloadComplete = { gamePath, modLoaderPath ->
                        android.util.Log.d("DownloadScreen", ">>> onDownloadComplete: gamePath=$gamePath, modLoaderPath=$modLoaderPath")
                        downloadedGamePath = gamePath
                        downloadedModLoaderPath = modLoaderPath
                    }
                )
            },
            onInstall = {
                // 跳转到安装页面
                android.util.Log.d("DownloadScreen", ">>> onInstall clicked")
                android.util.Log.d("DownloadScreen", "    gamePath=$downloadedGamePath")
                android.util.Log.d("DownloadScreen", "    modLoaderPath=$downloadedModLoaderPath")
                android.util.Log.d("DownloadScreen", "    gameName=${currentGame?.title}")
                
                // 保存路径到临时变量，避免 showDownloadDialog=false 后状态问题
                val gamePath = downloadedGamePath
                val modLoaderPath = downloadedModLoaderPath
                val gameName = currentGame?.title
                
                showDownloadDialog = false
                onNavigateToImport(gamePath, modLoaderPath, gameName)
            },
            onDismiss = {
                if (downloadStatus is DownloadStatus.Downloading) {
                    downloader.cancel()
                }
                showDownloadDialog = false
            }
        )
    }
}

private fun handleWebLogin(
    authCode: String,
    authClient: GogAuthClient,
    websiteApi: GogWebsiteApi,
    scope: CoroutineScope,
    context: android.content.Context,
    onStateChange: ((GogUiState) -> GogUiState) -> Unit
) {
    onStateChange { it.copy(isLoading = true, loadingMessage = context.getString(R.string.gog_logging_in)) }

    scope.launch(Dispatchers.IO) {
        try {
            val success = authClient.exchangeCodeForToken(authCode)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, context.getString(R.string.gog_login_success), Toast.LENGTH_SHORT).show()
                    onStateChange { it.copy(isLoggedIn = true) }
                    loadUserInfoAndGames(
                        authClient = authClient,
                        websiteApi = websiteApi,
                        scope = scope,
                        context = context,
                        onStateChange = onStateChange
                    )
                } else {
                    onStateChange { it.copy(isLoading = false) }
                    Toast.makeText(context, context.getString(R.string.gog_login_failed), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            AppLogger.error("DownloadScreen", "WebView 登录异常", e)
            withContext(Dispatchers.Main) {
                onStateChange { it.copy(isLoading = false) }
                ErrorHandler.handleError(context.getString(R.string.gog_login_error, e.message), e)
            }
        }
    }
}

private fun loadUserInfoAndGames(
    authClient: GogAuthClient,
    websiteApi: GogWebsiteApi,
    scope: CoroutineScope,
    context: android.content.Context,
    onStateChange: ((GogUiState) -> GogUiState) -> Unit
) {
    onStateChange { it.copy(isLoading = true, loadingMessage = context.getString(R.string.gog_loading_user_info)) }

    scope.launch(Dispatchers.IO) {
        try {
            val userInfo = websiteApi.getUserInfo()
            withContext(Dispatchers.Main) {
                if (userInfo != null) {
                    onStateChange {
                        it.copy(
                            username = userInfo.username,
                            email = userInfo.email,
                            avatarUrl = userInfo.avatarUrl
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onStateChange { it.copy(loadingMessage = context.getString(R.string.gog_loading_games)) }
            }

            val games = websiteApi.getOwnedGames()
            val gameUiList = games.map { game ->
                GogGameUi(
                    id = game.id,
                    title = game.title,
                    imageUrl = game.imageUrl
                )
            }

            withContext(Dispatchers.Main) {
                onStateChange {
                    it.copy(
                        isLoading = false,
                        games = gameUiList,
                        filteredGames = gameUiList
                    )
                }

                if (games.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.gog_library_empty), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.gog_games_count, games.size), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            AppLogger.error("DownloadScreen", "加载数据失败", e)
            withContext(Dispatchers.Main) {
                onStateChange { it.copy(isLoading = false) }
                ErrorHandler.handleError(context.getString(R.string.gog_load_games_failed, e.message), e)
            }
        }
    }
}

private fun handleGameClick(
    game: GogGameUi,
    websiteApi: GogWebsiteApi,
    modLoaderConfigManager: ModLoaderConfigManager,
    scope: CoroutineScope,
    context: android.content.Context,
    onStateChange: ((GogUiState) -> GogUiState) -> Unit,
    onShowDownloadDialog: (List<GogGameFile>, ModLoaderConfigManager.ModLoaderRule?) -> Unit
) {
    val rule = modLoaderConfigManager.getRule(game.id)

    onStateChange {
        it.copy(
            isLoading = true,
            loadingMessage = context.getString(R.string.gog_loading_version_info, game.title)
        )
    }

    scope.launch(Dispatchers.IO) {
        try {
            // 并行获取游戏详情和 ModLoader 版本
            val detailsDeferred = async { websiteApi.getGameDetails(game.id.toString()) }
            val modLoaderDeferred = async {
                if (rule != null) {
                    // 从 GitHub API 动态获取最新版本
                    modLoaderConfigManager.getVersions(rule, forceRefresh = true)
                }
                rule
            }

            val details = detailsDeferred.await()
            val updatedRule = modLoaderDeferred.await()

            withContext(Dispatchers.Main) {
                onStateChange { it.copy(isLoading = false) }
                
                // 过滤 Linux 版本的安装文件
                val linuxInstallers = details.installers.filter { 
                    it.os.equals("linux", ignoreCase = true) 
                }
                
                if (linuxInstallers.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.gog_no_linux_version), Toast.LENGTH_SHORT).show()
                } else {
                    onShowDownloadDialog(linuxInstallers, updatedRule)
                }
            }
        } catch (e: Exception) {
            AppLogger.error("DownloadScreen", "获取游戏详情失败", e)
            withContext(Dispatchers.Main) {
                onStateChange { it.copy(isLoading = false) }
                Toast.makeText(context, context.getString(R.string.gog_get_details_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun startDownload(
    gameFile: GogGameFile?,
    modLoaderVersion: ModLoaderVersion?,
    downloader: GogDownloader,
    scope: CoroutineScope,
    context: android.content.Context,
    onStatusChange: (DownloadStatus) -> Unit,
    onDownloadComplete: (gamePath: String?, modLoaderPath: String?) -> Unit
) {
    if (gameFile == null) {
        Toast.makeText(context, context.getString(R.string.gog_select_game_version_prompt), Toast.LENGTH_SHORT).show()
        return
    }

    val downloadDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GOG"
    )
    if (!downloadDir.exists()) {
        downloadDir.mkdirs()
    }

    scope.launch(Dispatchers.IO) {
        var downloadedGamePath: String? = null
        var downloadedModLoaderPath: String? = null

        try {
            // 1. 下载游戏
            val gameFileName = gameFile.getFileName()
            val gameTargetFile = File(downloadDir, gameFileName)
            
            withContext(Dispatchers.Main) {
                onStatusChange(DownloadStatus.Downloading(
                    fileName = gameFileName,
                    progress = 0f,
                    downloaded = 0,
                    total = gameFile.size,
                    speed = 0
                ))
            }

            downloader.downloadGameFile(
                gameFile = gameFile,
                targetDir = downloadDir,
                progress = { downloaded, total, speed ->
                    scope.launch(Dispatchers.Main) {
                        onStatusChange(DownloadStatus.Downloading(
                            fileName = gameFileName,
                            progress = if (total > 0) downloaded.toFloat() / total else 0f,
                            downloaded = downloaded,
                            total = total,
                            speed = speed
                        ))
                    }
                }
            )
            
            downloadedGamePath = gameTargetFile.absolutePath
            AppLogger.info("DownloadScreen", "游戏下载完成: $downloadedGamePath")

            // 2. 下载 ModLoader（如果有）
            if (modLoaderVersion != null && modLoaderVersion.url.isNotEmpty()) {
                val modLoaderFileName = modLoaderVersion.fileName
                val modLoaderTargetFile = File(downloadDir, modLoaderFileName)
                
                withContext(Dispatchers.Main) {
                    onStatusChange(DownloadStatus.Downloading(
                        fileName = modLoaderFileName,
                        progress = 0f,
                        downloaded = 0,
                        total = 0,
                        speed = 0
                    ))
                }

                // ModLoader 从 GitHub 下载，不需要认证
                downloadFromUrl(
                    url = modLoaderVersion.url,
                    targetFile = modLoaderTargetFile,
                    context = context,
                    onProgress = { downloaded, total, speed ->
                        scope.launch(Dispatchers.Main) {
                            onStatusChange(DownloadStatus.Downloading(
                                fileName = modLoaderFileName,
                                progress = if (total > 0) downloaded.toFloat() / total else 0f,
                                downloaded = downloaded,
                                total = total,
                                speed = speed
                            ))
                        }
                    }
                )
                
                downloadedModLoaderPath = modLoaderTargetFile.absolutePath
                AppLogger.info("DownloadScreen", "ModLoader 下载完成: $downloadedModLoaderPath")
            }

            withContext(Dispatchers.Main) {
                onDownloadComplete(downloadedGamePath, downloadedModLoaderPath)
                onStatusChange(DownloadStatus.Completed(downloadedGamePath, downloadedModLoaderPath))
                Toast.makeText(context, context.getString(R.string.gog_download_complete), Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            AppLogger.error("DownloadScreen", "下载失败", e)
            withContext(Dispatchers.Main) {
                val errorMsg = if (e.message?.contains("cancelled") == true) {
                    context.getString(R.string.gog_download_cancelled)
                } else {
                    e.message ?: context.getString(R.string.common_unknown_error)
                }
                onStatusChange(DownloadStatus.Failed(errorMsg))
            }
        }
    }
}

/**
 * 从 URL 下载文件（不需要 GOG 认证）
 */
private fun downloadFromUrl(
    url: String,
    targetFile: File,
    context: android.content.Context,
    onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit
) {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val code = conn.responseCode
        if (code >= 400) throw java.io.IOException(context.getString(R.string.gog_error_download_failed, code))

        val total = conn.contentLengthLong
        var lastTime = System.currentTimeMillis()
        var lastDownloaded = 0L

        conn.inputStream.use { input ->
            java.io.FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var len: Int

                while (input.read(buffer).also { len = it } != -1) {
                    output.write(buffer, 0, len)
                    downloaded += len

                    val currentTime = System.currentTimeMillis()
                    val timeDiff = currentTime - lastTime
                    if (timeDiff >= 500) {
                        val bytesPerSecond = ((downloaded - lastDownloaded) * 1000) / timeDiff
                        onProgress(downloaded, total, bytesPerSecond)
                        lastTime = currentTime
                        lastDownloaded = downloaded
                    }
                }

                onProgress(downloaded, total, 0)
            }
        }
    } finally {
        conn.disconnect()
    }
}
