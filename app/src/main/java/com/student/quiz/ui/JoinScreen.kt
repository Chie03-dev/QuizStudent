package com.student.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Step 1 entry point: name + scan QR or enter the 4-digit PIN. */
@Composable
fun JoinScreen(
    state: JoinUiState,
    vm: AppViewModel,
    onScanClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Join the quiz", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ask the instructor for the QR code, or type the 4-digit PIN.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = state.name,
            onValueChange = vm::onNameChange,
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.pin,
            onValueChange = vm::onPinChange,
            label = { Text("PIN") },
            singleLine = true,
            // Kept as a String so a PIN like 0042 keeps its zeros.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onScanClicked,
            enabled = state.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Scan QR") }

        Button(
            onClick = vm::joinByPin,
            enabled = state.name.isNotBlank() && state.pin.length == 4 && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.busy) "Joining…" else "Join with PIN") }

        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        state.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))

        if (state.showManualEntry) {
            Text("Enter the address manually", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.ip,
                onValueChange = vm::onIpChange,
                label = { Text("Instructor's IP, e.g. 192.168.0.235") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = vm::onPortChange,
                label = { Text("Port, e.g. 8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = vm::joinManually,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Join with address") }
            OutlinedButton(
                onClick = { vm.showManualEntry(false) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Try the PIN again") }
        }
    }
}