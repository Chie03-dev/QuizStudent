package com.student.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A pause badge: a ringed dot, centered above the card.
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary,
                    ) {}
                }
            }

            InfoCard(modifier = Modifier.widthIn(max = 520.dp)) {
                Text(
                    "Paused",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    when (lock.pausedReason) {
                        "network" -> "The connection to the instructor's computer dropped. " +
                            "Your answers are kept and sent when you are back."
                        // "focus" covers a lost window, the background and unpinning.
                        else -> "The quiz app lost focus, so the quiz is hidden. " +
                            "Your answers are kept."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    // Only honest when the phone really has focus and is pinned:
                    // asking to resume from the recents screen would be pointless.
                    onClick = vm::requestResume,
                    enabled = lock.paused && lock.canResume,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ask to resume", style = MaterialTheme.typography.labelLarge) }
                if (lock.needsPin) {
                    OutlinedButton(
                        onClick = vm::pinAgain,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pin again") }
                }
                if (!lock.canResume) {
                    Text(
                        if (lock.needsPin) "Pin the quiz again to ask to resume."
                        else "Come back to the quiz to ask to resume.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text(
                // The clock never stops for a paused student, so say so.
                "The quiz timer keeps running.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}