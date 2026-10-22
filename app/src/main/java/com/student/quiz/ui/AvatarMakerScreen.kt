package com.student.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.student.quiz.data.AvatarSpec
import java.util.UUID

@Composable
fun AvatarMakerScreen(
    initialSpec: AvatarSpec,
    onSave: (AvatarSpec) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var spec by remember { mutableStateOf(initialSpec) }

    val faceNames = listOf("Round", "Soft", "Oval", "Square", "Heart", "Diamond", "Blob")
    val eyeNames = listOf("None", "Closed", "Open", "Wink", "Happy", "Squint", "Up", "Half", "Wide")
    val mouthNames = listOf("None", "Smile", "Grin", "Laughing", "Sad", "Neutral", "Surprised")
    val accessoryNames = listOf("None", "Glasses", "Sunglasses", "Beanie", "Headband", "Bowtie", "Crown")

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarPaddingOnly()
            .navigationBarPaddingOnly()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Customize Your Avatar", style = MaterialTheme.typography.headlineMedium)

        // Live Preview Card
        InfoCard(
            modifier = Modifier.widthIn(max = 400.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AvatarView(spec = spec, size = 120.dp)

                OutlinedButton(
                    onClick = {
                        spec = spec.copy(seed = UUID.randomUUID().toString())
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Randomize Variation")
                }
            }
        }

        // Pickers Container
        InfoCard(
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            // Skin Tone Hue Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Skin Tone Hue: ${spec.hue}°", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = spec.hue.toFloat(),
                    onValueChange = { spec = spec.copy(hue = it.toInt()) },
                    valueRange = 0f..360f,
                )
            }

            // Face Shape Picker
            PickerRow(
                title = "Face Shape",
                currentName = faceNames.getOrElse(spec.face) { "Round" },
                onPrev = { spec = spec.copy(face = (spec.face - 1 + faceNames.size) % faceNames.size) },
                onNext = { spec = spec.copy(face = (spec.face + 1) % faceNames.size) },
            )

            // Eyes Picker
            PickerRow(
                title = "Eyes",
                currentName = eyeNames.getOrElse(spec.eyes) { "Open" },
                onPrev = { spec = spec.copy(eyes = (spec.eyes - 1 + eyeNames.size) % eyeNames.size) },
                onNext = { spec = spec.copy(eyes = (spec.eyes + 1) % eyeNames.size) },
            )

            // Mouth Picker
            PickerRow(
                title = "Mouth",
                currentName = mouthNames.getOrElse(spec.mouth) { "Smile" },
                onPrev = { spec = spec.copy(mouth = (spec.mouth - 1 + mouthNames.size) % mouthNames.size) },
                onNext = { spec = spec.copy(mouth = (spec.mouth + 1) % mouthNames.size) },
            )

            // Accessory Picker
            PickerRow(
                title = "Accessory",
                currentName = accessoryNames.getOrElse(spec.accessory) { "None" },
                onPrev = { spec = spec.copy(accessory = (spec.accessory - 1 + accessoryNames.size) % accessoryNames.size) },
                onNext = { spec = spec.copy(accessory = (spec.accessory + 1) % accessoryNames.size) },
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onSave(AvatarSpec.sanitize(spec.seed, spec.hue, spec.face, spec.eyes, spec.mouth, spec.accessory)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Save Avatar", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Cancel")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PickerRow(
    title: String,
    currentName: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPrev, shape = RoundedCornerShape(8.dp)) {
                Text("◄")
            }
            Text(currentName, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            OutlinedButton(onClick = onNext, shape = RoundedCornerShape(8.dp)) {
                Text("►")
            }
        }
    }
}
