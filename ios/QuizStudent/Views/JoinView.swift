import SwiftUI

struct JoinView: View {
    @ObservedObject var vm: AppViewModel
    var onScanClicked: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Spacer().frame(height: 8)
                VStack(spacing: 6) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.accentColor)
                            .frame(width: 64, height: 64)
                        Text("Q")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(.white)
                    }
                    Text("Join the quiz")
                        .font(.title2)
                        .fontWeight(.bold)
                    Text("Scan the instructor's QR code, or type the 4-digit PIN.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 12) {
                    TextField("Your name", text: Binding(
                        get: { vm.name },
                        set: { vm.onNameChange($0) }
                    ))
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .autocorrectionDisabled()

                    TextField("PIN", text: Binding(
                        get: { vm.pin },
                        set: { vm.onPinChange($0) }
                    ))
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .keyboardType(.numberPad)

                    Button(action: onScanClicked) {
                        Text("Scan QR")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.accentColor)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                    .disabled(vm.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                    Button(action: { vm.joinByPin() }) {
                        Text(vm.busy ? "Joining…" : "Join with PIN")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(vm.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || vm.pin.count != 4 ? Color.gray.opacity(0.3) : Color.accentColor)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                    .disabled(vm.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || vm.pin.count != 4 || vm.busy)
                }
                .frame(maxWidth: 520)

                if vm.busy {
                    ProgressView()
                }
                if let status = vm.status {
                    Text(status)
                        .font(.body)
                        .multilineTextAlignment(.center)
                }
                if let error = vm.error {
                    Text(error)
                        .font(.body)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                }

                if vm.showManualEntry {
                    Divider()
                    Text("or enter the address")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    VStack(spacing: 12) {
                        TextField("Instructor's IP, e.g. 192.168.0.235", text: Binding(
                            get: { vm.ip },
                            set: { vm.onIpChange($0) }
                        ))
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)

                        TextField("Port, e.g. 8080", text: Binding(
                            get: { vm.port },
                            set: { vm.onPortChange($0) }
                        ))
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .keyboardType(.numberPad)

                        Button(action: { vm.joinManually() }) {
                            Text("Join with address")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.secondary.opacity(0.2))
                                .foregroundColor(.primary)
                                .cornerRadius(12)
                        }

                        Button(action: { vm.showManualEntry(false) }) {
                            Text("Try the PIN again")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .foregroundColor(.accentColor)
                        }
                    }
                    .frame(maxWidth: 520)
                }
            }
            .padding(24)
        }
    }
}
