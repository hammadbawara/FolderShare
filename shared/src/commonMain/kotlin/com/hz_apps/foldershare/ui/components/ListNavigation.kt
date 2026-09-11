package com.hz_apps.foldershare.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.hz_apps.foldershare.core.discovery.DeviceCategory
import com.hz_apps.foldershare.getPlatform

/**
 * A modifier that enables explicit keyboard/D-Pad focus navigation.
 * 
 * On Desktop platforms, directional keys (Up/Down) within a scrollable container 
 * default to scrolling the container rather than moving keyboard focus between items.
 * This modifier intercepts directional keys and manually moves the focus, replicating 
 * the native Android TV D-Pad experience on Desktop.
 */
fun Modifier.dpadAndArrowNavigation(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    this.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                else -> false
            }
        } else {
            false
        }
    }
}

/**
 * Standard modifier for TV and Desktop screens, scrollable containers, and lists.
 * 
 * 1. Automatically remembers the last focused child item (button, card, list row)
 *    and restores focus to it when returning from any Dialog, Sheet, or Sub-window.
 * 2. Enables D-Pad / Arrow key navigation.
 */
fun Modifier.tvFocusContainer(
    fallbackFocusRequester: FocusRequester = FocusRequester.Default
): Modifier = this
    .focusRestorer(fallbackFocusRequester)

/**
 * A modifier for Desktop list keyboard navigation (such as navigating file lists with arrow keys).
 * On Android and non-desktop targets, this is a no-op that preserves default Compose 2D focus traversal.
 */
fun Modifier.desktopListKeyNavigation(
    onNavigateUp: () -> Boolean,
    onNavigateDown: () -> Boolean,
    onConfirm: () -> Boolean
): Modifier = composed {
    val isDesktop = remember { getPlatform().category == DeviceCategory.COMPUTER }
    if (!isDesktop) {
        this
    } else {
        this.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.DirectionUp -> onNavigateUp()
                    Key.DirectionDown -> onNavigateDown()
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> onConfirm()
                    else -> false
                }
            } else {
                false
            }
        }
    }
}

/**
 * State holder that retains the identity/key of the currently or last focused item
 * across Navigation 3 destination switches, backstack pops, and configuration changes.
 */
class RetainedFocusState<K : Any>(
    private val savedKey: MutableState<K?>,
    private val requesters: MutableMap<K, FocusRequester>
) {
    val currentFocusedKey: K? get() = savedKey.value

    fun getRequester(key: K): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun onItemFocused(key: K) {
        savedKey.value = key
    }

    fun clearFocusMemory() {
        savedKey.value = null
    }

    /**
     * Restores focus to the retained key (or fallback key / default index) in the given item list.
     * Automatically scrolls the [LazyListState] if needed to ensure the item is composed in viewport.
     */
    suspend fun restoreFocus(
        itemKeys: List<K>,
        listState: LazyListState? = null,
        fallbackKey: K? = null,
        defaultIndex: Int = 0
    ): Boolean {
        if (itemKeys.isEmpty()) return false

        val targetKey = savedKey.value ?: fallbackKey ?: itemKeys.getOrNull(defaultIndex) ?: return false
        val targetIndex = itemKeys.indexOf(targetKey)

        if (targetIndex >= 0) {
            listState?.scrollToItem(targetIndex)
            val requester = getRequester(targetKey)
            return try {
                requester.requestFocus()
                true
            } catch (e: Exception) {
                // Yield one frame for Compose layout pass to attach FocusRequester
                kotlinx.coroutines.delay(50)
                try {
                    requester.requestFocus()
                    true
                } catch (e2: Exception) {
                    false
                }
            }
        }
        return false
    }
}

/**
 * Remember a [RetainedFocusState] for [String] keys that survives Navigation 3 navigation.
 */
@Composable
fun rememberRetainedFocusState(): RetainedFocusState<String> {
    val savedKey = rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    return remember(savedKey) {
        RetainedFocusState(savedKey, requesters)
    }
}

/**
 * Modifier to register an item with a [RetainedFocusState].
 */
fun Modifier.retainedFocusItem(
    key: String,
    retainedFocusState: RetainedFocusState<String>
): Modifier = this
    .focusRequester(retainedFocusState.getRequester(key))
    .onFocusChanged { state ->
        if (state.isFocused) {
            retainedFocusState.onItemFocused(key)
        }
    }


