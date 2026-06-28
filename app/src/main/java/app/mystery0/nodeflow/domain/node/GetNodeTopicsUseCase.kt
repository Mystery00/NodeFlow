package app.mystery0.nodeflow.domain.node

class GetNodeTopicsUseCase(
    private val repository: NodeRepository,
) {
    suspend operator fun invoke(
        name: String,
        page: Int = 1,
        forceRefresh: Boolean = false,
    ) = repository.topics(name, page, forceRefresh)
}
