package com.student.quiz.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/** Step 1 entry point: name + scan QR or enter the 4-digit PIN. */
@Composable
fun JoinScreen(
    state: JoinUiState,
    vm: AppViewModel,
    onScanClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (original != null) {
                    val maxDim = 200
                    val width = original.width
                    val height = original.height
                    val ratio = width.toFloat() / height.toFloat()
                    val newWidth: Int
                    val newHeight: Int
                    if (width > height) {
                        newWidth = maxDim
                        newHeight = (maxDim / ratio).toInt()
                    } else {
                        newHeight = maxDim
                        newWidth = (maxDim * ratio).toInt()
                    }
                    val scaled = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
                    val outputStream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                    val bytes = outputStream.toByteArray()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    vm.updateProfileImage("data:image/jpeg;base64,$base64")
                }
            }
        }
    }

    val decodedBitmap = remember(state.profileImageBase64) {
        val b64 = state.profileImageBase64
        if (b64 != null) {
            runCatching {
                val cleanB64 = b64.substringAfter("base64,")
                val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        AppHeader(
            title = "Join the quiz",
            subtitle = "Scan the instructor's QR code, or type the 4-digit PIN.",
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { imagePicker.launch("image/*") }
                    .border(2.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (decodedBitmap != null) {
                    Image(
                        bitmap = decodedBitmap.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = state.name.take(2).uppercase().ifBlank { "📷" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { imagePicker.launch("image/*") }) {
                Text(if (decodedBitmap != null) "Change profile picture" else "Upload profile picture")
            }
        }

        InfoCard(
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onNameChange,
                label = { Text("Your name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.pin,
                onValueChange = vm::onPinChange,
                label = { Text("PIN") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                // Kept as a String so a PIN like 0042 keeps its zeros.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onScanClicked,
                enabled = state.name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Scan QR", style = MaterialTheme.typography.titleMedium) }

            Button(
                onClick = vm::joinByPin,
                enabled = state.name.isNotBlank() && state.pin.length == 4 && !state.busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (state.busy) "Joining…" else "Join with PIN", style = MaterialTheme.typography.titleMedium) }
        }

        if (state.busy) {
            CircularProgressIndicator()
        }
        state.status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (state.showManualEntry) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("or enter the address", style = MaterialTheme.typography.labelMedium)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            InfoCard(
                modifier = Modifier.widthIn(max = 520.dp),
            ) {
                OutlinedTextField(
                    value = state.ip,
                    onValueChange = vm::onIpChange,
                    label = { Text("Instructor's IP, e.g. 192.168.0.235") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
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
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = vm::joinManually,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Join with address", style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = { vm.showManualEntry(false) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Try the PIN again") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
