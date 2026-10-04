package com.student.quiz.data

/**
 * Answers sent but not yet acked. Entries are removed on an ack with the same
 * qid and seq. After a reconnect the latest unacked value per qid is resent
 * (protocol.md: "the phone just reconnects and resends unacked answers").
 */
class AnswerQueue {

    data class Entry(val qid: String, val seq: Long, val value: AnswerValue)

    private val entries = mutableListOf<Entry>()

    fun add(qid: String, seq: Long, value: AnswerValue) {
        entries.add(Entry(qid, seq, value))
    }

    /**
     * Removes the entry acked, plus any older entry for the same question: the
     * server has the latest value, so the older ones will never be acked.
     */
    fun onAck(qid: String, seq: Long): Boolean {
        val matched = entries.removeAll { it.qid == qid && it.seq <= seq }
        return matched
    }

    /** True while this question's latest answer is still waiting for an ack. */
    fun isUnacked(qid: String): Boolean = entries.any { it.qid == qid }

    fun unackedCount(): Int = entries.size

    /** The qids that currently have at least one unacked answer. */
    fun unackedQids(): Set<String> = entries.map { it.qid }.toSet()

    /** The newest unacked value per question, oldest first. */
    fun pendingPerQuestion(): List<Entry> =
        entries.groupBy { it.qid }.map { (_, list) -> list.maxByOrNull { it.seq }!! }
            .sortedBy { it.seq }

    fun clear() = entries.clear()
}