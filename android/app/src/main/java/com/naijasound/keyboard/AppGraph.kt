package com.naijasound.keyboard

import android.content.Context
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.data.ClipRepository
import com.naijasound.keyboard.data.LocalStore
import com.naijasound.keyboard.data.SupabaseApi
import com.naijasound.keyboard.media.ClipSender
import com.naijasound.keyboard.media.MediaCache
import com.naijasound.keyboard.media.MediaKind
import com.naijasound.keyboard.media.PreviewPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's shared objects. The main app and the keyboard run in the same process and share these. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val store = LocalStore(context, json)
    val repository = ClipRepository(
        SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, http, json),
        store,
        scope,
    )
    val media = MediaCache(context, http)
    val previewPlayer = PreviewPlayer(media, scope) { clip -> repository.recordPlay(clip) }
    val sender = ClipSender(context, media, repository, store)

    /** Saves or unsaves a clip; saved clips are downloaded so they work offline. */
    fun toggleFavourite(clip: Clip): Boolean {
        val saved = store.toggleFavourite(clip)
        if (saved) {
            scope.launch {
                runCatching { media.get(clip, MediaKind.Audio) }
                runCatching { media.get(clip, MediaKind.Video) }
            }
        }
        return saved
    }
}
