package com.app.ralaunch.shared.core.component.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 颜色选择器对话框 - Compose Multiplatform 版本（横屏双栏布局，支持透明度）
 */
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hsvColor by remember { mutableStateOf(HsvColor.fromArgb(currentColor)) }
    // 提取当前颜色的 alpha 值
    var alpha by remember { mutableFloatStateOf(((currentColor shr 24) and 0xFF) / 255f) }

    val presetColors = listOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF393E46.toInt(), 0xFF00ADB5.toInt(),
        0xFF355C7D.toInt(), 0xFF8C82FC.toInt(), 0xFF00B8A9.toInt(), 0xFF71C9CE.toInt(),
        0xFFFF2E63.toInt(), 0xFFF6416C.toInt(), 0xFFFC5185.toInt(), 0xFFF38181.toInt(),
        0xFFFF9A00.toInt(), 0xFFFFD460.toInt(), 0xFFAA96DA.toInt(), 0xFFB83B5E.toInt()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 560.dp, max = 640.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                "选择颜色",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val leftColumnWidth = 90.dp
                val rightColumnWidth = 72.dp
                val horizontalGap = 12.dp
                val middleWidth =
                    (maxWidth - leftColumnWidth - rightColumnWidth - horizontalGap * 2).coerceAtLeast(220.dp)
                val squareSize = middleWidth.coerceIn(220.dp, 320.dp)
                val pickerRowHeight = squareSize + 52.dp

                // 三栏横向布局
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pickerRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap)
                ) {
                    // 左侧：颜色预览 + 信息
                    Column(
                        modifier = Modifier
                            .width(leftColumnWidth)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 颜色预览方块（带透明度）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .checkerboardBackground()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(hsvColor.toArgb()).copy(alpha = alpha))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            )
                        }

                        // HEX 值（含透明度）
                        val alphaHex = (alpha * 255).roundToInt().toString(16).uppercase().padStart(2, '0')
                        val colorHex = Integer.toHexString(hsvColor.toArgb() and 0x00FFFFFF).uppercase().padStart(6, '0')
                        Text(
                            text = "#$alphaHex$colorHex",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        // RGBA 值
                        val color = hsvColor.toArgb()
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF
                        val a = (alpha * 255).roundToInt()
                        Text(
                            text = "RGBA($r,$g,$b,$a)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // 中间：饱和度/明度选择器 + 色相条
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 饱和度/明度选择器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                SaturationValueSelector(
                                    hue = hsvColor.hue,
                                    saturation = hsvColor.saturation,
                                    value = hsvColor.value,
                                    onSaturationValueChange = { s, v ->
                                        hsvColor = hsvColor.copy(saturation = s, value = v)
                                    }
                                )
                            }
                        }

                        // 色相选择条（彩虹条）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            HueSelector(
                                hue = hsvColor.hue,
                                onHueChange = { h ->
                                    hsvColor = hsvColor.copy(hue = h)
                                }
                            )
                        }

                        // 透明度选择条
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "透明度",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                AlphaSelector(
                                    hue = hsvColor.hue,
                                    saturation = hsvColor.saturation,
                                    value = hsvColor.value,
                                    alpha = alpha,
                                    onAlphaChange = { a -> alpha = a }
                                )
                            }
                            Text(
                                text = "${(alpha * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.width(32.dp)
                            )
                        }
                    }

                    // 右侧：预设颜色（竖向两列）
                    Column(
                        modifier = Modifier
                            .width(rightColumnWidth)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "预设",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(presetColors) { presetColor ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(presetColor))
                                        .then(
                                            if (presetColor == 0xFFFFFFFF.toInt()) {
                                                Modifier.border(1.dp, Color(0xFF505050), RoundedCornerShape(6.dp))
                                            } else Modifier
                                        )
                                        .clickable {
                                            hsvColor = HsvColor.fromArgb(presetColor)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 返回带有 alpha 的完整颜色值
                    val alphaInt = (alpha * 255).roundToInt().coerceIn(0, 255)
                    val colorWithAlpha = (alphaInt shl 24) or (hsvColor.toArgb() and 0x00FFFFFF)
                    onSelect(colorWithAlpha)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B4BB9))
            ) {
                Text("确定", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}


@Composable
private fun SaturationValueSelector(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChange(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChange(s, v)
                }
            }
    ) {
        // 色相背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.hsv(hue, 1f, 1f))
        )
        // 饱和度渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.White, Color.Transparent)
                    )
                )
        )
        // 明度渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black)
                    )
                )
        )
        // 选择指示器
        if (size.width > 0 && size.height > 0) {
            val selectorSizePx = with(density) { 20.dp.roundToPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (saturation * size.width - selectorSizePx / 2f).roundToInt(),
                            y = ((1f - value) * size.height - selectorSizePx / 2f).roundToInt()
                        )
                    }
                    .size(20.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .padding(2.dp)
                    .background(Color.hsv(hue, saturation, value), CircleShape)
            )
        }
    }
}

