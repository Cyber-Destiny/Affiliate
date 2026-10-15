package com.naijasound.keyboard.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/** Plays a downloaded clip video once; tap the button to play it again. */
@Composable
fun ClipVideo(file: File, onStarted: () -> Unit, modifier: Modifier = Modifier) {
    val latestOnStarted by rememberUpdatedState(onStarted)
    var finished by remember(file) { mutableStateOf(false) }
    val player = remember(file) { VideoPlayerHolder() }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            player.start(
                                file = file,
                                surface = Surface(texture),
                                onPrepared = { latestOnStarted() },
                                onFinished = { finished = true },
                            )
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            player.release()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (finished) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        finished = false
                        player.replay()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play again", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
    }
}

private class VideoPlayerHolder {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null

    fun start(file: File, surface: Surface, onPrepared: () -> Unit, onFinished: () -> Unit) {
        release()
        this.surface = surface
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setSurface(surface)
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnPreparedListener {
                it.start()
                onPrepared()
            }
            mediaPlayer.setOnCompletionListener { onFinished() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                onFinished()
                true
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            release()
            onFinished()
        }
    }

    fun replay() {
        player?.let {
            it.seekTo(0)
            it.start()
        }
    }

    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }
}
