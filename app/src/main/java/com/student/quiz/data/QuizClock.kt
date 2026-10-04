package com.student.quiz.data

import java.util.Locale

/**
 * The phone is never authoritative for time: it records
 * `offset = serverTime - phoneNow` when quiz_start arrives and shows
 * `endsAt - (phoneNow + offset)`.
 */
object QuizClock {

    /** Milliseconds the phone's clock is behind the server's (negative if ahead). */
    fun offsetMs(serverTime: Long, phoneNow: Long): Long = serverTime - phoneNow

    /** Remaining quiz time; zero or less means the countdown is over. */
    fun remainingMs(endsAt: Long, phoneNow: Long, offsetMs: Long): Long =
        endsAt - (phoneNow + offsetMs)

    /** Formats a remaining duration as m:ss, clamping at zero. */
    fun format(remainingMs: Long): String {
        val total = (remainingMs / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        // Locale.US so locales with non-ASCII digits still show 0:07, not ٠:٠٧.
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}