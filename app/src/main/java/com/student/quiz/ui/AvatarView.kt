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
            val seedJitter = (seedHash % 5) - 2f

            val skinColor = Color.hsl(
                hue = spec.hue.toFloat().coerceIn(0f, 360f),
                saturation = 0.65f,
                lightness = 0.72f,
            )

            drawFace(spec.face, center, w * 0.38f + seedJitter * 0.2f, h * 0.44f, skinColor)
            drawEyes(spec.eyes, center, w)
            drawMouth(spec.mouth, center, w)
            drawAccessory(spec.accessory, center, w, h)
        }
    }
}

private fun DrawScope.drawFace(face: Int, center: Offset, rx: Float, ry: Float, color: Color) {
    val path = Path()
    val top = center.y - ry
    val bottom = center.y + ry
    val left = center.x - rx
    val right = center.x + rx

    when (face) {
        0 -> { // Round
            drawOval(color = color, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f))
        }
        1 -> { // Soft rounded rect
            drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f), cornerRadius = CornerRadius(rx * 0.5f, ry * 0.5f))
        }
        2 -> { // Oval tall
            drawOval(color = color, topLeft = Offset(center.x - rx * 0.85f, top * 1.05f), size = Size(rx * 1.7f, ry * 2.1f))
        }
        3 -> { // Square
            drawRect(color = color, topLeft = Offset(left * 1.02f, top * 1.02f), size = Size(rx * 1.9f, ry * 1.9f))
        }
        4 -> { // Heart
            path.moveTo(center.x, bottom)
            path.cubicTo(center.x - rx * 1.5f, center.y, left, top * 0.8f, center.x, top * 1.2f)
            path.cubicTo(right, top * 0.8f, center.x + rx * 1.5f, center.y, center.x, bottom)
            drawPath(path, color)
        }
        5 -> { // Diamond
            path.moveTo(center.x, top)
            path.lineTo(right, center.y)
            path.lineTo(center.x, bottom)
            path.lineTo(left, center.y)
            path.close()
            drawPath(path, color)
        }
        else -> {
            drawOval(color = color, topLeft = Offset(left, top), size = Size(rx * 2f, ry * 2f))
        }
    }
}

