package com.hz_apps.foldershare.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class SubMenuInfo(
    val anchorBounds: IntRect,
    val content: @Composable CascadingMenuScope.() -> Unit
)

@Stable
class CascadingMenuState(
    val onDismissRequest: () -> Unit
) {
    private val _activePath = mutableStateListOf<Any>()
    val subMenus = mutableStateMapOf<Int, SubMenuInfo>()
    val activeParentFocusRequesters = mutableMapOf<Int, FocusRequester>()

    fun isSubMenuOpen(depth: Int, key: Any): Boolean {
        return _activePath.getOrNull(depth) == key
    }

    fun enterItem(
        depth: Int,
        key: Any,
        anchorBounds: IntRect,
        parentFocusRequester: FocusRequester,
        content: @Composable CascadingMenuScope.() -> Unit
    ) {
        while (_activePath.size > depth) {
            val removeDepth = _activePath.lastIndex
            _activePath.removeAt(removeDepth)
            subMenus.remove(removeDepth + 1)
            activeParentFocusRequesters.remove(removeDepth)
        }
        _activePath.add(key)
        subMenus[depth + 1] = SubMenuInfo(anchorBounds, content)
        activeParentFocusRequesters[depth] = parentFocusRequester
    }

    fun closeSubMenuAtOrBelow(depth: Int) {
        val focusToRestore = activeParentFocusRequesters[depth - 1]
        while (_activePath.size > depth) {
            val removeDepth = _activePath.lastIndex
            _activePath.removeAt(removeDepth)
            subMenus.remove(removeDepth + 1)
            activeParentFocusRequesters.remove(removeDepth)
        }
        try {
            focusToRestore?.requestFocus()
        } catch (e: Exception) {}
    }

    fun dismissAll() {
        _activePath.clear()
        subMenus.clear()
        activeParentFocusRequesters.clear()
        onDismissRequest()
    }
}

val LocalCascadingMenuState = compositionLocalOf<CascadingMenuState> {
    error("No CascadingMenuState provided")
}

@Stable
interface CascadingMenuScope {
    val depth: Int

    @Composable
    fun MenuItem(
        text: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true,
        colors: MenuItemColors = MenuDefaults.itemColors(),
        contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
        subMenus: (@Composable CascadingMenuScope.() -> Unit)? = null
    )

    @Composable
    fun MenuDivider(modifier: Modifier = Modifier)
}

