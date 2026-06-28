package app.mystery0.nodeflow.domain.node

class GetNodeUseCase(
    private val repository: NodeRepository,
) {
    suspend operator fun invoke(name: String, forceRefresh: Boolean = false) =
        repository.node(name, forceRefresh)
}
