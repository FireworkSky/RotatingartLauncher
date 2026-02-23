package com.app.ralaunch.feature.sponsor

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 赞助商星空墙页面 - Compose 版本
 * 横屏沉浸式体验，使用 Konfetti 实现星空效果
 */
@Composable
fun SponsorsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var uiState by remember { mutableStateOf<SponsorsUiState>(SponsorsUiState.Loading) }
    var konfettiView by remember { mutableStateOf<KonfettiView?>(null) }
    
    val sponsorService = remember { SponsorRepositoryService(context) }
    
    // 加载赞助商数据
    LaunchedEffect(Unit) {
        val result = sponsorService.fetchSponsors(forceRefresh = true)
        result.fold(
            onSuccess = { repository ->
                if (repository.sponsors.isEmpty()) {
                    uiState = SponsorsUiState.Error(context.getString(R.string.sponsors_empty))
                } else {
                    uiState = SponsorsUiState.Success(repository)
                    // 延迟播放入场动画
                    delay(800)
                    konfettiView?.let { playEntranceCelebration(it) }
                }
            },
            onFailure = { error ->
                uiState = SponsorsUiState.Error(
                    context.getString(R.string.sponsors_error) + "\n" + error.message
                )
            }
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 星空背景
        StarfieldBackground()
        
        // Konfetti 效果层 (使用 AndroidView 包装)
        AndroidView(
            factory = { ctx ->
                KonfettiView(ctx).also { view ->
                    konfettiView = view
                    // 启动星空效果
                    startStarfieldEffect(view, scope)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 主内容
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部标题栏
            SponsorsTopBar(
                onBack = onBack,
                onSponsor = {
                    konfettiView?.let { playCelebration(it) }
                    openSponsorPage(context)
                }
            )
            
            // 内容区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is SponsorsUiState.Loading -> {
                        LoadingState()
                    }
                    is SponsorsUiState.Error -> {
                        ErrorState(
                            message = state.message,
                            onRetry = {
                                uiState = SponsorsUiState.Loading
                                scope.launch {
                                    val result = sponsorService.fetchSponsors(forceRefresh = true)
                                    result.fold(
                                        onSuccess = { repository ->
                                            if (repository.sponsors.isEmpty()) {
                                                uiState = SponsorsUiState.Error(context.getString(R.string.sponsors_empty))
                                            } else {
                                                uiState = SponsorsUiState.Success(repository)
                                            }
                                        },
                                        onFailure = { error ->
                                            uiState = SponsorsUiState.Error(
                                                context.getString(R.string.sponsors_error) + "\n" + error.message
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }
                    is SponsorsUiState.Success -> {
                        // 使用 AndroidView 包装 SponsorWallView
                        AndroidView(
                            factory = { ctx ->
                                SponsorWallView(ctx).apply {
                                    setSponsors(state.repository.sponsors, state.repository.tiers)
                                    onSponsorClick = { sponsor ->
                                        showSponsorInfo(context, sponsor)
                                    }
                                    onHighTierSponsorClick = { tier, x, y ->
                                        konfettiView?.let { 
                                            playTierCelebration(it, tier, x, y) 
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            // 底部提示
            Text(
                text = stringResource(R.string.sponsors_tip_gesture),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SponsorsTopBar(
    onBack: () -> Unit,
    onSponsor: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
            .padding(top = 32.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        // 返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White
            )
        }
        
        // 标题
        Text(
            text = stringResource(R.string.sponsors_wall_title),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
        
        // 赞助按钮
        IconButton(
            onClick = onSponsor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = stringResource(R.string.become_sponsor),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StarfieldBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "starfield")
    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )
    
    val stars = remember {
        List(100) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.5f
            )
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { index, star ->
            val alpha = if (index % 5 == 0) twinkleAlpha * star.alpha else star.alpha
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.size,
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}

private data class Star(val x: Float, val y: Float, val size: Float, val alpha: Float)

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.sponsors_loading),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

private sealed class SponsorsUiState {
    data object Loading : SponsorsUiState()
    data class Error(val message: String) : SponsorsUiState()
    data class Success(val repository: SponsorRepository) : SponsorsUiState()
}

// Konfetti 效果函数
private fun startStarfieldEffect(view: KonfettiView, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        // 初始星空
        emitInitialStars(view)
        
        // 持续闪烁
        while (true) {
            delay(400)
            emitTwinkle(view)
        }
    }
    
    // 流星效果
    scope.launch {
        while (true) {
            delay(10000 + Random.nextLong(10000))
            emitMeteor(view)
        }
    }
}

private fun emitInitialStars(view: KonfettiView) {
    val starColors = listOf(0xFFFFFFFF.toInt(), 0xFFE8E8FF.toInt(), 0xFFFFF8E8.toInt())
    repeat(80) {
        view.start(
            Party(
                speed = 0f,
                maxSpeed = 0f,
                damping = 1f,
                colors = starColors,
                shapes = listOf(Shape.Circle),
                size = listOf(Size(1), Size(2), Size(3)),
                timeToLive = 30000L,
                fadeOutEnabled = false,
                position = Position.Relative(Random.nextDouble(), Random.nextDouble() * 0.95),
                emitter = Emitter(duration = 50, TimeUnit.MILLISECONDS).max(1)
            )
        )
    }
}

private fun emitTwinkle(view: KonfettiView) {
    val twinkleColors = listOf(0xFFFFFFFF.toInt(), 0xFFFFFF99.toInt(), 0xFFCCCCFF.toInt())
    repeat(Random.nextInt(2, 4)) {
        view.start(
            Party(
                speed = 0f,
                maxSpeed = 0f,
                damping = 1f,
                colors = twinkleColors,
                shapes = listOf(Shape.Circle),
                size = listOf(Size(3), Size(4), Size(5)),
                timeToLive = 800L,
                fadeOutEnabled = true,
                position = Position.Relative(Random.nextDouble(), Random.nextDouble() * 0.9),
                emitter = Emitter(duration = 50, TimeUnit.MILLISECONDS).max(1)
            )
        )
    }
}

private fun emitMeteor(view: KonfettiView) {
    view.start(
        Party(
            angle = 135,
            spread = 5,
            speed = 80f,
            maxSpeed = 120f,
            damping = 0.95f,
            colors = listOf(0xFFFFFFFF.toInt(), 0xFFFFD700.toInt(), 0xFF87CEEB.toInt()),
            shapes = listOf(Shape.Circle),
            size = listOf(Size(3), Size(4), Size(5)),
            timeToLive = 1500L,
            fadeOutEnabled = true,
            position = Position.Relative(Random.nextDouble() * 0.6 + 0.2, 0.0),
            emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(15)
        )
    )
}

private fun playEntranceCelebration(view: KonfettiView) {
    val colors = listOf(
        0xFFFFD700.toInt(), 0xFFFF6B9D.toInt(), 0xFF4ECDC4.toInt(),
        0xFFB48DEF.toInt(), 0xFFFFE66D.toInt(), 0xFFFFFFFF.toInt()
    )
    
    // 左侧
    view.start(
        Party(
            angle = Angle.RIGHT - 30,
            spread = Spread.SMALL,
            speed = 40f,
            maxSpeed = 70f,
            damping = 0.9f,
            colors = colors,
            position = Position.Relative(0.0, 0.4),
            emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(35)
        )
    )
    
    // 右侧
    view.start(
        Party(
            angle = Angle.LEFT + 30,
            spread = Spread.SMALL,
            speed = 40f,
            maxSpeed = 70f,
            damping = 0.9f,
            colors = colors,
            position = Position.Relative(1.0, 0.4),
            emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(35)
        )
    )
    
    // 顶部
    view.start(
        Party(
            angle = Angle.BOTTOM,
            spread = 90,
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            colors = colors,
            position = Position.Relative(0.5, 0.0),
            emitter = Emitter(duration = 2500, TimeUnit.MILLISECONDS).perSecond(25)
        )
    )
}

private fun playCelebration(view: KonfettiView) {
    val colors = listOf(
        0xFFFF6B9D.toInt(), 0xFFFFD700.toInt(), 
        0xFF4ECDC4.toInt(), 0xFFB48DEF.toInt()
    )
    
    view.start(
        Party(
            speed = 0f,
            maxSpeed = 50f,
            damping = 0.9f,
            spread = 360,
            colors = colors,
            position = Position.Relative(0.9, 0.08),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(80)
        )
    )
}

private fun playTierCelebration(view: KonfettiView, tier: SponsorTier, centerX: Float, centerY: Float) {
    val color = try {
        AndroidColor.parseColor(tier.color)
    } catch (_: Exception) {
        AndroidColor.WHITE
    }
    
    val colors = listOf(color, AndroidColor.WHITE, 0xFFFFD700.toInt())
    val xRatio = (centerX / view.width).toDouble().coerceIn(0.05, 0.95)
    val yRatio = (centerY / view.height).toDouble().coerceIn(0.05, 0.95)
    
    when {
        tier.order >= 100 -> {
            view.start(
                Party(
                    speed = 0f,
                    maxSpeed = 70f,
                    damping = 0.9f,
                    spread = 360,
                    colors = colors,
                    size = listOf(Size(4), Size(6), Size(8)),
                    position = Position.Relative(xRatio, yRatio),
                    emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(150)
                )
            )
        }
        tier.order >= 80 -> {
            view.start(
                Party(
                    speed = 0f,
                    maxSpeed = 55f,
                    damping = 0.9f,
                    spread = 360,
                    colors = colors,
                    size = listOf(Size(3), Size(5), Size(7)),
                    position = Position.Relative(xRatio, yRatio),
                    emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(100)
                )
            )
        }
        tier.order >= 60 -> {
            view.start(
                Party(
                    speed = 0f,
                    maxSpeed = 45f,
                    damping = 0.9f,
                    spread = 360,
                    colors = colors,
                    size = listOf(Size(3), Size(4)),
                    position = Position.Relative(xRatio, yRatio),
                    emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(60)
                )
            )
        }
    }
}

private fun showSponsorInfo(context: android.content.Context, sponsor: Sponsor) {
    val message = buildString {
        append(sponsor.name)
        if (sponsor.bio.isNotEmpty()) {
            append("\n\n")
            append(sponsor.bio)
        }
        if (sponsor.joinDate.isNotEmpty()) {
            append("\n\n")
            append(context.getString(R.string.sponsors_join_date_format, sponsor.joinDate))
        }
    }
    
    if (sponsor.website.isNotEmpty()) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(sponsor.name)
            .setMessage(message)
            .setPositiveButton(R.string.view) { _, _ -> 
                openUrl(context, sponsor.website) 
            }
            .setNegativeButton(R.string.close, null)
            .show()
    } else {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, R.string.error_open_browser, Toast.LENGTH_SHORT).show()
    }
}

private fun openSponsorPage(context: android.content.Context) {
    val url = if (SponsorRepositoryService.isChinese(context)) {
        "https://afdian.com/a/RotatingartLauncher"
    } else {
        "https://www.patreon.com/c/RotatingArtLauncher"
    }
    openUrl(context, url)
}
