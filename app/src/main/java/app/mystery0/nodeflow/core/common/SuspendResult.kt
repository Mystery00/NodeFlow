package app.mystery0.nodeflow.core.common

import kotlinx.coroutines.CancellationException

/** 将业务失败转为 Result，同时保留结构化并发的取消语义。 */
internal suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
