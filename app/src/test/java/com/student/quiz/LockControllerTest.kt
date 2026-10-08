package com.student.quiz

import com.student.quiz.data.LockCommand
import com.student.quiz.data.LockController
import com.student.quiz.data.LockEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 3 lock and pause. LockController holds every decision, so these tests
 * cover the whole decision table without an emulator: when a focus event is
 * sent, when it is swallowed by the grace window, and when the pin goes up and
 * comes down.
 */
class LockControllerTest {

    private val lock = LockController()

    /** A locked quiz whose pin prompt was already accepted. */
    private fun pinnedQuiz() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        lock.on(LockEvent.PinState(pinned = true))
        lock.on(LockEvent.GraceElapsed)
    }

    private fun lost(): List<LockCommand> = lock.on(LockEvent.WindowFocus(changed = false))

    @Test
    fun `quiz_start with lock on asks for the pin`() {
        assertEquals(
            listOf(LockCommand.StartPin),
            lock.on(LockEvent.QuizStarted(lockMode = true)),
        )
    }

    @Test
    fun `quiz_start with lock off never pins`() {
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.QuizStarted(lockMode = false)))
        assertFalse(lock.state().lockMode)
    }

    @Test
    fun `focus lost is reported once, not on every change`() {
        pinnedQuiz()
        assertEquals(listOf(LockCommand.FocusLost("window")), lost())
        // Still lost: no repeats while it stays that way.
        assertEquals(emptyList<LockCommand>(), lost())
        assertEquals(emptyList<LockCommand>(), lost())
    }

    @Test
    fun `focus lost after the pin prompt is suppressed by the grace window`() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        // The prompt takes the window focus away; that must not pause anyone.
        assertEquals(emptyList<LockCommand>(), lost())
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        // The prompt itself never counts. Once the grace window is over the
        // real state is checked, and a missing pin is reported exactly once.
        assertEquals(
            listOf(LockCommand.FocusLost("unpinned")),
            lock.on(LockEvent.GraceElapsed),
        )
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
    }

    @Test
    fun `declining the pin prompt reports unpinned once the grace window is over`() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        // The prompt was never accepted, so by the time the grace window is
        // over there is still no lock task: that is reported as "unpinned".
        assertEquals(
            listOf(LockCommand.FocusLost("unpinned")),
            lock.on(LockEvent.GraceElapsed),
        )
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        assertFalse(lock.state().pinned)
    }

    @Test
    fun `a pin the poll confirmed before grace elapses never reports focus lost`() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        // The ~1 s poll saw the pin take effect and reported it before the grace
        // window closed. The grace timer must not then read that still-recent pin
        // as an unpin: that false "unpinned" is what paused every student at the
        // start, and it is only avoided because the poll keeps feeding the real
        // lock task state in (see the pin poll in MainActivity).
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.GraceElapsed))
        assertTrue(lock.state().pinned)
        assertFalse(lock.state().needsPin)
    }

    @Test
    fun `the background reports background and the window reports window`() {
        pinnedQuiz()
        assertEquals(listOf(LockCommand.FocusLost("background")), lock.on(LockEvent.Stopped(true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.Stopped(true)))
    }

    @Test
    fun `focus gained is sent once when focus comes back`() {
        pinnedQuiz()
        lost()
        assertEquals(
            listOf(LockCommand.FocusGained),
            lock.on(LockEvent.WindowFocus(changed = true)),
        )
        // Nothing to say while focus stays.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
    }

    @Test
    fun `no focus events at all when lock mode is off`() {
        lock.on(LockEvent.QuizStarted(lockMode = false))
        assertEquals(emptyList<LockCommand>(), lost())
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.Stopped(true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        // Regaining focus with lock off must stay silent too.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
    }

    @Test
    fun `leaving screen pinning is reported once as unpinned`() {
        pinnedQuiz()
        assertEquals(listOf(LockCommand.FocusLost("unpinned")), lock.on(LockEvent.PinState(false)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        assertFalse(lock.state().pinned)
        // Without a pin, asking to resume is not offered.
        assertFalse(lock.state().canResume)
        assertTrue(lock.state().needsPin)
    }

    @Test
    fun `lock off releases the pin and stops sending focus events`() {
        pinnedQuiz()
        assertEquals(listOf(LockCommand.FocusLost("window")), lost())
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.LockChanged(on = false)))
        // The instructor's toggle resumes focus-paused students server side,
        // so the phone sends nothing more.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
        assertFalse(lock.state().lockMode)
        assertFalse(lock.state().pinned)
    }

    @Test
    fun `lock on again during the quiz asks for the pin`() {
        lock.on(LockEvent.QuizStarted(lockMode = false))
        assertEquals(emptyList<LockCommand>(), lost())
        assertEquals(listOf(LockCommand.StartPin), lock.on(LockEvent.LockChanged(on = true)))
    }

    @Test
    fun `the end of the quiz releases the pin`() {
        pinnedQuiz()
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.QuizEnded))
        // And nothing is reported afterwards.
        assertEquals(emptyList<LockCommand>(), lost())
        assertFalse(lock.state().running)
    }

    @Test
    fun `a permission dialog suppresses focus reporting`() {
        pinnedQuiz()
        lock.on(LockEvent.PermissionDialog(open = true))
        assertEquals(emptyList<LockCommand>(), lost())
        lock.on(LockEvent.PermissionDialog(open = false))
        assertEquals(listOf(LockCommand.FocusLost("window")), lost())
    }

    @Test
    fun `lock off releases the pin when the pin state is not yet known`() {
        lock.on(LockEvent.QuizStarted(lockMode = false))
        assertEquals(listOf(LockCommand.StartPin), lock.on(LockEvent.LockChanged(on = true)))
        // The ~1 s poll has not reported PinState(pinned = true) yet, so the
        // controller still believes pinned = false. The off must release the
        // pin anyway, or the student stays stuck on a pinned screen.
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.LockChanged(on = false)))
        assertFalse(lock.state().pinned)
        assertFalse(lock.state().lockMode)
    }

    @Test
    fun `lock off during the grace window stops the pin and stays silent`() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        // The pin prompt just went up (inGrace) and no poll reported the pin.
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.LockChanged(on = false)))
        // The late grace timer and the poll after stopLockTask say nothing.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.GraceElapsed))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        assertFalse(lock.state().needsPin)
    }

    @Test
    fun `lock on after off asks for the pin again`() {
        lock.on(LockEvent.QuizStarted(lockMode = false))
        assertEquals(listOf(LockCommand.StartPin), lock.on(LockEvent.LockChanged(on = true)))
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.LockChanged(on = false)))
        assertEquals(listOf(LockCommand.StartPin), lock.on(LockEvent.LockChanged(on = true)))
    }

    @Test
    fun `a rapid on off on off sequence ends with StopPin`() {
        lock.on(LockEvent.QuizStarted(lockMode = false))
        val commands = mutableListOf<LockCommand>()
        commands += lock.on(LockEvent.LockChanged(on = true))
        commands += lock.on(LockEvent.LockChanged(on = false))
        commands += lock.on(LockEvent.LockChanged(on = true))
        commands += lock.on(LockEvent.LockChanged(on = false))
        assertEquals(
            listOf(
                LockCommand.StartPin,
                LockCommand.StopPin,
                LockCommand.StartPin,
                LockCommand.StopPin,
            ),
            commands,
        )
        assertEquals(LockCommand.StopPin, commands.last())
    }

    @Test
    fun `nothing is sent or re-pinned while lock is off`() {
        lock.on(LockEvent.QuizStarted(lockMode = false))
        lock.on(LockEvent.LockChanged(on = true))
        lock.on(LockEvent.LockChanged(on = false))
        // A stale poll result, the grace timer, focus changes, the background,
        // the "Pin again" button and a quiz_start replay with the current
        // lockMode = false: none of them may speak or pin again.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = false)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.GraceElapsed))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = false)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.Stopped(true)))
        assertEquals(emptyList<LockCommand>(), lock.requestPin())
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.QuizStarted(lockMode = false)))
        assertFalse(lock.state().needsPin)
    }

    @Test
    fun `a quiz_start replay with lock off releases a stale pin`() {
        pinnedQuiz()
        // The connection dropped before lock { on: false } arrived; the replay
        // carries the CURRENT lockMode, so it must not turn the lock back on
        // and must release the pin still left behind.
        assertEquals(
            listOf(LockCommand.StopPin),
            lock.on(LockEvent.QuizStarted(lockMode = false)),
        )
        assertFalse(lock.state().lockMode)
        assertFalse(lock.state().pinned)
    }

    @Test
    fun `quiz end before the pin poll still stops the pin`() {
        // QuizStarted asks for the pin, but the ~1 s poll has not reported
        // pinned = true yet: pinned == false, pinRequested == true. The end
        // must still release the screen, like lock { on: false } does.
        lock.on(LockEvent.QuizStarted(lockMode = true))
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.QuizEnded))
        assertFalse(lock.state().pinned)
        assertFalse(lock.state().running)
    }

    @Test
    fun `quiz end during the grace window stops the pin and never pins again`() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        // The pin prompt just went up: inGrace, no poll result yet.
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.QuizEnded))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.GraceElapsed))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = true)))
        assertFalse(lock.state().needsPin)
    }

    @Test
    fun `no re-pinning after the quiz has ended`() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        lock.on(LockEvent.QuizEnded)
        // Delayed poll result, grace timer, focus changes, background and the
        // "Pin again" path: none may pin or speak again after the end.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.GraceElapsed))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = false)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.Stopped(true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.Stopped(false)))
        assertEquals(emptyList<LockCommand>(), lock.requestPin())
        assertFalse(lock.state().needsPin)
    }

    @Test
    fun `finish before the pin poll still stops the pin`() {
        lock.on(LockEvent.QuizStarted(lockMode = true))
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.Finished))
        assertTrue(lock.state().finished)
        assertFalse(lock.state().pinned)
    }

    @Test
    fun `a finished student is never pinned again`() {
        pinnedQuiz()
        assertEquals(listOf(LockCommand.StopPin), lock.on(LockEvent.Finished))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.PinState(pinned = true)))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.GraceElapsed))
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.WindowFocus(changed = true)))
        assertEquals(emptyList<LockCommand>(), lock.requestPin())
        // A rejoin's quiz_start must not raise the pin either.
        assertEquals(emptyList<LockCommand>(), lock.on(LockEvent.QuizStarted(lockMode = true)))
    }
}