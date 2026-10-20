import Foundation

class StudentStore {
    private let defaults = UserDefaults.standard

    private enum Keys {
        static let deviceToken = "device_token"
        static let studentId = "student_id"
        static let name = "name"
        static let lastIp = "last_ip"
        static let lastPort = "last_port"
        static let lastPin = "last_pin"
        static let answerSeq = "answer_seq"
    }

    var deviceToken: String {
        if let token = defaults.string(forKey: Keys.deviceToken) {
            return token
        }
        let newToken = UUID().uuidString
        defaults.set(newToken, forKey: Keys.deviceToken)
        return newToken
    }

    var studentId: String? {
        get { defaults.string(forKey: Keys.studentId) }
        set { defaults.set(newValue, forKey: Keys.studentId) }
    }

    var name: String? {
        get { defaults.string(forKey: Keys.name) }
        set { defaults.set(newValue, forKey: Keys.name) }
    }

    var lastIp: String? {
        get { defaults.string(forKey: Keys.lastIp) }
        set { defaults.set(newValue, forKey: Keys.lastIp) }
    }

    var lastPort: Int {
        get { defaults.integer(forKey: Keys.lastPort) }
        set { defaults.set(newValue, forKey: Keys.lastPort) }
    }

    var lastPin: String? {
        get { defaults.string(forKey: Keys.lastPin) }
        set { defaults.set(newValue, forKey: Keys.lastPin) }
    }

    var answerSeq: Int64 {
        get {
            let val = defaults.integer(forKey: Keys.answerSeq)
            return Int64(val)
        }
        set {
            defaults.set(Int(newValue), forKey: Keys.answerSeq)
        }
    }

    func rememberSession(ip: String, port: Int, pin: String) {
        defaults.set(ip, forKey: Keys.lastIp)
        defaults.set(port, forKey: Keys.lastPort)
        defaults.set(pin, forKey: Keys.lastPin)
    }

    func clearSession() {
        defaults.removeObject(forKey: Keys.studentId)
        defaults.removeObject(forKey: Keys.lastIp)
        defaults.removeObject(forKey: Keys.lastPort)
        defaults.removeObject(forKey: Keys.lastPin)
    }
}
