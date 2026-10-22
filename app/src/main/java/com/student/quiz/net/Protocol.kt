package com.student.quiz.net

import org.json.JSONObject

/**
 * Wire helpers for the envelope described in docs/protocol.md:
 *   { "t": "<type>", "d": { ... } }
 * The server is authoritative; this app only sends join/hb and reads replies.
 */
object Protocol {

    const val PATH = "/ws"

    fun join(pin: String, name: String, deviceToken: String, avatar: com.student.quiz.data.AvatarSpec? = null): String {
        val d = JSONObject()
            .put("pin", pin)
            .put("name", name)
            .put("deviceToken", deviceToken)
        avatar?.let { d.put("avatar", it.toJson()) }
        return envelope("join", d)
    }

    fun heartbeat(): String = envelope("hb", JSONObject())

    /** Step 2: an answer for one question. */
    fun answer(qid: String, value: Any, seq: Long): String {
        val d = JSONObject()
            .put("qid", qid)
            .put("value", value)
            .put("seq", seq)
        return envelope("answer", d)
    }

    private fun envelope(type: String, data: JSONObject): String =
        JSONObject().put("t", type).put("d", data).toString()

    /**
     * Step 3: the student left the app, so the server may pause them.
     * "background" (activity stopped), "window" (window focus lost) and
     * "unpinned" (lock task mode ended) are the only reasons in the protocol.
     */
    fun focusLost(reason: String): String {
        val d = JSONObject().put("reason", reason)
        return envelope("focus_lost", d)
    }

    fun focusGained(): String = envelope("focus_gained", JSONObject())

    /** A request only: it never resumes anything by itself. */
    fun resumeRequest(): String = envelope("resume_request", JSONObject())

    /** Step 5: the student submits; no further answers are accepted from them. */
    fun finish(): String = envelope("finish", JSONObject())

    /** Decoded server -> phone messages. */
    sealed interface ServerMessage {
        data class Joined(val studentId: String, val deviceToken: String?, val avatar: com.student.quiz.data.AvatarSpec?) : ServerMessage
        data class Failure(val code: String) : ServerMessage
        data object Kick : ServerMessage
        data class QuizStartMessage(val quiz: com.student.quiz.data.QuizStart) : ServerMessage
        data class AnswersState(val answers: Map<String, Any?>) : ServerMessage
        data class Ack(val qid: String, val seq: Long) : ServerMessage
        data class QuizEnd(val reason: String) : ServerMessage
        /** Step 3: only the instructor can resume, via resumed. */
        data class Paused(val reason: String) : ServerMessage
        data object Resumed : ServerMessage
        /** The instructor toggled lock during the quiz. */
        data class Lock(val on: Boolean) : ServerMessage
        /** Timer control: the instructor paused, resumed, or shifted the countdown. */
        data class TimeUpdate(val endsAt: Long, val serverTime: Long, val paused: Boolean) : ServerMessage
        /** Meme pop: display an image overlay on the student phone. */
        data class MemePop(
            val memeId: String,
            val path: String,
            val mime: String,
            val width: Int,
            val height: Int,
            val durationMs: Long,
        ) : ServerMessage
        /** Meme hide: clear the image overlay. */
        data object MemeHide : ServerMessage
        /** Step 5: confirmation; also sent after a rejoin if already finished. */
        data object Finished : ServerMessage
        data class Unknown(val type: String) : ServerMessage
    }

    fun parse(text: String): ServerMessage {
        val root = JSONObject(text)
        return when (val type = root.optString("t")) {
            "joined" -> {
                val d = root.optJSONObject("d") ?: JSONObject()
                ServerMessage.Joined(
                    studentId = d.optString("studentId"),
                    deviceToken = d.optString("deviceToken").ifBlank { null },
                    avatar = com.student.quiz.data.AvatarSpec.fromJson(d.optJSONObject("avatar")),
                )
            }
            "error" -> ServerMessage.Failure(root.optJSONObject("d")?.optString("code").orEmpty())
            "kick" -> ServerMessage.Kick
            "quiz_start" -> ServerMessage.QuizStartMessage(parseQuizStart(root.optJSONObject("d")))
            "answers_state" -> ServerMessage.AnswersState(parseAnswersState(root.optJSONObject("d")))
            "ack" -> {
                val d = root.optJSONObject("d") ?: JSONObject()
                ServerMessage.Ack(d.optString("qid"), d.optLong("seq"))
            }
            "quiz_end" -> ServerMessage.QuizEnd(root.optJSONObject("d")?.optString("reason").orEmpty())
            "paused" -> ServerMessage.Paused(root.optJSONObject("d")?.optString("reason").orEmpty())
            "resumed" -> ServerMessage.Resumed
            "lock" -> ServerMessage.Lock(root.optJSONObject("d")?.optBoolean("on", false) ?: false)
            "time_update" -> {
                val d = root.optJSONObject("d") ?: JSONObject()
                ServerMessage.TimeUpdate(
                    endsAt = d.optLong("endsAt"),
                    serverTime = d.optLong("serverTime"),
                    paused = d.optBoolean("paused", false),
                )
            }
            "meme_pop" -> {
                val d = root.optJSONObject("d") ?: JSONObject()
                ServerMessage.MemePop(
                    memeId = d.optString("memeId"),
                    path = d.optString("path"),
                    mime = d.optString("mime"),
                    width = d.optInt("width"),
                    height = d.optInt("height"),
                    durationMs = d.optLong("durationMs", 3000L),
                )
            }
            "meme_hide" -> ServerMessage.MemeHide
            "finished" -> ServerMessage.Finished
            else -> ServerMessage.Unknown(type)
        }
    }

