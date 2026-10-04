package com.student.quiz.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.student.quiz.data.Question
import com.student.quiz.data.QuizClock
import com.student.quiz.net.ConnState
import kotlinx.coroutines.delay

/** Step 2: one question at a time, with a countdown and answer chips. */
@Composable
fun QuizScreen(quiz: QuizUiState, conn: ConnState?, vm: AppViewModel, modifier: Modifier = Modifier) {
    val view = LocalView.current
    // Keep the screen on while the quiz is running.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // A rejected connection here means a kick or a closed session.
    LaunchedEffect(conn) {
        if (conn is ConnState.Rejected) vm.backToJoin(conn.message)
    }

    val question = quiz.current

    // Ticking here drives the countdown; the server stays authoritative for time.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(quiz.endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            // Stop the ticker once the time is up: the server ends the quiz, and
            // this screen waits for that message without recomposing.
            if (QuizClock.remainingMs(quiz.endsAt, now, quiz.offsetMs) <= 0) break
            delay(1000)
        }
    }
    val remaining = QuizClock.remainingMs(quiz.endsAt, now, quiz.offsetMs)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        CountdownBar(remaining)
        QuizChips(quiz, vm)

        if (question == null) {
            Text("This quiz has no questions.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Question ${quiz.currentIndex + 1} of ${quiz.questions.size}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(question.body, style = MaterialTheme.typography.titleMedium)
                QuestionInput(question, quiz, vm)
                SaveIndicator(question, quiz, vm)
                Spacer(Modifier.height(8.dp))
            }
        }

        NavRow(quiz, question, vm)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CountdownBar(remaining: Long) {
    val over = remaining <= 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (over) {
            Text(
                "Time's up",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Spacer(Modifier.height(1.dp))
        }
        Text(
            QuizClock.format(remaining),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}
@Composable
private fun QuizChips(quiz: QuizUiState, vm: AppViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        quiz.questions.forEachIndexed { index, question ->
            val answered = quiz.isAnswered(question)
            FilterChip(
                selected = index == quiz.currentIndex,
                onClick = {
                    quiz.current?.let(vm::onLeaveQuestion)
                    vm.selectQuestion(index)
                },
                label = {
                    // A bold chip number means the question has an answer.
                    Text(
                        "${index + 1}",
                        fontWeight = if (answered) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun NavRow(quiz: QuizUiState, question: Question?, vm: AppViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                question?.let(vm::onLeaveQuestion)
                vm.previousQuestion()
            },
            enabled = quiz.currentIndex > 0,
            modifier = Modifier.weight(1f),
        ) { Text("Previous") }
        Button(
            onClick = {
                question?.let(vm::onLeaveQuestion)
                vm.nextQuestion()
            },
            enabled = question != null && quiz.currentIndex < quiz.questions.lastIndex,
            modifier = Modifier.weight(1f),
        ) { Text("Next") }
    }
}