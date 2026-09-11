package com.hz_apps.foldershare.ui.navigation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import com.hz_apps.foldershare.feature.devices.DevicesScreen
import com.hz_apps.foldershare.feature.devices.DevicesViewModel
import com.hz_apps.foldershare.feature.explorer.FileExplorerScreen
import com.hz_apps.foldershare.feature.explorer.FileExplorerViewModel
import com.hz_apps.foldershare.feature.settings.SettingsScreen
import com.hz_apps.foldershare.feature.settings.SettingsViewModel
import com.hz_apps.foldershare.feature.share.ShareScreen
import com.hz_apps.foldershare.feature.share.ShareViewModel
import com.hz_apps.foldershare.getPlatform
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FolderShareAppContent() {
    val isTv = remember { getPlatform().isTv }
    val topLevelRoutes = remember(isTv) {
        if (isTv) {
            setOf(DevicesRoute)
        } else {
            setOf(DevicesRoute, ShareRoute, SettingsRoute)
        }
    }
    val navState = rememberNavState(
        startRoute = DevicesRoute,
        topLevelRoutes = topLevelRoutes
    )
    val navigator = remember(navState) { Navigator(navState) }

    val currentRoute = navState.currentBackstack.lastOrNull()
    val topLevelRoute = navState.topLevelRoute

    val selectedDestination = when (topLevelRoute) {
        is DevicesRoute -> NavDestination.DEVICES
        is ShareRoute -> NavDestination.SHARE
        is SettingsRoute -> NavDestination.SETTINGS
        else -> NavDestination.DEVICES
    }

    val entryProvider = entryProvider {
        entry<DevicesRoute> {
            val viewModel: DevicesViewModel = koinViewModel()
            val navigateToFileExplorer: (RemoteTargetDevice, String?, String?) -> Unit = { targetDevice, username, password ->
                navigator.add(
                    FileExplorerRoute(
                        device = targetDevice,
                        username = username,
                        password = password,
                        path = "/"
                    )
                )
            }
            DevicesScreen(
                viewModel = viewModel,
                onDeviceClick = { device ->
                    viewModel.connectToDevice(
                        device = device,
                        onNavigateToFileExplorer = navigateToFileExplorer
                    )
                },
                onNavigateToFileExplorer = navigateToFileExplorer,
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<ShareRoute> {
            val viewModel: ShareViewModel = koinViewModel()
            ShareScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<SettingsRoute> {
            val viewModel: SettingsViewModel = koinViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<FileExplorerRoute> { route ->
            val viewModel: FileExplorerViewModel = koinViewModel(key = "${route.device.id}:${route.path}")
            LaunchedEffect(route) {
                if (viewModel.uiState.value.device == null) {
                    viewModel.setTargetDevice(
                        device = route.device,
                        username = route.username,
                        password = route.password,
                        initialPath = route.path
                    )
                }
            }
            FileExplorerScreen(
                viewModel = viewModel,
                onBackClick = { navigator.goBack() },
                onNavigateToFolder = { newPath ->
                    navigator.add(
                        FileExplorerRoute(
                            device = route.device,
                            username = route.username,
                            password = route.password,
                            path = newPath
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val defaultTransitionSpec = remember {
        fadeIn(
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.97f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) togetherWith fadeOut(
            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
        )
    }

    val defaultPopTransitionSpec = remember {
        fadeIn(
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) togetherWith (
            fadeOut(
                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
            ) + scaleOut(
                targetScale = 0.97f,
                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
            )
        )
    }

    if (!isTv && currentRoute in topLevelRoutes) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                NavDestination.entries.forEach { destination ->
                    item(
                        selected = selectedDestination == destination,
                        onClick = {
                            val newRoute = when (destination) {
                                NavDestination.DEVICES -> DevicesRoute
                                NavDestination.SHARE -> ShareRoute
                                NavDestination.SETTINGS -> SettingsRoute
                            }
                            navigator.activate(newRoute)
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title
                            )
                        },
                        label = { Text(destination.title) }
                    )
                }
            }
        ) {
            val entries = navState.toDecoratedEntries(entryProvider)
            NavDisplay(
                entries = entries,
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { defaultTransitionSpec },
                popTransitionSpec = { defaultPopTransitionSpec }
            )
        }
    } else {
        val entries = navState.toDecoratedEntries(entryProvider)
        NavDisplay(
            entries = entries,
            onBack = { navigator.goBack() },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { defaultTransitionSpec },
            popTransitionSpec = { defaultPopTransitionSpec }
        )
    }
}
