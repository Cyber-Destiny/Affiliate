package com.naijasound.keyboard.data

/** Small list rules for favourites and recents, kept separate so they are easy to test. */
object ClipLists {
    const val MAX_RECENT = 30

    fun pushRecent(list: List<Clip>, clip: Clip, max: Int = MAX_RECENT): List<Clip> =
        (listOf(clip) + list.filterNot { it.id == clip.id }).take(max)

    /** Newest favourite first. */
    fun toggle(list: List<Clip>, clip: Clip): List<Clip> =
        if (list.any { it.id == clip.id }) list.filterNot { it.id == clip.id } else listOf(clip) + list

    /**
     * Applies fresh server data to a saved list: updated clips replace their old copy,
     * clips in [removedIds] (taken down) disappear, everything else is left alone.
     */
    fun refresh(list: List<Clip>, fresh: Collection<Clip>, removedIds: Set<String>): List<Clip> {
        val byId = fresh.associateBy { it.id }
        return list.filterNot { it.id in removedIds }.map { byId[it.id] ?: it }
    }
}
