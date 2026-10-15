package com.naijasound.keyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.media.MediaKind
import com.naijasound.keyboard.ui.Load
import com.naijasound.keyboard.ui.LocalAppGraph
import com.naijasound.keyboard.ui.LocalMessages
import com.naijasound.keyboard.ui.components.ClipVideo
import com.naijasound.keyboard.ui.components.ScreenHeader
import com.naijasound.keyboard.ui.components.rememberShareAction
import com.naijasound.keyboard.ui.rememberLoad
import com.naijasound.keyboard.ui.theme.CategoryColors
import com.naijasound.keyboard.util.formatCount
import com.naijasound.keyboard.util.friendlyError
import com.naijasound.keyboard.util.sendsLabel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipDetailScreen(clip: Clip, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val showMessage = LocalMessages.current
    val share = rememberShareAction()
    val busy by graph.sender.busy.collectAsState()
    val favourites by graph.store.favourites.collectAsState()
    val isFavourite = favourites.any { it.id == clip.id }
    val scope = rememberCoroutineScope()
    var reporting by remember { mutableStateOf(false) }
    var sendingReport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { graph.previewPlayer.stop() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = clip.category.ifBlank { "Sound" }, onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            VideoBox(clip)
            Spacer(Modifier.height(20.dp))
            Text(clip.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatCount(clip.playCount)} plays · ${sendsLabel(clip.sendCount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (clip.tags.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    clip.tags.forEach { tag ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val sharing = clip.id in busy
                Button(
                    onClick = { share(clip) },
                    enabled = !sharing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    if (sharing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    } else {
                        Icon(Icons.Filled.Share, contentDescription = null)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (sharing) "Getting sound…" else "Share", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalIconButton(
                    onClick = {
                        val saved = graph.toggleFavourite(clip)
                        showMessage(if (saved) "Saved to favourites" else "Removed from favourites")
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavourite) "Remove from favourites" else "Save to favourites",
                        tint = if (isFavourite) Color(0xFFFF4F6D) else LocalContentColor.current,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { reporting = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Report sound")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (reporting) {
        ReportDialog(
            sending = sendingReport,
            onDismiss = { reporting = false },
            onSubmit = { reason, email ->
                sendingReport = true
                scope.launch {
                    try {
                        graph.repository.report(clip, reason, email)
                        reporting = false
                        showMessage("Thanks — we'll review this sound.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showMessage(friendlyError(e))
                    } finally {
                        sendingReport = false
                    }
                }
            },
        )
    }
}

@Composable
private fun VideoBox(clip: Clip) {
    val graph = LocalAppGraph.current
    val video = rememberLoad(clip.mp4Url) { graph.media.get(clip, MediaKind.Video) }
    val background = CategoryColors.background(clip.category)
    val content = CategoryColors.content(background)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = video.state) {
            Load.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    clip.title,
                    color = content,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
                CircularProgressIndicator(color = content)
            }
            is Load.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = content, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                TextButton(
                    onClick = video.retry,
                    colors = ButtonDefaults.textButtonColors(contentColor = content),
                ) { Text("Try again") }
            }
            is Load.Ready -> ClipVideo(
                file = state.value,
                onStarted = { graph.repository.recordPlay(clip) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val reportReasons = listOf(
    "I own this sound (copyright)",
    "Offensive or hateful",
    "Wrong title or category",
    "Doesn't play / bad quality",
    "Something else",
)

@Composable
private fun ReportDialog(sending: Boolean, onDismiss: () -> Unit, onSubmit: (reason: String, email: String?) -> Unit) {
    var reason by remember { mutableStateOf(reportReasons.first()) }
    var details by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this sound") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                reportReasons.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == reason, onClick = { reason = option }, role = Role.RadioButton)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == reason, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(300) },
                    label = { Text("More details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(254) },
                    label = { Text("Your email (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Add your email if you own this sound and want a reply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    val fullReason = listOf(reason, details.trim()).filter { it.isNotEmpty() }.joinToString(": ")
                    onSubmit(fullReason, email.trim().ifEmpty { null })
                },
            ) { Text(if (sending) "Sending…" else "Send report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
