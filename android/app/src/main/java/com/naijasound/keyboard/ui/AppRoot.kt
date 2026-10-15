package com.naijasound.keyboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.naijasound.keyboard.AppGraph
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.ui.screens.CategoryScreen
import com.naijasound.keyboard.ui.screens.ClipDetailScreen
import com.naijasound.keyboard.ui.screens.EnableKeyboardScreen
import com.naijasound.keyboard.ui.screens.FavouritesScreen
import com.naijasound.keyboard.ui.screens.HomeScreen
import com.naijasound.keyboard.ui.screens.OnboardingScreen
import com.naijasound.keyboard.ui.screens.SearchScreen
import com.naijasound.keyboard.ui.screens.SettingsScreen
import com.naijasound.keyboard.ui.screens.TermsScreen
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Onboarding : Screen
    data class EnableKeyboard(val firstRun: Boolean) : Screen
    data object Home : Screen
    data object Favourites : Screen
    data object Settings : Screen
    data object Terms : Screen
    data object Search : Screen
    data class Category(val name: String, val icon: String) : Screen
    data class Detail(val clip: Clip) : Screen
}

private class Navigator(start: Screen) {
    val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun reset(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Home, "Home", Icons.Filled.Home),
    Tab(Screen.Favourites, "Favourites", Icons.Filled.Favorite),
    Tab(Screen.Settings, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(graph: AppGraph) {
    val navigator = remember { Navigator(if (graph.store.onboardingDone) Screen.Home else Screen.Onboarding) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = remember {
        { text ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }
    val saveableState = rememberSaveableStateHolder()
    val current = navigator.current

    // Back steps out of a screen; from Favourites or Settings it goes Home before leaving the app.
    val backGoesHome = current == Screen.Favourites || current == Screen.Settings
    BackHandler(enabled = navigator.stack.size > 1 || backGoesHome) {
        if (navigator.stack.size > 1) navigator.pop() else navigator.reset(Screen.Home)
    }

    CompositionLocalProvider(LocalAppGraph provides graph, LocalMessages provides showMessage) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (tabs.any { it.screen == current }) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.screen,
                                onClick = { if (current != tab.screen) navigator.reset(tab.screen) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                // Keeps each screen's scroll position when you come back to it.
                saveableState.SaveableStateProvider(navigator.stack.size.toString() + current.toString()) {
                    when (current) {
                        Screen.Onboarding -> OnboardingScreen(
                            onDone = {
                                graph.store.onboardingDone = true
                                navigator.reset(Screen.EnableKeyboard(firstRun = true))
                            },
                        )
                        is Screen.EnableKeyboard -> EnableKeyboardScreen(
                            firstRun = current.firstRun,
                            onDone = { if (current.firstRun) navigator.reset(Screen.Home) else navigator.pop() },
                        )
                        Screen.Home -> HomeScreen(
                            onSearch = { navigator.push(Screen.Search) },
                            onOpenClip = { navigator.push(Screen.Detail(it)) },
                            onOpenCategory = { navigator.push(Screen.Category(it.name, it.icon)) },
                        )
                        Screen.Search -> SearchScreen(
                            onBack = navigator::pop,
                            onOpenClip = { navigator.push(Screen.Detail(it)) },
                        )
                        is Screen.Category -> CategoryScreen(
                            name = current.name,
                            icon = current.icon,
                            onBack = navigator::pop,
                            onOpenClip = { navigator.push(Screen.Detail(it)) },
                        )
                        is Screen.Detail -> ClipDetailScreen(clip = current.clip, onBack = navigator::pop)
                        Screen.Favourites -> FavouritesScreen(onOpenClip = { navigator.push(Screen.Detail(it)) })
                        Screen.Settings -> SettingsScreen(
                            onKeyboardSetup = { navigator.push(Screen.EnableKeyboard(firstRun = false)) },
                            onTerms = { navigator.push(Screen.Terms) },
                        )
                        Screen.Terms -> TermsScreen(onBack = navigator::pop)
                    }
                }
            }
        }
    }
}
