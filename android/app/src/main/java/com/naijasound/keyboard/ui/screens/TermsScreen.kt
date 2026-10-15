package com.naijasound.keyboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naijasound.keyboard.BuildConfig
import com.naijasound.keyboard.R
import com.naijasound.keyboard.ui.components.ScreenHeader

@Composable
fun TermsScreen(onBack: () -> Unit) {
    val appName = stringResource(R.string.app_name)
    val sections = listOf(
        "The sounds" to
            "$appName offers short clips from Nigerian memes, films and viral moments for fun and commentary. " +
            "Every sound belongs to its owner. If you own a sound and want it removed, tap “Report sound” on it " +
            "or email ${BuildConfig.CONTACT_EMAIL} and we will take it down.",
        "Your privacy" to
            "There are no accounts. Your favourites and recently sent sounds are stored only on your phone. " +
            "The keyboard never reads or stores what you type — it only inserts the sound you pick. " +
            "We count how many times each sound is played and sent, without any account or ID attached. " +
            "If you report a sound we receive your reason, and your email only if you choose to add it.",
        "Using sounds" to
            "Don't use sounds to harass, threaten or mislead people. Sounds may be removed at any time.",
        "No warranty" to
            "$appName is provided as is. Sending a sound uses mobile data the first time it is downloaded.",
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Terms & privacy", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            sections.forEach { (title, body) ->
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
