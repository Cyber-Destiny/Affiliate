package com.naijasound.keyboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.ui.Load
import com.naijasound.keyboard.ui.LocalAppGraph
import com.naijasound.keyboard.ui.components.CenteredMessage
import com.naijasound.keyboard.ui.components.ClipRow
import com.naijasound.keyboard.ui.components.LoadingBlock
import com.naijasound.keyboard.ui.components.ScreenHeader
import com.naijasound.keyboard.ui.rememberLoad

@Composable
fun CategoryScreen(name: String, icon: String, onBack: () -> Unit, onOpenClip: (Clip) -> Unit) {
    val repository = LocalAppGraph.current.repository
    val load = rememberLoad("category:$name", initial = repository.cachedCategory(name)) { repository.category(name) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "$icon $name".trim(), onBack = onBack)
        when (val state = load.state) {
            Load.Loading -> LoadingBlock()
            is Load.Failed -> CenteredMessage("📡", state.message, actionLabel = "Try again", onAction = load.retry)
            is Load.Ready -> if (state.value.isEmpty()) {
                CenteredMessage("🫙", "No sounds here yet", body = "Check back soon.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.value, key = { it.id }) { clip -> ClipRow(clip, onOpen = { onOpenClip(clip) }) }
                }
            }
        }
    }
}
