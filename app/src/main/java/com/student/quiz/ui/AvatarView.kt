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
    size: Dp = 72.dp,
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
            val inkColor = Color(0xFF111111) // Wimpy Kid black ink outline
            val paperColor = Color(0xFFFFFDF9) // Clean notebook/paper fill

            // 1. Draw Face Silhouette (0..6) in cartoon ink style
            drawWimpyFace(spec.face, center, w * 0.4f, h * 0.45f, paperColor, inkColor)

            // 2. Draw Hair tufts based on seed
            drawWimpyHair(center, w, seedHash, inkColor)

            // 3. Draw Eyes (0..8)
            drawWimpyEyes(spec.eyes, center, w, inkColor)

            // 4. Draw Mouth (0..6)
            drawWimpyMouth(spec.mouth, center, w, inkColor)

            // 5. Draw Accessory (0..4)
            drawWimpyAccessory(spec.accessory, center, w, h, inkColor)
        }
    }
}

private fun DrawScope.drawWimpyFace(face: Int, center: Offset, rx: Float, ry: Float, fill: Color, ink: Color) {
    val path = Path()
    val top = center.y - ry
    val bottom = center.y + ry
    val left = center.x - rx
    val right = center.x + rx

    // Draw base head shape with ink outline
    when (face) {
        0 -> { // Round
            drawOval(color = fill, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f))
            drawOval(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        1 -> { // Soft rounded rect / square
            drawRoundRect(color = fill, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.6f, ry * 0.6f))
            drawRoundRect(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.6f, ry * 0.6f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        2 -> { // Oval tall
            drawOval(color = fill, topLeft = Offset(center.x - rx * 0.8f, top * 1.05f), size = Size(rx * 1.6f, ry * 2.1f))
            drawOval(color = ink, topLeft = Offset(center.x - rx * 0.8f, top * 1.05f), size = Size(rx * 1.6f, ry * 2.1f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        3 -> { // Square wimpy head
            drawRoundRect(color = fill, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.25f, ry * 0.25f))
            drawRoundRect(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.25f, ry * 0.25f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        4 -> { // Bean shape
            drawOval(color = fill, topLeft = Offset(left * 0.95f, top * 1.05f), size = Size(rx * 2.1f, ry * 1.9f))
            drawOval(color = ink, topLeft = Offset(left * 0.95f, top * 1.05f), size = Size(rx * 2.1f, ry * 1.9f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        5 -> { // Wide
            drawOval(color = fill, topLeft = Offset(left * 1.05f, top * 0.95f), size = Size(rx * 1.9f, ry * 2.1f))
            drawOval(color = ink, topLeft = Offset(left * 1.05f, top * 0.95f), size = Size(rx * 1.9f, ry * 2.1f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        else -> {
            drawOval(color = fill, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f))
            drawOval(color = ink, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawWimpyHair(center: Offset, size: Float, seed: Int, ink: Color) {
    val hairStyle = seed % 4
    val headTop = center.y - size * 0.42f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    when (hairStyle) {
        0 -> { // 3 funny tufts
            drawLine(ink, Offset(center.x - size * 0.1f, headTop), Offset(center.x - size * 0.15f, headTop - size * 0.12f), stroke.width, stroke.cap)
            drawLine(ink, Offset(center.x, headTop), Offset(center.x + size * 0.02f, headTop - size * 0.15f), stroke.width, stroke.cap)
            drawLine(ink, Offset(center.x + size * 0.1f, headTop), Offset(center.x + size * 0.18f, headTop - size * 0.1f), stroke.width, stroke.cap)
        }
        1 -> { // Cowlick spike
            val path = Path().apply {
                moveTo(center.x - size * 0.15f, headTop + size * 0.05f)
                quadraticTo(center.x, headTop - size * 0.2f, center.x + size * 0.15f, headTop + size * 0.02f)
            }
            drawPath(path, ink, style = stroke)
        }
        2 -> { // Messy fringe
            drawLine(ink, Offset(center.x - size * 0.2f, headTop + size * 0.03f), Offset(center.x - size * 0.08f, headTop - size * 0.05f), stroke.width, stroke.cap)
            drawLine(ink, Offset(center.x - size * 0.08f, headTop - size * 0.05f), Offset(center.x + size * 0.05f, headTop + size * 0.02f), stroke.width, stroke.cap)
        }
        else -> { // Neat side part
            drawLine(ink, Offset(center.x - size * 0.18f, headTop + size * 0.03f), Offset(center.x + size * 0.05f, headTop - size * 0.08f), stroke.width, stroke.cap)
        }
    }
}

private fun DrawScope.drawWimpyEyes(eyes: Int, center: Offset, size: Float, ink: Color) {
    val eyeY = center.y - size * 0.06f
    val leftX = center.x - size * 0.13f
    val rightX = center.x + size * 0.13f
    val r = size * 0.045f
    val stroke = Stroke(width = 2.8f.dp.toPx(), cap = StrokeCap.Round)

    when (eyes) {
        0 -> {} // None
        1 -> { // Closed (Wimpy Kid happy curved lines)
            drawArc(ink, 10f, 160f, false, topLeft = Offset(leftX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
            drawArc(ink, 10f, 160f, false, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
        }
        2 -> { // Open (classic black dot pupils inside big white/outlined circles)
            drawCircle(Color.White, r, Offset(leftX, eyeY))
            drawCircle(ink, r, Offset(leftX, eyeY), style = stroke)
            drawCircle(ink, r * 0.45f, Offset(leftX, eyeY))

            drawCircle(Color.White, r, Offset(rightX, eyeY))
            drawCircle(ink, r, Offset(rightX, eyeY), style = stroke)
            drawCircle(ink, r * 0.45f, Offset(rightX, eyeY))
        }
        3 -> { // Wink
            drawCircle(Color.White, r, Offset(leftX, eyeY))
            drawCircle(ink, r, Offset(leftX, eyeY), style = stroke)
            drawCircle(ink, r * 0.45f, Offset(leftX, eyeY))

            drawArc(ink, 10f, 160f, false, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
        }
        4 -> { // Happy (closed upward arcs)
            drawArc(ink, 190f, 160f, false, topLeft = Offset(leftX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
            drawArc(ink, 190f, 160f, false, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2), style = stroke)
        }
        5 -> { // Squint (dashes)
            drawLine(ink, Offset(leftX - r, eyeY), Offset(leftX + r, eyeY), strokeWidth = stroke.width, cap = stroke.cap)
            drawLine(ink, Offset(rightX - r, eyeY), Offset(rightX + r, eyeY), strokeWidth = stroke.width, cap = stroke.cap)
        }
        6 -> { // Dot eyes (Wimpy Kid minimalist dots)
            drawCircle(ink, r * 0.6f, Offset(leftX, eyeY))
            drawCircle(ink, r * 0.6f, Offset(rightX, eyeY))
        }
        7 -> { // Half-closed
            drawArc(ink, 0f, 180f, true, topLeft = Offset(leftX - r, eyeY - r), size = Size(r * 2, r * 2))
            drawArc(ink, 0f, 180f, true, topLeft = Offset(rightX - r, eyeY - r), size = Size(r * 2, r * 2))
        }
        else -> { // Wide eyed
            drawCircle(Color.White, r * 1.2f, Offset(leftX, eyeY))
            drawCircle(ink, r * 1.2f, Offset(leftX, eyeY), style = stroke)
            drawCircle(ink, r * 0.5f, Offset(leftX, eyeY))

            drawCircle(Color.White, r * 1.2f, Offset(rightX, eyeY))
            drawCircle(ink, r * 1.2f, Offset(rightX, eyeY), style = stroke)
            drawCircle(ink, r * 0.5f, Offset(rightX, eyeY))
        }
    }
}

private fun DrawScope.drawWimpyMouth(mouth: Int, center: Offset, size: Float, ink: Color) {
    val mouthY = center.y + size * 0.18f
    val w = size * 0.2f
    val stroke = Stroke(width = 2.8f.dp.toPx(), cap = StrokeCap.Round)

    when (mouth) {
        0 -> {} // None
        1 -> { // Smile (classic curved ink line)
            drawArc(ink, 10f, 160f, false, topLeft = Offset(center.x - w / 2f, mouthY - w / 4f), size = Size(w, w / 2f), style = stroke)
        }
        2 -> { // Grin (wide open smiling arc)
            drawArc(ink, 0f, 180f, false, topLeft = Offset(center.x - w / 2f, mouthY - w / 3f), size = Size(w, w * 0.6f), style = stroke)
            drawLine(ink, Offset(center.x - w / 2f, mouthY), Offset(center.x + w / 2f, mouthY), stroke.width, stroke.cap)
        }
        3 -> { // Laughing (big open box mouth)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(center.x - w / 2f, mouthY - w * 0.1f),
                size = Size(w, w * 0.45f),
                cornerRadius = CornerRadius(4f, 4f),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(center.x - w / 2f, mouthY - w * 0.1f),
                size = Size(w, w * 0.45f),
                cornerRadius = CornerRadius(4f, 4f),
                style = stroke,
            )
        }
        4 -> { // Sad (frown arc)
            drawArc(ink, 190f, 160f, false, topLeft = Offset(center.x - w / 2f, mouthY), size = Size(w, w / 2f), style = stroke)
        }
        5 -> { // Neutral (wavy or straight line)
            drawLine(ink, Offset(center.x - w / 2f, mouthY), Offset(center.x + w / 2f, mouthY), stroke.width, stroke.cap)
        }
        else -> { // Surprised (little O)
            drawCircle(Color.White, w * 0.25f, Offset(center.x, mouthY))
            drawCircle(ink, w * 0.25f, Offset(center.x, mouthY), style = stroke)
        }
    }
}

private fun DrawScope.drawWimpyAccessory(accessory: Int, center: Offset, w: Float, h: Float, ink: Color) {
    val stroke = Stroke(width = 2.8f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (accessory) {
        1 -> { // Nerd Glasses (classic round Wimpy kid glasses)
            val eyeY = center.y - w * 0.06f
            val r = w * 0.11f
            drawCircle(Color.White, r, Offset(center.x - r * 1.15f, eyeY))
            drawCircle(ink, r, Offset(center.x - r * 1.15f, eyeY), style = stroke)

            drawCircle(Color.White, r, Offset(center.x + r * 1.15f, eyeY))
            drawCircle(ink, r, Offset(center.x + r * 1.15f, eyeY), style = stroke)

            drawLine(ink, Offset(center.x - r * 0.2f, eyeY), Offset(center.x + r * 0.2f, eyeY), stroke.width, stroke.cap)
        }
        2 -> { // Cool Sunglasses
            val eyeY = center.y - w * 0.06f
            val r = w * 0.11f
            drawRect(ink, topLeft = Offset(center.x - r * 2.2f, eyeY - r * 0.8f), size = Size(r * 2.1f, r * 1.6f))
            drawRect(ink, topLeft = Offset(center.x + r * 0.1f, eyeY - r * 0.8f), size = Size(r * 2.1f, r * 1.6f))
            drawLine(ink, Offset(center.x - r * 0.1f, eyeY), Offset(center.x + r * 0.1f, eyeY), stroke.width * 1.2f, stroke.cap)
        }
        3 -> { // Baseball cap
            val headTop = center.y - h * 0.44f
            drawArc(ink, 180f, 180f, true, topLeft = Offset(center.x - w * 0.4f, headTop - w * 0.15f), size = Size(w * 0.8f, w * 0.4f))
            // Brim
            val brim = Path().apply {
                moveTo(center.x - w * 0.05f, headTop + w * 0.05f)
                lineTo(center.x + w * 0.45f, headTop + w * 0.12f)
                lineTo(center.x - w * 0.1f, headTop + w * 0.15f)
            }
            drawPath(brim, ink)
        }
        4 -> { // Headband
            val headY = center.y - h * 0.32f
            drawArc(ink, 195f, 150f, false, topLeft = Offset(center.x - w * 0.42f, headY - w * 0.1f), size = Size(w * 0.84f, w * 0.2f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        }
        else -> {}
    }
}
