package app.mystery0.nodeflow.domain.node

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BlockNodeUseCaseTest {
    @Test
    fun invoke_delegatesNodeNameAndResult() = runTest {
        val repository = FakeNodeRepository()
        val useCase = BlockNodeUseCase(repository)

        val result = useCase("android")

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.blockedNames).containsExactly("android")
    }

    private class FakeNodeRepository : NodeRepository {
        val blockedNames = mutableListOf<String>()

        override suspend fun blockNode(name: String): Result<Unit> {
            blockedNames += name
            return Result.success(Unit)
        }

        override suspend fun nodePlanes(forceRefresh: Boolean): Result<List<NodePlane>> =
            Result.success(emptyList())

        override suspend fun node(name: String, forceRefresh: Boolean): Result<Node> =
            Result.failure(AssertionError("Unexpected node request"))

        override suspend fun topics(
            name: String,
            page: Int,
            forceRefresh: Boolean,
        ): Result<List<Topic>> = Result.success(emptyList())

        override fun topicsPaging(name: String): Flow<PagingData<Topic>> =
            flowOf(PagingData.empty())

        override suspend fun clearCache() = Unit
    }
}
