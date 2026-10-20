import SwiftUI

struct PausedView: View {
    let lock: LockState
    @ObservedObject var vm: AppViewModel

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "pause.circle.fill")
                .font(.system(size: 64))
                .foregroundColor(.orange)
            Text("Quiz Paused")
                .font(.title)
                .fontWeight(.bold)
            Text(reasonText)
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)

            Button(action: { vm.requestResume() }) {
                Text("Ask to resume")
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .disabled(!lock.canResume)
            .padding(.horizontal, 32)
            Spacer()
        }
        .padding(24)
    }

    private var reasonText: String {
        switch lock.pausedReason {
        case "focus": return "You left the quiz window or screen pinning ended."
        case "network": return "Connection lost. Reconnecting…"
        default: return "The instructor has paused your session."
        }
    }
}

struct FinishedView: View {
    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundColor(.green)
            Text("Quiz Finished")
                .font(.title)
                .fontWeight(.bold)
            Text("Your answers have been submitted. You can wait for the instructor to end the session.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
            Spacer()
        }
        .padding(24)
    }
}

struct EndedView: View {
    @ObservedObject var vm: AppViewModel
    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Text("Quiz Ended")
                .font(.title)
                .fontWeight(.bold)
            Text("The quiz session has been ended by the instructor.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
            Button(action: { vm.backToJoin() }) {
                Text("Back to Join")
                    .fontWeight(.bold)
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            Spacer()
        }
        .padding(24)
    }
}
