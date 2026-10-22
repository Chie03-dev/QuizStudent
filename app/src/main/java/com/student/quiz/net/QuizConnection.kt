package com.student.quiz.net

import android.content.Context
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.student.quiz.data.StudentStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/** Everything the UI needs to know about the socket. */
sealed interface ConnState {
    data object Connecting : ConnState
    data class Joined(val name: String) : ConnState
    data object Reconnecting : ConnState
    data class Rejected(val message: String) : ConnState
}

/**
 * The single WebSocket client: joins a lobby, heartbeats every 3 s and
 * reconnects every 2 s with the same deviceToken when the socket drops.
 * Sockets are bound to the Wi-Fi network so a classroom AP without internet
 * does not push traffic onto mobile data.
 */
class QuizConnection(
    context: Context,
    private val store: StudentStore,
    private val host: String,
    private val port: Int,
    private val pin: String,
    private val name: String,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private val wifi = WifiNetworkBinder(appContext)
    private var client: OkHttpClient? = null
    private var socket: WebSocket? = null
    private var stopped = false

    private val _state = MutableStateFlow<ConnState>(ConnState.Connecting)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    // Every decoded server message except joined/error/kick, for the quiz UI.
    private val _events = MutableSharedFlow<Protocol.ServerMessage>(
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<Protocol.ServerMessage> = _events.asSharedFlow()

    /** Sends an answer, or returns false if the socket is not open. */
    fun sendAnswer(json: String): Boolean = socket?.send(json) ?: false

    /**
     * Step 3 sends focus_lost, focus_gained and resume_request on the same
     * socket. They go out even while the student is paused, which is the whole
     * point: the server needs them to log and to decide who can resume.
     */
    fun sendMessage(json: String): Boolean = socket?.send(json) ?: false

    // Increments every time the socket joins again (reconnect or Wi-Fi recovery).
    // The quiz UI watches it to resend unacked answers.
    private val _rejoins = MutableStateFlow(0)
    val rejoins: StateFlow<Int> = _rejoins.asStateFlow()

    private val heartbeat = object : Runnable {
        override fun run() {
            socket?.send(Protocol.heartbeat())
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private val retry = object : Runnable {
        override fun run() {
            if (stopped) return
            _state.value = ConnState.Reconnecting
            open()
        }
    }

    init {
        wifi.onAvailable = { network -> onWifiAvailable(network) }
        wifi.onLost = {
            // The socket is dead; force a reconnect when Wi-Fi comes back.
            Log.i(TAG, "Wi-Fi lost")
            socket?.cancel()
        }
        wifi.acquire()
        // The network callback can be slow, so also try immediately.
        open()
    }

    private fun onWifiAvailable(network: Network?) {
        if (stopped) return
        if (socket == null) open() else rebuildClient(network)
    }

    private fun buildClient(network: Network?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // kept alive by hb
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
        if (network != null) {
            // Bind the TCP socket to the Wi-Fi network.
            builder.socketFactory(network.socketFactory)
            builder.dns(
                object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> =
                        network.getAllByName(hostname).toList()
                },
            )
        }
        return builder.build()
    }

    private fun rebuildClient(network: Network?) {
        socket?.cancel()
        client?.dispatcher?.executorService?.shutdown()
        client = buildClient(network)
        open()
    }

    private fun open() {
        if (stopped) return
        val ok = client ?: buildClient(wifi.currentNetwork).also { client = it }
        val request = Request.Builder().url("ws://$host:$port${Protocol.PATH}").build()
        if (socket == null && _state.value !is ConnState.Joined) {
            _state.value = ConnState.Connecting
        }
        socket = ok.newWebSocket(request, Listener())
    }

    fun stop() {
        stopped = true
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacks(retry)
        socket?.close(NORMAL_CLOSURE, "leaving")
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        wifi.release()
    }

    /** Fetches a meme image or resource over HTTP from the same server. */
    suspend fun fetchBytes(path: String): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val ok = client ?: buildClient(wifi.currentNetwork).also { client = it }
        val url = "http://$host:$port$path"
        val request = Request.Builder().url(url).build()
        runCatching {
            ok.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrElse {
            Log.w(TAG, "Failed to fetch meme from $url: ${it.message}")
            null
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (stopped) return
            // Same deviceToken on every join: the server restores the studentId.
            webSocket.send(Protocol.join(pin, name, store.deviceToken, store.profileImageBase64))
            handler.removeCallbacks(heartbeat)
            handler.postDelayed(heartbeat, HEARTBEAT_MS)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (stopped) return
            // Matching and connect carry the most per-item data, so a missing
            // item field shows up as a blank screen. Log the raw question so
            // the exact wire shape can be compared with docs/protocol.md.
            if (text.contains("\"matching\"") || text.contains("\"connect\"")) {
                Log.i(TAG, "RAW matching/connect question: $text")
            }
            val message = runCatching { Protocol.parse(text) }.getOrNull()
            if (message == null) {
                Log.w(TAG, "Unparseable message: $text")
                return
            }
            when (message) {
                is Protocol.ServerMessage.Joined -> {
                    _rejoins.value += 1
                    _state.value = ConnState.Joined(name)
                    store.studentId = message.studentId
                    store.name = name
                    message.avatar?.let { store.profileImageBase64 = it }
                    store.rememberSession(host, port, pin)
                }
                is Protocol.ServerMessage.Failure -> {
                    // Step 1 join errors end the session; step 2 errors (QUIZ_ENDED,
                    // BAD_ANSWER, UNKNOWN_QUESTION) belong to one answer, so they
                    // are handed to the quiz UI instead of dropping the socket.
                    if (message.code in JOIN_ERRORS) {
                        _state.value = ConnState.Rejected(Protocol.errorMessage(message.code))
                        stop()
                    } else {
                        _events.tryEmit(message)
                    }
                }
                is Protocol.ServerMessage.Kick -> {
                    _state.value = ConnState.Rejected("You were removed from the quiz.")
                    stop()
                }
                else -> _events.tryEmit(message) // quiz_start / answers_state / ack / quiz_end
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (stopped) return
            Log.i(TAG, "Socket failed: ${t.message}")
            scheduleRetry()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (stopped) return
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacks(retry)
        _state.value = ConnState.Reconnecting
        handler.postDelayed(retry, RETRY_MS)
    }

    companion object {
        private const val TAG = "QuizConnection"
        private const val HEARTBEAT_MS = 3_000L
        private const val RETRY_MS = 2_000L
        private const val NORMAL_CLOSURE = 1000
        private val JOIN_ERRORS = setOf("BAD_PIN", "NAME_TAKEN", "RATE_LIMITED", "CLOSED")
    }
}