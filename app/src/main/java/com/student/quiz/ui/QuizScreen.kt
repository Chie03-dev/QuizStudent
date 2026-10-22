package com.student.quiz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.student.quiz.data.FinishFlow
import com.student.quiz.data.Question
import com.student.quiz.data.QuizClock
import com.student.quiz.data.QuizNav
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
    LaunchedEffect(quiz.endsAt, quiz.paused) {
        if (!quiz.paused) {
            while (true) {
                now = System.currentTimeMillis()
                // Stop the ticker once the time is up: the server ends the quiz, and
                // this screen waits for that message without recomposing.
                if (QuizClock.remainingMs(quiz.endsAt, now, quiz.offsetMs) <= 0) break
                delay(1000)
            }
        }
    }
    val remaining = QuizClock.remainingMs(quiz.endsAt, now, quiz.offsetMs)
    val state by vm.ui.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarPaddingOnly()
            .navigationBarPaddingOnly(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .imePadding(),
        ) {
            CountdownBar(remaining, quiz.paused)
            QuizChips(quiz, vm)
            QuizProgress(quiz)

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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Question ${quiz.currentIndex + 1} of ${quiz.questions.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(question.body, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    QuestionInput(question, quiz, vm)
                    SaveIndicator(question, quiz, vm)
                    Spacer(Modifier.height(8.dp))
                }
            }

            NavRow(quiz, question, vm)
            Spacer(Modifier.height(12.dp))
            FinishState(vm)
        }

        state.finishDialog?.let { unanswered ->
            FinishDialog(unanswered, vm)
        }
    }
}

@Composable
private fun FinishState(vm: AppViewModel) {
    val state by vm.ui.collectAsState()
    // Step 5: the unacked queue must be empty before finish goes out, so
    // while it drains the student waits here.
    if (state.savingAnswers) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text("Saving your answers...")
        }
    }
    state.saveError?.let { error ->
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FinishDialog(unanswered: Int, vm: AppViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                vm.closeFinishDialog()
            },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {},
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    FinishFlow.dialogText(unanswered),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (FinishFlow.showUnansweredOffer(unanswered)) {
                        TextButton(onClick = vm::showUnanswered) {
                            Text("Show unanswered")
                        }
                    }
                    TextButton(onClick = vm::closeFinishDialog) {
                        Text("Cancel")
                    }
                    TextButton(onClick = vm::confirmFinish) {
                        Text("Finish")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownBar(remaining: Long, paused: Boolean) {
    val over = remaining <= 0
    // The timer lives in its own card so it reads as chrome, not content.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    over -> "Time's up"
                    paused -> "Time paused"
                    else -> "Time left"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                QuizClock.format(remaining),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
@Composable
private fun QuizChips(quiz: QuizUiState, vm: AppViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        quiz.questions.forEachIndexed { index, question ->
            // The current question is also marked, so the three answer states
            // stay independent of where the student is.
            val here = if (index == quiz.currentIndex) "$CURRENT_MARKER" else ""
            when (quiz.chipState(question)) {
                // Answered: filled.
                QuizNav.ChipState.ANSWERED -> FilterChip(
                    selected = true,
                    onClick = { vm.selectQuestion(index) },
                    label = { Text("$here${index + 1}") },
                )
                // Seen but unanswered: outlined with a marker.
                QuizNav.ChipState.VISITED -> FilterChip(
                    selected = false,
                    onClick = { vm.selectQuestion(index) },
                    label = { Text("$here$VISITED_MARKER${index + 1}") },
                )
                // Not reached yet: plain.
                QuizNav.ChipState.UNVISITED -> FilterChip(
                    selected = false,
                    onClick = { vm.selectQuestion(index) },
                    label = { Text("$here${index + 1}") },
                )
            }
        }
    }
}

/** Chip markers: the question on screen, and a seen-but-unanswered question. */
private const val CURRENT_MARKER = "\u25B6 "
private const val VISITED_MARKER = "\u2022 "

@Composable
private fun NavRow(quiz: QuizUiState, question: Question?, vm: AppViewModel) {
    val count = quiz.questions.size
    // None of these depend on whether the current question is answered.
    val last = QuizNav.isLast(count, quiz.currentIndex)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { vm.previousQuestion() },
            enabled = question != null && QuizNav.canGoPrevious(quiz.currentIndex),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        ) { Text("Previous") }
        if (last) {
            // Last question: finish goes through its confirmation dialog.
            Button(
                onClick = { vm.openFinishDialog() },
                enabled = question != null,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            ) { Text("Finish") }
        } else {
            Button(
                onClick = { vm.nextQuestion() },
                enabled = question != null && QuizNav.canGoNext(count, quiz.currentIndex),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            ) { Text("Next") }
            // Skipping sends nothing: only a pending debounced answer is flushed.
            OutlinedButton(
                onClick = { vm.skipQuestion() },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            ) { Text("Skip") }
        }
    }
}

@Composable
private fun QuizProgress(quiz: QuizUiState) {
    val answeredCount = quiz.questions.count { quiz.isAnswered(it) }
    val total = quiz.questions.size
    val progress = if (total > 0) answeredCount.toFloat() / total.toFloat() else 0f

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Progress",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$answeredCount of $total answered",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}