private fun DrawScope.drawEyes(eyes: Int, center: Offset, size: Float) {
    val eyeY = center.y - size * 0.08f
    val leftX = center.x - size * 0.14f
    val rightX = center.x + size * 0.14f
    val radius = size * 0.045f
    val stroke = size * 0.02f
    val eyeColor = Color(0xFF2D3748)

    when (eyes) {
        0 -> {}
        1 -> { // Closed
            drawArc(eyeColor, 0f, 180f, false, topLeft = Offset(leftX - radius, eyeY - radius), size = Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(eyeColor, 0f, 180f, false, topLeft = Offset(rightX - radius, eyeY - radius), size = Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        2 -> { // Open
            drawCircle(eyeColor, radius * 0.9f, Offset(leftX, eyeY))
            drawCircle(eyeColor, radius * 0.9f, Offset(rightX, eyeY))
            drawCircle(Color.White, radius * 0.3f, Offset(leftX - radius * 0.3f, eyeY - radius * 0.3f))
            drawCircle(Color.White, radius * 0.3f, Offset(rightX - radius * 0.3f, eyeY - radius * 0.3f))
        }
        3 -> { // Wink
            drawCircle(eyeColor, radius * 0.9f, Offset(leftX, eyeY))
            drawCircle(Color.White, radius * 0.3f, Offset(leftX - radius * 0.3f, eyeY - radius * 0.3f))
            drawArc(eyeColor, 0f, 180f, false, topLeft = Offset(rightX - radius, eyeY - radius), size = Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        4 -> { // Happy
            drawArc(eyeColor, 180f, 180f, false, topLeft = Offset(leftX - radius, eyeY - radius), size = Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(eyeColor, 180f, 180f, false, topLeft = Offset(rightX - radius, eyeY - radius), size = Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        5 -> { // Squint
            drawLine(eyeColor, Offset(leftX - radius, eyeY), Offset(leftX + radius, eyeY), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)
            drawLine(eyeColor, Offset(rightX - radius, eyeY), Offset(rightX + radius, eyeY), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)
        }
        6 -> { // Up
            drawCircle(eyeColor, radius * 0.8f, Offset(leftX, eyeY - radius * 0.3f))
            drawCircle(eyeColor, radius * 0.8f, Offset(rightX, eyeY - radius * 0.3f))
        }
        7 -> { // Half
            drawArc(eyeColor, 0f, 180f, true, topLeft = Offset(leftX - radius, eyeY - radius), size = Size(radius * 2, radius * 2))
            drawArc(eyeColor, 0f, 180f, true, topLeft = Offset(rightX - radius, eyeY - radius), size = Size(radius * 2, radius * 2))
        }
        else -> { // Wide
            drawCircle(eyeColor, radius * 1.1f, Offset(leftX, eyeY))
            drawCircle(eyeColor, radius * 1.1f, Offset(rightX, eyeY))
            drawCircle(Color.White, radius * 0.4f, Offset(leftX - radius * 0.3f, eyeY - radius * 0.3f))
            drawCircle(Color.White, radius * 0.4f, Offset(rightX - radius * 0.3f, eyeY - radius * 0.3f))
        }
    }
}

private fun DrawScope.drawMouth(mouth: Int, center: Offset, size: Float) {
    val mouthY = center.y + size * 0.16f
    val w = size * 0.18f
    val stroke = size * 0.02f
    val mouthColor = Color(0xFF2D3748)

    when (mouth) {
        0 -> {}
        1 -> { // Smile
            drawArc(mouthColor, 0f, 180f, false, topLeft = Offset(center.x - w / 2f, mouthY - w / 4f), size = Size(w, w / 2f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        2 -> { // Grin
            drawArc(mouthColor, 0f, 180f, true, topLeft = Offset(center.x - w / 2f, mouthY - w / 3f), size = Size(w, w / 1.5f))
        }
        3 -> { // Laughing
            drawRoundRect(mouthColor, topLeft = Offset(center.x - w / 2f, mouthY), size = Size(w, w * 0.4f), cornerRadius = CornerRadius(w * 0.2f, w * 0.2f))
        }
        4 -> { // Sad
            drawArc(mouthColor, 180f, 180f, false, topLeft = Offset(center.x - w / 2f, mouthY), size = Size(w, w / 2f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        5 -> { // Neutral
            drawLine(mouthColor, Offset(center.x - w / 2f, mouthY), Offset(center.x + w / 2f, mouthY), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        else -> { // Surprised
            drawCircle(mouthColor, stroke * 1.5f, Offset(center.x, mouthY), style = Stroke(stroke))
        }
    }
}

private fun DrawScope.drawAccessory(accessory: Int, center: Offset, w: Float, h: Float) {
    val accColor = Color(0xFF4A5568)
    val stroke = w * 0.02f

    when (accessory) {
        1 -> { // Glasses
            val eyeY = center.y - w * 0.08f
            val r = w * 0.09f
            drawRoundRect(accColor, topLeft = Offset(center.x - r * 2.1f, eyeY - r), size = Size(r * 2f, r * 2f), cornerRadius = CornerRadius(r * 0.3f, r * 0.3f), style = Stroke(stroke))
            drawRoundRect(accColor, topLeft = Offset(center.x + r * 0.1f, eyeY - r), size = Size(r * 2f, r * 2f), cornerRadius = CornerRadius(r * 0.3f, r * 0.3f), style = Stroke(stroke))
            drawLine(accColor, Offset(center.x - r * 0.1f, eyeY), Offset(center.x + r * 0.1f, eyeY), strokeWidth = stroke)
        }
        2 -> { // Sunglasses
            val eyeY = center.y - w * 0.08f
            val r = w * 0.095f
            drawRoundRect(Color.Black, topLeft = Offset(center.x - r * 2.2f, eyeY - r), size = Size(r * 2.1f, r * 2f), cornerRadius = CornerRadius(r * 0.3f, r * 0.3f))
            drawRoundRect(Color.Black, topLeft = Offset(center.x + r * 0.1f, eyeY - r), size = Size(r * 2.1f, r * 2f), cornerRadius = CornerRadius(r * 0.3f, r * 0.3f))
            drawLine(Color.Black, Offset(center.x - r * 0.1f, eyeY), Offset(center.x + r * 0.1f, eyeY), strokeWidth = stroke * 1.5f)
        }
        3 -> { // Hat
            val headTop = center.y - h * 0.44f
            drawRoundRect(accColor, topLeft = Offset(center.x - w * 0.3f, headTop - w * 0.2f), size = Size(w * 0.6f, w * 0.25f), cornerRadius = CornerRadius(w * 0.1f, w * 0.1f))
            drawRect(accColor, topLeft = Offset(center.x - w * 0.4f, headTop), size = Size(w * 0.8f, w * 0.05f))
        }
        4 -> { // Headband
            val headY = center.y - h * 0.32f
            drawArc(Color(0xFFE53E3E), 190f, 160f, false, topLeft = Offset(center.x - w * 0.4f, headY - w * 0.1f), size = Size(w * 0.8f, w * 0.2f), style = Stroke(stroke * 2f, cap = StrokeCap.Round))
        }
        else -> {}
    }
}
