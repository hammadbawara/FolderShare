package com.hz_apps.foldershare.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

actual interface ScrollbarAdapter

private object AndroidDummyScrollbarAdapter : ScrollbarAdapter

actual class ScrollbarStyle

@Composable
actual fun rememberScrollbarAdapter(scrollState: ScrollState): ScrollbarAdapter =
    remember(scrollState) { AndroidDummyScrollbarAdapter }

@Composable
actual fun rememberScrollbarAdapter(scrollState: LazyListState): ScrollbarAdapter =
    remember(scrollState) { AndroidDummyScrollbarAdapter }

@Composable
actual fun rememberScrollbarAdapter(scrollState: LazyGridState): ScrollbarAdapter =
    remember(scrollState) { AndroidDummyScrollbarAdapter }

actual fun defaultScrollbarStyle(
    minimalHeight: Dp,
    thickness: Dp,
    shape: Shape,
    hoverDurationMillis: Int,
    unhoverColor: Color,
    hoverColor: Color
): ScrollbarStyle = ScrollbarStyle()

@Composable
actual fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier,
    reverseLayout: Boolean,
    style: ScrollbarStyle,
    interactionSource: MutableInteractionSource
) {
    // No-op on Android (touchscreens use native Android fling gestures and indicators)
}

@Composable
actual fun HorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier,
    reverseLayout: Boolean,
    style: ScrollbarStyle,
    interactionSource: MutableInteractionSource
) {
    // No-op on Android
}
