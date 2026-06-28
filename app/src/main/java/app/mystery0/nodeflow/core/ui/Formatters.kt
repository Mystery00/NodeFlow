package app.mystery0.nodeflow.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatEpochSeconds(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return ""
    return DateFormatter.format(
        Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()),
    )
}
