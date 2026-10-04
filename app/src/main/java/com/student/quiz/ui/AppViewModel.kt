package com.student.quiz.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.student.quiz.data.AnswerCodec
import com.student.quiz.data.AnswerQueue
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.KeyValueStore
import com.student.quiz.data.Question
import com.student.quiz.data.QuizClock
import com.student.quiz.data.QuizStart
import com.student.quiz.data.SeqCounter
import com.student.quiz.data.StudentStore
import com.student.quiz.net.ConnState
import com.student.quiz.net.Protocol
import com.student.quiz.net.QuizConnection
import com.student.quiz.net.QrPayload
import com.student.quiz.net.ServiceDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the app is right now. */
sealed interface Screen {
    data object Join : Screen
    data object Scan : Screen
    data class Lobby(val name: String) : Screen
    data class Quiz(val name: String) : Screen
    data object Ended : Screen
}

/** Everything the quiz screen needs for the running quiz. */
data class QuizUiState(
    val title: String = "",
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val offsetMs: Long = 0,
    val endsAt: Long = 0,
    /** Local answer per qid, including answers not yet acked. */
    val answers: Map<String, AnswerValue> = emptyMap(),
    /** qids whose latest answer is still waiting for an ack. */
    val unacked: Set<String> = emptySet(),
    /** qids the server rejected with BAD_ANSWER. */
    val failed: Set<String> = emptySet(),
) {
    val current: Question? get() = questions.getOrNull(currentIndex)
    fun isAnswered(question: Question): Boolean = AnswerCodec.isAnswered(question, answers[question.qid])
}

data class JoinUiState(
    val screen: Screen = Screen.Join,
    val name: String = "",
    val pin: String = "",
    val ip: String = "",
    val port: String = "",
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val showManualEntry: Boolean = false,
    val conn: ConnState? = null,
    val quiz: QuizUiState? = null,
)

