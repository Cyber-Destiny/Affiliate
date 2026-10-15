package com.naijasound.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipListsTest {
    private fun clip(id: String, title: String = id) = Clip(id = id, title = title, mp4Url = "", mp3Url = "")

    @Test
    fun `recent puts the newest first without duplicates and keeps a limit`() {
        var list = emptyList<Clip>()
        listOf("a", "b", "c", "a").forEach { list = ClipLists.pushRecent(list, clip(it), max = 3) }
        assertEquals(listOf("a", "c", "b"), list.map { it.id })
        list = ClipLists.pushRecent(list, clip("d"), max = 3)
        assertEquals(listOf("d", "a", "c"), list.map { it.id })
    }

    @Test
    fun `toggle adds to the front and removes`() {
        val added = ClipLists.toggle(listOf(clip("a")), clip("b"))
        assertEquals(listOf("b", "a"), added.map { it.id })
        assertEquals(listOf("a"), ClipLists.toggle(added, clip("b")).map { it.id })
    }

    @Test
    fun `refresh updates saved clips and drops taken-down ones`() {
        val saved = listOf(clip("a", "Old title"), clip("b"), clip("c"))
        val result = ClipLists.refresh(saved, fresh = listOf(clip("a", "New title")), removedIds = setOf("b"))
        assertEquals(listOf("a" to "New title", "c" to "c"), result.map { it.id to it.title })
    }
}
