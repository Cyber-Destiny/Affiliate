package com.naijasound.keyboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.ui.LocalAppGraph
import com.naijasound.keyboard.ui.components.CenteredMessage
import com.naijasound.keyboard.ui.components.ClipRow
import com.naijasound.keyboard.ui.components.ScreenHeader

@Composable
fun FavouritesScreen(onOpenClip: (Clip) -> Unit) {
    val favourites by LocalAppGraph.current.store.favourites.collectAsState()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Favourites")
        if (favourites.isEmpty()) {
            CenteredMessage(
                emoji = "❤️",
                title = "No favourites yet",
                body = "Tap ♡ on any sound to keep it here. Favourites also show first in the keyboard and work offline.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(favourites, key = { it.id }) { clip -> ClipRow(clip, onOpen = { onOpenClip(clip) }) }
            }
        }
    }
}
