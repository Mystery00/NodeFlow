package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.domain.reply.CreateReplyResult
import app.mystery0.nodeflow.domain.reply.ReplyConstraints
import app.mystery0.nodeflow.domain.reply.ReplyFailureReason
import app.mystery0.nodeflow.domain.reply.ReplyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ReplyRepositoryImpl(
    private val remote: ReplyRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : ReplyRepository {
    override suspend fun loadConstraints(topicId: Long): Result<ReplyConstraints> =
        withContext(ioDispatcher) { runCatching { remote.loadConstraints(topicId) } }

    override suspend fun createReply(
        topicId: Long,
        content: String,
    ): CreateReplyResult = withContext(ioDispatcher) {
        try {
            remote.createReply(topicId, content)
        } catch (error: CancellationException) {
            throw error
        } catch (error: NodeFlowException) {
            val reason = when (error.kind) {
                NodeFlowException.Kind.Auth -> ReplyFailureReason.AuthenticationRequired
                NodeFlowException.Kind.Network -> ReplyFailureReason.Network
                else -> ReplyFailureReason.Server
            }
            CreateReplyResult.Failure(reason, error.message)
        } catch (_: Throwable) {
            CreateReplyResult.Failure(ReplyFailureReason.Server, "回复失败，请稍后重试")
        }
    }
}
