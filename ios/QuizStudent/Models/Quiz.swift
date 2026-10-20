import Foundation

enum QuestionType: String, Codable {
    case mcq, tf, identification, fillin, enumeration, problem, matching, connect
}

struct OptionItem: Identifiable, Codable, Equatable {
    let id: String
    let text: String
}

struct Item: Identifiable, Codable, Equatable {
    let id: String
    let text: String
}

enum Question: Codable, Identifiable {
    case mcq(qid: String, body: String, points: Double, options: [OptionItem])
    case tf(qid: String, body: String, points: Double)
    case identification(qid: String, body: String, points: Double)
    case fillin(qid: String, body: String, points: Double, blanks: Int)
    case enumeration(qid: String, body: String, points: Double, count: Int)
    case problem(qid: String, body: String, points: Double)
    case matching(qid: String, body: String, points: Double, left: [Item], right: [Item])
    case connect(qid: String, body: String, points: Double, prompts: [Item], answers: [Item])

    var id: String { qid }
    var qid: String {
        switch self {
        case .mcq(let q, _, _, _), .tf(let q, _, _), .identification(let q, _, _),
             .fillin(let q, _, _, _), .enumeration(let q, _, _, _), .problem(let q, _, _),
             .matching(let q, _, _, _, _), .connect(let q, _, _, _, _):
            return q
        }
    }
    var body: String {
        switch self {
        case .mcq(_, let b, _, _), .tf(_, let b, _), .identification(_, let b, _),
             .fillin(_, let b, _, _), .enumeration(_, let b, _, _), .problem(_, let b, _),
             .matching(_, let b, _, _, _), .connect(_, let b, _, _, _):
            return b
        }
    }
    var points: Double {
        switch self {
        case .mcq(_, _, let p, _), .tf(_, _, let p), .identification(_, _, let p),
             .fillin(_, _, let p, _), .enumeration(_, _, let p, _), .problem(_, _, let p),
             .matching(_, _, let p, _, _), .connect(_, _, let p, _, _):
            return p
        }
    }

    enum CodingKeys: String, CodingKey {
        case qid, type, body, points, options, blanks, count, left, right, prompts, answers
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let qid = try container.decode(String.self, forKey: .qid)
        let type = try container.decode(String.self, forKey: .type)
        let body = try container.decodeIfPresent(String.self, forKey: .body) ?? ""
        let points = try container.decodeIfPresent(Double.self, forKey: .points) ?? 0.0

        switch type {
        case "mcq":
            let options = try container.decodeIfPresent([OptionItem].self, forKey: .options) ?? []
            self = .mcq(qid: qid, body: body, points: points, options: options)
        case "tf":
            self = .tf(qid: qid, body: body, points: points)
        case "identification":
            self = .identification(qid: qid, body: body, points: points)
        case "fillin":
            let blanks = try container.decodeIfPresent(Int.self, forKey: .blanks) ?? 1
            self = .fillin(qid: qid, body: body, points: points, blanks: blanks)
        case "enumeration":
            let count = try container.decodeIfPresent(Int.self, forKey: .count) ?? 1
            self = .enumeration(qid: qid, body: body, points: points, count: count)
        case "problem":
            self = .problem(qid: qid, body: body, points: points)
        case "matching":
            let left = try container.decodeIfPresent([Item].self, forKey: .left) ?? []
            let right = try container.decodeIfPresent([Item].self, forKey: .right) ?? []
            self = .matching(qid: qid, body: body, points: points, left: left, right: right)
        case "connect":
            let prompts = try container.decodeIfPresent([Item].self, forKey: .prompts) ?? []
            let answers = try container.decodeIfPresent([Item].self, forKey: .answers) ?? []
            self = .connect(qid: qid, body: body, points: points, prompts: prompts, answers: answers)
        default:
            throw DecodingError.dataCorruptedError(forKey: .type, in: container, debugDescription: "Unknown question type: \\(type)")
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(qid, forKey: .qid)
        try container.encode(body, forKey: .body)
        try container.encode(points, forKey: .points)

        switch self {
        case .mcq(_, _, _, let options):
            try container.encode("mcq", forKey: .type)
            try container.encode(options, forKey: .options)
        case .tf:
            try container.encode("tf", forKey: .type)
        case .identification:
            try container.encode("identification", forKey: .type)
        case .fillin(_, _, _, let blanks):
            try container.encode("fillin", forKey: .type)
            try container.encode(blanks, forKey: .blanks)
        case .enumeration(_, _, _, let count):
            try container.encode("enumeration", forKey: .type)
            try container.encode(count, forKey: .count)
        case .problem:
            try container.encode("problem", forKey: .type)
        case .matching(_, _, _, let left, let right):
            try container.encode("matching", forKey: .type)
            try container.encode(left, forKey: .left)
            try container.encode(right, forKey: .right)
        case .connect(_, _, _, let prompts, let answers):
            try container.encode("connect", forKey: .type)
            try container.encode(prompts, forKey: .prompts)
            try container.encode(answers, forKey: .answers)
        }
    }
}

struct QuizStart: Codable {
    let quizId: String
    let title: String
    let questions: [Question]
    let endsAt: Int64
    let serverTime: Int64
    let lockMode: Bool
    let paused: Bool

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        quizId = try container.decodeIfPresent(String.self, forKey: .quizId) ?? ""
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
        questions = try container.decodeIfPresent([Question].self, forKey: .questions) ?? []
        endsAt = try container.decodeIfPresent(Int64.self, forKey: .endsAt) ?? 0
        serverTime = try container.decodeIfPresent(Int64.self, forKey: .serverTime) ?? 0
        lockMode = try container.decodeIfPresent(Bool.self, forKey: .lockMode) ?? false
        paused = try container.decodeIfPresent(Bool.self, forKey: .paused) ?? false
    }

    enum CodingKeys: String, CodingKey {
        case quizId, title, questions, endsAt, serverTime, lockMode, paused
    }
}

enum AnswerValue: Codable, Equatable {
    case text(String)
    case flag(Bool)
    case items([String])
    case links([String: String])

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let b = try? container.decode(Bool.self) {
            self = .flag(b)
        } else if let s = try? container.decode(String.self) {
            self = .text(s)
        } else if let arr = try? container.decode([String].self) {
            self = .items(arr)
        } else if let dict = try? container.decode([String: String].self) {
            self = .links(dict)
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid AnswerValue format")
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .text(let s): try container.encode(s)
        case .flag(let b): try container.encode(b)
        case .items(let arr): try container.encode(arr)
        case .links(let dict): try container.encode(dict)
        }
    }

    var jsonObject: Any {
        switch self {
        case .text(let s): return s
        case .flag(let b): return b
        case .items(let arr): return arr
        case .links(let dict): return dict
        }
    }

    static func isAnswered(_ question: Question, _ value: AnswerValue?) -> Bool {
        guard let value = value else { return false }
        switch (question, value) {
        case (.mcq, .text(let s)), (.identification, .text(let s)), (.problem, .text(let s)):
            return !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case (.tf, .flag):
            return true
        case (.fillin(_, _, _, let blanks), .items(let list)):
            if list.count < blanks { return false }
            return list.prefix(blanks).contains { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        case (.enumeration(_, _, _, let count), .items(let list)):
            return list.contains { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        case (.matching, .links(let dict)), (.connect, .links(let dict)):
            return !dict.isEmpty
        default:
            return false
        }
    }
}
