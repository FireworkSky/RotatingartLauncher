package com.app.ralaunch.ui.screens.settings
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.activity.MainActivity
import com.app.ralaunch.locales.AppLanguage
import com.app.ralaunch.locales.AppLanguage.Companion.fromDisplayName
import com.app.ralaunch.locales.LocaleManager
import com.app.ralaunch.locales.LocaleManager.strings
import com.app.ralaunch.ui.components.SettingsComponents
import com.app.ralaunch.utils.ConfigManager
import com.materialkolor.hct.Hct
import kotlin.math.roundToInt

@Composable
fun GeneralScreen() {
    Scaffold { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SettingsComponents.DropdownSetting(
                    title = strings.settingsLanguage,
                    icon = Icons.Default.Language,
                    options = listOf(strings.settingsFollowSystem, AppLanguage.ZH.displayName, AppLanguage.EN.displayName),
                    selectedOption = if (LocaleManager.currentLanguage == AppLanguage.SYSTEM) strings.settingsFollowSystem else LocaleManager.currentLanguage.displayName,
                    onOptionSelected = {
                        LocaleManager.setLanguage(fromDisplayName(it))
                    }
                )
            }

            item {
                SettingsComponents.DropdownSetting(
                    title = strings.settingsTheme,
                    icon = Icons.Default.Style,
                    options = listOf(
                        ConfigManager.ThemeMode.SYSTEM.getName(),
                        ConfigManager.ThemeMode.DARK.getName(),
                        ConfigManager.ThemeMode.LIGHT.getName()),
                    selectedOption = MainActivity.getAppTheme().getName(),
                    onOptionSelected = {
                        when (it) {
                            ConfigManager.ThemeMode.SYSTEM.getName() -> MainActivity.setAppTheme(ConfigManager.ThemeMode.SYSTEM)
                            ConfigManager.ThemeMode.DARK.getName() -> MainActivity.setAppTheme(ConfigManager.ThemeMode.DARK)
                            ConfigManager.ThemeMode.LIGHT.getName() -> MainActivity.setAppTheme(ConfigManager.ThemeMode.LIGHT)
                        }
                    }
                )
            }

            item {
                SettingsComponents.SwitchSetting(
                    title = strings.settingsDynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    isChecked = MainActivity.getDynamicColor(),
                    onCheckedChange = { MainActivity.setDynamicColor(it) },
                    icon = Icons.Outlined.Gradient,
                )
            }

            // 主题颜色设置
            item {
                ThemeColorSetting(
                    title = strings.settingsThemeColor,
                    description = strings.settingsThemeColorDescription,
                    currentColor = MainActivity.getThemeSeedColor(),
                    onColorSelected = { MainActivity.setThemeSeedColor(it) },
                    icon = Icons.Outlined.ColorLens,
                    enabled = !MainActivity.getDynamicColor()
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSetting(
    title: String,
    description: String? = null,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true
) {
    val showColorPicker = remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showColorPicker.value = true },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                    description?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 颜色预览
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (enabled) currentColor
                        else currentColor.copy(alpha = 0.38f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }

    // 颜色选择器对话框
    if (showColorPicker.value && enabled) {
        MaterialColorPickerDialog(
            currentColor = currentColor,
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker.value = false
            },
            onDismiss = { showColorPicker.value = false }
        )
    }
}

/**
 * Material Design 颜色选择器对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }
    var activeTab by remember { mutableStateOf(ColorPickerTab.COLOR_PALETTE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.settingsThemeColorDescription,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()) // 添加垂直滚动
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 标签页选择
                        SecondaryTabRow(
                            selectedTabIndex = activeTab.ordinal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ColorPickerTab.entries.forEach { tab ->
                                Tab(
                                    selected = activeTab == tab,
                                    onClick = { activeTab = tab },
                                    text = {
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 内容区域 - 移除固定高度，使用自适应高度
                        when (activeTab) {
                            ColorPickerTab.COLOR_PALETTE -> {
                                MaterialColorPalette(
                                    selectedColor = selectedColor,
                                    onColorSelected = { selectedColor = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp, max = 300.dp) // 使用范围而不是固定高度
                                )
                            }
                            ColorPickerTab.CUSTOM_COLOR -> {
                                CustomColorPicker(
                                    selectedColor = selectedColor,
                                    onColorSelected = { selectedColor = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp, max = 300.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 颜色预览和操作按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 颜色预览和详细信息
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp) // 稍微缩小预览框
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(selectedColor)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    val hct = Hct.fromInt(selectedColor.toArgb())
                                    Text(
                                        text = "HEX: #${selectedColor.toArgb().and(0xFFFFFF).toString(16).padStart(6, '0').uppercase()}",
                                        style = MaterialTheme.typography.bodySmall, // 使用更小的字体
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "HCT: ${hct.hue.roundToInt()}° ${"%.1f".format(hct.chroma)} ${"%.1f".format(hct.tone)}",
                                        style = MaterialTheme.typography.labelSmall, // 使用标签字体
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onColorSelected(selectedColor)
                onDismiss()
            }) {
                Text(strings.confirm)
            }
        }
    )
}

/**
 * Material Design 调色板
 */
@Composable
private fun MaterialColorPalette(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    // Material Design 3 调色板
    val materialColors = listOf(
        // Primary colors
        listOf(Color(0xFF6750A4), Color(0xFF7E57C2), Color(0xFF9575CD)),
        listOf(Color(0xFF2196F3), Color(0xFF42A5F5), Color(0xFF64B5F6)),
        listOf(Color(0xFF4CAF50), Color(0xFF66BB6A), Color(0xFF81C784)),

        // Accent colors
        listOf(Color(0xFFFF9800), Color(0xFFFFA726), Color(0xFFFFB74D)),
        listOf(Color(0xFFF44336), Color(0xFFEF5350), Color(0xFFE57373)),
        listOf(Color(0xFF9C27B0), Color(0xFFAB47BC), Color(0xFFBA68C8)),

        // Neutral colors
        listOf(Color(0xFF607D8B), Color(0xFF78909C), Color(0xFF90A4AE)),
        listOf(Color(0xFF795548), Color(0xFF8D6E63), Color(0xFFA1887F)),
        listOf(Color(0xFF546E7A), Color(0xFF607D8B), Color(0xFF78909C))
    )

    LazyColumn(modifier = modifier) {
        items(materialColors) { colorRow ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colorRow.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .border(
                                width = if (selectedColor == color) 3.dp else 1.dp,
                                color = if (selectedColor == color) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

/**
 * 自定义颜色选择器
 */
@Composable
private fun CustomColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var hue by remember { mutableFloatStateOf(Hct.fromInt(selectedColor.toArgb()).hue.toFloat()) }
    var chroma by remember { mutableFloatStateOf(Hct.fromInt(selectedColor.toArgb()).chroma.toFloat()) }
    var tone by remember { mutableFloatStateOf(Hct.fromInt(selectedColor.toArgb()).tone.toFloat()) }

    Column(modifier = modifier) {
        // 实时预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.hsv(hue, chroma / 150f, tone / 100f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // HCT 滑块控制
        Column {
            Text("H: ${hue.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    updateColorFromHCT(hue, chroma, tone, onColorSelected)
                },
                valueRange = 0f..360f
            )

            Text("C: ${chroma.roundToInt()}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = chroma,
                onValueChange = {
                    chroma = it
                    updateColorFromHCT(hue, chroma, tone, onColorSelected)
                },
                valueRange = 0f..150f
            )

            Text("T: ${tone.roundToInt()}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = tone,
                onValueChange = {
                    tone = it
                    updateColorFromHCT(hue, chroma, tone, onColorSelected)
                },
                valueRange = 0f..100f
            )
        }
    }
}

private fun updateColorFromHCT(hue: Float, chroma: Float, tone: Float, onColorSelected: (Color) -> Unit) {
    val hct = Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble())
    onColorSelected(Color(hct.toInt()))
}


/**
 * 颜色选择器标签页枚举
 */
private enum class ColorPickerTab(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    COLOR_PALETTE(strings.settingsPalette, Icons.Outlined.Palette),
    CUSTOM_COLOR(strings.settingsCustom, Icons.Outlined.Tune),
}