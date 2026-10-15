package com.naijasound.keyboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.naijasound.keyboard.AppGraph
import com.naijasound.keyboard.util.friendlyError
import kotlin.coroutines.cancellation.CancellationException

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }

/** Shows a short message at the bottom of the screen. */
val LocalMessages = staticCompositionLocalOf<(String) -> Unit> { {} }

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class LoadHandle<T>(val state: Load<T>, val retry: () -> Unit)

/**
 * Runs [block] when [key] changes and exposes loading / ready / failed.
 * If [initial] is given (e.g. a cached copy) it shows straight away and stays
 * on screen if the refresh fails.
 */
@Composable
fun <T> rememberLoad(key: Any?, initial: T? = null, block: suspend () -> T): LoadHandle<T> {
    val latestBlock by rememberUpdatedState(block)
    var attempt by remember(key) { mutableIntStateOf(0) }
    var state by remember(key) { mutableStateOf<Load<T>>(if (initial != null) Load.Ready(initial) else Load.Loading) }
    LaunchedEffect(key, attempt) {
        if (state !is Load.Ready) state = Load.Loading
        state = try {
            Load.Ready(latestBlock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.takeIf { it is Load.Ready } ?: Load.Failed(friendlyError(e))
        }
    }
    return LoadHandle(state) { attempt++ }
}
