package com.student.quiz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.student.quiz.data.AnswerCodec
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.Question

/** One input per question type. */
@Composable
fun QuestionInput(question: Question, quiz: QuizUiState, vm: AppViewModel) {
    val value = quiz.answers[question.qid] ?: AnswerCodec.emptyValue(question)

    when (question) {
        is Question.Mcq -> McqInput(question, value, vm)
        is Question.Tf -> TfInput(question, value, vm)
        is Question.Identification -> OneTextField(question, value, vm, "Your answer")
        is Question.Problem -> OneTextField(question, value, vm, "Final answer")
        is Question.FillIn -> ListInput(question, value, vm, question.blanks, "Blank")
        is Question.Enumeration -> ListInput(question, value, vm, question.count, "Item")
        is Question.Matching -> MatchingInput(question, value, vm)
        is Question.Connect -> ConnectInput(question, value, vm)
    }
}

private fun textOf(value: AnswerValue): String = (value as? AnswerValue.Text)?.text.orEmpty()

@Composable
private fun AnswerTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun McqInput(question: Question.Mcq, value: AnswerValue, vm: AppViewModel) {
    val selected = textOf(value)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        question.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.onAnswer(question, AnswerValue.Text(option.id), immediate = true)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = option.id == selected,
                    onClick = {
                        vm.onAnswer(question, AnswerValue.Text(option.id), immediate = true)
                    },
                )
                Text(option.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** True/False sends a JSON boolean, not a string. */
@Composable
private fun TfInput(question: Question.Tf, value: AnswerValue, vm: AppViewModel) {
    val selected = (value as? AnswerValue.Flag)?.value
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true to "True", false to "False").forEach { (flag, label) ->
            val onClick = { vm.onAnswer(question, AnswerValue.Flag(flag), immediate = true) }
            if (selected == flag) Button(onClick = onClick) { Text(label) }
            else OutlinedButton(onClick = onClick) { Text(label) }
        }
    }
}
@Composable
private fun OneTextField(
    question: Question,
    value: AnswerValue,
    vm: AppViewModel,
    label: String,
) {
    AnswerTextField(
        value = textOf(value),
        onChange = { vm.onAnswer(question, AnswerValue.Text(it)) },
        label = label,
    )
}

/** One field per blank (fillin) or per item slot (enumeration); sent as an array. */
@Composable
private fun ListInput(
    question: Question,
    value: AnswerValue,
    vm: AppViewModel,
    slots: Int,
    label: String,
) {
    val received = (value as? AnswerValue.Items)?.items
    val items = List(slots) { index -> received?.getOrNull(index).orEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, text ->
            AnswerTextField(
                value = text,
                onChange = { newText ->
                    val updated = items.toMutableList().also { it[index] = newText }
                    vm.onAnswer(question, AnswerValue.Items(updated))
                },
                label = "$label ${index + 1}",
            )
        }
    }
}

/** A dropdown per left item; sent as a map leftId -> rightId. */
@Composable
private fun MatchingInput(
    question: Question.Matching,
    value: AnswerValue,
    vm: AppViewModel,
) {
    val links = (value as? AnswerValue.Links)?.links.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.left.forEach { leftItem ->
            val chosenText = question.right
                .firstOrNull { it.id == links[leftItem.id] }?.text
            var open by remember { mutableStateOf(false) }
            Column {
                Text(leftItem.text, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(
                    onClick = { open = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text(chosenText ?: "Choose…") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    question.right.forEach { rightItem ->
                        DropdownMenuItem(
                            text = { Text(rightItem.text) },
                            onClick = {
                                open = false
                                val updated = links + (leftItem.id to rightItem.id)
                                vm.onAnswer(question, AnswerValue.Links(updated), immediate = true)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "Saving…" while the answer is unacked, "Not saved, tap to retry" on BAD_ANSWER. */
@Composable
fun SaveIndicator(question: Question, quiz: QuizUiState, vm: AppViewModel) {
    when {
        question.qid in quiz.failed -> TextButton(onClick = { vm.retry(question) }) {
            Text(
                "Not saved, tap to retry",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        question.qid in quiz.unacked ->
            Text("Saving…", style = MaterialTheme.typography.bodyMedium)
        quiz.isAnswered(question) ->
            Text("Saved", style = MaterialTheme.typography.bodyMedium)
    }
}