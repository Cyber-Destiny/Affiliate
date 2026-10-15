package com.naijasound.keyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.data.SearchIdeas
import com.naijasound.keyboard.data.SearchResult
import com.naijasound.keyboard.ui.LocalAppGraph
import com.naijasound.keyboard.ui.components.CenteredMessage
import com.naijasound.keyboard.ui.components.ClipRow
import com.naijasound.keyboard.ui.components.LoadingBlock
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenClip: (Clip) -> Unit) {
    val repository = LocalAppGraph.current.repository
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Results update as you type; the short wait avoids a request per letter.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            result = null
            loading = false
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        result = repository.search(query)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text("Type a vibe… “leave me alone”") },
                singleLine = true,
                shape = CircleShape,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }
        if (loading && result != null) LinearProgressIndicator(Modifier.fillMaxWidth())

        val current = result
        when {
            query.isBlank() -> Column(Modifier.padding(20.dp)) {
                Text("Try one of these", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    SearchIdeas.forEach { idea ->
                        SuggestionChip(onClick = { query = idea }, label = { Text(idea) })
                    }
                }
            }
            current == null -> LoadingBlock()
            current.clips.isEmpty() -> CenteredMessage(
                emoji = "🤷🏾",
                title = "No sounds for “$query” yet",
                body = if (current.offline) {
                    "You're offline, so only saved sounds were searched."
                } else {
                    "Try another word or spelling — like “comot” or “abeg”."
                },
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (current.offline) {
                    item(key = "offline") {
                        Text(
                            "Offline — showing matches from sounds already on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                items(current.clips, key = { it.id }) { clip ->
                    ClipRow(clip, onOpen = { onOpenClip(clip) })
                }
            }
        }
    }
}
