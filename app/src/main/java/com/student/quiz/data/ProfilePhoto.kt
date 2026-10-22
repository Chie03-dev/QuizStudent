package com.student.quiz.data

import org.json.JSONObject

/**
 * Profile photo matching instructor repo contract (shared/types.ts & profilePhoto.ts).
 * Constraints:
 *   data: raw base64 string only — NO data:image/jpeg;base64, prefix.
 *   mime: "image/jpeg" or "image/png".
 *   MAX_PHOTO_BASE64_LEN = 10000 (~7.5 KB decoded).
 */
data class ProfilePhoto(
    val data: String,
    val mime: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("data", data)
        .put("mime", mime)

    companion object {
        const val MAX_LEN = 10000

        fun fromJson(json: JSONObject?): ProfilePhoto? {
            if (json == null) return null
            val data = json.optString("data")
            val mime = json.optString("mime")
            if (data.isBlank() || data.length > MAX_LEN) return null
            return ProfilePhoto(data = data, mime = mime.ifBlank { "image/jpeg" })
        }
    }
}
