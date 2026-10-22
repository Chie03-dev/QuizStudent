package com.student.quiz.data

import java.util.UUID
import org.json.JSONObject

/**
 * Avatar specification matching the instructor repo contract (shared/avatar.ts).
 * Validation bounds:
 *   hue: 0..360
 *   face: 0..6
 *   eyes: 0..8
 *   mouth: 0..6
 *   accessory: 0..4
 *   seed: non-empty string
 */
data class AvatarSpec(
    val seed: String,
    val hue: Int,
    val face: Int,
    val eyes: Int,
    val mouth: Int,
    val accessory: Int,
) {
    companion object {
        fun default(): AvatarSpec = AvatarSpec(
            seed = UUID.randomUUID().toString(),
            hue = 30,
            face = 0,
            eyes = 2, // open
            mouth = 1, // smile
            accessory = 0, // none
        )

        fun sanitize(
            seed: String?,
            hue: Int?,
            face: Int?,
            eyes: Int?,
            mouth: Int?,
            accessory: Int?,
        ): AvatarSpec {
            val validSeed = if (seed.isNullOrBlank()) UUID.randomUUID().toString() else seed
            val validHue = (hue ?: 30).coerceIn(0, 360)
            val validFace = (face ?: 0).coerceIn(0, 6)
            val validEyes = (eyes ?: 2).coerceIn(0, 8)
            val validMouth = (mouth ?: 1).coerceIn(0, 6)
            val validAccessory = (accessory ?: 0).coerceIn(0, 4)
            return AvatarSpec(
                seed = validSeed,
                hue = validHue,
                face = validFace,
                eyes = validEyes,
                mouth = validMouth,
                accessory = validAccessory,
            )
        }

        fun fromJson(json: JSONObject?): AvatarSpec? {
            if (json == null) return null
            val seed = json.optString("seed")
            if (seed.isBlank()) return null
            return sanitize(
                seed = seed,
                hue = json.optInt("hue", 30),
                face = json.optInt("face", 0),
                eyes = json.optInt("eyes", 2),
                mouth = json.optInt("mouth", 1),
                accessory = json.optInt("accessory", 0),
            )
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("seed", seed)
        .put("hue", hue)
        .put("face", face)
        .put("eyes", eyes)
        .put("mouth", mouth)
        .put("accessory", accessory)
}
