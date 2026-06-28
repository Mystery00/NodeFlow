package app.mystery0.nodeflow.domain.node

import javax.inject.Inject

class GetNodeUseCase @Inject constructor(
    private val repository: NodeRepository,
) {
    suspend operator fun invoke(name: String, forceRefresh: Boolean = false) =
        repository.node(name, forceRefresh)
}
