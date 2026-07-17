package app.mystery0.nodeflow.domain.membertag

import kotlinx.coroutines.flow.Flow

class ObserveMemberTagsUseCase(
    private val repository: MemberTagRepository,
) {
    operator fun invoke(): Flow<Map<String, List<String>>> = repository.observeTags()
}

class ObserveMemberTagSyncedAtUseCase(
    private val repository: MemberTagRepository,
) {
    operator fun invoke(): Flow<Long?> = repository.observeSyncedAt()
}

class RefreshMemberTagsUseCase(
    private val repository: MemberTagRepository,
) {
    suspend operator fun invoke(force: Boolean = false): Result<Unit> = repository.refresh(force)
}