/** Drives step 1 (join a lobby) and step 2 (take part in a running quiz). */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = StudentStore(app)
    private val discovery = ServiceDiscovery(app)
    private var connection: QuizConnection? = null
    private var connJob: Job? = null
    private var eventJob: Job? = null
    private var rejoinJob: Job? = null

    /** seq persists across app restarts, per protocol.md. */
    private val seq = SeqCounter(
        object : KeyValueStore {
            override fun getLong(key: String) = store.answerSeq
            override fun putLong(key: String, value: Long) {
                store.answerSeq = value
            }
        },
    )

    /** Answers sent but not yet acked, resent after a reconnect. */
    private val queue = AnswerQueue()

    // One debounce job per question so typing does not flood the socket.
    private val debounceJobs = mutableMapOf<String, Job>()

    /** qid -> value waiting for its debounce timer to fire. */
    private val pendingSends = mutableMapOf<String, AnswerValue>()

    /** The qid of the most recently sent answer, for BAD_ANSWER reporting. */
    private var lastSentQid: String? = null

    private val _ui = MutableStateFlow(
        // Restore the name after a restart so rejoining is one tap.
        JoinUiState(name = store.name.orEmpty()),
    )
    val ui: StateFlow<JoinUiState> = _ui.asStateFlow()

    fun onNameChange(value: String) = _ui.update { it.copy(name = value, error = null) }

    // PIN is kept as a String so leading zeros survive.
    fun onPinChange(value: String) = _ui.update {
        it.copy(pin = value.filter(Char::isDigit).take(4), error = null)
    }

    fun onIpChange(value: String) = _ui.update { it.copy(ip = value, error = null) }

    fun onPortChange(value: String) = _ui.update {
        it.copy(port = value.filter(Char::isDigit).take(5), error = null)
    }

    fun showManualEntry(show: Boolean) =
        _ui.update { it.copy(showManualEntry = show, status = null, error = null) }

    fun openScanner() {
        if (_ui.value.name.isBlank()) {
            _ui.update { it.copy(error = "Enter your name first.") }
            return
        }
        _ui.update { it.copy(screen = Screen.Scan, error = null, status = null) }
    }

    fun onCameraDenied() = _ui.update {
        it.copy(error = "Camera access is needed to scan the QR code. You can join with the PIN instead.")
    }

    fun closeScanner() =
        _ui.update { if (it.screen is Screen.Scan) it.copy(screen = Screen.Join) else it }

    /** Called by the scanner with the raw QR string. */
    fun onQrScanned(raw: String) {
        val lobby = runCatching { QrPayload.parse(raw) }.getOrElse {
            _ui.update { s -> s.copy(error = it.message ?: "Could not read that QR code.") }
            return
        }
        _ui.update { it.copy(screen = Screen.Join, pin = lobby.pin, status = "Scanned. Joining…") }
        connect(lobby.ip, lobby.port, lobby.pin)
    }

    /** Joins by browsing for quiz-<PIN> over mDNS. */
    fun joinByPin() {
        val state = _ui.value
        if (state.name.isBlank()) {
            _ui.update { it.copy(error = "Enter your name first.") }
            return
        }
        if (state.pin.length != 4) {
            _ui.update { it.copy(error = "The PIN is 4 digits.") }
            return
        }
        if (state.showManualEntry) {
            joinManually()
            return
        }
        _ui.update { it.copy(busy = true, error = null, status = "Looking for quiz ${state.pin}…") }
        discovery.findByPin(state.pin) { endpoint ->
            if (endpoint == null) {
                _ui.update {
                    it.copy(
                        busy = false,
                        status = null,
                        showManualEntry = true,
                        error = "No quiz found for PIN ${state.pin}. Check the PIN, make sure " +
                            "you are on the same Wi-Fi as the instructor, and turn off any VPN " +
                            "(a VPN blocks mDNS discovery). You can also type the address below.",
                    )
                }
            } else {
                _ui.update { it.copy(status = "Found the quiz. Joining…") }
                connect(endpoint.host, endpoint.port, endpoint.pin)
            }
        }
    }

    /** Last resort: type the instructor's address by hand. */
    fun joinManually() {
        val state = _ui.value
        val ip = state.ip.trim()
        val port = state.port.toIntOrNull()
        when {
            state.name.isBlank() -> _ui.update { it.copy(error = "Enter your name first.") }
            ip.isEmpty() -> _ui.update { it.copy(error = "Enter the instructor's IP address.") }
            port == null || port !in 1..65535 ->
                _ui.update { it.copy(error = "Enter a valid port, e.g. 8080.") }
            state.pin.length != 4 -> _ui.update { it.copy(error = "The PIN is 4 digits.") }
            else -> {
                _ui.update { it.copy(status = "Joining $ip:$port…") }
                connect(ip, port, state.pin)
            }
        }
    }

    private fun connect(host: String, port: Int, pin: String) {
        discovery.stop()
        teardownConnection()
        // A new session must not inherit the previous one's pending answers.
        queue.clear()
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingSends.clear()
        lastSentQid = null
        val name = _ui.value.name.trim()
        store.rememberSession(host, port, pin)
        val conn = QuizConnection(getApplication(), store, host, port, pin, name)
        connection = conn
        _ui.update {
            it.copy(busy = false, screen = Screen.Lobby(name), status = null, error = null)
        }
        connJob = viewModelScope.launch {
            conn.state.collect { s -> _ui.update { it.copy(conn = s) } }
        }
        eventJob = viewModelScope.launch {
            conn.events.collect(::onServerMessage)
        }
        // A fresh join means we may have been offline: resend unacked answers.
        rejoinJob = viewModelScope.launch {
            conn.rejoins.collect { resendUnacked() }
        }
    }

    // ---------- Step 2: the running quiz ----------

    private fun onServerMessage(message: Protocol.ServerMessage) {
        when (message) {
            is Protocol.ServerMessage.QuizStartMessage -> onQuizStart(message.quiz)
            is Protocol.ServerMessage.AnswersState -> onAnswersState(message.answers)
            is Protocol.ServerMessage.Ack -> onAck(message.qid, message.seq)
            is Protocol.ServerMessage.QuizEnd -> endQuiz()
            is Protocol.ServerMessage.Failure -> onQuizError(message.code)
            else -> Unit
        }
    }

    private fun onQuizStart(quiz: QuizStart) {
        val phoneNow = System.currentTimeMillis()
        val offset = QuizClock.offsetMs(quiz.serverTime, phoneNow)
        _ui.update { state ->
            // On a reconnect quiz_start arrives again: rebuild the questions but
            // keep whatever the student already entered.
            val keepIndex = state.quiz?.currentIndex
                ?.coerceAtMost((quiz.questions.size - 1).coerceAtLeast(0)) ?: 0
            state.copy(
                screen = Screen.Quiz(state.screen.name()),
                quiz = QuizUiState(
                    title = quiz.title,
                    questions = quiz.questions,
                    currentIndex = keepIndex,
                    offsetMs = offset,
                    endsAt = quiz.endsAt,
                    answers = state.quiz?.answers.orEmpty(),
                    unacked = state.quiz?.unacked.orEmpty(),
                ),
            )
        }
    }

    /** Fills in answers the server already has; never overwrites local input. */
    private fun onAnswersState(raw: Map<String, Any?>) {
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            val merged = quiz.answers.toMutableMap()
            raw.forEach { (qid, value) ->
                val decoded = AnswerCodec.fromJson(value) ?: return@forEach
                if (qid !in merged) merged[qid] = decoded
            }
            state.copy(quiz = quiz.copy(answers = merged))
        }
        resendUnacked()
    }

    private fun onAck(qid: String, ackSeq: Long) {
        queue.onAck(qid, ackSeq)
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(
                quiz = quiz.copy(
                    unacked = queue.unackedQids() - qid,
                    failed = quiz.failed - qid,
                ),
            )
        }
    }

    private fun onQuizError(code: String) {
        when (code) {
            "QUIZ_ENDED" -> endQuiz()
            "BAD_ANSWER" -> {
                // The error carries no qid, so flag the answer we sent last.
                Log.w(TAG, "BAD_ANSWER from the server")
                val qid = lastSentQid ?: return
                _ui.update { state ->
                    val quiz = state.quiz ?: return@update state
                    state.copy(quiz = quiz.copy(failed = quiz.failed + qid))
                }
            }
            "UNKNOWN_QUESTION" -> Log.w(TAG, "Server rejected a question id")
        }
    }

    private fun endQuiz() {
        // The server is authoritative: the phone never ends the quiz itself.
        // Drop anything still waiting to send instead of flushing it: the quiz
        // is over, so the server would answer QUIZ_ENDED and loop back here.
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingSends.clear()
        _ui.update { it.copy(screen = Screen.Ended, quiz = null) }
    }

    // ---------- Answering ----------

    /** Called on every input change: sends now, or after ~500 ms of quiet. */
    fun onAnswer(question: Question, value: AnswerValue, immediate: Boolean = false) {
        val qid = question.qid
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(answers = quiz.answers + (qid to value)))
        }
        if (immediate) {
            // An immediate send supersedes anything waiting on the debounce timer.
            debounceJobs.remove(qid)?.cancel()
            pendingSends.remove(qid)
            sendNow(qid, value)
        } else {
            debounceJobs[qid]?.cancel()
            pendingSends[qid] = value
            debounceJobs[qid] = viewModelScope.launch {
                delay(DEBOUNCE_MS)
                pendingSends.remove(qid)?.let { sendNow(qid, it) }
            }
        }
    }

    /** Called when the student leaves a question, so nothing is left unsent. */
    fun onLeaveQuestion(question: Question) {
        val qid = question.qid
        debounceJobs.remove(qid)?.cancel()
        val pending = pendingSends.remove(qid)
        if (pending != null) {
            sendNow(qid, pending)
        } else {
            // Nothing typed since the last send: retry it if it is still unacked.
            if (queue.isUnacked(qid)) {
                _ui.value.quiz?.answers?.get(qid)?.let { sendNow(qid, it) }
            }
        }
    }

    /** "Not saved, tap to retry" on a question the server rejected. */
    fun retry(question: Question) {
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(failed = quiz.failed - question.qid))
        }
        _ui.value.quiz?.answers?.get(question.qid)?.let { sendNow(question.qid, it) }
    }

    private fun sendNow(qid: String, value: AnswerValue) {
        val number = seq.next()
        lastSentQid = qid
        queue.add(qid, number, value)
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(unacked = queue.unackedQids()))
        }
        val json = Protocol.answer(qid, AnswerCodec.toJson(value), number)
        if (connection?.sendAnswer(json) != true) {
            // No socket: it stays queued and goes out on the next join.
            Log.i(TAG, "Answer for $qid held until reconnect")
        }
    }

    /** Resends the latest unacked value per question after a reconnect. */
    private fun resendUnacked() {
        queue.pendingPerQuestion().forEach { entry ->
            val json = Protocol.answer(entry.qid, AnswerCodec.toJson(entry.value), entry.seq)
            connection?.sendAnswer(json)
        }
    }

    // ---------- Navigation ----------

    fun selectQuestion(index: Int) {
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            if (index !in quiz.questions.indices) return@update state
            state.copy(quiz = quiz.copy(currentIndex = index))
        }
    }

    fun nextQuestion() {
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            val last = (quiz.questions.size - 1).coerceAtLeast(0)
            state.copy(quiz = quiz.copy(currentIndex = (quiz.currentIndex + 1).coerceAtMost(last)))
        }
    }

    fun previousQuestion() {
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(currentIndex = (quiz.currentIndex - 1).coerceAtLeast(0)))
        }
    }

    /** The student's name, whichever screen we are on. */
    private fun Screen.name(): String = when (this) {
        is Screen.Lobby -> name
        is Screen.Quiz -> name
        else -> _ui.value.name
    }

    /** Kick or a rejected join: show the message back on the Join screen. */
    fun backToJoin(message: String? = null) {
        teardownConnection()
        _ui.update {
            it.copy(
                screen = Screen.Join,
                busy = false,
                status = null,
                conn = null,
                error = message,
            )
        }
    }

    private fun teardownConnection() {
        connJob?.cancel()
        connJob = null
        eventJob?.cancel()
        eventJob = null
        rejoinJob?.cancel()
        rejoinJob = null
        connection?.stop()
        connection = null
    }

    override fun onCleared() {
        discovery.stop()
        teardownConnection()
        super.onCleared()
    }

    private companion object {
        const val TAG = "AppViewModel"
        const val DEBOUNCE_MS = 500L
    }
}

private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    while (true) {
        val old = value
        if (compareAndSet(old, block(old))) return
    }
}