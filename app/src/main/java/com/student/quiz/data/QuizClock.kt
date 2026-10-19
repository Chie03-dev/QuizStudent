package com.student.quiz.data

import java.util.Locale

/**
 * The phone is never authoritative for time: it records
 * `offset = serverTime - phoneNow` when quiz_start or time_update arrives and shows
 * `endsAt - (phoneNow + offset)`.
 */
object QuizClock {

    data class State(
        val endsAt: Long,
        val offsetMs: Long,
        val paused: Boolean,
    ) {
        fun remaining(phoneNow: Long): Long = QuizClock.remainingMs(endsAt, phoneNow, offsetMs)
    }

    /** Milliseconds the phone's clock is behind the server's (negative if ahead). */
    fun offsetMs(serverTime: Long, phoneNow: Long): Long = serverTime - phoneNow

    /** Remaining quiz time; zero or less means the countdown is over. */
    fun remainingMs(endsAt: Long, phoneNow: Long, offsetMs: Long): Long =
        endsAt - (phoneNow + offsetMs)

    /** Recomputes countdown state on time_update or quiz_start. */
    fun recompute(serverTime: Long, endsAt: Long, paused: Boolean, phoneNow: Long): State {
        val offset = offsetMs(serverTime, phoneNow)
        return State(endsAt = endsAt, offsetMs = offset, paused = paused)
    }

    /** Formats a remaining duration as m:ss, clamping at zero. */
    fun format(remainingMs: Long): String {
        val total = (remainingMs / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        // Locale.US so locales with non-ASCII digits still show 0:07, not ٠:٠٧.
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
