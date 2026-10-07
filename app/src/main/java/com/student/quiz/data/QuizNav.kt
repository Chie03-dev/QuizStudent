package com.student.quiz.data

/**
 * Navigation rules for the running quiz, kept free of Android so they can be
 * unit tested: the phone must never block moving between questions because
 * one is unanswered, for any question type.
 */
object QuizNav {

    /** What a question number chip shows. */
    enum class ChipState {
        /** Has an answer: drawn filled. */
        ANSWERED,

        /** Seen but not answered: outlined, with a marker. */
        VISITED,

        /** Not reached yet: plain. */
        UNVISITED,
    }

    fun nextIndex(count: Int, currentIndex: Int): Int =
        (currentIndex + 1).coerceIn(0, (count - 1).coerceAtLeast(0))

    fun previousIndex(count: Int, currentIndex: Int): Int = (currentIndex - 1).coerceAtLeast(0)

    fun isFirst(currentIndex: Int): Boolean = currentIndex <= 0

    /** Next is available whenever there is a next question, answered or not. */
    fun canGoNext(count: Int, currentIndex: Int): Boolean =
        count > 0 && currentIndex < count - 1

    fun canGoPrevious(currentIndex: Int): Boolean = !isFirst(currentIndex)

    /** The last question offers Done instead of Skip. */
    fun isLast(count: Int, currentIndex: Int): Boolean = count > 0 && currentIndex >= count - 1

    /** The first question with no answer, or null when every question has one. */
    fun firstUnanswered(
        questions: List<Question>,
        answers: Map<String, AnswerValue>,
    ): Int? = questions.indexOfFirst { !AnswerCodec.isAnswered(it, answers[it.qid]) }
        .takeIf { it >= 0 }

    /**
     * Where Done goes: the first unanswered question. Only offered on the last
     * question, and never disabled while anything is still unanswered, so a
     * student always has a way back to it.
     */
    fun doneTarget(
        questions: List<Question>,
        answers: Map<String, AnswerValue>,
    ): Int? = firstUnanswered(questions, answers)

    /** Done is shown, and enabled, exactly while a question is unanswered. */
    fun canFinish(questions: List<Question>, answers: Map<String, AnswerValue>): Boolean =
        doneTarget(questions, answers) != null

    fun chipState(
        question: Question,
        answered: Boolean,
        visited: Boolean,
    ): ChipState = when {
        answered -> ChipState.ANSWERED
        visited -> ChipState.VISITED
        else -> ChipState.UNVISITED
    }
}
