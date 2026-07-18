package app.mystery0.nodeflow.feature.topicdetail

data class ScrollPosition(
    val itemIndex: Int,
    val itemOffset: Int,
) : Comparable<ScrollPosition> {
    override fun compareTo(other: ScrollPosition): Int =
        compareValuesBy(this, other, ScrollPosition::itemIndex, ScrollPosition::itemOffset)
}

fun replyFabVisibleAfterScroll(
    previous: ScrollPosition,
    current: ScrollPosition,
    previousVisible: Boolean,
): Boolean = when {
    current > previous -> false
    current < previous -> true
    else -> previousVisible
}
