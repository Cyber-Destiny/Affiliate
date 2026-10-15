package com.naijasound.keyboard.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naijasound.keyboard.BuildConfig
import com.naijasound.keyboard.R
import com.naijasound.keyboard.ui.LocalAppGraph
import com.naijasound.keyboard.ui.LocalMessages
import com.naijasound.keyboard.ui.components.ScreenHeader

@Composable
fun SettingsScreen(onKeyboardSetup: () -> Unit, onTerms: () -> Unit) {
    val store = LocalAppGraph.current.store
    val context = LocalContext.current
    val showMessage = LocalMessages.current
    val autoPreview by store.autoPreview.collectAsState()
    val appName = stringResource(R.string.app_name)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Settings")
        SettingRow(
            title = "Auto-preview sounds",
            subtitle = "Play a sound as soon as you tap it in the keyboard. Turn off to pick quietly.",
            onClick = { store.setAutoPreview(!autoPreview) },
        ) {
            Switch(checked = autoPreview, onCheckedChange = store::setAutoPreview)
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingRow(
            title = "Keyboard setup",
            subtitle = "Turn on or switch to the $appName keyboard",
            onClick = onKeyboardSetup,
        )
        SettingRow(
            title = "Contact & takedown",
            subtitle = BuildConfig.CONTACT_EMAIL,
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.CONTACT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, "$appName: takedown / contact")
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    showMessage("Email us at ${BuildConfig.CONTACT_EMAIL}")
                }
            },
        )
        SettingRow(
            title = "Terms & privacy",
            subtitle = "How sounds and your data are handled",
            onClick = onTerms,
        )
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}
