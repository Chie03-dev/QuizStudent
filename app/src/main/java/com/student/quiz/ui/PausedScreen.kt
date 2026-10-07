package com.student.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.student.quiz.data.LockState

/**
 * Step 3: the paused screen. It replaces the quiz entirely, so nothing of the
 * question can be read, glanced at or screenshotted while the student is
 * waiting for the instructor. The reason from the server picks the wording.
 */
@Composable
fun PausedScreen(lock: LockState, vm: AppViewModel, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Paused",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (lock.pausedReason) {
                    "network" -> "The connection to the instructor's computer dropped. " +
                        "Your answers are kept and sent when you are back."
                    // "focus" covers a lost window, the background and unpinning.
                    else -> "The quiz app lost focus, so the quiz is hidden. " +
                        "Your answers are kept."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                // Only honest when the phone really has focus and is pinned:
                // asking to resume from the recents screen would be pointless.
                onClick = vm::requestResume,
                enabled = lock.paused && lock.canResume,
            ) { Text("Ask to resume") }
            if (lock.needsPin) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = vm::pinAgain) { Text("Pin again") }
            }
            if (!lock.canResume) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (lock.needsPin) "Pin the quiz again to ask to resume."
                    else "Come back to the quiz to ask to resume.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                // The clock never stops for a paused student, so say so.
                "The quiz timer keeps running.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}