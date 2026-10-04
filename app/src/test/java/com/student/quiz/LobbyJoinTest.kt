package com.student.quiz

import com.student.quiz.net.Protocol
import com.student.quiz.net.QrPayload
import com.student.quiz.net.ServiceDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    @Test
    fun `parses a lobby payload and keeps leading zeros in the PIN`() {
        val lobby = QrPayload.parse("""{"ip":"10.0.0.5","port":8081,"pin":"0042"}""")
        assertEquals("10.0.0.5", lobby.ip)
        assertEquals(8081, lobby.port)
        assertEquals("0042", lobby.pin)
    }

    @Test
    fun `rejects a url instead of bare JSON`() {
        assertThrows(IllegalArgumentException::class.java) {
            QrPayload.parse("ws://192.168.0.235:8080/ws")
        }
    }

    @Test
    fun `rejects a payload without a port`() {
        assertThrows(IllegalArgumentException::class.java) {
            QrPayload.parse("""{"ip":"10.0.0.5","pin":"0042"}""")
        }
    }
}

class ProtocolTest {

    @Test
    fun `join sends pin name and deviceToken`() {
        val raw = Protocol.join("0042", "Ada", "token-1")
        assertTrue(raw.contains("\"t\":\"join\""))
        assertTrue(raw.contains("\"pin\":\"0042\""))
        assertTrue(raw.contains("\"name\":\"Ada\""))
        assertTrue(raw.contains("\"deviceToken\":\"token-1\""))
    }

    @Test
    fun `parses a joined message`() {
        val message = Protocol.parse("""{"t":"joined","d":{"studentId":"s1","deviceToken":"t"}}""")
        assertTrue(message is Protocol.ServerMessage.Joined)
        assertEquals("s1", (message as Protocol.ServerMessage.Joined).studentId)
    }

    @Test
    fun `error codes have readable messages`() {
        assertTrue(Protocol.errorMessage("BAD_PIN").contains("Wrong PIN"))
        assertTrue(Protocol.errorMessage("NAME_TAKEN").contains("name is already"))
        assertTrue(Protocol.errorMessage("RATE_LIMITED").contains("minute"))
        assertTrue(Protocol.errorMessage("CLOSED").contains("closed"))
    }
}

class ServiceDiscoveryNameTest {

    @Test
    fun `matches quiz instance names with and without a trailing dot`() {
        assertTrue(ServiceDiscovery.nameMatchesPin("quiz-0042", "0042"))
        assertTrue(ServiceDiscovery.nameMatchesPin("quiz-0042.", "0042"))
        assertTrue(ServiceDiscovery.nameMatchesPin("QUIZ-0042", "0042"))
    }

    @Test
    fun `does not match another PIN`() {
        assertEquals(false, ServiceDiscovery.nameMatchesPin("quiz-1234", "0042"))
        assertEquals(false, ServiceDiscovery.nameMatchesPin(null, "0042"))
    }
}