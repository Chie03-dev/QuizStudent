import Foundation

struct QuizClock {
    static func offsetMs(serverTime: Int64, phoneNow: Int64) -> Int64 {
        return serverTime - phoneNow
    }

    static func remainingMs(endsAt: Int64, phoneNow: Int64, offsetMs: Int64) -> Int64 {
        return endsAt - (phoneNow + offsetMs)
    }

    static func format(remainingMs: Int64) -> String {
        let totalSeconds = max(0, remainingMs / 1000)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}
