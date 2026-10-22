package com.student.quiz.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.student.quiz.data.AnswerCodec
import com.student.quiz.data.AnswerQueue
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.FinishFlow
import com.student.quiz.data.KeyValueStore
import com.student.quiz.data.LockCommand
import com.student.quiz.data.LockController
import com.student.quiz.data.LockEvent
import com.student.quiz.data.LockState
import com.student.quiz.data.Question
import com.student.quiz.data.QuizClock
import com.student.quiz.data.QuizNav
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the app is right now. */
sealed interface Screen {
    data object Join : Screen
    data object Scan : Screen
    data class Lobby(val name: String) : Screen
    data class Quiz(val name: String) : Screen
    /** Step 5: submitted. No answers can be changed after this. */
    data class Finished(val name: String) : Screen
    data object Ended : Screen
}

/** Everything the quiz screen needs for the running quiz. */
data class QuizUiState(
    val title: String = "",
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val offsetMs: Long = 0,
    val endsAt: Long = 0,
    val paused: Boolean = false,
    /** Local answer per qid, including answers not yet acked. */
    val answers: Map<String, AnswerValue> = emptyMap(),
    /** qids whose latest answer is still waiting for an ack. */
    val unacked: Set<String> = emptySet(),
    /** qids the server rejected with BAD_ANSWER. */
    val failed: Set<String> = emptySet(),
    /** qids the student has been on at least once, whether or not they answered. */
    val visited: Set<String> = emptySet(),
) {
    val current: Question? get() = questions.getOrNull(currentIndex)
    fun isAnswered(question: Question): Boolean = AnswerCodec.isAnswered(question, answers[question.qid])

    /** Chip state for a question: answered, seen but unanswered, or unseen. */
    fun chipState(question: Question): QuizNav.ChipState =
        QuizNav.chipState(question, isAnswered(question), question.qid in visited)

    /** Index of the first question with no answer, or null when all are answered. */
    fun firstUnansweredIndex(): Int? = QuizNav.firstUnanswered(questions, answers)

    /** How many questions still have no answer. */
    fun unansweredCount(): Int = FinishFlow.unansweredCount(questions, answers)
}

