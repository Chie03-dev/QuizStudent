package com.student.quiz.data

import kotlin.math.sqrt

/** A point in the connect canvas' own pixel space. */
data class Pt(val x: Float, val y: Float)

/** A card as measured on screen, in the canvas' own pixel space. */
data class Card(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val centreY: Float get() = top + height / 2f
}

/**
 * The geometry and the link bookkeeping behind the connect question, kept free
 * of Compose so it can be unit tested: dot positions come from measured cards
 * (so cards of different heights still get their own dot), and every rule for
 * moving/removing a line lives here.
 */
object ConnectLayout {

    /**
     * The dot sits on the card's edge facing the other column: the right edge of
     * a prompt card, the left edge of an answer card, at the card's mid height.
     */
    fun dot(card: Card, facesRight: Boolean): Pt =
        Pt(if (facesRight) card.right else card.left, card.centreY)

    /** Shortest distance from [point] to the segment [a]-[b]. */
    fun distanceToSegment(point: Pt, a: Pt, b: Pt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return distance(point, a)
        val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared)
            .coerceIn(0f, 1f)
        return distance(point, Pt(a.x + t * dx, a.y + t * dy))
    }

    /** True when [point] is within [touchRadius] of the line [a]-[b]. */
    fun touchesLine(point: Pt, a: Pt, b: Pt, touchRadius: Float): Boolean =
        distanceToSegment(point, a, b) <= touchRadius

    /** The closest dot within [touchRadius], or null. */
    fun <T> hitDot(
        dots: List<Pair<Pt, T>>,
        point: Pt,
        touchRadius: Float,
    ): T? = dots
        .filter { (at, _) -> distance(at, point) <= touchRadius }
        .minByOrNull { (at, _) -> distance(at, point) }
        ?.second

    /**
     * The links after dragging [promptId] onto [answerId]: the prompt's old line
     * is replaced, and an answer another prompt held moves to this prompt.
     * Order of the remaining prompts is kept.
     */
    fun connect(
        links: Map<String, String>,
        promptId: String,
        answerId: String,
    ): Map<String, String> {
        val kept = links.filterKeys { it != promptId }.filterValues { it != answerId }
        return kept + (promptId to answerId)
    }

    /** The prompt holding [answerId], or null when the answer is free. */
    fun holderOf(links: Map<String, String>, answerId: String): String? =
        links.entries.firstOrNull { it.value == answerId }?.key

    private fun distance(a: Pt, b: Pt): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}