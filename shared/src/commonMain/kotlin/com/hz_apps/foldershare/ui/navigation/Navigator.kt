package com.hz_apps.foldershare.ui.navigation

class Navigator(
    val state: NavState,
) {
    fun goBack() {
        val currentBackstack = state.currentBackstack

        if (state.topLevelRoute == null) {
            // We're using the default stack, remove an entry if possible
            if (currentBackstack.size > 1) {
                currentBackstack.removeLastOrNull()
            }
            return
        }

        if (currentBackstack.size > 1) {
            currentBackstack.removeLastOrNull()
        }
    }

    fun add(route: NavRoute) {
        if (route in state.topLevelBackStacks.keys) {
            activate(route)
        } else {
            state.currentBackstack.add(route)
        }
    }

    fun set(route: NavRoute) {
        state.currentBackstack.clear()
        add(route)
    }

    fun activate(route: NavRoute) {
        if (route == state.topLevelRoute) {
            val currentBackstack = state.currentBackstack
            // Reselected the current top-level route, clear to root
            if (currentBackstack.size > 1) {
                currentBackstack.removeRange(1, currentBackstack.size)
            }
            return
        }
        state.topLevelRoute = route
    }
}
