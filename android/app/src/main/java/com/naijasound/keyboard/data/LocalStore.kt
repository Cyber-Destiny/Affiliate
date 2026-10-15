package com.naijasound.keyboard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Everything kept on the phone: favourites, recently sent, cached lists and settings.
 * There are no user accounts — this is the only place these live.
 */
class LocalStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("naija_sound", Context.MODE_PRIVATE)
    private val clipList = ListSerializer(Clip.serializer())
    private val categoryList = ListSerializer(Category.serializer())

    private val _favourites = MutableStateFlow(read(KEY_FAVOURITES, clipList).orEmpty())
    val favourites: StateFlow<List<Clip>> = _favourites.asStateFlow()

    private val _recent = MutableStateFlow(read(KEY_RECENT, clipList).orEmpty())
    val recent: StateFlow<List<Clip>> = _recent.asStateFlow()

    private val _autoPreview = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PREVIEW, true))
    val autoPreview: StateFlow<Boolean> = _autoPreview.asStateFlow()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var cachedTrending: List<Clip>
        get() = read(KEY_TRENDING, clipList).orEmpty()
        set(value) = write(KEY_TRENDING, clipList, value)

    var cachedCategories: List<Category>
        get() = read(KEY_CATEGORIES, categoryList).orEmpty()
        set(value) = write(KEY_CATEGORIES, categoryList, value)

    fun isFavourite(clipId: String): Boolean = _favourites.value.any { it.id == clipId }

    /** Returns true if the clip is now a favourite. */
    fun toggleFavourite(clip: Clip): Boolean {
        val updated = _favourites.updateAndGet { ClipLists.toggle(it, clip) }
        write(KEY_FAVOURITES, clipList, updated)
        return updated.any { it.id == clip.id }
    }

    fun addRecent(clip: Clip) {
        write(KEY_RECENT, clipList, _recent.updateAndGet { ClipLists.pushRecent(it, clip) })
    }

    fun setAutoPreview(enabled: Boolean) {
        _autoPreview.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_PREVIEW, enabled).apply()
    }

    /** Ids of every clip saved on the phone, to check against the server. */
    fun savedIds(): Set<String> = (_favourites.value + _recent.value).mapTo(mutableSetOf()) { it.id }

    fun refreshSaved(fresh: Collection<Clip>, removedIds: Set<String>) {
        write(KEY_FAVOURITES, clipList, _favourites.updateAndGet { ClipLists.refresh(it, fresh, removedIds) })
        write(KEY_RECENT, clipList, _recent.updateAndGet { ClipLists.refresh(it, fresh, removedIds) })
    }

    private fun <T> read(key: String, serializer: KSerializer<T>): T? {
        val text = prefs.getString(key, null) ?: return null
        return runCatching { json.decodeFromString(serializer, text) }.getOrNull()
    }

    private fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(serializer, value)).apply()
    }

    private companion object {
        const val KEY_FAVOURITES = "favourites"
        const val KEY_RECENT = "recent"
        const val KEY_TRENDING = "trending_cache"
        const val KEY_CATEGORIES = "categories_cache"
        const val KEY_AUTO_PREVIEW = "auto_preview"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
