package com.student.quiz

import com.student.quiz.data.LockCommand
import com.student.quiz.data.LockController
import com.student.quiz.data.LockEvent
import com.student.quiz.net.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paused state of step 3, and the wire shapes of the new messages. The
 * paused screen replaces the quiz entirely, so these tests pin down exactly
 * when the phone is paused and when it may ask to resume.
 */
class LockPauseTest {

    private val lock = LockController()

    private fun pinnedQuiz() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        lock.on(LockEvent.PinState(pinned = true))
        lock.on(LockEvent.GraceElapsed)
    }

    @Test
    fun `paused and resumed drive the paused screen`() {
        pinnedQuiz()
        lock.on(LockEvent.Paused("focus"))
        assertTrue(lock.state().paused)
        assertEquals("focus", lock.state().pausedReason)
        // Still pinned and in front, so asking to resume is allowed.
        assertTrue(lock.state().canResume)

        lock.on(LockEvent.Resumed)
        assertFalse(lock.state().paused)
        assertNull(lock.state().pausedReason)
        // Resuming keeps the pin: the student stays locked in.
        assertTrue(lock.state().pinned)
    }

    @Test
    fun `a network pause keeps the pin and allows a resume request`() {
        pinnedQuiz()
        lock.on(LockEvent.Paused("network"))
        assertTrue(lock.state().paused)
        assertEquals("network", lock.state().pausedReason)
        assertTrue(lock.state().canResume)
    }

    @Test
    fun `canResume is false without focus and without a pin`() {
        pinnedQuiz()
        lock.on(LockEvent.Stopped(true))
        assertFalse(lock.state().canResume)
        lock.on(LockEvent.Stopped(false))
        assertTrue(lock.state().canResume)
        lock.on(LockEvent.PinState(pinned = false))
        assertFalse(lock.state().canResume)
    }

    @Test
    fun `pin again starts a fresh grace window`() {
        pinnedQuiz()
        lock.on(LockEvent.PinState(pinned = false))
        lock.on(LockEvent.Paused("focus"))
        assertEquals(listOf(LockCommand.StartPin), lock.requestPin())
        // The new prompt takes the focus away again, so nothing is reported.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = false)))
        assertFalse(lock.state().canResume)
        // Once the pin is accepted again, focus is reported back to the server.
        lock.on(LockEvent.GraceElapsed)
        // Accepting the pin clears the earlier unpinned report, so the server
        // is told the phone is focused and pinned again.
        assertEquals(
            listOf(LockCommand.FocusGained),
            lock.on(LockEvent.PinState(pinned = true)),
        )
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
        assertTrue(lock.state().canResume)
    }

    @Test
    fun `a reconnect mid-quiz never asks for the pin twice`() {
        pinnedQuiz()
        // quiz_start arrives again with the same lockMode after a reconnect.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.QuizStarted(lockMode = true)))
        assertTrue(lock.state().pinned)
    }

    @Test
    fun `the focus messages match the protocol shapes`() {
        assertEquals(
            "{\"t\":\"focus_lost\",\"d\":{\"reason\":\"unpinned\"}}",
            Protocol.focusLost("unpinned"),
        )
        assertEquals("{\"t\":\"focus_gained\",\"d\":{}}", Protocol.focusGained())
        assertEquals("{\"t\":\"resume_request\",\"d\":{}}", Protocol.resumeRequest())
    }

    @Test
    fun `step 3 server messages parse`() {
        assertEquals(
            Protocol.ServerMessage.Paused("network"),
            Protocol.parse("""{"t":"paused","d":{"reason":"network"}}"""),
        )
        assertEquals(
            Protocol.ServerMessage.Resumed,
            Protocol.parse("""{"t":"resumed","d":{}}"""),
        )
        assertEquals(
            Protocol.ServerMessage.Lock(true),
            Protocol.parse("""{"t":"lock","d":{"on":true}}"""),
        )
        assertEquals(
            Protocol.ServerMessage.Lock(false),
            Protocol.parse("""{"t":"lock","d":{"on":false}}"""),
        )
    }

    @Test
    fun `quiz_start carries lockMode and defaults to off`() {
        val locked = Protocol.parse(
            """{"t":"quiz_start","d":{"quizId":"q1","lockMode":true,"questions":[]}}""",
        ) as Protocol.ServerMessage.QuizStartMessage
        assertTrue(locked.quiz.lockMode)
        val unlocked = Protocol.parse(
            """{"t":"quiz_start","d":{"quizId":"q1","questions":[]}}""",
        ) as Protocol.ServerMessage.QuizStartMessage
        assertFalse(unlocked.quiz.lockMode)
    }
}