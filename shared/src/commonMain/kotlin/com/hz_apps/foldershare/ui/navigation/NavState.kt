package com.hz_apps.foldershare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

@Composable
fun rememberNavState(
    startRoute: NavRoute,
    topLevelRoutes: Set<NavRoute>,
): NavState {
    // Map of top-level route to its own backstack
    val topLevelBackStacks: Map<NavRoute, SnapshotStateList<NavRoute>> = remember(topLevelRoutes) {
        topLevelRoutes.associateWith { route ->
            mutableStateListOf(route)
        }
    }

    val defaultBackstack = remember {
        if (startRoute !in topLevelRoutes) {
            mutableStateListOf(startRoute)
        } else {
            mutableStateListOf<NavRoute>()
        }
    }

    val currentBackstack = remember {
        val restoredBackstack = if (startRoute in topLevelRoutes) {
            topLevelBackStacks[startRoute]!!
        } else {
            defaultBackstack
        }
        val list = mutableStateListOf<NavRoute>()
        list.addAll(restoredBackstack)
        list
    }

    return remember(startRoute, topLevelRoutes) {
        NavState(
            topLevelBackStacks = topLevelBackStacks,
            defaultBackstack = defaultBackstack,
            currentBackstack = currentBackstack,
        )
    }
}

class NavState(
    val topLevelBackStacks: Map<NavRoute, SnapshotStateList<NavRoute>>,
    val defaultBackstack: SnapshotStateList<NavRoute>,
    val currentBackstack: SnapshotStateList<NavRoute>,
) {

    var topLevelRoute: NavRoute?
        get() = currentBackstack.firstOrNull()
        set(value) {
            val oldRoute = topLevelRoute

            // Save current backstack to the old route's storage
            val oldStorage = if (oldRoute != null && oldRoute in topLevelBackStacks) {
                topLevelBackStacks[oldRoute]!!
            } else {
                defaultBackstack
            }
            oldStorage.clear()
            oldStorage.addAll(currentBackstack)

            // Load new route's backstack into currentBackstack
            val newStorage = if (value != null && value in topLevelBackStacks) {
                topLevelBackStacks[value]!!
            } else {
                defaultBackstack
            }
            currentBackstack.clear()
            currentBackstack.addAll(newStorage)
        }

    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavRoute) -> NavEntry<NavRoute>
    ): List<NavEntry<NavRoute>> {
        val topLevelEntries = topLevelBackStacks
            .mapValues { (route, stack) ->
                val decorators: List<androidx.navigation3.runtime.NavEntryDecorator<NavRoute>> = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavRoute>(),
                    rememberViewModelStoreNavEntryDecorator<NavRoute>()
                )
                rememberDecoratedNavEntries(
                    backStack = if (route == topLevelRoute) currentBackstack else stack,
                    entryDecorators = decorators,
                    entryProvider = entryProvider
                )
            }
            .withDefault { emptyList() }

        val defaultDecorators: List<androidx.navigation3.runtime.NavEntryDecorator<NavRoute>> = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavRoute>(),
            rememberViewModelStoreNavEntryDecorator<NavRoute>()
        )
        val defaultEntries = rememberDecoratedNavEntries(
            backStack = if (topLevelRoute == null || topLevelRoute !in topLevelBackStacks) currentBackstack else defaultBackstack,
            entryDecorators = defaultDecorators,
            entryProvider = entryProvider,
        )

        val topRoute = topLevelRoute
        return if (topRoute != null && topRoute in topLevelBackStacks) {
            topLevelEntries.getValue(topRoute)
        } else {
            defaultEntries
        }
    }
}
