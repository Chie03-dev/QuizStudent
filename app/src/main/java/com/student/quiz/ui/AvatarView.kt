package com.student.quiz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.student.quiz.data.AvatarSpec
import kotlin.math.abs

@Composable
fun AvatarView(
    spec: AvatarSpec,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = size.toPx()
            val h = size.toPx()
            val center = Offset(w / 2f, h / 2f)

            val seedHash = abs(spec.seed.hashCode())
            val inkColor = Color(0xFF1A202C) // Deep crisp ink outline
            val skinTone = Color.hsl(
                hue = spec.hue.toFloat().coerceIn(0f, 360f),
                saturation = 0.55f,
                lightness = 0.78f,
            )

            // 1. Ears
            drawEars(center, w * 0.42f, skinTone, inkColor)

            // 2. Face Silhouette
            drawFaceShape(spec.face, center, w * 0.38f, h * 0.42f, skinTone, inkColor)

            // 3. Hair
            drawHair(seedHash, center, w, inkColor)

            // 4. Eyes
            drawEyes(spec.eyes, center, w, inkColor)

            // 5. Mouth
            drawWimpyMouth(spec.mouth, center, w, inkColor)

            // 6. Accessory
            drawWimpyAccessory(spec.accessory, center, w, h, inkColor)
        }
    }
}

private fun DrawScope.drawEars(center: Offset, rx: Float, skin: Color, ink: Color) {
    val earSize = Size(12.dp.toPx(), 18.dp.toPx())
    // Left ear
    drawOval(color = skin, topLeft = Offset(center.x - rx - earSize.width * 0.7f, center.y - earSize.height * 0.5f), size = earSize)
    drawOval(color = ink, topLeft = Offset(center.x - rx - earSize.width * 0.7f, center.y - earSize.height * 0.5f), size = earSize, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

    // Right ear
    drawOval(color = skin, topLeft = Offset(center.x + rx - earSize.width * 0.3f, center.y - earSize.height * 0.5f), size = earSize)
    drawOval(color = ink, topLeft = Offset(center.x + rx - earSize.width * 0.3f, center.y - earSize.height * 0.5f), size = earSize, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawFaceShape(face: Int, center: Offset, rx: Float, ry: Float, skin: Color, ink: Color) {
    val top = center.y - ry
    val left = center.x - rx
    val right = center.x + rx
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (face) {
        0 -> { // Classic Round
            drawOval(color = skin, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f))
            drawOval(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), style = stroke)
        }
        1 -> { // Soft Rounded Square
            drawRoundRect(color = skin, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.5f, ry * 0.5f))
            drawRoundRect(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.5f, ry * 0.5f), style = stroke)
        }
        2 -> { // Oval / Tall
            drawOval(color = skin, topLeft = Offset(center.x - rx * 0.85f, top * 0.9f), size = Size(rx * 1.7f, ry * 2.2f))
            drawOval(color = ink, topLeft = Offset(center.x - rx * 0.85f, top * 0.9f), size = Size(rx * 1.7f, ry * 2.2f), style = stroke)
        }
        3 -> { // Square Jaw
            drawRoundRect(color = skin, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.2f, ry * 0.2f))
            drawRoundRect(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.2f, ry * 0.2f), style = stroke)
        }
        4 -> { // Heart / Chiseled
            val path = Path().apply {
                moveTo(center.x, top + ry * 2f)
                cubicTo(left - rx * 0.5f, center.y, left, top, center.x, top + ry * 0.4f)
                cubicTo(right, top, right + rx * 0.5f, center.y, center.x, top + ry * 2f)
            }
            drawPath(path, skin)
            drawPath(path, ink, style = stroke)
        }
        else -> {
            drawOval(color = skin, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f))
            drawOval(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), style = stroke)
        }
    }
}

