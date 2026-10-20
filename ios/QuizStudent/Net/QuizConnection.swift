import Foundation
import Combine

enum ConnState: Equatable {
    case connecting
    case joined(name: String)
    case reconnecting
    case rejected(message: String)
}

class QuizConnection: NSObject, URLSessionWebSocketDelegate {
    private let store: StudentStore
    private let host: String
    private let port: Int
    private let pin: String
    private let name: String

    private var webSocketTask: URLSessionWebSocketTask?
    private var session: URLSession?
    private var heartbeatTimer: Timer?
    private var retryTimer: Timer?
    private var isStopped = false

    @Published var state: ConnState = .connecting
    let events = PassthroughSubject<ServerMessage, Never>()
    let rejoins = PassthroughSubject<Void, Never>()

    init(store: StudentStore, host: String, port: Int, pin: String, name: String) {
        self.store = store
        self.host = host
        self.port = port
        self.pin = pin
        self.name = name
        super.init()

        let configuration = URLSessionConfiguration.default
        configuration.allowsCellularAccess = true
        configuration.waitsForConnectivity = true
        self.session = URLSession(configuration: configuration, delegate: self, delegateQueue: .main)

        open()
    }

    func open() {
        guard !isStopped else { return }
        let urlString = "ws://\(host):\(port)\(Protocol.path)"
        guard let url = URL(string: urlString) else { return }

        if case .joined = state {} else {
            state = .connecting
        }

        let task = session?.webSocketTask(with: url)
        webSocketTask = task
        task?.resume()
        listen()
        startHeartbeat()
    }

    func send(json: String) -> Bool {
        guard let task = webSocketTask, task.state == .running else { return false }
        task.send(.string(json)) { error in
            if let error = error {
                print("WebSocket send error: \(error)")
            }
        }
        return true
    }

    func fetchBytes(path: String, completion: @escaping (Data?) -> Void) {
        let urlString = "http://\(host):\(port)\(path)"
        guard let url = URL(string: urlString) else {
            completion(nil)
            return
        }
        let task = URLSession.shared.dataTask(with: url) { data, response, error in
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200, let data = data {
                completion(data)
            } else {
                completion(nil)
            }
        }
        task.resume()
    }

    func stop() {
        isStopped = true
        stopHeartbeat()
        retryTimer?.invalidate()
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
    }

    private func listen() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self, !self.isStopped else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleMessage(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleMessage(text)
                    }
                @unknown default:
                    break
                }
                self.listen()
            case .failure(let error):
                print("WebSocket receive error: \(error)")
                self.handleDisconnect()
            }
        }
    }

    private func handleMessage(_ text: String) {
        let message = Protocol.parse(text)
        switch message {
        case .joined(let studentId, let deviceToken):
            store.studentId = studentId
            if let dt = deviceToken {
                // store deviceToken if needed
            }
            state = .joined(name: name)
            rejoins.send(())
        case .failure(let code):
            let errText = Protocol.errorMessage(for: code)
            state = .rejected(message: errText)
            stop()
        case .kick:
            state = .rejected(message: "You have been removed from the quiz.")
            stop()
        default:
            events.send(message)
        }
    }

    private func handleDisconnect() {
        stopHeartbeat()
        guard !isStopped else { return }
        state = .reconnecting
        retryTimer?.invalidate()
        retryTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: false) { [weak self] _ in
            self?.open()
        }
    }

    private func startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            _ = self.send(json: Protocol.heartbeat())
        }
    }

    private func stopHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }
}
