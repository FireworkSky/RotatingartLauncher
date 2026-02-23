package com.app.ralaunch.core.ui.dialog

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.R
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Compose 版错误对话框
 * 横屏极简风格设计
 */
@Composable
fun ErrorDialogCompose(
    title: String,
    message: String,
    details: String? = null,
    isFatal: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var detailsExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isFatal) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isFatal,
            dismissOnClickOutside = !isFatal,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 错误消息
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                    lineHeight = 22.sp
                )

                // 展开/折叠按钮
                if (!details.isNullOrEmpty()) {
                    TextButton(
                        onClick = { detailsExpanded = !detailsExpanded },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = if (detailsExpanded) 
                                stringResource(R.string.error_hide_details) 
                            else 
                                stringResource(R.string.error_show_details),
                            fontSize = 12.sp
                        )
                    }

                    // 详情内容（可折叠）
                    AnimatedVisibility(
                        visible = detailsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.error_details_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 复制按钮
                    if (!details.isNullOrEmpty()) {
                        TextButton(
                            onClick = {
                                copyErrorToClipboard(context, title, message, details)
                            }
                        ) {
                            Text(stringResource(R.string.error_copy))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 确定按钮
                    Button(
                        onClick = {
                            onDismiss()
                            if (isFatal) {
                                (context as? Activity)?.finishAffinity()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

/**
 * 从 Throwable 创建错误对话框
 */
@Composable
fun ErrorDialogCompose(
    title: String,
    throwable: Throwable,
    isFatal: Boolean = false,
    onDismiss: () -> Unit
) {
    val message = throwable.message?.takeIf { it.isNotEmpty() } ?: throwable.javaClass.simpleName
    val details = StringWriter().also { sw -> 
        throwable.printStackTrace(PrintWriter(sw)) 
    }.toString()
    
    ErrorDialogCompose(
        title = title,
        message = message,
        details = details,
        isFatal = isFatal,
        onDismiss = onDismiss
    )
}

private fun copyErrorToClipboard(
    context: Context,
    title: String,
    message: String,
    details: String?
) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        val text = buildString {
            append(title).append("\n\n")
            append(message)
            if (!details.isNullOrEmpty()) {
                append("\n\n").append(context.getString(R.string.error_details_title)).append(":\n")
                append(details)
            }
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.error_details_title), text))
        Toast.makeText(context, context.getString(R.string.error_copy_success), Toast.LENGTH_SHORT).show()
    } catch (_: Exception) { }
}
