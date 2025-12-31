package com.app.ralaunch.sponsor

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.app.ralaunch.R
import com.app.ralaunch.utils.LocaleManager
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
 * 赞助商星空墙 - 全屏沉浸式体验
 * 使用 Konfetti 库实现星空背景和庆祝特效
 */
class SponsorsActivity : AppCompatActivity() {

    private lateinit var sponsorWallView: SponsorWallView
    private lateinit var konfettiView: KonfettiView
    private lateinit var loadingContainer: View
    private lateinit var errorContainer: View
    private lateinit var tvError: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnSponsor: ImageButton
    private lateinit var btnRetry: com.google.android.material.button.MaterialButton

    private lateinit var sponsorService: SponsorRepositoryService
    
    private val handler = Handler(Looper.getMainLooper())
    private var starfieldRunnable: Runnable? = null
    private var meteorRunnable: Runnable? = null

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(LocaleManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 必须先设置这些窗口属性（在 setContentView 之前）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        setContentView(R.layout.activity_sponsors)
        
        // 隐藏系统UI必须在 setContentView 之后
        hideSystemUI()

        initViews()
        setupListeners()
        initService()
        
        // 立即启动星空效果
        startStarfieldEffect()
        
        loadSponsors()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun initViews() {
        sponsorWallView = findViewById(R.id.sponsorWallView)
        konfettiView = findViewById(R.id.konfettiView)
        loadingContainer = findViewById(R.id.loadingContainer)
        errorContainer = findViewById(R.id.errorContainer)
        tvError = findViewById(R.id.tvError)
        btnBack = findViewById(R.id.btnBack)
        btnSponsor = findViewById(R.id.btnSponsor)
        btnRetry = findViewById(R.id.btnRetry)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnSponsor.setOnClickListener {
            playCelebration()
            openSponsorPage()
        }

        btnRetry.setOnClickListener { loadSponsors() }

        // 赞助者点击
        sponsorWallView.onSponsorClick = { sponsor ->
            showSponsorInfo(sponsor)
        }
        
        // 高级赞助者点击时触发 Konfetti 特效
        sponsorWallView.onHighTierSponsorClick = { tier, x, y ->
            playTierCelebration(tier, x, y)
        }
    }

    private fun initService() {
        sponsorService = SponsorRepositoryService(this)
    }

    /**
     * 使用 Konfetti 实现星空背景效果
     * 静态闪烁的星星，不下落
     */
    private fun startStarfieldEffect() {
        // 初始化星空 - 填充固定星星
        emitInitialStars()
        
        // 星星闪烁效果
        starfieldRunnable = object : Runnable {
            override fun run() {
                emitTwinkle()
                handler.postDelayed(this, 400) // 每0.4秒闪烁几颗星
            }
        }
        handler.postDelayed(starfieldRunnable!!, 300)
        
        // 偶尔的流星效果
        meteorRunnable = object : Runnable {
            override fun run() {
                emitMeteor()
                handler.postDelayed(this, 10000 + Random.nextLong(10000)) // 10-20秒一颗流星
            }
        }
        handler.postDelayed(meteorRunnable!!, 5000)
    }
    
    /**
     * 初始化星空 - 在随机位置放置静态星星
     */
    private fun emitInitialStars() {
        val starColors = listOf(
            0xFFFFFFFF.toInt(),  // 白色
            0xFFE8E8FF.toInt(),  // 淡蓝白
            0xFFFFF8E8.toInt(),  // 淡黄白
        )
        
        // 在随机位置生成静态星星
        repeat(80) {
            val xPos = Random.nextDouble()
            val yPos = Random.nextDouble() * 0.95
            
            konfettiView.start(
                Party(
                    speed = 0f,
                    maxSpeed = 0f,
                    damping = 1f,
                    colors = starColors,
                    shapes = listOf(Shape.Circle),
                    size = listOf(Size(1), Size(2), Size(3)),
                    timeToLive = 30000L,  // 30秒
                    fadeOutEnabled = false,
                    position = Position.Relative(xPos, yPos),
                    emitter = Emitter(duration = 50, TimeUnit.MILLISECONDS).max(1)
                )
            )
        }
    }
    
    /**
     * 星星闪烁效果 - 在随机位置短暂亮起
     */
    private fun emitTwinkle() {
        val twinkleColors = listOf(
            0xFFFFFFFF.toInt(),  // 亮白
            0xFFFFFF99.toInt(),  // 亮黄
            0xFFCCCCFF.toInt(),  // 淡蓝
        )
        
        // 随机位置闪烁2-3颗星
        repeat(Random.nextInt(2, 4)) {
            val xPos = Random.nextDouble()
            val yPos = Random.nextDouble() * 0.9
            
            konfettiView.start(
                Party(
                    speed = 0f,
                    maxSpeed = 0f,
                    damping = 1f,
                    colors = twinkleColors,
                    shapes = listOf(Shape.Circle),
                    size = listOf(Size(3), Size(4), Size(5)),
                    timeToLive = 800L,  // 短暂闪烁
                    fadeOutEnabled = true,
                    position = Position.Relative(xPos, yPos),
                    emitter = Emitter(duration = 50, TimeUnit.MILLISECONDS).max(1)
                )
            )
        }
    }
    
    /**
     * 发射流星
     */
    private fun emitMeteor() {
        val startX = Random.nextDouble() * 0.6 + 0.2  // 0.2 - 0.8
        
        konfettiView.start(
            Party(
                angle = 135,  // 左下方向
                spread = 5,
                speed = 80f,
                maxSpeed = 120f,
                damping = 0.95f,
                colors = listOf(0xFFFFFFFF.toInt(), 0xFFFFD700.toInt(), 0xFF87CEEB.toInt()),
                shapes = listOf(Shape.Circle),
                size = listOf(Size(3), Size(4), Size(5)),
                timeToLive = 1500L,
                fadeOutEnabled = true,
                position = Position.Relative(startX, 0.0),
                emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(15)
            )
        )
    }

    private fun loadSponsors() {
        showLoading()

        lifecycleScope.launch {
            val result = sponsorService.fetchSponsors(forceRefresh = true)

            result.fold(
                onSuccess = { repository ->
                    if (repository.sponsors.isEmpty()) {
                        showError(getString(R.string.sponsors_empty))
                    } else {
                        showSponsors(repository)
                    }
                },
                onFailure = { error ->
                    showError(getString(R.string.sponsors_error) + "\n" + error.message)
                }
            )
        }
    }

    private fun showLoading() {
        loadingContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
        sponsorWallView.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        sponsorWallView.visibility = View.GONE
        tvError.text = message
    }

    private fun showSponsors(repository: SponsorRepository) {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        sponsorWallView.visibility = View.VISIBLE
        sponsorWallView.setSponsors(repository.sponsors, repository.tiers)

        // 入场庆祝
        handler.postDelayed({ playEntranceCelebration() }, 800)
    }

    /**
     * 入场庆祝特效
     */
    private fun playEntranceCelebration() {
        val colors = listOf(
            0xFFFFD700.toInt(),
            0xFFFF6B9D.toInt(),
            0xFF4ECDC4.toInt(),
            0xFFB48DEF.toInt(),
            0xFFFFE66D.toInt(),
            0xFFFFFFFF.toInt()
        )

        // 左侧
        konfettiView.start(
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
        konfettiView.start(
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
        konfettiView.start(
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

    /**
     * 赞助按钮庆祝
     */
    private fun playCelebration() {
        val colors = listOf(
            0xFFFF6B9D.toInt(),
            0xFFFFD700.toInt(),
            0xFF4ECDC4.toInt(),
            0xFFB48DEF.toInt()
        )

        konfettiView.start(
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

    /**
     * 高级赞助者专属庆祝特效
     */
    private fun playTierCelebration(tier: SponsorTier, centerX: Float, centerY: Float) {
        val color = try {
            Color.parseColor(tier.color)
        } catch (e: Exception) {
            Color.WHITE
        }
        
        val colors = listOf(
            color,
            Color.WHITE,
            0xFFFFD700.toInt()
        )

        val xRatio = (centerX / konfettiView.width).toDouble().coerceIn(0.05, 0.95)
        val yRatio = (centerY / konfettiView.height).toDouble().coerceIn(0.05, 0.95)

        when {
            tier.order >= 100 -> {
                // 银河守护者 - 超级爆炸 + 环绕
                konfettiView.start(
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
                // 外圈扩散
                konfettiView.start(
                    Party(
                        speed = 30f,
                        maxSpeed = 50f,
                        damping = 0.95f,
                        spread = 360,
                        colors = listOf(Color.WHITE, 0xFFFFD700.toInt()),
                        size = listOf(Size(2), Size(3)),
                        position = Position.Relative(xRatio, yRatio),
                        delay = 100,
                        emitter = Emitter(duration = 300, TimeUnit.MILLISECONDS).max(60)
                    )
                )
            }
            tier.order >= 80 -> {
                // 星空探索家 - 烟花
                konfettiView.start(
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
                // 极致合伙人
                konfettiView.start(
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

    private fun showSponsorInfo(sponsor: Sponsor) {
        val message = buildString {
            append(sponsor.name)
            if (sponsor.bio.isNotEmpty()) {
                append("\n\n")
                append(sponsor.bio)
            }
            if (sponsor.joinDate.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.sponsors_join_date_format, sponsor.joinDate))
            }
        }

        if (sponsor.website.isNotEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(sponsor.name)
                .setMessage(message)
                .setPositiveButton(R.string.view) { _, _ -> openUrl(sponsor.website) }
                .setNegativeButton(R.string.close, null)
                .show()
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_open_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSponsorPage() {
        val url = if (SponsorRepositoryService.isChinese(this)) {
            "https://afdian.com/a/RotatingartLauncher"
        } else {
            "https://www.patreon.com/c/RotatingArtLauncher"
        }
        openUrl(url)
    }

    override fun onDestroy() {
        super.onDestroy()
        starfieldRunnable?.let { handler.removeCallbacks(it) }
        meteorRunnable?.let { handler.removeCallbacks(it) }
    }
}
