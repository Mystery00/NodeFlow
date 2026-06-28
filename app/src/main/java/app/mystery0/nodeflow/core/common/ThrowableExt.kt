package app.mystery0.nodeflow.core.common

fun Throwable.toUserMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试"
