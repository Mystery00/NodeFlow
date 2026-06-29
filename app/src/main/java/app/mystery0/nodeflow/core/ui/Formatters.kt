package app.mystery0.nodeflow.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SecondsPerMinute = 60L
private const val SecondsPerHour = 60L * SecondsPerMinute
private const val SecondsPerDay = 24L * SecondsPerHour

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun formatEpochSeconds(
    epochSeconds: Long?,
    nowEpochSeconds: Long = Instant.now().epochSecond,
): String {
    if (epochSeconds == null || epochSeconds <= 0) return ""
    val elapsedSeconds = nowEpochSeconds - epochSeconds
    if (elapsedSeconds < SecondsPerMinute) return "刚刚"
    if (elapsedSeconds < SecondsPerHour) return "${elapsedSeconds / SecondsPerMinute} 分钟前"
    if (elapsedSeconds < SecondsPerDay) return "${elapsedSeconds / SecondsPerHour} 小时前"
    return DateFormatter.format(
        Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()),
    )
}
