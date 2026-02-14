package com.app.ralaunch.feature.init

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.R
import com.app.ralaunch.core.common.PermissionManager
import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.shared.core.model.ui.ComponentState
import com.app.ralaunch.shared.core.model.ui.InitUiState
import com.app.ralaunch.shared.core.theme.RaLaunchTheme

/**
 * 初始化页面步骤
 */
enum class InitPage {
    LEGAL,      // 法律说明
    SETUP       // 初始化（权限+解压）
}

/**
 * 横屏初始化界面
 * 第一页：法律说明
 * 第二页：初始化（权限请求 + 组件解压）
 */
@Composable
fun InitializationScreen(
    permissionManager: PermissionManager,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    onExtract: (List<ComponentState>, (Int, Int, Boolean, String) -> Unit) -> Unit,
    prefs: SharedPreferences,
    context: Context
) {
    var currentPage by remember { mutableStateOf(InitPage.LEGAL) }
    var uiState by remember { mutableStateOf(InitUiState()) }
    
    // 初始化组件列表（仅显示核心组件，runtime_libs 在后台处理）
    LaunchedEffect(Unit) {
        val components = mutableListOf(
            ComponentState("dotnet", context.getString(R.string.init_component_dotnet_desc), "dotnet.tar.xz", true)
        )
        
        uiState = uiState.copy(
            components = components,
            hasPermissions = permissionManager.hasRequiredPermissions()
        )
        
        // 检查是否已同意法律条款
        val legalAgreed = prefs.getBoolean(AppConstants.InitKeys.LEGAL_AGREED, false)
        if (legalAgreed) {
            currentPage = InitPage.SETUP
        }
    }

    // 深色渐变背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        // 装饰性背景元素
        DecorativeBackground()
        
        // 主内容
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { if (targetState > initialState) it else -it },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(300)) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { if (targetState > initialState) -it else it },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200))
            },
            label = "page_transition"
        ) { page ->
            when (page) {
                InitPage.LEGAL -> LegalPage(
                    onAccept = {
                        prefs.edit().putBoolean(AppConstants.InitKeys.LEGAL_AGREED, true).apply()
                        currentPage = InitPage.SETUP
                    },
                    onDecline = onExit,
                    context = context
                )
                InitPage.SETUP -> SetupPage(
                    uiState = uiState,
                    onRequestPermissions = {
                        if (permissionManager.hasRequiredPermissions()) {
                            prefs.edit().putBoolean(AppConstants.InitKeys.PERMISSIONS_GRANTED, true).apply()
                            uiState = uiState.copy(hasPermissions = true)
                        } else {
                            permissionManager.requestRequiredPermissions(object : PermissionManager.PermissionCallback {
                                override fun onPermissionsGranted() {
                                    prefs.edit().putBoolean(AppConstants.InitKeys.PERMISSIONS_GRANTED, true).apply()
                                    uiState = uiState.copy(hasPermissions = true)
                                    Toast.makeText(context, context.getString(R.string.init_permissions_granted), Toast.LENGTH_SHORT).show()
                                }
                                override fun onPermissionsDenied() {
                                    Toast.makeText(context, context.getString(R.string.init_permissions_denied), Toast.LENGTH_LONG).show()
                                }
                            })
                        }
                    },
                    onStartExtraction = {
                        if (uiState.isExtracting) return@SetupPage
                        uiState = uiState.copy(isExtracting = true, statusMessage = context.getString(R.string.init_preparing_file))
                        
                        onExtract(uiState.components) { index, progress, installed, status ->
                            val list = uiState.components.toMutableList()
                            list[index] = list[index].copy(progress = progress, isInstalled = installed, status = status)
                            val overall = list.sumOf { it.progress } / list.size
                            uiState = uiState.copy(
                                components = list,
                                overallProgress = overall,
                                statusMessage = status,
                                isComplete = overall >= 100
                            )
                        }
                    },
                    context = context
                )
            }
        }
    }
}

/**
 * 装饰性背景
 */
@Composable
private fun DecorativeBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 渐变光晕
        Box(
            modifier = Modifier
                .size(600.dp)
                .offset(x = (-200).dp, y = (-100).dp)
                .graphicsLayer { rotationZ = rotation }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 150.dp, y = 100.dp)
                .graphicsLayer { rotationZ = -rotation * 0.5f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * 法律说明页面 - 横屏双栏布局
 */
@Composable
private fun LegalPage(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // 左侧品牌区域
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            clip = true
                            scaleX = 1.42f
                            scaleY = 1.42f
                        },
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "RaLaunch",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "v1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // 步骤指示器
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepDot(isActive = true, isPassed = false, label = "1")
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
                StepDot(isActive = false, isPassed = false, label = "2")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.init_legal_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.width(32.dp))
        
        // 右侧内容区域
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.init_legal_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 法律条款滚动区域
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.init_legal_terms),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 官方链接
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nicholasng1998/RotatingartLauncher")))
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.init_cannot_open_browser), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.init_legal_official_download),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(R.string.init_exit))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.init_accept_and_continue))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 初始化页面 - Material3 横屏左右分栏布局
 */
