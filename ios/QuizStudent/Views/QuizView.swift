import SwiftUI
import Combine

struct QuizView: View {
    @ObservedObject var vm: AppViewModel
    @State private var now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)

    let timer = Timer.publish(every: 1.0, on: .main, in: .common).autoconnect()

    var body: some View {
        guard let quiz = vm.quizState else {
            return AnyView(Text("Loading quiz..."))
        }

        let remaining = QuizClock.remainingMs(endsAt: quiz.endsAt, phoneNow: now, offsetMs: quiz.offsetMs)
        let question = quiz.current

        return AnyView(
            VStack(spacing: 16) {
                // Countdown Bar
                HStack {
                    Text(quiz.paused ? "Time paused" : (remaining <= 0 ? "Time's up" : "Time left"))
                        .fontWeight(.bold)
                        .foregroundColor(remaining <= 0 ? .red : .secondary)
                    Spacer()
                    Text(QuizClock.format(remainingMs: remaining))
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(remaining <= 0 ? .red : Color.accentColor)
                }
                .padding()
                .background(Color.secondary.opacity(0.1))
                .cornerRadius(12)

                // Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(quiz.questions.enumerated()), id: \.offset) { index, q in
                            let isCurrent = index == quiz.currentIndex
                            let isAnswered = quiz.isAnswered(q)
                            Button(action: { vm.selectQuizIndex(index) }) {
                                Text("\(isCurrent ? "▶ " : "")\(index + 1)")
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(isAnswered ? Color.accentColor : Color.secondary.opacity(0.2))
                                    .foregroundColor(isAnswered ? .white : .primary)
                                    .cornerRadius(8)
                            }
                        }
                    }
                }

                if let q = question {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Question \(quiz.currentIndex + 1) of \(quiz.questions.count)")
                                    .font(.caption)
                                    .foregroundColor(.accentColor)
                                Text(q.body)
                                    .font(.title3)
                                    .fontWeight(.semibold)
                            }
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.secondary.opacity(0.05))
                            .cornerRadius(16)

                            QuestionInputView(
                                question: q,
                                value: Binding(
                                    get: { quiz.answers[q.qid] },
                                    set: { _ in }
                                ),
                                vm: vm
                            )
                        }
                    }
                } else {
                    Text("This quiz has no questions.")
                }

                // Nav Row
                HStack(spacing: 12) {
                    Button(action: { vm.previousQuestion() }) {
                        Text("Previous")
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.secondary.opacity(0.2))
                            .cornerRadius(12)
                    }
                    .disabled(quiz.currentIndex == 0)

                    if quiz.currentIndex == quiz.questions.count - 1 {
                        Button(action: { vm.openFinishDialog() }) {
                            Text("Finish")
                                .fontWeight(.bold)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.accentColor)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                        }
                    } else {
                        Button(action: { vm.nextQuestion() }) {
                            Text("Next")
                                .fontWeight(.bold)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.accentColor)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                        }
                        Button(action: { vm.skipQuestion() }) {
                            Text("Skip")
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.secondary.opacity(0.2))
                                .cornerRadius(12)
                        }
                    }
                }
            }
            .padding(16)
            .onReceive(timer) { _ in
                if !quiz.paused {
                    now = Int64(Date().timeIntervalSince1970 * 1000)
                }
            }
            .alert(item: Binding(
                get: { vm.finishDialog.map { FinishDialogWrapper(count: $0) } },
                set: { _ in vm.closeFinishDialog() }
            )) { wrapper in
                Alert(
                    title: Text("Finish Quiz"),
                    message: Text("You have \(wrapper.count) unanswered question(s). Are you sure you want to finish?"),
                    primaryButton: .destructive(Text("Finish")) {
                        vm.confirmFinish()
                    },
                    secondaryButton: .cancel()
                )
            }
        )
    }
}

struct FinishDialogWrapper: Identifiable {
    let id = UUID()
    let count: Int
}
