package com.student.quiz

import com.student.quiz.data.AnswerCodec
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.Question
import com.student.quiz.data.QuizClock
import com.student.quiz.net.Protocol
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** offset = serverTime - phoneNow; remaining = endsAt - (phoneNow + offset). */
class QuizClockTest {

    @Test
    fun `offset is the server clock minus the phone clock`() {
        // The phone is 5 s slow.
        assertEquals(5_000L, QuizClock.offsetMs(serverTime = 10_000, phoneNow = 5_000))
        // The phone is 3 s ahead.
        assertEquals(-3_000L, QuizClock.offsetMs(serverTime = 7_000, phoneNow = 10_000))
        assertEquals(0L, QuizClock.offsetMs(1_000, 1_000))
    }

    @Test
    fun `remaining shrinks with the phone clock and corrects for its own drift`() {
        // The phone is 1 s slow, so the server clock is phoneNow + 1000.
        val offset = QuizClock.offsetMs(serverTime = 10_000, phoneNow = 9_000)
        assertEquals(29_000L, QuizClock.remainingMs(endsAt = 40_000, phoneNow = 10_000, offsetMs = offset))
        // One second later on the phone, one second less remaining.
        assertEquals(28_000L, QuizClock.remainingMs(endsAt = 40_000, phoneNow = 11_000, offsetMs = offset))
    }

    @Test
    fun `remaining reaches zero at the end`() {
        assertEquals(0L, QuizClock.remainingMs(endsAt = 40_000, phoneNow = 40_000, offsetMs = 0))
    }

    @Test
    fun `formats as minutes and seconds`() {
        assertEquals("0:00", QuizClock.format(0))
        assertEquals("0:00", QuizClock.format(-5_000))
        assertEquals("0:09", QuizClock.format(9_000))
        assertEquals("2:05", QuizClock.format(125_000))
    }

    @Test
    fun `formats with ASCII digits whatever the default locale is`() {
        val original = Locale.getDefault()
        try {
            // Arabic: default digits would render ٢:٠٥ instead of 2:05.
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("2:05", QuizClock.format(125_000))
        } finally {
            Locale.setDefault(original)
        }
    }
}

/** Each answer type serializes to the wire shape the server expects. */
class AnswerSerializationTest {

    private fun sent(qid: String, value: AnswerValue, seq: Long = 4): JSONObject =
        JSONObject(Protocol.answer(qid, AnswerCodec.toJson(value), seq)).getJSONObject("d")

    @Test
    fun `answer envelope carries qid value and seq`() {
        val d = sent("q1", AnswerValue.Text("4"))
        assertEquals("q1", d.getString("qid"))
        assertEquals(4L, d.getLong("seq"))
    }

    @Test
    fun `mcq sends the option id as a string`() {
        val value = sent("m1", AnswerValue.Text("b")).get("value")
        assertEquals("b", value)
        assertTrue(value is String)
    }

    @Test
    fun `tf sends a JSON boolean not a string`() {
        val value = sent("t1", AnswerValue.Flag(true)).get("value")
        assertEquals(true, value)
        assertTrue(value is Boolean)
        assertEquals(false, sent("t1", AnswerValue.Flag(false)).get("value"))
    }

    @Test
    fun `identification and problem send a string`() {
        assertEquals("Paris", sent("i1", AnswerValue.Text("Paris")).get("value"))
        assertEquals("4", sent("p1", AnswerValue.Text("4")).get("value"))
    }

    @Test
    fun `fillin and enumeration send arrays`() {
        val fillIn = sent("f1", AnswerValue.Items(listOf("blue", "green"))).getJSONArray("value")
        assertEquals(2, fillIn.length())
        assertEquals("blue", fillIn.getString(0))
        assertEquals("green", fillIn.getString(1))
        assertEquals(1, sent("e1", AnswerValue.Items(listOf("one"))).getJSONArray("value").length())
    }

    @Test
    fun `matching and connect send maps of id to id`() {
        val matching = sent("m1", AnswerValue.Links(mapOf("l1" to "r1", "l2" to "r2")))
            .getJSONObject("value")
        assertEquals("r1", matching.getString("l1"))
        assertEquals("r2", matching.getString("l2"))
        assertEquals("a1", sent("c1", AnswerValue.Links(mapOf("p1" to "a1")))
            .getJSONObject("value").getString("p1"))
    }