@Composable
private fun SetupPage(
    uiState: InitUiState,
    onRequestPermissions: () -> Unit,
    onStartExtraction: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ========== 左侧：进度展示区 ==========
        Surface(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // 大进度环 + Logo
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 进度轨道（始终显示）
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(180.dp),
                        strokeWidth = 12.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        trackColor = Color.Transparent
                    )
                    
                    // 进度条
                    if (uiState.isExtracting) {
                        CircularProgressIndicator(
                            progress = { uiState.overallProgress / 100f },
                            modifier = Modifier.size(180.dp),
                            strokeWidth = 12.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                    
                    // 中心内容
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.isExtracting) {
                            // 安装中：显示百分比
                            Text(
                                text = "${uiState.overallProgress}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            // 未开始：显示 Logo
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .graphicsLayer {
                                            clip = true
                                            scaleX = 1.42f
                                            scaleY = 1.42f
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 品牌名
                Text(
                    text = "RaLaunch",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 状态文字
                Text(
                    text = if (uiState.isExtracting) 
                        stringResource(R.string.init_installing) 
                    else 
                        stringResource(R.string.init_click_to_start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 当前操作状态
                if (uiState.isExtracting && uiState.statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            maxLines = 1
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 步骤指示器
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepDot(isActive = false, isPassed = true, label = "1")
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(1.dp)
                            )
                    )
                    StepDot(isActive = true, isPassed = false, label = "2")
                }
            }
        }
        
        // ========== 右侧：组件与操作区 ==========
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 权限卡片
                if (!uiState.hasPermissions) {
                    PermissionCard(
                        hasPermissions = uiState.hasPermissions,
                        onRequestPermissions = onRequestPermissions
                    )
                }

                // 组件区域
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // 标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "运行环境组件",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "安装游戏所需的运行时组件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 组件列表
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            uiState.components.forEach { component ->
                                ComponentCard(component)
                            }
                        }
                    }
                }

                // 功能说明卡片
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "首次运行需要解压运行环境，仅需几分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 开始按钮
            Button(
                onClick = onStartExtraction,
                enabled = !uiState.isExtracting && uiState.hasPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.init_installing),
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (uiState.hasPermissions) 
                            "开始安装"
                        else 
                            stringResource(R.string.init_grant_permissions),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 700, heightDp = 350)
@Composable
private fun InitializationLegalPagePreview() {
    val context = LocalContext.current
    RaLaunchTheme {
        LegalPage(
            onAccept = {},
            onDecline = {},
            context = context
        )
    }
}

@Preview(showBackground = true, widthDp = 700, heightDp = 350)
@Composable
private fun InitializationSetupPagePreview() {
    val context = LocalContext.current
    val previewState = InitUiState(
        hasPermissions = true,
        components = listOf(
            ComponentState(
                name = "dotnet",
                description = "Microsoft .NET Runtime",
                fileName = "dotnet.tar.xz",
                needsExtraction = true,
                progress = 45
            )
        ),
        isExtracting = true,
        overallProgress = 45,
        statusMessage = "正在解压 dotnet..."
    )

    RaLaunchTheme {
        SetupPage(
            uiState = previewState,
            onRequestPermissions = {},
            onStartExtraction = {},
            context = context
        )
    }
}

/**
 * 步骤指示点
 */
@Composable
private fun StepDot(
    isActive: Boolean,
    isPassed: Boolean,
    label: String
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(
                if (!isActive && !isPassed) 
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPassed) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 权限请求卡片
 */
@Composable
private fun PermissionCard(
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (hasPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasPermissions) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasPermissions) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (hasPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.init_grant_permissions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.init_permission_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!hasPermissions) {
                FilledTonalButton(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.init_grant_permissions))
                }
            }
        }
    }
}

/**
 * 组件卡片
 */
@Composable
private fun ComponentCard(component: ComponentState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                component.isInstalled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                component.progress > 0 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            component.isInstalled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            component.progress > 0 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    component.isInstalled -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    component.progress > 0 -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    else -> Icon(
                        imageVector = Icons.Outlined.Inventory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = component.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                
                // 进度条
                if (component.progress > 0 && !component.isInstalled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { component.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            
            // 进度百分比
            if (component.progress > 0 || component.isInstalled) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (component.isInstalled) "✓" else "${component.progress}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (component.isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
