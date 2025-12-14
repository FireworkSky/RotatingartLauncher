package com.app.ralaunch.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.app.ralaunch.R
import com.app.ralaunch.locales.LocaleManager.strings

/**
 * “关于”屏幕的 Compose 实现，展示应用信息、特性和致谢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val appVersion = remember(context) { getAppVersion(context) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // 外层 Box 用于整体内边距
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // 使用 Row 实现左右两栏布局
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp) // 卡片间间距
            ) {
                // ========== 左侧：主要内容区域 ==========
                LazyColumn(
                    modifier = Modifier
                        .weight(0.7f) // 占据 70% 宽度
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp) // 内容项之间间距
                ) {

                    // --- 简介部分 ---
                    item {
                        InfoCard(icon = Icons.Default.Info, title = strings.aboutIntroduction) {
                            Text(
                                strings.aboutIntroductionText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // --- 特性部分 ---
                    item {
                        SectionHeader(strings.aboutFeature)
                        FeatureList(
                            modifier = Modifier.fillMaxWidth(),
                            features = listOf(
                            strings.aboutFeatureDotNetRuntime to Icons.Default.Code,
                            strings.aboutFeatureFNAXNA to Icons.Default.Gamepad,
                            strings.aboutFeatureMaterialYou to Icons.Default.Palette,
                            strings.aboutFeatureFullscreen to Icons.Default.Fullscreen
                        ))
                    }

                    // --- 三栏信息部分 ---
                    item {
                        // 将 Row 和其子项（包括 weight modifier）放在这里
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp) // 卡片间水平间距
                        ) {
                            // TripleInfoCard 现在是 Row 的直接子项，可以在其内部使用 Modifier.weight(1f)
                            TripleInfoCard(
                                icon = Icons.Default.SportsEsports,
                                title = strings.aboutSupportedGamesTitle,
                                items = listOf("tModLoader", "Stardew Valley", strings.aboutSupportedGameOtherFna),
                                modifier = Modifier.weight(1f)
                            )
                            TripleInfoCard(
                                icon = Icons.Default.Memory,
                                title = strings.aboutSystemRequirementsTitle,
                                items = listOf("Android 7+", "ARM64", strings.aboutSystemRequirementStorage, "4GB RAM"),
                                modifier = Modifier.weight(1f)
                            )
                            TripleInfoCard(
                                icon = Icons.Default.Build,
                                title = strings.aboutTechStackTitle,
                                items = listOf("Java 17", "SDL2 + GL4ES", "CoreCLR", "FAudio", "Compose"),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // --- 已知问题部分 ---
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp) // 卡片间水平间距
                        ) {
                            InfoCard(modifier = Modifier.weight(1f).fillMaxWidth(), icon = Icons.Default.BugReport, title = strings.aboutKnownIssuesTitle) {
                                Text(
                                    strings.aboutKnownIssueContext,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            InfoCard(modifier = Modifier.weight(1f).fillMaxWidth(), icon = Icons.Default.Add, title = strings.aboutContributeTitle) {
                                Text(
                                    strings.aboutContributeContext,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // --- 更新日志部分 ---
                    item {
                        InfoCard(modifier = Modifier.fillMaxWidth(), icon = Icons.Default.History, title = strings.aboutChangelogTitle.format(strings.appVersion, strings.appRelease)) {
                            Text(
                                strings.aboutChangelogContext,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }


                    // --- 许可证部分 ---
                    item {
                        InfoCard(modifier = Modifier.fillMaxWidth(), icon = Icons.Default.Description, title = strings.aboutLicenseTitle) {
                            Text(
                                strings.aboutLicenseContext,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // --- 致谢部分 ---
                    item {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), // 卡片间水平间距
                        ) {

                            InfoCard(modifier = Modifier.weight(1f).fillMaxWidth().padding(4.dp), icon = Icons.Default.Favorite, title = strings.aboutCreditsThanksTitle) {
                                Text(
                                    strings.aboutCreditsThanksContext,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            InfoCard(modifier = Modifier.weight(1f).fillMaxWidth().padding(4.dp), icon = Icons.Default.People, title = strings.aboutAuthorsTitle) {
                                Text(
                                    buildAnnotatedString {
                                        appendLine("FireworkSky")
                                        appendLine("LaoSparrow")
                                        appendLine("eternalfuture-e38299")
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            InfoCard(modifier = Modifier.weight(1f).fillMaxWidth().padding(4.dp), icon = Icons.Default.Email, title = strings.aboutContactTitle) {
                                Text(
                                    strings.aboutContactInstructions,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        openUrl(context, "https://github.com/FireworkSky/Rotating-art-Launcher/issues")
                                    }) {
                                        Text(strings.aboutContactIssueButton)
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ========== 右侧：应用信息卡片 ==========
                ElevatedCard(
                    modifier = Modifier
                        .weight(0.3f) // 占据 30% 宽度
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Logo
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground), // 确保使用正确的 logo 资源
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // App 名称
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 版本号
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                                    append("${strings.appVersion} - ${strings.appRelease}")
                                }
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 技术标签
                        Text(
                            text = ".NET · SDL2 · Android",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // GitHub 按钮
                        OutlinedButton(
                            onClick = {
                                openUrl(context, "https://github.com/FireworkSky/Rotating-art-Launcher")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("GitHub")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew, // 使用 AutoMirrored 以适配 RTL
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 作者信息
                        Text(
                            text = "Made with ❤️ by FireworkSky & LaoSparrow & eternalfuture-e38299",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- UI 组件 ---

/**
 * 通用信息卡片
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // 如果没有标题，则图标放在内容上方居中或靠左
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start).size(24.dp) // 或 Align.CenterHorizontally
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}


/**
 * 特性列表项
 */
@Composable
fun FeatureItem(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, // 使用 primary 色更突出
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 特性列表容器
 */
@Composable
fun FeatureList(features: List<Pair<String, ImageVector>>, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            features.forEach { (text, icon) ->
                FeatureItem(icon, text)
            }
        }
    }
}

/**
 * 三栏信息卡片
 */
@Composable
fun TripleInfoCard(icon: ImageVector, title: String, items: List<String>, modifier: Modifier) {
    // 注意：这里的 Modifier.weight(1f) 现在是在有效的 RowScope/ColumnScope 内调用的
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier // 正确地在父 Row 的 Scope 内使用 weight
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            items.forEach { item ->
                Text(
                    "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 章节标题
 */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp) // 标题下方间距
    )
}


// --- 工具函数 ---

/**
 * 获取应用版本名
 */
private fun getAppVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }
}

/**
 * 打开网页链接
 */
@SuppressLint("UseKtx")
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(context, intent, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}