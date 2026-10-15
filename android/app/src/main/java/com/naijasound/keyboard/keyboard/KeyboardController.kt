package com.naijasound.keyboard.keyboard

import android.content.ActivityNotFoundException
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.naijasound.keyboard.AppGraph
import com.naijasound.keyboard.data.Category
import com.naijasound.keyboard.data.Clip
import com.naijasound.keyboard.util.friendlyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface KeyboardTab {
    val key: String
    val label: String

    data object Recent : KeyboardTab {
        override val key = "recent"
        override val label = "🕘 Recent"
    }

    data object Favourites : KeyboardTab {
        override val key = "favourites"
        override val label = "❤️ Favourites"
    }

    data object Trending : KeyboardTab {
        override val key = "trending"
        override val label = "🔥 Trending"
    }

    data class Mood(val category: Category) : KeyboardTab {
        override val key = "mood:${category.name}"
        override val label = "${category.icon} ${category.name}".trim()
    }
}

/** Everything the keyboard shows and does. Lives as long as the keyboard service. */
class KeyboardController(
    private val service: SoundKeyboardService,
    private val graph: AppGraph,
    private val scope: CoroutineScope,
) {
    private val repository = graph.repository
    private val store = graph.store
    val player = graph.previewPlayer
    val favourites = store.favourites
    val recent = store.recent

    /** True while the mini letter keyboard is up for typing a search. */
    var typing by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set

    /** A finished search shown in the big grid instead of a tab. */
    var appliedQuery by mutableStateOf<String?>(null)
        private set

    var categories by mutableStateOf(repository.cachedCategories())
        private set
    var tab by mutableStateOf<KeyboardTab>(KeyboardTab.Trending)
        private set
    var tabClips by mutableStateOf<List<Clip>>(emptyList())
        private set
    var tabLoading by mutableStateOf(false)
        private set
    var tabError by mutableStateOf<String?>(null)
        private set

    var results by mutableStateOf<List<Clip>>(emptyList())
        private set
    var resultsLoading by mutableStateOf(false)
        private set

    var selectedId by mutableStateOf<String?>(null)
        private set
    var sendingId by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    val tabs: List<KeyboardTab>
        get() = listOf(KeyboardTab.Recent, KeyboardTab.Favourites, KeyboardTab.Trending) +
            categories.map { KeyboardTab.Mood(it) }

    private var tabJob: Job? = null
    private var searchJob: Job? = null
    private var noticeJob: Job? = null

    fun onShown() {
        typing = false
        query = ""
        appliedQuery = null
        results = emptyList()
        selectedId = null
        // Favourites first, then recents, then what's trending.
        selectTab(
            when {
                favourites.value.isNotEmpty() -> KeyboardTab.Favourites
                recent.value.isNotEmpty() -> KeyboardTab.Recent
                else -> KeyboardTab.Trending
            },
        )
        scope.launch { categories = repository.categories() }
        scope.launch { repository.syncSaved() }
    }

    fun onHidden() {
        player.stop()
        searchJob?.cancel()
        selectedId = null
    }

    fun selectTab(newTab: KeyboardTab) {
        tab = newTab
        selectedId = null
        tabError = null
        tabJob?.cancel()
        when (newTab) {
            KeyboardTab.Recent, KeyboardTab.Favourites -> {
                tabClips = emptyList()
                tabLoading = false
            }
            KeyboardTab.Trending -> loadTab(repository.cachedTrending()) { repository.trending() }
            is KeyboardTab.Mood -> loadTab(repository.cachedCategory(newTab.category.name)) {
                repository.category(newTab.category.name)
            }
        }
    }

    fun retryTab() = selectTab(tab)

    private fun loadTab(cached: List<Clip>?, load: suspend () -> List<Clip>) {
        tabClips = cached.orEmpty()
        tabLoading = cached.isNullOrEmpty()
        tabJob = scope.launch {
            try {
                tabClips = load()
                tabLoading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (tabClips.isEmpty()) tabError = friendlyError(e)
                tabLoading = false
            }
        }
    }

    // --- Search --------------------------------------------------------------

    fun openSearch() {
        player.stop()
        selectedId = null
        typing = true
        if (query.isEmpty()) appliedQuery?.let { updateQuery(it) }
    }

    fun closeSearch() {
        typing = false
        selectedId = null
    }

    fun type(text: String) {
        if (query.length < MAX_QUERY) updateQuery(query + text)
    }

    fun backspace() {
        if (query.isNotEmpty()) updateQuery(query.dropLast(1))
    }

    fun clearQuery() = updateQuery("")

    /** The search key: show the results in the big grid. */
    fun submitSearch() {
        val text = query.trim()
        typing = false
        selectedId = null
        appliedQuery = text.ifEmpty { null }
    }

    fun clearAppliedSearch() {
        appliedQuery = null
        updateQuery("")
    }

    fun updateQuery(text: String) {
        query = text
        selectedId = null
        searchJob?.cancel()
        val q = text.trim()
        if (q.isEmpty()) {
            results = emptyList()
            resultsLoading = false
            return
        }
        // Instant matches from what's on the phone, then the full server search.
        results = repository.localSearch(q)
        resultsLoading = true
        searchJob = scope.launch {
            delay(250)
            results = repository.search(q).clips
            resultsLoading = false
        }
    }

    // --- Preview and send ----------------------------------------------------

    /** First tap previews (if auto-preview is on), second tap sends. */
    fun onTileTap(clip: Clip) {
        if (sendingId != null) return
        if (selectedId == clip.id) {
            send(clip)
            return
        }
        selectedId = clip.id
        if (store.autoPreview.value) player.play(clip, PREVIEW_VOLUME) else player.stop()
    }

    fun onPlayTap(clip: Clip) {
        if (sendingId != null) return
        selectedId = clip.id
        player.toggle(clip, PREVIEW_VOLUME)
    }

    fun send(clip: Clip) {
        if (sendingId != null) return
        player.stop()
        sendingId = clip.id
        scope.launch {
            try {
                val uri = graph.sender.prepareVideo(clip)
                if (!insertIntoChat(clip, uri)) {
                    // The chat box doesn't take videos directly: open the share sheet,
                    // going straight to the same chat app when possible.
                    val target = service.currentInputEditorInfo?.packageName
                    service.startActivity(
                        graph.sender.shareIntent(clip, uri, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                graph.sender.markSent(clip)
                selectedId = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ActivityNotFoundException) {
                showNotice("This app can't receive videos.")
            } catch (e: Exception) {
                showNotice(friendlyError(e))
            } finally {
                sendingId = null
            }
        }
    }

    private fun insertIntoChat(clip: Clip, uri: Uri): Boolean {
        val editor = service.currentInputEditorInfo ?: return false
        val connection = service.currentInputConnection ?: return false
        val acceptsVideo = EditorInfoCompat.getContentMimeTypes(editor).any {
            ClipDescription.compareMimeTypes(VIDEO_MIME, it)
        }
        if (!acceptsVideo) return false

        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            service.grantUriPermission(editor.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val content = InputContentInfoCompat(uri, ClipDescription(clip.title, arrayOf(VIDEO_MIME)), null)
        return InputConnectionCompat.commitContent(connection, editor, content, flags, null)
    }

    // --- Misc ----------------------------------------------------------------

    fun showNotice(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(2800)
            notice = null
        }
    }

    fun switchKeyboard() = service.switchToPreviousKeyboard()

    fun pickKeyboard() = service.showKeyboardPicker()

    private companion object {
        const val VIDEO_MIME = "video/mp4"
        const val PREVIEW_VOLUME = 0.6f
        const val MAX_QUERY = 60
    }
}
