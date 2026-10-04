package com.student.quiz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.Question

/**
 * The connect question: two columns of dots, with a line the student drags from
 * a prompt to an answer. Sent as a map promptId -> answerId.
 *
 * Rules:
 *  - dragging from a prompt replaces that prompt's existing line;
 *  - using an answer another prompt already holds moves the line to this prompt;
 *  - tapping a connected line, or a connected dot, removes it.
 *
 * The layout is deliberately plain so it works in portrait on small screens:
 * evenly spaced rows, large dots and a generous touch radius.
 */
@Composable
fun ConnectInput(question: Question.Connect, value: AnswerValue, vm: AppViewModel) {
    val links = (value as? AnswerValue.Links)?.links.orEmpty()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val lineColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val unusedColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant

    var lines by remember(question.qid) { mutableStateOf(links) }
    var dragPrompt by remember(question.qid) { mutableStateOf<String?>(null) }
    var dragTo by remember(question.qid) { mutableStateOf<Offset?>(null) }

    // Follow an answer restored from the server, but never fight the student
    // mid-drag. Done in a side effect, not during composition.
    LaunchedEffect(links, dragPrompt) {
        if (dragPrompt == null) lines = links
    }

    val rows = maxOf(question.prompts.size, question.answers.size).coerceAtLeast(1)
    val rowHeight = 76.dp
    val dotRadius = 11.dp
    val touchRadius = 36.dp
    val columnInset = 92.dp

    val rowHeightPx = with(density) { rowHeight.toPx() }
    val dotRadiusPx = with(density) { dotRadius.toPx() }
    val touchRadiusPx = with(density) { touchRadius.toPx() }
    val columnInsetPx = with(density) { columnInset.toPx() }

    fun yOf(index: Int) = rowHeightPx * index + rowHeightPx / 2f
    fun promptX(width: Float) = columnInsetPx
    fun answerX(width: Float) = width - columnInsetPx

    fun dotAt(width: Float, position: Offset): Pair<String, Boolean>? {
        question.prompts.forEachIndexed { index, item ->
            if ((position - Offset(promptX(width), yOf(index))).getDistance() <= touchRadiusPx) {
                return item.id to true
            }
        }
        question.answers.forEachIndexed { index, item ->
            if ((position - Offset(answerX(width), yOf(index))).getDistance() <= touchRadiusPx) {
                return item.id to false
            }
        }
        return null
    }

    // The gesture handler is keyed on the question only (see below), so it must
    // read the current lines through a state holder, not a captured value.
    val currentLines = rememberUpdatedState(lines)
    val currentDragPrompt = rememberUpdatedState(dragPrompt)
    val currentDragTo = rememberUpdatedState(dragTo)

    fun lineNear(width: Float, position: Offset): String? =
        currentLines.value.entries.firstOrNull { (promptId, answerId) ->
            val p = question.prompts.indexOfFirst { it.id == promptId }
            val a = question.answers.indexOfFirst { it.id == answerId }
            if (p < 0 || a < 0) return@firstOrNull false
            distanceToSegment(
                position,
                Offset(promptX(width), yOf(p)),
                Offset(answerX(width), yOf(a)),
            ) <= touchRadiusPx
        }?.key
Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * rows)
            // Keyed on the question only: keying on the answer would restart this
            // handler in the middle of a drag, because a completed drag changes it.
            .pointerInput(question.qid) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    val start = down.position
                    val hit = dotAt(width, start)

                    when {
                        // A prompt dot: drag out a line, or tap to clear it.
                        hit != null && hit.second -> {
                            val before = currentLines.value
                            dragPrompt = hit.first
                            dragTo = start
                            down.consume()

                            var moved = false
                            var released: Offset? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    released = change.position
                                    change.consume()
                                    break
                                }
                                if ((change.position - start).getDistance() > SLOP) moved = true
                                dragTo = change.position
                                change.consume()
                            }

                            if (!moved) {
                                // A tap on a connected prompt dot removes its line.
                                if (before.containsKey(hit.first)) {
                                    lines = before - hit.first
                                    vm.onAnswer(question, AnswerValue.Links(lines), immediate = true)
                                }
                            } else {
                                val target = released?.let { dotAt(width, it) }
                                if (target != null && !target.second) {
                                    // Reusing an answer another prompt holds moves the line here.
                                    val previous = before.entries
                                        .firstOrNull { it.value == target.first }?.key
                                    lines = before.filterKeys {
                                        it != hit.first && it != previous
                                    } + (hit.first to target.first)
                                    vm.onAnswer(question, AnswerValue.Links(lines), immediate = true)
                                }
                                // Dropped away from a dot: the prompt keeps its old line.
                            }
                            dragPrompt = null
                            dragTo = null
                        }

                        // A tap on a connected answer dot removes whoever holds it.
                        hit != null -> {
                            down.consume()
                            val before = currentLines.value
                            val holder = before.entries.firstOrNull { it.value == hit.first }?.key
                            if (holder != null) {
                                lines = before - holder
                                vm.onAnswer(question, AnswerValue.Links(lines), immediate = true)
                            }
                        }

                        // A tap on a line, away from any dot, removes it.
                        else -> {
                            down.consume()
                            val line = lineNear(width, start)
                            if (line != null) {
                                lines = currentLines.value - line
                                vm.onAnswer(question, AnswerValue.Links(lines), immediate = true)
                            }
                        }
                    }
                }
            },
    ) {
        val width = size.width

        // Existing lines first, so the dots sit on top of them.
        lines.forEach { (promptId, answerId) ->
            val p = question.prompts.indexOfFirst { it.id == promptId }
            val a = question.answers.indexOfFirst { it.id == answerId }
            if (p < 0 || a < 0) return@forEach
            drawLine(
                color = lineColor,
                start = Offset(promptX(width), yOf(p)),
                end = Offset(answerX(width), yOf(a)),
                strokeWidth = dotRadiusPx * 0.55f,
                cap = StrokeCap.Round,
            )
        }

        // The line being dragged right now.
        currentDragPrompt.value?.let { promptId ->
            val p = question.prompts.indexOfFirst { it.id == promptId }
            if (p >= 0) {
                drawLine(
                    color = lineColor.copy(alpha = 0.6f),
                    start = Offset(promptX(width), yOf(p)),
                    end = currentDragTo.value ?: Offset(promptX(width), yOf(p)),
                    strokeWidth = dotRadiusPx * 0.55f,
                    cap = StrokeCap.Round,
                )
            }
        }

        val used = lines.values.toSet()
        question.prompts.forEachIndexed { index, item ->
            val at = Offset(promptX(width), yOf(index))
            drawCircle(Color.White, dotRadiusPx, at)
            drawCircle(lineColor, dotRadiusPx, at, style = Stroke(dotRadiusPx * 0.35f))
            drawLabel(measurer, item.text, at.y, rightEdge = at.x - dotRadiusPx - 8f)
        }
        question.answers.forEachIndexed { index, item ->
            val at = Offset(answerX(width), yOf(index))
            val colour = if (item.id in used) lineColor else unusedColor
            drawCircle(Color.White, dotRadiusPx, at)
            drawCircle(colour, dotRadiusPx, at, style = Stroke(dotRadiusPx * 0.35f))
            if (item.id in used) drawCircle(colour, dotRadiusPx * 0.45f, at)
            drawLabel(measurer, item.text, at.y, leftEdge = at.x + dotRadiusPx + 8f)
        }
    }
}

/** Draws a two-line label, right-aligned up to [rightEdge] or left-aligned from [leftEdge]. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    centreY: Float,
    rightEdge: Float = Float.NaN,
    leftEdge: Float = Float.NaN,
) {
    val layout = measurer.measure(
        text = text,
        style = TextStyle(
            fontSize = 14.sp,
            textAlign = if (rightEdge.isNaN()) TextAlign.Start else TextAlign.End,
        ),
        maxLines = 2,
    )
    val x = if (rightEdge.isNaN()) leftEdge else rightEdge - layout.size.width
    val top = centreY - layout.size.height / 2f
    drawText(textLayoutResult = layout, topLeft = Offset(x, top))
}

/** Shortest distance from [point] to the segment [a]-[b]. */
private fun distanceToSegment(point: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return (point - a).getDistance()
    val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val projection = Offset(a.x + t * dx, a.y + t * dy)
    return (point - projection).getDistance()
}

/** Movement past this counts as a drag rather than a tap. */
private const val SLOP = 24f
