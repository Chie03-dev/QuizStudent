import Foundation
import Combine
#if canImport(UIKit)
import UIKit
#endif

enum Screen: Equatable {
    case join
    case scan
    case lobby(name: String)
    case quiz(name: String)
    case finished(name: String)
    case ended
}

struct QuizUiState {
    var title: String = ""
    var questions: [Question] = []
    var currentIndex: Int = 0
    var offsetMs: Int64 = 0
    var endsAt: Int64 = 0
    var paused: Bool = false
    var answers: [String: AnswerValue] = [:]
    var unacked: Set<String> = []
    var failed: Set<String> = []
    var visited: Set<String> = []

    var current: Question? {
        guard questions.indices.contains(currentIndex) else { return nil }
        return questions[currentIndex]
    }

    func isAnswered(_ question: Question) -> Bool {
        return AnswerValue.isAnswered(question, answers[question.qid])
    }
}

struct LockState {
    var running: Bool = false
    var lockMode: Bool = false
    var pinned: Bool = false
    var paused: Bool = false
    var pausedReason: String? = nil
    var hasFocus: Bool = true
    var stopped: Bool = false
    var canResume: Bool = true
    var needsPin: Bool = false
    var finished: Bool = false
}

struct MemeState {
    var memeId: String
    var image: UIImage
    var durationMs: Int64
}

class AppViewModel: ObservableObject {
    private let store = StudentStore()
    private let discovery = ServiceDiscovery()
    private var connection: QuizConnection?
    private var cancellables = Set<AnyCancellable>()

    @Published var screen: Screen = .join
    @Published var name: String = ""
    @Published var pin: String = ""
    @Published var ip: String = ""
    @Published var port: String = ""
    @Published var busy: Bool = false
    @Published var status: String? = nil
    @Published var error: String? = nil
    @Published var showManualEntry: Bool = false
    @Published var connState: ConnState? = nil
    @Published var quizUiState: QuizUiState? = nil
    @Published var finishDialog: Int? = nil
    @Published var savingAnswers: Bool = false
    @Published var saveError: String? = nil
    @Published var lockState: LockState = LockState()
    @Published var memeState: MemeState? = nil

    private var memeCache = [String: UIImage]()
    private var memeHideTimer: Timer?
    private var pendingAnswers = [String: AnswerValue]()
    private var unackedQueue = [String: (value: AnswerValue, seq: Int64)]()
    private var lastSentQid: String? = nil

    init() {
        self.name = store.name ?? ""
        setupLifecycleObservers()
    }

    func onNameChange(_ value: String) {
        name = value
        error = nil
    }

    func onPinChange(_ value: String) {
        pin = String(value.filter(\.isNumber).prefix(4))
        error = nil
    }

    func onIpChange(_ value: String) {
        ip = value
        error = nil
    }

    func onPortChange(_ value: String) {
        port = String(value.filter(\.isNumber).prefix(5))
        error = nil
    }

    func showManualEntry(_ show: Bool) {
        showManualEntry = show
        status = nil
        error = nil
    }

