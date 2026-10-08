package com.student.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.student.quiz.net.ConnState

/** Step 1 end state: in the lobby, waiting for the instructor. */
@Composable
fun LobbyScreen(name: String, conn: ConnState?, vm: AppViewModel, modifier: Modifier = Modifier) {
    // A rejected join (bad PIN, name taken, kick) returns to the Join screen.
    LaunchedEffect(conn) {
        if (conn is ConnState.Rejected) vm.backToJoin(conn.message)
    }

    Column(
        modifier = modifier.fillMaxSize().systemBarPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InfoCard(modifier = Modifier.widthIn(max = 520.dp)) {
            Text(
                "You're in, $name.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Waiting for the instructor to start.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            when (conn) {
                is ConnState.Joined -> StatusPill(
                    text = "Connected",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                is ConnState.Reconnecting -> StatusPill(
                    text = "Reconnecting…",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                is ConnState.Connecting -> StatusPill(
                    text = "Connecting…",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                is ConnState.Rejected -> Unit
                null -> StatusPill(
                    text = "Connecting…",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** Step 2 end state. No score and no answers are shown, on purpose. */
@Composable
fun EndedScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().systemBarPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InfoCard(modifier = Modifier.widthIn(max = 520.dp)) {
            Text(
                "Quiz ended",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Wait for your instructor.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { vm.backToJoin() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Leave") }
        }
    }
}

/**
 * Step 5: full-screen done state. No answers can be changed after this; the
 * pin is released but the quiz still counts as running so screenshots stay
 * blocked.
 */
@Composable
fun FinishedScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().systemBarPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InfoCard(modifier = Modifier.widthIn(max = 520.dp)) {
            Text(
                "You're done.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Wait for your instructor.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}