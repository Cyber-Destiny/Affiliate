package com.naijasound.keyboard.ui.components

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.media.isLoading
import com.naijasound.keyboard.media.isPlaying
import com.naijasound.keyboard.ui.LocalAppGraph
import com.naijasound.keyboard.ui.LocalMessages
import com.naijasound.keyboard.ui.theme.CategoryColors
import com.naijasound.keyboard.util.friendlyError
import com.naijasound.keyboard.util.sendsLabel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Round play/stop button that previews a clip's sound. */
@Composable
fun PlayButton(
    clip: Clip,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    volume: Float = 1f,
) {
    val player = LocalAppGraph.current.previewPlayer
    val preview by player.state.collectAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable { player.toggle(clip, volume) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            preview.isLoading(clip.id) -> CircularProgressIndicator(
                modifier = Modifier.size(size * 0.45f),
                color = content,
                strokeWidth = 2.dp,
            )
            preview.isPlaying(clip.id) -> Box(
                Modifier
                    .size(size * 0.3f)
                    .background(content, RoundedCornerShape(3.dp)),
            )
            else -> Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play ${clip.title}",
                tint = content,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}

/** Square, colourful tile used in the home screen rows. */
@Composable
fun ClipCard(clip: Clip, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val background = CategoryColors.background(clip.category)
    val content = CategoryColors.content(background)
    Box(
        modifier = modifier
            .size(136.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        Text(
            text = clip.title,
            color = content,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, lineHeight = 20.sp),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            text = sendsLabel(clip.sendCount),
            color = content.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
        )
        PlayButton(
            clip = clip,
            background = content.copy(alpha = 0.2f),
            content = content,
            size = 40.dp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** List row: play, title, sends, share. */
@Composable
fun ClipRow(clip: Clip, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val busy by LocalAppGraph.current.sender.busy.collectAsState()
    val share = rememberShareAction()
    val tile = CategoryColors.background(clip.category)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayButton(clip = clip, background = tile, content = CategoryColors.content(tile), size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(clip.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOf(sendsLabel(clip.sendCount), clip.category).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (clip.id in busy) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = { share(clip) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share ${clip.title}")
            }
        }
    }
}

/** Downloads the clip's video and opens Android's share sheet. */
@Composable
fun rememberShareAction(): (Clip) -> Unit {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val showMessage = LocalMessages.current
    val scope = rememberCoroutineScope()
    return remember(graph, context, showMessage, scope) {
        { clip ->
            scope.launch {
                try {
                    val uri = graph.sender.prepareVideo(clip)
                    context.startActivity(graph.sender.shareIntent(clip, uri))
                    graph.sender.markSent(clip)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ActivityNotFoundException) {
                    showMessage("No app on this phone can share videos.")
                } catch (e: Exception) {
                    showMessage(friendlyError(e))
                }
            }
        }
    }
}