data class MemeState(
    val memeId: String,
    val bitmap: android.graphics.Bitmap,
    val durationMs: Long,
)

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
    val profilePhoto: com.student.quiz.data.ProfilePhoto? = null,
    val conn: ConnState? = null,
    val quiz: QuizUiState? = null,
    val meme: MemeState? = null,
    /** Step 5: the finish confirmation dialog (null when closed). */
    val finishDialog: Int? = null,
    /** Step 5: waiting for the unacked queue to drain before sending finish. */
    val savingAnswers: Boolean = false,
    /** Step 5: shown when the queue did not drain within 10 s. */
    val saveError: String? = null,
    /** Step 3: pin state for the quiz screen and the paused screen. */
    val lock: LockState = LockState(
        running = false,
        lockMode = false,
        pinned = false,
        paused = false,
        pausedReason = null,
        hasFocus = true,
        stopped = false,
        canResume = true,
        needsPin = false,
    ),
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

    // ---------- Step 3: lock and pause ----------

    /**
     * The decision logic is pure: it only says what to send and when to pin.
     * MainActivity performs the Android side (startLockTask/stopLockTask,
     * FLAG_SECURE) and reports lifecycle events back through [onLockEvent].
     */
    private val lock = LockController()

    /** Pin and unpin requests for MainActivity. Focus messages stay here. */
    private val _pinCommands = MutableSharedFlow<LockCommand>(extraBufferCapacity = 8)
    val pinCommands: SharedFlow<LockCommand> = _pinCommands.asSharedFlow()

    /** Runs the grace window after startLockTask; cancelled on any re-pin. */
    private var graceJob: Job? = null

    /** The qid of the most recently sent answer, for BAD_ANSWER reporting. */
    private var lastSentQid: String? = null

    private val _ui = MutableStateFlow(
        JoinUiState(
            name = store.name.orEmpty(),
            profilePhoto = store.profilePhoto,
        ),
    )
    val ui: StateFlow<JoinUiState> = _ui.asStateFlow()

    fun updateProfilePhoto(photo: com.student.quiz.data.ProfilePhoto?) {
        store.profilePhoto = photo
        _ui.update { it.copy(profilePhoto = photo) }
    }

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
        var rawIp = state.ip.trim()
        if (rawIp.contains("://")) {
            rawIp = rawIp.substringAfter("://")
        }
        var cleanIp = rawIp
        var manualPort = state.port.toIntOrNull()
        if (rawIp.contains(':') && !rawIp.startsWith('[')) {
            val parts = rawIp.split(':')
            cleanIp = parts[0]
            val parsedPort = parts.getOrNull(1)?.toIntOrNull()
            if (parsedPort != null && parsedPort in 1..65535) {
                manualPort = parsedPort
            }
        }
        val port = manualPort
        when {
            state.name.isBlank() -> _ui.update { it.copy(error = "Enter your name first.") }
            cleanIp.isEmpty() -> _ui.update { it.copy(error = "Enter the instructor's IP address.") }
            port == null || port !in 1..65535 ->
                _ui.update { it.copy(error = "Enter a valid port, e.g. 8080.") }
            state.pin.length != 4 -> _ui.update { it.copy(error = "The PIN is 4 digits.") }
            else -> {
                _ui.update { it.copy(status = "Joining $cleanIp:$port…") }
                connect(cleanIp, port, state.pin)
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
        // A fresh join means we may have been offline: resend unacked answers,
        // and retry a held finish on the new socket.
        rejoinJob = viewModelScope.launch {
            conn.rejoins.collect {
                resendUnacked()
                if (_ui.value.savingAnswers) maybeSendFinish()
            }
        }
    }

    private val memeCache = mutableMapOf<String, android.graphics.Bitmap>()
    private var memeHideJob: Job? = null

    // ---------- Step 2: the running quiz ----------

    private fun onServerMessage(message: Protocol.ServerMessage) {
        when (message) {
            is Protocol.ServerMessage.QuizStartMessage -> onQuizStart(message.quiz)
            is Protocol.ServerMessage.AnswersState -> onAnswersState(message.answers)
            is Protocol.ServerMessage.Ack -> onAck(message.qid, message.seq)
            is Protocol.ServerMessage.QuizEnd -> endQuiz()
            is Protocol.ServerMessage.Paused -> onLockEvent(LockEvent.Paused(message.reason))
            is Protocol.ServerMessage.Resumed -> {
                onLockEvent(LockEvent.Resumed)
                // Answers typed while paused are still queued with their seq.
                resendUnacked()
            }
            is Protocol.ServerMessage.Lock -> onLockEvent(LockEvent.LockChanged(message.on))
            is Protocol.ServerMessage.TimeUpdate -> onTimeUpdate(message.endsAt, message.serverTime, message.paused)
            is Protocol.ServerMessage.MemePop -> onMemePop(
                message.memeId, message.path, message.mime, message.width, message.height, message.durationMs
            )
            is Protocol.ServerMessage.MemeHide -> onMemeHide()
            is Protocol.ServerMessage.Finished -> onFinished()
            is Protocol.ServerMessage.Failure -> onQuizError(message.code)
            else -> Unit
        }
    }

    private fun onMemePop(memeId: String, path: String, mime: String, width: Int, height: Int, durationMs: Long) {
        memeHideJob?.cancel()
        val cached = memeCache[memeId]
        if (cached != null) {
            _ui.update { it.copy(meme = MemeState(memeId, cached, durationMs)) }
            scheduleMemeHide(durationMs)
            return
        }
        viewModelScope.launch {
            val conn = connection ?: return@launch
            val bytes = conn.fetchBytes(path) ?: return@launch
            val bitmap = runCatching {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrElse {
                Log.w(TAG, "Failed to decode meme bitmap for $memeId: ${it.message}")
                null
            } ?: return@launch
            memeCache[memeId] = bitmap
            _ui.update { it.copy(meme = MemeState(memeId, bitmap, durationMs)) }
            scheduleMemeHide(durationMs)
        }
    }

    private fun onMemeHide() {
        memeHideJob?.cancel()
        _ui.update { it.copy(meme = null) }
    }

    private fun scheduleMemeHide(durationMs: Long) {
        memeHideJob = viewModelScope.launch {
            delay(durationMs)
            _ui.update { it.copy(meme = null) }
        }
    }

    private fun onQuizStart(quiz: QuizStart) {
        val phoneNow = System.currentTimeMillis()
        val offset = QuizClock.offsetMs(quiz.serverTime, phoneNow)
        _ui.update { state ->
            // On a reconnect quiz_start arrives again: rebuild the questions but
            // keep whatever the student already entered. A finished student
            // stays on the finished screen (answers_state then finished follow).
            val wasFinished = state.screen is Screen.Finished || state.lock.finished
            val keepIndex = state.quiz?.currentIndex
                ?.coerceAtMost((quiz.questions.size - 1).coerceAtLeast(0)) ?: 0
            state.copy(
                screen = if (wasFinished) Screen.Finished(state.screen.name()) else Screen.Quiz(state.screen.name()),
                quiz = QuizUiState(
                    title = quiz.title,
                    questions = quiz.questions,
                    currentIndex = keepIndex,
                    offsetMs = offset,
                    endsAt = quiz.endsAt,
                    paused = quiz.paused,
                    answers = state.quiz?.answers.orEmpty(),
                    unacked = state.quiz?.unacked.orEmpty(),
                    // Rejoining keeps what the student already saw.
                    visited = state.quiz?.visited.orEmpty() +
                        listOfNotNull(keepQuestionQid(state.quiz, keepIndex)),
                ),
            )
        }
        // Lock first: quiz_start carries lockMode, so the pin prompt goes up
        // before anything can report a focus change. On a reconnect this is
        // also where lockMode is refreshed from the server.
        onLockEvent(LockEvent.QuizStarted(quiz.lockMode))
    }

    private fun onTimeUpdate(endsAt: Long, serverTime: Long, paused: Boolean) {
        val phoneNow = System.currentTimeMillis()
        val offset = QuizClock.offsetMs(serverTime, phoneNow)
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(
                quiz = quiz.copy(
                    endsAt = endsAt,
                    offsetMs = offset,
                    paused = paused,
                ),
            )
        }
    }

    /** The qid of the question a rebuilt quiz should mark as visited, if any. */
    private fun keepQuestionQid(previous: QuizUiState?, index: Int): String? =
        previous?.questions?.getOrNull(index)?.qid

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
        // Finish waits for an empty queue: an ack may be the last one.
        if (_ui.value.savingAnswers) maybeSendFinish()
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
            // PAUSED is not a failure: the answer was not stored, so it stays
            // queued and goes out again after resumed (see resendUnacked).
            "PAUSED" -> Log.i(TAG, "Answer held: the student is paused")
            // FINISHED answers one of our own answers: the student finished on
            // another path (or rejoined finished), so show the done screen.
            "FINISHED" -> {
                Log.i(TAG, "Answer rejected: already finished")
                onFinished()
            }
        }
    }

    private fun endQuiz() {
        // The server is authoritative: the phone never ends the quiz itself.
        // Drop anything still waiting to send instead of flushing it: the quiz
        // is over, so the server would answer QUIZ_ENDED and loop back here.
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingSends.clear()
        finishJob?.cancel()
        finishJob = null
        // Releases the pin and clears FLAG_SECURE via the emitted StopPin.
        onLockEvent(LockEvent.QuizEnded)
        _ui.update { it.copy(screen = Screen.Ended, quiz = null, finishDialog = null, savingAnswers = false, saveError = null) }
    }

    // ---------- Step 5: finish ----------

    /** The finish job: waits for the queue to drain, then gives up after 10 s. */
    private var finishJob: Job? = null

    /** When the current drain wait started (epoch ms). */
    private var finishWaitStartMs: Long = 0L

    /** Opens the confirmation dialog with the current unanswered count. */
    fun openFinishDialog() {
        val quiz = _ui.value.quiz ?: return
        if (_ui.value.screen !is Screen.Quiz) return
        if (_ui.value.lock.finished) return
        // Flush anything still on the debounce timer so N is exact and every
        // typed answer is queued before the drain wait starts.
        flushPendingSends()
        val count = _ui.value.quiz?.unansweredCount() ?: quiz.unansweredCount()
        _ui.update { it.copy(finishDialog = count, saveError = null) }
    }

    fun closeFinishDialog() {
        _ui.update { it.copy(finishDialog = null) }
    }

    /**
     * From the dialog: jump to the first unanswered question. This replaces
     * the old Done behavior, which no longer exists.
     */
    fun showUnanswered() {
        val target = _ui.value.quiz?.firstUnansweredIndex() ?: return
        _ui.update { it.copy(finishDialog = null) }
        goTo(target)
    }

    /**
     * From the dialog: send finish, but only once the unacked queue is empty
     * (answers_state ordering and the 10 s give-up are handled by [maybeSendFinish]
     * and the timeout below).
     */
    fun confirmFinish() {
        if (_ui.value.screen !is Screen.Quiz) return
        if (_ui.value.lock.finished) return
        if (_ui.value.lock.paused) {
            _ui.update { it.copy(saveError = "You are paused. Ask to resume, then finish.") }
            return
        }
        flushPendingSends()
        resendUnacked()
        finishWaitStartMs = System.currentTimeMillis()
        _ui.update { it.copy(finishDialog = null, savingAnswers = true, saveError = null) }
        finishJob?.cancel()
        finishJob = viewModelScope.launch {
            delay(FinishFlow.SAVE_TIMEOUT_MS)
            // Still waiting: the queue never drained, so stop and say so.
            if (_ui.value.savingAnswers && !queueIsDrained()) {
                _ui.update {
                    it.copy(
                        savingAnswers = false,
                        saveError = "Still saving your answers. Check the connection and try again.",
                    )
                }
            }
        }
        maybeSendFinish()
    }

    /** Sends finish now if the queue is empty; called on confirm and on ack. */
    private fun maybeSendFinish() {
        if (!_ui.value.savingAnswers) return
        if (_ui.value.lock.finished) {
            finishJob?.cancel()
            finishJob = null
            _ui.update { it.copy(savingAnswers = false) }
            return
        }
        if (FinishFlow.timedOut(finishWaitStartMs, System.currentTimeMillis()) && !queueIsDrained()) {
            finishJob?.cancel()
            finishJob = null
            _ui.update {
                it.copy(
                    savingAnswers = false,
                    saveError = "Still saving your answers. Check the connection and try again.",
                )
            }
            return
        }
        if (!queueIsDrained()) return
        if (connection?.sendMessage(Protocol.finish()) != true) {
            // No socket: keep waiting for the timeout; the ack path retries.
            Log.i(TAG, "Finish held: no socket")
            return
        }
        finishJob?.cancel()
        finishJob = null
        // The server confirms with finished; the screen switches there.
        _ui.update { it.copy(savingAnswers = false) }
    }

    private fun queueIsDrained(): Boolean = FinishFlow.isDrained(queue.unackedQids())

    /** Full-screen "You're done": no answers can be changed after this. */
    private fun onFinished() {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingSends.clear()
        finishJob?.cancel()
        finishJob = null
        // Releases the pin via StopPin; running stays true so FLAG_SECURE holds.
        onLockEvent(LockEvent.Finished)
        _ui.update {
            it.copy(
                screen = Screen.Finished(it.screen.name()),
                finishDialog = null,
                savingAnswers = false,
                saveError = null,
            )
        }
    }

    /** Flushes every debounced answer into the queue without clearing any. */
    private fun flushPendingSends() {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingSends.toMap().forEach { (qid, value) -> sendNow(qid, value) }
        pendingSends.clear()
    }

    // ---------- Answering ----------

    /** Called on every input change: sends now, or after ~500 ms of quiet. */
    fun onAnswer(question: Question, value: AnswerValue, immediate: Boolean = false) {
        // Step 5: no answers can be changed after finish.
        if (!FinishFlow.canSendAnswer(_ui.value.lock.finished)) return
        if (_ui.value.screen is Screen.Finished) return
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
        if (_ui.value.lock.finished) return
        if (_ui.value.screen is Screen.Finished) return
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
        if (_ui.value.lock.finished) return
        if (_ui.value.screen is Screen.Finished) return
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(failed = quiz.failed - question.qid))
        }
        _ui.value.quiz?.answers?.get(question.qid)?.let { sendNow(question.qid, it) }
    }

    private fun sendNow(qid: String, value: AnswerValue) {
        // Step 5: a finished student never sends answers.
        if (_ui.value.lock.finished) return
        if (_ui.value.screen is Screen.Finished) return
        val number = seq.next()
        lastSentQid = qid
        queue.add(qid, number, value)
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(unacked = queue.unackedQids()))
        }
        // While paused nothing is sent: the entry stays queued with its seq and
        // goes out after resumed (resendUnacked), so no seq is ever wasted.
        if (_ui.value.lock.paused) {
            Log.i(TAG, "Answer for $qid held: the student is paused")
            return
        }
        val json = Protocol.answer(qid, AnswerCodec.toJson(value), number)
        if (connection?.sendAnswer(json) != true) {
            // No socket: it stays queued and goes out on the next join.
            Log.i(TAG, "Answer for $qid held until reconnect")
        }
    }

    /**
     * Resends the latest unacked value per question after a reconnect or after
     * resumed. Reusing the original seq is safe: the protocol says an answer
     * with seq <= the last one seen is ignored but still acked.
     */
    private fun resendUnacked() {
        if (_ui.value.lock.paused) return
        queue.pendingPerQuestion().forEach { entry ->
            val json = Protocol.answer(entry.qid, AnswerCodec.toJson(entry.value), entry.seq)
            connection?.sendAnswer(json)
        }
    }

    // ---------- Step 3: lock and pause ----------

    /**
     * The one entry point for everything that changes lock or pause state.
     * MainActivity feeds lifecycle and pin state in; it only carries out the
     * StartPin / StopPin commands, everything else is sent from here.
     */
    fun onLockEvent(event: LockEvent) {
        val commands = lock.on(event)
        _ui.update { it.copy(lock = lock.state()) }
        commands.forEach(::carryOut)
    }

    private fun carryOut(command: LockCommand) {
        when (command) {
            is LockCommand.FocusLost -> sendQuietly(Protocol.focusLost(command.reason))
            LockCommand.FocusGained -> sendQuietly(Protocol.focusGained())
            // MainActivity owns the Android side of pinning.
            LockCommand.StartPin -> {
                _pinCommands.tryEmit(command)
                startGraceWindow()
            }
            LockCommand.StopPin -> {
                graceJob?.cancel()
                graceJob = null
                _pinCommands.tryEmit(command)
            }
        }
    }

    /**
     * The pin confirmation prompt (and permission dialogs) take the window
     * focus away for a moment. Nothing is reported until the grace window is
     * over, so the prompt itself never pauses the student.
     */
    private fun startGraceWindow() {
        graceJob?.cancel()
        graceJob = viewModelScope.launch {
            delay(LockController.GRACE_MS)
            onLockEvent(LockEvent.GraceElapsed)
        }
    }

    /**
     * Focus messages are informational: the server only logs them, so a
     * dropped socket must not turn into an error on the student's screen.
     */
    private fun sendQuietly(json: String) {
        val ok = connection?.sendMessage(json) == true
        if (!ok) Log.i(TAG, "Focus message held: no socket")
    }

    /** MainActivity: onWindowFocusChanged(hasFocus). */
    fun onWindowFocusChanged(hasFocus: Boolean) = onLockEvent(LockEvent.WindowFocus(hasFocus))

    /** MainActivity: onStop / onPause. */
    fun onStopped(stopped: Boolean) = onLockEvent(LockEvent.Stopped(stopped))

    /** MainActivity: ActivityManager.getLockTaskModeState() != NONE. */
    fun onPinState(pinned: Boolean) = onLockEvent(LockEvent.PinState(pinned))

    /** MainActivity: while a permission dialog is showing. */
    fun onPermissionDialog(open: Boolean) = onLockEvent(LockEvent.PermissionDialog(open))

    /** The "Ask to resume" button. It requests only; the instructor decides. */
    fun requestResume() {
        val state = _ui.value.lock
        // Enabled state already reflects this, but a stale tap must not send.
        if (!state.paused || !state.canResume) return
        sendQuietly(Protocol.resumeRequest())
    }

    /** The "Pin again" button on the paused screen. */
    fun pinAgain() {
        val state = _ui.value.lock
        // Only meaningful while locked and no longer pinned: pinning without
        // lock on would be theatre, and the controller refuses it too.
        if (!state.paused || !state.needsPin) return
        lock.requestPin().forEach(::carryOut)
        _ui.update { it.copy(lock = lock.state()) }
    }

    /** True when the back button must be ignored: the quiz is pinned. */
    fun blocksBackButton(): Boolean =
        _ui.value.lock.running && _ui.value.lock.lockMode && _ui.value.lock.pinned

    /** True while the quiz is running, whatever the pause state: FLAG_SECURE. */
    fun quizRunning(): Boolean = _ui.value.lock.running

    /** Leaves the quiz (kick, closed session, explicit back to join). */
    fun leaveQuiz() = onLockEvent(LockEvent.QuizEnded)

    // ---------- Navigation ----------

    /**
     * Moves to another question. Every question is reachable: this never looks
     * at whether the current one is answered, so an unanswered question (a
     * matching one included) can never block Next, Previous or the chips.
     * Leaving only flushes a pending debounced answer; it never clears one.
     */
    private fun goTo(index: Int) {
        val question = _ui.value.quiz?.current
        question?.let(::onLeaveQuestion)
        _ui.update { state ->
            val quiz = state.quiz ?: return@update state
            val target = index.coerceIn(0, (quiz.questions.size - 1).coerceAtLeast(0))
            state.copy(
                quiz = quiz.copy(
                    currentIndex = target,
                    visited = quiz.visited + listOfNotNull(quiz.questions.getOrNull(target)?.qid),
                ),
            )
        }
    }

    /** Jumps to a question from its chip. */
    fun selectQuestion(index: Int) = goTo(index)

    fun nextQuestion() {
        val quiz = _ui.value.quiz ?: return
        if (!QuizNav.canGoNext(quiz.questions.size, quiz.currentIndex)) return
        goTo(QuizNav.nextIndex(quiz.questions.size, quiz.currentIndex))
    }

    fun previousQuestion() {
        val quiz = _ui.value.quiz ?: return
        if (!QuizNav.canGoPrevious(quiz.currentIndex)) return
        goTo(QuizNav.previousIndex(quiz.questions.size, quiz.currentIndex))
    }

    /**
     * Skip: move on without answering. Only a pending debounced answer is
     * flushed, so an answer already given is kept and never re-sent as empty.
     * On the last question Skip does nothing: finishing goes through the
     * Finish button and its confirmation dialog.
     */
    fun skipQuestion() {
        val quiz = _ui.value.quiz ?: return
        if (QuizNav.isLast(quiz.questions.size, quiz.currentIndex)) return
        nextQuestion()
    }

    /** The student's name, whichever screen we are on. */
    private fun Screen.name(): String = when (this) {
        is Screen.Lobby -> name
        is Screen.Quiz -> name
        is Screen.Finished -> name
        else -> _ui.value.name
    }

    /** Kick or a rejected join: show the message back on the Join screen. */
    fun backToJoin(message: String? = null) {
        teardownConnection()
        // A kick or a closed session leaves the quiz: release the pin.
        leaveQuiz()
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
        // Never leave the phone pinned after the ViewModel goes away.
        leaveQuiz()
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