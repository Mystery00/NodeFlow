package app.mystery0.nodeflow.domain.node

class BlockNodeUseCase(
    private val repository: NodeRepository,
) {
    suspend operator fun invoke(name: String): Result<Unit> = repository.blockNode(name)
}
