package com.student.quiz

import com.student.quiz.data.MatchingLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchingLogicTest {

    @Test
    fun `selectRight assigns right item to left item`() {
        val links = emptyMap<String, String>()
        val updated = MatchingLogic.selectRight(links, "left1", "right1")
        assertEquals(mapOf("left1" to "right1"), updated)
    }

    @Test
    fun `one right item used once rule reassigns right item`() {
        val links = mapOf("left1" to "right1")
        val updated = MatchingLogic.selectRight(links, "left2", "right1")
        assertEquals(mapOf("left2" to "right1"), updated)
        assertNull(updated["left1"])
    }

    @Test
    fun `holderOf identifies other left item using right item`() {
        val links = mapOf("left1" to "right1")
        assertEquals("left1", MatchingLogic.holderOf(links, "left2", "right1"))
        assertNull(MatchingLogic.holderOf(links, "left1", "right1"))
        assertNull(MatchingLogic.holderOf(links, "left2", "right2"))
    }
}