private fun DrawScope.drawHair(seed: Int, center: Offset, size: Float, ink: Color) {
    val hairType = seed % 5
    val headTop = center.y - size * 0.42f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    when (hairType) {
        0 -> { // Spiky tufts
            drawLine(ink, Offset(center.x - size * 0.12f, headTop + size * 0.05f), Offset(center.x - size * 0.18f, headTop - size * 0.15f), stroke.width, stroke.cap)
            drawLine(ink, Offset(center.x, headTop + size * 0.02f), Offset(center.x + size * 0.02f, headTop - size * 0.2f), stroke.width, stroke.cap)
            drawLine(ink, Offset(center.x + size * 0.12f, headTop + size * 0.05f), Offset(center.x + size * 0.2f, headTop - size * 0.12f), stroke.width, stroke.cap)
        }
        1 -> { // Curly top
            val path = Path().apply {
                moveTo(center.x - size * 0.2f, headTop + size * 0.05f)
                quadraticTo(center.x - size * 0.1f, headTop - size * 0.18f, center.x, headTop - size * 0.05f)
                quadraticTo(center.x + size * 0.1f, headTop - size * 0.22f, center.x + size * 0.2f, headTop + size * 0.02f)
            }
            drawPath(path, ink, style = stroke)
        }
        2 -> { // Neat side part
            val path = Path().apply {
                moveTo(center.x - size * 0.3f, headTop + size * 0.08f)
                quadraticTo(center.x, headTop - size * 0.12f, center.x + size * 0.25f, headTop + size * 0.05f)
            }
            drawPath(path, ink, style = stroke)
        }
        3 -> { // Messy bedhead
            drawLine(ink, Offset(center.x - size * 0.22f, headTop + size * 0.05f), Offset(center.x - size * 0.12f, headTop - size * 0.12f), stroke.width, stroke.cap)
            drawLine(ink, Offset(center.x - size * 0.05f, headTop + size * 0.02f), Offset(center.x + size * 0.08f, headTop - size * 0.16f), stroke.width, stroke.cap)
            drawLine(ink, Offset(center.x + size * 0.1f, headTop + size * 0.05f), Offset(center.x + size * 0.22f, headTop - size * 0.08f), stroke.width, stroke.cap)
        }
        else -> { // Classic fringe
            val path = Path().apply {
                moveTo(center.x - size * 0.25f, headTop + size * 0.05f)
                lineTo(center.x - size * 0.1f, headTop + size * 0.12f)
                lineTo(center.x + size * 0.05f, headTop + size * 0.05f)
                lineTo(center.x + size * 0.25f, headTop + size * 0.08f)
            }
            drawPath(path, ink, style = stroke)
        }
    }
}

