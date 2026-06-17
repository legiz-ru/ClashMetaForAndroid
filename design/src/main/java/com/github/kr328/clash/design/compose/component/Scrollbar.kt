package com.github.kr328.clash.design.compose.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Thin scrollbar thumb for a [ScrollState]-backed column — a discoverability cue. */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color,
    width: Dp = 4.dp,
): Modifier = drawWithContent {
    drawContent()
    val maxValue = state.maxValue
    if (maxValue <= 0) return@drawWithContent
    val viewportHeight = size.height
    val contentHeight = viewportHeight + maxValue
    val thumbHeight = (viewportHeight / contentHeight) * viewportHeight
    val thumbOffsetY = (state.value.toFloat() / maxValue) * (viewportHeight - thumbHeight)
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - w, thumbOffsetY),
        size = Size(w, thumbHeight),
        cornerRadius = CornerRadius(w / 2, w / 2),
        alpha = 0.5f,
    )
}

/** Thin scrollbar thumb for a [LazyListState]-backed list (approximate). */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    color: Color,
    width: Dp = 4.dp,
): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (total <= 0 || visible <= 0 || visible >= total) return@drawWithContent
    val viewportHeight = size.height
    val w = width.toPx()
    val thumbHeight = (viewportHeight * visible.toFloat() / total).coerceAtLeast(w * 6)
    val denom = (total - visible).coerceAtLeast(1)
    val progress = (state.firstVisibleItemIndex.toFloat() / denom).coerceIn(0f, 1f)
    val thumbOffsetY = progress * (viewportHeight - thumbHeight)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - w, thumbOffsetY),
        size = Size(w, thumbHeight),
        cornerRadius = CornerRadius(w / 2, w / 2),
        alpha = 0.5f,
    )
}
