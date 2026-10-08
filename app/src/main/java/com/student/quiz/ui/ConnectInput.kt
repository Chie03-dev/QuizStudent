package com.student.quiz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.student.quiz.data.AnswerValue
import com.student.quiz.data.ConnectLayout
import com.student.quiz.data.Question
import com.student.quiz.data.Card as CardBox
import com.student.quiz.data.Pt

/**
 * The connect question: two columns of cards, each with a dot on the edge
 * facing the other column, and a line the student drags from a prompt to an
 * answer. Sent as a map promptId -> answerId.
 *
 * Rules:
 *  - dragging from a prompt replaces that prompt's existing line;
 *  - using an answer another prompt already holds moves the line to this prompt;
 *  - tapping a connected line, or a connected dot, removes it.
 *
 * Cards size themselves to their text, so every dot position is computed from
 * the measured card box (onGloballyPositioned) instead of an assumed row height:
 * lines attach to the right card even when the columns have different heights.
 * Every card is at least 48dp tall and every dot keeps a 48dp touch target.
 */
@Composable
fun ConnectInput(question: Question.Connect, value: AnswerValue, vm: AppViewModel) {
    val links = (value as? AnswerValue.Links)?.links.orEmpty()
    val density = LocalDensity.current
    val lineColor = MaterialTheme.colorScheme.primary
    val unusedColor = MaterialTheme.colorScheme.outlineVariant

    var lines by remember(question.qid) { mutableStateOf(links) }
    var dragPrompt by remember(question.qid) { mutableStateOf<String?>(null) }
    var dragTo by remember(question.qid) { mutableStateOf<Pt?>(null) }

    // Follow an answer restored from the server, but never fight the student
    // mid-drag. Done in a side effect, not during composition.
    LaunchedEffect(links, dragPrompt) {
        if (dragPrompt == null) lines = links
    }

    // Measured card boxes, in root coordinates, plus this Box's own origin so
    // dots can be expressed in the canvas' space (where touches arrive).
    val boxes = remember(question.qid) { mutableStateMapOf<String, CardBox>() }
    var origin by remember(question.qid) { mutableStateOf(Offset.Zero) }

    val dotRadiusPx = with(density) { DOT_RADIUS.toPx() }
    val touchRadiusPx = with(density) { TOUCH_TARGET.toPx() }

    fun promptDot(id: String): Pt? = boxes["p:$id"]
        ?.let { ConnectLayout.dot(it, facesRight = true) }
        ?.movedBy(origin)

    fun answerDot(id: String): Pt? = boxes["a:$id"]
        ?.let { ConnectLayout.dot(it, facesRight = false) }
        ?.movedBy(origin)

    /** Every measured dot, with what it is, for hit testing. */
    fun allDots(): List<Pair<Pt, ConnectDot>> = buildList {
        question.prompts.forEach { item ->
            promptDot(item.id)?.let { dot -> add(dot to ConnectDot(item.id, true)) }
        }
        question.answers.forEach { item ->
            answerDot(item.id)?.let { dot -> add(dot to ConnectDot(item.id, false)) }
        }
    }

    // The gesture handler is keyed on the question only (see below), so it must
    // read the current lines through a state holder, not a captured value.
    val currentLines = rememberUpdatedState(lines)
    val currentDragPrompt = rememberUpdatedState(dragPrompt)
    val currentDragTo = rememberUpdatedState(dragTo)

    fun lineNear(point: Pt): String? = currentLines.value.entries.firstOrNull { (promptId, answerId) ->
        val start = promptDot(promptId)
        val end = answerDot(answerId)
        start != null && end != null && ConnectLayout.touchesLine(point, start, end, touchRadiusPx)
    }?.key

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Keyed on the question only: keying on the answer would restart this
            // handler in the middle of a drag, because a completed drag changes it.
            .pointerInput(question.qid) {
    awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hit = ConnectLayout.hitDot(allDots(), down.position.toPt(), touchRadiusPx)

                    when {
                        // A prompt dot: drag out a line, or tap to clear it.
                        hit != null && hit.isPrompt -> {
                            val before = currentLines.value
                            dragPrompt = hit.id
                            dragTo = down.position.toPt()
                            down.consume()

                            var moved = false
                            var released: Pt? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    released = change.position.toPt()
                                    change.consume()
                                    break
                                }
                                if ((change.position - down.position).getDistance() > SLOP) moved = true
                                dragTo = change.position.toPt()
                                change.consume()
                            }

                            if (!moved) {
                                // A tap on a connected prompt dot removes its line.
                                if (before.containsKey(hit.id)) {
                                    lines = before - hit.id
                                    vm.onAnswer(question, AnswerValue.Links(lines), immediate = true)
                                }
                            } else {
                                val target = released
                                    ?.let { ConnectLayout.hitDot(allDots(), it, touchRadiusPx) }
                                if (target != null && !target.isPrompt) {
                                    // Reusing an answer another prompt holds moves the line here.
                                    lines = ConnectLayout.connect(before, hit.id, target.id)
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
                            val holder = ConnectLayout.holderOf(before, hit.id)
                            if (holder != null) {
                                lines = before - holder
                                vm.onAnswer(question, AnswerValue.Links(lines), immediate = true)
                            }
                        }

                        // A tap on a line, away from any dot, removes it.
                        else -> {
                            val line = lineNear(down.position.toPt())
                            if (line != null) {
                                down.consume()
                                lines = currentLines.value - line
                                vm.onAnswer(question, AnswerValue.Links(lines), immediate = true)
                            }
                        }
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            // Wide enough for both columns' dots (11dp each side = 22dp) plus
            // clearance, so opposing dots and their touch targets never collide.
            horizontalArrangement = Arrangement.spacedBy(GUTTER),
            verticalAlignment = Alignment.Top,
        ) {
            ConnectColumn(
                items = question.prompts.map { it.id to it.text },
                used = lines.keys,
                usedColor = lineColor,
                onMeasured = { id, box -> boxes["p:$id"] = box },
                facesRight = true,
                modifier = Modifier.weight(1f),
            )
            ConnectColumn(
                items = question.answers.map { it.id to it.text },
                used = lines.values.toSet(),
                usedColor = lineColor,
                onMeasured = { id, box -> boxes["a:$id"] = box },
                facesRight = false,
                modifier = Modifier.weight(1f),
            )
        }

        // Drawn last so a line ends up on top of the cards, but it takes no
        // pointer input: every touch still reaches the Box's gesture handler.
        Canvas(Modifier.fillMaxSize()) {
            lines.forEach { (promptId, answerId) ->
                val start = promptDot(promptId) ?: return@forEach
                val end = answerDot(answerId) ?: return@forEach
                drawLine(
                    color = lineColor,
                    start = start.toOffset(),
                    end = end.toOffset(),
                    strokeWidth = dotRadiusPx * 0.55f,
                    cap = StrokeCap.Round,
                )
            }

            // The line being dragged right now.
            currentDragPrompt.value?.let { promptId ->
                val start = promptDot(promptId)
                if (start != null) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.6f),
                        start = start.toOffset(),
                        end = (currentDragTo.value ?: start).toOffset(),
                        strokeWidth = dotRadiusPx * 0.55f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            question.prompts.forEach { item ->
                val at = promptDot(item.id) ?: return@forEach
                drawDot(at, lineColor, filled = item.id in lines, radius = dotRadiusPx)
            }
            val used = lines.values.toSet()
            question.answers.forEach { item ->
                val at = answerDot(item.id) ?: return@forEach
                val isUsed = item.id in used
                drawDot(
                    at,
                    if (isUsed) lineColor else unusedColor,
                    filled = isUsed,
                    radius = dotRadiusPx,
                )
            }
        }
    }
}

