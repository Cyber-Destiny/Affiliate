package com.naijasound.keyboard.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.data.SearchIdeas
import com.naijasound.keyboard.media.PreviewState
import com.naijasound.keyboard.media.isLoading
import com.naijasound.keyboard.media.isPlaying
import com.naijasound.keyboard.ui.theme.CategoryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** About the height of a normal phone keyboard with its suggestion strip. */
private val KeyboardHeight = 300.dp
private val TopBarHeight = 46.dp

@Composable
fun KeyboardUi(controller: KeyboardController) {
    val preview by controller.player.state.collectAsState()
    LaunchedEffect(preview) {
        if (preview is PreviewState.Failed) controller.showNotice("Couldn't load that sound. Check your connection.")
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(KeyboardHeight),
        ) {
            if (controller.typing) TypingMode(controller, preview) else BrowseMode(controller, preview)
        }
        controller.notice?.let { text ->
            Text(
                text,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

// --- Browsing: tabs and the clip grid ----------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.BrowseMode(c: KeyboardController, preview: PreviewState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = c::switchKeyboard, onLongClick = c::pickKeyboard),
            contentAlignment = Alignment.Center,
        ) {
            Text("ABC", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = c::openSearch)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val applied = c.appliedQuery
            Text(
                applied ?: "Search sounds… “leave me alone”",
                style = MaterialTheme.typography.bodyMedium,
                color = if (applied != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (applied != null) {
                IconKey(Icons.Filled.Close, "Clear search", onClick = c::clearAppliedSearch)
            }
        }
    }

    val applied = c.appliedQuery
    if (applied == null) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(c.tabs, key = { it.key }) { tab ->
                Chip(tab.label, selected = tab == c.tab, onClick = { c.selectTab(tab) })
            }
        }
    }

    val favourites by c.favourites.collectAsState()
    val recent by c.recent.collectAsState()
    val clips = when {
        applied != null -> c.results
        c.tab == KeyboardTab.Favourites -> favourites
        c.tab == KeyboardTab.Recent -> recent
        else -> c.tabClips
    }
    val loading = if (applied != null) c.resultsLoading else c.tabLoading
    val error = if (applied != null) null else c.tabError

    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        when {
            clips.isEmpty() && loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            clips.isEmpty() && error != null -> HintText(error, actionLabel = "Try again", onAction = c::retryTab)
            clips.isEmpty() -> HintText(
                when {
                    applied != null -> "No sounds for “$applied” yet. Try another word."
                    c.tab == KeyboardTab.Favourites -> "Save sounds with ♡ in the app and they'll show here."
                    c.tab == KeyboardTab.Recent -> "Sounds you send will show here."
                    else -> "No sounds here yet."
                },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(clips, key = { it.id }) { clip ->
                    SoundTile(clip, c, preview, Modifier.height(64.dp))
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** One sound. Tap: preview. Tap again (or Send): put it in the chat. */
@Composable
private fun SoundTile(clip: Clip, c: KeyboardController, preview: PreviewState, modifier: Modifier = Modifier) {
    val background = CategoryColors.background(clip.category)
    val content = CategoryColors.content(background)
    val selected = c.selectedId == clip.id
    val sending = c.sendingId == clip.id
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
            .clickable { c.onTileTap(clip) },
    ) {
        Text(
            clip.title,
            color = content,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp),
        )
        if (selected && !sending) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(content)
                    .clickable { c.send(clip) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Send", color = background, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = background, modifier = Modifier.size(11.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(content.copy(alpha = 0.22f))
                .clickable { c.onPlayTap(clip) },
            contentAlignment = Alignment.Center,
        ) {
            when {
                preview.isLoading(clip.id) -> CircularProgressIndicator(Modifier.size(12.dp), color = content, strokeWidth = 1.5.dp)
                preview.isPlaying(clip.id) -> Box(Modifier.size(9.dp).background(content, RoundedCornerShape(2.dp)))
                else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Preview ${clip.title}", tint = content, modifier = Modifier.size(18.dp))
            }
        }
        if (sending) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun HintText(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            Chip(actionLabel, selected = true, onClick = onAction)
        }
    }
}

// --- Typing a search: field, live results and a small letter keyboard ------------

@Composable
private fun ColumnScope.TypingMode(c: KeyboardController, preview: PreviewState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconKey(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = c::closeSearch)
        Spacer(Modifier.width(4.dp))
        Row(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            if (c.query.isEmpty()) {
                BlinkingCursor()
                Text(
                    " Type a vibe…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            } else {
                // Show the end of long searches, where the user is typing.
                Text(
                    c.query.takeLast(28),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                BlinkingCursor()
            }
            Spacer(Modifier.weight(1f))
            if (c.query.isNotEmpty()) IconKey(Icons.Filled.Close, "Clear", onClick = c::clearQuery)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(70.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            c.query.isBlank() -> LazyRow(
                contentPadding = PaddingValues(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(SearchIdeas) { idea -> Chip(idea, selected = false, onClick = { c.updateQuery(idea) }) }
            }
            c.results.isEmpty() && c.resultsLoading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            c.results.isEmpty() -> Text(
                "No sounds for that yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(c.results, key = { it.id }) { clip ->
                    SoundTile(clip, c, preview, Modifier.width(112.dp).fillMaxHeight())
                }
            }
        }
    }

    LetterKeys(
        c,
        Modifier
            .fillMaxWidth()
            .weight(1f),
    )
}

private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

@Composable
private fun LetterKeys(c: KeyboardController, modifier: Modifier) {
    Column(
        modifier.padding(start = 3.dp, end = 3.dp, top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        letterRows.forEachIndexed { index, letters ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (index == 1) Spacer(Modifier.weight(0.5f))
                letters.forEach { letter -> Key(letter.toString(), onPress = { c.type(letter.toString()) }) }
                if (index == 1) Spacer(Modifier.weight(0.5f))
                if (index == 2) Key("⌫", weight = 1.5f, onPress = c::backspace, repeat = true, dark = true)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Key("ABC", weight = 1.5f, onPress = c::switchKeyboard, dark = true, fontSize = 14.sp)
            Key("'", onPress = { c.type("'") })
            Key("space", weight = 4.5f, onPress = { c.type(" ") }, fontSize = 14.sp)
            Key("Search", weight = 2f, onPress = c::submitSearch, accent = true, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    onPress: () -> Unit,
    weight: Float = 1f,
    repeat: Boolean = false,
    dark: Boolean = false,
    accent: Boolean = false,
    fontSize: TextUnit = 19.sp,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    if (repeat) {
        // Hold to keep deleting.
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed) {
            if (pressed) {
                delay(450)
                while (isActive) {
                    onPress()
                    delay(60)
                }
            }
        }
    }
    val background = when {
        accent -> MaterialTheme.colorScheme.primary
        dark -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onPress()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = fontSize,
            color = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (label.length > 1) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun IconKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Box(
        Modifier
            .width(2.dp)
            .height(18.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.primary),
    )
}
