import SwiftUI

struct LobbyView: View {
    let name: String
    let conn: ConnState?
    @ObservedObject var vm: AppViewModel

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            ProgressView()
                .scaleEffect(1.5)
            Text("Waiting for the quiz to start, \(name)…")
                .font(.headline)
                .multilineTextAlignment(.center)

            if case .reconnecting = conn {
                Text("Reconnecting to instructor…")
                    .font(.subheadline)
                    .foregroundColor(.orange)
            }

            Button(action: { vm.backToJoin() }) {
                Text("Leave")
                    .foregroundColor(.red)
            }
            Spacer()
        }
        .padding(24)
    }
}
