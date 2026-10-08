package com.student.quiz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import com.student.quiz.data.MatchingLogic
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
        shape = MaterialTheme.shapes.medium,
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
            if (selected == flag) {
                Button(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) { Text(label) }
            } else {
                OutlinedButton(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) { Text(label) }
            }
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

/** An inline expandable list per left item; sent as a map leftId -> rightId. */
@Composable
private fun MatchingInput(
    question: Question.Matching,
    value: AnswerValue,
    vm: AppViewModel,
) {
    val links = (value as? AnswerValue.Links)?.links.orEmpty()
    var expandedLeftId by remember(question.qid) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.left.forEach { leftItem ->
            val chosenRightId = links[leftItem.id]
            val chosenText = question.right.firstOrNull { it.id == chosenRightId }?.text
            val isExpanded = expandedLeftId == leftItem.id

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        expandedLeftId = if (isExpanded) null else leftItem.id
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${leftItem.text.ifBlank { leftItem.id }}: ${chosenText?.ifBlank { "(no text)" } ?: "Choose…"}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        question.right.forEach { rightItem ->
                            val otherOwnerId = MatchingLogic.holderOf(links, leftItem.id, rightItem.id)
                            val isTaken = otherOwnerId != null
                            val isSelected = chosenRightId == rightItem.id

                            OutlinedButton(
                                onClick = {
                                    expandedLeftId = null
                                    val updated = MatchingLogic.selectRight(links, leftItem.id, rightItem.id)
                                    vm.onAnswer(question, AnswerValue.Links(updated), immediate = true)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val label = buildString {
                                    append(rightItem.text.ifBlank { rightItem.id })
                                    if (isTaken) {
                                        append(" (Taken)")
                                    }
                                    if (isSelected) {
                                        append(" ✓")
                                    }
                                }
                                Text(label)
                            }
                        }
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