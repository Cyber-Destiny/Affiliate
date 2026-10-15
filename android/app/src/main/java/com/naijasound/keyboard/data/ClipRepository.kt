package com.naijasound.keyboard.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

data class LatestClips(val clips: List<Clip>, val thisWeek: Boolean)

data class SearchResult(val clips: List<Clip>, val offline: Boolean)

/**
 * The one place screens and the keyboard get clips from. Network first, with
 * on-phone copies so trending, favourites and recents still work offline.
 * Lists are reused for a few minutes so moving between screens costs no data.
 */
class ClipRepository(
    private val api: SupabaseApi,
    private val store: LocalStore,
    private val scope: CoroutineScope,
) {
    private class Entry(val savedAt: Long, val value: Any)

    private val memory = ConcurrentHashMap<String, Entry>()
    private val playedThisSession = Collections.synchronizedSet(mutableSetOf<String>())

    val isConfigured: Boolean get() = api.isConfigured

    /** Whatever trending list is already on the phone, for an instant first paint. */
    fun cachedTrending(): List<Clip> = peek(KEY_TRENDING) ?: store.cachedTrending

    fun cachedLatest(): LatestClips? = peek(KEY_LATEST)

    fun cachedCategory(name: String): List<Clip>? = peek(categoryKey(name))

    /** Categories without waiting for the network. */
    fun cachedCategories(): List<Category> =
        peek(KEY_CATEGORIES) ?: store.cachedCategories.ifEmpty { DefaultCategories }

    suspend fun trending(): List<Clip> = withFallback(
        load = { cachedLoad(KEY_TRENDING) { api.trending().also { store.cachedTrending = it } } },
        fallback = { cachedTrending().takeIf { it.isNotEmpty() } },
    )

    suspend fun latest(): LatestClips = withFallback(
        load = {
            cachedLoad(KEY_LATEST) {
                val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                val thisWeek = api.newest(sinceMillis = weekAgo)
                if (thisWeek.isNotEmpty()) LatestClips(thisWeek, thisWeek = true) else LatestClips(api.newest(), thisWeek = false)
            }
        },
        fallback = { cachedLatest() },
    )

    suspend fun category(name: String): List<Clip> = withFallback(
        load = { cachedLoad(categoryKey(name)) { api.byCategory(name) } },
        fallback = { cachedCategory(name) },
    )

    suspend fun categories(): List<Category> = withFallback(
        load = {
            cachedLoad(KEY_CATEGORIES) {
                api.categories().also { if (it.isNotEmpty()) store.cachedCategories = it }.ifEmpty { DefaultCategories }
            }
        },
        fallback = { cachedCategories() },
    )

    suspend fun search(query: String): SearchResult = try {
        SearchResult(api.search(query), offline = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SearchResult(localSearch(query), offline = true)
    }

    /** Searches only what is already on the phone. */
    fun localSearch(query: String): List<Clip> {
        val known = buildList {
            addAll(store.favourites.value)
            addAll(store.recent.value)
            addAll(cachedTrending())
            memory.values.forEach { entry ->
                when (val value = entry.value) {
                    is LatestClips -> addAll(value.clips)
                    is List<*> -> addAll(value.filterIsInstance<Clip>())
                    else -> Unit
                }
            }
        }
        return SearchText.search(known, query)
    }

    /** Refreshes saved clips and drops any that were taken down. */
    suspend fun syncSaved() {
        val ids = store.savedIds()
        if (ids.isEmpty() || !api.isConfigured) return
        val fresh = try {
            api.byIds(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        val removed = ids - fresh.mapTo(mutableSetOf()) { it.id }
        store.refreshSaved(fresh, removed)
    }

    suspend fun report(clip: Clip, reason: String, contactEmail: String?) =
        api.report(clip.id, reason, contactEmail)

    fun recordPlay(clip: Clip) {
        if (!playedThisSession.add(clip.id)) return
        scope.launch { runCatching { api.incrementPlay(clip.id) } }
    }

    fun recordSend(clip: Clip) {
        scope.launch { runCatching { api.incrementSend(clip.id) } }
    }

    private suspend fun <T : Any> cachedLoad(key: String, load: suspend () -> T): T {
        val hit = memory[key]
        if (hit != null && System.currentTimeMillis() - hit.savedAt < FRESH_FOR_MS) {
            @Suppress("UNCHECKED_CAST")
            return hit.value as T
        }
        return load().also { memory[key] = Entry(System.currentTimeMillis(), it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> peek(key: String): T? = memory[key]?.value as T?

    private suspend fun <T> withFallback(load: suspend () -> T, fallback: () -> T?): T = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback() ?: throw e
    }

    private fun categoryKey(name: String) = "category:$name"

    private companion object {
        const val FRESH_FOR_MS = 5 * 60 * 1000L
        const val KEY_TRENDING = "trending"
        const val KEY_LATEST = "latest"
        const val KEY_CATEGORIES = "categories"
    }
}
