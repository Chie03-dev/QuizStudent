import Foundation

struct LobbyPayload: Codable {
    let ip: String
    let port: Int
    let pin: String

    static func parse(_ raw: String) throws -> LobbyPayload {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("{"), let data = trimmed.data(using: .utf8) else {
            throw NSError(domain: "QrPayload", code: 1, userInfo: [NSLocalizedDescriptionKey: "That code is not a quiz lobby code."])
        }
        let decoder = JSONDecoder()
        let payload = try decoder.decode(LobbyPayload.self, from: data)
        guard !payload.ip.isEmpty else {
            throw NSError(domain: "QrPayload", code: 2, userInfo: [NSLocalizedDescriptionKey: "The lobby code has no IP address."])
        }
        guard (1...65535).contains(payload.port) else {
            throw NSError(domain: "QrPayload", code: 3, userInfo: [NSLocalizedDescriptionKey: "The lobby code has an invalid port."])
        }
        guard !payload.pin.isEmpty else {
            throw NSError(domain: "QrPayload", code: 4, userInfo: [NSLocalizedDescriptionKey: "The lobby code has no PIN."])
        }
        return payload
    }
}
