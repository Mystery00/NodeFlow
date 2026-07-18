package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.ReplyDraftRepository
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ReplyDraftRepositoryImpl(
    private val local: ReplyDraftLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : ReplyDraftRepository {
    override suspend fun load(topicId: Long) = withContext(ioDispatcher) { local.load(topicId) }
    override suspend fun save(draft: ReplyDraft) = withContext(ioDispatcher) { local.save(draft) }
    override suspend fun addImage(topicId: Long, image: UploadedReplyImage) =
        withContext(ioDispatcher) { local.addImage(topicId, image) }
    override suspend fun clear(topicId: Long) = withContext(ioDispatcher) { local.clear(topicId) }
}
