package com.student.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("You're in, $name.", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Waiting for the instructor to start.",
            style = MaterialTheme.typography.bodyLarge,
        )
        when (conn) {
            is ConnState.Joined -> Text("Connected", style = MaterialTheme.typography.bodyMedium)
            is ConnState.Reconnecting -> Text("Reconnecting…", style = MaterialTheme.typography.bodyMedium)
            is ConnState.Connecting -> {
                CircularProgressIndicator()
                Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
            }
            is ConnState.Rejected -> Unit
            null -> Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Step 2 end state. No score and no answers are shown, on purpose. */
@Composable
fun EndedScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Quiz ended", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Wait for your instructor.",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = { vm.backToJoin() }) { Text("Leave") }
    }
}