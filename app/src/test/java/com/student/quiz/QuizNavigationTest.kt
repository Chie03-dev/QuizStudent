package com.student.quiz

import com.student.quiz.data.AnswerValue
import com.student.quiz.data.Item
import com.student.quiz.data.Question
import com.student.quiz.data.QuizNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving between questions must never depend on the current question being
 * answered, for any of the eight types.
 */
class QuizNavigationTest {

    private fun matching(qid: String = "m1") = Question.Matching(
        qid = qid,
        body = "Match these",
        points = 2.0,
        left = listOf(Item("l1", "cat"), Item("l2", "dog")),
        right = listOf(Item("r1", "meow"), Item("r2", "woof")),
    )

    /** One question of each type, all unanswered. */
    private fun everyType(): List<Question> = listOf(
        Question.Mcq("a", "", 1.0, listOf()),
        Question.Tf("b", "", 1.0),
        Question.Identification("c", "", 1.0),
        Question.FillIn("d", "", 1.0, 2),
        Question.Enumeration("e", "", 1.0, 2),
        Question.Problem("f", "", 1.0),
        matching("g"),
        Question.Connect("h", "", 1.0, listOf(), listOf()),
    )

    @Test
    fun `next is available on every unanswered question, matching included`() {
        val questions = everyType()
        // Every index but the last, where there is simply no next question.
        for (index in 0 until questions.size - 1) {
            assertTrue(
                "Next must be enabled on unanswered ${questions[index]::class.simpleName}",
                QuizNav.canGoNext(questions.size, index),
            )
        }
    }

    @Test
    fun `an unanswered matching question never blocks next`() {
        val count = 3
        val matchingIndex = 1
        assertFalse(QuizNav.chipState(matching(), answered = false, visited = true) ==
            QuizNav.ChipState.ANSWERED)
        assertTrue(QuizNav.canGoNext(count, matchingIndex))
        assertEquals(2, QuizNav.nextIndex(count, matchingIndex))
    }

    @Test
    fun `next stops at the last question and previous stops at the first`() {
        assertEquals(2, QuizNav.nextIndex(3, 1))
        assertEquals(2, QuizNav.nextIndex(3, 2))
        assertFalse(QuizNav.canGoNext(3, 2))
        assertTrue(QuizNav.canGoNext(3, 1))

        assertEquals(0, QuizNav.previousIndex(3, 1))
        assertEquals(0, QuizNav.previousIndex(3, 0))
        assertFalse(QuizNav.canGoPrevious(0))
        assertTrue(QuizNav.canGoPrevious(1))
    }

    @Test
    fun `skip on the last question is the done button`() {
        assertTrue(QuizNav.isLast(5, 4))
        assertFalse(QuizNav.isLast(5, 3))
        // An empty quiz has no current question to skip.
        assertFalse(QuizNav.isLast(0, 0))
        assertFalse(QuizNav.canGoNext(0, 0))
    }

    @Test
    fun `done goes back to the first unanswered question`() {
        val questions = everyType()
        // Only the last question is answered.
        val answers = mapOf("h" to AnswerValue.Links(mapOf("p" to "a")))
        assertEquals(0, QuizNav.firstUnanswered(questions, answers))
    }

    @Test
    fun `done skips past questions that already have answers`() {
        val questions = everyType()
        val answers = mapOf(
            "a" to AnswerValue.Text("x"),
            "b" to AnswerValue.Flag(false),
            "g" to AnswerValue.Links(mapOf("l1" to "r1")),
        )
        assertEquals(2, QuizNav.firstUnanswered(questions, answers))
    }

    @Test
    fun `all answered means no question is left to review`() {
        val questions = everyType()
        val answers = mapOf(
            "a" to AnswerValue.Text("x"),
            "b" to AnswerValue.Flag(true),
            "c" to AnswerValue.Text("Paris"),
            "d" to AnswerValue.Items(listOf("blue", "sky")),
            "e" to AnswerValue.Items(listOf("one", "two")),
            "f" to AnswerValue.Text("2"),
            "g" to AnswerValue.Links(mapOf("l1" to "r1")),
            "h" to AnswerValue.Links(mapOf("p" to "a")),
        )
        assertNull(QuizNav.firstUnanswered(questions, answers))
    }

    @Test
    fun `chips have three states`() {
        val question = matching()
        assertEquals(QuizNav.ChipState.ANSWERED, QuizNav.chipState(question, answered = true, visited = false))
        assertEquals(QuizNav.ChipState.VISITED, QuizNav.chipState(question, answered = false, visited = true))
        assertEquals(QuizNav.ChipState.UNVISITED, QuizNav.chipState(question, answered = false, visited = false))
    }

    @Test
    fun `an empty quiz has nothing to navigate`() {
        assertEquals(0, QuizNav.nextIndex(0, 0))
        assertEquals(0, QuizNav.previousIndex(0, 0))
        assertNull(QuizNav.firstUnanswered(emptyList(), emptyMap()))
    }
}
