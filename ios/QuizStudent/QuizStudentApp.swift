import SwiftUI

@main
struct QuizStudentApp: App {
    @StateObject private var vm = AppViewModel()

    var body: some Scene {
        WindowGroup {
            ZStack {
                Group {
                    switch vm.screen {
                    case .join:
                        JoinView(vm: vm, onScanClicked: { vm.openScanner() })
                    case .scan:
                        ScanView(
                            onScanned: { raw in vm.onQrScanned(raw) },
                            onCancelled: { vm.closeScanner() }
                        )
                    case .lobby(let name):
                        LobbyView(name: name, conn: vm.connState, vm: vm)
                    case .quiz:
                        if vm.lockState.finished {
                            FinishedView()
                        } else if vm.lockState.paused {
                            PausedView(lock: vm.lockState, vm: vm)
                        } else {
                            QuizView(vm: vm)
                        }
                    case .finished:
                        FinishedView()
                    case .ended:
                        EndedView(vm: vm)
                    }
                }

                // Meme Pop Full-Screen Overlay
                if let meme = vm.memeState {
                    ZStack {
                        Color.black.ignoresSafeArea()
                        Image(uiImage: meme.image)
                            .resizable()
                            .scaledToFit()
                            .ignoresSafeArea()
                    }
                }
            }
        }
    }
}
