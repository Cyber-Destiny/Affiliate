package com.naijasound.keyboard.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.naijasound.keyboard.data.Clip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface PreviewState {
    data object Idle : PreviewState
    data class Loading(val clipId: String) : PreviewState
    data class Playing(val clipId: String) : PreviewState
    data class Failed(val clipId: String) : PreviewState
}

fun PreviewState.isPlaying(clipId: String) = this is PreviewState.Playing && this.clipId == clipId
fun PreviewState.isLoading(clipId: String) = this is PreviewState.Loading && this.clipId == clipId

/** Plays one clip's MP3 at a time. Must be used from the main thread. */
class PreviewPlayer(
    private val media: MediaCache,
    private val scope: CoroutineScope,
    private val onStarted: (Clip) -> Unit,
) {
    private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var job: Job? = null

    fun toggle(clip: Clip, volume: Float = 1f) {
        val current = _state.value
        if (current.isPlaying(clip.id) || current.isLoading(clip.id)) stop() else play(clip, volume)
    }

    fun play(clip: Clip, volume: Float = 1f) {
        stop()
        _state.value = PreviewState.Loading(clip.id)
        job = scope.launch {
            val file = try {
                media.get(clip, MediaKind.Audio)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PreviewState.Failed(clip.id)
                return@launch
            }
            val mediaPlayer = MediaPlayer()
            player = mediaPlayer
            try {
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                mediaPlayer.setDataSource(file.absolutePath)
                mediaPlayer.setVolume(volume, volume)
                mediaPlayer.setOnPreparedListener {
                    if (player === it) {
                        it.start()
                        _state.value = PreviewState.Playing(clip.id)
                        onStarted(clip)
                    }
                }
                mediaPlayer.setOnCompletionListener { if (player === it) stop() }
                mediaPlayer.setOnErrorListener { mp, _, _ ->
                    if (player === mp) {
                        release()
                        _state.value = PreviewState.Failed(clip.id)
                    }
                    true
                }
                mediaPlayer.prepareAsync()
            } catch (e: Exception) {
                release()
                _state.value = PreviewState.Failed(clip.id)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        release()
        _state.value = PreviewState.Idle
    }

    private fun release() {
        player?.release()
        player = null
    }
}
