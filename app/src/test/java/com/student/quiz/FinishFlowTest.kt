package com.student.quiz

import com.student.quiz.data.AnswerValue
import com.student.quiz.data.FinishFlow
import com.student.quiz.data.LockCommand
import com.student.quiz.data.LockController
import com.student.quiz.data.LockEvent
import com.student.quiz.data.Question
import com.student.quiz.net.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 5, "Finish" in docs/protocol.md. The FinishFlow helper holds the pure
 * parts (dialog text, queue drain, timeout), LockController holds the pin and
 * focus parts, and the wire shapes are pinned here. ViewModel timing (the
 * 10 s give-up) is driven by these same helpers.
 */
class FinishFlowTest {

    private fun twoQuestions(): List<Question> = listOf(
        Question.Tf("a", "Sky?", 1.0),
        Question.Tf("b", "Sea?", 1.0),
    )

    @Test
    fun `finish dialog with everything answered`() {
        assertEquals(
            "Finish the quiz? You answered every question.",
            FinishFlow.dialogText(0),
        )
        assertFalse(FinishFlow.showUnansweredOffer(0))
    }

    @Test
    fun `finish dialog names the unanswered count`() {
        assertEquals(
            "Finish the quiz? You have 1 unanswered questions.",
            FinishFlow.dialogText(1),
        )
        assertEquals(
            "Finish the quiz? You have 3 unanswered questions.",
            FinishFlow.dialogText(3),
        )
        assertTrue(FinishFlow.showUnansweredOffer(1))
        assertTrue(FinishFlow.showUnansweredOffer(3))
    }

    @Test
    fun `unanswered count only counts answered questions`() {
        val questions = twoQuestions()
        assertEquals(2, FinishFlow.unansweredCount(questions, emptyMap()))
        assertEquals(
            1,
            FinishFlow.unansweredCount(questions, mapOf("a" to AnswerValue.Flag(true))),
        )
        assertEquals(
            0,
            FinishFlow.unansweredCount(
                questions,
                mapOf("a" to AnswerValue.Flag(true), "b" to AnswerValue.Flag(false)),
            ),
        )
    }

    @Test
    fun `finish waits for an empty queue and times out after 10 s`() {
        assertTrue(FinishFlow.isDrained(emptySet()))
        assertFalse(FinishFlow.isDrained(setOf("a")))
        assertFalse(FinishFlow.isDrained(setOf("a", "b")))
        assertEquals(10_000L, FinishFlow.SAVE_TIMEOUT_MS)
        assertFalse(FinishFlow.timedOut(0L, 9_999L))
        assertFalse(FinishFlow.timedOut(0L, 10_000L))
        assertTrue(FinishFlow.timedOut(0L, 10_001L))
    }

    @Test
    fun `finished state blocks answer sending`() {
        assertTrue(FinishFlow.canSendAnswer(false))
        assertFalse(FinishFlow.canSendAnswer(true))
    }

    @Test
    fun `finish stops sending events and releases the pin`() {
        val lock = LockController()
        lock.on(LockEvent.QuizStarted(lockMode = true))
        lock.on(LockEvent.PinState(pinned = true))
        lock.on(LockEvent.GraceElapsed)
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.Finished))
        // The quiz still counts as running so the finished screen keeps
        // FLAG_SECURE, but no focus event ever goes out again.
        assertTrue(lock.state().running)
        assertTrue(lock.state().finished)
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = false)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.Stopped(true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
    }

    @Test
    fun `a finished student is never paused`() {
        val lock = LockController()
        lock.on(LockEvent.QuizStarted(lockMode = true))
        lock.on(LockEvent.Finished)
        lock.on(LockEvent.Paused("focus"))
        assertFalse(lock.state().paused)
        lock.on(LockEvent.Paused("network"))
        assertFalse(lock.state().paused)
        }

    @Test
    fun `rejoin ordering restores the finished screen`() {
        // quiz_start, answers_state, then finished: a finished rejoin replays
        // the start without clearing the flag, and finished re-applies it.
        val lock = LockController()
        lock.on(LockEvent.QuizStarted(lockMode = true))
        lock.on(LockEvent.PinState(pinned = true))
        lock.on(LockEvent.GraceElapsed)
        // Finishing releases the pin.
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.Finished))
        // The rejoin's quiz_start must not clear the flag...
        lock.on(LockEvent.QuizStarted(lockMode = true))
        assertTrue(lock.state().finished)
        assertFalse(lock.state().paused)
        // ...and the trailing finished lands on the done screen.
        lock.on(LockEvent.Finished)
        assertTrue(lock.state().finished)
        assertTrue(lock.state().running)
    }

    @Test
    fun `the finish wire shapes match the protocol`() {
        assertEquals("{\"t\":\"finish\",\"d\":{}}", Protocol.finish())
        assertEquals(
            Protocol.ServerMessage.Finished,
            Protocol.parse("{\"t\":\"finished\",\"d\":{}}"),
        )
        assertEquals(
            "You already finished the quiz.",
            Protocol.errorMessage("FINISHED"),
        )
    }
}
