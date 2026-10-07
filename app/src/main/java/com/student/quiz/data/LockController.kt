package com.student.quiz.data

/**
 * Step 3, "Lock and pause" in docs/protocol.md, as pure logic.
 *
 * The phone is never trusted to enforce anything: lock is a deterrent plus
 * visibility. So this class only decides which focus messages to send, and
 * when to pin or unpin. Every Android call (startLockTask, stopLockTask,
 * ActivityManager) stays in MainActivity and AppViewModel.
 *
 * Rules implemented here, from the protocol and the step 3 spec:
 * - Focus messages go out only while the quiz runs AND lockMode is on. The
 *   server ignores them otherwise, so the phone does not send them at all.
 * - Each event is sent once per change: no repeats while focus stays lost.
 * - The pin prompt (and permission dialogs) steal window focus, so for about
 *   GRACE_MS after startLockTask() nothing is sent.
 * - Unpinning is reported once as focus_lost { reason: "unpinned" }.
 * - quiz_end, a kick or leaving the quiz stops any pin.
 * - Step 5: a finished student never sends focus events and is never paused;
 *   finishing releases the pin but the finished screen still counts as running
 *   (FLAG_SECURE, back-button and pin polling all key off running).
 */
class LockController {

    private var running = false
    private var lockMode = false
    private var pinned = false
    private var pinRequested = false
    private var inGrace = false
    private var permissionDialog = false

    /** True while the activity window has focus. */
    private var hasFocus = true

    /** True while the activity is stopped (in the background). */
    private var stopped = false

    /** True once focus_lost has been sent and not yet answered by focus_gained. */
    private var reportedLost = false

    /** True once finish has been confirmed: no focus events, never paused. */
    private var finished = false

    private var pausedReason: String? = null

    /** Feeds one event in and returns the commands to carry out, in order. */
    fun on(event: LockEvent): List<LockCommand> = when (event) {
        is LockEvent.QuizStarted -> onQuizStarted(event.lockMode)
        is LockEvent.LockChanged -> onLockChanged(event.on)
        is LockEvent.WindowFocus -> onWindowFocus(event.changed)
        is LockEvent.Stopped -> onStopped(event.changed)
        is LockEvent.PinState -> onPinState(event.pinned)
        LockEvent.GraceElapsed -> onGraceElapsed()
        is LockEvent.PermissionDialog -> onPermissionDialog(event.open)
        is LockEvent.Paused -> {
            // A finished student is never paused (protocol.md step 5).
            if (running && !finished) pausedReason = event.reason
            emptyList()
        }
        LockEvent.Resumed -> {
            pausedReason = null
            emptyList()
        }
        LockEvent.QuizEnded -> onQuizEnded()
        LockEvent.Finished -> onFinished()
    }

    private fun onQuizStarted(lockMode: Boolean): List<LockCommand> {
        running = true
        this.lockMode = lockMode
        // quiz_start rebuilds the questions on every rejoin; the finished flag
        // is restored by a following finished message (quiz_start,
        // answers_state, then finished per protocol.md), so it must survive
        // here and only reset for a brand-new quiz handled by the ViewModel.
        reportedLost = false
        stopped = false
        // A reconnect mid-quiz reuses the same pin: never ask twice.
        return if (lockMode && !pinned && !pinRequested) requestPin() else emptyList()
    }

    private fun onLockChanged(on: Boolean): List<LockCommand> {
        if (on) {
            lockMode = true
            return if (running && !pinned && !pinRequested) requestPin() else emptyList()
        }
        lockMode = false
        pinRequested = false
        inGrace = false
        // Turning lock off is what resumes focus-paused students, so the phone
        // stops tracking focus from here on.
        reportedLost = false
        return if (pinned) {
            pinned = false
            listOf(LockCommand.StopPin)
        } else {
            emptyList()
        }
    }

    private fun onWindowFocus(changed: Boolean): List<LockCommand> {
        hasFocus = changed
        if (!changed) return commands(focusLost("window"))
        val commands = mutableListOf<LockCommand>()
        focusGained(commands)
        // Regaining focus with lock on but no lock task means the student left
        // screen pinning: report that once.
        if (!inGrace && !pinned) commands += focusLost("unpinned") ?: return commands
        return commands
    }

    private fun onStopped(changed: Boolean): List<LockCommand> {
        stopped = changed
        if (!changed) return emptyList()
        return commands(focusLost("background"))
    }

    private fun onPinState(isPinned: Boolean): List<LockCommand> {
        if (isPinned) {
            pinRequested = false
            val wasPinned = pinned
            pinned = true
            val commands = mutableListOf<LockCommand>()
            if (!wasPinned) focusGained(commands)
            return commands
        }
        pinned = false
        if (inGrace || permissionDialog) return emptyList()
        return commands(focusLost("unpinned"))
    }

    private fun onGraceElapsed(): List<LockCommand> {
        inGrace = false
        // The user may simply have declined the pin prompt.
        return if (running && lockMode && !pinned) commands(focusLost("unpinned"))
        else emptyList()
    }

    private fun onPermissionDialog(open: Boolean): List<LockCommand> {
        permissionDialog = open
        return emptyList()
    }

    private fun onQuizEnded(): List<LockCommand> {
        running = false
        lockMode = false
        pinRequested = false
        inGrace = false
        reportedLost = false
        pausedReason = null
        finished = false
        if (!pinned) return emptyList()
        pinned = false
        return listOf(LockCommand.StopPin)
    }

    /**
     * Step 5: finishing releases the pin and stops every focus event, but the
     * quiz still counts as running so the finished screen keeps FLAG_SECURE.
     */
    private fun onFinished(): List<LockCommand> {
        finished = true
        reportedLost = false
        pausedReason = null
        pinRequested = false
        inGrace = false
        if (!pinned) return emptyList()
        pinned = false
        return listOf(LockCommand.StopPin)
    }

    /** The "Pin again" button on the paused screen. */
    fun requestPin(): List<LockCommand> {
        if (!running || !lockMode) return emptyList()
        pinRequested = true
        inGrace = true
        return listOf(LockCommand.StartPin)
    }

    private fun focusGained(into: MutableList<LockCommand>) {
        if (!reportedLost) return
        reportedLost = false
        into += LockCommand.FocusGained
    }

    /**
     * The focus_lost to send, or null when there is nothing to send: not
     * running, finished, lock off, inside the grace window, a dialog is open,
     * or this loss was already reported.
     */
    private fun focusLost(reason: String): LockCommand? {
        if (!running || finished || !lockMode || inGrace || permissionDialog) return null
        if (reportedLost) return null
        reportedLost = true
        return LockCommand.FocusLost(reason)
    }

    private fun commands(command: LockCommand?): List<LockCommand> =
        if (command == null) emptyList() else listOf(command)

    fun state(): LockState = LockState(
        running = running,
        lockMode = lockMode,
        pinned = pinned,
        paused = pausedReason != null,
        pausedReason = pausedReason,
        hasFocus = hasFocus,
        stopped = stopped,
        // Asking to resume is only honest when the phone is really locked down.
        canResume = !stopped && hasFocus && (!lockMode || pinned),
        // While lock is on and the pin is gone, offer to pin again.
        needsPin = lockMode && !pinned,
        // Step 5: the finished screen. Never paused, never asks to resume.
        finished = finished,
    )

    companion object {
        /** The pin prompt (and permission dialogs) steal focus; wait them out. */
        const val GRACE_MS = 4_000L
    }
}