private fun Modifier.checkerboardBackground(
    cellSize: Dp = 8.dp,
    lightColor: Color = Color(0xFF666666),
    darkColor: Color = Color(0xFF4A4A4A)
): Modifier = drawBehind {
    val cellPx = cellSize.toPx().coerceAtLeast(1f)
    var y = 0f
    var row = 0

    while (y < size.height) {
        var x = 0f
        var column = 0
        while (x < size.width) {
            val rectColor = if ((row + column) % 2 == 0) lightColor else darkColor
            drawRect(
                color = rectColor,
                topLeft = Offset(x, y),
                size = Size(
                    width = min(cellPx, size.width - x),
                    height = min(cellPx, size.height - y)
                )
            )
            x += cellPx
            column++
        }
        y += cellPx
        row++
    }
}

@Composable
private fun HueSelector(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val hueGradient = Brush.horizontalGradient(
        colors = (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val h = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                    onHueChange(h)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val h = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                    onHueChange(h)
                }
            }
            .background(hueGradient)
    ) {
        // 选择指示器
        if (size.width > 0) {
            val markerWidthPx = with(density) { 6.dp.roundToPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (hue / 360f * size.width - markerWidthPx / 2f).roundToInt(),
                            y = 0
                        )
                    }
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun AlphaSelector(
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val currentColor = Color.hsv(hue, saturation, value)
    val alphaGradient = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, currentColor)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val a = (offset.x / size.width).coerceIn(0f, 1f)
                    onAlphaChange(a)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val a = (change.position.x / size.width).coerceIn(0f, 1f)
                    onAlphaChange(a)
                }
            }
    ) {
        // 棋盘格背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF404040), Color(0xFF606060))
                    )
                )
        )
        // Alpha 渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(alphaGradient)
        )
        // 选择指示器
        if (size.width > 0) {
            val markerWidthPx = with(density) { 6.dp.roundToPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (alpha * size.width - markerWidthPx / 2f).roundToInt(),
                            y = 0
                        )
                    }
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(3.dp))
            )
        }
    }
}

private data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float
) {
    fun toArgb(): Int {
        val c = value * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
        val m = value - c

        val (r1, g1, b1) = when {
            hue < 60 -> Triple(c, x, 0f)
            hue < 120 -> Triple(x, c, 0f)
            hue < 180 -> Triple(0f, c, x)
            hue < 240 -> Triple(0f, x, c)
            hue < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255).roundToInt().coerceIn(0, 255)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        fun fromArgb(argb: Int): HsvColor {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f

            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min

            val h = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6)
                max == g -> 60f * (((b - r) / delta) + 2)
                else -> 60f * (((r - g) / delta) + 4)
            }.let { if (it < 0) it + 360 else it }

            val s = if (max == 0f) 0f else delta / max
            val v = max

            return HsvColor(h, s, v)
        }
    }
}