internal class CascadingMenuScopeImpl(
    override val depth: Int,
    private val menuState: CascadingMenuState
) : CascadingMenuScope {

    @Composable
    override fun MenuItem(
        text: @Composable () -> Unit,
        modifier: Modifier,
        onClick: (() -> Unit)?,
        leadingIcon: @Composable (() -> Unit)?,
        trailingIcon: @Composable (() -> Unit)?,
        enabled: Boolean,
        colors: MenuItemColors,
        contentPadding: PaddingValues,
        subMenus: (@Composable CascadingMenuScope.() -> Unit)?
    ) {
        val key = remember { Any() }
        val isSubMenuOpen = menuState.isSubMenuOpen(depth, key)
        val hasSubMenu = subMenus != null

        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

        var itemBounds by remember { mutableStateOf(IntRect.Zero) }
        val focusRequester = remember { FocusRequester() }

        val pointerModifier = Modifier.pointerInput(menuState, depth, enabled) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Enter && enabled) {
                        if (event.changes.any { it.type == PointerType.Mouse }) {
                            if (subMenus != null) {
                                menuState.enterItem(depth, key, itemBounds, focusRequester, subMenus)
                            } else {
                                menuState.closeSubMenuAtOrBelow(depth)
                                try { focusRequester.requestFocus() } catch (e: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        val isSelected = isHovered || isSubMenuOpen

        val itemModifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val windowRect = coords.positionInWindow()
                itemBounds = IntRect(
                    left = windowRect.x.roundToInt(),
                    top = windowRect.y.roundToInt(),
                    right = windowRect.x.roundToInt() + coords.size.width,
                    bottom = windowRect.y.roundToInt() + coords.size.height
                )
            }
            .focusRequester(focusRequester)
            .then(pointerModifier)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight, Key.Enter, Key.Spacebar -> {
                            if (enabled) {
                                if (subMenus != null && !isSubMenuOpen) {
                                    menuState.enterItem(depth, key, itemBounds, focusRequester, subMenus)
                                    true
                                } else if (subMenus == null && (event.key == Key.Enter || event.key == Key.Spacebar)) {
                                    onClick?.invoke()
                                    menuState.dismissAll()
                                    true
                                } else false
                            } else false
                        }
                        Key.DirectionLeft -> {
                            if (depth > 0) {
                                menuState.closeSubMenuAtOrBelow(depth)
                                true
                            } else false
                        }
                        Key.Escape -> {
                            if (depth > 0) {
                                menuState.closeSubMenuAtOrBelow(depth)
                                true
                            } else {
                                menuState.dismissAll()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .background(
                if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                else Color.Transparent
            )

        Box(modifier = Modifier.fillMaxWidth()) {
            DropdownMenuItem(
                text = text,
                onClick = {
                    if (enabled) {
                        if (subMenus != null) {
                            if (isSubMenuOpen) {
                                menuState.closeSubMenuAtOrBelow(depth)
                            } else {
                                menuState.enterItem(depth, key, itemBounds, focusRequester, subMenus)
                            }
                        } else {
                            onClick?.invoke()
                            menuState.dismissAll()
                        }
                    }
                },
                modifier = itemModifier,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon ?: if (hasSubMenu) {
                    { Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Submenu") }
                } else null,
                enabled = enabled,
                colors = colors,
                contentPadding = contentPadding,
                interactionSource = interactionSource
            )
        }
    }

    @Composable
    override fun MenuDivider(modifier: Modifier) {
        HorizontalDivider(modifier = modifier)
    }
}

@Composable
private fun MenuSurface(
    depth: Int,
    menuState: CascadingMenuState,
    modifier: Modifier = Modifier,
    content: @Composable CascadingMenuScope.() -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }

    val focusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visibleState = transitionState,
        enter = fadeIn() + scaleIn(initialScale = 0.95f, transformOrigin = TransformOrigin(0f, 0f)),
        modifier = modifier
    ) {
        Surface(
            shape = MenuDefaults.shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { /* consume taps on the surface background to prevent dismiss */ }
            }
        ) {
            val childScope = remember(depth, menuState) {
                CascadingMenuScopeImpl(depth = depth, menuState = menuState)
            }
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(IntrinsicSize.Max)
                    .focusRequester(focusRequester)
                    .focusGroup()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            if (depth > 0) {
                                menuState.closeSubMenuAtOrBelow(depth)
                            } else {
                                menuState.dismissAll()
                            }
                            true
                        } else false
                    }
            ) {
                LaunchedEffect(Unit) {
                    try {
                        kotlinx.coroutines.delay(10.milliseconds)
                        focusRequester.requestFocus()
                    } catch (e: Exception) {}
                }
                content(childScope)
            }
        }
    }
}

