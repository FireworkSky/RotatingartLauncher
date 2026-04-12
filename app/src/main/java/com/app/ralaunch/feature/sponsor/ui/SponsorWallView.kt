package com.app.ralaunch.feature.sponsor.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.app.ralaunch.R
import com.app.ralaunch.feature.sponsor.Sponsor
import com.app.ralaunch.feature.sponsor.SponsorTier
import kotlin.math.*

/**
 * 赞助者星空墙 - 简化版
 * 只负责显示赞助者头像、名称和光晕效果
 * 粒子特效交给 Konfetti 处理
 */
class SponsorWallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 赞助者节点
    private val sponsorNodes = mutableListOf<SponsorNode>()
    
    // 绘制相关
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    
    // 动画
    private var animationTime = 0f
    private var entryAnimationProgress = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            animationTime += 0.016f
            invalidate()
        }
    }
    
    // 入场动画
    private val entryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        interpolator = OvershootInterpolator(0.8f)
        addUpdateListener {
            entryAnimationProgress = it.animatedValue as Float
            invalidate()
        }
    }
    
    // 视图变换
    private var offsetX = 0f
    private var offsetY = 0f
    private var scale = 1f
    private val minScale = 0.3f
    private val maxScale = 4f
    
    // 手势检测
    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector
    
    // 点击回调
    var onSponsorClick: ((Sponsor) -> Unit)? = null
    
    // 点击高级赞助者时的回调（用于触发 Konfetti）
    var onHighTierSponsorClick: ((SponsorTier, Float, Float) -> Unit)? = null
    
    // 默认头像
    private var defaultAvatar: Bitmap? = null
    
    // 赞助级别映射
    private var tierMap: Map<String, SponsorTier> = emptyMap()
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.TRANSPARENT)
        
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                offsetX -= distanceX / scale
                offsetY -= distanceY / scale
                invalidate()
                return true
            }
            
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleClick(e.x, e.y)
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                animateToCenter()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val decayFactor = 0.003f
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = 500
                    addUpdateListener { anim ->
                        val factor = anim.animatedValue as Float
                        offsetX += velocityX * decayFactor * factor / scale
                        offsetY += velocityY * decayFactor * factor / scale
                        invalidate()
                    }
                    start()
                }
                return true
            }
        })
        
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scale
                scale *= detector.scaleFactor
                scale = scale.coerceIn(minScale, maxScale)
                
                if (scale != oldScale) {
                    val focusX = detector.focusX
                    val focusY = detector.focusY
                    offsetX += (focusX - width / 2f) * (1f / oldScale - 1f / scale)
                    offsetY += (focusY - height / 2f) * (1f / oldScale - 1f / scale)
                }
                
                invalidate()
                return true
            }
        })
        
        loadDefaultAvatar()
    }
    
    private fun loadDefaultAvatar() {
        try {
            val drawable = context.getDrawable(R.drawable.ic_person)
            defaultAvatar = drawable?.toBitmap(100, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 设置赞助者数据
     */
    fun setSponsors(sponsors: List<Sponsor>, tiers: List<SponsorTier>) {
        sponsorNodes.clear()
        tierMap = tiers.associateBy { it.id }
        
        // 按级别排序
        val sortedSponsors = sponsors.sortedByDescending { sponsor ->
            tierMap[sponsor.tier]?.order ?: 0
        }
        
        // 创建节点
        sortedSponsors.forEachIndexed { index, sponsor ->
            val tier = tierMap[sponsor.tier]
            val node = createSponsorNode(sponsor, tier, index, sortedSponsors.size)
            sponsorNodes.add(node)
            loadAvatar(node)
        }
        
        // 入场动画
        entryAnimationProgress = 0f
        entryAnimator.start()
        
        if (!animator.isRunning) {
            animator.start()
        }
        
        invalidate()
    }
    
    private fun createSponsorNode(
        sponsor: Sponsor,
        tier: SponsorTier?,
        index: Int,
        total: Int
    ): SponsorNode {
        val tierOrder = tier?.order ?: 0
        
        // 根据级别确定大小 - 高级别显著更大
        val baseSize = when {
            tierOrder >= 100 -> 160f  // 银河守护者
            tierOrder >= 80 -> 120f   // 星空探索家
            tierOrder >= 60 -> 90f    // 极致合伙人
            tierOrder >= 40 -> 65f    // 星光先锋
            else -> 45f               // 爱心维护员
        }
        
        val color = try {
            Color.parseColor(tier?.color ?: "#FFFFFF")
        } catch (e: Exception) {
            Color.WHITE
        }
        
        val (x, y) = calculatePosition(index, total, tierOrder)
        
        return SponsorNode(
            sponsor = sponsor,
            x = x,
            y = y,
            baseSize = baseSize,
            color = color,
            tierOrder = tierOrder
        )
    }
    
    private fun calculatePosition(index: Int, total: Int, tierOrder: Int): Pair<Float, Float> {
        val goldenAngle = 137.508f * (Math.PI / 180f)
        val angle = index * goldenAngle
        
        // 高级别在内圈，低级别在外圈
        val tierBasedRadius = when {
            tierOrder >= 100 -> 50f + kotlin.random.Random.nextFloat() * 150f
            tierOrder >= 80 -> 200f + kotlin.random.Random.nextFloat() * 200f
            tierOrder >= 60 -> 400f + kotlin.random.Random.nextFloat() * 200f
            tierOrder >= 40 -> 600f + kotlin.random.Random.nextFloat() * 300f
            else -> 800f + kotlin.random.Random.nextFloat() * 400f
        }
        
        val x = (cos(angle) * tierBasedRadius).toFloat()
        val y = (sin(angle) * tierBasedRadius).toFloat()
        
        return Pair(x, y)
    }
    
    private fun loadAvatar(node: SponsorNode) {
        if (node.sponsor.avatarUrl.isEmpty()) {
            node.avatar = defaultAvatar
            return
        }
        
        try {
            Glide.with(context)
                .asBitmap()
                .load(node.sponsor.avatarUrl)
                .circleCrop()
                .override((node.baseSize * 2.5f).toInt())
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        node.avatar = resource
                        invalidate()
                    }
                    
                    override fun onLoadCleared(placeholder: Drawable?) {
                        node.avatar = defaultAvatar
                    }
                    
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        node.avatar = defaultAvatar
                    }
                })
        } catch (e: Exception) {
            node.avatar = defaultAvatar
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(width / 2f + offsetX * scale, height / 2f + offsetY * scale)
        canvas.scale(scale, scale)
        
        // 绘制连接线
        drawConstellationLines(canvas)
        
        // 绘制赞助者节点
        val sortedNodes = sponsorNodes.sortedBy { it.tierOrder }
        sortedNodes.forEach { node ->
            val nodeIndex = sponsorNodes.indexOf(node)
            val nodeProgress = calculateNodeEntryProgress(nodeIndex)
            if (nodeProgress > 0) {
                drawSponsorNode(canvas, node, nodeProgress)
            }
        }
        
        canvas.restore()
    }
    
    private fun drawConstellationLines(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        
        for (i in sponsorNodes.indices) {
            for (j in i + 1 until sponsorNodes.size) {
                val node1 = sponsorNodes[i]
                val node2 = sponsorNodes[j]
                
                if (abs(node1.tierOrder - node2.tierOrder) > 20) continue
                
                val distance = hypot(node1.x - node2.x, node1.y - node2.y)
                val maxDist = when {
                    node1.tierOrder >= 80 -> 300f
                    node1.tierOrder >= 60 -> 250f
                    else -> 200f
                }
                
                if (distance < maxDist) {
                    val alpha = ((1f - distance / maxDist) * 50 * entryAnimationProgress).toInt()
                    paint.color = Color.argb(alpha, 
                        Color.red(node1.color), 
                        Color.green(node1.color), 
                        Color.blue(node1.color))
                    canvas.drawLine(node1.x, node1.y, node2.x, node2.y, paint)
                }
            }
        }
    }
    
    private fun calculateNodeEntryProgress(index: Int): Float {
        if (entryAnimationProgress >= 1f) return 1f
        val nodeDelay = index.toFloat() / sponsorNodes.size * 0.6f
        val nodeProgress = (entryAnimationProgress - nodeDelay) / 0.4f
        return nodeProgress.coerceIn(0f, 1f)
    }
    
    private fun drawSponsorNode(canvas: Canvas, node: SponsorNode, entryProgress: Float) {
        val animatedSize = node.baseSize * entryProgress
        
        // 外层光晕
        drawOuterGlow(canvas, node, animatedSize)

        // 高级别动态光环位于头像后方
        if (node.tierOrder >= 40) {
            drawDynamicRing(canvas, node, animatedSize)
        }
        
        // 头像
        drawAvatar(canvas, node, animatedSize)
        
        // 最高级别皇冠
        if (node.tierOrder >= 100) {
            drawCrown(canvas, node, animatedSize)
        }

        // 名称保持在最上层，避免被装饰元素遮挡
        drawName(canvas, node, animatedSize, entryProgress)
    }
    
    private fun drawOuterGlow(canvas: Canvas, node: SponsorNode, size: Float) {
        val glowSize = size * when {
            node.tierOrder >= 100 -> 1.3f
            node.tierOrder >= 80 -> 1.1f
            node.tierOrder >= 60 -> 0.9f
            node.tierOrder >= 40 -> 0.75f
            else -> 0.55f
        }
        
        val glowAlpha = when {
            node.tierOrder >= 100 -> 200
            node.tierOrder >= 80 -> 160
            node.tierOrder >= 60 -> 120
            node.tierOrder >= 40 -> 80
            else -> 50
        }
        
        val pulse = 1f + 0.12f * sin(animationTime * 2.5f + node.y * 0.01f)
        
        val gradient = RadialGradient(
            node.x, node.y, glowSize * pulse,
            intArrayOf(
                Color.argb(glowAlpha, Color.red(node.color), Color.green(node.color), Color.blue(node.color)),
                Color.argb(glowAlpha / 3, Color.red(node.color), Color.green(node.color), Color.blue(node.color)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        paint.style = Paint.Style.FILL
        canvas.drawCircle(node.x, node.y, glowSize * pulse, paint)
        paint.shader = null
    }
    
    private fun drawAvatar(canvas: Canvas, node: SponsorNode, size: Float) {
        val avatar = node.avatar ?: defaultAvatar ?: return
        val avatarRadius = size / 2f
        val borderWidth = when {
            node.tierOrder >= 100 -> 5f
            node.tierOrder >= 80 -> 4f
            node.tierOrder >= 60 -> 3f
            else -> 2f
        }
        
        // 边框
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidth
        
        if (node.tierOrder >= 60) {
            val shift = (sin(animationTime * 2) + 1f) / 2f
            paint.color = blendColor(node.color, Color.WHITE, shift * 0.3f)
        } else {
            paint.color = node.color
        }
        canvas.drawCircle(node.x, node.y, avatarRadius + borderWidth, paint)
        
        // 头像
        val avatarRect = RectF(
            node.x - avatarRadius,
            node.y - avatarRadius,
            node.x + avatarRadius,
            node.y + avatarRadius
        )
        
        canvas.save()
        val clipPath = Path().apply {
            addCircle(node.x, node.y, avatarRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        canvas.drawBitmap(avatar, null, avatarRect, null)
        canvas.restore()
    }
    
    private fun drawName(canvas: Canvas, node: SponsorNode, size: Float, alpha: Float) {
        textPaint.textSize = when {
            node.tierOrder >= 100 -> 22f
            node.tierOrder >= 80 -> 18f
            node.tierOrder >= 60 -> 15f
            node.tierOrder >= 40 -> 13f
            else -> 11f
        }
        
        textPaint.color = Color.argb((255 * alpha).toInt(), 255, 255, 255)
        textPaint.setShadowLayer(6f, 0f, 2f, Color.argb((200 * alpha).toInt(), 0, 0, 0))
        
        val name = if (node.sponsor.name.length > 10 && node.tierOrder < 60) {
            node.sponsor.name.take(8) + "..."
        } else {
            node.sponsor.name
        }
        
        canvas.drawText(name, node.x, node.y + size / 2f + textPaint.textSize + 12f, textPaint)
        textPaint.clearShadowLayer()
    }
    
    private fun drawDynamicRing(canvas: Canvas, node: SponsorNode, size: Float) {
        val ringRadius = size / 2f + when {
            node.tierOrder >= 100 -> 20f
            node.tierOrder >= 80 -> 15f
            else -> 10f
        }
        
        ringPaint.strokeWidth = when {
            node.tierOrder >= 100 -> 3f
            node.tierOrder >= 80 -> 2.5f
            else -> 2f
        }
        
        val rotationSpeed = when {
            node.tierOrder >= 100 -> 50f
            node.tierOrder >= 80 -> 40f
            else -> 30f
        }
        
        val startAngle = (animationTime * rotationSpeed + node.x) % 360f
        val sweepAngle = when {
            node.tierOrder >= 100 -> 300f
            node.tierOrder >= 80 -> 240f
            else -> 180f
        }
        
        val gradient = SweepGradient(
            node.x, node.y,
            intArrayOf(Color.TRANSPARENT, node.color, Color.WHITE, node.color, Color.TRANSPARENT),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f)
        )
        ringPaint.shader = gradient
        
        canvas.save()
        canvas.rotate(startAngle, node.x, node.y)
        canvas.drawArc(
            node.x - ringRadius, node.y - ringRadius,
            node.x + ringRadius, node.y + ringRadius,
            0f, sweepAngle, false, ringPaint
        )
        canvas.restore()
        ringPaint.shader = null
    }
    
    private fun drawCrown(canvas: Canvas, node: SponsorNode, size: Float) {
        val crownY = node.y - size / 2f - 25f
        val crownSize = 15f
        
        paint.color = Color.parseColor("#FFD700")
        paint.style = Paint.Style.FILL
        
        val path = Path().apply {
            moveTo(node.x - crownSize, crownY + crownSize)
            lineTo(node.x - crownSize, crownY + crownSize * 0.4f)
            lineTo(node.x - crownSize * 0.5f, crownY + crownSize * 0.7f)
            lineTo(node.x, crownY)
            lineTo(node.x + crownSize * 0.5f, crownY + crownSize * 0.7f)
            lineTo(node.x + crownSize, crownY + crownSize * 0.4f)
            lineTo(node.x + crownSize, crownY + crownSize)
            close()
        }
        canvas.drawPath(path, paint)
        
        val shimmer = (sin(animationTime * 4) + 1f) / 2f
        paint.color = Color.argb((100 + 100 * shimmer).toInt(), 255, 215, 0)
        canvas.drawCircle(node.x, crownY + crownSize * 0.5f, crownSize * 0.3f, paint)
    }
    
    private fun blendColor(color1: Int, color2: Int, ratio: Float): Int {
        val r = (Color.red(color1) * (1 - ratio) + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * (1 - ratio) + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * (1 - ratio) + Color.blue(color2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }
    
    private fun handleClick(screenX: Float, screenY: Float) {
        val worldX = (screenX - width / 2f) / scale - offsetX
        val worldY = (screenY - height / 2f) / scale - offsetY
        
        val clickedNode = sponsorNodes
            .sortedByDescending { it.baseSize }
            .find { node ->
                val distance = hypot(node.x - worldX, node.y - worldY)
                distance < node.baseSize / 2f + 15f
            }
        
        clickedNode?.let { node ->
            onSponsorClick?.invoke(node.sponsor)
            
            // 高级别赞助者点击时触发 Konfetti 效果
            val tier = tierMap[node.sponsor.tier]
            if (tier != null && tier.order >= 60) {
                // 计算屏幕坐标
                val screenNodeX = width / 2f + (node.x + offsetX) * scale
                val screenNodeY = height / 2f + (node.y + offsetY) * scale
                onHighTierSponsorClick?.invoke(tier, screenNodeX, screenNodeY)
            }
            
            // 点击缩放动画
            ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
                duration = 300
                addUpdateListener { anim ->
                    node.clickScale = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }
    
    private fun animateToCenter() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                offsetX = offsetX * (1f - progress)
                offsetY = offsetY * (1f - progress)
                scale = scale + (1f - scale) * progress
                invalidate()
            }
            start()
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
        entryAnimator.cancel()
    }
    
    /**
     * 赞助者节点
     */
    data class SponsorNode(
        val sponsor: Sponsor,
        var x: Float,
        var y: Float,
        val baseSize: Float,
        val color: Int,
        val tierOrder: Int,
        var avatar: Bitmap? = null,
        var clickScale: Float = 1f
    )
}
