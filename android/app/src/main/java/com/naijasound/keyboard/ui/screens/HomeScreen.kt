package com.naijasound.keyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naijasound.keyboard.R
import com.naijasound.keyboard.data.Category
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.ui.Load
import com.naijasound.keyboard.ui.LocalAppGraph
import com.naijasound.keyboard.ui.rememberLoad
import com.naijasound.keyboard.ui.components.ClipCard
import com.naijasound.keyboard.ui.components.NotConfiguredCard
import com.naijasound.keyboard.ui.components.SearchBarButton
import com.naijasound.keyboard.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onOpenClip: (Clip) -> Unit,
    onOpenCategory: (Category) -> Unit,
) {
    val repository = LocalAppGraph.current.repository
    val configured = repository.isConfigured

    val trending = rememberLoad("trending", initial = repository.cachedTrending().ifEmpty { null }) { repository.trending() }
    val latest = rememberLoad("latest", initial = repository.cachedLatest()) { repository.latest() }
    val categories = rememberLoad("categories", initial = repository.cachedCategories()) { repository.categories() }
    val categoryList = (categories.state as? Load.Ready)?.value ?: repository.cachedCategories()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "The perfect Naija sound for every vibe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SearchBarButton(onClick = onSearch)
            }
        }

        if (!configured) {
            item(key = "setup") { NotConfiguredCard(Modifier.padding(20.dp)) }
            return@LazyColumn
        }

        item(key = "trending") {
            ClipShelf("🔥 Trending", trending.state, trending.retry, onOpenClip)
        }
        item(key = "latest") {
            val title = when (val state = latest.state) {
                is Load.Ready -> if (state.value.thisWeek) "✨ New this week" else "✨ Latest"
                else -> "✨ New this week"
            }
            val clips = when (val state = latest.state) {
                is Load.Ready -> Load.Ready(state.value.clips)
                is Load.Failed -> state
                Load.Loading -> Load.Loading
            }
            ClipShelf(title, clips, latest.retry, onOpenClip)
        }
        items(categoryList, key = { "category-${it.name}" }) { category ->
            CategoryShelf(category, onOpenClip, onSeeAll = { onOpenCategory(category) })
        }
    }
}

@Composable
private fun CategoryShelf(category: Category, onOpenClip: (Clip) -> Unit, onSeeAll: () -> Unit) {
    val repository = LocalAppGraph.current.repository
    val load = rememberLoad("category:${category.name}", initial = repository.cachedCategory(category.name)) {
        repository.category(category.name)
    }
    val state = load.state
    if (state is Load.Ready && state.value.isEmpty()) return
    ClipShelf("${category.icon} ${category.name}".trim(), state, load.retry, onOpenClip, onSeeAll)
}

@Composable
private fun ClipShelf(
    title: String,
    state: Load<List<Clip>>,
    onRetry: () -> Unit,
    onOpenClip: (Clip) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        SectionHeader(title, onSeeAll)
        when (state) {
            Load.Loading -> Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(136.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                    )
                }
            }
            is Load.Failed -> Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            is Load.Ready -> if (state.value.isEmpty()) {
                Text(
                    "No sounds here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.value, key = { it.id }) { clip ->
                        ClipCard(clip, onOpen = { onOpenClip(clip) })
                    }
                }
            }
        }
    }
}
