package com.student.quiz.data

object MatchingLogic {
    /**
     * Selects a right item for a left item, enforcing the rule that
     * one right item can be used at most once. If the right item is already
     * used by another left item, it is removed from that previous left item.
     */
    fun selectRight(
        links: Map<String, String>,
        leftId: String,
        rightId: String,
    ): Map<String, String> {
        val mutable = links.toMutableMap()
        val previousOwner = mutable.entries.firstOrNull { it.value == rightId && it.key != leftId }?.key
        if (previousOwner != null) {
            mutable.remove(previousOwner)
        }
        mutable[leftId] = rightId
        return mutable
    }

    /**
     * Returns the leftId (other than currentLeftId) that is currently using rightId, if any.
     */
    fun holderOf(
        links: Map<String, String>,
        currentLeftId: String,
        rightId: String,
    ): String? {
        return links.entries.firstOrNull { it.value == rightId && it.key != currentLeftId }?.key
    }
}
