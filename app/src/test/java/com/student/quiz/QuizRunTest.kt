package com.student.quiz

import com.student.quiz.data.AnswerCodec
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.Question
import com.student.quiz.net.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** quiz_start parsing for all eight question types. */
class QuizStartParseTest {

    private fun parse(questions: String): List<Question> {
        val message = Protocol.parse(
            """{"t":"quiz_start","d":{"quizId":"q1","title":"Sample","endsAt":2000,
               "serverTime":1000,"questions":[$questions]}}""",
        )
        return (message as Protocol.ServerMessage.QuizStartMessage).quiz.questions
    }

    @Test
    fun `reads the quiz header and times`() {
        val quiz = (Protocol.parse(
            """{"t":"quiz_start","d":{"quizId":"q1","title":"Sample quiz","endsAt":2000,
               "serverTime":1000,"questions":[]}}""",
        ) as Protocol.ServerMessage.QuizStartMessage).quiz
        assertEquals("q1", quiz.quizId)
        assertEquals("Sample quiz", quiz.title)
        assertEquals(2000L, quiz.endsAt)
        assertEquals(1000L, quiz.serverTime)
    }

    @Test
    fun `parses mcq with options`() {
        val q = parse(
            """{"qid":"m1","type":"mcq","body":"2+2?","points":2,
               "options":[{"id":"a","text":"3"},{"id":"b","text":"4"}]}""",
        ).single() as Question.Mcq
        assertEquals("m1", q.qid)
        assertEquals("2+2?", q.body)
        assertEquals(2.0, q.points, 0.0)
        assertEquals(listOf("a", "b"), q.options.map { it.id })
        assertEquals("4", q.options[1].text)
    }

    @Test
    fun `parses tf with no extra fields`() {
        val q = parse("""{"qid":"t1","type":"tf","body":"Water is wet","points":1}""")
            .single() as Question.Tf
        assertEquals("t1", q.qid)
        assertEquals(1.0, q.points, 0.0)
    }

    @Test
    fun `parses identification`() {
        val q = parse("""{"qid":"i1","type":"identification","body":"Capital?","points":3}""")
            .single() as Question.Identification
        assertEquals("Capital?", q.body)
        assertEquals(3.0, q.points, 0.0)
    }

    @Test
    fun `parses fillin with its blank count`() {
        val q = parse("""{"qid":"f1","type":"fillin","body":"The sky is ___","points":2,"blanks":2}""")
            .single() as Question.FillIn
        assertEquals(2, q.blanks)
    }

    @Test
    fun `parses enumeration with its item count`() {
        val q = parse("""{"qid":"e1","type":"enumeration","body":"Name two","points":4,"count":3}""")
            .single() as Question.Enumeration
        assertEquals(3, q.count)
    }

    @Test
    fun `parses problem`() {
        val q = parse("""{"qid":"p1","type":"problem","body":"Solve x^2=4","points":5}""")
            .single() as Question.Problem
        assertEquals("Solve x^2=4", q.body)
    }

    @Test
    fun `parses matching with left and right lists`() {
        val q = parse(
            """{"qid":"m2","type":"matching","body":"Match","points":3,
               "left":[{"id":"l1","text":"cat"},{"id":"l2","text":"dog"}],
               "right":[{"id":"r1","text":"meow"},{"id":"r2","text":"woof"}]}""",
        ).single() as Question.Matching
        assertEquals(listOf("l1", "l2"), q.left.map { it.id })
        assertEquals(listOf("meow", "woof"), q.right.map { it.text })
    }

    @Test
    fun `parses connect with prompts and answers including decoys`() {
        val q = parse(
            """{"qid":"c1","type":"connect","body":"Connect","points":3,
               "prompts":[{"id":"p1","text":"mitochondrion"}],
               "answers":[{"id":"a1","text":"ATP"},{"id":"a2","text":"decoy"}]}""",
        ).single() as Question.Connect
        assertEquals(1, q.prompts.size)
        assertEquals(2, q.answers.size)
        assertEquals("decoy", q.answers[1].text)
    }

    @Test
    fun `parses all eight types in one quiz and skips an unknown type`() {
        val questions = parse(
            """{"qid":"a","type":"mcq","body":"b","points":1,"options":[]},
               {"qid":"b","type":"tf","body":"b","points":1},
               {"qid":"c","type":"identification","body":"b","points":1},
               {"qid":"d","type":"fillin","body":"b","points":1,"blanks":1},
               {"qid":"e","type":"enumeration","body":"b","points":1,"count":1},
               {"qid":"f","type":"problem","body":"b","points":1},
               {"qid":"g","type":"matching","body":"b","points":1,"left":[],"right":[]},
               {"qid":"h","type":"connect","body":"b","points":1,"prompts":[],"answers":[]},
               {"qid":"z","type":"mystery","body":"b","points":1}""",
        )
        assertEquals(8, questions.size)
        assertEquals("h", questions.last().qid)
    }
}

/** ack and quiz_end are decoded too. */
class ServerRunMessageTest {

    @Test
    fun `parses ack and quiz_end`() {
        val ack = Protocol.parse("""{"t":"ack","d":{"qid":"q1","seq":12}}""")
                as Protocol.ServerMessage.Ack
        assertEquals("q1", ack.qid)
        assertEquals(12L, ack.seq)

        val end = Protocol.parse("""{"t":"quiz_end","d":{"reason":"instructor"}}""")
                as Protocol.ServerMessage.QuizEnd
        assertEquals("instructor", end.reason)
    }

    @Test
    fun `answers_state restores earlier answers in their per-type shapes`() {
        val message = Protocol.parse(
            """{"t":"answers_state","d":{"answers":{"t1":true,"f1":["a","b"],
               "m1":{"l1":"r1"}}}}""",
        ) as Protocol.ServerMessage.AnswersState

        assertEquals(AnswerValue.Flag(true), AnswerCodec.fromJson(message.answers["t1"]))
        assertEquals(
            AnswerValue.Items(listOf("a", "b")),
            AnswerCodec.fromJson(message.answers["f1"]),
        )
        assertEquals(
            AnswerValue.Links(mapOf("l1" to "r1")),
            AnswerCodec.fromJson(message.answers["m1"]),
        )
    }
}