@Composable
private fun MenuOverlayLayout(
    rootOffset: IntOffset,
    rootAnchorBounds: IntRect,
    windowSize: IntSize,
    menuState: CascadingMenuState,
    rootModifier: Modifier = Modifier,
    rootContent: @Composable CascadingMenuScope.() -> Unit
) {
    Layout(
        content = {
            MenuSurface(depth = 0, menuState = menuState, modifier = rootModifier, content = rootContent)
            
            val sortedDepths = menuState.subMenus.keys.sorted()
            sortedDepths.forEach { depth ->
                val info = menuState.subMenus[depth]!!
                MenuSurface(depth = depth, menuState = menuState, content = info.content)
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(looseConstraints) }
        
        layout(windowSize.width, windowSize.height) {
            if (placeables.isEmpty()) return@layout
            
            val rootPlaceable = placeables[0]
            var rootX = rootOffset.x
            var rootY = rootOffset.y
            
            // Clamp root menu
            if (rootX + rootPlaceable.width > windowSize.width) {
                rootX = (windowSize.width - rootPlaceable.width).coerceAtLeast(0)
            }
            if (rootY + rootPlaceable.height > windowSize.height) {
                val flippedY = rootAnchorBounds.top - rootPlaceable.height
                rootY = if (flippedY >= 0) {
                    flippedY
                } else {
                    (windowSize.height - rootPlaceable.height).coerceAtLeast(0)
                }
            }
            
            rootPlaceable.place(rootX, rootY)
            
            val sortedDepths = menuState.subMenus.keys.sorted()
            for (i in sortedDepths.indices) {
                val placeableIndex = i + 1
                if (placeableIndex < placeables.size) {
                    val placeable = placeables[placeableIndex]
                    val depth = sortedDepths[i]
                    val parentBounds = menuState.subMenus[depth]!!.anchorBounds
                    
                    var x = parentBounds.right
                    if (x + placeable.width > windowSize.width) {
                        x = parentBounds.left - placeable.width
                        if (x < 0) x = (windowSize.width - placeable.width).coerceAtLeast(0)
                    }
                    
                    var y = parentBounds.top
                    if (y + placeable.height > windowSize.height) {
                        y = (windowSize.height - placeable.height).coerceAtLeast(0)
                    }
                    
                    placeable.place(x, y)
                }
            }
        }
    }
}

@Composable
fun CascadingDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable CascadingMenuScope.() -> Unit
) {
    if (!expanded) return

    val menuState = remember(onDismissRequest) {
        CascadingMenuState(onDismissRequest = onDismissRequest)
    }

    val density = LocalDensity.current
    var rootOffset by remember { mutableStateOf(IntOffset.Zero) }
    var rootAnchorBounds by remember { mutableStateOf(IntRect.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }

    val positionProvider = remember(offset, density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = anchorBounds.left + with(density) { offset.x.roundToPx() }
                val y = anchorBounds.bottom + with(density) { offset.y.roundToPx() }
                rootOffset = IntOffset(x, y)
                rootAnchorBounds = anchorBounds
                popupSize = windowSize
                return IntOffset.Zero
            }
        }
    }

    CompositionLocalProvider(LocalCascadingMenuState provides menuState) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = { menuState.dismissAll() },
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier
                    .size(
                        width = with(density) { popupSize.width.toDp() },
                        height = with(density) { popupSize.height.toDp() }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { menuState.dismissAll() })
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            menuState.dismissAll()
                            true
                        } else false
                    }
            ) {
                MenuOverlayLayout(
                    rootOffset = rootOffset,
                    rootAnchorBounds = rootAnchorBounds,
                    windowSize = popupSize,
                    menuState = menuState,
                    rootModifier = modifier,
                    rootContent = content
                )
            }
        }
    }
}

@Preview
@Composable
fun CascadingDropdownMenuPreview() {
    MaterialTheme {
        Surface {
            CascadingDropdownMenu(
                expanded = true,
                onDismissRequest = {}
            ) {
                MenuItem(
                    text = { Text("New File") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                    onClick = {}
                )
                MenuItem(
                    text = { Text("New Folder") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = {}
                )

                MenuDivider()

                MenuItem(
                    text = { Text("Sort By") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                    subMenus = {
                        MenuItem(
                            text = { Text("Name") },
                            trailingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = {}
                        )
                        MenuItem(
                            text = { Text("Date Modified") },
                            onClick = {}
                        )
                        MenuItem(
                            text = { Text("Size") },
                            onClick = {}
                        )

                        MenuDivider()

                        MenuItem(
                            text = { Text("Order") },
                            subMenus = {
                                MenuItem(
                                    text = { Text("Ascending") },
                                    trailingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {}
                                )
                                MenuItem(
                                    text = { Text("Descending") },
                                    onClick = {}
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}