    /** Parses quiz_start.d into the eight question shapes. */
    fun parseQuizStart(d: JSONObject?): com.student.quiz.data.QuizStart {
        val data = d ?: JSONObject()
        val questions = ArrayList<com.student.quiz.data.Question>()
        data.optJSONArray("questions")?.let { array ->
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { raw -> parseQuestion(raw)?.let(questions::add) }
            }
        }
        return com.student.quiz.data.QuizStart(
            quizId = data.optString("quizId"),
            title = data.optString("title"),
            questions = questions,
            endsAt = data.optLong("endsAt"),
            serverTime = data.optLong("serverTime"),
            // Absent means off: locking is opt-in and must never surprise a
            // student whose server predates step 3.
            lockMode = data.optBoolean("lockMode", false),
            paused = data.optBoolean("paused", false),
        )
    }

    private fun parseQuestion(raw: JSONObject): com.student.quiz.data.Question? {
        val qid = raw.optString("qid")
        val body = raw.optString("body")
        val points = if (raw.has("points")) raw.optDouble("points", 0.0) else 0.0
        return when (raw.optString("type")) {
            "mcq" -> com.student.quiz.data.Question.Mcq(
                qid, body, points, parseOptions(raw.optJSONArray("options")),
            )
            "tf" -> com.student.quiz.data.Question.Tf(qid, body, points)
            "identification" -> com.student.quiz.data.Question.Identification(qid, body, points)
            "fillin" -> com.student.quiz.data.Question.FillIn(qid, body, points, raw.optInt("blanks"))
            "enumeration" -> com.student.quiz.data.Question.Enumeration(
                qid, body, points, raw.optInt("count"),
            )
            "problem" -> com.student.quiz.data.Question.Problem(qid, body, points)
            "matching" -> com.student.quiz.data.Question.Matching(
                qid, body, points,
                left = parseItems(raw.optJSONArray("left"), "text"),
                right = parseItems(raw.optJSONArray("right"), "text"),
            )
            "connect" -> com.student.quiz.data.Question.Connect(
                qid, body, points,
                prompts = parseItems(raw.optJSONArray("prompts"), "text"),
                answers = parseItems(raw.optJSONArray("answers"), "text"),
            )
            else -> null // Unknown type: skip rather than crash the whole quiz.
        }
    }

    private fun parseOptions(array: org.json.JSONArray?): List<com.student.quiz.data.Option> {
if (array == null) return emptyList()
val options = ArrayList<com.student.quiz.data.Option>(array.length())
for (i in 0 until array.length()) {
val obj = array.optJSONObject(i) ?: continue
options.add(
com.student.quiz.data.Option(id = obj.optString("id"), text = obj.optString("text")),
)
}
return options
}

private fun parseItems(array: org.json.JSONArray?, textKey: String): List<com.student.quiz.data.Item> {
        if (array == null) return emptyList()
        val items = ArrayList<com.student.quiz.data.Item>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items.add(
                com.student.quiz.data.Item(
                    id = obj.optString("id"),
                    text = obj.optString(textKey),
                ),
            )
        }
        return items
    }

    /** Parses answers_state.d.answers into raw JSON values keyed by qid. */
    fun parseAnswersState(d: JSONObject?): Map<String, Any?> {
        val answers = LinkedHashMap<String, Any?>()
        val obj = d?.optJSONObject("answers") ?: return answers
        obj.keys().asSequence().forEach { qid -> answers[qid] = obj.get(qid) }
        return answers
    }

    /** Human-readable text for the four error codes the server may send. */
    fun errorMessage(code: String): String = when (code) {
        "BAD_PIN" -> "Wrong PIN. Check the code on the instructor's screen and try again."
        "NAME_TAKEN" -> "That name is already in this quiz. Pick a different name."
        "RATE_LIMITED" -> "Too many wrong PINs from this network. Wait about a minute."
        "CLOSED" -> "The quiz has been closed by the instructor."
        "QUIZ_ENDED" -> "The quiz has ended."
        "BAD_ANSWER" -> "The instructor's computer could not accept that answer."
        "UNKNOWN_QUESTION" -> "That question is not part of this quiz."
        "PAUSED" -> "You are paused. The answer is kept and sent when you resume."
        "FINISHED" -> "You already finished the quiz."
        else -> "Could not join: $code"
    }
}