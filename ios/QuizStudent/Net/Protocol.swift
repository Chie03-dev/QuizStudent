import Foundation

enum ServerMessage {
    case joined(studentId: String, deviceToken: String?)
    case failure(code: String)
    case kick
    case quizStart(QuizStart)
    case answersState([String: Any])
    case ack(qid: String, seq: Int64)
    case quizEnd(reason: String)
    case paused(reason: String)
    case resumed
    case lock(on: Bool)
    case timeUpdate(endsAt: Int64, serverTime: Int64, paused: Bool)
    case memePop(memeId: String, path: String, mime: String, width: Int, height: Int, durationMs: Int64)
    case memeHide
    case finished
    case unknown(type: String)
}

struct Protocol {
    static let path = "/ws"

    static func join(pin: String, name: String, deviceToken: String) -> String {
        let d: [String: Any] = [
            "pin": pin,
            "name": name,
            "deviceToken": deviceToken
        ]
        return envelope(type: "join", data: d)
    }

    static func heartbeat() -> String {
        return envelope(type: "hb", data: [:])
    }

    static func answer(qid: String, value: Any, seq: Int64) -> String {
        let d: [String: Any] = [
            "qid": qid,
            "value": value,
            "seq": seq
        ]
        return envelope(type: "answer", data: d)
    }

    static func focusLost(reason: String) -> String {
        return envelope(type: "focus_lost", data: ["reason": reason])
    }

    static func focusGained() -> String {
        return envelope(type: "focus_gained", data: [:])
    }

    static func resumeRequest() -> String {
        return envelope(type: "resume_request", data: [:])
    }

    static func finish() -> String {
        return envelope(type: "finish", data: [:])
    }

    private static func envelope(type: String, data: [String: Any]) -> String {
        let dict: [String: Any] = [
            "t": type,
            "d": data
        ]
        if let jsonData = try? JSONSerialization.data(withJSONObject: dict, options: []),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            return jsonString
        }
        return "{\"t\":\"\(type)\",\"d\":{}}"
    }

    static func parse(_ text: String) -> ServerMessage {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
              let type = json["t"] as? String else {
            return .unknown(type: "invalid")
        }
        let d = json["d"] as? [String: Any] ?? [:]

        switch type {
        case "joined":
            let studentId = d["studentId"] as? String ?? ""
            let deviceToken = d["deviceToken"] as? String
            return .joined(studentId: studentId, deviceToken: deviceToken?.isEmpty == true ? nil : deviceToken)
        case "error":
            let code = d["code"] as? String ?? ""
            return .failure(code: code)
        case "kick":
            return .kick
        case "quiz_start":
            if let jsonData = try? JSONSerialization.data(withJSONObject: d, options: []),
               let quizStart = try? JSONDecoder().decode(QuizStart.self, from: jsonData) {
                return .quizStart(quizStart)
            }
            return .unknown(type: type)
        case "answers_state":
            let rawAnswers = d["answers"] as? [String: Any] ?? [:]
            return .answersState(rawAnswers)
        case "ack":
            let qid = d["qid"] as? String ?? ""
            let seq = (d["seq"] as? NSNumber)?.int64Value ?? 0
            return .ack(qid: qid, seq: seq)
        case "quiz_end":
            let reason = d["reason"] as? String ?? ""
            return .quizEnd(reason: reason)
        case "paused":
            let reason = d["reason"] as? String ?? ""
            return .paused(reason: reason)
        case "resumed":
            return .resumed
        case "lock":
            let on = d["on"] as? Bool ?? false
            return .lock(on: on)
        case "time_update":
            let endsAt = (d["endsAt"] as? NSNumber)?.int64Value ?? 0
            let serverTime = (d["serverTime"] as? NSNumber)?.int64Value ?? 0
            let paused = d["paused"] as? Bool ?? false
            return .timeUpdate(endsAt: endsAt, serverTime: serverTime, paused: paused)
        case "meme_pop":
            let memeId = d["memeId"] as? String ?? ""
            let path = d["path"] as? String ?? ""
            let mime = d["mime"] as? String ?? ""
            let width = d["width"] as? Int ?? 0
            let height = d["height"] as? Int ?? 0
            let durationMs = (d["durationMs"] as? NSNumber)?.int64Value ?? 3000
            return .memePop(memeId: memeId, path: path, mime: mime, width: width, height: height, durationMs: durationMs)
        case "meme_hide":
            return .memeHide
        case "finished":
            return .finished
        default:
            return .unknown(type: type)
        }
    }

    static func errorMessage(for code: String) -> String {
        switch code {
        case "BAD_PIN": return "Wrong PIN. Check the code on the instructor's screen and try again."
        case "NAME_TAKEN": return "That name is already in this quiz. Pick a different name."
        case "RATE_LIMITED": return "Too many wrong PINs from this network. Wait about a minute."
        case "CLOSED": return "The quiz has been closed by the instructor."
        case "QUIZ_ENDED": return "The quiz has ended."
        case "BAD_ANSWER": return "The instructor's computer could not accept that answer."
        case "UNKNOWN_QUESTION": return "That question is not part of this quiz."
        case "PAUSED": return "You are paused. The answer is kept and sent when you resume."
        case "FINISHED": return "You already finished the quiz."
        default: return "Could not join: \(code)"
        }
    }
}
