package app.mystery0.nodeflow.domain.node

class GetNodePlanesUseCase(
    private val repository: NodeRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false) = repository.nodePlanes(forceRefresh)
}
