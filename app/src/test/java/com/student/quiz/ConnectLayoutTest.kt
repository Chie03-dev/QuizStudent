package com.student.quiz

import com.student.quiz.data.AnswerCodec
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.Card
import com.student.quiz.data.ConnectLayout
import com.student.quiz.data.Pt
import com.student.quiz.data.Item
import com.student.quiz.data.Option
import com.student.quiz.data.Question
import com.student.quiz.data.QuizNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dot placement and line bookkeeping for the connect question. These are the
 * rules the UI relies on when it maps measured cards to on-screen dots.
 */
class ConnectLayoutTest {

    private fun card(left: Float, top: Float, right: Float, bottom: Float) =
        Card(left, top, right, bottom)

    @Test
    fun `a prompt dot sits on the card's right edge at mid height`() {
        val dot = ConnectLayout.dot(card(0f, 100f, 150f, 160f), facesRight = true)
        assertEquals(Pt(150f, 130f), dot)
    }

    @Test
    fun `an answer dot sits on the card's left edge at mid height`() {
        val dot = ConnectLayout.dot(card(200f, 40f, 350f, 100f), facesRight = false)
        assertEquals(Pt(200f, 70f), dot)
    }

    @Test
    fun `each card gets its own dot even when the columns have different heights`() {
        // A tall prompt beside a short answer: the dots must not share a y.
        val tallPrompt = card(0f, 0f, 150f, 300f)
        val shortAnswer = card(200f, 100f, 350f, 140f)
        assertEquals(Pt(150f, 150f), ConnectLayout.dot(tallPrompt, facesRight = true))
        assertEquals(Pt(200f, 120f), ConnectLayout.dot(shortAnswer, facesRight = false))
    }

    @Test
    fun `an odd card height still puts the dot at the true middle`() {
        assertEquals(Pt(10f, 25.5f), ConnectLayout.dot(card(0f, 1f, 10f, 50f), facesRight = true))
    }

    @Test
    fun `hitDot picks the dot under the touch, not the first one listed`() {
        val first = Pt(10f, 10f)
        val second = Pt(100f, 10f)
        val dots = listOf(first to "p1", second to "p2")
        assertEquals("p2", ConnectLayout.hitDot(dots, Pt(104f, 12f), touchRadius = 24f))
    }

    @Test
    fun `hitDot returns null when nothing is within the touch radius`() {
        val dots = listOf(Pt(10f, 10f) to "p1")
        assertNull(ConnectLayout.hitDot(dots, Pt(10f, 100f), touchRadius = 24f))
    }

    @Test
    fun `a 48dp touch target reaches dots spaced more than a row apart`() {
        // The whole reason the radius is 48dp: neighbouring dots in a dense
        // column must still be individually grabbable.
        val dots = listOf(Pt(10f, 10f) to "p1", Pt(10f, 60f) to "p2")
        assertEquals("p1", ConnectLayout.hitDot(dots, Pt(10f, 22f), touchRadius = 48f))
        assertEquals("p2", ConnectLayout.hitDot(dots, Pt(10f, 48f), touchRadius = 48f))
    }

    @Test
    fun `tapping a line near its middle counts, tapping far away does not`() {
        val a = Pt(0f, 0f)
        val b = Pt(200f, 0f)
        assertEquals(10f, ConnectLayout.distanceToSegment(Pt(100f, 10f), a, b), 0.001f)
        assertTrue(ConnectLayout.touchesLine(Pt(100f, 20f), a, b, touchRadius = 24f))
        assertFalse(ConnectLayout.touchesLine(Pt(100f, 40f), a, b, touchRadius = 24f))
    }

    @Test
    fun `a tap beyond either end measures to that end, not to an extension`() {
        val a = Pt(0f, 0f)
        val b = Pt(100f, 0f)
        assertEquals(20f, ConnectLayout.distanceToSegment(Pt(120f, 0f), a, b), 0.001f)
    }

    @Test
    fun `dragging a prompt onto a free answer adds the line`() {
        val links = ConnectLayout.connect(emptyMap(), "p1", "a1")
        assertEquals(mapOf("p1" to "a1"), links)
    }

