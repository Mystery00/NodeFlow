package app.mystery0.nodeflow.domain.topic

/** 为主题详情界面创建按需分页器。 */
class GetTopicDetailUseCase(
    private val repository: TopicRepository,
) {
    operator fun invoke(topicId: Long): TopicDetailPager = repository.topicDetailPager(topicId)
}
