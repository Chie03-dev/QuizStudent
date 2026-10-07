package com.student.quiz.data

/** Everything that can happen to the phone while the quiz runs. */
sealed interface LockEvent {
    /** quiz_start (reconnect included) with its lockMode flag. */
    data class QuizStarted(val lockMode: Boolean) : LockEvent
    /** The instructor toggled lock during the quiz: lock { on }. */
    data class LockChanged(val on: Boolean) : LockEvent
    /** onWindowFocusChanged(hasFocus). */
    data class WindowFocus(val changed: Boolean) : LockEvent
    /** onStop / onPause. */
    data class Stopped(val changed: Boolean) : LockEvent
    /** ActivityManager.getLockTaskModeState() != NONE. */
    data class PinState(val pinned: Boolean) : LockEvent
    /** GRACE_MS after startLockTask(). */
    data object GraceElapsed : LockEvent
    /** A permission dialog is showing: it steals focus, so send nothing. */
    data class PermissionDialog(val open: Boolean) : LockEvent
    /** paused { reason } from the server. */
    data class Paused(val reason: String) : LockEvent
    /** resumed { } from the server. */
    data object Resumed : LockEvent
    /** finished { } from the server: release the pin, stop focus events. */
    data object Finished : LockEvent
    /** quiz_end, a kick, or leaving the quiz. */
    data object QuizEnded : LockEvent
}

/** What to do about it. The Android calls stay outside LockController. */
sealed interface LockCommand {
    /** Send focus_lost { reason }. */
    data class FocusLost(val reason: String) : LockCommand
    /** Send focus_gained { }. */
    data object FocusGained : LockCommand
    /** Call startLockTask(). */
    data object StartPin : LockCommand
    /** Call stopLockTask(). */
    data object StopPin : LockCommand
}

/** What the UI needs to know about lock and pause. */
data class LockState(
    val running: Boolean,
    val lockMode: Boolean,
    val pinned: Boolean,
    val paused: Boolean,
    val pausedReason: String?,
    val hasFocus: Boolean,
    val stopped: Boolean,
    /** Asking to resume is only honest when the phone is really locked down. */
    val canResume: Boolean,
    /** While lock is on and the pin is gone, offer to pin again. */
    val needsPin: Boolean,
    /** Step 5: finished students see the done screen, never the quiz. */
    val finished: Boolean = false,
)