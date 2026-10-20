import SwiftUI

struct QuestionInputView: View {
    let question: Question
    @Binding var value: AnswerValue?
    let vm: AppViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            switch question {
            case .mcq(_, _, _, let options):
                let currentVal = (value as? AnswerValue)?.textValue ?? ""
                ForEach(options) { option in
                    Button(action: {
                        let newVal = AnswerValue.text(option.id)
                        value = newVal
                        vm.onAnswer(question: question, value: newVal)
                    }) {
                        HStack {
                            Image(systemName: currentVal == option.id ? "largecircle.fill.circle" : "circle")
                            Text(option.text)
                                .foregroundColor(.primary)
                            Spacer()
                        }
                        .padding()
                        .background(currentVal == option.id ? Color.accentColor.opacity(0.15) : Color.secondary.opacity(0.1))
                        .cornerRadius(12)
                    }
                }

            case .tf:
                let currentFlag = (value as? AnswerValue)?.flagValue
                HStack(spacing: 16) {
                    Button(action: {
                        let newVal = AnswerValue.flag(true)
                        value = newVal
                        vm.onAnswer(question: question, value: newVal)
                    }) {
                        Text("True")
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(currentFlag == true ? Color.accentColor : Color.secondary.opacity(0.1))
                            .foregroundColor(currentFlag == true ? .white : .primary)
                            .cornerRadius(12)
                    }
                    Button(action: {
                        let newVal = AnswerValue.flag(false)
                        value = newVal
                        vm.onAnswer(question: question, value: newVal)
                    }) {
                        Text("False")
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(currentFlag == false ? Color.accentColor : Color.secondary.opacity(0.1))
                            .foregroundColor(currentFlag == false ? .white : .primary)
                            .cornerRadius(12)
                    }
                }

            case .identification, .problem:
                let textVal = Binding(
                    get: { (value as? AnswerValue)?.textValue ?? "" },
                    set: { newVal in
                        let val = AnswerValue.text(newVal)
                        value = val
                        vm.onAnswer(question: question, value: val)
                    }
                )
                TextField("Type your answer here...", text: textVal)
                    .textFieldStyle(RoundedBorderTextFieldStyle())

            case .fillin(_, _, _, let blanks):
                let itemsVal = Binding(
                    get: {
                        if case let .items(list) = value { return list }
                        return Array(repeating: "", count: blanks)
                    },
                    set: { list in
                        let val = AnswerValue.items(list)
                        value = val
                        vm.onAnswer(question: question, value: val)
                    }
                )
                ForEach(0..<blanks, id: \.self) { i in
                    HStack {
                        Text("Blank \(i + 1):")
                        TextField("Answer", text: Binding(
                            get: { itemsVal.wrappedValue.indices.contains(i) ? itemsVal.wrappedValue[i] : "" },
                            set: { s in
                                var current = itemsVal.wrappedValue
                                while current.count <= i { current.append("") }
                                current[i] = s
                                itemsVal.wrappedValue = current
                            }
                        ))
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    }
                }

            case .enumeration(_, _, _, let count):
                let itemsVal = Binding(
                    get: {
                        if case let .items(list) = value { return list }
                        return Array(repeating: "", count: count)
                    },
                    set: { list in
                        let val = AnswerValue.items(list)
                        value = val
                        vm.onAnswer(question: question, value: val)
                    }
                )
                ForEach(0..<count, id: \.self) { i in
                    HStack {
                        Text("\(i + 1).")
                        TextField("Item", text: Binding(
                            get: { itemsVal.wrappedValue.indices.contains(i) ? itemsVal.wrappedValue[i] : "" },
                            set: { s in
                                var current = itemsVal.wrappedValue
                                while current.count <= i { current.append("") }
                                current[i] = s
                                itemsVal.wrappedValue = current
                            }
                        ))
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    }
                }

            case .matching(_, _, _, let left, let right):
                let linksVal = Binding(
                    get: { (value as? AnswerValue)?.linksValue ?? [:] },
                    set: { dict in
                        let val = AnswerValue.links(dict)
                        value = val
                        vm.onAnswer(question: question, value: val)
                    }
                )
                VStack(spacing: 12) {
                    ForEach(left) { lItem in
                        HStack {
                            Text(lItem.text)
                            Spacer()
                            Picker("Match", selection: Binding(
                                get: { linksVal.wrappedValue[lItem.id] ?? "" },
                                set: { rId in
                                    var dict = linksVal.wrappedValue
                                    if rId.isEmpty { dict.removeValue(forKey: lItem.id) }
                                    else { dict[lItem.id] = rId }
                                    linksVal.wrappedValue = dict
                                }
                            )) {
                                Text("Select match...").tag("")
                                ForEach(right) { rItem in
                                    Text(rItem.text).tag(rItem.id)
                                }
                            }
                            .pickerStyle(MenuPickerStyle())
                        }
                    }
                }

            case .connect(_, _, _, let prompts, let answers):
                let linksVal = Binding(
                    get: { (value as? AnswerValue)?.linksValue ?? [:] },
                    set: { dict in
                        let val = AnswerValue.links(dict)
                        value = val
                        vm.onAnswer(question: question, value: val)
                    }
                )
                VStack(spacing: 12) {
                    ForEach(prompts) { prompt in
                        HStack {
                            Text(prompt.text)
                            Spacer()
                            Picker("Connect", selection: Binding(
                                get: { linksVal.wrappedValue[prompt.id] ?? "" },
                                set: { aId in
                                    var dict = linksVal.wrappedValue
                                    if aId.isEmpty { dict.removeValue(forKey: prompt.id) }
                                    else { dict[prompt.id] = aId }
                                    linksVal.wrappedValue = dict
                                }
                            )) {
                                Text("Select target...").tag("")
                                ForEach(answers) { ans in
                                    Text(ans.text).tag(ans.id)
                                }
                            }
                            .pickerStyle(MenuPickerStyle())
                        }
                    }
                }
            }
        }
    }
}

extension AnswerValue {
    var textValue: String? {
        if case let .text(s) = self { return s }
        return nil
    }
    var flagValue: Bool? {
        if case let .flag(b) = self { return b }
        return nil
    }
    var linksValue: [String: String]? {
        if case let .links(d) = self { return d }
        return nil
    }
}
