package com.naijasound.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {
    private fun clip(id: String, title: String, tags: List<String> = emptyList(), sends: Long = 0) =
        Clip(id = id, title = title, tags = tags, mp4Url = "", mp3Url = "", sendCount = sends)

    @Test
    fun `normalize matches the database rules`() {
        // Same cases as supabase/test/schema.test.js
        assertEquals("geh", SearchText.normalize("Gehhhh!!"))
        assertEquals("dont vex o", SearchText.normalize("Don't  VEX o"))
        assertEquals("chai wahala", SearchText.normalize("Chaiii... wahala"))
        assertEquals("leave me alone", SearchText.normalize("leave-me-alone"))
        assertEquals("good food", SearchText.normalize("good food"))
    }

    @Test
    fun `exact tag beats phrase, then most sent wins`() {
        val leave = clip("1", "Leave me alone", listOf("comot", "go away"), sends = 5)
        val comot = clip("2", "Comot for my road", listOf("comot", "move"), sends = 50)
        val other = clip("3", "Comotion everywhere", sends = 999)
        val ids = SearchText.search(listOf(other, leave, comot), "comot").map { it.id }
        assertEquals(listOf("2", "1", "3"), ids)
    }

    @Test
    fun `words in any order and stretched spelling match`() {
        val leave = clip("1", "Leave me alone")
        assertEquals(listOf("1"), SearchText.search(listOf(leave), "alone leave").map { it.id })
        assertEquals(listOf("1"), SearchText.search(listOf(leave), "LEAVE MEEEE").map { it.id })
    }

    @Test
    fun `blank query and no match return nothing`() {
        val leave = clip("1", "Leave me alone")
        assertEquals(emptyList<Clip>(), SearchText.search(listOf(leave), "   "))
        assertNull(SearchText.rank(leave, SearchText.normalize("vex")))
    }

    @Test
    fun `duplicates are dropped`() {
        val leave = clip("1", "Leave me alone")
        assertEquals(1, SearchText.search(listOf(leave, leave), "leave").size)
    }
}
