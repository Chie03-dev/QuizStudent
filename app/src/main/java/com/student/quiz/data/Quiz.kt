package com.student.quiz.data

/**
 * The eight question shapes from docs/protocol.md "Quiz run (step 2)".
 * Answer keys, accepted answers and tolerances never reach the phone.
 */
sealed class Question {
    abstract val qid: String
    abstract val body: String
    abstract val points: Double

    data class Mcq(
        override val qid: String,
        override val body: String,
        override val points: Double,
        val options: List<Option>,
    ) : Question()

    data class Tf(
        override val qid: String,
        override val body: String,
        override val points: Double,
    ) : Question()

    data class Identification(
        override val qid: String,
        override val body: String,
        override val points: Double,
    ) : Question()

    data class FillIn(
        override val qid: String,
        override val body: String,
        override val points: Double,
        val blanks: Int,
    ) : Question()

    data class Enumeration(
        override val qid: String,
        override val body: String,
        override val points: Double,
        val count: Int,
    ) : Question()

    data class Problem(
        override val qid: String,
        override val body: String,
        override val points: Double,
    ) : Question()

    data class Matching(
        override val qid: String,
        override val body: String,
        override val points: Double,
        val left: List<Item>,
        val right: List<Item>,
    ) : Question()

    data class Connect(
        override val qid: String,
        override val body: String,
        override val points: Double,
        val prompts: List<Item>,
        val answers: List<Item>,
    ) : Question()
}

data class Option(val id: String, val text: String)

data class Item(val id: String, val text: String)

/** Everything in a quiz_start message. */
data class QuizStart(
    val quizId: String,
    val title: String,
    val questions: List<Question>,
    val endsAt: Long,
    val serverTime: Long,
    /** Step 3: while true the phone pins itself and reports focus changes. */
    val lockMode: Boolean = false,
    /** Timer control: while true the countdown is frozen. */
    val paused: Boolean = false,
)

/**
 * A student answer, kept in the shape the wire uses for each question type:
 * a boolean for tf, a list for fillin/enumeration, a map for matching/connect.
 */
sealed interface AnswerValue {
    data class Text(val text: String) : AnswerValue
    data class Flag(val value: Boolean) : AnswerValue
    data class Items(val items: List<String>) : AnswerValue
    data class Links(val links: Map<String, String>) : AnswerValue
}