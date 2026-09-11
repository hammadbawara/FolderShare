package com.hz_apps.foldershare.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Multiplatform abstraction for scrollbar adapter.
 */
expect interface ScrollbarAdapter

@Composable
expect fun rememberScrollbarAdapter(scrollState: ScrollState): ScrollbarAdapter

@Composable
expect fun rememberScrollbarAdapter(scrollState: LazyListState): ScrollbarAdapter

@Composable
expect fun rememberScrollbarAdapter(scrollState: LazyGridState): ScrollbarAdapter

/**
 * Multiplatform abstraction for scrollbar visual styling.
 */
expect class ScrollbarStyle

expect fun defaultScrollbarStyle(
    minimalHeight: Dp = 16.dp,
    thickness: Dp = 8.dp,
    shape: Shape = RoundedCornerShape(4.dp),
    hoverDurationMillis: Int = 300,
    unhoverColor: Color = Color.Black.copy(alpha = 0.12f),
    hoverColor: Color = Color.Black.copy(alpha = 0.50f)
): ScrollbarStyle

/**
 * Default scrollbar style adhering to Material 3 dynamic color theme tokens.
 */
@Composable
fun defaultMaterialScrollbarStyle(
    thickness: Dp = 8.dp,
    shape: Shape = RoundedCornerShape(4.dp),
    minimalHeight: Dp = 16.dp,
    hoverDurationMillis: Int = 300,
    unhoverColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    hoverColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
): ScrollbarStyle = defaultScrollbarStyle(
    minimalHeight = minimalHeight,
    thickness = thickness,
    shape = shape,
    hoverDurationMillis = hoverDurationMillis,
    unhoverColor = unhoverColor,
    hoverColor = hoverColor
)

/**
 * Multiplatform Vertical Scrollbar component.
 */
@Composable
expect fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    style: ScrollbarStyle = defaultMaterialScrollbarStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
)

/**
 * Multiplatform Horizontal Scrollbar component.
 */
@Composable
expect fun HorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    style: ScrollbarStyle = defaultMaterialScrollbarStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
)

/**
 * Reusable scrollable column with an integrated VerticalScrollbar for dialogs and bounded containers.
 */
@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollbarPaddingEnd: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .verticalScroll(state)
                .padding(end = if (state.maxValue > 0) 8.dp else 0.dp),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
        if (state.maxValue > 0) {
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .matchParentSize()
                    .padding(end = scrollbarPaddingEnd),
                adapter = rememberScrollbarAdapter(state)
            )
        }
    }
}
