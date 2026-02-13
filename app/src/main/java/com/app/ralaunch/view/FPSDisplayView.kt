package com.app.ralaunch.view

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.controls.bridges.SDLInputBridge
import com.app.ralaunch.data.SettingsAccess
import java.io.BufferedReader
import java.io.FileReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * FPS 显示视图
 * 从 SDL 底层获取 FPS，显示 GPU/CPU/RAM 信息
 */
class FPSDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val UPDATE_INTERVAL = 200L  // 更频繁更新 FPS 显示
        private const val DRAG_THRESHOLD = 10f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private var currentFPS = 0f
    private var frameTimeMs = 0f
    private var cpuUsage = -1f   // -1 表示无数据
    private var gpuUsage = -1f   // -1 表示无数据
    private var ramUsage = ""
    
    // CPU 频率估算（兼容 Android 8+，无需 root）
    private val numCpuCores = Runtime.getRuntime().availableProcessors()
    private val cpuMinFreqs = IntArray(numCpuCores)
    private val cpuMaxFreqs = IntArray(numCpuCores)
    private var cpuFreqInited = false
    
    // GPU 频率估算 fallback
    private var gpuFreqPath: String? = null       // 缓存找到的 GPU 频率路径
    private var gpuMinFreq = 0L
    private var gpuMaxFreq = 0L
    private var gpuFreqInited = false
    
    // 直接读取 GPU 利用率的已知可用路径（缓存，避免每次扫描）
    private var knownGpuUtilPath: String? = null
    private var knownGpuUtilParser: ((String) -> Float)? = null
    private var gpuPathScanned = false

    private val handler = Handler(Looper.getMainLooper())
    private var inputBridge: SDLInputBridge? = null
    private val settingsManager = SettingsAccess
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // 拖动相关
    private var lastX = 0f
    private var lastY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialViewX = 0f
    private var initialViewY = 0f
    private var isDragging = false
    private var isTrackingTouch = false

    // 固定位置
    private var fixedX = settingsManager.fpsDisplayX
    private var fixedY = settingsManager.fpsDisplayY

    // 文本边界
    private var textLeft = 0f
    private var textTop = 0f
    private var textRight = 0f
    private var textBottom = 0f

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateData()
            invalidate()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    init {
        visibility = GONE
    }

    fun setInputBridge(bridge: SDLInputBridge?) {
        inputBridge = bridge
    }

    fun start() {
        updateVisibility()
        handler.post(updateRunnable)   
    }

    fun stop() {
        handler.removeCallbacks(updateRunnable)
    }

    fun refreshVisibility() {
        updateVisibility()
        invalidate()
    }

    /** 更新所有数据 */
    private fun updateData() {
        try {
            // 从 SDL 底层读取 FPS（滑动窗口平均）
            Os.getenv("RAL_FPS")?.takeIf { it.isNotEmpty() }?.let {
                currentFPS = it.toFloatOrNull() ?: currentFPS
            }
            // 从 SDL 底层读取帧时间
            Os.getenv("RAL_FRAME_TIME")?.takeIf { it.isNotEmpty() }?.let {
                frameTimeMs = it.toFloatOrNull() ?: frameTimeMs
            }
        } catch (_: Exception) { }
        
        // 更新 CPU/GPU/RAM 使用率
        updateCpuUsage()
        updateGpuUsage()
        updateRamUsage()
        updateVisibility()
    }

    /** 
     * 更新 CPU 使用率（基于核心频率估算，和 Winlator/AndroidCPU 相同方案）
     * 原理：读取每个核心的当前频率，相对于最小/最大频率估算负载
     * 兼容 Android 8+ (Oreo)，无需 root 权限
     */
    private fun updateCpuUsage() {
        try {
            // 初始化每个核心的最小/最大频率
            if (!cpuFreqInited) {
                for (i in 0 until numCpuCores) {
                    cpuMinFreqs[i] = readIntFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
                    cpuMaxFreqs[i] = readIntFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                }
                cpuFreqInited = true
            }
            
            var totalUsage = 0f
            var activeCores = 0
            for (i in 0 until numCpuCores) {
                val curFreq = readIntFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val minFreq = cpuMinFreqs[i]
                val maxFreq = cpuMaxFreqs[i]
                
                // 核心可能离线，min/max 为 0
                if (minFreq == 0 || maxFreq == 0) {
                    cpuMinFreqs[i] = readIntFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
                    cpuMaxFreqs[i] = readIntFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                }
                
                if (maxFreq > minFreq && curFreq > 0) {
                    val coreUsage = ((curFreq - minFreq).toFloat() / (maxFreq - minFreq).toFloat()) * 100f
                    totalUsage += coreUsage.coerceIn(0f, 100f)
                    activeCores++
                }
            }
            
            if (activeCores > 0) {
                cpuUsage = (totalUsage / activeCores).coerceIn(0f, 100f)
            }
        } catch (_: Exception) { }
    }
    
    /** 读取 sysfs 整数文件，失败返回 0 */
    private fun readIntFromFile(path: String): Int {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine()?.trim()?.toIntOrNull() ?: 0 }
        } catch (_: Exception) { 0 }
    }
    
    /** 读取 sysfs 长整数文件，失败返回 0 */
    private fun readLongFromFile(path: String): Long {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine()?.trim()?.toLongOrNull() ?: 0L }
        } catch (_: Exception) { 0L }
    }

    /** 
     * 更新 GPU 使用率
     * 策略1: 直接读取 GPU 利用率文件（部分设备可用）
     * 策略2: 通过 GPU 频率估算负载（和 CPU 频率估算同理，兼容性更好）
     */
    private fun updateGpuUsage() {
        // 策略1: 尝试直接读取 GPU 利用率
        if (tryReadGpuUtilization()) return
        
        // 策略2: GPU 频率估算
        tryGpuFreqEstimation()
    }
    
    /** 尝试直接读取 GPU 利用率文件，成功返回 true */
    private fun tryReadGpuUtilization(): Boolean {
        // 如果之前已经找到可用路径，直接读
        knownGpuUtilPath?.let { path ->
            try {
                BufferedReader(FileReader(path)).use { reader ->
                    val line = reader.readLine()
                    if (line != null) {
                        val result = knownGpuUtilParser?.invoke(line) ?: -1f
                        if (result >= 0f) {
                            gpuUsage = result.coerceIn(0f, 100f)
                            return true
                        }
                    }
                }
            } catch (_: Exception) {
                knownGpuUtilPath = null // 路径失效，下次重新扫描
                knownGpuUtilParser = null
                gpuPathScanned = false
            }
        }
        
        // 只扫描一次
        if (gpuPathScanned) return false
        gpuPathScanned = true
        
        val gpuPaths = listOf(
            // Adreno GPU (Qualcomm)
            "/sys/class/kgsl/kgsl-3d0/gpubusy" to ::parseAdrenoGpuBusy,
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" to ::parsePercentage,
            // Mali GPU (ARM)
            "/sys/kernel/gpu/gpu_busy" to ::parsePercentage,
            "/sys/class/misc/mali0/device/utilization" to ::parsePercentage,
            // 联发科 (MediaTek)
            "/sys/kernel/ged/hal/gpu_utilization" to ::parsePercentage,
            "/sys/module/ged/parameters/gpu_loading" to ::parsePercentage,
            // Samsung Exynos
            "/sys/devices/platform/17500000.g3d/utilization" to ::parsePercentage,
            "/sys/devices/platform/18500000.g3d/utilization" to ::parsePercentage,
        )
        
        for ((path, parser) in gpuPaths) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) {
                    BufferedReader(FileReader(path)).use { reader ->
                        val line = reader.readLine()
                        if (line != null) {
                            val result = parser(line)
                            if (result >= 0f) {
                                knownGpuUtilPath = path
                                knownGpuUtilParser = parser
                                gpuUsage = result.coerceIn(0f, 100f)
                                return true
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return false
    }
    
    /** 通过 GPU 频率估算负载（cur_freq 相对于 min/max 的位置） */
    private fun tryGpuFreqEstimation() {
        try {
            if (!gpuFreqInited) {
                initGpuFreqPaths()
                gpuFreqInited = true
            }
            
            val path = gpuFreqPath ?: return
            val curFreq = readLongFromFile("$path/cur_freq")
            if (curFreq > 0 && gpuMaxFreq > gpuMinFreq) {
                gpuUsage = ((curFreq - gpuMinFreq).toFloat() / (gpuMaxFreq - gpuMinFreq).toFloat() * 100f)
                    .coerceIn(0f, 100f)
            }
        } catch (_: Exception) { }
    }
    
    /** 初始化 GPU 频率路径（自动检测 GPU 类型） */
    private fun initGpuFreqPaths() {
        // Adreno (Qualcomm) - devfreq 路径
        val adrenoPath = "/sys/class/kgsl/kgsl-3d0/devfreq"
        if (java.io.File(adrenoPath, "cur_freq").let { it.exists() && it.canRead() }) {
            gpuFreqPath = adrenoPath
            gpuMinFreq = readLongFromFile("$adrenoPath/min_freq")
            gpuMaxFreq = readLongFromFile("$adrenoPath/max_freq")
            if (gpuMaxFreq > 0) return
        }
        
        // 通用 devfreq: 扫描 /sys/class/devfreq/ 下包含 gpu/mali/kgsl/g3d 的设备
        try {
            val devfreqDir = java.io.File("/sys/class/devfreq")
            if (devfreqDir.exists() && devfreqDir.isDirectory) {
                devfreqDir.listFiles()?.forEach { device ->
                    val name = device.name.lowercase()
                    if (name.contains("gpu") || name.contains("mali") || name.contains("kgsl") || name.contains("g3d")) {
                        val curFile = java.io.File(device, "cur_freq")
                        if (curFile.exists() && curFile.canRead()) {
                            gpuFreqPath = device.absolutePath
                            gpuMinFreq = readLongFromFile("${device.absolutePath}/min_freq")
                            gpuMaxFreq = readLongFromFile("${device.absolutePath}/max_freq")
                            if (gpuMaxFreq > 0) return
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        
        // Adreno 备用路径
        val kgslPath = "/sys/class/kgsl/kgsl-3d0"
        if (java.io.File(kgslPath, "gpuclk").let { it.exists() && it.canRead() }) {
            gpuFreqPath = kgslPath
            gpuMinFreq = readLongFromFile("$kgslPath/min_pwrlevel").let { 
                if (it > 0) readLongFromFile("$kgslPath/gpu_available_frequencies")
                    .toString().split("\\s+".toRegex()).lastOrNull()?.toLongOrNull() ?: 0L
                else 0L
            }
            gpuMaxFreq = readLongFromFile("$kgslPath/max_gpuclk")
        }
    }
    
    private fun parseAdrenoGpuBusy(line: String): Float {
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 2) {
            val busy = parts[0].toLongOrNull() ?: return -1f
            val total = parts[1].toLongOrNull() ?: return -1f
            if (total > 0) return (busy.toFloat() / total.toFloat()) * 100f
        }
        return -1f
    }
    
    private fun parsePercentage(line: String): Float {
        val cleaned = line.trim().replace("%", "").replace("@", " ").split("\\s+".toRegex())[0]
        return cleaned.toFloatOrNull() ?: -1f
    }

    /** 更新 RAM 使用 */
    private fun updateRamUsage() {
        try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val usedMB = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
            val totalMB = memInfo.totalMem / (1024 * 1024)
            ramUsage = "${usedMB}/${totalMB}MB"
        } catch (_: Exception) {
            ramUsage = ""
        }
    }

    private fun updateVisibility() {
        visibility = if (settingsManager.isFPSDisplayEnabled) VISIBLE else GONE
    }

    private fun isTouchInTextArea(touchX: Float, touchY: Float): Boolean {
        if (textRight <= textLeft || textBottom <= textTop) return false
        val padding = 30f
        return touchX >= textLeft - padding && touchX <= textRight + padding &&
               touchY >= textTop - padding && touchY <= textBottom + padding
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchInTextArea(touchX, touchY)) {
                    isTrackingTouch = false
                    return false
                }
                isTrackingTouch = true
                lastX = event.rawX
                lastY = event.rawY
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialViewX = if (fixedX >= 0) fixedX else 100f
                initialViewY = if (fixedY >= 0) fixedY else 100f
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isTrackingTouch) return false
                val deltaX = event.rawX - lastX
                val deltaY = event.rawY - lastY
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

                if (distance > DRAG_THRESHOLD || isDragging) {
                    if (!isDragging) {
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    val margin = 50f
                    fixedX = max(margin, min(initialViewX + (event.rawX - initialTouchX), width - margin))
                    fixedY = max(margin, min(initialViewY + (event.rawY - initialTouchY), height - margin))
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isTrackingTouch) return false
                isTrackingTouch = false
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    settingsManager.fpsDisplayX = fixedX
                    settingsManager.fpsDisplayY = fixedY
                }
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!settingsManager.isFPSDisplayEnabled) return

        // FPS 和帧时间显示
        val fpsText = if (currentFPS > 0) {
            if (frameTimeMs > 0) {
                String.format("%.1f FPS (%.1fms)", currentFPS, frameTimeMs)
            } else {
                String.format("%.1f FPS", currentFPS)
            }
        } else "-- FPS"
        
        // 构建显示信息行
        val lines = mutableListOf(fpsText)
        // CPU 和 GPU 使用率 - 无数据时显示 N/A
        val cpuStr = if (cpuUsage >= 0f) String.format("%.0f%%", cpuUsage) else "N/A"
        val gpuStr = if (gpuUsage >= 0f) String.format("%.0f%%", gpuUsage) else "N/A"
        lines.add("CPU: $cpuStr  GPU: $gpuStr")
        if (ramUsage.isNotEmpty()) lines.add("RAM: $ramUsage")

        val textBounds = Rect()
        textPaint.getTextBounds(fpsText, 0, fpsText.length, textBounds)
        val lineHeight = textBounds.height().toFloat() + 8f
        val padding = 12f

        // 计算最大宽度
        var maxWidth = 0f
        val smallPaint = Paint(textPaint).apply { textSize = textPaint.textSize * 0.85f }
        maxWidth = textPaint.measureText(fpsText)
        for (i in 1 until lines.size) {
            maxWidth = max(maxWidth, smallPaint.measureText(lines[i]))
        }

        val baseX = if (fixedX >= 0) fixedX else 100f
        val baseY = if (fixedY >= 0) fixedY else 100f

        var textX = baseX + maxWidth / 2 + padding
        var textY = baseY + lineHeight

        if (textX - maxWidth / 2 < padding) textX = maxWidth / 2 + padding
        else if (textX + maxWidth / 2 > width - padding) textX = width - maxWidth / 2 - padding

        if (textY < padding + lineHeight) textY = padding + lineHeight
        else if (textY + lineHeight * lines.size > height - padding) 
            textY = height - padding - lineHeight * lines.size

        val bgLeft = textX - maxWidth / 2 - padding
        val bgTop = textY - lineHeight
        val bgRight = textX + maxWidth / 2 + padding
        val bgBottom = textY + lineHeight * (lines.size - 1) + padding

        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, backgroundPaint)

        textLeft = bgLeft; textTop = bgTop; textRight = bgRight; textBottom = bgBottom

        // 绘制 FPS（主文字）
        canvas.drawText(fpsText, textX, textY, textPaint)

        // 绘制其他信息（小号灰色文字）
        smallPaint.color = Color.rgb(200, 200, 200)
        for (i in 1 until lines.size) {
            canvas.drawText(lines[i], textX, textY + lineHeight * i, smallPaint)
        }
    }
}