    @Test
    fun `an empty map is still sent`() {
        assertEquals(0, sent("c1", AnswerValue.Links(emptyMap())).getJSONObject("value").length())
    }

    @Test
    fun `values survive a round trip through the codec`() {
        listOf(
            AnswerValue.Text("hello"),
            AnswerValue.Flag(false),
            AnswerValue.Items(listOf("a", "", "c")),
            AnswerValue.Links(mapOf("x" to "y")),
        ).forEach { value ->
            assertEquals(value, AnswerCodec.fromJson(AnswerCodec.toJson(value)))
        }
    }

    @Test
    fun `isAnswered follows the rules per type`() {
        assertTrue(AnswerCodec.isAnswered(Question.Tf("t", "", 1.0), AnswerValue.Flag(false)))
        assertFalse(AnswerCodec.isAnswered(Question.Mcq("m", "", 1.0, emptyList()), AnswerValue.Text("")))
        assertTrue(
            AnswerCodec.isAnswered(
                Question.Enumeration("e", "", 1.0, 2),
                AnswerValue.Items(listOf("", "two")),
            ),
        )
        assertFalse(
            AnswerCodec.isAnswered(
                Question.Connect("c", "", 1.0, emptyList(), emptyList()),
                AnswerValue.Links(emptyMap()),
            ),
        )
    }

    @Test
    fun `quiz clock recompute handles pause, adjust, and resume`() {
        val phoneNow = 10_000L
        val serverTime = 10_000L

        // Initial start: 60s remaining
        val initial = QuizClock.recompute(serverTime, endsAt = 70_000L, paused = false, phoneNow)
        assertEquals(60_000L, initial.remaining(phoneNow))
        assertFalse(initial.paused)

        // Paused time_update: frozen remainder preserved
        val paused = QuizClock.recompute(serverTime = 20_000L, endsAt = 70_000L, paused = true, phoneNow = 20_000L)
        assertTrue(paused.paused)
        assertEquals(50_000L, paused.remaining(20_000L))

        // Adjusted time_update (+1 min): shifted deadline
        val adjusted = QuizClock.recompute(serverTime = 20_000L, endsAt = 130_000L, paused = true, phoneNow = 20_000L)
        assertTrue(adjusted.paused)
        assertEquals(110_000L, adjusted.remaining(20_000L))

        // Resumed time_update: endsAt moves forward on resume, unpaused
        val resumed = QuizClock.recompute(serverTime = 25_000L, endsAt = 135_000L, paused = false, phoneNow = 25_000L)
        assertFalse(resumed.paused)
        assertEquals(110_000L, resumed.remaining(25_000L))
    }

    @Test
    fun `parses time_update and quiz_start paused flag`() {
        val timeUpdate = Protocol.parse("""{"t":"time_update","d":{"endsAt":50000,"serverTime":20000,"paused":true}}""")
            as Protocol.ServerMessage.TimeUpdate
        assertEquals(50000L, timeUpdate.endsAt)
        assertEquals(20000L, timeUpdate.serverTime)
        assertTrue(timeUpdate.paused)

        val quizStart = Protocol.parse(
            """{"t":"quiz_start","d":{"quizId":"q1","title":"Test","endsAt":40000,
               "serverTime":10000,"paused":true,"questions":[]}}""",
        ) as Protocol.ServerMessage.QuizStartMessage
        assertTrue(quizStart.quiz.paused)
    }

    @Test
    fun `parses meme_pop and meme_hide messages`() {
        val pop = Protocol.parse(
            """{"t":"meme_pop","d":{"memeId":"m1","path":"/memes/test.jpg","mime":"image/jpeg","width":800,"height":600,"durationMs":3000}}""",
        ) as Protocol.ServerMessage.MemePop
        assertEquals("m1", pop.memeId)
        assertEquals("/memes/test.jpg", pop.path)
        assertEquals("image/jpeg", pop.mime)
        assertEquals(800, pop.width)
        assertEquals(600, pop.height)
        assertEquals(3000L, pop.durationMs)

        val hide = Protocol.parse("""{"t":"meme_hide","d":{}}""")
        assertTrue(hide is Protocol.ServerMessage.MemeHide)
    }
}