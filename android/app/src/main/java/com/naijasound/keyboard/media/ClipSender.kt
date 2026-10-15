package com.naijasound.keyboard.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.data.ClipRepository
import com.naijasound.keyboard.data.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Gets a clip's video ready to hand to another app, and records the send. */
class ClipSender(
    private val context: Context,
    private val media: MediaCache,
    private val repository: ClipRepository,
    private val store: LocalStore,
) {
    private val _busy = MutableStateFlow<Set<String>>(emptySet())

    /** Clips whose video is downloading for a send or share. */
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    /** Downloads the MP4 if needed and returns a content:// link other apps can read. */
    suspend fun prepareVideo(clip: Clip): Uri {
        _busy.update { it + clip.id }
        try {
            val file = media.get(clip, MediaKind.Video)
            return FileProvider.getUriForFile(context, "${context.packageName}.media", file)
        } finally {
            _busy.update { it - clip.id }
        }
    }

    /**
     * A share intent for the video. With [targetPackage] (the chat app the keyboard is in)
     * it goes straight to that app when it can; otherwise Android's share sheet opens.
     */
    fun shareIntent(clip: Clip, uri: Uri, targetPackage: String? = null): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(clip.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!targetPackage.isNullOrBlank() && targetPackage != context.packageName) {
            val direct = Intent(send).setPackage(targetPackage)
            if (direct.resolveActivity(context.packageManager) != null) return direct
        }
        return Intent.createChooser(send, clip.title)
    }

    fun markSent(clip: Clip) {
        store.addRecent(clip)
        repository.recordSend(clip)
    }
}
