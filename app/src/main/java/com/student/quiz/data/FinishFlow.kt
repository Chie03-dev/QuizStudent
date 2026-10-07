package com.student.quiz.data

import org.json.JSONObject

/**
 * Step 5, "Finish" in docs/protocol.md, as pure logic so it can be unit
 * tested off-device.
 *
 * The server is authoritative: the phone never scores and never ends the quiz
 * itself. Finishing only submits; the quiz still ends on the timer or the
 * instructor (quiz_end).
 */
object FinishFlow {

    /** How long confirmFinish waits for the unacked queue to drain. */
    const val SAVE_TIMEOUT_MS = 10_000L

    /** Questions with no answer yet. */
    fun unansweredCount(
        questions: List<Question>,
        answers: Map<String, AnswerValue>,
    ): Int = questions.count { !AnswerCodec.isAnswered(it, answers[it.qid]) }

    /**
     * Exact confirmation text. N == 0 is the all-answered case; N > 0 names
     * how many are left.
     */
    fun dialogText(unanswered: Int): String =
        if (unanswered == 0) {
            "Finish the quiz? You answered every question."
        } else {
            "Finish the quiz? You have $unanswered unanswered questions."
        }

    /** "Show unanswered" is only offered while anything is unanswered. */
    fun showUnansweredOffer(unanswered: Int): Boolean = unanswered > 0

    /** True once every sent answer has been acked. */
    fun isDrained(unacked: Set<String>): Boolean = unacked.isEmpty()

    /** True once waiting has gone on longer than [SAVE_TIMEOUT_MS]. */
    fun timedOut(startMs: Long, nowMs: Long): Boolean =
        nowMs - startMs > SAVE_TIMEOUT_MS

    /** No answers may be sent or changed after finish. */
    fun canSendAnswer(finished: Boolean): Boolean = !finished
}
