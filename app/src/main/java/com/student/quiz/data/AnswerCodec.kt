package com.student.quiz.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts answers to and from the wire shape for each question type
 * (docs/protocol.md): tf is a JSON boolean, fillin/enumeration are arrays,
 * matching/connect are maps of leftId/promptId -> rightId/answerId.
 */
object AnswerCodec {

    /** Turns a stored [AnswerValue] into a value org.json can write out. */
    fun toJson(value: AnswerValue): Any = when (value) {
        is AnswerValue.Text -> value.text
        is AnswerValue.Flag -> value.value
        is AnswerValue.Items -> JSONArray(value.items)
        is AnswerValue.Links -> JSONObject(value.links)
    }

    /** Reads a value the server sent, e.g. in answers_state. Unparsable -> null. */
    fun fromJson(raw: Any?): AnswerValue? = when (raw) {
        is Boolean -> AnswerValue.Flag(raw)
        is String -> AnswerValue.Text(raw)
        is JSONArray -> AnswerValue.Items(
            (0 until raw.length()).map { index -> raw.optString(index) },
        )
        is JSONObject -> AnswerValue.Links(
            raw.keys().asSequence().associateWith { key -> raw.optString(key) },
        )
        else -> null
    }

    /** True when the value has something worth showing as an answer. */
    fun isAnswered(question: Question, value: AnswerValue?): Boolean = when (question) {
        is Question.Mcq -> value is AnswerValue.Text && value.text.isNotEmpty()
        is Question.Tf -> value is AnswerValue.Flag
        is Question.Identification, is Question.Problem ->
            value is AnswerValue.Text && value.text.isNotBlank()
        is Question.FillIn -> anyFilled(value)
        is Question.Enumeration -> anyFilled(value)
        is Question.Matching, is Question.Connect ->
            value is AnswerValue.Links && value.links.isNotEmpty()
    }

    private fun anyFilled(value: AnswerValue?): Boolean {
        val items = (value as? AnswerValue.Items)?.items ?: return false
        return items.any { item: String -> item.isNotBlank() }
    }

    /** The blank/empty value for a question, used before the student types. */
    fun emptyValue(question: Question): AnswerValue = when (question) {
        is Question.Tf -> AnswerValue.Flag(false)
        is Question.FillIn -> emptyItems(question.blanks)
        is Question.Enumeration -> emptyItems(question.count)
        is Question.Matching -> AnswerValue.Links(emptyMap())
        is Question.Connect -> AnswerValue.Links(emptyMap())
        else -> AnswerValue.Text("")
    }

    private fun emptyItems(size: Int): AnswerValue.Items =
        AnswerValue.Items(List(size) { index: Int -> "" })
}