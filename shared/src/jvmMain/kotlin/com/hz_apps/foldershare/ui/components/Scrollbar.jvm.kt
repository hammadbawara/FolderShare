package com.hz_apps.foldershare.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter as composeRememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar as ComposeVerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar as ComposeHorizontalScrollbar
import androidx.compose.foundation.v2.ScrollbarAdapter as ComposeScrollbarAdapter
import androidx.compose.foundation.ScrollbarStyle as ComposeScrollbarStyle
import androidx.compose.foundation.defaultScrollbarStyle as composeDefaultScrollbarStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

actual typealias ScrollbarAdapter = ComposeScrollbarAdapter
actual typealias ScrollbarStyle = ComposeScrollbarStyle

@Composable
actual fun rememberScrollbarAdapter(scrollState: ScrollState): ScrollbarAdapter =
    composeRememberScrollbarAdapter(scrollState)

@Composable
actual fun rememberScrollbarAdapter(scrollState: LazyListState): ScrollbarAdapter =
    composeRememberScrollbarAdapter(scrollState)

@Composable
actual fun rememberScrollbarAdapter(scrollState: LazyGridState): ScrollbarAdapter =
    composeRememberScrollbarAdapter(scrollState)

actual fun defaultScrollbarStyle(
    minimalHeight: Dp,
    thickness: Dp,
    shape: Shape,
    hoverDurationMillis: Int,
    unhoverColor: Color,
    hoverColor: Color
): ScrollbarStyle = ComposeScrollbarStyle(
    minimalHeight = minimalHeight,
    thickness = thickness,
    shape = shape,
    hoverDurationMillis = hoverDurationMillis,
    unhoverColor = unhoverColor,
    hoverColor = hoverColor
)

@Composable
actual fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier,
    reverseLayout: Boolean,
    style: ScrollbarStyle,
    interactionSource: MutableInteractionSource
) {
    ComposeVerticalScrollbar(
        adapter = adapter,
        modifier = modifier,
        reverseLayout = reverseLayout,
        style = style,
        interactionSource = interactionSource
    )
}

@Composable
actual fun HorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier,
    reverseLayout: Boolean,
    style: ScrollbarStyle,
    interactionSource: MutableInteractionSource
) {
    ComposeHorizontalScrollbar(
        adapter = adapter,
        modifier = modifier,
        reverseLayout = reverseLayout,
        style = style,
        interactionSource = interactionSource
    )
}
