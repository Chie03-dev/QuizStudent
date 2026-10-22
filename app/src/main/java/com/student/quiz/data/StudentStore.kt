package com.student.quiz.data

import android.content.Context
import java.util.UUID

/**
 * Small persistent store for the identity of this phone in a quiz session.
 * The deviceToken is generated exactly once and sent on every join, so the
 * server restores the same studentId after a restart (protocol.md "Rules").
 */
class StudentStore(context: Context) {

    private val prefs = context.getSharedPreferences("quiz_student", Context.MODE_PRIVATE)

    val deviceToken: String
        get() {
            prefs.getString(KEY_TOKEN, null)?.let { return it }
            val token = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TOKEN, token).apply()
            return token
        }

    var studentId: String?
        get() = prefs.getString(KEY_STUDENT_ID, null)
        set(value) = prefs.edit().putString(KEY_STUDENT_ID, value).apply()

    var name: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var profilePhoto: ProfilePhoto?
        get() {
            val data = prefs.getString(KEY_PHOTO_DATA, null)
            val mime = prefs.getString(KEY_PHOTO_MIME, "image/jpeg")
            if (data.isNullOrBlank()) return null
            return ProfilePhoto(data = data, mime = mime ?: "image/jpeg")
        }
        set(value) {
            prefs.edit()
                .putString(KEY_PHOTO_DATA, value?.data)
                .putString(KEY_PHOTO_MIME, value?.mime)
                .apply()
        }

    /** Last session address, so the app can offer to rejoin after a restart. */
    var lastIp: String?
        get() = prefs.getString(KEY_LAST_IP, null)
        private set(value) = prefs.edit().putString(KEY_LAST_IP, value).apply()

    var lastPort: Int
        get() = prefs.getInt(KEY_LAST_PORT, 0)
        private set(value) = prefs.edit().putInt(KEY_LAST_PORT, value).apply()

    var lastPin: String?
        get() = prefs.getString(KEY_LAST_PIN, null)
        private set(value) = prefs.edit().putString(KEY_LAST_PIN, value).apply()

    /** The answer seq counter; it only ever increases, across app restarts. */
    var answerSeq: Long
        get() = prefs.getLong(KEY_ANSWER_SEQ, 0L)
        set(value) = prefs.edit().putLong(KEY_ANSWER_SEQ, value).apply()

    fun rememberSession(ip: String, port: Int, pin: String) {
        prefs.edit()
            .putString(KEY_LAST_IP, ip)
            .putInt(KEY_LAST_PORT, port)
            .putString(KEY_LAST_PIN, pin)
            .apply()
    }

    fun clearSession() {
        // Keep the deviceToken: it is the device's identity, not the session's.
        prefs.edit()
            .remove(KEY_STUDENT_ID)
            .remove(KEY_LAST_IP)
            .remove(KEY_LAST_PORT)
            .remove(KEY_LAST_PIN)
            .apply()
    }

    private companion object {
        const val KEY_TOKEN = "device_token"
        const val KEY_STUDENT_ID = "student_id"
        const val KEY_NAME = "name"
        const val KEY_PHOTO_DATA = "photo_data"
        const val KEY_PHOTO_MIME = "photo_mime"
        const val KEY_LAST_IP = "last_ip"
        const val KEY_LAST_PORT = "last_port"
        const val KEY_LAST_PIN = "last_pin"
        const val KEY_ANSWER_SEQ = "answer_seq"
    }
}
