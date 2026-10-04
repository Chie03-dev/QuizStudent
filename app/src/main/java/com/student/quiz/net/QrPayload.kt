package com.student.quiz.net

import org.json.JSONObject

/**
 * Parser for the lobby QR payload (docs/protocol.md "QR payload"):
 *   {"ip":"192.168.0.235","port":8080,"pin":"0042"}
 * It is bare JSON, not a URL. The PIN stays a String so leading zeros survive.
 */
object QrPayload {

    data class Lobby(val ip: String, val port: Int, val pin: String)

    fun parse(raw: String): Lobby {
        val text = raw.trim()
        require(text.startsWith("{")) { "That code is not a quiz lobby code." }
        val json = JSONObject(text)
        val ip = json.optString("ip").trim()
        require(ip.isNotEmpty()) { "The lobby code has no IP address." }
        val port = json.optInt("port", 0)
        require(port in 1..65535) { "The lobby code has an invalid port." }
        val pin = json.optString("pin").trim()
        require(pin.isNotEmpty()) { "The lobby code has no PIN." }
        return Lobby(ip, port, pin)
    }
}