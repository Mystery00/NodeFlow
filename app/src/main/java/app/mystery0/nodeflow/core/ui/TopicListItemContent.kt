package app.mystery0.nodeflow.core.ui

import app.mystery0.nodeflow.core.model.Topic

data class TopicNodeChip(
    val label: String,
    val nodeName: String,
)

fun topicNodeChip(topic: Topic): TopicNodeChip? {
    val nodeName = topic.node.name.trim()
    if (nodeName.isBlank()) return null
    return TopicNodeChip(
        label = topic.node.title.ifBlank { nodeName },
        nodeName = nodeName,
    )
}

fun topicPinnedChip(topic: Topic, label: String): String? = label.takeIf { topic.isPinned }

fun topicVotesChip(topic: Topic): String? {
    if (topic.votes <= 0) return null
    return "▲ ${topic.votes}"
}