private fun DrawScope.drawEyes(eyes: Int, center: Offset, size: Float, ink: Color) {
    val eyeY = center.y - size * 0.05f
    val leftX = center.x - size * 0.14f
    val rightX = center.x + size * 0.14f
    val r = size * 0.05f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    when (eyes) {
        0 -> {} // None
        1 -> { // Closed happy arcs
            drawArc(ink, 10f, 160f, false, topLeft = Offset(leftX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
            drawArc(ink, 10f, 160f, false, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
        }
        2 -> { // Expressive anime/cartoon eyes (large white with big dark pupils & highlight)
            drawCircle(Color.White, r * 1.2f, Offset(leftX, eyeY))
            drawCircle(ink, r * 1.2f, Offset(leftX, eyeY), style = stroke)
            drawCircle(ink, r * 0.6f, Offset(leftX - r * 0.15f, eyeY))
            drawCircle(Color.White, r * 0.35f, Offset(leftX - r * 0.3f, eyeY - r * 0.3f))

            drawCircle(Color.White, r * 1.2f, Offset(rightX, eyeY))
            drawCircle(ink, r * 1.2f, Offset(rightX, eyeY), style = stroke)
            drawCircle(ink, r * 0.6f, Offset(rightX - r * 0.15f, eyeY))
            drawCircle(Color.White, r * 0.35f, Offset(rightX - r * 0.3f, eyeY - r * 0.3f))
        }
        3 -> { // Wink
            drawCircle(Color.White, r * 1.2f, Offset(leftX, eyeY))
            drawCircle(ink, r * 1.2f, Offset(leftX, eyeY), style = stroke)
            drawCircle(ink, r * 0.6f, Offset(leftX, eyeY))
            drawCircle(Color.White, r * 0.35f, Offset(leftX - r * 0.3f, eyeY - r * 0.3f))

            drawArc(ink, 10f, 160f, false, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
        }
        4 -> { // Happy upward curves
            drawArc(ink, 190f, 160f, false, topLeft = Offset(leftX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
            drawArc(ink, 190f, 160f, false, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
        }
        5 -> { // Squint / cool dashes
            drawLine(ink, Offset(leftX - r, eyeY), Offset(leftX + r, eyeY), stroke.width, stroke.cap)
            drawLine(ink, Offset(rightX - r, eyeY), Offset(rightX + r, eyeY), stroke.width, stroke.cap)
        }
        6 -> { // Simple dot eyes
            drawCircle(ink, r * 0.65f, Offset(leftX, eyeY))
            drawCircle(ink, r * 0.65f, Offset(rightX, eyeY))
        }
        7 -> { // Half closed
            drawArc(ink, 0f, 180f, true, topLeft = Offset(leftX - r, eyeY - r), size = Size(r * 2, r * 2))
            drawArc(ink, 0f, 180f, true, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2))
        }
        else -> { // Sparkle wide eyes
            drawCircle(Color.White, r * 1.3f, Offset(leftX, eyeY))
            drawCircle(ink, r * 1.3f, Offset(leftX, eyeY), style = stroke)
            drawCircle(ink, r * 0.5f, Offset(leftX, eyeY))
            drawCircle(Color.White, r * 0.4f, Offset(leftX - r * 0.3f, eyeY - r * 0.3f))

            drawCircle(Color.White, r * 1.3f, Offset(rightX, eyeY))
            drawCircle(ink, r * 1.3f, Offset(rightX, eyeY), style = stroke)
            drawCircle(ink, r * 0.5f, Offset(rightX, eyeY))
            drawCircle(Color.White, r * 0.4f, Offset(rightX - r * 0.3f, eyeY - r * 0.3f))
        }
    }
}

private fun DrawScope.drawWimpyMouth(mouth: Int, center: Offset, size: Float, ink: Color) {
    val mouthY = center.y + size * 0.18f
    val w = size * 0.22f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    when (mouth) {
        0 -> {} // None
        1 -> { // Friendly Smile
            drawArc(ink, 10f, 160f, false, topLeft = Offset(center.x - w / 2f, mouthY - w / 4f), size = Size(w, w / 2f), style = stroke)
        }
        2 -> { // Big Open Grin
            drawArc(ink, 0f, 180f, false, topLeft = Offset(center.x - w / 2f, mouthY - w / 3f), size = Size(w, w * 0.6f), style = stroke)
            drawLine(ink, Offset(center.x - w / 2f, mouthY), Offset(center.x + w / 2f, mouthY), stroke.width, stroke.cap)
        }
        3 -> { // Laughing Open Box
            drawRoundRect(Color.White, topLeft = Offset(center.x - w / 2f, mouthY - w * 0.1f), size = Size(w, w * 0.5f), cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(Color.White, topLeft = Offset(center.x - w / 2f, mouthY - w * 0.1f), size = Size(w, w * 0.5f), cornerRadius = CornerRadius(6f, 6f), style = stroke)
        }
        4 -> { // Sad Frown
            drawArc(ink, 190f, 160f, false, topLeft = Offset(center.x - w / 2f, mouthY), size = Size(w, w / 2f), style = stroke)
        }
        5 -> { // Neutral line
            drawLine(ink, Offset(center.x - w / 2f, mouthY), Offset(center.x + w / 2f, mouthY), stroke.width, stroke.cap)
        }
        else -> { // Surprised O
            drawCircle(Color.White, w * 0.25f, Offset(center.x, mouthY))
            drawCircle(ink, w * 0.25f, Offset(center.x, mouthY), style = stroke)
        }
    }
}

private fun DrawScope.drawWimpyAccessory(accessory: Int, center: Offset, w: Float, h: Float, ink: Color) {
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (accessory) {
        1 -> { // Stylish Round Glasses
            val eyeY = center.y - w * 0.05f
            val r = w * 0.12f
            drawCircle(Color.White, r, Offset(center.x - r * 1.15f, eyeY))
            drawCircle(ink, r, Offset(center.x - r * 1.15f, eyeY), style = stroke)

            drawCircle(Color.White, r, Offset(center.x + r * 1.15f, eyeY))
            drawCircle(ink, r, Offset(center.x + r * 1.15f, eyeY), style = stroke)

            drawLine(ink, Offset(center.x - r * 0.2f, eyeY), Offset(center.x + r * 0.2f, eyeY), stroke.width, stroke.cap)
        }
        2 -> { // Cool Sunglasses
            val eyeY = center.y - w * 0.05f
            val r = w * 0.12f
            drawRect(ink, topLeft = Offset(center.x - r * 2.3f, eyeY - r * 0.9f), size = Size(r * 2.2f, r * 1.8f))
            drawRect(ink, topLeft = Offset(center.x + r * 0.1f, eyeY - r * 0.9f), size = Size(r * 2.2f, r * 1.8f))
            drawLine(ink, Offset(center.x - r * 0.1f, eyeY), Offset(center.x + r * 0.1f, eyeY), stroke.width * 1.3f, stroke.cap)
        }
        3 -> { // Cozy Beanie Hat with Pom-pom
            val headTop = center.y - h * 0.42f
            drawArc(Color(0xFFE53E3E), 180f, 180f, true, topLeft = Offset(center.x - w * 0.38f, headTop - w * 0.25f), size = Size(w * 0.76f, w * 0.5f))
            drawArc(ink, 180f, 180f, true, topLeft = Offset(center.x - w * 0.38f, headTop - w * 0.25f), size = Size(w * 0.76f, w * 0.5f), style = stroke)
            drawLine(ink, Offset(center.x - w * 0.36f, headTop), Offset(center.x + w * 0.36f, headTop), stroke.width, stroke.cap)
            drawCircle(Color.White, w * 0.1f, Offset(center.x, headTop - w * 0.25f))
            drawCircle(ink, w * 0.1f, Offset(center.x, headTop - w * 0.25f), style = stroke)
        }
        4 -> { // Headband
            val headY = center.y - h * 0.32f
            drawArc(Color(0xFF3182CE), 195f, 150f, false, topLeft = Offset(center.x - w * 0.42f, headY - w * 0.1f), size = Size(w * 0.84f, w * 0.2f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        }
        5 -> { // Bowtie
            val chinY = center.y + h * 0.45f
            val bw = w * 0.15f
            val path = Path().apply {
                moveTo(center.x, chinY)
                lineTo(center.x - bw, chinY - bw * 0.6f)
                lineTo(center.x - bw, chinY + bw * 0.6f)
                close()
                moveTo(center.x, chinY)
                lineTo(center.x + bw, chinY - bw * 0.6f)
                lineTo(center.x + bw, chinY + bw * 0.6f)
                close()
            }
            drawPath(path, Color(0xFFD69E2E))
            drawPath(path, ink, style = stroke)
            drawCircle(ink, 4.dp.toPx(), Offset(center.x, chinY))
        }
        6 -> { // Crown
            val headTop = center.y - h * 0.46f
            val cw = w * 0.5f
            val crownPath = Path().apply {
                moveTo(center.x - cw / 2f, headTop + w * 0.15f)
                lineTo(center.x - cw / 2f, headTop - w * 0.1f)
                lineTo(center.x - cw / 4f, headTop)
                lineTo(center.x, headTop - w * 0.15f)
                lineTo(center.x + cw / 4f, headTop)
                lineTo(center.x + cw / 2f, headTop - w * 0.1f)
                lineTo(center.x + cw / 2f, headTop + w * 0.15f)
                close()
            }
            drawPath(crownPath, Color(0xFFECC94B))
            drawPath(crownPath, ink, style = stroke)
        }
        else -> {}
    }
}
