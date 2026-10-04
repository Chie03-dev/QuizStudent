package com.student.quiz.data

/**
 * `seq` increases for every answer the phone sends and is persisted, so it keeps
 * increasing across app restarts (protocol.md). Backed by a tiny key/value
 * store so the counter can be unit tested without Android.
 */
interface KeyValueStore {
    fun getLong(key: String): Long
    fun putLong(key: String, value: Long)
}

class SeqCounter(private val store: KeyValueStore) {

    /** The next seq to use, then persists it. */
    fun next(): Long {
        val value = store.getLong(KEY) + 1
        store.putLong(KEY, value)
        return value
    }

    var current: Long
        get() = store.getLong(KEY)
        set(value) {
            store.putLong(KEY, value)
        }

    private companion object {
        const val KEY = "answer_seq"
    }
}