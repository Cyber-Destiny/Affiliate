package com.naijasound.keyboard.data

/** Ideas shown before the user types anything. */
val SearchIdeas = listOf("leave me alone", "I don vex", "God abeg", "e choke", "na wa o", "I'm broke", "who ask you", "sapa")

/**
 * Offline copy of the server's search rules (see supabase/schema.sql), used for
 * instant results while the network answers and when there is no connection.
 */
object SearchText {
    private val apostrophes = Regex("['’`]")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val stretched = Regex("(\\p{L})\\1\\1+")
    private val spaces = Regex("\\s+")

    /** "Gehhhh!!" → "geh", "Don't VEX o" → "dont vex o". Must match public.normalize_search. */
    fun normalize(text: String): String = text
        .lowercase()
        .replace(apostrophes, "")
        .replace(nonWord, " ")
        .replace(stretched, "$1")
        .replace(spaces, " ")
        .trim()

    /**
     * 0 = exact tag or title, 1 = phrase found, 2 = every word found, null = no match.
     * (The server also matches close spellings; offline search does not.)
     */
    fun rank(clip: Clip, normalizedQuery: String): Int? {
        if (normalizedQuery.isEmpty()) return null
        if (normalize(clip.title) == normalizedQuery || clip.tags.any { normalize(it) == normalizedQuery }) return 0
        val haystack = normalize(clip.title + " " + clip.tags.joinToString(" "))
        if (haystack.contains(normalizedQuery)) return 1
        val words = normalizedQuery.split(' ').filter { it.isNotEmpty() }
        if (words.all { haystack.contains(it) }) return 2
        return null
    }

    fun search(clips: Iterable<Clip>, query: String, limit: Int = 40): List<Clip> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return clips
            .distinctBy { it.id }
            .mapNotNull { clip -> rank(clip, q)?.let { clip to it } }
            .sortedWith(compareBy<Pair<Clip, Int>> { it.second }.thenByDescending { it.first.sendCount })
            .take(limit)
            .map { it.first }
    }
}
