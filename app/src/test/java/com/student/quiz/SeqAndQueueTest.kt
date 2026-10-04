package com.student.quiz

import com.student.quiz.data.AnswerQueue
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.KeyValueStore
import com.student.quiz.data.SeqCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fake prefs file so the seq counter can be tested off-device. */
private class FakeStore : KeyValueStore {
    val values = mutableMapOf<String, Long>()
    override fun getLong(key: String): Long = values[key] ?: 0L
    override fun putLong(key: String, value: Long) {
        values[key] = value
    }
}

class SeqCounterTest {

    @Test
    fun `seq increases by one for every answer`() {
        val counter = SeqCounter(FakeStore())
        assertEquals(1L, counter.next())
        assertEquals(2L, counter.next())
        assertEquals(3L, counter.next())
    }

    @Test
    fun `seq keeps increasing after an app restart`() {
        val store = FakeStore()
        SeqCounter(store).next()
        SeqCounter(store).next()
        // A new counter over the same persisted storage, as after a restart.
        val afterRestart = SeqCounter(store)
        assertEquals(3L, afterRestart.next())
        assertEquals(3L, SeqCounter(store).current)
    }
}

/** add, ack removes, resend after reconnect. */
class AnswerQueueTest {

    private val text = AnswerValue.Text("a")

    @Test
    fun `a fresh queue resends nothing`() {
        assertTrue(AnswerQueue().pendingPerQuestion().isEmpty())
    }

    @Test
    fun `clearing drops everything, so a new session inherits no answers`() {
        val queue = AnswerQueue()
        queue.add("q1", 1, text)
        queue.clear()
        assertTrue(queue.pendingPerQuestion().isEmpty())
        assertFalse(queue.isUnacked("q1"))
    }

    @Test
    fun `an added answer is unacked`() {
        val queue = AnswerQueue()
        queue.add("q1", 1, text)
        assertTrue(queue.isUnacked("q1"))
        assertEquals(1, queue.unackedCount())
    }

    @Test
    fun `an ack with the same qid and seq removes the entry`() {
        val queue = AnswerQueue()
        queue.add("q1", 7, text)
        assertTrue(queue.onAck("q1", 7))
        assertFalse(queue.isUnacked("q1"))
        assertEquals(0, queue.unackedCount())
    }

    @Test
    fun `an ack with a different seq or qid does not remove it`() {
        val queue = AnswerQueue()
        queue.add("q1", 7, text)
        assertFalse(queue.onAck("q1", 6))
        assertFalse(queue.onAck("q2", 7))
        assertTrue(queue.isUnacked("q1"))
    }

    @Test
    fun `after a reconnect only the latest value per question is resent`() {
        val queue = AnswerQueue()
        queue.add("q1", 1, AnswerValue.Text("a"))
        queue.add("q1", 2, AnswerValue.Text("ab"))
        queue.add("q2", 3, AnswerValue.Text("x"))

        val pending = queue.pendingPerQuestion()
        assertEquals(2, pending.size)
        assertEquals(AnswerValue.Text("ab"), pending.first { it.qid == "q1" }.value)
        assertEquals(3L, pending.first { it.qid == "q2" }.seq)
    }

    @Test
    fun `a resend keeps the original seq, which the server would otherwise drop`() {
        val queue = AnswerQueue()
        queue.add("q1", 42, text)
        val pending = queue.pendingPerQuestion()
        assertEquals(42L, pending.single().seq)
        assertEquals(text, pending.single().value)
    }

    @Test
    fun `acking the latest value clears the question`() {
        val queue = AnswerQueue()
        queue.add("q1", 1, text)
        queue.add("q1", 2, AnswerValue.Text("ab"))
        queue.onAck("q1", 2)
        assertFalse(queue.isUnacked("q1"))
    }

    @Test
    fun `unacked qids drive the saving indicator`() {
        val queue = AnswerQueue()
        queue.add("q1", 1, text)
        queue.add("q2", 2, text)
        assertEquals(setOf("q1", "q2"), queue.unackedQids())
        queue.onAck("q1", 1)
        assertEquals(setOf("q2"), queue.unackedQids())
    }
}