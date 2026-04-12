package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.app.ralaunch.R
import kotlin.math.roundToInt

private val PropertyToolbarWidth = 56.dp
private val PropertyToolbarSpacing = 2.dp
private val PropertyPanelOuterPadding = 16.dp

@Composable
internal fun PropertyPanelToolbar(
    panelBounds: Rect,
    rootSize: IntSize,
    alpha: Float = 1f,
    modifier: Modifier = Modifier,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(durationMillis = 180),
        label = "propertyToolbarAlpha"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val toolbarWidthPx = with(density) { PropertyToolbarWidth.roundToPx().toFloat() }
    val toolbarSpacingPx = with(density) { PropertyToolbarSpacing.roundToPx().toFloat() }
    val panelOuterPaddingPx = with(density) { PropertyPanelOuterPadding.roundToPx().toFloat() }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }

    val panelLeft = panelBounds.left + panelOuterPaddingPx
    val panelRight = panelBounds.right - panelOuterPaddingPx
    val panelBottom = panelBounds.bottom - panelOuterPaddingPx

    val leftCandidate = panelLeft - toolbarSpacingPx - toolbarWidthPx
    val rightCandidate = panelRight + toolbarSpacingPx
    val rootWidth = rootSize.width.toFloat()

    val placeOnLeft = when {
        rightCandidate + toolbarWidthPx <= rootWidth -> false
        leftCandidate >= 0f -> true
        else -> {
            val maxX = (rootWidth - toolbarWidthPx).coerceAtLeast(0f)
            val clampedLeft = leftCandidate.coerceIn(0f, maxX)
            val clampedRight = rightCandidate.coerceIn(0f, maxX)
            clampedRight >= clampedLeft
        }
    }

    val toolbarX = if (placeOnLeft) {
        leftCandidate
    } else {
        rightCandidate
    }.let { candidate ->
        val maxX = (rootWidth - toolbarWidthPx).coerceAtLeast(0f)
        candidate.coerceIn(0f, maxX)
    }

    val toolbarY = panelBottom - toolbarSize.height

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(2f)
    ) {
        Surface(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    toolbarSize = coordinates.size
                }
                .offset {
                    IntOffset(
                        x = toolbarX.roundToInt(),
                        y = toolbarY.roundToInt()
                    )
                }
                .width(PropertyToolbarWidth)
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
            tonalElevation = 12.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer { this.alpha = animatedAlpha }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalIconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.editor_copy)
                    )
                }
                FilledTonalIconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.editor_delete_control)
                    )
                }
            }
        }
    }
}
