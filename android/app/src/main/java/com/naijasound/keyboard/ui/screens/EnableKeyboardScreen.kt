package com.naijasound.keyboard.ui.screens

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.naijasound.keyboard.keyboard.KeyboardStatus
import com.naijasound.keyboard.ui.components.ScreenHeader
import kotlinx.coroutines.delay

@Composable
fun EnableKeyboardScreen(firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(KeyboardStatus.isEnabled(context)) }
    var selected by remember { mutableStateOf(KeyboardStatus.isSelected(context)) }
    var tryText by rememberSaveable { mutableStateOf("") }

    // Ticks the steps off by themselves when the user comes back from Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                enabled = KeyboardStatus.isEnabled(context)
                selected = KeyboardStatus.isSelected(context)
                delay(700)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = if (firstRun) "Set up the keyboard" else "Keyboard setup",
            onBack = if (firstRun) null else onDone,
        )
        Text(
            "Naija Sound works as a keyboard, so you can send sounds from inside any chat app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))

        StepCard(
            number = 1,
            title = "Turn on Naija Sound",
            body = "Find “Naija Sound keyboard” in the list and switch it on. Android shows the same warning " +
                "for every keyboard — Naija Sound only sends sounds and never reads what you type.",
            done = enabled,
            actionLabel = "Open keyboard settings",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
        StepCard(
            number = 2,
            title = "Switch to it",
            body = "Pick Naija Sound from the list. In chats, tap the keyboard or 🌐 key on your normal " +
                "keyboard to swap, and the ABC key in Naija Sound to swap back.",
            done = selected,
            actionEnabled = enabled,
            actionLabel = "Choose keyboard",
            onAction = { context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
        )
        StepCard(
            number = 3,
            title = "Try it",
            body = "Tap the box below and search for a vibe.",
            done = false,
        ) {
            OutlinedTextField(
                value = tryText,
                onValueChange = { tryText = it },
                placeholder = { Text("Tap here") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .height(56.dp),
        ) {
            Text(if (firstRun) "Continue" else "Done", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    extra: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(
                        if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(number.toString(), style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (actionLabel != null && !done) {
                    FilledTonalButton(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
                }
                extra?.invoke()
            }
        }
    }
}