/** A dot the student can grab: the item id and which column it sits in. */
private data class ConnectDot(val id: String, val isPrompt: Boolean)

/** One column of cards; [onMeasured] reports each card's measured box. */
@Composable
private fun ConnectColumn(
    items: List<Pair<String, String>>,
    used: Set<String>,
    usedColor: Color,
    onMeasured: (String, CardBox) -> Unit,
    facesRight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        items.forEach { (id, text) ->
            val isUsed = id in used
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // 48dp minimum touch target, even for a one-word answer.
                    .heightIn(min = TOUCH_TARGET)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        val size = coords.size
                        onMeasured(
                            id,
                            CardBox(
                                left = pos.x,
                                top = pos.y,
                                right = pos.x + size.width,
                                bottom = pos.y + size.height,
                            ),
                        )
                    },
                shape = RoundedCornerShape(12.dp),
                border = if (isUsed) {
                    BorderStroke(2.dp, usedColor)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isUsed) {
                        usedColor.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Text(
                    // Fall back to the id so a missing "text" field can never
                    // leave an unreadable card.
                    text = text.ifBlank { id },
                    style = MaterialTheme.typography.bodyMedium,
                    // Long items are trimmed with an ellipsis rather than
                    // stretching the column.
                    maxLines = MAX_TEXT_LINES,
                    overflow = TextOverflow.Ellipsis,
                    // Extra padding on the dot's side so wrapped text never
                    // runs under the dot drawn on the card edge.
                    modifier = Modifier.padding(
                        start = if (facesRight) 12.dp else TEXT_BEHIND_DOT,
                        end = if (facesRight) TEXT_BEHIND_DOT else 12.dp,
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
                )
            }
        }
    }
}

/** A dot on a card's inner edge: hollow when free, filled when connected. */
private fun DrawScope.drawDot(at: Pt, color: Color, filled: Boolean, radius: Float) {
    val centre = at.toOffset()
    drawCircle(Color.White, radius, centre)
    drawCircle(color, radius, centre, style = Stroke(radius * 0.35f))
    if (filled) drawCircle(color, radius * 0.45f, centre)
}

/** Card boxes are measured in root space; touches arrive in this Box's space. */
private fun Pt.movedBy(delta: Offset): Pt = Pt(x - delta.x, y - delta.y)

private fun Offset.toPt(): Pt = Pt(x, y)

private fun Pt.toOffset(): Offset = Offset(x, y)

/** Movement past this counts as a drag rather than a tap. */
private const val SLOP = 24f

/** A dot keeps a 48dp touch target even though it is drawn much smaller. */
private val TOUCH_TARGET = 48.dp

private val DOT_RADIUS = 11.dp

/**
 * Space between the two columns: both columns' dots sit on the facing edges
 * (11dp each = 22dp), so the gutter needs to fit them plus clearance.
 */
private val GUTTER = 32.dp

/** Vertical gap between cards; enough for the neighbouring dots' reach. */
private val CARD_GAP = 14.dp

/**
 * Text padding on the side a dot is drawn on: DOT_RADIUS (11dp) plus room so
 * wrapped text never touches the dot.
 */
private val TEXT_BEHIND_DOT = 16.dp

private const val MAX_TEXT_LINES = 3