    @Test
    fun `dragging a prompt again replaces its own old line`() {
        val links = ConnectLayout.connect(mapOf("p1" to "a1", "p2" to "a2"), "p1", "a3")
        assertEquals(mapOf("p1" to "a3", "p2" to "a2"), links)
    }

    @Test
    fun `an answer already used moves to the new prompt`() {
        val links = ConnectLayout.connect(mapOf("p1" to "a1", "p2" to "a2"), "p2", "a1")
        // p1 loses the line because a1 now belongs to p2.
        assertEquals(mapOf("p2" to "a1"), links)
    }

    @Test
    fun `connect keeps the order of the untouched prompts`() {
        val links = ConnectLayout.connect(mapOf("p1" to "a1", "p2" to "a2", "p3" to "a3"), "p3", "a9")
        assertEquals(listOf("p1", "p2", "p3"), links.keys.toList())
    }

    @Test
    fun `holderOf finds the prompt holding an answer and null when it is free`() {
        val links: Map<String, String> = mapOf("p1" to "a1")
        assertEquals("p1", ConnectLayout.holderOf(links, "a1"))
        assertNull(ConnectLayout.holderOf(links, "a2"))
    }

    @Test
    fun `the links a drag produces round-trip through the wire codec unchanged`() {
        val links = ConnectLayout.connect(mapOf("p2" to "a2"), "p1", "a1")
        val json = AnswerCodec.toJson(AnswerValue.Links(links))
        assertEquals(
            AnswerValue.Links(mapOf("p2" to "a2", "p1" to "a1")),
            AnswerCodec.fromJson(json),
        )
    }
}

/**
 * The Done button on the last question: it goes to the first unanswered
 * question, and is only ever disabled once nothing is left.
 */
class DoneButtonTest {

    private val questions = listOf(
        Question.Mcq("m1", "2+2?", 1.0, listOf(Option("a", "3"), Option("b", "4"))),
        Question.Tf("t1", "Water is wet?", 1.0),
        Question.Connect(
            "c1",
            "Connect",
            1.0,
            prompts = listOf(Item("p1", "mitochondrion")),
            answers = listOf(Item("a1", "ATP")),
        ),
    )

    @Test
    fun `Done targets the first unanswered question`() {
        // Only the middle one answered: Done goes to question 0.
        val answers = mapOf("t1" to AnswerValue.Flag(true))
        assertEquals(0, QuizNav.doneTarget(questions, answers))
    }

    @Test
    fun `Done targets the earliest gap, not the last one`() {
        // Only the connect question is left, and an empty link map is not an
        // answer, so Done must go there.
        val answers = mapOf(
            "m1" to AnswerValue.Text("b"),
            "t1" to AnswerValue.Flag(true),
            "c1" to AnswerValue.Links(emptyMap()),
        )
        assertEquals(2, QuizNav.doneTarget(questions, answers))
    }

    @Test
    fun `Done stays enabled while any question is unanswered`() {
        assertTrue(QuizNav.canFinish(questions, emptyMap()))
        assertTrue(QuizNav.canFinish(questions, mapOf("m1" to AnswerValue.Text("b"))))
        // A connect question only counts as answered once it has a line.
        assertTrue(
            QuizNav.canFinish(questions, mapOf("c1" to AnswerValue.Links(emptyMap()))),
        )
    }

    @Test
    fun `Done is disabled only when every question has an answer`() {
        val answers = mapOf(
            "m1" to AnswerValue.Text("b"),
            "t1" to AnswerValue.Flag(true),
            "c1" to AnswerValue.Links(mapOf("p1" to "a1")),
        )
        assertFalse(QuizNav.canFinish(questions, answers))
        assertNull(QuizNav.doneTarget(questions, answers))
    }

    @Test
    fun `Done replaces Next and Skip only on the last question`() {
        assertTrue(QuizNav.isLast(questions.size, 2))
        assertFalse(QuizNav.isLast(questions.size, 1))
    }
}