    func openScanner() {
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            error = "Enter your name first."
            return
        }
        screen = .scan
        error = nil
        status = nil
    }

    func closeScanner() {
        if case .scan = screen {
            screen = .join
        }
    }

    func onQrScanned(_ raw: String) {
        do {
            let lobby = try LobbyPayload.parse(raw)
            screen = .join
            pin = lobby.pin
            status = "Scanned. Joining…"
            connect(host: lobby.ip, port: lobby.port, pin: lobby.pin)
        } catch {
            self.error = error.localizedDescription
        }
    }

    func joinByPin() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty {
            error = "Enter your name first."
            return
        }
        if pin.count != 4 {
            error = "The PIN is 4 digits."
            return
        }
        if showManualEntry {
            joinManually()
            return
        }
        busy = true
        error = nil
        status = "Looking for quiz \(pin)…"

        discovery.findByPin(pin) { [weak self] endpoint in
            guard let self = self else { return }
            self.busy = false
            if let endpoint = endpoint {
                self.status = "Found the quiz. Joining…"
                self.connect(host: endpoint.host, port: endpoint.port, pin: endpoint.pin)
            } else {
                self.showManualEntry = true
                self.status = nil
                self.error = "No quiz found for PIN \(self.pin). Check the PIN, Wi-Fi connection, or type the address below."
            }
        }
    }

    func joinManually() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        var rawIp = ip.trimmingCharacters(in: .whitespacesAndNewlines)
        if rawIp.contains("://") {
            rawIp = rawIp.components(separatedBy: "://").last ?? rawIp
        }
        var cleanIp = rawIp
        var manualPort = Int(port)
        if rawIp.contains(":") && !rawIp.hasPrefix("[") {
            let parts = rawIp.components(separatedBy: ":")
            cleanIp = parts[0]
            if let p = parts.indices.contains(1) ? Int(parts[1]) : nil {
                manualPort = p
            }
        }

        guard !trimmedName.isEmpty else {
            error = "Enter your name first."
            return
        }
        guard !cleanIp.isEmpty else {
            error = "Enter the instructor's IP address."
            return
        }
        guard let p = manualPort, (1...65535).contains(p) else {
            error = "Enter a valid port, e.g. 8080."
            return
        }
        guard pin.count == 4 else {
            error = "The PIN is 4 digits."
            return
        }

        status = "Joining \(cleanIp):\(p)…"
        connect(host: cleanIp, port: p, pin: pin)
    }

    func connect(host: String, port: Int, pin: String) {
        discovery.stop()
        connection?.stop()
        unackedQueue.removeAll()
        pendingAnswers.removeAll()
        lastSentQid = nil

        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        store.name = trimmedName
        store.rememberSession(ip: host, port: port, pin: pin)

        let conn = QuizConnection(store: store, host: host, port: port, pin: pin, name: trimmedName)
        connection = conn
        screen = .lobby(name: trimmedName)
        status = nil
        error = nil

        conn.$state
            .receive(on: DispatchQueue.main)
            .assign(to: \.connState, on: self)
            .store(in: &cancellables)

        conn.events
            .receive(on: DispatchQueue.main)
            .sink { [weak self] message in
                self?.onServerMessage(message)
            }
            .store(in: &cancellables)

        conn.rejoins
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.resendUnacked()
            }
            .store(in: &cancellables)
    }

    func backToJoin(_ message: String? = nil) {
        connection?.stop()
        connection = nil
        screen = .join
        if let msg = message {
            error = msg
        }
    }

    func leaveQuiz() {
        backToJoin()
    }

    private func onServerMessage(_ message: ServerMessage) {
        switch message {
        case .quizStart(let quiz):
            onQuizStart(quiz)
        case .answersState(let raw):
            onAnswersState(raw)
        case .ack(let qid, let seq):
            onAck(qid: qid, seq: seq)
        case .quizEnd:
            screen = .ended
        case .paused(let reason):
            updateLock { $0.paused = true; $0.pausedReason = reason }
        case .resumed:
            updateLock { $0.paused = false; $0.pausedReason = nil }
            resendUnacked()
        case .lock(let on):
            updateLock {
                if !on && $0.pausedReason == "focus" {
                    $0.paused = false
                    $0.pausedReason = nil
                }
            }
        case .timeUpdate(let endsAt, let serverTime, let paused):
            onTimeUpdate(endsAt: endsAt, serverTime: serverTime, paused: paused)
        case .memePop(let memeId, let path, _, _, _, let durationMs):
            onMemePop(memeId: memeId, path: path, durationMs: durationMs)
        case .memeHide:
            onMemeHide()
        case .finished:
            onFinished()
        default:
            break
        }
    }

    private func onQuizStart(_ quiz: QuizStart) {
        let phoneNow = Int64(Date().timeIntervalSince1970 * 1000)
        let offset = QuizClock.offsetMs(serverTime: quiz.serverTime, phoneNow: phoneNow)
        let wasFinished = lockState.finished || (case .finished = screen)
        let keepIndex = quizUiState?.currentIndex ?? 0

        screen = wasFinished ? .finished(name: name) : .quiz(name: name)
        quizUiState = QuizUiState(
            title: quiz.title,
            questions: quiz.questions,
            currentIndex: min(keepIndex, max(0, quiz.questions.count - 1)),
            offsetMs: offset,
            endsAt: quiz.endsAt,
            paused: quiz.paused,
            answers: quizUiState?.answers ?? [:],
            unacked: quizUiState?.unacked ?? [],
            visited: (quizUiState?.visited ?? [])
        )
        updateLock {
            $0.running = true
            $0.lockMode = quiz.lockMode
        }
    }

    private func onAnswersState(_ raw: [String: Any]) {
        var answers = quizUiState?.answers ?? [:]
        for (qid, val) in raw {
            if answers[qid] == nil {
                if let b = val as? Bool {
                    answers[qid] = .flag(b)
                } else if let s = val as? String {
                    answers[qid] = .text(s)
                } else if let arr = val as? [String] {
                    answers[qid] = .items(arr)
                } else if let dict = val as? [String: String] {
                    answers[qid] = .links(dict)
                }
            }
        }
        quizUiState?.answers = answers
        resendUnacked()
    }

    private func onTimeUpdate(endsAt: Int64, serverTime: Int64, paused: Bool) {
        let phoneNow = Int64(Date().timeIntervalSince1970 * 1000)
        let offset = QuizClock.offsetMs(serverTime: serverTime, phoneNow: phoneNow)
        quizUiState?.endsAt = endsAt
        quizUiState?.offsetMs = offset
        quizUiState?.paused = paused
    }

    private func onAck(qid: String, seq: Int64) {
        quizUiState?.unacked.remove(qid)
        if let queued = unackedQueue[qid], queued.seq <= seq {
            unackedQueue.removeValue(forKey: qid)
        }
        if lastSentQid == qid {
            lastSentQid = nil
        }
    }

    private func onFinished() {
        updateLock { $0.finished = true }
        screen = .finished(name: name)
        finishDialog = nil
        savingAnswers = false
    }

    private func onMemePop(memeId: String, path: String, durationMs: Int64) {
        memeHideTimer?.invalidate()
        if let cached = memeCache[memeId] {
            memeState = MemeState(memeId: memeId, image: cached, durationMs: durationMs)
            scheduleMemeHide(durationMs)
            return
        }
        connection?.fetchBytes(path: path) { [weak self] data in
            guard let self = self, let data = data, let image = UIImage(data: data) else { return }
            DispatchQueue.main.async {
                self.memeCache[memeId] = image
                self.memeState = MemeState(memeId: memeId, image: image, durationMs: durationMs)
                self.scheduleMemeHide(durationMs)
            }
        }
    }

    private func onMemeHide() {
        memeHideTimer?.invalidate()
        memeState = nil
    }

    private func scheduleMemeHide(_ durationMs: Int64) {
        memeHideTimer?.invalidate()
        memeHideTimer = Timer.scheduledTimer(withTimeInterval: TimeInterval(durationMs) / 1000.0, repeats: false) { [weak self] _ in
            self?.memeState = nil
        }
    }

    func selectQuizIndex(_ index: Int) {
        guard let quiz = quizUiState, quiz.questions.indices.contains(index) else { return }
        quizUiState?.currentIndex = index
        let qid = quiz.questions[index].qid
        quizUiState?.visited.insert(qid)
    }

    func nextQuestion() {
        guard let quiz = quizUiState else { return }
        if quiz.currentIndex < quiz.questions.count - 1 {
            selectQuizIndex(quiz.currentIndex + 1)
        }
    }

    func previousQuestion() {
        guard let quiz = quizUiState else { return }
        if quiz.currentIndex > 0 {
            selectQuizIndex(quiz.currentIndex - 1)
        }
    }

    func skipQuestion() {
        nextQuestion()
    }

    func onAnswer(question: Question, value: AnswerValue) {
        quizUiState?.answers[question.qid] = value
        let newSeq = store.answerSeq + 1
        store.answerSeq = newSeq

        let json = Protocol.answer(qid: question.qid, value: value.jsonObject, seq: newSeq)
        unackedQueue[question.qid] = (value: value, seq: newSeq)
        quizUiState?.unacked.insert(question.qid)
        lastSentQid = question.qid

        if lockState.paused {
            return
        }
        _ = connection?.send(json: json)
    }

    private func resendUnacked() {
        for (qid, item) in unackedQueue {
            let json = Protocol.answer(qid: qid, value: item.value.jsonObject, seq: item.seq)
            _ = connection?.send(json: json)
        }
    }

    func openFinishDialog() {
        guard let quiz = quizUiState else { return }
        let unanswered = quiz.questions.filter { !quiz.isAnswered($0) }.count
        finishDialog = unanswered
    }

    func closeFinishDialog() {
        finishDialog = nil
    }

    func confirmFinish() {
        closeFinishDialog()
        savingAnswers = true
        saveError = nil
        _ = connection?.send(json: Protocol.finish())
    }

    func requestResume() {
        _ = connection?.send(json: Protocol.resumeRequest())
    }

    private func updateLock(_ update: (inout LockState) -> Void) {
        update(&lockState)
    }

    private func setupLifecycleObservers() {
        #if canImport(UIKit)
        NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self, self.lockState.running, !self.lockState.finished else { return }
            _ = self.connection?.send(json: Protocol.focusLost(reason: "window"))
        }

        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self, self.lockState.running, !self.lockState.finished else { return }
            _ = self.connection?.send(json: Protocol.focusLost(reason: "background"))
        }

        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self = self, self.lockState.running, !self.lockState.finished else { return }
            _ = self.connection?.send(json: Protocol.focusGained())
            self.resendUnacked()
        }
        #endif
    }
}
