package app.mystery0.nodeflow.domain.node

class GetNodeTopicsPagingUseCase(
    private val repository: NodeRepository,
) {
    operator fun invoke(name: String) = repository.topicsPaging(name)
}
